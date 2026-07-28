package com.example.app_pos.data

import com.example.app_pos.model.ClaimStatus
import com.example.app_pos.model.Customer
import com.example.app_pos.model.Transaction
import com.example.app_pos.model.TransactionType
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
            RawCustomer("c1", "Ahmet Yılmaz", "+905551112233", ClaimStatus.CLAIMED),
            RawCustomer("c2", "Ayşe Demir", "+905552223344", ClaimStatus.UNCLAIMED),
            RawCustomer("c3", "Mehmet Kaya", "+905554445566", ClaimStatus.CLAIMED),
            RawCustomer("c4", "Fatma Şahin", "+905556667788", ClaimStatus.UNCLAIMED),
            RawCustomer("c5", "Hasan Öztürk", "+905558889900", ClaimStatus.UNCLAIMED)
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
            RawCustomer(id, displayName.trim(), normalizePhone(phone), ClaimStatus.UNCLAIMED)
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
        val claimStatus: ClaimStatus
    )
}
