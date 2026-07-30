package com.example.app_mobile.ui.customerdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.app_mobile.data.FakeRepository
import com.example.app_pos.model.Transaction
import com.example.app_pos.model.TransactionType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
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

    private val allTransactions =
        FakeRepository.observeCurrentUser().flatMapLatest { user ->
            if (user == null) flowOf(emptyList())
            else FakeRepository.observeTransactions(user.userId, customerId)
        }

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
        val sellerId = FakeRepository.currentUserId() ?: return
        viewModelScope.launch {
            FakeRepository.requestApproval(
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
