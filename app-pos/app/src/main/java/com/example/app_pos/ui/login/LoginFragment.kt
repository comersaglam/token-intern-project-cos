package com.example.app_pos.ui.login

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
import com.example.app_pos.databinding.FragmentLoginBinding
import kotlinx.coroutines.launch
import dagger.hilt.android.AndroidEntryPoint

/**
 * The login gate — the app's start destination. The merchant cannot reach the
 * dashboard until signed in (see nav_graph startDestination).
 *
 * Mock for now: the button signs in regardless of the phone field. Real login
 * later adds an OTP step; this same screen stays, driven by LoginViewModel.state.
 */
@AndroidEntryPoint
class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LoginViewModel by viewModels()

    // Guards against re-showing the register dialog on every re-emit / config change.
    private var registerDialog: androidx.appcompat.app.AlertDialog? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnLogin.setOnClickListener {
            val phone = binding.phoneInput.text?.toString()?.trim().orEmpty()
            viewModel.login(phone.ifEmpty { null })
        }
        observeState()
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    renderState(state)
                }
            }
        }
    }

    private fun renderState(state: LoginState) {
        when (state) {
            LoginState.IDLE -> {
                binding.btnLogin.isEnabled = true
                binding.statusText.visibility = View.GONE
            }
            LoginState.SUBMITTING -> {
                binding.btnLogin.isEnabled = false
                binding.statusText.visibility = View.GONE
            }
            LoginState.SUCCESS -> {
                // If a CREDIT handoff was waiting, the activity resumes the sale flow
                // and handles navigation; otherwise go to the dashboard (dropping the
                // gate from the back stack so Back exits the app).
                val handled = (activity as? MainActivity)?.onLoginSucceeded() == true
                if (!handled) {
                    findNavController().navigate(R.id.action_global_dashboard_after_login)
                }
            }
            LoginState.NEEDS_REGISTER -> {
                binding.btnLogin.isEnabled = true
                binding.statusText.visibility = View.GONE
                showRegisterDialog()
            }
            LoginState.ERROR -> {
                binding.btnLogin.isEnabled = true
                binding.statusText.setText(R.string.msg_login_wrong_number)
                binding.statusText.visibility = View.VISIBLE
            }
        }
    }

    /** Confirmation for registering an unknown number; already-showing = no-op. */
    private fun showRegisterDialog() {
        if (registerDialog?.isShowing == true) return
        val phone = viewModel.pendingPhoneDisplay ?: return
        registerDialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.register_confirm_title)
            .setMessage(getString(R.string.register_confirm_message, phone))
            .setPositiveButton(R.string.register_confirm_positive) { _, _ -> viewModel.register() }
            .setNegativeButton(R.string.register_confirm_negative) { _, _ -> viewModel.cancelRegister() }
            .setOnCancelListener { viewModel.cancelRegister() }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        registerDialog?.dismiss()
        registerDialog = null
        _binding = null
    }
}
