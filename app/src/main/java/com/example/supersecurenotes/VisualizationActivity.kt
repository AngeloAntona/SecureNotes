package com.example.supersecurenotes

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Base64
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class VisualizationActivity : AppCompatActivity() {

    private lateinit var passwordManager: PasswordManager
    private lateinit var sharedPreferences: SharedPreferences
    private val noteTitlesKey = "noteTitlesKey"
    private val GCM_TAG_LENGTH = 128

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_visualization)

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

        val originalTitle = intent.getStringExtra("noteTitle") ?: ""
        val noteContent = intent.getStringExtra("noteContent") ?: ""
        val titleEditText = findViewById<EditText>(R.id.noteTitleEditText)
        val bodyEditText = findViewById<EditText>(R.id.noteBodyEditText)
        val saveButton = findViewById<Button>(R.id.saveButton)

        titleEditText.setText(originalTitle)
        bodyEditText.setText(noteContent)

        saveButton.setOnClickListener {
            val app = applicationContext as MyApplication
            if (app.isSessionExpired()) {
                app.clearSession()
                Toast.makeText(this, "Session expired. Please log in again.", Toast.LENGTH_SHORT).show()
                navigateToLogin()
            } else {
                app.updateLastActiveTime()
                val modifiedTitle = titleEditText.text.toString()
                val modifiedBody = bodyEditText.text.toString()

                if (originalTitle != modifiedTitle) {
                    removeOldNote(originalTitle)
                }

                saveEncryptedNote(modifiedTitle, modifiedBody)
                Toast.makeText(this, "Note saved", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun getSessionKey(): ByteArray? {
        val app = applicationContext as MyApplication
        return app.sessionKey
    }

    private fun navigateToLogin() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun encryptNoteContent(content: String): ByteArray? {
        val key = getSessionKey() ?: run {
            Toast.makeText(this, "Session expired. Please log in again.", Toast.LENGTH_SHORT).show()
            finish()
            return null
        }
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

    private fun saveEncryptedNote(title: String, content: String) {
        val encryptedContent = encryptNoteContent(content)
        if (encryptedContent != null) {
            val encodedContent = Base64.encodeToString(encryptedContent, Base64.DEFAULT)
            sharedPreferences.edit().putString(title, encodedContent).apply()
            val noteTitles = sharedPreferences.getStringSet(noteTitlesKey, mutableSetOf())!!.toMutableSet()
            noteTitles.add(title)
            sharedPreferences.edit().putStringSet(noteTitlesKey, noteTitles).apply()
        } else {
            Toast.makeText(this, "Failed to encrypt note", Toast.LENGTH_SHORT).show()
        }
    }

    private fun removeOldNote(oldTitle: String) {
        if (oldTitle.isBlank()) return

        val noteTitles = sharedPreferences.getStringSet(noteTitlesKey, mutableSetOf())!!.toMutableSet()
        noteTitles.remove(oldTitle)
        sharedPreferences.edit().putStringSet(noteTitlesKey, noteTitles).apply()
        sharedPreferences.edit().remove(oldTitle).apply()
    }
}