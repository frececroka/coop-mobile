package de.lorenzgorse.coopmobile.client

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test

class ConfigTest {

    @Test
    fun testValues() {
        val config = LocalizedConfig(userCountry = "it")

        val expectations = listOf(
            Pair(
                config.coopBase(),
                "https://myaccount.coopmobile.ch"
            ),
            Pair(
                config.loginUrl(),
                "https://myaccount.coopmobile.ch/eCare/de/users/sign_in"
            ),
            Pair(
                config.loginUrlRegex(),
                "\\Qhttps://myaccount.coopmobile.ch/eCare\\E/([^/]+)/users/sign_in"
            ),
            Pair(
                config.loginSuccessRegex(),
                "\\Qhttps://myaccount.coopmobile.ch/eCare\\E/(.*)/(de|fr|it)/?(\\?login=true)?"
            ),
            Pair(
                config.planRegex(),
                "\\Qhttps://myaccount.coopmobile.ch/eCare\\E/([^/]+)/.+"
            ),
            Pair(
                config.overviewUrl(),
                "https://myaccount.coopmobile.ch/eCare/de"
            ),
            Pair(
                config.consumptionLogUrl(),
                "https://myaccount.coopmobile.ch/de/ajax_load_cdr"
            ),
            Pair(
                config.correspondencesUrl(),
                "https://myaccount.coopmobile.ch/eCare/de/my_correspondence"
            ),
            Pair(
                config.productsUrl(),
                "https://myaccount.coopmobile.ch/eCare/it/add_product"
            ),
        )

        for (expectation in expectations) {
            assertThat(expectation.first, equalTo(expectation.second))
        }
    }

}
