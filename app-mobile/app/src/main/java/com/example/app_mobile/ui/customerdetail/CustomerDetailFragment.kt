package com.example.app_mobile.ui.customerdetail

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.app_mobile.R
import com.example.app_pos.data.RepositoryProvider
import com.example.app_mobile.databinding.FragmentCustomerDetailBinding
import com.example.app_mobile.ui.sellerdetail.TransactionAdapter
import com.example.app_pos.model.ClaimStatus
import com.example.app_pos.model.TransactionType
import com.example.app_mobile.util.toTlString
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import kotlin.math.roundToLong

/**
 * The seller's view of one customer: ledger history + balance, and two write actions
 * ([Veresiye Yaz] / [Ödeme Al]) that each open an amount popup and go through the
 * approval gate. Mirrors app-pos's CustomerDetailFragment (the factory pattern), but
 * the write is a popup instead of the keypad sale flow.
 */
class CustomerDetailFragment : Fragment() {

    private var _binding: FragmentCustomerDetailBinding? = null
    private val binding get() = _binding!!

    private val args: CustomerDetailFragmentArgs by navArgs()

    private val viewModel: CustomerDetailViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                CustomerDetailViewModel(args.customerId) as T
        }
    }

    private val adapter = TransactionAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCustomerDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.detailName.text = args.customerName

        binding.transactionList.layoutManager = LinearLayoutManager(requireContext())
        binding.transactionList.adapter = adapter

        setupFilters()
        binding.btnDebt.setOnClickListener { showAmountDialog(TransactionType.DEBT) }
        binding.btnPay.setOnClickListener { showAmountDialog(TransactionType.PAYMENT) }
        observePhone()
        observeBalance()
        observeTransactions()
    }

    private fun setupFilters() {
        binding.txFilterGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val filter = when (checkedIds.firstOrNull()) {
                R.id.chipTxDebt -> TransactionFilter.DEBT
                R.id.chipTxPayment -> TransactionFilter.PAYMENT
                else -> TransactionFilter.ALL
            }
            viewModel.onFilterChanged(filter)
        }
    }

    /** Amount popup for a veresiye/payment; lira parsed, stored as kuruş. */
    private fun showAmountDialog(type: TransactionType) {
        val input = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = getString(R.string.pay_dialog_hint)
        }
        val titleRes = if (type == TransactionType.DEBT)
            R.string.detail_write_debt else R.string.detail_take_payment
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(titleRes)
            .setView(input)
            .setPositiveButton(R.string.pay_dialog_positive) { _, _ ->
                val lira = input.text.toString().replace(',', '.').toDoubleOrNull() ?: return@setPositiveButton
                val amountMinor = (lira * 100).roundToLong()
                if (amountMinor <= 0) return@setPositiveButton
                val description = if (type == TransactionType.DEBT) "Veresiye" else "Ödeme"
                viewModel.submit(type, amountMinor, description)
                // Feedback matches the approval routing: app customer waits for approval,
                // app-less customer is written immediately.
                val msg = if (viewModel.isClaimed.value)
                    R.string.detail_approval_sent else R.string.detail_written
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.pay_dialog_negative, null)
            .show()
    }

    private fun observePhone() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.phone.collect { binding.detailPhone.text = it }
            }
        }
    }

    private fun observeBalance() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.balanceMinor.collect { balance ->
                    binding.detailBalance.text = balance.toTlString()
                    val colorRes = if (balance > 0) R.color.balance_due else R.color.payment_received
                    binding.detailBalance.setTextColor(requireContext().getColor(colorRes))
                }
            }
        }
    }

    private fun observeTransactions() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.transactions.collect { txs ->
                    adapter.submitList(txs)
                    binding.emptyView.visibility = if (txs.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.transactionList.adapter = null
        _binding = null
    }
}
