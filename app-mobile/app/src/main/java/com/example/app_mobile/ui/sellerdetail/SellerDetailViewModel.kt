package com.example.app_mobile.ui.sellerdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.app_pos.data.RepositoryProvider
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
 * The buyer's ledger history with ONE seller, plus the balance owed to them.
 *
 * The mirror of app-pos's CustomerDetailViewModel — scoped by (signed-in user,
 * seller) instead of (seller, customer). Entries come as a Flow, so an approval or
 * a payment updates the balance here live. Balance is recomputed, never stored.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SellerDetailViewModel(private val sellerId: String) : ViewModel() {

    private val repo = RepositoryProvider.instance

    private val allTransactions =
        repo.observeCurrentUser().flatMapLatest { user ->
            if (user == null) flowOf(emptyList())
            else repo.observeMyTransactions(user.userId, sellerId)
        }

    /**
     * The shop's phone for the header — how the buyer reaches them. Empty when the
     * seller has not set one, so the row can hide. Mirrors CustomerDetailViewModel's
     * phone, simpler because sellerId is a constructor argument.
     */
    val shopPhone: StateFlow<String> =
        flow { emit(repo.shopPhoneOf(sellerId).orEmpty()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

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
     *
     * [onResult] reports whether the request actually went out, so the screen only
     * claims success when something was sent.
     */
    fun pay(amountMinor: Long, onResult: (Boolean) -> Unit) {
        if (amountMinor <= 0) return onResult(false)
        val userId = repo.currentUserId() ?: return onResult(false)
        viewModelScope.launch {
            // false = no shared record with this seller, so nothing was sent; the screen
            // reports that rather than claiming success.
            onResult(repo.initiatePayment(userId, sellerId, amountMinor))
        }
    }
}
