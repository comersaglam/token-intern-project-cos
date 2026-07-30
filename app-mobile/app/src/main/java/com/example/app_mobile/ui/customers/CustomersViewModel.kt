package com.example.app_mobile.ui.customers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.app_mobile.data.FakeRepository
import com.example.app_pos.model.Customer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/** Which customers the list should show. */
enum class CustomerFilter { ALL, WITH_DEBT }

/**
 * The seller's customer list (once the signed-in user is a seller). The mirror of
 * app-pos's CustomersViewModel — scoped to the signed-in user as the sellerId.
 * Search + filter apply on top of the repository Flow, so a ledger write updates
 * balances here live.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CustomersViewModel : ViewModel() {

    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(CustomerFilter.ALL)

    private val sellerCustomers =
        FakeRepository.observeCurrentUser().flatMapLatest { user ->
            if (user == null) flowOf(emptyList()) else FakeRepository.observeCustomers(user.userId)
        }

    val customers: StateFlow<List<Customer>> =
        combine(sellerCustomers, query, filter) { all, q, f ->
            all
                .filter { it.displayName.contains(q, ignoreCase = true) }
                .filter { customer ->
                    when (f) {
                        CustomerFilter.ALL -> true
                        CustomerFilter.WITH_DEBT -> customer.balanceMinor > 0
                    }
                }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val totalReceivableMinor: StateFlow<Long> =
        FakeRepository.observeCurrentUser().flatMapLatest { user ->
            if (user == null) flowOf(0L) else FakeRepository.observeTotalReceivableMinor(user.userId)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    fun onSearchChanged(newQuery: String) { query.value = newQuery }
    fun onFilterChanged(newFilter: CustomerFilter) { filter.value = newFilter }
}
