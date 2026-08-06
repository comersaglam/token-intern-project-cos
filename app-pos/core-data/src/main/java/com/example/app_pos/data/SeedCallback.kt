package com.example.app_pos.data

import android.content.ContentValues
import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.RoomDatabase

/**
 * Seeds the demo data the first time the database is created, so the app opens with the
 * same customers/ledger the old FakeRepository had. Runs once (onCreate), on a Room IO
 * thread. Inserts go straight through SupportSQLiteDatabase (the DAOs are not available
 * inside the callback), matching the entity column names exactly.
 *
 * Seed = the shopkeeper u_owner and their book (c1..c5, ledger t1..t10). Balances that
 * result: Ahmet 40,00 / Ayşe 165,00 / Mehmet 0 / Fatma 25,50 / Hasan 210,00 — identical
 * to the fake, so screens look the same after the switch to Room.
 *
 * Timestamps are ISO-8601 UTC, matching the wire contract; the UI formats them for
 * display. The local times these replaced were Istanbul (UTC+3), so 09:15 local is
 * written as 06:15Z — the same instant, now unambiguous.
 */
internal class SeedCallback(private val context: Context) : RoomDatabase.Callback() {

    /**
     * Seeds on the first open of an empty database — whether it was just created or was
     * emptied by a destructive migration (every version bump of the mock phase drops and
     * recreates the tables, and the demo data is still wanted afterwards).
     *
     * onOpen rather than onCreate/onDestructiveMigration because BOTH of those fire while
     * the schema is still being built, so inserting from them fails with "no such table".
     * By onOpen the tables exist; the emptiness check keeps the seed a one-off instead of
     * something that runs on every launch.
     */
    override fun onOpen(db: SupportSQLiteDatabase) {
        super.onOpen(db)
        if (isEmpty(db)) seed(db)
    }

    /** True when no user row exists — the marker that this database was never seeded. */
    private fun isEmpty(db: SupportSQLiteDatabase): Boolean =
        db.query("SELECT COUNT(*) FROM users").use { cursor ->
            !cursor.moveToFirst() || cursor.getInt(0) == 0
        }

    private fun seed(db: SupportSQLiteDatabase) {

        user(db, "u_owner", "+905554443322", "", isSeller = true,
            shopName = null, createdAt = "2026-07-01T06:00:00Z")
        user(db, "u1", "+905551112233", "Ahmet Yılmaz", isSeller = false,
            shopName = null, createdAt = "2026-07-05T09:30:00Z")
        user(db, "u3", "+905554445566", "Mehmet Kaya", isSeller = false,
            shopName = null, createdAt = "2026-07-08T12:45:00Z")

        customer(db, "c1", "Ahmet Yılmaz", "+905551112233", "CLAIMED", "u1")
        customer(db, "c2", "Ayşe Demir", "+905552223344", "UNCLAIMED", null)
        customer(db, "c3", "Mehmet Kaya", "+905554445566", "CLAIMED", "u3")
        customer(db, "c4", "Fatma Şahin", "+905556667788", "UNCLAIMED", null)
        customer(db, "c5", "Hasan Öztürk", "+905558889900", "UNCLAIMED", null)

        // Ahmet (c1): 50 + 30 - 40 = 40,00
        tx(db, "t1", "u_owner", "c1", 5000, "DEBT", "Ekmek, süt", "2026-07-20T06:15:00Z")
        tx(db, "t2", "u_owner", "c1", 3000, "DEBT", "Peynir", "2026-07-21T07:40:00Z")
        tx(db, "t3", "u_owner", "c1", 4000, "PAYMENT", "Nakit ödeme", "2026-07-22T15:00:00Z")
        // Ayşe (c2): 120 + 45 = 165,00
        tx(db, "t4", "u_owner", "c2", 12000, "DEBT", "Market alışverişi", "2026-07-18T08:20:00Z")
        tx(db, "t5", "u_owner", "c2", 4500, "DEBT", "Deterjan", "2026-07-22T13:05:00Z")
        // Mehmet (c3): 80 - 80 = 0
        tx(db, "t6", "u_owner", "c3", 8000, "DEBT", "Kahvaltılık", "2026-07-15T05:30:00Z")
        tx(db, "t7", "u_owner", "c3", 8000, "PAYMENT", "Kart ile ödeme", "2026-07-19T09:00:00Z")
        // Fatma (c4): 25,50
        tx(db, "t8", "u_owner", "c4", 2550, "DEBT", "Çay, şeker", "2026-07-23T05:45:00Z")
        // Hasan (c5): 310 - 100 = 210,00
        tx(db, "t9", "u_owner", "c5", 31000, "DEBT", "Toplu alışveriş", "2026-07-10T14:30:00Z")
        tx(db, "t10", "u_owner", "c5", 10000, "PAYMENT", "Kısmi ödeme", "2026-07-20T11:10:00Z")
    }

    private fun user(
        db: SupportSQLiteDatabase, id: String, phone: String, name: String,
        isSeller: Boolean, shopName: String?, createdAt: String
    ) = db.insert("users", 0, ContentValues().apply {
        put("userId", id); put("phone", phone); put("displayName", name)
        put("isBuyer", 1); put("isSeller", if (isSeller) 1 else 0)
        putNull("email"); if (shopName == null) putNull("shopName") else put("shopName", shopName)
        putNull("shopPhone"); put("createdAt", createdAt)
    })

    private fun customer(
        db: SupportSQLiteDatabase, id: String, name: String, phone: String,
        claim: String, claimedBy: String?
    ) = db.insert("customers", 0, ContentValues().apply {
        put("customerId", id); put("displayName", name); put("phone", phone)
        put("claimStatus", claim)
        if (claimedBy == null) putNull("claimedByUserId") else put("claimedByUserId", claimedBy)
        put("createdAt", "2026-07-01T06:00:00Z")
    })

    private fun tx(
        db: SupportSQLiteDatabase, id: String, seller: String, customer: String,
        amount: Long, type: String, desc: String, createdAt: String
    ) = db.insert("transactions", 0, ContentValues().apply {
        put("transactionId", id); put("sellerId", seller); put("customerId", customer)
        put("amountMinor", amount); put("type", type); put("description", desc)
        putNull("basketId"); put("settledViaPgw", 0); putNull("receiptNo")
        put("createdAt", createdAt)
    })
}
