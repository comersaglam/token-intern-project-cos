package com.example.app_pos.data

import com.example.app_pos.model.ClaimStatus
import com.example.app_pos.model.Customer
import com.example.app_pos.model.SellerInfo
import com.example.app_pos.model.Transaction
import com.example.app_pos.model.TransactionType
import com.example.app_pos.model.User
import com.example.app_pos.util.PhoneFormat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * In-memory stand-in for the real data layer (phase 1).
 *
 * It deliberately mimics how the real Repository will behave: the ledger
 * (transactions) is the source of truth and balances are DERIVED from it, never
 * stored. The ledger is exposed as an observable Flow, so a write is seen by
 * every screen at once — exactly how Room's DAO Flows will behave in phase 3, so
 * the ViewModels reading it here will not change when Room arrives.
 */
object FakeRepository {

    // The seed shopkeeper's phone. Login now accepts any registered number, so this
    // is only a reference for the u_owner seed below — not a login credential.
    private const val OWNER_PHONE = "+905554443322"

    // Mock session lifetime. The check is real; RAM storage just cannot outlive a
    // full process kill yet (persistence lands with Room in phase 3).
    private const val SESSION_TTL_MILLIS = 7L * 24 * 60 * 60 * 1000

    // Observable so a newly added customer shows up on every screen at once, the
    // same way the ledger does.
    //
    // Every customer has a PHONE — it is the identity the whole system tracks a
    // customer by (a later app sign-in claims the record via this number). The
    // claimStatus axis is separate: it only says whether the customer has the app
    // yet (CLAIMED) or not (UNCLAIMED). Phone is no longer optional.
    private val _customers = MutableStateFlow(
        listOf(
            // CLAIMED customers point back to the User account that claimed them (u1, u3).
            RawCustomer("c1", "Ahmet Yılmaz", "+905551112233", ClaimStatus.CLAIMED, "u1"),
            RawCustomer("c2", "Ayşe Demir", "+905552223344", ClaimStatus.UNCLAIMED, null),
            RawCustomer("c3", "Mehmet Kaya", "+905554445566", ClaimStatus.CLAIMED, "u3"),
            RawCustomer("c4", "Fatma Şahin", "+905556667788", ClaimStatus.UNCLAIMED, null),
            RawCustomer("c5", "Hasan Öztürk", "+905558889900", ClaimStatus.UNCLAIMED, null)
        )
    )

    // The append-only ledger, held in an observable so writes propagate. The list
    // is only ever appended to (never updated or deleted), matching the rule.
    // Every seed entry belongs to the one seller (u_owner) — the whole seed ledger
    // is that shop's book, so balances stay identical to before the seller scoping.
    private val _transactions = MutableStateFlow(
        listOf(
            // Ahmet: 50 + 30 - 40 = 40,00 TL
            Transaction("t1", "u_owner", "c1", 5000, TransactionType.DEBT, "Ekmek, süt", "20.07.2026 09:15"),
            Transaction("t2", "u_owner", "c1", 3000, TransactionType.DEBT, "Peynir", "21.07.2026 10:40"),
            Transaction("t3", "u_owner", "c1", 4000, TransactionType.PAYMENT, "Nakit ödeme", "22.07.2026 18:00"),

            // Ayşe: 120 + 45 = 165,00 TL
            Transaction("t4", "u_owner", "c2", 12000, TransactionType.DEBT, "Market alışverişi", "18.07.2026 11:20"),
            Transaction("t5", "u_owner", "c2", 4500, TransactionType.DEBT, "Deterjan", "22.07.2026 16:05"),

            // Mehmet: 80 - 80 = 0 (fully paid)
            Transaction("t6", "u_owner", "c3", 8000, TransactionType.DEBT, "Kahvaltılık", "15.07.2026 08:30"),
            Transaction("t7", "u_owner", "c3", 8000, TransactionType.PAYMENT, "Kart ile ödeme", "19.07.2026 12:00"),

            // Fatma: 25,50 TL
            Transaction("t8", "u_owner", "c4", 2550, TransactionType.DEBT, "Çay, şeker", "23.07.2026 08:45"),

            // Hasan: 310 - 100 = 210,00 TL
            Transaction("t9", "u_owner", "c5", 31000, TransactionType.DEBT, "Toplu alışveriş", "10.07.2026 17:30"),
            Transaction("t10", "u_owner", "c5", 10000, TransactionType.PAYMENT, "Kısmi ödeme", "20.07.2026 14:10")
        )
    )

