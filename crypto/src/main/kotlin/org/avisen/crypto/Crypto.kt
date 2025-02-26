package org.avisen.crypto

import kotlinx.serialization.Serializable
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.io.UnsupportedEncodingException
import java.security.Key
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Security
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.EncodedKeySpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.*


fun setupSecurity() {
    Security.addProvider(BouncyCastleProvider())
}

fun hash(data: String) = try {
    val digest = MessageDigest.getInstance("SHA-256")
    val bytes = digest.digest(data.toByteArray(Charsets.UTF_8))

    bytes.joinToString("") { "%02x".format(it) }
} catch( e: NoSuchAlgorithmException) {
    ""
} catch( e: UnsupportedEncodingException) {
    ""
}

fun generateKeyPair(): Pair<PrivateKey, PublicKey>? {
    return try {
        val keyGen = KeyPairGenerator.getInstance("ECDSA", "BC")
        val random: SecureRandom = SecureRandom.getInstance("SHA1PRNG")
        val ecSpec = ECGenParameterSpec("prime192v1")
        // Initialize the key generator and generate a KeyPair
        keyGen.initialize(ecSpec, random) //256 bytes provides an acceptable security level
        val keyPair = keyGen.generateKeyPair()
        // Set the public and private keys from the keyPair
        Pair(keyPair.private, keyPair.public)
    } catch (e: Exception) {
        null
    }
}

/**
 * Applies an ECDSA signature to the input.
 */
fun sign(privateKey: PrivateKey, input: String): ByteArray {
    val dsa: Signature
    val output: ByteArray
    try {
        dsa = Signature.getInstance("ECDSA", "BC")
        dsa.initSign(privateKey)
        val strByte = input.toByteArray()
        dsa.update(strByte)
        val realSig: ByteArray = dsa.sign()
        output = realSig
    } catch (e: java.lang.Exception) {
        throw RuntimeException(e)
    }
    return output
}

/**
 * Verifies an ECDSA signature
 */
fun verifySignature(publicKey: PublicKey?, data: String, signature: ByteArray?): Boolean {
    try {
        val ecdsaVerify: Signature = Signature.getInstance("ECDSA", "BC")
        ecdsaVerify.initVerify(publicKey)
        ecdsaVerify.update(data.toByteArray())
        return ecdsaVerify.verify(signature)
    } catch (e: java.lang.Exception) {
        throw RuntimeException(e)
    }
}

fun Key.getString(): String {
    return Base64.getEncoder().encodeToString(this.encoded)
}

fun ByteArray.toHexString(): String {
    return Base64.getEncoder().encodeToString(this)
}

fun String.hexStringToByteArray(): ByteArray {
    return Base64.getDecoder().decode(this)
}

fun String.toPublicKey(): PublicKey {
    val publicKeySpec: EncodedKeySpec = X509EncodedKeySpec(this.hexStringToByteArray())
    val kf: KeyFactory = KeyFactory.getInstance("EC")
    return kf.generatePublic(publicKeySpec)
}

fun String.toPrivateKey(): PrivateKey {
    val privateKeySpec: EncodedKeySpec = PKCS8EncodedKeySpec(this.hexStringToByteArray())
    val kf: KeyFactory = KeyFactory.getInstance("EC")
    return kf.generatePrivate(privateKeySpec)
}

@Serializable
data class KeyPairString(val privateKey: String, val publicKey: String)

@Serializable
data class SigningPayload(val privateKey: String, val data: String)

@Serializable
data class Signature(val signature: String)
