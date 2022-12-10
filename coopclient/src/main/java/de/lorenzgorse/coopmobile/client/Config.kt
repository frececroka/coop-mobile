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

class LocalizedConfig : Config {
    private val country = determineCountry()
    private val coopBase = "https://myaccount.coopmobile.ch"
    private val ecareBase = "$coopBase/eCare"
    private val ecareLocalBase = "$ecareBase/$country"

    override fun coopBase() = coopBase

    override fun loginUrl() = "$ecareLocalBase/users/sign_in"

    override fun loginUrlRegex() = "${Regex.escape(ecareBase)}/([^/]+)/users/sign_in"

    // https://myaccount.coopmobile.ch/eCare/wireless/de
    // https://myaccount.coopmobile.ch/eCare/prepaid/de
    // https://myaccount.coopmobile.ch/eCare/wireless/de?login=true
    // https://myaccount.coopmobile.ch/eCare/prepaid/de?login=true
    override fun loginSuccessRegex() = "${Regex.escape(ecareBase)}/(.*)/(de|fr|it)/?(\\?login=true)?"

    override fun planRegex() = "${Regex.escape(ecareBase)}/([^/]+)/.+"

    override fun overviewUrl() = ecareLocalBase

    override fun consumptionLogUrl() = "$coopBase/$country/ajax_load_cdr"

    override fun productsUrl() = "$ecareLocalBase/add_product"

    override fun correspondencesUrl() = "$ecareLocalBase/my_correspondence"
}
