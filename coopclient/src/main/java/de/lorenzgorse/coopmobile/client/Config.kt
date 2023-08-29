package de.lorenzgorse.coopmobile.client

import de.lorenzgorse.coopmobile.client.simple.determineCountry

interface Config {
    fun coopBase(): String
    fun loginUrl(): String
    fun loginUrlRegex(): String
    fun loginSuccessRegex(): String
    fun planRegex(): String
    fun overviewUrl(): String
    fun consumptionLogUrl(): String
    fun productsUrl(): String
    fun correspondencesUrl(): String
}

class LocalizedConfig(
    // The user country is the country code used to fetch
    // pages that have text that we show to the user.
    private val userCountry: String = determineCountry(),
) : Config {
    private val dataCountry = "de"

    private val coopBase = "https://myaccount.coopmobile.ch"
    private val ecareBase = "$coopBase/eCare"

    override fun coopBase() = coopBase

    override fun loginUrl() = "$ecareBase/$dataCountry/users/sign_in"

    override fun loginUrlRegex() = "${Regex.escape(ecareBase)}/([^/]+)/users/sign_in"

    // https://myaccount.coopmobile.ch/eCare/wireless/de
    // https://myaccount.coopmobile.ch/eCare/prepaid/de
    // https://myaccount.coopmobile.ch/eCare/wireless/de?login=true
    // https://myaccount.coopmobile.ch/eCare/prepaid/de?login=true
    override fun loginSuccessRegex() = "${Regex.escape(ecareBase)}/(.*)/(de|fr|it)/?(\\?login=true)?"

    override fun planRegex() = "${Regex.escape(ecareBase)}/([^/]+)/.+"

    override fun overviewUrl() = "$ecareBase/$dataCountry"

    override fun consumptionLogUrl() = "$coopBase/$dataCountry/ajax_load_cdr"

    // The products page has a lot of text that we don't want to translate. Use
    // the user country to load this, so that the user can understand everything.
    override fun productsUrl() = "$ecareBase/$userCountry/add_product"

    override fun correspondencesUrl() = "$ecareBase/$dataCountry/my_correspondence"
}
