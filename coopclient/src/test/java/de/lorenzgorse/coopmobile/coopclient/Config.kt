package de.lorenzgorse.coopmobile.coopclient

import java.io.File

object Config {

    fun read(property: String): String {
        return File(".config/$property").readText().trim()
    }

}
