package com.example.app_pos.model

import kotlinx.coroutines.flow.Flow

/**
 * The data-layer contract, in the pure domain module so the app talks to THIS type
 * rather than to a concrete store. Swapping the in-memory FakeRepository (phase 1)
 * for the Room-backed one (phase 3) therefore changes no ViewModel.
 *
 * app-mobile is one app in TWO roles, so the surface has two reading directions over
 * the same ledger: buyer-scoped (my debts, per shop) and seller-scoped (my customers).
 * Both are additive and never conflict.
 *
 * Reads return Flow so a write refreshes every screen at once. Writes are suspend
 * (they hit the database); the session pair below is deliberately NOT — see there.
 */
interface Repository {

    // --- session / auth ---
    /**
     * Session reads stay SYNCHRONOUS on purpose: the session lives in memory (a mock
     * token, and later a cached one), not in the database. MainActivity picks the nav
     * graph's start destination from it before the graph exists, which a suspend call
     * could not do without a login flash.
     */
    fun isSessionValid(): Boolean
    fun currentUserId(): String?
    fun observeCurrentUser(): Flow<User?>
    val isPairedWithApp: Flow<Boolean>
    suspend fun login(phone: String?): Boolean
    suspend fun logout()
    suspend fun pairWithApp()

    // --- users / profile ---
    suspend fun findUserByPhone(phone: String): User?
    suspend fun registerUser(phone: String, displayName: String, isSeller: Boolean = false): User
    suspend fun setSeller(userId: String, shopName: String, shopPhone: String? = null)
    suspend fun updateShopName(userId: String, shopName: String)
    suspend fun updateDisplayName(userId: String, displayName: String)
    suspend fun updateEmail(userId: String, email: String)
    /** The shop's display name, falling back to the id when it has none yet. */
    suspend fun shopNameOf(sellerId: String): String
    /** The shop's contact number, or null when the seller has not set one. */
    suspend fun shopPhoneOf(sellerId: String): String?

    // --- claim (the bridge between a Customer record and a User account) ---
    /**
     * Links every UNCLAIMED customer record holding this phone to the user, so a
     * merchant's existing book (and its debt) is inherited on first sign-in.
     */
    suspend fun claimCustomerForUser(userId: String, phone: String): List<Customer>

    // --- customers (seller-scoped: the merchant surface) ---
    fun observeCustomers(sellerId: String): Flow<List<Customer>>
    suspend fun addCustomer(displayName: String, phone: String): String
    suspend fun findCustomerById(sellerId: String, customerId: String): Customer?
    suspend fun findCustomerByPhone(sellerId: String, phone: String): Customer?

    /**
     * What a phone number means to THIS seller, when they are about to book an entry.
     * A person is one Customer row system-wide (phone = identity), but ownership lives
     * in the ledger — so someone another shop knows is still new to this book and must
     * be reusable here rather than rejected.
     */
    suspend fun lookupCustomerForSeller(sellerId: String, phone: String): CustomerLookup

    // --- ledger (seller-scoped) ---
    fun observeTransactions(sellerId: String, customerId: String): Flow<List<Transaction>>
    fun observeTotalReceivableMinor(sellerId: String): Flow<Long>

    // --- ledger (buyer-scoped: the mirror of the reads above) ---
    /** This buyer's debts grouped by shop — one row per seller they owe. */
    fun observeMyDebtsBySeller(userId: String): Flow<List<SellerDebt>>
    fun observeMyTotalDebtMinor(userId: String): Flow<Long>
    fun observeMyTransactions(userId: String, sellerId: String): Flow<List<Transaction>>
    fun observeMyBalanceWithSeller(userId: String, sellerId: String): Flow<Long>

    // --- approvals (every write passes through here) ---
    fun observePendingApprovals(userId: String): Flow<List<PendingApproval>>
    suspend fun approvePending(approvalId: String)
    suspend fun rejectPending(approvalId: String)

    /**
     * Sends an entry for the counterparty's approval. If the customer record is
     * CLAIMED (they hold the app) a pending approval is raised and nothing is written
     * yet; if UNCLAIMED, the SMS-OTP case is mocked as approved and the entry is
     * written straight away. Either way the ledger is touched in one place.
     */
    suspend fun requestApproval(
        fromUserId: String,
        sellerId: String,
        customerId: String,
        amountMinor: Long,
        type: TransactionType,
        description: String
    )

    /**
     * A buyer pays a seller, through the same approval gate. Returns false when this
     * buyer has no record with that seller — there is no shared history to pay
     * against, so nothing is written and the screen can say so.
     */
    suspend fun initiatePayment(userId: String, sellerId: String, amountMinor: Long): Boolean

    /** Appends one entry to the ledger (append-only). Idempotent by transactionId. */
    suspend fun addTransaction(transaction: Transaction)
}
