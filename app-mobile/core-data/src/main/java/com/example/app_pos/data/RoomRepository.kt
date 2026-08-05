package com.example.app_pos.data

import com.example.app_pos.data.db.AppDatabase
import com.example.app_pos.data.db.entity.CustomerEntity
import com.example.app_pos.data.db.toDomain
import com.example.app_pos.data.db.toEntity
import com.example.app_pos.model.ClaimStatus
import com.example.app_pos.model.Customer
import com.example.app_pos.model.CustomerLookup
import com.example.app_pos.model.PendingApproval
import com.example.app_pos.model.Repository
import com.example.app_pos.model.SellerDebt
import com.example.app_pos.model.Transaction
import com.example.app_pos.model.TransactionType
import com.example.app_pos.model.User
import com.example.app_pos.model.balanceOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * Room-backed [Repository]: the persistent replacement for FakeRepository. Same public
 * surface, so the ViewModels do not change — the offline-first payoff.
 *
 * What persists: users, customers, the append-only ledger, approvals. What stays in RAM
 * (mock, like before): the session + pairing flag — a real backend returns a JWT stored
 * in DataStore later, so only these bodies change then. Balances are never stored; they
 * are summed from the ledger (DAO SUM or the pure balanceOf).
 */
class RoomRepository(private val db: AppDatabase) : Repository {

    private val users = db.userDao()
    private val customers = db.customerDao()
    private val transactions = db.transactionDao()
    private val approvals = db.approvalDao()

    // --- session + pairing (RAM, mock — see class doc) -----------------------

    private data class Session(val userId: String, val token: String, val expiresAt: Long)
    private val session = MutableStateFlow<Session?>(null)
    private val _isPairedWithApp = MutableStateFlow(false)
    override val isPairedWithApp: Flow<Boolean> = _isPairedWithApp.asStateFlow()

    override fun isSessionValid(): Boolean =
        session.value?.let { it.expiresAt > System.currentTimeMillis() } == true

    override fun currentUserId(): String? =
        session.value?.takeIf { it.expiresAt > System.currentTimeMillis() }?.userId

    override suspend fun login(phone: String?): Boolean {
        val user = phone?.let { findUserByPhone(it) } ?: return false
        val now = System.currentTimeMillis()
        session.value = Session(user.userId, UUID.randomUUID().toString(), now + SESSION_TTL_MILLIS)
        return true
    }

    override suspend fun logout() {
        session.value = null
        _isPairedWithApp.value = false
    }

    override suspend fun pairWithApp() {
        _isPairedWithApp.value = true
    }

    override fun observeCurrentUser(): Flow<User?> =
        // Re-query whenever the user table or the session changes, so a profile edit or
        // a login/logout is reflected live (mirrors FakeRepository's combine).
        combine(users.observeAll(), session) { list, s ->
            val valid = s != null && s.expiresAt > System.currentTimeMillis()
            if (!valid) null else list.firstOrNull { it.userId == s!!.userId }?.toDomain()
        }

    // --- users / profile -----------------------------------------------------

    override suspend fun findUserByPhone(phone: String): User? =
        users.findByPhoneDigits(phone.digits())?.toDomain()

    override suspend fun registerUser(phone: String, displayName: String, isSeller: Boolean): User {
        findUserByPhone(phone)?.let { return it }
        val user = User(
            userId = UUID.randomUUID().toString(),
            phone = storedPhone(phone),
            displayName = displayName.trim(),
            isBuyer = true,
            isSeller = isSeller,
            email = null,
            sellerInfo = null,
            createdAt = nowStamp()
        )
        users.insert(user.toEntity())
        return user
    }

    override suspend fun setSeller(userId: String, shopName: String, shopPhone: String?) {
        val existing = users.findById(userId) ?: return
        users.update(existing.copy(isSeller = true, shopName = shopName.trim(), shopPhone = shopPhone))
    }

    override suspend fun updateShopName(userId: String, shopName: String) {
        // Keeps any existing shopPhone (unlike setSeller, which replaces the whole info).
        val existing = users.findById(userId) ?: return
        users.update(existing.copy(isSeller = true, shopName = shopName.trim()))
    }

    override suspend fun updateDisplayName(userId: String, displayName: String) {
        val existing = users.findById(userId) ?: return
        users.update(existing.copy(displayName = displayName.trim()))
    }