    // App-mobile ACCOUNTS. Separate from customers on purpose (see User/Customer docs):
    // a customer is the merchant's ledger entry, a user is a real signed-in account.
    //  - u_owner: the shopkeeper running app-pos — both a buyer AND a seller. Has no
    //    customer record (a merchant does not owe themselves), proving one account can
    //    hold both roles at once.
    //  - u1 / u3: the accounts behind the two CLAIMED customers (same phone numbers).
    private val _users = MutableStateFlow(
        listOf(
            // The shopkeeper who signs in on this terminal. Login credential is this
            // phone (only "05554443322" is accepted). displayName + sellerInfo start
            // EMPTY on purpose: the merchant fills them in from the profile screen.
            // isSeller stays true — whoever runs app-pos is a seller; the shop details
            // being blank is a separate axis (shown as "not set" until entered).
            User(
                userId = "u_owner",
                phone = OWNER_PHONE,
                displayName = "",
                isBuyer = true,
                isSeller = true,
                email = null,
                sellerInfo = null,
                createdAt = "01.07.2026 09:00"
            ),
            User(
                userId = "u1",
                phone = "+905551112233",
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

    /**
     * Appends one entry to the ledger (append-only: never update or delete).
     * Emitting a new list makes every observing screen recompute immediately.
     */
    fun addTransaction(transaction: Transaction) {
        _transactions.value = _transactions.value + transaction
    }

    /**
     * Creates a customer from a name + phone and returns its new id. UNCLAIMED
     * because the customer has no app yet; a later sign-in with this phone claims
     * the record. The phone is the identity, so duplicates are rejected upstream
     * (see customerPhoneExists) — two different names may share nothing but must
     * not share a number.
     */
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

    /** The customer with this id, or null. Balance is scoped to the given seller. */
    fun findCustomerById(sellerId: String, customerId: String): Customer? {
        val raw = _customers.value.firstOrNull { it.id == customerId } ?: return null
        return raw.toCustomer(sellerId, _transactions.value)
    }

    /** The existing customer with this phone, or null — used to skip re-entry. */
    fun findCustomerByPhone(sellerId: String, phone: String): Customer? {
        val target = normalizePhone(phone)
        val raw = _customers.value.firstOrNull { normalizePhone(it.phone) == target } ?: return null
        return raw.toCustomer(sellerId, _transactions.value)
    }

    /** Compares phones by digits only, so "+90 555 111" and "0555111" match. */
    private fun normalizePhone(phone: String?): String =
        phone?.filter { it.isDigit() }.orEmpty()

    /**
     * The given seller's customers: everyone they have at least one ledger entry
     * with. A customer is not tied to a seller directly (the same person can buy
     * from many sellers) — the (seller, customer) pairs live in the ledger, so the
     * customer list is derived from it. SQL: SELECT DISTINCT customer_id FROM
     * transactions WHERE seller_id = :sellerId.
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

    // --- User accounts (app-mobile) -----------------------------------------
    // Signatures are backend-ready: when a real backend/Room arrives, only the
    // bodies change. Some work already (below); the heavier claim flow is stubbed
    // until app-mobile drives it end to end.

    /** The account with this phone, or null. Phones compared by digits only. */
    fun findUserByPhone(phone: String): User? {
        val target = normalizePhone(phone)
        return _users.value.firstOrNull { normalizePhone(it.phone) == target }
    }

    // --- Session + pairing (mock, backend-ready signatures) ------------------
    // A signed-in session, token-based. Kept here (not on User): a User is an
    // identity, a Session is "who is signed in on this terminal and until when".
    // A real backend (Square/SumUp: enrol once, sign in once, silent afterwards)
    // returns a JWT stored in DataStore/Room — only the bodies below change then.
    private data class Session(
        val userId: String,   // WHO is signed in — the profile/ledger follow this
        val token: String,
        val loggedInAt: Long,
        val expiresAt: Long
    )

    // Mock caveat: held in RAM, so it resets when the process is fully killed
    // (persistence — remembering across app restarts — arrives with Room in phase 3).
    // The 7-day "expiresAt" check below is real; it just cannot survive a cold kill yet.
    private val _session = MutableStateFlow<Session?>(null)

    /** True while a non-expired session exists. Session state is read via
     *  isSessionValid() (sync) or observeCurrentUser() (Flow) — nothing needs a
     *  standalone isLoggedIn StateFlow, so there is none. */
    fun isSessionValid(): Boolean =
        _session.value?.let { it.expiresAt > System.currentTimeMillis() } == true

    // Whether this POS terminal is paired to the merchant's app account. Separate
    // axis from login; starts false so the pairing flow is reachable in the demo.
    private val _isPairedWithApp = MutableStateFlow(false)
    val isPairedWithApp: StateFlow<Boolean> = _isPairedWithApp.asStateFlow()

    /**
     * Signs in any REGISTERED phone and returns whether it was accepted. An unknown
     * number is rejected here — the caller (LoginViewModel) offers registration for
     * those instead. Real login: request+verify an OTP, then store the backend token.
     *
     * findUserByPhone compares by digits, so it accepts any format (raw or E.164) —
     * no PhoneFormat.toStored here (running it on an already-E.164 number would fail
     * its 11-digit-starting-with-0 check and wrongly reject the login).
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

    /** Ends the session; pairing drops with it, so the terminal re-pairs next time. */
    fun logout() {
        _session.value = null
        _isPairedWithApp.value = false
    }

    /** Marks this terminal paired — called after the mock pairing screen confirms. */
    fun pairWithApp() {
        _isPairedWithApp.value = true
    }

    /**
     * The account that is actually signed in — resolved from the session's userId,
     * so registering a new number and signing in shows THAT user (not a fixed owner).
     * Emits null while logged out (the login gate handles that).
     */
    fun observeCurrentUser(): Flow<User?> =
        combine(_users, _session) { users, session ->
            val valid = session != null && session.expiresAt > System.currentTimeMillis()
            if (!valid) null else users.firstOrNull { it.userId == session!!.userId }
        }

    /**
     * The signed-in seller's id, read synchronously — the single write point
     * (OtpViewModel) needs it without collecting a Flow. Null when logged out.
     */
    fun currentSellerId(): String? =
        _session.value?.takeIf { it.expiresAt > System.currentTimeMillis() }?.userId

    /**
     * Sign-up == sign-in: if an account with this phone exists, return it; otherwise
     * create one (a buyer by default) and return it. This is the auto-register step
     * that runs after a successful OTP on app-mobile.
     */
    fun registerUser(phone: String, displayName: String, isSeller: Boolean = false): User {
        findUserByPhone(phone)?.let { return it }
        // Store E.164 to match the seed users and login lookups. isSeller is a
        // parameter because who registers depends on the app: app-pos registers a
        // seller (isSeller = true), app-mobile a buyer. sellerInfo stays null either
        // way — the shop details are filled in later from the profile.
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
     * Flips an account into a seller (the "Become a seller" button). Idempotent:
     * re-running just updates the shop info. No-op if the user id is unknown.
     */
    fun setSeller(userId: String, shopName: String, shopPhone: String? = null) {
        _users.value = _users.value.map { user ->
            if (user.userId != userId) user
            else user.copy(isSeller = true, sellerInfo = SellerInfo(shopName.trim(), shopPhone))
        }
    }

    /** Updates just the display name (a User field, not seller info). Reactive
     *  readers (observeCurrentUser) re-emit, so the profile refreshes on its own. */
    fun updateDisplayName(userId: String, displayName: String) {
        _users.value = _users.value.map { user ->
            if (user.userId != userId) user else user.copy(displayName = displayName.trim())
        }
    }

    /**
     * Updates just the shop name, keeping any existing shopPhone (unlike setSeller,
     * which rewrites the whole SellerInfo). Creates SellerInfo if absent and marks
     * the user a seller, so entering a shop name from the profile is enough.
     */
    fun updateShopName(userId: String, shopName: String) {
        _users.value = _users.value.map { user ->
            if (user.userId != userId) return@map user
            val info = user.sellerInfo?.copy(shopName = shopName.trim())
                ?: SellerInfo(shopName = shopName.trim(), shopPhone = null)
            user.copy(isSeller = true, sellerInfo = info)
        }
    }

    /**
     * Links an UNCLAIMED customer (matched by phone) to a user, inheriting the old
     * debt. TODO(app-mobile): implement once sign-in drives claiming end to end —
     * set claimStatus = CLAIMED and claimedByUserId = userId on the matched record.
     */
    fun claimCustomerForUser(userId: String, phone: String): Customer? {
        TODO("Claim flow arrives with app-mobile sign-in (phase: app-mobile)")
    }

    // observeUser(userId) and observeMyTransactions(userId) — a buyer's own account
    // and cross-merchant ledger — are intentionally not implemented yet. They only
    // make sense once a real backend can serve data across merchants; app-mobile's
    // profile/history screens will add them then.

    // forLanguageTag (not the deprecated Locale(String, String) used elsewhere in the
    // codebase) — the modern, warning-free way to build a locale.
    private fun nowStamp(): String =
        java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.forLanguageTag("tr-TR"))
            .format(java.util.Date())

    /**
     * The append-only rule in practice: a balance is the sum of the (seller,
     * customer) pair's entries, with DEBT adding and PAYMENT subtracting. Scoped to
     * one seller so the same person can owe different amounts to different sellers.
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

    /** Builds the domain Customer with its balance for one seller. Single place for
     *  the RawCustomer → Customer mapping (used by observe + both find methods). */
    private fun RawCustomer.toCustomer(sellerId: String, ledger: List<Transaction>): Customer =
        Customer(
            customerId = id,
            displayName = name,
            phone = phone,
            claimStatus = claimStatus,
            claimedByUserId = claimedByUserId,
            balanceMinor = balanceOf(sellerId, id, ledger)
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
