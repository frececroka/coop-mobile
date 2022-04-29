package de.lorenzgorse.coopmobile.client

import de.lorenzgorse.coopmobile.client.simple.determineCountry

// TODO: write all values to analytics at some point
open class Config {
    private val country = determineCountry()
    private val coopBase = "https://myaccount.coopmobile.ch"
    private val ecareBase = "$coopBase/eCare"
    private val ecareLocalBase = "$ecareBase/$country"

    open fun coopBase() = coopBase

    open fun loginUrl() = "$ecareLocalBase/users/sign_in"

    open fun loginUrlRegex() = "${Regex.escape(ecareBase)}/([^/]+)/users/sign_in"

    // https://myaccount.coopmobile.ch/eCare/wireless/de
    // https://myaccount.coopmobile.ch/eCare/prepaid/de
    // https://myaccount.coopmobile.ch/eCare/wireless/de?login=true
    // https://myaccount.coopmobile.ch/eCare/prepaid/de?login=true
    open fun loginSuccessRegex() = "${Regex.escape(ecareBase)}/(.*)/(de|fr|it)/?(\\?login=true)?"

    open fun planRegex() = "${Regex.escape(ecareBase)}/([^/]+)/.+"

    open fun overviewUrl() = ecareLocalBase

    open fun consumptionLogUrl() = "$coopBase/$country/ajax_load_cdr"

    open fun productsUrl() = "$ecareLocalBase/add_product"

    open fun correspondencesUrl() = "$ecareLocalBase/my_correspondence"
}