    override suspend fun updateEmail(userId: String, email: String) {
        val existing = users.findById(userId) ?: return
        users.update(existing.copy(email = email.trim().ifBlank { null }))
    }

    override suspend fun shopNameOf(sellerId: String): String =
        users.findById(sellerId)?.shopName ?: sellerId

    override suspend fun shopPhoneOf(sellerId: String): String? =
        users.findById(sellerId)?.shopPhone

    // --- claim ---------------------------------------------------------------

    override suspend fun claimCustomerForUser(userId: String, phone: String): List<Customer> {
        customers.claimByPhoneDigits(userId, phone.digits())
        // The claimed records carry no balance here: the claim is seller-independent
        // (it links an identity), and every screen derives balances per seller anyway.
        return customers.claimedBy(userId).map { it.toDomain(0L) }
    }

    // --- customers (seller-scoped) -------------------------------------------

    override fun observeCustomers(sellerId: String): Flow<List<Customer>> =
        // Balance each listed customer against this seller's ledger. Kept reactive by
        // combining the seller's customer rows with the whole ledger.
        combine(customers.observeForSeller(sellerId), transactions.observeAll()) { rows, ledgerRaw ->
            val ledger = ledgerRaw.map { it.toDomain() }
            rows.map { it.toDomain(balanceOf(sellerId, it.customerId, ledger)) }
        }

    override suspend fun addCustomer(displayName: String, phone: String): String {
        val id = UUID.randomUUID().toString()
        customers.insert(
            CustomerEntity(
                customerId = id,
                displayName = displayName.trim(),
                phone = storedPhone(phone),
                claimStatus = ClaimStatus.UNCLAIMED.name,
                claimedByUserId = null,
                createdAt = nowStamp()
            )
        )
        return id
    }

    override suspend fun findCustomerById(sellerId: String, customerId: String): Customer? {
        val row = customers.findById(customerId) ?: return null
        return row.toDomain(balanceOfCustomer(sellerId, customerId))
    }

    override suspend fun findCustomerByPhone(sellerId: String, phone: String): Customer? {
        val row = customers.findByPhoneDigits(phone.digits()) ?: return null
        return row.toDomain(balanceOfCustomer(sellerId, row.customerId))
    }

    override suspend fun lookupCustomerForSeller(sellerId: String, phone: String): CustomerLookup {
        val digits = phone.digits()
        val row = customers.findByPhoneDigits(digits) ?: return CustomerLookup.New
        val existing = row.toDomain(balanceOfCustomer(sellerId, row.customerId))
        // Mine = we already share at least one ledger entry. Otherwise the person is
        // simply known to another shop, and this seller may add them to their own book.
        return if (customers.countForSellerByPhoneDigits(sellerId, digits) > 0) {
            CustomerLookup.AlreadyMine(existing)
        } else {
            CustomerLookup.KnownToOtherSeller(existing)
        }
    }

    // --- ledger (seller-scoped) ----------------------------------------------

    override fun observeTransactions(sellerId: String, customerId: String): Flow<List<Transaction>> =
        transactions.observeForSellerCustomer(sellerId, customerId).map { list -> list.map { it.toDomain() } }

    override fun observeTotalReceivableMinor(sellerId: String): Flow<Long> =
        transactions.observeTotalReceivable(sellerId)

    // --- ledger (buyer-scoped) -----------------------------------------------

    override fun observeMyDebtsBySeller(userId: String): Flow<List<SellerDebt>> =
        transactions.observeDebtsBySeller(userId).map { rows ->
            // Sorted here rather than in SQL so the shop-name fallback and the ordering
            // stay in one place (mirrors the fake's sortedByDescending).
            rows.map { it.toDomain() }.sortedByDescending { it.balanceMinor }
        }

    override fun observeMyTotalDebtMinor(userId: String): Flow<Long> =
        transactions.observeBuyerTotalDebt(userId)

    override fun observeMyTransactions(userId: String, sellerId: String): Flow<List<Transaction>> =
        transactions.observeForBuyerSeller(userId, sellerId).map { list -> list.map { it.toDomain() } }

    override fun observeMyBalanceWithSeller(userId: String, sellerId: String): Flow<Long> =
        transactions.observeBuyerBalanceWithSeller(userId, sellerId)

