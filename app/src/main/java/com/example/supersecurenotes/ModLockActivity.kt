package com.example.supersecurenotes

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Base64
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat

class ModLockActivity : AppCompatActivity() {

    private lateinit var passwordManager: PasswordManager
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var oldPasswordEditText: EditText
    private lateinit var newPasswordEditText: EditText
    private lateinit var confirmPasswordEditText: EditText
    private lateinit var passwordStrengthTextView: TextView
    private lateinit var saveButton: Button
    private lateinit var enableBiometricButton: Button
    private lateinit var biometricStatusTextView: TextView

    private val noteTitlesKey = "noteTitlesKey"
    private var masterKeyToEncrypt: ByteArray? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mod_lock)

        val app = applicationContext as MyApplication
        if (app.isSessionExpired()) {
            app.clearSession()
            Toast.makeText(this, "Session expired", Toast.LENGTH_SHORT).show()
            navigateToLogin()
            return
        } else {
            app.updateLastActiveTime()
        }

        passwordManager = PasswordManager(applicationContext)
        sharedPreferences = getSharedPreferences("notes_prefs", Context.MODE_PRIVATE)

        oldPasswordEditText = findViewById(R.id.previousPasswordEditText)
        newPasswordEditText = findViewById(R.id.newPasswordEditText)
        confirmPasswordEditText = findViewById(R.id.repeatNewPasswordEditText)
        passwordStrengthTextView = findViewById(R.id.passwordStrengthTextView)
        saveButton = findViewById(R.id.savePasswordButton)
        enableBiometricButton = findViewById(R.id.enableBiometricButton)
        biometricStatusTextView = findViewById(R.id.biometricStatusTextView)

        updateBiometricButtonText()
        updateBiometricStatusText()

        newPasswordEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val password = s.toString()
                val (isComplex, message) = checkPasswordComplexity(password)
                passwordStrengthTextView.text = if (isComplex) "Strong Password" else message
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int){}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int){}
        })

        saveButton.setOnClickListener {
            val oldPassword = oldPasswordEditText.text.toString()
            val newPassword = newPasswordEditText.text.toString()
            val confirmPassword = confirmPasswordEditText.text.toString()

            if (!isCurrentPasswordValid(oldPassword)) return@setOnClickListener

            if (newPassword == confirmPassword) {
                val (isComplex, message) = checkPasswordComplexity(newPassword)
                if (isComplex) {
                    if (reEncryptMasterKeyWithNewPassword(newPassword, oldPassword)) {
                        Toast.makeText(this, "Password updated", Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        Toast.makeText(this, "Error", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
            }
        }

        enableBiometricButton.setOnClickListener {
            val oldPass = oldPasswordEditText.text.toString()
            if (!isCurrentPasswordValid(oldPass)) return@setOnClickListener

            if (passwordManager.isBiometricEnabled()) {
                disableBiometric()
            } else {
                val derivedKey = passwordManager.deriveSessionKeyWithoutStoring(oldPass)
                val encryptedMasterKeyWithPassword = passwordManager.getEncryptedMasterKeyPassword()

                if (derivedKey != null && encryptedMasterKeyWithPassword != null) {
                    val mk = EncryptionUtils.decryptAesGcm(encryptedMasterKeyWithPassword, derivedKey)
                    if (mk != null) {
                        masterKeyToEncrypt = mk
                        showBiometricPromptForEncryption()
                    } else {
                        Toast.makeText(this, "Cannot decrypt master key", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Cannot access master key", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun navigateToLogin() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun isCurrentPasswordValid(oldPassword: String): Boolean {
        if (!passwordManager.isPasswordCorrect(oldPassword)) {
            Toast.makeText(this, "Current password incorrect", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun updateBiometricButtonText() {
        enableBiometricButton.text = if (passwordManager.isBiometricEnabled()) {
            "Disable Biometric"
        } else {
            "Enable Biometric"
        }
    }

    private fun updateBiometricStatusText() {
        biometricStatusTextView.text = if (passwordManager.isBiometricEnabled()) {
            "Status: Enabled"
        } else {
            "Status: Disabled"
        }
    }

    private fun checkPasswordComplexity(password: String): Pair<Boolean, String> {
        val minLength = 8
        val missingRequirements = mutableListOf<String>()

        if (password.length < minLength) missingRequirements.add("$minLength chars")
        if (!password.any { it.isUpperCase() }) missingRequirements.add("uppercase")
        if (!password.any { it.isLowerCase() }) missingRequirements.add("lowercase")
        if (!password.any { it.isDigit() }) missingRequirements.add("number")
        if (!password.any { !it.isLetterOrDigit() }) missingRequirements.add("special char")

        return if (missingRequirements.isEmpty()) {
            Pair(true, "")
        } else {
            val message = "Must contain: " + missingRequirements.joinToString(", ")
            Pair(false, message)
        }
    }

    private fun reEncryptMasterKeyWithNewPassword(newPassword: String, oldPassword: String): Boolean {
        val encryptedMasterKey = passwordManager.getEncryptedMasterKeyPassword() ?: return false

        val oldDerivedKey = passwordManager.deriveSessionKeyWithoutStoring(oldPassword) ?: return false
        val masterKey = EncryptionUtils.decryptAesGcm(encryptedMasterKey, oldDerivedKey) ?: return false

        passwordManager.setPassword(newPassword)
        val newDerivedKey = passwordManager.deriveSessionKeyWithoutStoring(newPassword) ?: return false
        val newEncryptedMasterKey = EncryptionUtils.encryptAesGcm(masterKey, newDerivedKey) ?: return false

        val sharedPrefs = passwordManager.javaClass.getDeclaredField("sharedPreferences")
        sharedPrefs.isAccessible = true
        val sp = sharedPrefs.get(passwordManager) as SharedPreferences
        sp.edit().putString("encrypted_master_key_password", Base64.encodeToString(newEncryptedMasterKey, Base64.DEFAULT)).apply()

        return true
    }

    private fun disableBiometric() {
        val sharedPrefs = passwordManager.javaClass.getDeclaredField("sharedPreferences")
        sharedPrefs.isAccessible = true
        val sp = sharedPrefs.get(passwordManager) as SharedPreferences
        sp.edit().remove("encrypted_master_key_biometric").apply()

        passwordManager.setBiometricEnabled(false)
        Toast.makeText(this, "Biometric disabled", Toast.LENGTH_SHORT).show()
        updateBiometricButtonText()
        updateBiometricStatusText()
    }

    private fun showBiometricPromptForEncryption() {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                Toast.makeText(applicationContext, "Biometric error: $errString", Toast.LENGTH_SHORT).show()
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val cipher = result.cryptoObject?.cipher
                val mk = masterKeyToEncrypt
                if (cipher != null && mk != null) {
                    try {
                        val iv = cipher.iv
                        val ciphertext = cipher.doFinal(mk)
                        val finalData = iv + ciphertext
                        passwordManager.storeBiometricEncryptedMasterKey(finalData)
                        passwordManager.setBiometricEnabled(true)
                        Toast.makeText(this@ModLockActivity, "Biometric enabled", Toast.LENGTH_SHORT).show()
                        updateBiometricButtonText()
                        updateBiometricStatusText()
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Toast.makeText(this@ModLockActivity, "Error enabling biometric", Toast.LENGTH_SHORT).show()
                    } finally {
                        masterKeyToEncrypt = null
                    }
                } else {
                    Toast.makeText(this@ModLockActivity, "Cipher or key not available", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onAuthenticationFailed() {
                Toast.makeText(applicationContext, "Biometric failed", Toast.LENGTH_SHORT).show()
            }
        })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Biometric Authentication")
            .setSubtitle("Authenticate to enable")
            .setNegativeButtonText("Cancel")
            .build()

        val encryptionManager = EncryptionManager()
        val encryptCipher = encryptionManager.getEncryptCipher()
        if (encryptCipher != null) {
            biometricPrompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(encryptCipher))
        } else {
            Toast.makeText(this, "No cipher", Toast.LENGTH_SHORT).show()
        }
    }
}