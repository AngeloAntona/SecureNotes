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

        // Recupera il titolo originale della nota dall'intent
        val originalTitle = intent.getStringExtra("noteTitle") ?: ""

        // Trova gli elementi del layout
        val titleEditText = findViewById<EditText>(R.id.noteTitleEditText)
        val bodyEditText = findViewById<EditText>(R.id.noteBodyEditText)
        val saveButton = findViewById<Button>(R.id.saveButton)

        // Imposta il titolo e il contenuto della nota
        titleEditText.setText(originalTitle)
        val noteContent = sharedPreferences.getString(originalTitle, "")
        bodyEditText.setText(noteContent)

        // Gestione del clic sul pulsante "Salva"
        saveButton.setOnClickListener {
            val modifiedTitle = titleEditText.text.toString()
            val modifiedBody = bodyEditText.text.toString()

            // Se il titolo è stato modificato, rimuove la vecchia nota
            if (originalTitle != modifiedTitle) {
                removeOldNote(originalTitle)
            }

            // Salva la nuova nota (titolo e contenuto)
            saveNote(modifiedTitle, modifiedBody)

            // Messaggio di conferma
            Toast.makeText(this, "Nota salvata", Toast.LENGTH_SHORT).show()

            // Chiude l'activity una volta premuto "Salva"
            finish()
        }
    }

    // Metodo per creare EncryptedSharedPreferences
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

    // Metodo per rimuovere una vecchia nota
    private fun removeOldNote(oldTitle: String) {
        val noteTitles = sharedPreferences.getStringSet(noteTitlesKey, mutableSetOf())!!.toMutableSet()
        noteTitles.remove(oldTitle)
        sharedPreferences.edit().putStringSet(noteTitlesKey, noteTitles).apply()

        // Rimuovi anche il contenuto della nota associata al vecchio titolo
        sharedPreferences.edit().remove(oldTitle).apply()
    }

    // Metodo per salvare una nuova nota
    private fun saveNote(newTitle: String, content: String) {
        val noteTitles = sharedPreferences.getStringSet(noteTitlesKey, mutableSetOf())!!.toMutableSet()

        // Aggiungi il nuovo titolo alla lista dei titoli
        noteTitles.add(newTitle)
        sharedPreferences.edit().putStringSet(noteTitlesKey, noteTitles).apply()

        // Salva il contenuto della nota
        sharedPreferences.edit().putString(newTitle, content).apply()
    }
}