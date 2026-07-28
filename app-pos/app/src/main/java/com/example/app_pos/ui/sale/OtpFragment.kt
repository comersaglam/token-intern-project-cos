package com.example.app_pos.ui.sale

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import com.example.app_pos.MainActivity
import com.example.app_pos.R
import com.example.app_pos.data.FakeRepository
import com.example.app_pos.databinding.FragmentOtpBinding
import com.example.app_pos.model.ClaimStatus
import com.example.app_pos.model.TransactionType
import com.example.app_pos.util.toTlString
import kotlinx.coroutines.launch

/**
 * Customer-approval step. Requests an OTP on entry and, once the merchant enters
 * the code, verifies it and — only on success — writes the entry. This is the
 * gate that stops the merchant booking anything without the customer's approval.
 * Verification is mocked (any code) until the backend lands.
 */
class OtpFragment : Fragment() {

    private var _binding: FragmentOtpBinding? = null
    private val binding get() = _binding!!

    private val saleViewModel: SaleViewModel by navGraphViewModels(R.id.saleFlow)
    private val viewModel: OtpViewModel by viewModels()

    // Whether the customer has the app: routes app-push vs SMS (both mocked). A
    // new customer has no app; an existing one is looked up by phone.
    private val hasApp: Boolean
        get() {
            val sel = saleViewModel.selectedCustomer.value ?: return false
            if (sel.isNew) return false
            return FakeRepository.findCustomerByPhone(sel.phone)?.claimStatus == ClaimStatus.CLAIMED
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOtpBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        installCancelGuard()
        val phone = saleViewModel.selectedCustomer.value?.phone.orEmpty()
        binding.otpPrompt.text = getString(R.string.otp_prompt, phone)
        binding.btnVerify.setOnClickListener { onVerify() }
        observeStatus()
        // Kick off the request once (the ViewModel survives config changes).
        if (savedInstanceState == null) viewModel.sendOtp(phone, hasApp)
    }

    private fun observeStatus() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.status.collect(::renderStatus)
            }
        }
    }

    private fun renderStatus(status: OtpStatus) = with(binding) {
        when (status) {
            OtpStatus.SENDING -> {
                statusText.visibility = View.VISIBLE
                statusText.setText(R.string.otp_sending)
                btnVerify.isEnabled = false
            }
            OtpStatus.READY -> {
                statusText.visibility = View.GONE
                btnVerify.isEnabled = true
            }
            OtpStatus.VERIFYING -> {
                statusText.visibility = View.VISIBLE
                statusText.setText(R.string.otp_verifying)
                btnVerify.isEnabled = false
            }
            OtpStatus.ERROR -> {
                statusText.visibility = View.GONE
                btnVerify.isEnabled = true
                Toast.makeText(requireContext(), R.string.msg_otp_failed, Toast.LENGTH_SHORT).show()
            }
            OtpStatus.DONE -> Unit // handled in the write callback
        }
    }

    private fun onVerify() {
        val sel = saleViewModel.selectedCustomer.value ?: return
        val code = binding.codeInput.text?.toString()?.trim().orEmpty()
        val amount = saleViewModel.amountMinor.value
        val type = saleViewModel.txType
        viewModel.verifyAndWrite(
            phone = sel.phone,
            code = code,
            hasApp = hasApp,
            isNew = sel.isNew,
            displayName = sel.displayName,
            knownCustomerId = sel.customerId,
            amountMinor = amount,
            type = type,
        ) { onWritten(sel.displayName, amount, type) }
    }

    private fun onWritten(name: String, amount: Long, type: TransactionType) {
        val msg = when (type) {
            TransactionType.DEBT -> getString(R.string.msg_credit_written, name, amount.toTlString())
            TransactionType.PAYMENT -> getString(R.string.msg_payment_written, name, amount.toTlString())
        }
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        // A DEBT may have come from the payment app (hand back to it); a PAYMENT
        // always started inside app-pos, so it never hands back. If we don't
        // finish, go to the dashboard.
        val isHandoffFlow = type == TransactionType.DEBT
        val finished = (activity as? MainActivity)?.finishCreditHandoff(isHandoffFlow) ?: false
        if (!finished) {
            findNavController().navigate(R.id.action_global_dashboard)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
