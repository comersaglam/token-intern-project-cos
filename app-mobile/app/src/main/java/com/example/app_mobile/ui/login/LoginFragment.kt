package com.example.app_mobile.ui.login

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.app_mobile.MainActivity
import com.example.app_mobile.R
import com.example.app_mobile.databinding.FragmentLoginBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/**
 * The login gate — the app's start destination. The customer cannot reach the
 * dashboard until signed in (see nav_graph startDestination). Mock OTP for now;
 * the real flow adds a code screen without changing this one.
 */
class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LoginViewModel by viewModels()

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
                viewModel.state.collect { renderState(it) }
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
                (activity as? MainActivity)?.onLoginSucceeded()
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

    private fun showRegisterDialog() {
        if (registerDialog?.isShowing == true) return
        val phone = viewModel.pendingPhoneDisplay ?: return
        registerDialog = MaterialAlertDialogBuilder(requireContext())
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
