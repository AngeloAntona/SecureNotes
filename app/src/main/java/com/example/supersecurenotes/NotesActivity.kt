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
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class NotesActivity : AppCompatActivity() {
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
            Toast.makeText(this, "Sessione scaduta. Esegui di nuovo l'accesso.", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, "Sessione scaduta. Esegui di nuovo l'accesso.", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this, "Sessione scaduta. Esegui di nuovo l'accesso.", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this, "Sessione scaduta. Esegui di nuovo l'accesso.", Toast.LENGTH_SHORT).show()
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

    private fun updateNotesList() {
        val noteTitles = sharedPreferences.getStringSet(noteTitlesKey, mutableSetOf())!!.toMutableList()
        val notesListView = findViewById<ListView>(R.id.notesListView)
        val emptyTextView = findViewById<TextView>(R.id.emptyTextView)

        if (noteTitles.isEmpty()) {
            emptyTextView.text = "No notes available"
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
                    Toast.makeText(this, "Decryption failed", Toast.LENGTH_SHORT).show()
                }
            }
        }

        notesListView.setOnItemLongClickListener { _, _, position, _ ->
            val selectedTitle = noteTitles[position]
            showDeleteConfirmationDialog(selectedTitle, position, adapter, noteTitles)
            true
        }
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

    private fun decryptNoteContent(encodedData: String): String? {
        val encryptedData = Base64.decode(encodedData, Base64.DEFAULT)
        val key = getSessionKey() ?: run {
            Toast.makeText(this, "Session expired. Please log in again.", Toast.LENGTH_SHORT).show()
            finish()
            return null
        }
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
}