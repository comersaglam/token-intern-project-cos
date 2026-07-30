package com.example.app_mobile.ui.customers

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.app_mobile.R
import com.example.app_mobile.databinding.FragmentCustomersBinding
import com.example.app_pos.model.Customer
import com.example.app_mobile.util.toTlString
import kotlinx.coroutines.launch

/** Müşterilerim — the seller's customer list (only reachable once isSeller). */
class CustomersFragment : Fragment() {

    private var _binding: FragmentCustomersBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CustomersViewModel by viewModels()
    private val adapter = CustomerAdapter(onClick = ::openCustomerDetail)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCustomersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.customerList.layoutManager = LinearLayoutManager(requireContext())
        binding.customerList.adapter = adapter

        binding.searchInput.doAfterTextChanged { viewModel.onSearchChanged(it?.toString().orEmpty()) }
        binding.filterGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val filter = if (checkedIds.firstOrNull() == R.id.chipWithDebt)
                CustomerFilter.WITH_DEBT else CustomerFilter.ALL
            viewModel.onFilterChanged(filter)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.customers.collect { customers ->
                        adapter.submitList(customers)
                        binding.emptyView.visibility =
                            if (customers.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    viewModel.totalReceivableMinor.collect { total ->
                        binding.totalAmount.text = total.toTlString()
                    }
                }
            }
        }
    }

    private fun openCustomerDetail(customer: Customer) {
        findNavController().navigate(
            CustomersFragmentDirections.actionCustomersToCustomerDetail(
                customerId = customer.customerId,
                customerName = customer.displayName
            )
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.customerList.adapter = null
        _binding = null
    }
}
