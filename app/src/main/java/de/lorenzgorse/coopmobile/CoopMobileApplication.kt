package de.lorenzgorse.coopmobile

import android.app.Application
import android.content.Context
import android.provider.Settings
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfig
import dagger.Component
import dagger.Module
import dagger.Provides
import de.lorenzgorse.coopmobile.client.Config
import de.lorenzgorse.coopmobile.client.LocalizedConfig
import de.lorenzgorse.coopmobile.client.RemoteConfig
import de.lorenzgorse.coopmobile.client.refreshing.CoopClientFactory
import de.lorenzgorse.coopmobile.client.refreshing.CredentialsStore
import de.lorenzgorse.coopmobile.client.refreshing.RealCoopClientFactory
import de.lorenzgorse.coopmobile.client.refreshing.RefreshingSessionCoopClient
import de.lorenzgorse.coopmobile.client.simple.*
import de.lorenzgorse.coopmobile.preferences.SharedPreferencesCredentialsStore
import de.lorenzgorse.coopmobile.ui.NavHost
import de.lorenzgorse.coopmobile.ui.RemoteDataView
import de.lorenzgorse.coopmobile.ui.consumption.ConsumptionFragment
import de.lorenzgorse.coopmobile.ui.correspondences.CorrespondencesFragment
import de.lorenzgorse.coopmobile.ui.debug.DebugFragment
import de.lorenzgorse.coopmobile.ui.login.LoginFragment
import de.lorenzgorse.coopmobile.ui.options.OptionsFragment
import de.lorenzgorse.coopmobile.ui.overview.OverviewFragment
import de.lorenzgorse.coopmobile.ui.preferences.PreferencesFragment
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import okhttp3.CookieJar

@Suppress("unused")
open class CoopMobileApplication : Application() {

    private var privateComponent: CoopComponent = createComponent()
    val component: CoopComponent get() = privateComponent

    private fun createComponent(): CoopComponent {
        return DaggerCoopComponent.builder()
            .mainCoopModule(MainCoopModule(this))
            .build()
    }

    fun recreateComponent() {
        privateComponent = createComponent()
    }

    override fun onCreate() {
        super.onCreate()
        UserProperties(this).restore()
        Firebase.remoteConfig.fetchAndActivate()
    }

}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@Component(modules = [MainCoopModule::class])
interface CoopComponent {
    fun inject(fragment: NavHost)
    fun inject(fragment: OverviewFragment)
    fun inject(fragment: OptionsFragment)
    fun inject(fragment: ConsumptionFragment)
    fun inject(fragment: CorrespondencesFragment)
    fun inject(fragment: DebugFragment)
    fun inject(fragment: LoginFragment)
    fun inject(fragment: PreferencesFragment)
    fun inject(remoteDataView: RemoteDataView)
    fun inject(balanceCheckWorker: BalanceCheckWorker)
}

@Module
class MainCoopModule(private val app: Application) {

    @Provides
    fun application(): Application = app

    @Provides
    fun context(): Context = app

    @Provides
    fun client(clientFactory: CoopClientFactory): CoopClient =
        MonitoredCoopClient(RefreshingSessionCoopClient(clientFactory))

    @Provides
    fun coopClientFactory(
        credentialsStore: CredentialsStore,
        coopLogin: CoopLogin,
        staticSessionCoopClient: CoopClientFromSessionId,
    ): CoopClientFactory =
        RealCoopClientFactory(credentialsStore, coopLogin, staticSessionCoopClient::create)

    @Provides
    fun staticSessionCoopClient(
        config: Config,
        parserExperiments: CoopHtmlParser.Experiments,
        httpClientFactory: HttpClientFromCookieJar,
    ): CoopClientFromSessionId = object : CoopClientFromSessionId {
        override fun create(sessionId: String): CoopClient =
            if (testAccounts().modeActive()) TestModeCoopClient()
            else StaticSessionCoopClient(
                config,
                parserExperiments,
                sessionId,
                httpClientFactory::create
            )
    }

    @Provides
    fun parserExperiments() = CoopHtmlParser.Experiments(
        enableOptionsAndCallsFix = FirebaseRemoteConfig.getInstance().getBoolean("enable_parse_consumption_options_and_calls_fix")
    )

    // Function types like (String) -> CoopClient don't seem to work with Dagger
    interface CoopClientFromSessionId {
        fun create(sessionId: String): CoopClient
    }

    @Provides
    fun coopLogin(
        config: Config,
        httpClientFactory: HttpClientFromCookieJar,
        userProperties: UserProperties,
        firebaseAnalytics: FirebaseAnalytics,
    ): CoopLogin {
        val coopLogin =
            if (testAccounts().modeActive()) TestModeCoopLogin()
            else RealCoopLogin(config, httpClientFactory::create)
        return MonitoredCoopLogin(app, coopLogin, userProperties, firebaseAnalytics)
    }

    @Provides
    fun credentialsStore(): CredentialsStore =
        SharedPreferencesCredentialsStore(app)

    @Provides
    fun httpClientFactory(): HttpClientFromCookieJar =
        object : HttpClientFromCookieJar {
            override fun create(cookieJar: CookieJar): HttpClient =
                MonitoredHttpClient(app, SimpleHttpClient(cookieJar))
        }

    // Function types like (String) -> CoopClient don't seem to work with Dagger
    interface HttpClientFromCookieJar {
        fun create(cookieJar: CookieJar): HttpClient
    }

    @Provides
    fun userProperties(): UserProperties = UserProperties(app)

    @Provides
    fun firebaseAnalytics(): FirebaseAnalytics {
        val analytics = com.google.firebase.analytics.FirebaseAnalytics.getInstance(context())
        val testLabSetting = Settings.System.getString(
            context().contentResolver, "firebase.test.lab"
        )
        if ("true" == testLabSetting) {
            analytics.setAnalyticsCollectionEnabled(false)
        }
        return RealFirebaseAnalytics(analytics)
    }

    @Provides
    fun testAccounts(): TestAccounts = TestAccounts(app)

    @Provides
    fun config(): Config = RemoteConfig(LocalizedConfig())

}
