package com.example.app_mobile.ui.approvals

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
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.app_mobile.R
import com.example.app_mobile.data.PendingApproval
import com.example.app_mobile.databinding.FragmentApprovalsBinding
import kotlinx.coroutines.launch

/**
 * Onaylar — pending veresiye/payment requests waiting on the buyer, each an
 * Approve/Reject card (no OTP code: the app-holding customer approves in-app).
 */
class ApprovalsFragment : Fragment() {

    private var _binding: FragmentApprovalsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ApprovalsViewModel by viewModels()
    private val adapter = ApprovalAdapter(onApprove = ::approve, onReject = ::reject)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentApprovalsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.approvalList.layoutManager = LinearLayoutManager(requireContext())
        binding.approvalList.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.approvals.collect { approvals ->
                    adapter.submitList(approvals)
                    binding.emptyView.visibility = if (approvals.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    private fun approve(approval: PendingApproval) {
        viewModel.approve(approval.approvalId)
        Toast.makeText(requireContext(), R.string.approval_approved, Toast.LENGTH_SHORT).show()
    }

    private fun reject(approval: PendingApproval) {
        viewModel.reject(approval.approvalId)
        Toast.makeText(requireContext(), R.string.approval_rejected, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.approvalList.adapter = null
        _binding = null
    }
}
