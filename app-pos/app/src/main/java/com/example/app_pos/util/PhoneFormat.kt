package com.example.app_pos.util

/**
 * Turns what the merchant types into one canonical phone format, so every number
 * in the system looks the same and two entries for one person always match.
 *
 * Input rule: a Turkish mobile number as digits, 10 long, starting with 0
 * (e.g. "0555 444 3322" — spaces are ignored). Anything else is invalid.
 *
 * Stored form: E.164 with the country code, leading 0 dropped:
 * "0555 444 3322" -> "+905554443322".
 */
object PhoneFormat {

    /** Preview shown in the input field so the expected shape is obvious. */
    const val HINT = "0555 444 3322"

    /**
     * Returns the canonical "+90…" form, or null if the input is not a valid
     * 10-digit number starting with 0. Callers use null to show a format error.
     */
    fun toStored(input: String): String? {
        val digits = input.filter { it.isDigit() }
        // Idempotent: an already-stored number ("+905554443322" -> "905554443322",
        // 12 digits starting with 90) is returned as-is, so calling toStored twice is
        // safe. This prevents a re-conversion from wrongly rejecting a stored number.
        if (digits.length == 12 && digits.startsWith("90")) return "+$digits"
        // A raw local number: 11 digits starting with 0, e.g. "0555 444 3322".
        if (digits.length != 11 || !digits.startsWith("0")) return null
        // Drop the leading 0, prepend the country code.
        return "+90" + digits.substring(1)
    }
}
