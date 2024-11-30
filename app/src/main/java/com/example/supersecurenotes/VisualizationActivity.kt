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

class VisualizationActivity : AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences
    private val noteTitlesKey = "noteTitlesKey"
    private val encryptionManager = EncryptionManager()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_visualization)

        val app = applicationContext as MyApplication
        if (app.isSessionExpired()) {
            app.clearSession()
            Toast.makeText(this, "Sessione scaduta. Effettua nuovamente l'accesso.", Toast.LENGTH_SHORT).show()
            navigateToLogin()
            return
        } else {
            app.updateLastActiveTime()
        }

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
                Toast.makeText(this, "Sessione scaduta. Effettua nuovamente l'accesso.", Toast.LENGTH_SHORT).show()
                navigateToLogin()
            } else {
                app.updateLastActiveTime()
                val modifiedTitle = titleEditText.text.toString()
                val modifiedBody = bodyEditText.text.toString()

                if (originalTitle != modifiedTitle) {
                    removeOldNote(originalTitle)
                }

                saveEncryptedNote(modifiedTitle, modifiedBody)
                Toast.makeText(this, "Nota salvata", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun navigateToLogin() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun encryptNoteContent(content: String): ByteArray? {
        return try {
            val cipher = encryptionManager.getEncryptCipher()
            if (cipher != null) {
                val iv = cipher.iv
                val ciphertext = cipher.doFinal(content.toByteArray())
                iv + ciphertext
            } else {
                null
            }
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
            Toast.makeText(this, "Impossibile criptare la nota", Toast.LENGTH_SHORT).show()
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