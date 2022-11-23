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
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.ktx.remoteConfig
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
            R.id.login, R.id.overview, R.id.options, R.id.correspondences, R.id.consumption)
        val appBarConfiguration = AppBarConfiguration(topLevelDestinationIds)
        setupActionBarWithNavController(navController, appBarConfiguration)
        navController.addOnDestinationChangedListener(::onDestinationChanged)

        val enableCorrespondences = Firebase.remoteConfig.getBoolean("enable_correspondences")
        if (!enableCorrespondences) {
            bottom_nav.menu.removeItem(R.id.itCorrespondences)
        }

        val enableOptions = Firebase.remoteConfig.getBoolean("enable_options")
        if (!enableOptions) {
            bottom_nav.menu.removeItem(R.id.itOptions)
        }

        bottom_nav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.itOverview -> navController.navigate(R.id.action_overview)
                R.id.itOptions -> navController.navigate(R.id.action_options)
                R.id.itCorrespondences -> navController.navigate(R.id.action_correspondences)
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

    private fun logout() {
        analytics.logEvent("logout", null)
        credentialsStore.clearSession()
        credentialsStore.clearCredentials()
        navController.navigate(R.id.action_login)
    }

    private fun preferences() {
        navController.navigate(R.id.action_preferences)
    }

    private fun debug() {
        navController.navigate(R.id.action_debug)
    }

}
