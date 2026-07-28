package com.example.app_pos.ui.sale

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.app_pos.data.FakeRepository
import com.example.app_pos.model.TransactionType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Backs the confirm step: shows the selected customer's balance FRESH from the
 * ledger (not a copy carried from the pick step, which could be stale).
 *
 * The write is NOT here — it happens after OTP approval (see OtpFragment). This
 * screen only summarises what is about to happen and hands off to the OTP step.
 *
 * customerId is empty for a brand-new customer (created only at write time), so
 * the balance is treated as zero in that case.
 */
class ConfirmViewModel(private val customerId: String) : ViewModel() {

    /** The customer's current balance, kept in sync with the ledger; 0 if new. */
    val currentBalanceMinor: StateFlow<Long> =
        if (customerId.isEmpty()) {
            MutableStateFlow(0L)
        } else {
            FakeRepository.observeTransactions(customerId)
                .map { txs ->
                    txs.sumOf { tx ->
                        when (tx.type) {
                            TransactionType.DEBT -> tx.amountMinor
                            TransactionType.PAYMENT -> -tx.amountMinor
                        }
                    }
                }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)
        }
}
