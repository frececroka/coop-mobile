package de.lorenzgorse.coopmobile

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import kotlinx.android.synthetic.main.activity_nav_host.*

class NavHost : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nav_host)
        setSupportActionBar(toolbar)
        val navController = findNavController(R.id.nav_host_fragment)
        val topLevelDestinationIds = setOf(
            R.id.login, R.id.overview, R.id.correspondences, R.id.web_view, R.id.consumption)
        val appBarConfiguration = AppBarConfiguration(topLevelDestinationIds)
        setupActionBarWithNavController(navController, appBarConfiguration)
        navController.addOnDestinationChangedListener(::onDestinationChanged)

        bottom_nav.setOnNavigationItemSelectedListener { item ->
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
        bottom_nav.visibility = if (destination.id == R.id.login) View.GONE else View.VISIBLE
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

}
