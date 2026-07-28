package com.example.mock_pos

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.mock_pos.databinding.ActivityMainBinding
import com.example.mock_pos.util.toTlString
import kotlinx.coroutines.launch

/**
 * Mock POS payment screen: key in an amount, pick a payment method.
 *
 * This stands in for Token's real POS payment app. Card / Meal Card / Cash are
 * mocked (Toast only). VERESİYE hands the amount off to the app-pos app over an
 * Intent — payment is a separate concern from the veresiye (credit) ledger, so
 * it lives in a separate app.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val viewModel: PaymentViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        bindKeypad()
        bindPaymentMethods()
        observeAmount()
    }

    /**
     * Each time the screen returns to the foreground — including coming back from
     * the app-pos handoff — start a fresh sale so a previous amount never lingers.
     */
    override fun onResume() {
        super.onResume()
        viewModel.onClear()
    }

    private fun bindKeypad() = with(binding) {
        key0.setOnClickListener { viewModel.onDigit(0) }
        key1.setOnClickListener { viewModel.onDigit(1) }
        key2.setOnClickListener { viewModel.onDigit(2) }
        key3.setOnClickListener { viewModel.onDigit(3) }
        key4.setOnClickListener { viewModel.onDigit(4) }
        key5.setOnClickListener { viewModel.onDigit(5) }
        key6.setOnClickListener { viewModel.onDigit(6) }
        key7.setOnClickListener { viewModel.onDigit(7) }
        key8.setOnClickListener { viewModel.onDigit(8) }
        key9.setOnClickListener { viewModel.onDigit(9) }
        keyClear.setOnClickListener { viewModel.onClear() }
        keyBackspace.setOnClickListener { viewModel.onBackspace() }
    }

    private fun bindPaymentMethods() = with(binding) {
        btnCard.setOnClickListener { onMockMethod(getString(R.string.method_card)) }
        btnMealCard.setOnClickListener { onMockMethod(getString(R.string.method_meal_card)) }
        btnCash.setOnClickListener { onMockMethod(getString(R.string.method_cash)) }
        btnCredit.setOnClickListener { onCreditSelected() }
    }

    private fun observeAmount() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.amountMinor.collect { amount ->
                    binding.amountText.text = amount.toTlString()
                }
            }
        }
    }

    private fun onMockMethod(methodName: String) {
        if (!requireAmount()) return
        toast(getString(R.string.msg_method_mocked, methodName))
    }

    /** VERESİYE is the one method that leaves this app: hand off to app-pos. */
    private fun onCreditSelected() {
        if (!requireAmount()) return
        val intent = Intent(ACTION_CREDIT).apply {
            // Same-device app; targeting the package keeps the handoff explicit.
            setPackage(APP_POS_PACKAGE)
            // app-pos opens in its OWN task (separate recents card), so it does not
            // live inside this app's back stack — it is the real POS payment app's
            // stand-in and must not own the veresiye app's lifecycle.
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(EXTRA_AMOUNT_MINOR, viewModel.amountMinor.value)
        }
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            toast(getString(R.string.msg_credit_app_missing))
        }
    }

    /** Guards every method: nothing proceeds before an amount is entered. */
    private fun requireAmount(): Boolean {
        if (!viewModel.hasAmount()) {
            toast(getString(R.string.msg_enter_amount))
            return false
        }
        return true
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    companion object {
        // The app-pos handoff contract. app-pos declares the matching intent-filter
        // and reads the same extra key. The two apps do not share code, so these
        // constants are duplicated on the app-pos side; they could move to
        // shared-contracts later.
        private const val APP_POS_PACKAGE = "com.example.app_pos"
        private const val ACTION_CREDIT = "com.example.app_pos.action.CREDIT"
        private const val EXTRA_AMOUNT_MINOR = "amount_minor"
    }
}
