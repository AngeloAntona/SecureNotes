package com.example.supersecurenotes

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
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
        initializeButtons()
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

    // Method to initialize buttons
    private fun initializeButtons() {
        // Button to create a new note
        val newNoteButton = findViewById<Button>(R.id.newNoteButton)
        newNoteButton.setOnClickListener {
            // Open an empty VisualizationActivity to create a new note
            val intent = Intent(this, VisualizationActivity::class.java)
            intent.putExtra("noteTitle", "") // Passing an empty title
            startActivity(intent)
        }

        // Button to change the password
        val changePasswordButton = findViewById<Button>(R.id.changePasswordButton)
        changePasswordButton.setOnClickListener {
            // Open ModLockActivity to change the password
            val intent = Intent(this, ModLockActivity::class.java)
            startActivity(intent)
        }
    }

    private fun updateNotesList() {
        val noteTitles = sharedPreferences.getStringSet(noteTitlesKey, mutableSetOf())!!.toMutableList()
        val notesListView = findViewById<ListView>(R.id.notesListView)
        val emptyTextView = findViewById<TextView>(R.id.emptyTextView)

        // Show a message if there are no notes
        if (noteTitles.isEmpty()) {
            emptyTextView.text = "No notes available"
            emptyTextView.visibility = TextView.VISIBLE
        } else {
            emptyTextView.visibility = TextView.GONE
        }

        // Adapt the data to the ListView
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, noteTitles)
        notesListView.adapter = adapter

        // Handle item clicks in the list
        notesListView.setOnItemClickListener { _, _, position, _ ->
            val selectedTitle = noteTitles[position]
            val intent = Intent(this, VisualizationActivity::class.java)
            intent.putExtra("noteTitle", selectedTitle)
            startActivity(intent)
        }

        // Handle long presses on list items
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
        dialogBuilder.setMessage("Do you really want to delete the note \"$noteTitle\"?")
            .setCancelable(false)
            .setPositiveButton("Yes") { dialog, _ ->
                deleteNote(noteTitle)
                noteTitles.removeAt(position)
                adapter.notifyDataSetChanged()
                dialog.dismiss()
            }
            .setNegativeButton("No") { dialog, _ ->
                dialog.dismiss()
            }

        val alert = dialogBuilder.create()
        alert.setTitle("Delete Note")
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