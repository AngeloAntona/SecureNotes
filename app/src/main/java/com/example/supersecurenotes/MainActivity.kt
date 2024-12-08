package com.example.supersecurenotes

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

class MainActivity : AppCompatActivity() {

    private lateinit var passwordManager: PasswordManager
    private lateinit var usePasswordButton: Button
    private lateinit var passwordLayout: EditText
    private lateinit var loginButton: Button
    private var showingPasswordLayout = false

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        passwordManager = PasswordManager(applicationContext)

        usePasswordButton = findViewById(R.id.usePasswordButton)
        passwordLayout = findViewById(R.id.passwordEditText)
        loginButton = findViewById(R.id.loginButton)

        if (!passwordManager.isPasswordSet()) {
            val intent = Intent(this, SetPasswordActivity::class.java)
            startActivity(intent)
            finish()
            return
        }

        if (passwordManager.isBiometricEnabled()) {
            usePasswordButton.visibility = Button.VISIBLE
            usePasswordButton.setOnClickListener {
                showPasswordLogin()
            }
            showBiometricPrompt()
        } else {
            showPasswordLogin()
        }

        loginButton.setOnClickListener {
            val enteredPassword = passwordLayout.text.toString()

            if (passwordManager.isLockedOut()) {
                Toast.makeText(this, "Account blocked. Try again later.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            if (passwordManager.isPasswordCorrect(enteredPassword)) {
                // Master Key caricata in sessionKey
                val app = applicationContext as MyApplication
                app.updateLastActiveTime()
                val intent = Intent(this, NotesActivity::class.java)
                startActivity(intent)
                finish()
            } else {
                Toast.makeText(this, "Wrong password", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showPasswordLogin() {
        showingPasswordLayout = true
        passwordLayout.visibility = EditText.VISIBLE
        loginButton.visibility = Button.VISIBLE
        usePasswordButton.visibility = Button.GONE
    }

    private fun showBiometricPrompt() {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    showPasswordLogin()
                } else {
                    Toast.makeText(applicationContext, "Biometric auth error: $errString", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                val encryptedMasterKey = passwordManager.getEncryptedMasterKeyBiometric()
                if (encryptedMasterKey != null) {
                    val cipher = result.cryptoObject?.cipher
                    if (cipher != null) {
                        val ciphertext = encryptedMasterKey.sliceArray(12 until encryptedMasterKey.size)
                        val masterKey = cipher.doFinal(ciphertext)

                        val app = applicationContext as MyApplication
                        app.sessionKey = masterKey
                        app.updateLastActiveTime()

                        val intent = Intent(this@MainActivity, NotesActivity::class.java)
                        startActivity(intent)
                        finish()
                    } else {
                        Toast.makeText(applicationContext, "Cipher not available", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(applicationContext, "No biometric master key found", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                Toast.makeText(applicationContext, "Biometric auth failed", Toast.LENGTH_SHORT).show()
            }
        })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Biometric Login for Secure Notes")
            .setSubtitle("Place your finger on the sensor")
            .setNegativeButtonText("Use Password")
            .build()

        val encryptedMasterKeyBiometric = passwordManager.getEncryptedMasterKeyBiometric()
        if (encryptedMasterKeyBiometric != null) {
            val iv = encryptedMasterKeyBiometric.sliceArray(0 until 12)
            val cipherForDecrypt = EncryptionManager().getDecryptCipher(iv)
            if (cipherForDecrypt != null) {
                biometricPrompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipherForDecrypt))
            } else {
                showPasswordLogin()
            }
        } else {
            showPasswordLogin()
        }
    }
}