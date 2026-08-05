package com.example.mock_pos

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * mock-pos stands in for Token's payment gateway (PGW).
 *
 * The real flow: a basket app builds a barcode and sends the basket to the PGW,
 * which forwards an `orderBody` JSON to us. We have no basket app, so this file
 * fabricates that same orderBody — either from a plain amount keyed in on the
 * keypad (the money-only default) or from a canned sample basket (to demo the
 * item-level path). app-pos parses the JSON on the other side (OrderBodyParser).
 *
 * The two apps share no code (separate APKs), so the field shape is duplicated
 * here to match app-pos's parser; it could move to shared-contracts later.
 *
 * Scaling matches the real PGW: quantity and taxPercent are ×1000 (1000 = 1 unit
 * / 10%); price is per-unit minor units (kuruş).
 */
object MockBasket {

    private const val QUANTITY_SCALE = 1000L

    /** One line of a mock basket, in the PGW's field shape. */
    data class Item(
        val name: String,
        val price: Long,       // per-unit, kuruş
        val quantity: Long = QUANTITY_SCALE,
        val taxPercent: Long = 1000L,
        val sectionNo: Int = 1,
        val status: Int = 1,
        val type: Int = 0,
        val limit: Long = 0L
    )

    /** A named sample basket the merchant can pick instead of keying an amount. */
    data class Sample(val label: String, val items: List<Item>)

    /**
     * Canned baskets for the demo. Each turns into a real orderBody via [toJson].
     * These prove the item-level path end to end without a real basket app.
     */
    val SAMPLES: List<Sample> = listOf(
        Sample(
            "Market sepeti (3 kalem)",
            listOf(
                Item("Ekmek", price = 1500, quantity = 2000),   // 15,00 × 2 = 30,00
                Item("Süt", price = 3200),                       // 32,00 × 1 = 32,00
                Item("Yumurta (10'lu)", price = 4500)            // 45,00 × 1 = 45,00
            )                                                    // total = 107,00 TL
        ),
        Sample(
            "Tek ürün (Yiyecek)",
            listOf(Item("Yiyecek", price = 10000))               // 100,00 TL
        )
    )

    /**
     * Builds an orderBody for a money-only handoff: a single synthetic line whose
     * total equals the entered amount, so app-pos's totalMinor() gives back
     * exactly [amountMinor]. This is the default VERESİYE path.
     */
    fun moneyOnly(amountMinor: Long): String =
        toJson(listOf(Item(name = "Veresiye", price = amountMinor, taxPercent = 0L)))

    /** Builds an orderBody from a picked sample basket. */
    fun fromSample(sample: Sample): String = toJson(sample.items)

    /** Serialises items into the PGW orderBody JSON shape app-pos expects. */
    private fun toJson(items: List<Item>): String {
        val itemsArray = JSONArray()
        for (item in items) {
            itemsArray.put(
                JSONObject().apply {
                    put("name", item.name)
                    put("price", item.price)
                    put("quantity", item.quantity)
                    put("taxPercent", item.taxPercent)
                    put("sectionNo", item.sectionNo)
                    put("status", item.status)
                    put("type", item.type)
                    put("limit", item.limit)
                }
            )
        }
        return JSONObject().apply {
            put("basketID", UUID.randomUUID().toString())
            put("createInvoice", false)
            put("documentType", 0)
            put("isVoid", false)
            put("items", itemsArray)
        }.toString()
    }
}
