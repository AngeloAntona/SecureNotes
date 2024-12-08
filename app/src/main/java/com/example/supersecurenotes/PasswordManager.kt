package com.example.supersecurenotes

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import org.mindrot.jbcrypt.BCrypt
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class PasswordManager(private val context: Context) {
    private val sharedPreferences: SharedPreferences

    companion object {
        private const val PASSWORD_HASH_KEY = "user_password_hash"
        private const val BCRYPT_COST = 10
        private const val PBKDF2_ITERATIONS = 100000
        private const val KEY_LENGTH = 256
        private const val BCRYPT_SALT_KEY = "bcrypt_salt"
        private const val PBKDF2_SALT_KEY = "pbkdf2_salt"
        private const val ATTEMPT_COUNT_KEY = "failed_attempts"
        private const val LAST_FAILED_ATTEMPT_TIME_KEY = "last_failed_attempt_time"
        private const val MAX_ATTEMPTS = 5
        private const val LOCKOUT_DURATION = 60000L
        private const val BIOMETRIC_ENABLED_KEY = "biometric_enabled"
        private const val ENCRYPTED_MASTER_KEY_PASSWORD = "encrypted_master_key_password"
        private const val ENCRYPTED_MASTER_KEY_BIOMETRIC = "encrypted_master_key_biometric"
    }

    init {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        sharedPreferences = EncryptedSharedPreferences.create(
            "secure_notes_prefs",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun isPasswordSet(): Boolean {
        return sharedPreferences.contains(PASSWORD_HASH_KEY)
    }

    fun setPassword(newPassword: String) {
        val bcryptSalt = BCrypt.gensalt(BCRYPT_COST)
        val passwordHash = BCrypt.hashpw(newPassword, bcryptSalt)

        val pbkdf2Salt = ByteArray(16)
        SecureRandom().nextBytes(pbkdf2Salt)
        val pbkdf2SaltString = Base64.encodeToString(pbkdf2Salt, Base64.DEFAULT)

        sharedPreferences.edit()
            .putString(PASSWORD_HASH_KEY, passwordHash)
            .putString(BCRYPT_SALT_KEY, bcryptSalt)
            .putString(PBKDF2_SALT_KEY, pbkdf2SaltString)
            .apply()

        if (!sharedPreferences.contains(ENCRYPTED_MASTER_KEY_PASSWORD)) {
            val masterKey = ByteArray(32)
            SecureRandom().nextBytes(masterKey)
            val derivedKey = deriveSessionKeyWithoutStoring(newPassword) ?: return
            val encryptedMasterKey = EncryptionUtils.encryptAesGcm(masterKey, derivedKey)
            if (encryptedMasterKey != null) {
                sharedPreferences.edit()
                    .putString(ENCRYPTED_MASTER_KEY_PASSWORD, Base64.encodeToString(encryptedMasterKey, Base64.DEFAULT))
                    .apply()
            }
        }
    }

    fun isBiometricEnabled(): Boolean {
        return sharedPreferences.getBoolean(BIOMETRIC_ENABLED_KEY, false)
    }

    fun setBiometricEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(BIOMETRIC_ENABLED_KEY, enabled).apply()
    }

    fun getEncryptedMasterKeyPassword(): ByteArray? {
        val base64 = sharedPreferences.getString(ENCRYPTED_MASTER_KEY_PASSWORD, null) ?: return null
        return Base64.decode(base64, Base64.DEFAULT)
    }

    fun getEncryptedMasterKeyBiometric(): ByteArray? {
        val base64 = sharedPreferences.getString(ENCRYPTED_MASTER_KEY_BIOMETRIC, null) ?: return null
        return Base64.decode(base64, Base64.DEFAULT)
    }

    fun storeBiometricEncryptedMasterKey(encrypted: ByteArray) {
        sharedPreferences.edit()
            .putString(ENCRYPTED_MASTER_KEY_BIOMETRIC, Base64.encodeToString(encrypted, Base64.DEFAULT))
            .apply()
    }

    fun isPasswordCorrect(enteredPassword: String): Boolean {
        if (isLockedOut()) {
            return false
        }

        val storedHash = sharedPreferences.getString(PASSWORD_HASH_KEY, null)
        if (storedHash != null && BCrypt.checkpw(enteredPassword, storedHash)) {
            resetFailedAttempts()

            val derivedKey = deriveSessionKeyWithoutStoring(enteredPassword) ?: return false
            val encryptedMasterKey = getEncryptedMasterKeyPassword() ?: return false
            val masterKey = EncryptionUtils.decryptAesGcm(encryptedMasterKey, derivedKey) ?: return false

            val app = context.applicationContext as MyApplication
            app.sessionKey = masterKey
            app.updateLastActiveTime()

            return true
        }

        incrementFailedAttempts()
        return false
    }

    fun isLockedOut(): Boolean {
        val failedAttempts = sharedPreferences.getInt(ATTEMPT_COUNT_KEY, 0)
        val lastFailedTime = sharedPreferences.getLong(LAST_FAILED_ATTEMPT_TIME_KEY, 0)
        return failedAttempts >= MAX_ATTEMPTS && (System.currentTimeMillis() - lastFailedTime) < LOCKOUT_DURATION
    }

    private fun incrementFailedAttempts() {
        val failedAttempts = sharedPreferences.getInt(ATTEMPT_COUNT_KEY, 0) + 1
        sharedPreferences.edit()
            .putInt(ATTEMPT_COUNT_KEY, failedAttempts)
            .putLong(LAST_FAILED_ATTEMPT_TIME_KEY, System.currentTimeMillis())
            .apply()
    }

    private fun resetFailedAttempts() {
        sharedPreferences.edit()
            .putInt(ATTEMPT_COUNT_KEY, 0)
            .apply()
    }

    fun deriveSessionKeyWithoutStoring(password: String): ByteArray? {
        val pbkdf2SaltString = sharedPreferences.getString(PBKDF2_SALT_KEY, null) ?: return null
        val pbkdf2Salt = Base64.decode(pbkdf2SaltString, Base64.DEFAULT)
        return try {
            val spec = PBEKeySpec(password.toCharArray(), pbkdf2Salt, PBKDF2_ITERATIONS, KEY_LENGTH)
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            factory.generateSecret(spec).encoded
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getPasswordHash(): String? {
        return sharedPreferences.getString(PASSWORD_HASH_KEY, null)
    }

    fun getPbkdf2Salt(): String? {
        return sharedPreferences.getString(PBKDF2_SALT_KEY, null)
    }

    fun restoreOldPassword(oldPasswordHash: String?, oldPbkdf2Salt: String?): Boolean {
        if (oldPasswordHash == null || oldPbkdf2Salt == null) return false
        sharedPreferences.edit()
            .putString(PASSWORD_HASH_KEY, oldPasswordHash)
            .putString(PBKDF2_SALT_KEY, oldPbkdf2Salt)
            .apply()
        return true
    }

    fun setSessionKey(key: ByteArray?) {
        val app = context.applicationContext as MyApplication
        app.sessionKey = key
    }
}