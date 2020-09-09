package de.lorenzgorse.coopmobile.coopclient

const val coopScheme = "https"
const val coopHost = "myaccount.coopmobile.ch"
const val coopBase = "$coopScheme://$coopHost/eCare"
val country = determineCountry()
val coopBaseLogin = "$coopBase/$country/users/sign_in"
val coopBaseAccount = "$coopBase/$country"
