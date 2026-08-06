package com.example.app_pos.ui.dashboard.detail

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.app_pos.R
import com.example.app_pos.databinding.ItemTransactionBinding
import com.example.app_pos.model.Transaction
import com.example.app_pos.model.TransactionType
import com.example.app_pos.util.toDisplayDateTime
import com.example.app_pos.util.toTlString

/** Renders the ledger entries of a single customer, newest first. */
class TransactionAdapter :
    ListAdapter<Transaction, TransactionAdapter.TransactionViewHolder>(DIFF) {

    class TransactionViewHolder(
        private val binding: ItemTransactionBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(transaction: Transaction) = with(binding) {
            txDescription.text = transaction.description
            // Stored as ISO-8601 UTC; shown in the device's time zone.
            txDate.text = transaction.createdAt.toDisplayDateTime()

            // The stored amount is always positive; the sign shown comes from
            // the entry type, mirroring how the balance is summed.
            val isDebt = transaction.type == TransactionType.DEBT
            val prefix = if (isDebt) "+" else "-"
            txAmount.text = prefix + transaction.amountMinor.toTlString()

            val colorRes = if (isDebt) R.color.balance_due else R.color.payment_received
            txAmount.setTextColor(root.context.getColor(colorRes))
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val binding = ItemTransactionBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return TransactionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<Transaction>() {
            override fun areItemsTheSame(oldItem: Transaction, newItem: Transaction) =
                oldItem.transactionId == newItem.transactionId

            override fun areContentsTheSame(oldItem: Transaction, newItem: Transaction) =
                oldItem == newItem
        }
    }
}
