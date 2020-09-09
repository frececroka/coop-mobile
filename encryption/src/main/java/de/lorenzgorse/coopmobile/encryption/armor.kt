package de.lorenzgorse.coopmobile.encryption

import org.bouncycastle.bcpg.ArmoredInputStream
import org.bouncycastle.bcpg.ArmoredOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

fun armor(raw: ByteArray): String {
    val output = ByteArrayOutputStream()
    ArmoredOutputStream(output).use { it.write(raw) }
    return String(output.toByteArray(), Charsets.UTF_8)
}

fun dearmor(armored: String): ByteArray {
    val armoredBytes = armored.toByteArray(Charsets.UTF_8)
    val armoredStream = ByteArrayInputStream(armoredBytes)
    val rawStream = ArmoredInputStream(armoredStream)
    return rawStream.readBytes()
}
