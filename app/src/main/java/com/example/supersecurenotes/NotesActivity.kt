package com.example.supersecurenotes

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

class NotesActivity : AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences
    private val noteTitlesKey = "noteTitlesKey"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notes)

        sharedPreferences = createEncryptedSharedPreferences(this)

        // Inizializza i pulsanti
        initializeButtons()

        // Aggiorna la lista delle note
        updateNotesList()
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

    // Metodo per inizializzare i pulsanti
    private fun initializeButtons() {
        // Pulsante per creare una nuova nota
        val newNoteButton = findViewById<Button>(R.id.newNoteButton)
        newNoteButton.setOnClickListener {
            // Apri una VisualizationActivity vuota per creare una nuova nota
            val intent = Intent(this, VisualizationActivity::class.java)
            intent.putExtra("noteTitle", "") // Passiamo un titolo vuoto
            startActivity(intent)
        }

        // Pulsante per modificare la password
        val changePasswordButton = findViewById<Button>(R.id.changePasswordButton)
        changePasswordButton.setOnClickListener {
            // Apri ModLockActivity per cambiare la password
            val intent = Intent(this, ModLockActivity::class.java)
            startActivity(intent)
        }
    }

    private fun updateNotesList() {
        val noteTitles = sharedPreferences.getStringSet(noteTitlesKey, mutableSetOf())!!.toMutableList()
        val notesListView = findViewById<ListView>(R.id.notesListView)
        val emptyTextView = findViewById<TextView>(R.id.emptyTextView)

        // Mostra un messaggio se non ci sono note
        if (noteTitles.isEmpty()) {
            emptyTextView.text = "Nessuna nota disponibile"
            emptyTextView.visibility = TextView.VISIBLE
        } else {
            emptyTextView.visibility = TextView.GONE
        }

        // Adatta i dati alla ListView
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, noteTitles)
        notesListView.adapter = adapter

        // Gestisci il clic sugli elementi della lista
        notesListView.setOnItemClickListener { _, _, position, _ ->
            val selectedTitle = noteTitles[position]
            val intent = Intent(this, VisualizationActivity::class.java)
            intent.putExtra("noteTitle", selectedTitle)
            startActivity(intent)
        }

        // Gestisci la pressione prolungata sugli elementi della lista
        notesListView.setOnItemLongClickListener { _, _, position, _ ->
            val selectedTitle = noteTitles[position]
            showDeleteConfirmationDialog(selectedTitle, position, adapter, noteTitles)
            true
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

    override fun onResume() {
        super.onResume()
        updateNotesList()
    }
}