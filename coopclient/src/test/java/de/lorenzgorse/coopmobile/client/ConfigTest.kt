package de.lorenzgorse.coopmobile.client

import de.lorenzgorse.coopmobile.client.simple.determineCountry
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.oneOf
import org.junit.Test

class ConfigTest {

    @Test
    fun testValues() {
        val config = Config()

        val country = determineCountry()
        assertThat(country, oneOf("de", "it", "fr"))

        val expectations = listOf(
            Pair(
                config.coopBase(),
                "https://myaccount.coopmobile.ch"
            ),
            Pair(
                config.loginUrl(),
                "https://myaccount.coopmobile.ch/eCare/$country/users/sign_in"
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
                "https://myaccount.coopmobile.ch/eCare/$country"
            ),
            Pair(
                config.consumptionLogUrl(),
                "https://myaccount.coopmobile.ch/$country/ajax_load_cdr"
            ),
            Pair(
                config.correspondencesUrl(),
                "https://myaccount.coopmobile.ch/eCare/$country/my_correspondence"
            ),
            Pair(
                config.productsUrl(),
                "https://myaccount.coopmobile.ch/eCare/$country/add_product"
            ),
        )

        for (expectation in expectations) {
            assertThat(expectation.first, equalTo(expectation.second))
        }
    }

}
