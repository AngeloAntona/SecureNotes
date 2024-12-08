package com.example.supersecurenotes

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class EncryptionManager {

    companion object {
        private const val KEY_ALIAS = "secure_notes_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    }

    private fun getSecretKey(): SecretKey? {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
            load(null)
        }

        return if (keyStore.containsAlias(KEY_ALIAS)) {
            val secretKeyEntry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            secretKeyEntry?.secretKey
        } else {
            createSecretKey()
        }
    }

    fun createSecretKey(): SecretKey? {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(true)
            .setUserAuthenticationValidityDurationSeconds(0) // Richiede impronta ogni volta
            .build()
        keyGenerator.init(keyGenParameterSpec)
        return keyGenerator.generateKey()
    }

    fun getEncryptCipher(): Cipher? {
        val secretKey = getSecretKey() ?: return null
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, secretKey)
        }
    }

    fun getDecryptCipher(iv: ByteArray): Cipher? {
        val secretKey = getSecretKey() ?: return null
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, secretKey, javax.crypto.spec.GCMParameterSpec(128, iv))
        }
    }

    // Funzione per criptare la Master Key con la chiave biometrica:
    fun encryptWithBiometricKey(masterKey: ByteArray): ByteArray? {
        val cipher = getEncryptCipher() ?: return null
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(masterKey)
        return iv + ciphertext
    }

    fun decryptWithBiometricKey(encryptedMasterKey: ByteArray, cipher: Cipher): ByteArray? {
        return cipher.doFinal(encryptedMasterKey)
    }
}