package com.example.app_pos.ui.dashboard.detail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.core.os.bundleOf
import androidx.navigation.Navigation
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.app_pos.R
import com.example.app_pos.data.FakeRepository
import com.example.app_pos.databinding.FragmentCustomerDetailBinding
import com.example.app_pos.util.toTlString
import kotlinx.coroutines.launch

/**
 * Third screen: one customer's ledger history and current balance.
 */
class CustomerDetailFragment : Fragment() {

    private var _binding: FragmentCustomerDetailBinding? = null
    private val binding get() = _binding!!

    // Safe Args reads the typed arguments declared in nav_graph.xml.
    private val args: CustomerDetailFragmentArgs by navArgs()

    // This ViewModel needs a constructor argument, so the default factory
    // cannot build it — we supply one that knows how.
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

    // The customer's phone: identity for the pay flow and how the merchant tells
    // two same-named customers apart. Read once here.
    private val phone: String by lazy {
        FakeRepository.findCustomerById(args.customerId)?.phone.orEmpty()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.detailName.text = args.customerName
        binding.detailPhone.text = phone

        setupList()
        setupFilters()
        setupPayButton()
        observeBalance()
        observeTransactions()
    }

    /**
     * Opens the sale flow as a PAYMENT for this customer.
     *
     * The keypad lives in the OUTER nav graph (the dashboard has its own inner
     * one), so we navigate through the activity's host. We target the destination
     * directly with a Bundle rather than a global action: a global action to a
     * nested destination couldn't be resolved from dashboardFragment and crashed.
     * Phone travels as the identity the OTP step needs.
     */
    private fun setupPayButton() {
        binding.btnPay.setOnClickListener {
            // Enter saleFlow (which starts at the keypad); args are forwarded to
            // its start destination. amountMinor is omitted (0) so the keypad
            // stays open for a payment.
            val payArgs = bundleOf(
                "payCustomerId" to args.customerId,
                "payCustomerName" to args.customerName,
                "payCustomerPhone" to phone
            )
            Navigation.findNavController(requireActivity(), R.id.navHostFragment)
                .navigate(R.id.action_global_pay, payArgs)
        }
    }

    private fun observeBalance() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.balanceMinor.collect { balance ->
                    binding.detailBalance.text = balance.toTlString()
                    // Same convention as the list: red while a debt is outstanding
                    // (>0), green once settled or overpaid (<=0).
                    val balanceColor =
                        if (balance > 0) R.color.balance_due else R.color.payment_received
                    binding.detailBalance.setTextColor(requireContext().getColor(balanceColor))
                }
            }
        }
    }

    private fun setupList() {
        binding.transactionList.layoutManager = LinearLayoutManager(requireContext())
        binding.transactionList.adapter = adapter
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

    private fun observeTransactions() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.transactions.collect(adapter::submitList)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.transactionList.adapter = null
        _binding = null
    }
}
