package com.example.app_pos.data

import android.content.ContentValues
import android.content.Context
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Seeds the demo data the first time the database is created, so the app opens with the
 * same accounts/ledger the old FakeRepository had. Runs once (onCreate), on a Room IO
 * thread. Inserts go straight through SupportSQLiteDatabase (the DAOs are not available
 * inside the callback), matching the entity column names exactly.
 *
 * The seed spans TWO sellers on purpose, so the buyer's debt list is not trivially one
 * row: u1 holds two customer records (c1 at Ahmet Bakkal, m1 at Ayşe Market) — the
 * multi-shop case that the per-seller balance rule exists for.
 *
 * Resulting balances: u1 owes 40,00 to Ahmet Bakkal + 100,00 to Ayşe Market (140,00
 * total); u_owner owes 60,00 to Ayşe Market; u_owner's own book totals 235,50
 * (Mehmet 0 / Fatma 25,50 / Hasan 210,00) — identical to the fake.
 *
 * Insert order matters: customers.claimedByUserId is a FK to users.
 */
internal class SeedCallback(private val context: Context) : RoomDatabase.Callback() {

    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)

        // Two shopkeepers (both also buyers — one account, two roles). A person's name
        // and their shop's name are deliberately DIFFERENT: buyer-facing screens show
        // the shop, seller-facing screens show the person, and identical names would
        // hide a screen pulling the wrong one.
        user(db, "u_owner", "+905554443322", "Ahmet Demirtaş", isSeller = true,
            shopName = "Ahmet Bakkal", shopPhone = "+902121112233", createdAt = "01.07.2026 09:00")
        user(db, "u_market", "+905553334455", "Ayşe Korkmaz", isSeller = true,
            shopName = "Ayşe Market", shopPhone = "+902123334455", createdAt = "02.07.2026 09:00")
        // … and two plain buyers.
        user(db, "u1", "+905551112233", "Ahmet Yılmaz", isSeller = false,
            shopName = null, shopPhone = null, createdAt = "05.07.2026 12:30")
        user(db, "u3", "+905554445566", "Mehmet Kaya", isSeller = false,
            shopName = null, shopPhone = null, createdAt = "08.07.2026 15:45")

        // u1 is one person with a record in each shop's book (c1 and m1).
        customer(db, "c1", "Ahmet Yılmaz", "+905551112233", "CLAIMED", "u1")
        customer(db, "m1", "Ahmet Y.", "+905551112233", "CLAIMED", "u1")
        // The shopkeeper's own record as a buyer at the other shop — a customer row is
        // a PERSON, so it carries their name, not their shop's.
        customer(db, "o1", "Ahmet Demirtaş", "+905554443322", "CLAIMED", "u_owner")
        customer(db, "c3", "Mehmet Kaya", "+905554445566", "CLAIMED", "u3")
        customer(db, "c4", "Fatma Şahin", "+905556667788", "UNCLAIMED", null)
        customer(db, "c5", "Hasan Öztürk", "+905558889900", "UNCLAIMED", null)

        // u1 @ Ahmet Bakkal (c1): 50 + 30 - 40 = 40,00
        tx(db, "t1", "u_owner", "c1", 5000, "DEBT", "Ekmek, süt", "20.07.2026 09:15")
        tx(db, "t2", "u_owner", "c1", 3000, "DEBT", "Peynir", "21.07.2026 10:40")
        tx(db, "t3", "u_owner", "c1", 4000, "PAYMENT", "Nakit ödeme", "22.07.2026 18:00")
        // u1 @ Ayşe Market (m1): 120 + 45 - 65 = 100,00
        tx(db, "t4", "u_market", "m1", 12000, "DEBT", "Market alışverişi", "18.07.2026 11:20")
        tx(db, "t5", "u_market", "m1", 4500, "DEBT", "Deterjan", "22.07.2026 16:05")
        tx(db, "t6", "u_market", "m1", 6500, "PAYMENT", "Kısmi ödeme", "24.07.2026 13:00")
        // u_owner @ Ayşe Market (o1): 90 - 30 = 60,00
        tx(db, "t7", "u_market", "o1", 9000, "DEBT", "Kırtasiye", "16.07.2026 10:00")
        tx(db, "t8", "u_market", "o1", 3000, "PAYMENT", "Nakit ödeme", "23.07.2026 15:30")
        // u_owner's OWN book: c3 = 0, c4 = 25,50, c5 = 210,00
        tx(db, "t9", "u_owner", "c3", 8000, "DEBT", "Kahvaltılık", "15.07.2026 08:30")
        tx(db, "t10", "u_owner", "c3", 8000, "PAYMENT", "Kart ile ödeme", "19.07.2026 12:00")
        tx(db, "t11", "u_owner", "c4", 2550, "DEBT", "Çay, şeker", "23.07.2026 08:45")
        tx(db, "t12", "u_owner", "c5", 31000, "DEBT", "Toplu alışveriş", "10.07.2026 17:30")
        tx(db, "t13", "u_owner", "c5", 10000, "PAYMENT", "Kısmi ödeme", "20.07.2026 14:10")

        // One pending approval per demo account, so the Onaylar tab is never empty.
        approval(db, "p1", "u_owner", "Ahmet Bakkal", "u1", "c1", 5000, "DEBT",
            "Ekmek, süt", "25.07.2026 10:05")
        approval(db, "p2", "u_market", "Ayşe Market", "u_owner", "o1", 7500, "DEBT",
            "Temizlik malzemesi", "25.07.2026 11:20")
    }

    private fun user(
        db: SupportSQLiteDatabase, id: String, phone: String, name: String,
        isSeller: Boolean, shopName: String?, shopPhone: String?, createdAt: String
    ) = db.insert("users", 0, ContentValues().apply {
        put("userId", id); put("phone", phone); put("displayName", name)
        put("isBuyer", 1); put("isSeller", if (isSeller) 1 else 0)
        putNull("email")
        if (shopName == null) putNull("shopName") else put("shopName", shopName)
        if (shopPhone == null) putNull("shopPhone") else put("shopPhone", shopPhone)
        put("createdAt", createdAt)
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

    private fun approval(
        db: SupportSQLiteDatabase, id: String, sellerId: String, shopName: String,
        targetUserId: String, customerId: String, amount: Long, type: String,
        desc: String, requestedAt: String
    ) = db.insert("approvals", 0, ContentValues().apply {
        put("approvalId", id)
        // Today's flow is seller-initiated; the direction fields record that explicitly.
        put("initiatorUserId", sellerId); put("initiatorRole", "SELLER")
        put("targetUserId", targetUserId); put("sellerId", sellerId)
        put("shopName", shopName); put("customerId", customerId)
        put("amountMinor", amount); put("type", type); put("description", desc)
        put("channel", "APP_PUSH"); put("status", "PENDING")
        put("requestedAt", requestedAt)
    })
}
