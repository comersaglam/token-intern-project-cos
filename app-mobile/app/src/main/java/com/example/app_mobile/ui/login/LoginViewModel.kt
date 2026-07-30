package com.example.app_mobile.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.app_mobile.data.FakeRepository
import com.example.app_mobile.data.OtpService
import com.example.app_mobile.util.PhoneFormat
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
 * The customer sign-in — telefon + OTP (mock).
 *
 * A registered phone signs in; an unknown but valid number offers registration
 * (as a BUYER — the whole difference from app-pos, which registers a seller). The
 * OTP request/verify is mocked (OtpService) but the calls are real, so the backend
 * slots in later without changing this screen. On success the account CLAIMS any
 * merchant records for its number (inherits old debt).
 */
class LoginViewModel : ViewModel() {

    private val _state = MutableStateFlow(LoginState.IDLE)
    val state: StateFlow<LoginState> = _state.asStateFlow()

    private var pendingPhone: String? = null
    val pendingPhoneDisplay: String? get() = pendingPhone

    /** Registered phone → sign in; valid-but-unknown → register; invalid → error. */
    fun login(phone: String?) {
        val stored = phone?.let { PhoneFormat.toStored(it) }
        if (stored == null) {
            _state.value = LoginState.ERROR
            return
        }
        _state.value = LoginState.SUBMITTING
        viewModelScope.launch {
            // Mock OTP round trip (backend-ready). Real flow adds a code-entry screen.
            OtpService.requestOtp(stored)
            OtpService.verifyOtp(stored, code = "000000")
            if (FakeRepository.findUserByPhone(stored) != null) {
                _state.value = if (signIn(stored)) LoginState.SUCCESS else LoginState.ERROR
            } else {
                pendingPhone = stored
                _state.value = LoginState.NEEDS_REGISTER
            }
        }
    }

    /** Confirms registration of the pending number: create a BUYER, then sign in. */
    fun register() {
        val phone = pendingPhone ?: return
        _state.value = LoginState.SUBMITTING
        viewModelScope.launch {
            // app-mobile registers a buyer (isSeller = false); name fills in from profile.
            FakeRepository.registerUser(phone, displayName = "", isSeller = false)
            signIn(phone)
            pendingPhone = null
            _state.value = LoginState.SUCCESS
        }
    }

    fun cancelRegister() {
        pendingPhone = null
        _state.value = LoginState.IDLE
    }

    /** Opens the session and claims this number's merchant records (old debt). */
    private fun signIn(phone: String): Boolean {
        val ok = FakeRepository.login(phone)
        if (ok) {
            FakeRepository.currentUserId()?.let { userId ->
                FakeRepository.claimCustomerForUser(userId, phone)
            }
        }
        return ok
    }
}
