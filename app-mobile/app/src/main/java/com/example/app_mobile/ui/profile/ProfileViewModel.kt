package com.example.app_mobile.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.app_mobile.data.FakeRepository
import com.example.app_pos.model.User
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * What the profile screen shows: the signed-in user plus, for a seller, whether the
 * POS is paired yet. The login gate guarantees a session, so user is null only during
 * the brief logout→login transition.
 */
data class ProfileUiState(
    val user: User,
    val isPaired: Boolean
)

class ProfileViewModel : ViewModel() {

    val uiState: StateFlow<ProfileUiState?> =
        combine(
            FakeRepository.observeCurrentUser(),
            FakeRepository.isPairedWithApp
        ) { user, paired ->
            if (user == null) null else ProfileUiState(user, paired)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun logout() = FakeRepository.logout()

    fun updateDisplayName(name: String) {
        currentUserId()?.let { FakeRepository.updateDisplayName(it, name) }
    }

    fun updateEmail(email: String) {
        currentUserId()?.let { FakeRepository.updateEmail(it, email) }
    }

    /** "Satıcı ol": flips the account into a seller with the given shop name. */
    fun becomeSeller(shopName: String) {
        if (shopName.isBlank()) return
        currentUserId()?.let { FakeRepository.setSeller(it, shopName) }
    }

    /** Updates just the shop name (already a seller). */
    fun updateShopName(shopName: String) {
        if (shopName.isBlank()) return
        currentUserId()?.let { FakeRepository.updateShopName(it, shopName) }
    }

    private fun currentUserId(): String? = uiState.value?.user?.userId
}
