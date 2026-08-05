package com.example.app_pos.ui.dashboard.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.app_pos.data.RepositoryProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Where the pairing is in its confirm → done lifecycle. */
enum class PairingStatus { IDLE, PAIRING, DONE, ERROR }

/**
 * Pairs this POS terminal to the merchant's app account.
 *
 * Mock for now: a short delay stands in for the network round trip, then the flag
 * is flipped. Real QR/NFC pairing (phase 8) replaces the delay with an actual
 * handshake; the flag write (repo.pairWithApp) and this screen stay.
 * No separate PairingService — a one-step mock does not need its own data object.
 */
class PairingViewModel : ViewModel() {

    private val repo = RepositoryProvider.instance
    private val _status = MutableStateFlow(PairingStatus.IDLE)
    val status: StateFlow<PairingStatus> = _status.asStateFlow()

    fun confirmPairing() {
        _status.value = PairingStatus.PAIRING
        viewModelScope.launch {
            delay(300) // stand-in for the pairing round trip
            repo.pairWithApp()
            _status.value = PairingStatus.DONE
        }
    }
}
