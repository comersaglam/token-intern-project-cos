package com.example.app_pos.data

import com.example.app_pos.model.OrderBody
import com.example.app_pos.model.OrderItem
import org.json.JSONException
import org.json.JSONObject

/**
 * Turns the PGW's `orderBody` JSON string into the pure OrderBody domain model.
 *
 * Uses org.json (bundled with Android — no extra dependency). This is the ONLY
 * place the wire format is read, so the JSON field names and the 1000-scaling
 * live in one spot; the rest of the app works with the typed OrderBody.
 *
 * Field names match Token's real orderBody:
 *   { "basketID", "createInvoice", "documentType", "isVoid",
 *     "items": [ { "name", "price", "quantity", "taxPercent",
 *                  "sectionNo", "status", "type", "limit" } ] }
 */
object OrderBodyParser {

    /**
     * Parses the JSON, or returns null if it is missing or malformed. A null tells
     * the caller the handoff carried no usable basket, so it can fall back safely
     * instead of crashing on bad input from another app.
     */
    fun parse(json: String?): OrderBody? {
        if (json.isNullOrBlank()) return null
        return try {
            val root = JSONObject(json)
            val itemsArray = root.optJSONArray("items")
            val items = buildList {
                if (itemsArray != null) {
                    for (i in 0 until itemsArray.length()) {
                        val o = itemsArray.getJSONObject(i)
                        add(
                            OrderItem(
                                name = o.optString("name", ""),
                                price = o.optLong("price", 0L),
                                quantity = o.optLong("quantity", OrderItem.QUANTITY_SCALE),
                                taxPercent = o.optLong("taxPercent", 0L),
                                sectionNo = o.optInt("sectionNo", 0),
                                status = o.optInt("status", 0),
                                type = o.optInt("type", 0),
                                limit = o.optLong("limit", 0L)
                            )
                        )
                    }
                }
            }
            OrderBody(
                // Real PGW uses "basketID"; accept "basketId" too, just in case.
                basketId = root.optString("basketID", root.optString("basketId", "")),
                createInvoice = root.optBoolean("createInvoice", false),
                documentType = root.optInt("documentType", 0),
                isVoid = root.optBoolean("isVoid", false),
                items = items
            )
        } catch (e: JSONException) {
            null
        }
    }
}
