package de.lorenzgorse.coopmobile.encryption

import org.bouncycastle.openpgp.PGPEncryptedData
import org.bouncycastle.openpgp.PGPEncryptedDataGenerator
import org.bouncycastle.openpgp.PGPLiteralDataGenerator
import org.bouncycastle.openpgp.PGPLiteralDataGenerator.NOW
import org.bouncycastle.openpgp.PGPLiteralDataGenerator.UTF8
import org.bouncycastle.openpgp.PGPPublicKey
import org.bouncycastle.openpgp.operator.bc.BcPGPDataEncryptorBuilder
import org.bouncycastle.openpgp.operator.bc.BcPublicKeyKeyEncryptionMethodGenerator
import java.io.ByteArrayOutputStream

fun encrypt(publicKey: PGPPublicKey, message: String): String {
    return armor(encrypt(publicKey, message.toByteArray(Charsets.UTF_8)))
}

fun encrypt(publicKey: PGPPublicKey, message: ByteArray): ByteArray {
    val ciphertext = ByteArrayOutputStream()
    val encryptor = buildEncryptor(publicKey)
    val literalData = encryptor.open(ciphertext, buffer())
    val plaintext = PGPLiteralDataGenerator().open(literalData, UTF8, "", NOW, buffer())
    plaintext.write(message)
    plaintext.close(); literalData.close(); encryptor.close()
    return ciphertext.toByteArray()
}

private fun buildEncryptor(publicKey: PGPPublicKey): PGPEncryptedDataGenerator {
    val encryptorBuilder = BcPGPDataEncryptorBuilder(PGPEncryptedData.AES_256)
    encryptorBuilder.setWithIntegrityPacket(true)
    val encryptor = PGPEncryptedDataGenerator(encryptorBuilder)
    encryptor.addMethod(BcPublicKeyKeyEncryptionMethodGenerator(publicKey))
    return encryptor
}

