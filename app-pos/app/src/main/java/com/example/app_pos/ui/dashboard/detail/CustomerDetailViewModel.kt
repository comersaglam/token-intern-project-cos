package com.example.app_pos.ui.dashboard.detail

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

/** Which ledger entries the history should show. */
enum class TransactionFilter { ALL, DEBT, PAYMENT }

/**
 * Ledger history for one customer.
 *
 * Entries come from the repository as a Flow, so a new veresiye entry shows up
 * here live. The balance is recomputed from those entries — never read from a
 * stored field — so the append-only rule holds on every screen.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CustomerDetailViewModel(customerId: String) : ViewModel() {

    private val repo = RepositoryProvider.instance

    // The customer's phone: identity for the pay flow and how the merchant tells two
    // same-named customers apart. Looked up once (a suspend DB read) and exposed as a
    // StateFlow so the fragment stays a pure renderer. Seller-independent (only the
    // phone is used); the current seller just satisfies the lookup signature.
    val phone: StateFlow<String> =
        repo.observeCurrentUser().flatMapLatest { user ->
            flow {
                emit(
                    if (user == null) ""
                    else repo.findCustomerById(user.userId, customerId)?.phone.orEmpty()
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    // Scoped to the signed-in seller: this customer's history with THIS seller only.
    private val allTransactions =
        repo.observeCurrentUser().flatMapLatest { user ->
            if (user == null) flowOf(emptyList())
            else repo.observeTransactions(user.userId, customerId)
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

    /** Sum of the entries: DEBT adds, PAYMENT subtracts; updates on every write. */
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
}
