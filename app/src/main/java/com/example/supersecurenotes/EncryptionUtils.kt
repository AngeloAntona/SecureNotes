package com.example.supersecurenotes

import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object EncryptionUtils {

    fun encryptAesGcm(plaintext: ByteArray, key: ByteArray): ByteArray? {
        return try {
            val secretKey = SecretKeySpec(key, "AES")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val ciphertext = cipher.doFinal(plaintext)
            iv + ciphertext
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun decryptAesGcm(ciphertext: ByteArray, key: ByteArray): ByteArray? {
        return try {
            val secretKey = SecretKeySpec(key, "AES")
            val iv = ciphertext.sliceArray(0 until 12)
            val encryptedText = ciphertext.sliceArray(12 until ciphertext.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
            cipher.doFinal(encryptedText)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}