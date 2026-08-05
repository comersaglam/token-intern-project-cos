package com.example.app_mobile.ui.customerdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.app_pos.data.RepositoryProvider
import com.example.app_pos.model.ClaimStatus
import com.example.app_pos.model.Customer
import com.example.app_pos.model.Transaction
import com.example.app_pos.model.TransactionType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Which ledger entries the history should show. */
enum class TransactionFilter { ALL, DEBT, PAYMENT }

/**
 * One customer's ledger with the signed-in SELLER, plus the write actions (veresiye /
 * payment) that go through the approval gate. Mirror of app-pos's CustomerDetailViewModel;
 * the difference is the write is a popup → requestApproval, not a keypad sale flow.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CustomerDetailViewModel(private val customerId: String) : ViewModel() {

    private val repo = RepositoryProvider.instance

    private val allTransactions =
        repo.observeCurrentUser().flatMapLatest { user ->
            if (user == null) flowOf(emptyList())
            else repo.observeTransactions(user.userId, customerId)
        }

    // The customer record behind this screen. Looked up once (a suspend DB read now)
    // and exposed as state, so the fragment stays a pure renderer instead of doing the
    // read itself — a `by lazy` block cannot host a suspend call.
    private val customer: StateFlow<Customer?> =
        repo.observeCurrentUser().flatMapLatest { user ->
            flow { emit(if (user == null) null else repo.findCustomerById(user.userId, customerId)) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Phone for the header; empty until the lookup lands. */
    val phone: StateFlow<String> =
        customer.map { it?.phone.orEmpty() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    /** Whether the customer holds the app — decides "sent for approval" vs "written". */
    val isClaimed: StateFlow<Boolean> =
        customer.map { it?.claimStatus == ClaimStatus.CLAIMED }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val filter = MutableStateFlow(TransactionFilter.ALL)

    val transactions: StateFlow<List<Transaction>> =
        combine(allTransactions, filter) { txs, f ->
            when (f) {
                TransactionFilter.ALL -> txs
                TransactionFilter.DEBT -> txs.filter { it.type == TransactionType.DEBT }
                TransactionFilter.PAYMENT -> txs.filter { it.type == TransactionType.PAYMENT }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val balanceMinor: StateFlow<Long> =
        allTransactions.map { txs ->
            txs.sumOf { tx ->
                when (tx.type) {
                    TransactionType.DEBT -> tx.amountMinor
                    TransactionType.PAYMENT -> -tx.amountMinor
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    fun onFilterChanged(newFilter: TransactionFilter) { filter.value = newFilter }

    /**
     * Books a veresiye (DEBT) or takes a payment (PAYMENT) for this customer, through
     * the approval gate: an app-holding customer gets a pending approval; an app-less
     * one is written immediately (mock SMS-OTP). Suspend call runs in viewModelScope.
     */
    fun submit(type: TransactionType, amountMinor: Long, description: String) {
        if (amountMinor <= 0) return
        val sellerId = repo.currentUserId() ?: return
        viewModelScope.launch {
            repo.requestApproval(
                fromUserId = sellerId,
                sellerId = sellerId,
                customerId = customerId,
                amountMinor = amountMinor,
                type = type,
                description = description
            )
        }
    }
}
