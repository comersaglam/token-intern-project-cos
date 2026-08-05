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
 */
internal class SeedCallback(private val context: Context) : RoomDatabase.Callback() {

    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)

        user(db, "u_owner", "+905554443322", "", isSeller = true,
            shopName = null, createdAt = "01.07.2026 09:00")
        user(db, "u1", "+905551112233", "Ahmet Yılmaz", isSeller = false,
            shopName = null, createdAt = "05.07.2026 12:30")
        user(db, "u3", "+905554445566", "Mehmet Kaya", isSeller = false,
            shopName = null, createdAt = "08.07.2026 15:45")

        customer(db, "c1", "Ahmet Yılmaz", "+905551112233", "CLAIMED", "u1")
        customer(db, "c2", "Ayşe Demir", "+905552223344", "UNCLAIMED", null)
        customer(db, "c3", "Mehmet Kaya", "+905554445566", "CLAIMED", "u3")
        customer(db, "c4", "Fatma Şahin", "+905556667788", "UNCLAIMED", null)
        customer(db, "c5", "Hasan Öztürk", "+905558889900", "UNCLAIMED", null)

        // Ahmet (c1): 50 + 30 - 40 = 40,00
        tx(db, "t1", "u_owner", "c1", 5000, "DEBT", "Ekmek, süt", "20.07.2026 09:15")
        tx(db, "t2", "u_owner", "c1", 3000, "DEBT", "Peynir", "21.07.2026 10:40")
        tx(db, "t3", "u_owner", "c1", 4000, "PAYMENT", "Nakit ödeme", "22.07.2026 18:00")
        // Ayşe (c2): 120 + 45 = 165,00
        tx(db, "t4", "u_owner", "c2", 12000, "DEBT", "Market alışverişi", "18.07.2026 11:20")
        tx(db, "t5", "u_owner", "c2", 4500, "DEBT", "Deterjan", "22.07.2026 16:05")
        // Mehmet (c3): 80 - 80 = 0
        tx(db, "t6", "u_owner", "c3", 8000, "DEBT", "Kahvaltılık", "15.07.2026 08:30")
        tx(db, "t7", "u_owner", "c3", 8000, "PAYMENT", "Kart ile ödeme", "19.07.2026 12:00")
        // Fatma (c4): 25,50
        tx(db, "t8", "u_owner", "c4", 2550, "DEBT", "Çay, şeker", "23.07.2026 08:45")
        // Hasan (c5): 310 - 100 = 210,00
        tx(db, "t9", "u_owner", "c5", 31000, "DEBT", "Toplu alışveriş", "10.07.2026 17:30")
        tx(db, "t10", "u_owner", "c5", 10000, "PAYMENT", "Kısmi ödeme", "20.07.2026 14:10")
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
        put("createdAt", "01.07.2026 09:00")
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
