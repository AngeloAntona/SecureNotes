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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class NotesActivity : AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences
    private val noteTitlesKey = "noteTitlesKey"
    private val encryptionManager = EncryptionManager()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notes)

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

        initializeButtons()
        updateNotesList()
    }

    override fun onResume() {
        super.onResume()
        val app = applicationContext as MyApplication
        if (app.isSessionExpired()) {
            app.clearSession()
            Toast.makeText(this, "Sessione scaduta. Effettua nuovamente l'accesso.", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this, "Sessione scaduta. Effettua nuovamente l'accesso.", Toast.LENGTH_SHORT).show()
                navigateToLogin()
            } else {
                app.updateLastActiveTime()
                val intent = Intent(this, VisualizationActivity::class.java)
                intent.putExtra("noteTitle", "")
                startActivity(intent)
            }
        }

        val lockButton = findViewById<Button>(R.id.lockButton)
        lockButton.setOnClickListener {
            val app = applicationContext as MyApplication
            app.clearSession()
            navigateToLogin()
        }
    }

    private fun navigateToLogin() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun updateNotesList() {
        val noteTitles = sharedPreferences.getStringSet(noteTitlesKey, mutableSetOf())!!.toMutableList()
        val notesListView = findViewById<ListView>(R.id.notesListView)
        val emptyTextView = findViewById<TextView>(R.id.emptyTextView)

        if (noteTitles.isEmpty()) {
            emptyTextView.text = "Nessuna nota disponibile"
            emptyTextView.visibility = TextView.VISIBLE
        } else {
            emptyTextView.visibility = TextView.GONE
        }

        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, noteTitles)
        notesListView.adapter = adapter

        notesListView.setOnItemClickListener { _, _, position, _ ->
            val selectedTitle = noteTitles[position]
            val encodedContent = sharedPreferences.getString(selectedTitle, null)
            if (encodedContent != null) {
                val content = decryptNoteContent(encodedContent)
                if (content != null) {
                    val intent = Intent(this, VisualizationActivity::class.java)
                    intent.putExtra("noteTitle", selectedTitle)
                    intent.putExtra("noteContent", content)
                    startActivity(intent)
                } else {
                    Toast.makeText(this, "Decrittazione fallita", Toast.LENGTH_SHORT).show()
                }
            }
        }

        notesListView.setOnItemLongClickListener { _, _, position, _ ->
            val selectedTitle = noteTitles[position]
            showDeleteConfirmationDialog(selectedTitle, position, adapter, noteTitles)
            true
        }
    }

    private fun decryptNoteContent(encodedData: String): String? {
        val encryptedData = Base64.decode(encodedData, Base64.DEFAULT)
        return try {
            val iv = encryptedData.sliceArray(0 until 12)
            val encryptedText = encryptedData.sliceArray(12 until encryptedData.size)
            val cipher = encryptionManager.getDecryptCipher(iv)
            if (cipher != null) {
                String(cipher.doFinal(encryptedText))
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun showDeleteConfirmationDialog(
        noteTitle: String,
        position: Int,
        adapter: ArrayAdapter<String>,
        noteTitles: MutableList<String>
    ) {
        val dialogBuilder = AlertDialog.Builder(this)
        dialogBuilder.setMessage("Vuoi davvero eliminare la nota \"$noteTitle\"?")
            .setCancelable(false)
            .setPositiveButton("Sì") { dialog, _ ->
                deleteNote(noteTitle)
                noteTitles.removeAt(position)
                adapter.notifyDataSetChanged()
                dialog.dismiss()
            }
            .setNegativeButton("No") { dialog, _ ->
                dialog.dismiss()
            }

        val alert = dialogBuilder.create()
        alert.setTitle("Elimina Nota")
        alert.show()
    }

    private fun deleteNote(noteTitle: String) {
        val noteTitles = sharedPreferences.getStringSet(noteTitlesKey, mutableSetOf())!!.toMutableSet()
        noteTitles.remove(noteTitle)
        sharedPreferences.edit().putStringSet(noteTitlesKey, noteTitles).apply()
        sharedPreferences.edit().remove(noteTitle).apply()
    }
}