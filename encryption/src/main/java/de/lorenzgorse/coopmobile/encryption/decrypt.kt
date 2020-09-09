package de.lorenzgorse.coopmobile.encryption

import org.bouncycastle.openpgp.PGPEncryptedDataList
import org.bouncycastle.openpgp.PGPLiteralData
import org.bouncycastle.openpgp.PGPPrivateKey
import org.bouncycastle.openpgp.PGPPublicKeyEncryptedData
import org.bouncycastle.openpgp.operator.bc.BcPublicKeyDataDecryptorFactory

fun decrypt(privateKey: PGPPrivateKey, message: String): String {
    val messageBytes = dearmor(message)
    return decrypt(privateKey, messageBytes)
}

fun decrypt(privateKey: PGPPrivateKey, message: ByteArray): String {
    val encryptedDataList = readPgpObject<PGPEncryptedDataList>(message)
    val encryptedDataObjects = encryptedDataList.encryptedDataObjects.asSequence().toList()
    val encryptedDataObject = encryptedDataObjects[0] as PGPPublicKeyEncryptedData
    val dataStream = encryptedDataObject.getDataStream(BcPublicKeyDataDecryptorFactory(privateKey))
    val literalData = readPgpObject<PGPLiteralData>(dataStream)
    return literalData.inputStream.bufferedReader(Charsets.UTF_8).readText()
}
