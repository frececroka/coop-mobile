package de.lorenzgorse.coopmobile.ui

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.installations.FirebaseInstallationsException
import com.google.firebase.installations.FirebaseInstallationsException.Status.TOO_MANY_REQUESTS
import com.google.firebase.installations.FirebaseInstallationsException.Status.UNAVAILABLE
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.ktx.remoteConfig
import de.lorenzgorse.coopmobile.*
import de.lorenzgorse.coopmobile.client.refreshing.CredentialsStore
import de.lorenzgorse.coopmobile.databinding.ActivityNavHostBinding
import de.lorenzgorse.coopmobile.ui.debug.DebugMode
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

class NavHost : AppCompatActivity(), MenuProvider {

    @Inject
    lateinit var analytics: FirebaseAnalytics

    @Inject
    lateinit var credentialsStore: CredentialsStore

    private lateinit var binding: ActivityNavHostBinding
    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        coopComponent().inject(this)
        binding = ActivityNavHostBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        navController = findNavController(R.id.nav_host_fragment)
        val topLevelDestinationIds = setOf(
            R.id.login, R.id.overview, R.id.options, R.id.correspondences, R.id.consumption)
        val appBarConfiguration = AppBarConfiguration(topLevelDestinationIds)
        setupActionBarWithNavController(navController, appBarConfiguration)
        navController.addOnDestinationChangedListener(::onDestinationChanged)

        lifecycleScope.launch { removeMenuItems() }

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.itOverview -> navController.navigate(R.id.action_overview)
                R.id.itOptions -> navController.navigate(R.id.action_options)
                R.id.itCorrespondences -> navController.navigate(R.id.action_correspondences)
                R.id.itConsumption -> navController.navigate(R.id.action_consumption)
            }
            true
        }

        BalanceCheckWorker.enqueueIfEnabled(this)
    }

    private suspend fun removeMenuItems() {
        try {
            waitForTask(Firebase.remoteConfig.fetchAndActivate())
        } catch (e: Exception) {
            val reportException = when (val rootCause = e.rootCause()) {
                is IOException -> false
                is FirebaseInstallationsException ->
                    rootCause.status !in setOf(UNAVAILABLE, TOO_MANY_REQUESTS)
                else -> true
            }
            if (reportException) {
                Firebase.crashlytics.recordException(e)
            }
        }

        val enableCorrespondences = Firebase.remoteConfig.getBoolean("enable_correspondences")
        if (!enableCorrespondences) {
            binding.bottomNav.menu.removeItem(R.id.itCorrespondences)
        }

        val enableOptions = Firebase.remoteConfig.getBoolean("enable_options")
        if (!enableOptions) {
            binding.bottomNav.menu.removeItem(R.id.itOptions)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun onDestinationChanged(
        navController: NavController,
        destination: NavDestination,
        bundle: Bundle?
    ) {
        val isLogin = destination.id == R.id.login
        binding.bottomNav.visibility = if (isLogin) View.GONE else View.VISIBLE
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
        binding.bottomNav.menu.findItem(itemId).isChecked = true
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
