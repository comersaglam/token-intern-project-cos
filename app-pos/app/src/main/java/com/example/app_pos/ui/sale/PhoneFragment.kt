package com.example.app_pos.ui.sale

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.navigation.navGraphViewModels
import com.example.app_pos.R
import com.example.app_pos.data.FakeRepository
import com.example.app_pos.databinding.FragmentPhoneBinding
import com.example.app_pos.util.PhoneFormat

/**
 * New-customer step: capture name + phone, then continue to confirm.
 *
 * Phone is the system's identity for a customer, so it is required and must be
 * unique — a duplicate is rejected here rather than creating a second record for
 * the same number. Only reached for a brand-new customer; picking an existing one
 * goes straight to confirm.
 */
class PhoneFragment : Fragment() {

    private var _binding: FragmentPhoneBinding? = null
    private val binding get() = _binding!!

    private val args: PhoneFragmentArgs by navArgs()
    private val saleViewModel: SaleViewModel by navGraphViewModels(R.id.saleFlow)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPhoneBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        installCancelGuard()
        // Show the expected phone shape as a placeholder inside the field.
        binding.phoneLayout.placeholderText = PhoneFormat.HINT
        // The name typed in the search box seeds the field, still editable here.
        if (savedInstanceState == null) {
            binding.nameInput.setText(args.initialName)
        }
        binding.btnContinue.setOnClickListener { onContinue() }
    }

    private fun onContinue() {
        val name = binding.nameInput.text?.toString()?.trim().orEmpty()
        val rawPhone = binding.phoneInput.text?.toString()?.trim().orEmpty()

        if (name.isEmpty()) {
            binding.nameLayout.error = getString(R.string.phone_name_hint)
            return
        }
        binding.nameLayout.error = null

        // Normalize to the stored "+90…" form; null means the format is wrong.
        val phone = PhoneFormat.toStored(rawPhone)
        if (phone == null) {
            binding.phoneLayout.error = getString(R.string.msg_phone_invalid)
            return
        }
        // Compare in the same canonical form so 0555… and +90555… match.
        if (FakeRepository.customerPhoneExists(phone)) {
            binding.phoneLayout.error = getString(R.string.msg_phone_exists)
            return
        }
        binding.phoneLayout.error = null

        // Not created yet — the write happens after OTP. customerId is empty; the
        // record is created (addCustomer) at write time. isNew = true. Phone is
        // stored canonical (+90…).
        saleViewModel.onCustomerSelected(
            customerId = "",
            displayName = name,
            phone = phone,
            isNew = true
        )
        findNavController().navigate(R.id.action_phone_to_confirm)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
