package com.example.app_mobile.ui.sellerdetail

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
 * The buyer's ledger history with ONE seller, plus the balance owed to them.
 *
 * The mirror of app-pos's CustomerDetailViewModel — scoped by (signed-in user,
 * seller) instead of (seller, customer). Entries come as a Flow, so an approval or
 * a payment updates the balance here live. Balance is recomputed, never stored.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SellerDetailViewModel(private val sellerId: String) : ViewModel() {

    private val allTransactions =
        FakeRepository.observeCurrentUser().flatMapLatest { user ->
            if (user == null) flowOf(emptyList())
            else FakeRepository.observeMyTransactions(user.userId, sellerId)
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

    fun onFilterChanged(newFilter: TransactionFilter) {
        filter.value = newFilter
    }

    /**
     * The buyer pays this seller. Goes through the approval gate (the seller confirms
     * receipt); suspend, so it runs in viewModelScope. The balance updates live once
     * the entry is written (immediately for an app-less seller, on approval otherwise).
     */
    fun pay(amountMinor: Long) {
        if (amountMinor <= 0) return
        val userId = FakeRepository.currentUserId() ?: return
        viewModelScope.launch {
            FakeRepository.initiatePayment(userId, sellerId, amountMinor)
        }
    }
}
