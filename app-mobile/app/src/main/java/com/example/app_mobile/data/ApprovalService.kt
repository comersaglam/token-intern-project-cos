package com.example.app_mobile.data

import kotlinx.coroutines.delay

/**
 * Sends a veresiye/payment entry to the backend for the counterparty's approval —
 * the merchant must not book debt unilaterally, and the same rule holds when a
 * buyer initiates a payment (the seller confirms receipt).
 *
 * MOCK for now: there is no backend, so this pretends the request went out. The
 * SIGNATURE is what the real service will use, so wiring a backend later (FAZ 4/5)
 * changes only the body — the callers (seller write popup, buyer payment) stay put.
 *
 * The two counterparty cases are branched by the CALLER (FakeRepository.requestApproval):
 *  - has the app (CLAIMED): a PendingApproval lands in their Onaylar tab (in-app push).
 *  - no app (UNCLAIMED): an SMS OTP would be sent; mocked as auto-approved, so the
 *    entry is written immediately.
 */
object ApprovalService {

    /** Pretends to POST an approval request to the backend. Mock: always accepted. */
    suspend fun requestApproval(
        fromUserId: String,
        toPhone: String,
        amountMinor: Long,
        typeName: String
    ): Boolean {
        // MOCK — stand-in for the network round trip so the UI can show progress.
        // TODO(FAZ 4/5): POST to the backend, which routes an in-app push (has app)
        // or an SMS OTP (no app) to `toPhone` and reports the counterparty's decision.
        delay(300)
        return true
    }
}
