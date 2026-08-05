package com.example.app_mobile.ui.sellerdetail

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
import com.example.app_mobile.databinding.FragmentSellerDetailBinding
import com.example.app_mobile.util.toTlString
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import kotlin.math.roundToLong

/**
 * The buyer's history with one seller + a [Ödeme Yap] button that starts a payment.
 * Mirrors app-pos's CustomerDetailFragment (the ViewModelProvider.Factory pattern
 * for a constructor arg).
 */
class SellerDetailFragment : Fragment() {

    private var _binding: FragmentSellerDetailBinding? = null
    private val binding get() = _binding!!

    private val args: SellerDetailFragmentArgs by navArgs()

    private val viewModel: SellerDetailViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                SellerDetailViewModel(args.sellerId) as T
        }
    }

    private val adapter = TransactionAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSellerDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.detailShopName.text = args.shopName

        binding.transactionList.layoutManager = LinearLayoutManager(requireContext())
        binding.transactionList.adapter = adapter

        setupFilters()
        binding.btnPay.setOnClickListener { showPayDialog() }
        observeShopPhone()
        observeBalance()
        observeTransactions()
    }

    private fun observeShopPhone() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.shopPhone.collect { phone ->
                    binding.detailShopPhone.text = phone
                    // A shop may not have set a number; hide the row rather than
                    // leaving an empty line under the name.
                    binding.detailShopPhone.visibility =
                        if (phone.isBlank()) View.GONE else View.VISIBLE
                }
            }
        }
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

    /** A single amount field; the value is parsed as lira and stored as kuruş. */
    private fun showPayDialog() {
        val input = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = getString(R.string.pay_dialog_hint)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.pay_dialog_title)
            .setView(input)
            .setPositiveButton(R.string.pay_dialog_positive) { _, _ ->
                val lira = input.text.toString().replace(',', '.').toDoubleOrNull() ?: return@setPositiveButton
                val amountMinor = (lira * 100).roundToLong()
                if (amountMinor > 0) {
                    // Only report success once the request actually went out. The callback
                    // arrives from viewModelScope, so guard against the view being gone.
                    viewModel.pay(amountMinor) { sent ->
                        val ctx = context ?: return@pay
                        val msg = if (sent) R.string.pay_done else R.string.pay_no_record
                        Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(R.string.pay_dialog_negative, null)
            .show()
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
