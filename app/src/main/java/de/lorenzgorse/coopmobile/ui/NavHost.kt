package de.lorenzgorse.coopmobile.ui

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuProvider
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import de.lorenzgorse.coopmobile.*
import de.lorenzgorse.coopmobile.client.refreshing.CredentialsStore
import de.lorenzgorse.coopmobile.ui.debug.DebugMode
import kotlinx.android.synthetic.main.activity_nav_host.*
import javax.inject.Inject

class NavHost : AppCompatActivity(), MenuProvider {

    @Inject
    lateinit var analytics: FirebaseAnalytics

    @Inject
    lateinit var credentialsStore: CredentialsStore

    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        coopComponent().inject(this)
        setContentView(R.layout.activity_nav_host)
        setSupportActionBar(toolbar)

        navController = findNavController(R.id.nav_host_fragment)
        val topLevelDestinationIds = setOf(
            R.id.login, R.id.overview, R.id.correspondences, R.id.web_view, R.id.consumption)
        val appBarConfiguration = AppBarConfiguration(topLevelDestinationIds)
        setupActionBarWithNavController(navController, appBarConfiguration)
        navController.addOnDestinationChangedListener(::onDestinationChanged)

        bottom_nav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.itOverview -> navController.navigate(R.id.action_overview)
                R.id.itCorrespondences -> navController.navigate(R.id.action_correspondences)
                R.id.itWebView -> navController.navigate(R.id.action_web_view)
                R.id.itConsumption -> navController.navigate(R.id.action_consumption)
            }
            true
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun onDestinationChanged(
        navController: NavController,
        destination: NavDestination,
        bundle: Bundle?
    ) {
        val isLogin = destination.id == R.id.login
        bottom_nav.visibility = if (isLogin) View.GONE else View.VISIBLE
        // TODO: The menu is briefly visible after opening app, before login is shown.
        //  Make the login page the default page, then this shouldn't happen anymore.
        if (isLogin) disableMenu() else enableMenu()
        when (destination.id) {
            R.id.overview -> {
                setBottomNavItem(R.id.itOverview)
            }
            R.id.correspondences -> {
                setBottomNavItem(R.id.itCorrespondences)
            }
            R.id.consumption -> {
                setBottomNavItem(R.id.itConsumption)
            }
            R.id.web_view -> {
                setBottomNavItem(R.id.itWebView)
            }
        }
    }

    private fun setBottomNavItem(itemId: Int) {
        bottom_nav.menu.findItem(itemId).isChecked = true
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment)
        navController.navigateUp()
        return super.onSupportNavigateUp()
    }

    private var menuActive = false

    private fun enableMenu() {
        if (!menuActive) {
            addMenuProvider(this, this)
            menuActive = true
        }
    }

    private fun disableMenu() {
        removeMenuProvider(this)
        menuActive = false
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.hamburger, menu)
    }

    override fun onPrepareMenu(menu: Menu) {
        val enabled = DebugMode.isEnabled(this)
        menu.findItem(R.id.itDebug).isVisible = enabled
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.itAddOption -> {
                addOption(); true
            }
            R.id.itLogout -> {
                logout(); true
            }
            R.id.itPreferences -> {
                preferences(); true
            }
            R.id.itDebug -> {
                debug(); true
            }
            else -> false
        }
    }

    private fun addOption() {
        navController.navigate(R.id.action_add_product)
    }

    private fun logout() {
        analytics.logEvent("logout", null)
        credentialsStore.clearSession()
        credentialsStore.clearCredentials()
        navController.navigate(R.id.action_login)
    }

    private fun preferences() {
        analytics.logEvent("Preferences", null)
        navController.navigate(R.id.action_preferences)
    }

    private fun debug() {
        navController.navigate(R.id.action_debug)
    }

}
