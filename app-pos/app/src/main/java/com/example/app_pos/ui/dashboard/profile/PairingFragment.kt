package com.example.app_pos.ui.dashboard.profile

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
import com.example.app_pos.R
import com.example.app_pos.databinding.FragmentPairingBinding
import kotlinx.coroutines.launch
import dagger.hilt.android.AndroidEntryPoint

/**
 * Mock pairing confirm screen (modelled on OtpFragment). The button pairs the
 * terminal; on success it pops back to the profile, which then shows "paired".
 *
 * NOTE: does NOT use installCancelGuard() — that guard reads the saleFlow
 * ViewModel scope, which does not exist here, so calling it would crash. Ordinary
 * Back (handled by the dashboard's inner nav) is enough.
 */
@AndroidEntryPoint
class PairingFragment : Fragment() {

    private var _binding: FragmentPairingBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PairingViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPairingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnConfirmPair.setOnClickListener { viewModel.confirmPairing() }
        observeStatus()
    }

    private fun observeStatus() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.status.collect { status ->
                    when (status) {
                        PairingStatus.IDLE -> {
                            binding.btnConfirmPair.isEnabled = true
                            binding.statusText.visibility = View.GONE
                        }
                        PairingStatus.PAIRING -> {
                            binding.btnConfirmPair.isEnabled = false
                            binding.statusText.setText(R.string.pairing_pairing)
                            binding.statusText.visibility = View.VISIBLE
                        }
                        PairingStatus.DONE -> {
                            Toast.makeText(requireContext(), R.string.msg_paired, Toast.LENGTH_SHORT)
                                .show()
                            findNavController().navigateUp()
                        }
                        PairingStatus.ERROR -> {
                            binding.btnConfirmPair.isEnabled = true
                            binding.statusText.setText(R.string.msg_pair_failed)
                            binding.statusText.visibility = View.VISIBLE
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
