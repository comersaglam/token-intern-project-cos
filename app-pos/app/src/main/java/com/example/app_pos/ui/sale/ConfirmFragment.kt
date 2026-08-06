package com.example.app_pos.ui.sale

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import com.example.app_pos.R
import com.example.app_pos.databinding.FragmentConfirmBinding
import com.example.app_pos.model.Repository
import com.example.app_pos.model.TransactionType
import com.example.app_pos.util.toTlString
import kotlinx.coroutines.launch
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Summary step of the sale flow: shows who, how much, and the resulting balance
 * for either a veresiye (DEBT) or a payment (PAYMENT), then sends the entry for
 * OTP approval. The actual write happens only after the customer approves (see
 * OtpFragment) — the merchant cannot book anything unilaterally.
 */
@AndroidEntryPoint
class ConfirmFragment : Fragment() {

    private var _binding: FragmentConfirmBinding? = null
    private val binding get() = _binding!!

    private val saleViewModel: SaleViewModel by navGraphViewModels(R.id.saleFlow)

    /**
     * The one ViewModel Hilt does not build.
     *
     * Its customerId comes from ANOTHER ViewModel's runtime state, not from a navigation
     * argument, so a SavedStateHandle cannot supply it — @HiltViewModel has no way to
     * express "read this from the sale flow's shared state at construction time". The
     * factory stays, and Hilt contributes only the repository, which it injects into the
     * fragment below. Hilt-built and hand-built ViewModels coexist without friction.
     *
     * A new customer has no id yet (created at write time), so pass empty and the balance
     * is treated as zero.
     */
    @Inject lateinit var repo: Repository

    private val viewModel: ConfirmViewModel by viewModels {
        val customerId = saleViewModel.selectedCustomer.value?.customerId.orEmpty()
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ConfirmViewModel(repo, customerId) as T
        }
    }

    private val isDebt get() = saleViewModel.txType == TransactionType.DEBT

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentConfirmBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        installCancelGuard()
        bindStaticFields()
        observeBalance()
        binding.btnConfirm.setOnClickListener { onConfirm() }
        binding.btnCancel.setOnClickListener { findNavController().navigateUp() }
    }

    /** Labels and the amount reflect whether this is a debt or a payment. */
    private fun bindStaticFields() {
        binding.confirmCustomerName.text =
            saleViewModel.selectedCustomer.value?.displayName.orEmpty()
        binding.confirmAmount.text = saleViewModel.amountMinor.value.toTlString()
        binding.confirmAmountLabel.setText(
            if (isDebt) R.string.confirm_amount_debt else R.string.confirm_amount_payment
        )
        binding.btnConfirm.setText(
            if (isDebt) R.string.confirm_write_debt else R.string.confirm_write_payment
        )
    }

    /**
     * Current and resulting balance track the ledger live. A DEBT adds to the
     * balance, a PAYMENT subtracts — the sign is the only difference.
     */
    private fun observeBalance() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.currentBalanceMinor.collect { current ->
                    binding.confirmCurrentBalance.text = current.toTlString()
                    val delta = saleViewModel.amountMinor.value
                    val newBalance = if (isDebt) current + delta else current - delta
                    binding.confirmNewBalance.text = newBalance.toTlString()
                }
            }
        }
    }

    /** No write here — hand off to the OTP step, which writes on approval. */
    private fun onConfirm() {
        findNavController().navigate(R.id.action_confirm_to_otp)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
