package com.example.app_pos.ui.dashboard.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.app_pos.MainActivity
import com.example.app_pos.R
import com.example.app_pos.databinding.FragmentProfileBinding
import com.example.app_pos.model.User
import kotlinx.coroutines.launch

/**
 * "Profil" tab: the signed-in merchant's own account, read-only (editing is on
 * app-mobile). Renders two states — NotPaired (offer to pair this terminal) and
 * Ready (paired) — plus logout. The login gate means there is no logged-out case.
 */
class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ProfileViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnPair.setOnClickListener {
            findNavController().navigate(R.id.action_profile_to_pairing)
        }
        binding.btnLogout.setOnClickListener {
            viewModel.logout()
            // login lives in the outer graph; go there via the activity (see
            // MainActivity.navigateToLogin), not the dashboard's inner controller.
            (activity as? MainActivity)?.navigateToLogin()
        }
        binding.btnEditName.setOnClickListener {
            showEditDialog(R.string.profile_edit_name_title, currentName) { viewModel.updateDisplayName(it) }
        }
        binding.btnEditShop.setOnClickListener {
            showEditDialog(R.string.profile_edit_shop_title, currentShopName) { viewModel.updateShopName(it) }
        }
        observeState()
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is ProfileUiState.NotPaired -> {
                            bindUser(state.user)
                            binding.pairCard.visibility = View.VISIBLE
                            binding.pairedText.visibility = View.GONE
                        }
                        is ProfileUiState.Ready -> {
                            bindUser(state.user)
                            binding.pairCard.visibility = View.GONE
                            binding.pairedText.visibility = View.VISIBLE
                        }
                        null -> Unit // brief logout transition; leave as is
                    }
                }
            }
        }
    }

    // Latest editable values, so the dialog can prefill with what is there now.
    private var currentName: String = ""
    private var currentShopName: String = ""

    private fun bindUser(user: User) {
        currentName = user.displayName
        currentShopName = user.sellerInfo?.shopName.orEmpty()

        // Name and shop are always shown (both are editable). Empty reads as a
        // placeholder rather than a blank line, so the "not set" state is clear.
        binding.displayNameText.text =
            user.displayName.ifEmpty { getString(R.string.profile_name_empty) }
        binding.shopNameText.text =
            currentShopName.ifEmpty { getString(R.string.profile_shop_empty) }

        binding.phoneText.text = user.phone
        binding.rolesText.text = rolesLabel(user)

        // E-mail row hides when absent, so an empty label never shows.
        binding.emailRow.visibility = if (user.email != null) View.VISIBLE else View.GONE
        user.email?.let { binding.emailText.text = it }
    }

    /** One-field edit dialog, prefilled; saves only a non-blank value. */
    private fun showEditDialog(titleRes: Int, current: String, onSave: (String) -> Unit) {
        val input = com.google.android.material.textfield.TextInputEditText(requireContext()).apply {
            setText(current)
            setSelection(text?.length ?: 0)
        }
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle(titleRes)
            .setView(input)
            .setPositiveButton(R.string.dialog_save) { _, _ ->
                val value = input.text?.toString()?.trim().orEmpty()
                if (value.isNotEmpty()) onSave(value)
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    /** Both roles can be active, so join whichever are set (e.g. "Alıcı, Satıcı"). */
    private fun rolesLabel(user: User): String {
        val roles = buildList {
            if (user.isBuyer) add(getString(R.string.role_buyer))
            if (user.isSeller) add(getString(R.string.role_seller))
        }
        return roles.joinToString(", ")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
