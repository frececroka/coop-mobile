package de.lorenzgorse.coopmobile

import java.io.File

object Config {

    fun read(property: String): String {
        return File(".config/$property").readText().trim()
    }

}
