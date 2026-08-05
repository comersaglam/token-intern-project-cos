package com.example.app_pos.util

import java.util.Locale

/**
 * Formats a minor-unit amount (kuruş) for display: 5000 -> "50,00 TL".
 *
 * Money is stored as Long in minor units everywhere in the app; formatting is a
 * presentation concern, so it lives here rather than in the model.
 */
private val TR = Locale.forLanguageTag("tr-TR")

/**
 * The sign is taken off the whole value before splitting: Kotlin's / and % both
 * truncate toward zero, so a negative remainder would print the minus twice
 * (-75622850 would render as "-756.228,-50 TL"). Balances go negative when a
 * customer overpays, so this is reachable.
 */
fun Long.toTlString(): String {
    val sign = if (this < 0) "-" else ""
    val abs = kotlin.math.abs(this)
    return String.format(TR, "%s%,d,%02d TL", sign, abs / 100, abs % 100)
}
