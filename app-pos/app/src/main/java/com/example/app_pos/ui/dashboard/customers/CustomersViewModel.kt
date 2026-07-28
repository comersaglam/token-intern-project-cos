package com.example.app_pos.ui.dashboard.customers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.app_pos.data.FakeRepository
import com.example.app_pos.model.Customer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Which customers the list should show. */
enum class CustomerFilter { ALL, WITH_DEBT }

/**
 * Everything the dashboard's customer tab needs: the visible customers, the
 * total receivable, and the current search/filter selection.
 *
 * The customer list comes from the repository as a Flow, so a ledger write (new
 * veresiye entry) updates the balances here live. Search and filter are applied
 * on top of that Flow — in the ViewModel, not the fragment — so the screen stays
 * a pure renderer and this logic stays unit-testable without Android.
 */
class CustomersViewModel : ViewModel() {

    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(CustomerFilter.ALL)

    val customers: StateFlow<List<Customer>> =
        combine(FakeRepository.observeCustomers(), query, filter) { all, q, f ->
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
        FakeRepository.observeTotalReceivableMinor()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    fun onSearchChanged(newQuery: String) {
        query.value = newQuery
    }

    fun onFilterChanged(newFilter: CustomerFilter) {
        filter.value = newFilter
    }
}
