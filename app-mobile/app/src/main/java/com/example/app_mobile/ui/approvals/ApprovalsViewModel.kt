package com.example.app_mobile.ui.approvals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.app_mobile.data.FakeRepository
import com.example.app_mobile.data.PendingApproval
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/**
 * The buyer's pending approvals: veresiye/payment requests a merchant made against
 * their account, waiting to be approved or rejected.
 *
 * FOREGROUND POLLING (mock): the app is a caller, never a listener — no FCM. While
 * this screen is open the repository StateFlow re-emits, which is exactly what a
 * poll loop would observe. TODO(FAZ 4): a WorkManager background poll refreshes
 * this while the app is closed; only foreground observation exists for now.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ApprovalsViewModel : ViewModel() {

    val approvals: StateFlow<List<PendingApproval>> =
        FakeRepository.observeCurrentUser().flatMapLatest { user ->
            if (user == null) flowOf(emptyList()) else FakeRepository.observePendingApprovals(user.userId)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Approve → the entry is written to the ledger (single write point). */
    fun approve(approvalId: String) = FakeRepository.approvePending(approvalId)

    /** Reject → the request is cleared, nothing written. */
    fun reject(approvalId: String) = FakeRepository.rejectPending(approvalId)
}
