package com.example.app_pos

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import com.example.app_pos.databinding.ActivityMainBinding
import com.example.app_pos.ui.dashboard.DashboardFragment

/**
 * The app's only Activity. Every screen is a Fragment inside the nav host.
 *
 * app-pos has two entry points, told apart by the launching Intent:
 *  - Launcher icon → lands on the dashboard (the nav graph's start destination):
 *    the merchant can browse customers/logs without any payment.
 *  - CREDIT Intent from the payment app (mock-pos) → jumps straight into the
 *    sale flow with the handed-over amount, and finishes back to the payment app
 *    once the veresiye entry is done (handoff mode).
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var appBarConfiguration: AppBarConfiguration

    /** True when opened from the payment app; drives the finish-back behaviour. */
    private var isCreditHandoff = false

    private val navController: NavController
        get() = (supportFragmentManager
            .findFragmentById(R.id.navHostFragment) as NavHostFragment).navController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Shows each destination's label in the action bar and adds the up
        // arrow on every screen except the start destination.
        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)

        // onPrepareOptionsMenu only runs when the menu is about to be shown, so
        // the action bar has to be told to rebuild it whenever the screen —
        // and with it the dashboard icon's relevance — changes.
        navController.addOnDestinationChangedListener { _, _, _ ->
            invalidateOptionsMenu()
        }

        // Only route the launching Intent on a fresh start: on recreation the
        // nav controller already restored its back stack, and re-navigating
        // would push a duplicate sale-flow screen.
        if (savedInstanceState == null) {
            handleIntent(intent)
        }
    }

    /**
     * singleTask means a launch while already running comes here instead of a new
     * onCreate. Re-read the intent so the handoff flag reflects THIS launch (a
     * launcher tap clears it, a CREDIT intent sets it) and route accordingly.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /**
     * Decides, from the launching intent, whether this is a payment-app handoff
     * (CREDIT) or a standalone launch, and acts on it.
     *
     * The flag is set on EVERY intent — set for CREDIT, cleared otherwise — so a
     * later standalone launch (e.g. tapping the launcher) never inherits a stale
     * "handoff" state from an earlier sale. That stale flag was what wrongly sent
     * the payment flow back to the payment app.
     */
    private fun handleIntent(intent: Intent) {
        isCreditHandoff = intent.action == ACTION_CREDIT
        if (isCreditHandoff) {
            val amountMinor = intent.getLongExtra(EXTRA_AMOUNT_MINOR, 0L)
            navController.navigate(
                R.id.saleFlow,
                bundleOf("amountMinor" to amountMinor)
            )
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.sale_menu, menu)
        return true
    }

    /**
     * The dashboard icon only makes sense inside the sale flow — once the
     * dashboard is open, offering to open it again would be noise.
     */
    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val inSaleFlow = navController.currentDestination?.parent?.id == R.id.saleFlow
        menu.findItem(R.id.action_dashboard)?.isVisible = inSaleFlow
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        if (item.itemId == R.id.action_dashboard) {
            // A global action, so the same tap works from any sale screen.
            navController.navigate(R.id.action_global_dashboard)
            true
        } else {
            super.onOptionsItemSelected(item)
        }

    /**
     * Routes the action bar's up arrow.
     *
     * The dashboard hosts its own inner nav controller (customers/reports/profile
     * + customer detail), which the outer controller knows nothing about. So try
     * the inner one first — that pops customer detail back to the list — and fall
     * back to the outer controller for everything else.
     */
    override fun onSupportNavigateUp(): Boolean =
        innerNavController()?.navigateUp() == true ||
            navController.navigateUp(appBarConfiguration) ||
            super.onSupportNavigateUp()

    /** The dashboard's inner nav controller, if the dashboard is on screen and
     *  its inner back stack has somewhere to go up to (e.g. customer detail). */
    private fun innerNavController(): NavController? {
        val dashboard = supportFragmentManager
            .findFragmentById(R.id.navHostFragment)
            ?.childFragmentManager
            ?.primaryNavigationFragment as? DashboardFragment ?: return null
        val inner = dashboard.innerNavControllerOrNull() ?: return null
        return if (inner.previousBackStackEntry != null) inner else null
    }

    /**
     * Ends a payment-app handoff and returns there, after the sale-flow screen is
     * done. Returns true if it closed; false means "stay in app-pos" and the
     * caller decides where to go (usually the dashboard).
     *
     * Only closes when BOTH: opened from the payment app (isCreditHandoff) AND the
     * caller says this flow may hand back (isHandoffFlow). A PAYMENT flow always
     * starts inside app-pos, so it passes false and never finishes — that stale
     * flag was what wrongly sent payments back to the payment app (and crashed
     * when it wasn't running).
     */
    fun finishCreditHandoff(isHandoffFlow: Boolean): Boolean {
        if (isCreditHandoff && isHandoffFlow) {
            finish()
            return true
        }
        return false
    }

    companion object {
        // The handoff contract with the payment app (mock-pos). The matching
        // action is declared in AndroidManifest and mock-pos sends the same
        // action + extra. Duplicated across the two apps on purpose: they share
        // no code (separate APKs); could move to shared-contracts later.
        const val ACTION_CREDIT = "com.example.app_pos.action.CREDIT"
        const val EXTRA_AMOUNT_MINOR = "amount_minor"
    }
}
