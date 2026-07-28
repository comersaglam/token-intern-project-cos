package com.example.app_pos.data

import com.example.app_pos.model.ClaimStatus
import com.example.app_pos.model.Customer
import com.example.app_pos.model.SellerInfo
import com.example.app_pos.model.Transaction
import com.example.app_pos.model.TransactionType
import com.example.app_pos.model.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
    private val _transactions = MutableStateFlow(
        listOf(
            // Ahmet: 50 + 30 - 40 = 40,00 TL
            Transaction("t1", "c1", 5000, TransactionType.DEBT, "Ekmek, süt", "20.07.2026 09:15"),
            Transaction("t2", "c1", 3000, TransactionType.DEBT, "Peynir", "21.07.2026 10:40"),
            Transaction("t3", "c1", 4000, TransactionType.PAYMENT, "Nakit ödeme", "22.07.2026 18:00"),

            // Ayşe: 120 + 45 = 165,00 TL
            Transaction("t4", "c2", 12000, TransactionType.DEBT, "Market alışverişi", "18.07.2026 11:20"),
            Transaction("t5", "c2", 4500, TransactionType.DEBT, "Deterjan", "22.07.2026 16:05"),

            // Mehmet: 80 - 80 = 0 (fully paid)
            Transaction("t6", "c3", 8000, TransactionType.DEBT, "Kahvaltılık", "15.07.2026 08:30"),
            Transaction("t7", "c3", 8000, TransactionType.PAYMENT, "Kart ile ödeme", "19.07.2026 12:00"),

            // Fatma: 25,50 TL
            Transaction("t8", "c4", 2550, TransactionType.DEBT, "Çay, şeker", "23.07.2026 08:45"),

            // Hasan: 310 - 100 = 210,00 TL
            Transaction("t9", "c5", 31000, TransactionType.DEBT, "Toplu alışveriş", "10.07.2026 17:30"),
            Transaction("t10", "c5", 10000, TransactionType.PAYMENT, "Kısmi ödeme", "20.07.2026 14:10")
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
            User(
                userId = "u_owner",
                phone = "+905550000000",
                displayName = "Ahmet (Dükkan)",
                isBuyer = true,
                isSeller = true,
                email = null,
                sellerInfo = SellerInfo(shopName = "Ahmet Bakkal", shopPhone = "+902120000000"),
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

    /** The customer with this id, or null. Used to read a phone for the pay flow. */
    fun findCustomerById(customerId: String): Customer? {
        val raw = _customers.value.firstOrNull { it.id == customerId } ?: return null
        return Customer(
            customerId = raw.id,
            displayName = raw.name,
            phone = raw.phone,
            claimStatus = raw.claimStatus,
            claimedByUserId = raw.claimedByUserId,
            balanceMinor = balanceOf(raw.id, _transactions.value)
        )
    }

    /** The existing customer with this phone, or null — used to skip re-entry. */
    fun findCustomerByPhone(phone: String): Customer? {
        val target = normalizePhone(phone)
        val raw = _customers.value.firstOrNull { normalizePhone(it.phone) == target } ?: return null
        return Customer(
            customerId = raw.id,
            displayName = raw.name,
            phone = raw.phone,
            claimStatus = raw.claimStatus,
            claimedByUserId = raw.claimedByUserId,
            balanceMinor = balanceOf(raw.id, _transactions.value)
        )
    }

    /** Compares phones by digits only, so "+90 555 111" and "0555111" match. */
    private fun normalizePhone(phone: String?): String =
        phone?.filter { it.isDigit() }.orEmpty()

    /** All customers, with each balance recomputed whenever either list changes. */
    fun observeCustomers(): Flow<List<Customer>> =
        combine(_customers, _transactions) { people, ledger ->
            people.map { raw ->
                Customer(
                    customerId = raw.id,
                    displayName = raw.name,
                    phone = raw.phone,
                    claimStatus = raw.claimStatus,
                    claimedByUserId = raw.claimedByUserId,
                    balanceMinor = balanceOf(raw.id, ledger)
                )
            }
        }

    /** Ledger entries for one customer, newest first; re-emits on every write. */
    fun observeTransactions(customerId: String): Flow<List<Transaction>> =
        _transactions.map { ledger ->
            ledger.filter { it.customerId == customerId }.reversed()
        }

    /** Total the merchant is owed, recomputed whenever either list changes. */
    fun observeTotalReceivableMinor(): Flow<Long> =
        combine(_customers, _transactions) { people, ledger ->
            people.sumOf { balanceOf(it.id, ledger) }
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

    /**
     * The signed-in account for the current app. In app-pos that is the shopkeeper,
     * so this mock always emits the owner. On app-mobile this will follow the real
     * session (whoever logged in with phone + OTP).
     */
    fun observeCurrentUser(): Flow<User?> =
        _users.map { users -> users.firstOrNull { it.userId == "u_owner" } }

    /**
     * Sign-up == sign-in: if an account with this phone exists, return it; otherwise
     * create one (a buyer by default) and return it. This is the auto-register step
     * that runs after a successful OTP on app-mobile.
     */
    fun registerUser(phone: String, displayName: String): User {
        findUserByPhone(phone)?.let { return it }
        val user = User(
            userId = UUID.randomUUID().toString(),
            phone = normalizePhone(phone),
            displayName = displayName.trim(),
            isBuyer = true,
            isSeller = false,
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
     * The append-only rule in practice: a balance is the sum of its entries,
     * with DEBT adding and PAYMENT subtracting. Computed against a given ledger
     * snapshot so the same logic serves every observer.
     */
    private fun balanceOf(customerId: String, ledger: List<Transaction>): Long =
        ledger
            .filter { it.customerId == customerId }
            .sumOf { tx ->
                when (tx.type) {
                    TransactionType.DEBT -> tx.amountMinor
                    TransactionType.PAYMENT -> -tx.amountMinor
                }
            }

    /** Customer data without a balance — the balance is always derived. */
    private data class RawCustomer(
        val id: String,
        val name: String,
        val phone: String?,
        val claimStatus: ClaimStatus,
        val claimedByUserId: String?
    )
}
