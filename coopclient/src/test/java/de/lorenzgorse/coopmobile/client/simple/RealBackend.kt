package de.lorenzgorse.coopmobile.client.simple

class RealBackend {
    val username = Config.read("username")
    val password = Config.read("password")
    val wrongUsername = "0781234567"
    val wrongPassword = "supersecret"
}
