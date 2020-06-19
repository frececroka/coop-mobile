package de.lorenzgorse.coopmobile

import android.os.Bundle
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
        val appBarConfiguration = AppBarConfiguration(setOf(R.id.login, R.id.status))
        setupActionBarWithNavController(navController, appBarConfiguration)
        navController.addOnDestinationChangedListener(::onDestinationChanged)
    }

    @Suppress("UNUSED_PARAMETER")
    private fun onDestinationChanged(
        navController: NavController,
        destination: NavDestination,
        bundle: Bundle?
    ) {
        when (destination.id) {
            R.id.login -> {
                toolbar.title = getString(R.string.title_login)
            }
            R.id.status -> {
                toolbar.title = getString(R.string.title_overview)
            }
            R.id.add_product -> {
                toolbar.title = getString(R.string.title_buy_option)
            }
            R.id.credits -> {
                toolbar.title = getString(R.string.title_credits)
            }
            R.id.correspondences -> {
                toolbar.title = getString(R.string.title_messages)
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment)
        navController.popBackStack()
        return super.onSupportNavigateUp()
    }

}
