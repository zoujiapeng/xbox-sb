package com.padmax.controller

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class CryptoBox private constructor(private val key: SecretKeySpec) {
    fun seal(plain: ByteArray, nonce: ByteArray): ByteArray {
        require(nonce.size == 12) { "AES-GCM nonce must be 12 bytes" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, nonce))
        return cipher.doFinal(plain)
    }

    companion object {
        private const val ITERATIONS = 120_000

        fun fromPairCode(pairCode: String, salt: ByteArray): CryptoBox {
            val normalized = pairCode.trim().ifEmpty { "000000" }
            val spec = PBEKeySpec(normalized.toCharArray(), salt, ITERATIONS, 256)
            val secret = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec)
            return CryptoBox(SecretKeySpec(secret.encoded, "AES"))
        }

        fun randomClientId(): Long {
            val b = ByteArray(8)
            SecureRandom().nextBytes(b)
            var v = 0L
            for (i in 0 until 8) v = v or ((b[i].toLong() and 0xffL) shl (8 * i))
            return v
        }

        fun nonce(clientId: Long, seq: Int): ByteArray {
            val out = ByteArray(12)
            var c = clientId
            for (i in 0 until 8) {
                out[i] = (c and 0xffL).toByte()
                c = c ushr 8
            }
            var s = seq
            for (i in 0 until 4) {
                out[8 + i] = (s and 0xff).toByte()
                s = s ushr 8
            }
            return out
        }
    }
}
