package com.example.app_pos.ui.sale

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.app_pos.data.OtpService
import com.example.app_pos.data.RepositoryProvider
import com.example.app_pos.model.OrderBody
import com.example.app_pos.model.Transaction
import com.example.app_pos.model.TransactionType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/** Where the OTP step is in its request → verify → write lifecycle. */
enum class OtpStatus { SENDING, READY, VERIFYING, DONE, ERROR }

/**
 * Runs the customer-approval step and, on approval, performs the write.
 *
 * The write lives here (not on the confirm screen) because it must happen only
 * after OTP succeeds. It is the single place that appends to the ledger: for a
 * new customer it creates the record first (addCustomer), then writes the entry.
 * A UUID transactionId keeps a retry from applying the same entry twice.
 */
class OtpViewModel : ViewModel() {

    private val repo = RepositoryProvider.instance
    private val _status = MutableStateFlow(OtpStatus.SENDING)
    val status: StateFlow<OtpStatus> = _status.asStateFlow()

    /**
     * Asks the customer to approve. hasApp routes app-push vs SMS (both mocked
     * for now); the branch already exists so the real service slots in later.
     */
    fun sendOtp(phone: String, hasApp: Boolean) {
        _status.value = OtpStatus.SENDING
        viewModelScope.launch {
            val sent = OtpService.requestOtp(phone, hasApp)
            _status.value = if (sent) OtpStatus.READY else OtpStatus.ERROR
        }
    }

    /**
     * Verifies the code and, on success, writes the entry. Creates the customer
     * first when new. onWritten receives the resolved customerId (for messaging).
     */
    fun verifyAndWrite(
        phone: String,
        code: String,
        hasApp: Boolean,
        isNew: Boolean,
        displayName: String,
        knownCustomerId: String,
        amountMinor: Long,
        type: TransactionType,
        orderBody: OrderBody? = null,
        onWritten: () -> Unit
    ) {
        _status.value = OtpStatus.VERIFYING
        viewModelScope.launch {
            val ok = OtpService.verifyOtp(phone, code, hasApp)
            if (!ok) {
                _status.value = OtpStatus.ERROR
                return@launch
            }
            // The login gate guarantees a signed-in seller here; the null-check is
            // defensive. The entry is booked to this seller's ledger.
            val sellerId = repo.currentSellerId() ?: run {
                _status.value = OtpStatus.ERROR
                return@launch
            }
            val customerId =
                if (isNew) repo.addCustomer(displayName, phone) else knownCustomerId
            // orderBody is present only for a basket handoff (DEBT from the PGW); when
            // set, the basket + its items are persisted and linked. Money-only passes null.
            repo.addTransaction(
                Transaction(
                    transactionId = UUID.randomUUID().toString(),
                    sellerId = sellerId,
                    customerId = customerId,
                    amountMinor = amountMinor,
                    type = type,
                    description = descriptionFor(type),
                    createdAt = createdAtFormat().format(Date())
                ),
                orderBody = orderBody
            )
            _status.value = OtpStatus.DONE
            onWritten()
        }
    }

    private fun descriptionFor(type: TransactionType): String =
        when (type) {
            TransactionType.DEBT -> "Veresiye"
            TransactionType.PAYMENT -> "Ödeme"
        }

    private companion object {
        /**
         * ISO-8601 UTC — the format the wire contract uses and the DAOs sort on. A new
         * formatter per call because SimpleDateFormat is not thread-safe.
         */
        fun createdAtFormat(): SimpleDateFormat =
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
    }
}
