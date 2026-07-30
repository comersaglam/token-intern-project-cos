package com.example.app_mobile.data

import kotlinx.coroutines.delay

/**
 * Confirms the customer's phone at sign-in via a one-time code.
 *
 * MOCK for now: there is no backend, so requestOtp pretends a code was sent and
 * verifyOtp accepts anything. The SIGNATURES match the real service, so wiring a
 * backend later (FAZ 4/5) changes only the bodies here — the login/OTP screens
 * stay put.
 *
 * NOTE: app-mobile's *approval* of a merchant's pending veresiye/payment does NOT
 * go through an OTP code (that is the app-holding customer's push case — handled
 * as an in-app Approve/Reject card, see FakeRepository.approvePending). OTP here
 * is only the sign-in factor.
 */
object OtpService {

    /** Asks the backend to send a sign-in code to `phone`. Mock: pretends it sent. */
    suspend fun requestOtp(phone: String): Boolean {
        // MOCK — pretend the SMS went out. TODO(FAZ 4/5): call backend.
        delay(300) // stand-in for the network round trip, so the UI shows progress
        return true
    }

    /** Verifies the sign-in code. Mock: accepts any code. */
    suspend fun verifyOtp(phone: String, code: String): Boolean {
        // MOCK — always approve. TODO(FAZ 4/5): real verification.
        delay(300)
        return true
    }
}
