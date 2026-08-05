package com.example.app_pos.ui.dashboard.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.app_pos.data.RepositoryProvider
import com.example.app_pos.model.User
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * What the profile screen shows. The gate guarantees the merchant is signed in by
 * the time this screen is reachable, so there is no "not logged in" case — only
 * whether the terminal is paired yet.
 */
sealed interface ProfileUiState {
    /** Signed in, but this terminal is not paired to the app account yet. */
    data class NotPaired(val user: User) : ProfileUiState

    /** Signed in and paired — the steady state. */
    data class Ready(val user: User) : ProfileUiState
}

class ProfileViewModel : ViewModel() {

    private val repo = RepositoryProvider.instance

    val uiState: StateFlow<ProfileUiState?> =
        combine(
            repo.observeCurrentUser(),
            repo.isPairedWithApp
        ) { user, paired ->
            // user is non-null here (the gate ensures a session). Null would only
            // occur during the brief logout→login transition; map it to null state
            // so the screen simply shows nothing until it leaves.
            when {
                user == null -> null
                !paired -> ProfileUiState.NotPaired(user)
                else -> ProfileUiState.Ready(user)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Ends the session; the caller then navigates back to the login gate. The repo
     *  call is suspend now (persisted state), so it runs in viewModelScope; the screen
     *  navigates on its own state change, so it does not need to await this. */
    fun logout() {
        viewModelScope.launch { repo.logout() }
    }

    fun updateDisplayName(name: String) {
        currentUserId()?.let { id -> viewModelScope.launch { repo.updateDisplayName(id, name) } }
    }

    fun updateShopName(name: String) {
        currentUserId()?.let { id -> viewModelScope.launch { repo.updateShopName(id, name) } }
    }

    /** The signed-in user's id, read from the latest state (both cases carry a user). */
    private fun currentUserId(): String? =
        when (val s = uiState.value) {
            is ProfileUiState.NotPaired -> s.user.userId
            is ProfileUiState.Ready -> s.user.userId
            null -> null
        }
}
