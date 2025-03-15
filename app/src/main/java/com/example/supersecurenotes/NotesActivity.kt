package com.example.supersecurenotes

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Base64
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class NotesActivity : ImmersiveActivity() {
    private lateinit var passwordManager: PasswordManager
    private lateinit var sharedPreferences: SharedPreferences
    private val noteTitlesKey = "noteTitlesKey"
    private val GCM_TAG_LENGTH = 128

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notes)

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

        initializeButtons()
        updateNotesList()
    }

    override fun onResume() {
        super.onResume()
        val app = applicationContext as MyApplication
        if (app.isSessionExpired()) {
            app.clearSession()
            Toast.makeText(this, "Session expired. Please log in again.", Toast.LENGTH_SHORT).show()
            navigateToLogin()
        } else {
            app.updateLastActiveTime()
            updateNotesList()
        }
    }

    private fun initializeButtons() {
        val newNoteButton = findViewById<Button>(R.id.newNoteButton)
        newNoteButton.setOnClickListener {
            val app = applicationContext as MyApplication
            if (app.isSessionExpired()) {
                app.clearSession()
                Toast.makeText(this, "Session expired. Please log in again.", Toast.LENGTH_SHORT).show()
                navigateToLogin()
            } else {
                app.updateLastActiveTime()
                val intent = Intent(this, VisualizationActivity::class.java)
                intent.putExtra("noteTitle", "")
                startActivity(intent)
            }
        }

        val changePasswordButton = findViewById<Button>(R.id.changePasswordButton)
        changePasswordButton.setOnClickListener {
            val app = applicationContext as MyApplication
            if (app.isSessionExpired()) {
                app.clearSession()
                Toast.makeText(this, "Session expired. Please log in again.", Toast.LENGTH_SHORT).show()
                navigateToLogin()
            } else {
                app.updateLastActiveTime()
                val intent = Intent(this, ModLockActivity::class.java)
                startActivity(intent)
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

    /**
     * Recupera tutti i titoli dal set, poi per ognuno legge il timestamp salvato.
     * Ordina le note in base al timestamp (discendente) e infine popola la ListView.
     */
    private fun updateNotesList() {
        val allTitles = sharedPreferences.getStringSet(noteTitlesKey, mutableSetOf())!!.toList()

        // Mappiamo (titolo -> timestamp) e ordiniamo in base al timestamp discendente
        val titlesWithTimestamps = allTitles.map { title ->
            val lastModified = sharedPreferences.getLong("${title}_lastModified", 0L)
            title to lastModified
        }.sortedByDescending { it.second }

        // Otteniamo la lista dei titoli ordinati
        val sortedTitles = titlesWithTimestamps.map { it.first }

        val notesListView = findViewById<ListView>(R.id.notesListView)
        val emptyTextView = findViewById<TextView>(R.id.emptyTextView)

        if (sortedTitles.isEmpty()) {
            emptyTextView.text = "No notes available"
            emptyTextView.visibility = TextView.VISIBLE
        } else {
            emptyTextView.visibility = TextView.GONE
        }

        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, sortedTitles)
        notesListView.adapter = adapter

        notesListView.setOnItemClickListener { _, _, position, _ ->
            val selectedTitle = sortedTitles[position]
            val encodedContent = sharedPreferences.getString(selectedTitle, null)
            if (encodedContent != null) {
                val content = decryptNoteContent(encodedContent)
                if (content != null) {
                    val intent = Intent(this, VisualizationActivity::class.java)
                    intent.putExtra("noteTitle", selectedTitle)
                    intent.putExtra("noteContent", content)
                    startActivity(intent)
                } else {
                    Toast.makeText(this, "Decryption failed", Toast.LENGTH_SHORT).show()
                }
            }
        }

        notesListView.setOnItemLongClickListener { _, _, position, _ ->
            val selectedTitle = sortedTitles[position]
            showDeleteConfirmationDialog(selectedTitle)
            true
        }
    }

    /**
     * Mostra il dialog di conferma e, in caso affermativo, esegue l’eliminazione,
     * poi ricarica la lista aggiornandone la visualizzazione.
     */
    private fun showDeleteConfirmationDialog(noteTitle: String) {
        val dialogBuilder = androidx.appcompat.app.AlertDialog.Builder(this)
        dialogBuilder.setMessage("Do you really want to delete the note \"$noteTitle\"?")
            .setCancelable(false)
            .setPositiveButton("Yes") { dialog, _ ->
                deleteNote(noteTitle)
                updateNotesList()
                dialog.dismiss()
            }
            .setNegativeButton("No") { dialog, _ ->
                dialog.dismiss()
            }

        val alert = dialogBuilder.create()
        alert.setTitle("Delete Note")
        alert.show()
    }

    /**
     * Rimuove la nota (e il timestamp associato) dallo SharedPreferences.
     */
    private fun deleteNote(noteTitle: String) {
        val noteTitles = sharedPreferences.getStringSet(noteTitlesKey, mutableSetOf())!!.toMutableSet()
        noteTitles.remove(noteTitle)
        sharedPreferences.edit().putStringSet(noteTitlesKey, noteTitles).apply()
        sharedPreferences.edit()
            .remove(noteTitle)
            .remove("${noteTitle}_lastModified")
            .apply()
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