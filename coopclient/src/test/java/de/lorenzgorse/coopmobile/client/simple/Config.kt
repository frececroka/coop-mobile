package de.lorenzgorse.coopmobile.client.simple

import java.io.File

object Config {

    fun read(property: String): String {
        return File(".config/$property").readText().trim()
    }

}
