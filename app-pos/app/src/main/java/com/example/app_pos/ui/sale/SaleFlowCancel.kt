package com.example.app_pos.ui.sale

import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.example.app_pos.MainActivity
import com.example.app_pos.R
import com.example.app_pos.model.TransactionType

/**
 * Guards every sale-flow screen against an accidental back press.
 *
 * The amount and customer are entered with a few taps but represent real money,
 * so leaving the flow must be deliberate: back shows a confirm dialog. Confirming
 * leaves the flow — back to the payment app in a handoff, or to the dashboard
 * when app-pos was opened on its own — dismissing keeps the screen.
 *
 * Registered on viewLifecycleOwner so it is active only while the view is shown.
 */
fun Fragment.installCancelGuard() {
    val callback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.cancel_dialog_title)
                .setMessage(R.string.cancel_dialog_message)
                .setNegativeButton(R.string.cancel_dialog_dismiss, null)
                .setPositiveButton(R.string.cancel_dialog_confirm) { _, _ -> leaveFlow() }
                .show()
        }
    }
    requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, callback)
}

/**
 * Ends the sale: hands back to the payment app only for a DEBT that came from it,
 * otherwise leaves the sale flow for the dashboard (same rule the write step uses).
 */
private fun Fragment.leaveFlow() {
    // Read the flow-scoped SaleViewModel to tell a handoff DEBT from an in-app
    // PAYMENT (delegates can't be used in a plain function, so resolve it by hand).
    val owner = findNavController().getViewModelStoreOwner(R.id.saleFlow)
    val saleViewModel = ViewModelProvider(owner)[SaleViewModel::class.java]
    val isHandoffFlow = saleViewModel.txType == TransactionType.DEBT
    val finished = (activity as? MainActivity)?.finishCreditHandoff(isHandoffFlow) ?: false
    if (!finished) {
        findNavController().navigate(R.id.action_global_dashboard)
    }
}
