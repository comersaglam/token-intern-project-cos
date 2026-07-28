package com.example.mock_pos.util

import java.util.Locale

/**
 * Formats a minor-unit amount (kuruş) for display: 5000 -> "50,00 TL".
 *
 * Money is kept as Long in minor units, so no floating point is ever involved;
 * formatting is a presentation concern and lives here. This is a deliberate copy
 * of app-pos's util/MoneyFormat.kt: the two apps do not import each other (each
 * is a separate APK), and this helper is small enough to duplicate. It could move
 * to shared-contracts later.
 */
fun Long.toTlString(): String {
    val lira = this / 100
    val kurus = this % 100
    return String.format(Locale("tr", "TR"), "%,d,%02d TL", lira, kurus)
}
