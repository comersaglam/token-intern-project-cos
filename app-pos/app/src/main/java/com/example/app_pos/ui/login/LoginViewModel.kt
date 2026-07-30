package com.example.app_pos.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.app_pos.data.FakeRepository
import com.example.app_pos.util.PhoneFormat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Where the sign-in is in its lifecycle.
 * NEEDS_REGISTER: a valid but unregistered number — the screen asks to confirm
 * registration, then register() creates the account and signs in.
 */
enum class LoginState { IDLE, SUBMITTING, SUCCESS, ERROR, NEEDS_REGISTER }

/**
 * The merchant sign-in.
 *
 * Mock for now: [login] resolves immediately and just flips the session flag.
 * The status states already exist so the real login slots in without changing the
 * screen — SUBMITTING will hold while a real phone+OTP request is in flight, and
 * SUCCESS will fire only after the code is verified and a session token is stored.
 */
class LoginViewModel : ViewModel() {

    private val _state = MutableStateFlow(LoginState.IDLE)
    val state: StateFlow<LoginState> = _state.asStateFlow()

    // The number awaiting a register confirmation, so register() needs no argument
    // and there is a single source of truth for what is being registered.
    private var pendingPhone: String? = null

    // The registering number for the confirmation prompt. Already stored in E.164
    // (pendingPhone is set from the normalized `stored` value), so no re-conversion.
    val pendingPhoneDisplay: String? get() = pendingPhone

    /**
     * Branches on the number: a registered phone signs in; a valid but unknown
     * number asks to register (NEEDS_REGISTER); a blank/invalid number is an ERROR.
     */
    fun login(phone: String?) {
        val stored = phone?.let { PhoneFormat.toStored(it) }
        if (stored == null) {
            _state.value = LoginState.ERROR
            return
        }
        _state.value = LoginState.SUBMITTING
        viewModelScope.launch {
            if (FakeRepository.findUserByPhone(stored) != null) {
                // Only SUCCESS if the session was actually opened (guards against a
                // login that silently fails while the number looks registered).
                _state.value = if (FakeRepository.login(stored)) LoginState.SUCCESS else LoginState.ERROR
            } else {
                pendingPhone = stored
                _state.value = LoginState.NEEDS_REGISTER
            }
        }
    }

    /** Confirms registration of the pending number: create a seller, then sign in. */
    fun register() {
        val phone = pendingPhone ?: return
        _state.value = LoginState.SUBMITTING
        viewModelScope.launch {
            // app-pos registers a seller; the name is blank and filled in from profile.
            FakeRepository.registerUser(phone, displayName = "", isSeller = true)
            FakeRepository.login(phone)
            pendingPhone = null
            _state.value = LoginState.SUCCESS
        }
    }

    /** The merchant declined to register; return to the idle input state. */
    fun cancelRegister() {
        pendingPhone = null
        _state.value = LoginState.IDLE
    }
}
