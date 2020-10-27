package de.lorenzgorse.coopmobile.encryption

import org.bouncycastle.openpgp.operator.jcajce.JcePBESecretKeyDecryptorBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PGPTest {

    @Test
    fun testReadPublicKeyRing() {
        val publicKeyRing = readPublicKeyRing(publicKeyRingStream())
        val publicKey = publicKeyRing.publicKey
        val userIds = publicKey.userIDs.asSequence().toList()
        assertEquals(listOf("Lorenz Gorse <lorenz.gorse@gmail.com>"), userIds)
    }

    @Test
    fun testReadPrivateKeyRing() {
        val privateKeyRing = readPrivateKeyRing(privateKeyRingStream())
        val privateKey = privateKeyRing.secretKey
        val userIds = privateKey.userIDs.asSequence().toList()
        assertEquals(listOf("Lorenz Gorse <lorenz.gorse@gmail.com>"), userIds)
    }

    @Test
    fun testEncryptDecrypt() {
        val publicKey = readPublicKeyRing(publicKeyRingStream()).publicKey
        val secretKey = readPrivateKeyRing(privateKeyRingStream()).secretKey
        val privateKey = secretKey.extractPrivateKey(JcePBESecretKeyDecryptorBuilder().build(CharArray(0)))
        val message = "plaintext"
        assertEquals(message, decrypt(privateKey, encrypt(publicKey, message)))
    }

    private fun publicKeyRingStream() = javaClass.getResourceAsStream("/publickey.gpg")

    private fun privateKeyRingStream() = javaClass.getResourceAsStream("/privatekey.gpg")

}
