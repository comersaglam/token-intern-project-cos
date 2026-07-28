package com.example.mock_pos

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Keypad state for the mock payment screen.
 *
 * This mirrors the amount-entry logic that used to live in app-pos's
 * SaleViewModel: money is a Long in minor units (kuruş), digits shift in from the
 * right the way a real POS keypad behaves. Once a method is chosen the amount is
 * handed to app-pos over an Intent, so the keypad belongs here, in the payment
 * app, not in the veresiye app.
 */
class PaymentViewModel : ViewModel() {

    private val _amountMinor = MutableStateFlow(0L)

    /** Amount being charged; observed by the payment screen. */
    val amountMinor: StateFlow<Long> = _amountMinor.asStateFlow()

    /**
     * Appends one digit, shifting the existing amount one place left.
     * 0 -> "5" -> 5 (0,05) -> "0" -> 50 (0,50) -> "0" -> 500 (5,00)
     */
    fun onDigit(digit: Int) {
        val next = _amountMinor.value * 10 + digit
        // Cap at 9.999.999,99 so a stuck key cannot overflow the Long.
        if (next <= 999_999_999L) {
            _amountMinor.value = next
        }
    }

    /** Removes the last digit — the inverse of onDigit. */
    fun onBackspace() {
        _amountMinor.value /= 10
    }

    /** Resets the amount to zero. */
    fun onClear() {
        _amountMinor.value = 0L
    }

    /** A sale can only proceed with a non-zero amount. */
    fun hasAmount(): Boolean = _amountMinor.value > 0L
}
