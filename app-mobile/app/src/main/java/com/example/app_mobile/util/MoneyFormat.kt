package com.example.app_mobile.util

import java.util.Locale

/**
 * Formats a minor-unit amount (kuruş) for display: 5000 -> "50,00 TL".
 *
 * Money is stored as Long in minor units everywhere in the app; formatting is a
 * presentation concern, so it lives here rather than in the model.
 *
 * Copied from app-pos (separate APKs cannot share a module — same reason the
 * :core-domain models are copied).
 */
fun Long.toTlString(): String {
    val lira = this / 100
    val kurus = this % 100
    return String.format(Locale("tr", "TR"), "%,d,%02d TL", lira, kurus)
}
