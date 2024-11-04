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
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class ModLockActivity : AppCompatActivity() {

    private lateinit var passwordManager: PasswordManager
    private lateinit var sharedPreferences: SharedPreferences
    private val noteTitlesKey = "noteTitlesKey"
    private val GCM_TAG_LENGTH = 128

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mod_lock)

        val app = applicationContext as MyApplication
        if (app.isSessionExpired()) {
            app.clearSession()
            Toast.makeText(this, "Session expired. Please log in again.", Toast.LENGTH_SHORT).show()
            navigateToLogin()
            return
        } else {
            app.updateLastActiveTime()
        }

        passwordManager = PasswordManager(applicationContext)
        sharedPreferences = getSharedPreferences("notes_prefs", Context.MODE_PRIVATE)

        val oldPasswordEditText = findViewById<EditText>(R.id.previousPasswordEditText)
        val newPasswordEditText = findViewById<EditText>(R.id.newPasswordEditText)
        val passwordStrengthTextView = findViewById<TextView>(R.id.passwordStrengthTextView)
        val confirmPasswordEditText = findViewById<EditText>(R.id.repeatNewPasswordEditText)
        val saveButton = findViewById<Button>(R.id.savePasswordButton)

        // Monitoring the strength of the new password as the user types
        newPasswordEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val password = s.toString()
                val (isComplex, message) = checkPasswordComplexity(password)
                passwordStrengthTextView.text = if (isComplex) "Strong password" else message
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        saveButton.setOnClickListener {
            val oldPassword = oldPasswordEditText.text.toString()
            val newPassword = newPasswordEditText.text.toString()
            val confirmPassword = confirmPasswordEditText.text.toString()

            if (passwordManager.isPasswordCorrect(oldPassword)) {
                if (newPassword == confirmPassword) {
                    val (isComplex, message) = checkPasswordComplexity(newPassword)
                    if (isComplex) {
                        // 1. Decrypt all notes with the old session key
                        val decryptedNotes = decryptAllNotes()

                        if (decryptedNotes != null) {
                            // 2. Update password and derive new session key
                            passwordManager.setPassword(newPassword)
                            passwordManager.isPasswordCorrect(newPassword) // Derive the new session key

                            // 3. Re-encrypt all notes with the new session key
                            reEncryptAllNotes(decryptedNotes)

                            Toast.makeText(this, "Password updated successfully", Toast.LENGTH_SHORT).show()
                            finish()
                        } else {
                            Toast.makeText(this, "Error while decrypting notes", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(this, "New passwords do not match", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "The current password is incorrect", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun navigateToLogin() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    // Password complexity check function
    private fun checkPasswordComplexity(password: String): Pair<Boolean, String> {
        val minLength = 8
        val missingRequirements = mutableListOf<String>()

        if (password.length < minLength) {
            missingRequirements.add("at least $minLength characters")
        }
        if (!password.any { it.isUpperCase() }) {
            missingRequirements.add("a capital letter")
        }
        if (!password.any { it.isLowerCase() }) {
            missingRequirements.add("a lowercase letter")
        }
        if (!password.any { it.isDigit() }) {
            missingRequirements.add("a number")
        }
        if (!password.any { !it.isLetterOrDigit() }) {
            missingRequirements.add("a special character")
        }

        return if (missingRequirements.isEmpty()) {
            Pair(true, "The password meets all the requirements.")
        } else {
            val message = "Password must contain " + missingRequirements.joinToString(", ") + "."
            Pair(false, message)
        }
    }

    // Function to decrypt all existing notes
    private fun decryptAllNotes(): Map<String, String>? {
        val noteTitles = sharedPreferences.getStringSet(noteTitlesKey, mutableSetOf()) ?: return null
        val decryptedNotes = mutableMapOf<String, String>()

        for (title in noteTitles) {
            val encodedContent = sharedPreferences.getString(title, null)
            if (encodedContent != null) {
                val content = decryptNoteContent(encodedContent)
                if (content != null) {
                    decryptedNotes[title] = content
                } else {
                    return null // Error during decryption
                }
            }
        }
        return decryptedNotes
    }

    // Function to encrypt all notes with new session key
    private fun reEncryptAllNotes(decryptedNotes: Map<String, String>) {
        for ((title, content) in decryptedNotes) {
            val encryptedContent = encryptNoteContent(content)
            if (encryptedContent != null) {
                val encodedContent = Base64.encodeToString(encryptedContent, Base64.DEFAULT)
                sharedPreferences.edit().putString(title, encodedContent).apply()
            }
        }
    }

    private fun getSessionKey(): ByteArray? {
        return passwordManager.getSessionKey()
    }

    private fun encryptNoteContent(content: String): ByteArray? {
        val key = getSessionKey() ?: return null
        return try {
            val secretKey = SecretKeySpec(key, "AES")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val ciphertext = cipher.doFinal(content.toByteArray())
            iv + ciphertext
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun decryptNoteContent(encodedData: String): String? {
        val encryptedData = Base64.decode(encodedData, Base64.DEFAULT)
        val key = getSessionKey() ?: return null
        return try {
            val secretKey = SecretKeySpec(key, "AES")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val iv = encryptedData.sliceArray(0 until 12)
            val encryptedText = encryptedData.sliceArray(12 until encryptedData.size)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            String(cipher.doFinal(encryptedText))
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}