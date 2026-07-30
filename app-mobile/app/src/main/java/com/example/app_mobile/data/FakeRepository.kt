package com.example.app_mobile.data

import com.example.app_mobile.util.PhoneFormat
import com.example.app_pos.model.ClaimStatus
import com.example.app_pos.model.Customer
import com.example.app_pos.model.SellerInfo
import com.example.app_pos.model.Transaction
import com.example.app_pos.model.TransactionType
import com.example.app_pos.model.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * In-memory stand-in for the real data layer.
 *
 * app-mobile is a single app with two roles: a BUYER (default) and, once "Satıcı ol"
 * is used, a SELLER. So this repository carries BOTH read directions off the one
 * append-only ledger:
 *  - buyer-scoped: "my debts across every seller I owe" (observeMyDebtsBySeller …)
 *  - seller-scoped: "my customers" (observeCustomers … — the app-pos merchant reads,
 *    now keyed by the signed-in user's own id as the sellerId).
 * The ledger is the source of truth; balances are DERIVED, never stored. Exposed as
 * Flows so a write is seen by every screen at once — exactly how Room's DAO Flows
 * behave in phase 3, so the ViewModels here will not change when Room lands.
 *
 * Separate APK from app-pos: it CANNOT see app-pos's repository and there is no
 * backend yet, so anything that would arrive from the merchant side (a pending
 * approval request) is SIMULATED locally here (see PendingApproval / ApprovalService).
 */
object FakeRepository {

    private const val SESSION_TTL_MILLIS = 7L * 24 * 60 * 60 * 1000

    // The demo account that is signed in by default in this build's seed. A BUYER
    // (u1, Ahmet) who owes two different shops — this is what "my debts across
    // sellers" looks like. Login accepts any of the seed phones below.
    private const val DEMO_BUYER_PHONE = "+905551112233"

    // App accounts. Two of them are SELLERS (shops the buyer owes) so their
    // shopName can be shown on the buyer's debt list; u1/u3 are plain buyers.
    private val _users = MutableStateFlow(
        listOf(
            // Sellers (their SellerInfo.shopName is what the buyer sees per debt).
            User(
                userId = "u_owner",
                phone = "+905554443322",
                displayName = "Ahmet Bakkal",
                isBuyer = true,
                isSeller = true,
                email = null,
                sellerInfo = SellerInfo(shopName = "Ahmet Bakkal", shopPhone = "+902121112233"),
                createdAt = "01.07.2026 09:00"
            ),
            User(
                userId = "u_market",
                phone = "+905553334455",
                displayName = "Ayşe Market",
                isBuyer = true,
                isSeller = true,
                email = null,
                sellerInfo = SellerInfo(shopName = "Ayşe Market", shopPhone = null),
                createdAt = "02.07.2026 09:00"
            ),
            // The signed-in buyer for the demo. displayName/email fill in from profile.
            User(
                userId = "u1",
                phone = DEMO_BUYER_PHONE,
                displayName = "Ahmet Yılmaz",
                isBuyer = true,
                isSeller = false,
                email = null,
                sellerInfo = null,
                createdAt = "05.07.2026 12:30"
            ),
            User(
                userId = "u3",
                phone = "+905554445566",
                displayName = "Mehmet Kaya",
                isBuyer = true,
                isSeller = false,
                email = null,
                sellerInfo = null,
                createdAt = "08.07.2026 15:45"
            )
        )
    )

    // Customer records = the merchants' ledger entries. The buyer u1 appears as a
    // CLAIMED customer in BOTH shops (same person, two shops) — matched by phone.
    // Ahmet Bakkal knows u1 as customer c1; Ayşe Market knows u1 as customer m1.
    //
    // u_owner is a seller who ALSO shops as a buyer (both roles): Ayşe Market knows
    // them as customer o1. This is what lets the well-known 05554443322 account show
    // debts + a pending approval on sign-in, not just u1.
    private val _customers = MutableStateFlow(
        listOf(
            RawCustomer("c1", "Ahmet Yılmaz", DEMO_BUYER_PHONE, ClaimStatus.CLAIMED, "u1"),
            RawCustomer("m1", "Ahmet Y.", DEMO_BUYER_PHONE, ClaimStatus.CLAIMED, "u1"),
            RawCustomer("o1", "Ahmet Bakkal", "+905554443322", ClaimStatus.CLAIMED, "u_owner"),
            // Customers in u_owner's own book (so the SELLER side has data when the
            // shopkeeper 05554443322 signs in). c3 has the app (CLAIMED → u3); c4/c5 do
            // not (UNCLAIMED) — the app-less case for the approval flow (SMS OTP branch).
            RawCustomer("c3", "Mehmet Kaya", "+905554445566", ClaimStatus.CLAIMED, "u3"),
            RawCustomer("c4", "Fatma Şahin", "+905556667788", ClaimStatus.UNCLAIMED, null),
            RawCustomer("c5", "Hasan Öztürk", "+905558889900", ClaimStatus.UNCLAIMED, null)
        )
    )

    // The append-only ledger across TWO sellers, so the buyer's debt list is not
    // trivially one row. Only ever appended to (never updated/deleted).
    private val _transactions = MutableStateFlow(
        listOf(
            // u1 @ Ahmet Bakkal (c1): 50 + 30 - 40 = 40,00 TL
            Transaction("t1", "u_owner", "c1", 5000, TransactionType.DEBT, "Ekmek, süt", "20.07.2026 09:15"),
            Transaction("t2", "u_owner", "c1", 3000, TransactionType.DEBT, "Peynir", "21.07.2026 10:40"),
            Transaction("t3", "u_owner", "c1", 4000, TransactionType.PAYMENT, "Nakit ödeme", "22.07.2026 18:00"),

            // u1 @ Ayşe Market (m1): 120 + 45 - 65 = 100,00 TL
            Transaction("t4", "u_market", "m1", 12000, TransactionType.DEBT, "Market alışverişi", "18.07.2026 11:20"),
            Transaction("t5", "u_market", "m1", 4500, TransactionType.DEBT, "Deterjan", "22.07.2026 16:05"),
            Transaction("t6", "u_market", "m1", 6500, TransactionType.PAYMENT, "Kısmi ödeme", "24.07.2026 13:00"),

            // u_owner @ Ayşe Market (o1): 90 - 30 = 60,00 TL (the shopkeeper's own debt
            // as a buyer, so signing in with 05554443322 shows a debt too).
            Transaction("t7", "u_market", "o1", 9000, TransactionType.DEBT, "Kırtasiye", "16.07.2026 10:00"),
            Transaction("t8", "u_market", "o1", 3000, TransactionType.PAYMENT, "Nakit ödeme", "23.07.2026 15:30"),

            // u_owner's OWN customer book (sellerId = "u_owner") — the SELLER side.
            // c3 (Mehmet, has app): 80 - 80 = 0. c4 (Fatma, no app): 25,50. c5 (Hasan): 210,00.
            Transaction("t9", "u_owner", "c3", 8000, TransactionType.DEBT, "Kahvaltılık", "15.07.2026 08:30"),
            Transaction("t10", "u_owner", "c3", 8000, TransactionType.PAYMENT, "Kart ile ödeme", "19.07.2026 12:00"),
            Transaction("t11", "u_owner", "c4", 2550, TransactionType.DEBT, "Çay, şeker", "23.07.2026 08:45"),
            Transaction("t12", "u_owner", "c5", 31000, TransactionType.DEBT, "Toplu alışveriş", "10.07.2026 17:30"),
            Transaction("t13", "u_owner", "c5", 10000, TransactionType.PAYMENT, "Kısmi ödeme", "20.07.2026 14:10")
        )
    )

    // Pending approvals waiting on the signed-in buyer. In production these arrive
    // from the backend (a POS asked to book a veresiye/payment against this phone)
    // and the app POLLS for them (foreground now, WorkManager in phase 4 — app is a
    // caller, never a listener; no FCM). Seeded with one so the demo shows the card.
    private val _pendingApprovals = MutableStateFlow(
        listOf(
            // For u1 (05551112233): a veresiye request from Ahmet Bakkal.
            PendingApproval(
                approvalId = "p1",
                sellerId = "u_owner",
                shopName = "Ahmet Bakkal",
                buyerUserId = "u1",
                amountMinor = 5000,
                type = TransactionType.DEBT,
                description = "Ekmek, süt",
                requestedAt = "25.07.2026 10:05"
            ),
            // For u_owner (05554443322): a veresiye request from Ayşe Market, so the
            // well-known account also has a pending approval to demo.
            PendingApproval(
                approvalId = "p2",
                sellerId = "u_market",
                shopName = "Ayşe Market",
                buyerUserId = "u_owner",
                amountMinor = 7500,
                type = TransactionType.DEBT,
                description = "Temizlik malzemesi",
                requestedAt = "25.07.2026 11:20"
            )
        )
    )

    // --- Session (mock, backend-ready signatures) ----------------------------
    // A User is an identity; a Session is "who is signed in and until when". A real
    // backend returns a JWT stored in DataStore/Room — only the bodies below change.
    private data class Session(
        val userId: String,
        val token: String,
        val loggedInAt: Long,
        val expiresAt: Long
    )

    // Mock caveat: RAM-held, so it resets on a full process kill (persistence lands
    // with Room in phase 3). The 7-day expiry check is real; it just cannot survive
    // a cold kill yet.
    private val _session = MutableStateFlow<Session?>(null)

    fun isSessionValid(): Boolean =
        _session.value?.let { it.expiresAt > System.currentTimeMillis() } == true

    // Whether the seller has paired this app with their POS terminal. Separate axis
    // from login; starts false so the pairing flow is reachable once a user becomes a
    // seller. Real pairing (phone-number handshake) is a later phase; mock here.
    private val _isPairedWithApp = MutableStateFlow(false)
    val isPairedWithApp: StateFlow<Boolean> = _isPairedWithApp.asStateFlow()

    // --- Sign-in / registration ---------------------------------------------

    /** The account with this phone, or null. Phones compared by digits only. */
    fun findUserByPhone(phone: String): User? {
        val target = normalizePhone(phone)
        return _users.value.firstOrNull { normalizePhone(it.phone) == target }
    }

    /**
     * Signs in a REGISTERED phone; returns whether it was accepted. Unknown numbers
     * are rejected here — the caller (LoginViewModel) offers registration instead.
     * No PhoneFormat.toStored here: findUserByPhone matches by digits, so any format
     * (raw or E.164) is accepted.
     */
    fun login(phone: String?): Boolean {
        val user = phone?.let { findUserByPhone(it) } ?: return false
        val now = System.currentTimeMillis()
        _session.value = Session(
            userId = user.userId,
            token = UUID.randomUUID().toString(),
            loggedInAt = now,
            expiresAt = now + SESSION_TTL_MILLIS
        )
        return true
    }

    fun logout() {
        _session.value = null
        _isPairedWithApp.value = false
    }

    /** Marks this app paired to the seller's POS terminal (mock single-confirm). */
    fun pairWithApp() {
        _isPairedWithApp.value = true
    }

    /**
     * Flips an account into a seller (the "Satıcı ol" button). Idempotent: re-running
     * just updates the shop info. No-op if the user id is unknown. SellerInfo is a
     * separate object so the type enforces "isSeller ⇒ sellerInfo != null".
     */
    fun setSeller(userId: String, shopName: String, shopPhone: String? = null) {
        _users.value = _users.value.map { user ->
            if (user.userId != userId) user
            else user.copy(isSeller = true, sellerInfo = SellerInfo(shopName.trim(), shopPhone))
        }
    }

    /** Updates just the shop name, keeping any existing shopPhone (unlike setSeller). */
    fun updateShopName(userId: String, shopName: String) {
        _users.value = _users.value.map { user ->
            if (user.userId != userId) return@map user
            val info = user.sellerInfo?.copy(shopName = shopName.trim())
                ?: SellerInfo(shopName = shopName.trim(), shopPhone = null)
            user.copy(isSeller = true, sellerInfo = info)
        }
    }

    /**
     * Sign-up == sign-in: return the existing account for this phone, or create one.
     * app-mobile registers a BUYER (isSeller = false — this is the whole difference
     * from app-pos, which registers a seller). sellerInfo stays null; the "Become a
     * seller" flow fills it in later.
     */
    fun registerUser(phone: String, displayName: String, isSeller: Boolean = false): User {
        findUserByPhone(phone)?.let { return it }
        val user = User(
            userId = UUID.randomUUID().toString(),
            phone = PhoneFormat.toStored(phone) ?: normalizePhone(phone),
            displayName = displayName.trim(),
            isBuyer = true,
            isSeller = isSeller,
            email = null,
            sellerInfo = null,
            createdAt = nowStamp()
        )
        _users.value = _users.value + user
        return user
    }

    /**
     * The account that is signed in, resolved from the session's userId. Emits null
     * while logged out (the login gate handles that).
     */
    fun observeCurrentUser(): Flow<User?> =
        combine(_users, _session) { users, session ->
            val valid = session != null && session.expiresAt > System.currentTimeMillis()
            if (!valid) null else users.firstOrNull { it.userId == session!!.userId }
        }

    /** The signed-in user's id, read synchronously (for the single write points). */
    fun currentUserId(): String? =
        _session.value?.takeIf { it.expiresAt > System.currentTimeMillis() }?.userId

    /** Updates just the display name; reactive readers refresh on their own. */
    fun updateDisplayName(userId: String, displayName: String) {
        _users.value = _users.value.map { user ->
            if (user.userId != userId) user else user.copy(displayName = displayName.trim())
        }
    }

    /** Updates just the email (an optional profile field). */
    fun updateEmail(userId: String, email: String) {
        _users.value = _users.value.map { user ->
            if (user.userId != userId) user
            else user.copy(email = email.trim().ifBlank { null })
        }
    }

    /**
     * Links every UNCLAIMED customer with this phone to the user, inheriting the old
     * debt (the CLAIM bridge — a User signing in adopts the merchant's records for
     * their number). Runs at sign-in on app-mobile. Returns the claimed records.
     *
     * The relationship is stored in DATA (claimedByUserId), not trusted to a phone
     * match at read time — same as Room's FK / the backend's join will be.
     */
    fun claimCustomerForUser(userId: String, phone: String): List<Customer> {
        val target = normalizePhone(phone)
        val claimed = mutableListOf<Customer>()
        _customers.value = _customers.value.map { raw ->
            if (raw.claimStatus == ClaimStatus.UNCLAIMED && normalizePhone(raw.phone) == target) {
                val updated = raw.copy(claimStatus = ClaimStatus.CLAIMED, claimedByUserId = userId)
                claimed += updated.toCustomer(sellerId = null, ledger = _transactions.value)
                updated
            } else {
                raw
            }
        }
        return claimed
    }

    // --- Seller-scoped reads/writes (the app-pos merchant surface) -----------
    // Used once the signed-in user is a seller; sellerId is their own userId.

    /**
     * The given seller's customers: everyone they have at least one ledger entry with
     * (SQL: SELECT DISTINCT customer_id FROM transactions WHERE seller_id = :sellerId).
     */
    fun observeCustomers(sellerId: String): Flow<List<Customer>> =
        combine(_customers, _transactions) { people, ledger ->
            val sellerLedger = ledger.filter { it.sellerId == sellerId }
            val customerIds = sellerLedger.map { it.customerId }.toSet()
            people
                .filter { it.id in customerIds }
                .map { it.toCustomer(sellerId, sellerLedger) }
        }

    /** One customer's history with the given seller, newest first. */
    fun observeTransactions(sellerId: String, customerId: String): Flow<List<Transaction>> =
        _transactions.map { ledger ->
            ledger
                .filter { it.sellerId == sellerId && it.customerId == customerId }
                .reversed()
        }

    /** Total the given seller is owed across their own customers. */
    fun observeTotalReceivableMinor(sellerId: String): Flow<Long> =
        _transactions.map { ledger ->
            ledger
                .filter { it.sellerId == sellerId }
                .sumOf { if (it.type == TransactionType.DEBT) it.amountMinor else -it.amountMinor }
        }

    /** Creates an UNCLAIMED customer (no app yet) and returns its new id. */
    fun addCustomer(displayName: String, phone: String): String {
        val id = UUID.randomUUID().toString()
        _customers.value = _customers.value +
            RawCustomer(id, displayName.trim(), normalizePhone(phone), ClaimStatus.UNCLAIMED, null)
        return id
    }

    /** Whether a customer with this phone already exists (digits compared). */
    fun customerPhoneExists(phone: String): Boolean {
        val target = normalizePhone(phone)
        return _customers.value.any { normalizePhone(it.phone) == target }
    }

    /** The customer with this id, balance scoped to the given seller, or null. */
    fun findCustomerById(sellerId: String, customerId: String): Customer? {
        val raw = _customers.value.firstOrNull { it.id == customerId } ?: return null
        return raw.toCustomer(sellerId, _transactions.value)
    }

    /** The existing customer with this phone (seller-scoped balance), or null. */
    fun findCustomerByPhone(sellerId: String, phone: String): Customer? {
        val target = normalizePhone(phone)
        val raw = _customers.value.firstOrNull { normalizePhone(it.phone) == target } ?: return null
        return raw.toCustomer(sellerId, _transactions.value)
    }

    // --- Buyer-scoped reads (the app-pos reads, mirrored) --------------------

    /**
     * The signed-in buyer's debts, grouped by seller: one row per shop they owe,
     * carrying that shop's name and the (seller, buyer) balance. The mirror of
     * app-pos's observeCustomers(sellerId).
     *
     * "My customer ids" = the customer records claimed by this user (the claim
     * bridge). For each, sum its ledger with that seller and label it with the
     * seller's shopName.
     */
    fun observeMyDebtsBySeller(userId: String): Flow<List<SellerDebt>> =
        combine(_customers, _transactions, _users) { people, ledger, users ->
            val myCustomerIds = people
                .filter { it.claimedByUserId == userId }
                .map { it.id }
                .toSet()
            ledger
                .filter { it.customerId in myCustomerIds }
                .groupBy { it.sellerId }
                .map { (sellerId, entries) ->
                    val balance = entries.sumOf {
                        if (it.type == TransactionType.DEBT) it.amountMinor else -it.amountMinor
                    }
                    val shopName = users.firstOrNull { it.userId == sellerId }
                        ?.sellerInfo?.shopName ?: sellerId
                    SellerDebt(sellerId = sellerId, shopName = shopName, balanceMinor = balance)
                }
                .sortedByDescending { it.balanceMinor }
        }

    /** The signed-in buyer's total debt across every seller. */
    fun observeMyTotalDebtMinor(userId: String): Flow<Long> =
        observeMyDebtsBySeller(userId).map { debts -> debts.sumOf { it.balanceMinor } }

    /**
     * The signed-in buyer's history with one seller, newest first — the mirror of
     * app-pos's observeTransactions(sellerId, customerId), scoped by the buyer's
     * claimed customer ids instead of a single customerId.
     */
    fun observeMyTransactions(userId: String, sellerId: String): Flow<List<Transaction>> =
        combine(_customers, _transactions) { people, ledger ->
            val myCustomerIds = people
                .filter { it.claimedByUserId == userId }
                .map { it.id }
                .toSet()
            ledger
                .filter { it.sellerId == sellerId && it.customerId in myCustomerIds }
                .reversed()
        }

    /** The buyer's balance with one seller (for the confirm/detail header). */
    fun observeMyBalanceWithSeller(userId: String, sellerId: String): Flow<Long> =
        observeMyTransactions(userId, sellerId).map { entries ->
            entries.sumOf {
                if (it.type == TransactionType.DEBT) it.amountMinor else -it.amountMinor
            }
        }

    /** The seller's shop name for a header, resolved synchronously. */
    fun shopNameOf(sellerId: String): String =
        _users.value.firstOrNull { it.userId == sellerId }?.sellerInfo?.shopName ?: sellerId

    // --- Pending approvals (foreground polling, mock) ------------------------

    /**
     * The pending approvals waiting on this buyer. In production the app POLLS the
     * backend for these; here the StateFlow just re-emits, which is what a poll loop
     * would observe. TODO(FAZ 4): a WorkManager background poll refreshes this while
     * the app is closed — foreground observation only for now.
     */
    fun observePendingApprovals(userId: String): Flow<List<PendingApproval>> =
        _pendingApprovals.map { list -> list.filter { it.buyerUserId == userId } }

    /**
     * The buyer approves a pending entry → it becomes a real ledger transaction
     * (the single write point, mirroring app-pos writing only after OTP), and the
     * pending request is cleared. In production this call also tells the backend,
     * which releases the POS. TODO(FAZ 4/5): POST the approval.
     */
    fun approvePending(approvalId: String) {
        val approval = _pendingApprovals.value.firstOrNull { it.approvalId == approvalId } ?: return
        val customerId = customerIdFor(approval.buyerUserId, approval.sellerId) ?: return
        addTransaction(
            Transaction(
                transactionId = UUID.randomUUID().toString(),
                sellerId = approval.sellerId,
                customerId = customerId,
                amountMinor = approval.amountMinor,
                type = approval.type,
                description = approval.description,
                createdAt = nowStamp()
            )
        )
        _pendingApprovals.value = _pendingApprovals.value.filterNot { it.approvalId == approvalId }
    }

    /** The buyer rejects a pending entry → cleared, nothing written. */
    fun rejectPending(approvalId: String) {
        _pendingApprovals.value = _pendingApprovals.value.filterNot { it.approvalId == approvalId }
    }

    // --- Approval-gated writes (both directions go through here) --------------

    /**
     * Sends a veresiye/payment entry for the counterparty's approval — docs: EVERY
     * DEBT/PAYMENT is confirmed by the other side, and EITHER side can start one:
     *  - a SELLER writes to one of their customers (customerId in their book), or
     *  - a BUYER pays a seller (their own claimed customer record with that seller).
     *
     * Routing (mock of the backend's push-vs-SMS branch):
     *  - counterparty HAS the app (the customer record is CLAIMED): a PendingApproval
     *    lands in their Onaylar tab; nothing is written until they approve.
     *  - counterparty has NO app (UNCLAIMED): the SMS-OTP case, mocked as auto-approved,
     *    so the entry is written straight away.
     *
     * The single write point is preserved: either approvePending() later, or the
     * immediate write here for the app-less case.
     */
    suspend fun requestApproval(
        fromUserId: String,
        sellerId: String,
        customerId: String,
        amountMinor: Long,
        type: TransactionType,
        description: String
    ) {
        val raw = _customers.value.firstOrNull { it.id == customerId } ?: return
        ApprovalService.requestApproval(fromUserId, raw.phone.orEmpty(), amountMinor, type.name)

        val approverUserId = raw.claimedByUserId
        if (approverUserId != null) {
            // Has the app: drop a pending approval into their Onaylar tab.
            _pendingApprovals.value = _pendingApprovals.value + PendingApproval(
                approvalId = UUID.randomUUID().toString(),
                sellerId = sellerId,
                shopName = shopNameOf(sellerId),
                buyerUserId = approverUserId,
                amountMinor = amountMinor,
                type = type,
                description = description,
                requestedAt = nowStamp()
            )
        } else {
            // No app (UNCLAIMED): SMS-OTP case, mocked true → write immediately.
            addTransaction(
                Transaction(
                    transactionId = UUID.randomUUID().toString(),
                    sellerId = sellerId,
                    customerId = customerId,
                    amountMinor = amountMinor,
                    type = type,
                    description = description,
                    createdAt = nowStamp()
                )
            )
        }
    }

    /**
     * A buyer pays a seller. Resolves the buyer's own customer record with that seller,
     * then goes through the same approval gate (the seller confirms receipt). Suspend
     * because it calls the mock ApprovalService.
     */
    suspend fun initiatePayment(userId: String, sellerId: String, amountMinor: Long) {
        val customerId = customerIdFor(userId, sellerId) ?: return
        requestApproval(
            fromUserId = userId,
            sellerId = sellerId,
            customerId = customerId,
            amountMinor = amountMinor,
            type = TransactionType.PAYMENT,
            description = "Uygulamadan ödeme"
        )
    }

    /** Appends one entry to the ledger (append-only). */
    fun addTransaction(transaction: Transaction) {
        _transactions.value = _transactions.value + transaction
    }

    // --- helpers -------------------------------------------------------------

    /** This buyer's customer id in a given seller's book, or null. */
    private fun customerIdFor(userId: String, sellerId: String): String? {
        // A user may hold more than one customer record (one per shop); any of them
        // that already has ledger entries with this seller is the right one, else
        // just the first claimed record works (a fresh shop with no prior entries).
        val mine = _customers.value.filter { it.claimedByUserId == userId }
        val withSeller = _transactions.value
            .filter { it.sellerId == sellerId }
            .map { it.customerId }
            .toSet()
        return mine.firstOrNull { it.id in withSeller }?.id ?: mine.firstOrNull()?.id
    }

    private fun normalizePhone(phone: String?): String =
        phone?.filter { it.isDigit() }.orEmpty()

    private fun nowStamp(): String =
        java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.forLanguageTag("tr-TR"))
            .format(java.util.Date())

    /**
     * The (seller, customer) pair balance: DEBT adds, PAYMENT subtracts. Scoped to one
     * seller so the same person can owe different amounts to different sellers.
     */
    private fun balanceOf(sellerId: String, customerId: String, ledger: List<Transaction>): Long =
        ledger
            .filter { it.sellerId == sellerId && it.customerId == customerId }
            .sumOf { tx ->
                when (tx.type) {
                    TransactionType.DEBT -> tx.amountMinor
                    TransactionType.PAYMENT -> -tx.amountMinor
                }
            }

    /** RawCustomer → domain Customer. sellerId is only needed for a balance; the
     *  claim flow does not need one, so it is nullable (balance = 0 then). */
    private fun RawCustomer.toCustomer(sellerId: String?, ledger: List<Transaction>): Customer =
        Customer(
            customerId = id,
            displayName = name,
            phone = phone,
            claimStatus = claimStatus,
            claimedByUserId = claimedByUserId,
            balanceMinor = if (sellerId == null) 0 else balanceOf(sellerId, id, ledger)
        )

    /** Customer data without a balance — the balance is always derived. */
    private data class RawCustomer(
        val id: String,
        val name: String,
        val phone: String?,
        val claimStatus: ClaimStatus,
        val claimedByUserId: String?
    )
}

/**
 * One row on the buyer's debt list: a shop they owe and how much. Not a domain
 * model (it is a buyer-side read projection), so it lives with the repository.
 */
data class SellerDebt(
    val sellerId: String,
    val shopName: String,
    val balanceMinor: Long
)

/**
 * A merchant's request, waiting on the buyer's approval, to book a veresiye (DEBT)
 * or payment (PAYMENT) against their account. Arrives from the backend in
 * production; simulated locally here.
 */
data class PendingApproval(
    val approvalId: String,
    val sellerId: String,
    val shopName: String,
    val buyerUserId: String,
    val amountMinor: Long,
    val type: TransactionType,
    val description: String,
    val requestedAt: String
)
