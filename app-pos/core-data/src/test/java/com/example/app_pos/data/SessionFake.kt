package com.example.app_pos.data

import com.example.app_pos.data.local.LocalSource
import com.example.app_pos.model.Customer
import com.example.app_pos.model.CustomerLookup
import com.example.app_pos.model.OrderBody
import com.example.app_pos.model.Transaction
import com.example.app_pos.model.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf

/**
 * A [LocalSource] holding a fixed user list and nothing else.
 *
 * The session tests only exercise the gate — which user the persisted token resolves to —
 * so the ledger side is stubbed rather than simulated. Standing up Room to reach that logic
 * would test Room, not the composition this phase introduces.
 */
class FakeLocalSource(users: List<User> = emptyList()) : LocalSource {

    private val _users = MutableStateFlow(users)

    /** Set by [logout], so a test can assert the local half was notified too. */
    var loggedOut = false
        private set

    override fun observeAllUsers(): Flow<List<User>> = _users.asStateFlow()

    override suspend fun findUserByPhone(phone: String): User? =
        _users.value.firstOrNull { trailingDigits(it.phone) == trailingDigits(phone) }

    /**
     * The last 10 digits, so "+905554443322" and "05554443322" are the same person — the
     * Kotlin stand-in for the DAO's REPLACE-based digit match. Without it every sign-in
     * test would fail on a formatting difference rather than on the logic under test.
     */
    private fun trailingDigits(phone: String): String =
        phone.filter { it.isDigit() }.takeLast(10)

    override suspend fun logout() {
        loggedOut = true
    }

    // --- the rest is out of scope for the session tests -----------------------

    override fun isSessionValid(): Boolean = false
    override fun currentSellerId(): String? = null
    override fun observeCurrentUser(): Flow<User?> = flowOf(null)
    override val isPairedWithApp: Flow<Boolean> = flowOf(false)
    override suspend fun login(phone: String?): Boolean = false
    override suspend fun pairWithApp() = Unit
    override suspend fun registerUser(phone: String, displayName: String, isSeller: Boolean): User =
        error("not used")
    override suspend fun setSeller(userId: String, shopName: String, shopPhone: String?) = Unit
    override suspend fun updateDisplayName(userId: String, displayName: String) = Unit
    override suspend fun updateShopName(userId: String, shopName: String) = Unit
    override fun observeCustomers(sellerId: String): Flow<List<Customer>> = flowOf(emptyList())
    override suspend fun addCustomer(displayName: String, phone: String): String = ""
    override suspend fun lookupCustomerForSeller(sellerId: String, phone: String): CustomerLookup =
        CustomerLookup.New
    override suspend fun findCustomerById(sellerId: String, customerId: String): Customer? = null
    override suspend fun findCustomerByPhone(sellerId: String, phone: String): Customer? = null
    override fun observeTransactions(sellerId: String, customerId: String): Flow<List<Transaction>> =
        flowOf(emptyList())
    override fun observeTotalReceivableMinor(sellerId: String): Flow<Long> = flowOf(0L)
    override fun observeBalance(sellerId: String, customerId: String): Flow<Long> = flowOf(0L)
    override suspend fun addTransaction(transaction: Transaction, orderBody: OrderBody?) = Unit
}

/** The seed's shopkeeper, the number the device tests sign in with. */
fun testUser(
    userId: String = "u_owner",
    phone: String = "+905554443322"
): User = User(
    userId = userId,
    phone = phone,
    displayName = "Ahmet Demirtaş",
    isBuyer = true,
    isSeller = true,
    email = null,
    sellerInfo = null,
    createdAt = "2026-07-01T09:00:00Z"
)
