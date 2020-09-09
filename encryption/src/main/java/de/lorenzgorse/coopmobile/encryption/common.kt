package de.lorenzgorse.coopmobile.encryption

import org.bouncycastle.openpgp.PGPObjectFactory
import org.bouncycastle.openpgp.PGPPublicKeyRing
import org.bouncycastle.openpgp.PGPSecretKeyRing
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator
import java.io.ByteArrayInputStream
import java.io.InputStream

fun readPublicKeyRing(input: InputStream): PGPPublicKeyRing =
    readPgpObject(input)

fun readPrivateKeyRing(input: InputStream): PGPSecretKeyRing =
    readPgpObject(input)

inline fun <reified T> readPgpObject(input: ByteArray): T =
    readPgpObject(ByteArrayInputStream(input))

inline fun <reified T> readPgpObject(input: InputStream): T {
    val factory = PGPObjectFactory(input, BcKeyFingerprintCalculator())
    return factory.nextObject() as T
}

fun buffer() = ByteArray(1024 * 1024)
