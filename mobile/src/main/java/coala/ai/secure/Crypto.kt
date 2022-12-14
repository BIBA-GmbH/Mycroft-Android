package coala.ai.secure

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONObject
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object Crypto {
    /**
     * Secure Using crypto the message to be exchanged with Hivemind
     * @author Georgia Chatzimarkaki
     */
    private val secureRandom = SecureRandom() //not caching previous seeded instance of SecureRandom

    private const val KEY_ALGORITHM = "AES"
    private const val SIGNATURE_ALGORITHM = "GCM"
    private const val ALGORITHM = "${KEY_ALGORITHM}/${SIGNATURE_ALGORITHM}/NoPadding";

    private const val TAG_LENGTH = 16
    private const val TAG_LENGTH_BIT = TAG_LENGTH * 8
    private const val IV_LENGTH_BYTE = 16 // 128/8
    private const val NONCE_LENGTH_BYTE = 12 // 96/8

    private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }

    private fun String.decodeHex(): ByteArray {
        check(length % 2 == 0) { "Must have an even length" }

        return chunked(2)
                .map { it.toInt(16).toByte() }
                .toByteArray()
    }

    /**
     * Generates an IV. The IV is always 128 bit long.
     */
    private fun generateIv(): ByteArray {
        val result = ByteArray(IV_LENGTH_BYTE)
        secureRandom.nextBytes(result)
        return result
    }

    /**
     * Generates a nonce for GCM mode. The nonce is always 96 bit long.
     */
    private fun generateNonce(): ByteArray {
        val result = ByteArray(NONCE_LENGTH_BYTE)
        secureRandom.nextBytes(result)
        return result
    }

    //    class Ciphertext(val ciphertext: ByteArray, val iv: ByteArray)
    class Ciphertext(val ciphertext: ByteArray, val nonce: ByteArray, val tag: ByteArray)

    /**
     * Encrypts the given [plaintext] with the given [key] under AES GCM with No Padding.
     *
     * This method generates a random IV.
     *
     * @return Ciphertext , nonce and tag
     */
    fun encryptGCM(plaintext: ByteArray, key: ByteArray): JSONObject {
        val cipher = Cipher.getInstance(ALGORITHM)
        val keySpec = SecretKeySpec(key, "AES")

        val iv = generateIv()
        val spec = GCMParameterSpec(TAG_LENGTH_BIT, iv)

        cipher.init(Cipher.ENCRYPT_MODE, keySpec, spec)

        val result = cipher.doFinal(plaintext)
        val ciphertext = result.copyOfRange(0, result.size - TAG_LENGTH)
        val tag = result.copyOfRange(result.size - TAG_LENGTH, result.size)

        return JSONObject("{\"ciphertext\": \"${ciphertext.toHexString()}\", \"tag\": \"${tag.toHexString()}\", \"nonce\": \"${iv.toHexString()}\"}")
    }

    /**
     * Decrypts the given [ciphertext] using the given [key] under AES GCM.
     *
     * @return Plaintext
     */
    fun decryptGcm(msg:String, key: ByteArray): ByteArray {
        val json_message = JSONObject(msg)
        val ciphertext = Ciphertext(json_message.getString("ciphertext").decodeHex(), json_message.getString("nonce").decodeHex(), json_message.getString("tag").decodeHex())

        val cipher = Cipher.getInstance(ALGORITHM)
        val keySpec = SecretKeySpec(key, "AES")

        val spec = GCMParameterSpec(TAG_LENGTH_BIT, ciphertext.nonce)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, spec)

        val result = cipher.doFinal(ciphertext.ciphertext + ciphertext.tag)
        return result
    }
}
