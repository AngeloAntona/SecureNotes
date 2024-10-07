package com.example.supersecurenotes

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

class VisualizationActivity : AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences
    private val noteTitlesKey = "noteTitlesKey"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_visualization)

        sharedPreferences = createEncryptedSharedPreferences(this)

        val originalTitle = intent.getStringExtra("noteTitle") ?: ""
        val titleEditText = findViewById<EditText>(R.id.noteTitleEditText)
        val bodyEditText = findViewById<EditText>(R.id.noteBodyEditText)
        val saveButton = findViewById<Button>(R.id.saveButton)

        // Set the title and content of the note
        titleEditText.setText(originalTitle)
        val noteContent = sharedPreferences.getString(originalTitle, "")
        bodyEditText.setText(noteContent)

        // Handle the click on the "Save" button
        saveButton.setOnClickListener {
            val modifiedTitle = titleEditText.text.toString()
            val modifiedBody = bodyEditText.text.toString()

            if (originalTitle != modifiedTitle) {
                removeOldNote(originalTitle)
            }

            saveNote(modifiedTitle, modifiedBody)
            Toast.makeText(this, "Note saved", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun createEncryptedSharedPreferences(context: Context): SharedPreferences {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

        return EncryptedSharedPreferences.create(
            "secure_shared_prefs",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun removeOldNote(oldTitle: String) {
        val noteTitles = sharedPreferences.getStringSet(noteTitlesKey, mutableSetOf())!!.toMutableSet()
        noteTitles.remove(oldTitle)
        sharedPreferences.edit().putStringSet(noteTitlesKey, noteTitles).apply()

        // Remove the note content associated with the old title
        sharedPreferences.edit().remove(oldTitle).apply()
    }

    private fun saveNote(newTitle: String, content: String) {
        val noteTitles = sharedPreferences.getStringSet(noteTitlesKey, mutableSetOf())!!.toMutableSet()

        // Add the new title to the list of titles
        noteTitles.add(newTitle)
        sharedPreferences.edit().putStringSet(noteTitlesKey, noteTitles).apply()

        // Save the note content
        sharedPreferences.edit().putString(newTitle, content).apply()
    }
}