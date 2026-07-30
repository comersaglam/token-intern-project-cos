package com.example.app_mobile.ui.approvals

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.app_mobile.R
import com.example.app_mobile.data.PendingApproval
import com.example.app_mobile.databinding.ItemApprovalBinding
import com.example.app_pos.model.TransactionType
import com.example.app_mobile.util.toTlString

/** Renders each pending approval as an Approve/Reject card. */
class ApprovalAdapter(
    private val onApprove: (PendingApproval) -> Unit,
    private val onReject: (PendingApproval) -> Unit
) : ListAdapter<PendingApproval, ApprovalAdapter.VH>(DIFF) {

    class VH(
        private val binding: ItemApprovalBinding,
        private val onApprove: (PendingApproval) -> Unit,
        private val onReject: (PendingApproval) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(approval: PendingApproval) = with(binding) {
            approvalShop.text = approval.shopName
            val kindRes = if (approval.type == TransactionType.DEBT)
                R.string.approval_debt_label else R.string.approval_payment_label
            approvalKind.text = root.context.getString(kindRes)
            approvalAmount.text = approval.amountMinor.toTlString()
            approvalDescription.text = approval.description
            btnApprove.setOnClickListener { onApprove(approval) }
            btnReject.setOnClickListener { onReject(approval) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemApprovalBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding, onApprove, onReject)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<PendingApproval>() {
            override fun areItemsTheSame(oldItem: PendingApproval, newItem: PendingApproval) =
                oldItem.approvalId == newItem.approvalId

            override fun areContentsTheSame(oldItem: PendingApproval, newItem: PendingApproval) =
                oldItem == newItem
        }
    }
}