    // --- approvals -----------------------------------------------------------

    override fun observePendingApprovals(userId: String): Flow<List<PendingApproval>> =
        approvals.observePendingFor(userId).map { list -> list.map { it.toDomain() } }

    override suspend fun approvePending(approvalId: String) {
        val approval = approvals.findById(approvalId) ?: return
        addTransaction(
            Transaction(
                transactionId = UUID.randomUUID().toString(),
                sellerId = approval.sellerId,
                // The record the request was raised against — not re-resolved here, so
                // the entry always lands in the same book the requester intended.
                customerId = approval.customerId,
                amountMinor = approval.amountMinor,
                type = TransactionType.valueOf(approval.type),
                description = approval.description.orEmpty(),
                createdAt = nowStamp()
            )
        )
        // Decided rows are kept (status change, not delete) so the trail survives; the
        // pending query filters on status, so the buyer's list looks the same as before.
        approvals.setStatus(approvalId, "APPROVED")
    }

    override suspend fun rejectPending(approvalId: String) {
        approvals.setStatus(approvalId, "REJECTED")
    }

    override suspend fun requestApproval(
        fromUserId: String,
        sellerId: String,
        customerId: String,
        amountMinor: Long,
        type: TransactionType,
        description: String
    ) {
        val row = customers.findById(customerId) ?: return
        ApprovalService.requestApproval(fromUserId, row.phone, amountMinor, type.name)

        // The COUNTERPARTY approves, never the initiator — that is the whole point of the
        // gate. Which side that is depends on who started it: a seller writing to their
        // book needs the customer's approval; a buyer paying needs the seller's (they
        // confirm receipt). Deriving it from the customer record alone would send a
        // buyer-initiated payment back to the buyer.
        val approverUserId =
            if (fromUserId == sellerId) row.claimedByUserId   // seller → the customer
            else sellerId                                     // buyer  → the shop
        if (approverUserId != null) {
            // Has the app: raise a pending approval; nothing reaches the ledger yet.
            // The card names the OTHER side, so it reads right whichever way it points.
            val counterpartyName =
                if (fromUserId == sellerId) shopNameOf(sellerId) else row.displayName
            approvals.insert(
                PendingApproval(
                    approvalId = UUID.randomUUID().toString(),
                    sellerId = sellerId,
                    counterpartyName = counterpartyName,
                    approverUserId = approverUserId,
                    customerId = customerId,
                    amountMinor = amountMinor,
                    type = type,
                    description = description,
                    requestedAt = nowStamp()
                ).toEntity(initiatorUserId = fromUserId)
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

    override suspend fun initiatePayment(userId: String, sellerId: String, amountMinor: Long): Boolean {
        // No shared record with this seller means there is nothing to pay against; the
        // caller reports that rather than guessing at another shop's record.
        val customerId = transactions.customerIdForBuyerSeller(userId, sellerId) ?: return false
        requestApproval(
            fromUserId = userId,
            sellerId = sellerId,
            customerId = customerId,
            amountMinor = amountMinor,
            type = TransactionType.PAYMENT,
            description = "Uygulamadan ödeme"
        )
        return true
    }

    override suspend fun addTransaction(transaction: Transaction) {
        transactions.insert(transaction.toEntity())
    }

    // --- helpers -------------------------------------------------------------

    private suspend fun balanceOfCustomer(sellerId: String, customerId: String): Long {
        // A one-shot balance for a point read (find*). Reactive reads use the DAO SUM Flow.
        val ledger = transactions.allOnce().map { it.toDomain() }
        return balanceOf(sellerId, customerId, ledger)
    }

    private fun String.digits(): String = filter { it.isDigit() }

    // Store E.164 when the input is a valid TR local number, else keep the digits so a
    // lookup by digits still matches (mirrors FakeRepository's fallback).
    private fun storedPhone(input: String): String {
        val d = input.digits()
        return when {
            d.length == 12 && d.startsWith("90") -> "+$d"
            d.length == 11 && d.startsWith("0") -> "+90" + d.substring(1)
            else -> d
        }
    }

    private fun nowStamp(): String =
        java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.forLanguageTag("tr-TR"))
            .format(java.util.Date())

    private companion object {
        const val SESSION_TTL_MILLIS = 7L * 24 * 60 * 60 * 1000
    }
}
