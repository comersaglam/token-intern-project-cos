package com.example.app_pos.data

import kotlinx.coroutines.delay

/**
 * Confirms a customer really agrees to a veresiye (credit) or payment entry
 * before it is written — the merchant must not be able to book debt unilaterally.
 *
 * MOCK for now: there is no backend, so requestOtp pretends a code was sent and
 * verifyOtp accepts anything. The point is that the SIGNATURES are already the
 * ones the real service will use, so wiring a backend later (FAZ 4/5) changes
 * only the bodies here — the confirm/OTP screens and the sale pipeline stay put.
 *
 * The two customer cases are already branched, even though both currently mock:
 *  - has the app (CLAIMED): approval will go through the app itself (push).
 *  - no app (UNCLAIMED): approval will be an SMS OTP to the phone.
 */
object OtpService {

    /**
     * Asks the customer to approve the pending entry.
     *
     * @param hasApp whether the customer has the mobile app (CLAIMED). Real impl
     *   will send an in-app push when true, an SMS code when false. Mock: true.
     */
    suspend fun requestOtp(phone: String, hasApp: Boolean): Boolean {
        // MOCK — pretend the request went out. TODO(FAZ 4/5): call backend.
        //  - hasApp  -> backend sends an approval push to the customer's app
        //  - !hasApp -> backend sends an SMS OTP to `phone`
        delay(300) // stand-in for the network round trip, so the UI shows progress
        return true
    }

    /**
     * Verifies the code (or app approval) the customer gave.
     *
     * Mock: accepts any code. TODO(FAZ 4/5): verify against the backend; only a
     * genuine approval returns true.
     */
    suspend fun verifyOtp(phone: String, code: String, hasApp: Boolean): Boolean {
        // MOCK — always approve. TODO(FAZ 4/5): real verification.
        delay(300)
        return true
    }
}
