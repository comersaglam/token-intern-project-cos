package com.example.app_pos.model

/**
 * Whether this customer record is linked to a real user account.
 *
 * UNCLAIMED: created by the merchant with just a name; the customer has no app.
 *            Most customers start out this way in the real world.
 * CLAIMED:   the customer signed in on app-mobile with their phone number, so
 *            the record is now bound to their account.
 */
enum class ClaimStatus { UNCLAIMED, CLAIMED }

/**
 * A credit (veresiye) customer.
 *
 * NOTE: balanceMinor is not stored anywhere — it is DERIVED from the customer's
 * ledger entries (append-only rule: a balance is never kept as a single mutable
 * number). FakeRepository computes it by summing the transactions.
 */
data class Customer(
    val customerId: String,
    val displayName: String,
    val phone: String?,              // null = UNCLAIMED customer (phone unknown)
    val claimStatus: ClaimStatus,
    val balanceMinor: Long           // in minor units (kuruş); positive = owes money
)
