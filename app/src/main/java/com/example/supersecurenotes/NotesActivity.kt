package com.example.supersecurenotes

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class NotesActivity : AppCompatActivity() {

    private lateinit var passwordManager: PasswordManager
    private lateinit var sharedPreferences: SharedPreferences

    private lateinit var notesListView: ListView
    private lateinit var emptyTextView: TextView
    private lateinit var newNoteButton: Button
    private lateinit var changePasswordButton: Button
    private lateinit var deleteSelectedButton: Button

    // Key for storing note titles
    private val noteTitlesKey = "noteTitlesKey"

    // For GCM
    private val GCM_TAG_LENGTH = 128

    // Flag indicating if multi-selection mode is active
    private var isSelectionModeActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notes)

        // Session check
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

        notesListView = findViewById(R.id.notesListView)
        emptyTextView = findViewById(R.id.emptyTextView)
        newNoteButton = findViewById(R.id.newNoteButton)
        changePasswordButton = findViewById(R.id.changePasswordButton)
        deleteSelectedButton = findViewById(R.id.deleteSelectedButton)

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

    /**
     * If multi-selection mode is active and the user presses Back,
     * exit selection mode with a fade effect. Otherwise, use the default behavior.
     */
    override fun onBackPressed() {
        if (isSelectionModeActive) {
            switchSelectionMode(false)
        } else {
            super.onBackPressed()
        }
    }

    /**
     * Initializes the New Note, Change Password, and Delete buttons.
     */
    private fun initializeButtons() {
        // New Note button (+)
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

        // Change Password button (M)
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

        // Delete button (D)
        deleteSelectedButton.setOnClickListener {
            showDeleteConfirmationForSelected()
        }
    }

    /**
     * Builds/updates the note list based on the current mode.
     */
    private fun updateNotesList() {
        val allTitles = sharedPreferences.getStringSet(noteTitlesKey, mutableSetOf())!!.toList()

        // Map (title -> timestamp) and sort by descending timestamp
        val titlesWithTimestamps = allTitles.map { title ->
            val lastModified = sharedPreferences.getLong("${title}_lastModified", 0L)
            title to lastModified
        }.sortedByDescending { it.second }

        // Retrieve the sorted list of titles
        val sortedTitles = titlesWithTimestamps.map { it.first }

        if (sortedTitles.isEmpty()) {
            emptyTextView.text = "No notes available"
            emptyTextView.visibility = View.VISIBLE
        } else {
            emptyTextView.visibility = View.GONE
        }

        // Use multiple-choice layout if in multi-selection mode, otherwise simple list layout
        val layoutForList = if (isSelectionModeActive) {
            android.R.layout.simple_list_item_multiple_choice
        } else {
            android.R.layout.simple_list_item_1
        }

        val adapter = ArrayAdapter(this, layoutForList, sortedTitles)
        notesListView.adapter = adapter

        // Enable multiple choice if in selection mode, otherwise disable selection
        notesListView.choiceMode = if (isSelectionModeActive) {
            ListView.CHOICE_MODE_MULTIPLE
        } else {
            ListView.CHOICE_MODE_NONE
        }

        // Single click: open the note
        notesListView.setOnItemClickListener { _, _, position, _ ->
            if (!isSelectionModeActive) {
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
            // In selection mode, checkboxes are handled automatically
        }

        // Long click: activate multi-selection mode if not already active
        notesListView.setOnItemLongClickListener { _, _, position, _ ->
            if (!isSelectionModeActive) {
                switchSelectionMode(true, position)
            }
            true
        }
    }

    /**
     * Shows a confirmation pop-up for deleting the selected notes.
     * The dialog is cancelable, but selection mode remains active if canceled.
     */
    private fun showDeleteConfirmationForSelected() {
        val adapter = notesListView.adapter as ArrayAdapter<*>
        val count = adapter.count
        val selectedTitles = mutableListOf<String>()
        for (position in count - 1 downTo 0) {
            if (notesListView.isItemChecked(position)) {
                val noteTitle = adapter.getItem(position) as String
                selectedTitles.add(noteTitle)
            }
        }
        if (selectedTitles.isEmpty()) return

        val messageBuilder = StringBuilder()
        messageBuilder.append("Do you really want to delete the following notes?\n")
        for (title in selectedTitles) {
            messageBuilder.append(" - \"$title\"\n")
        }

        val dialogBuilder = AlertDialog.Builder(this)
        dialogBuilder.setMessage(messageBuilder.toString().trim())
            .setCancelable(true)
            .setPositiveButton("Yes") { dialog, _ ->
                for (title in selectedTitles) {
                    deleteNote(title)
                }
                switchSelectionMode(false)
                dialog.dismiss()
            }
            .setNegativeButton("No") { dialog, _ ->
                dialog.dismiss()
            }

        val alert = dialogBuilder.create()
        alert.setTitle("Delete Note")
        alert.setCanceledOnTouchOutside(true)
        alert.show()
    }

    /**
     * Centralized function to enable/disable selection mode with a fade effect.
     * @param enable true to enable multi-selection, false to disable.
     * @param positionToCheck if specified, immediately selects that item.
     */
    private fun switchSelectionMode(enable: Boolean, positionToCheck: Int? = null) {
        // 1) Fade out the ListView
        notesListView.animate()
            .alpha(0f)
            .setDuration(200)
            .withEndAction {
                isSelectionModeActive = enable

                if (!enable) {
                    deleteSelectedButton.animate()
                        .alpha(0f)
                        .setDuration(200)
                        .withEndAction {
                            deleteSelectedButton.visibility = View.GONE
                            deleteSelectedButton.alpha = 1f
                        }.start()
                    notesListView.clearChoices()
                    notesListView.requestLayout()
                }

                updateNotesList()

                if (enable) {
                    positionToCheck?.let { pos ->
                        notesListView.setItemChecked(pos, true)
                    }
                    deleteSelectedButton.alpha = 0f
                    deleteSelectedButton.visibility = View.VISIBLE
                    deleteSelectedButton.animate()
                        .alpha(1f)
                        .setDuration(200)
                        .start()
                }

                notesListView.animate()
                    .alpha(1f)
                    .setDuration(200)
                    .start()
            }
            .start()
    }

    /**
     * Removes the note and its timestamp from SharedPreferences.
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

    /**
     * Decrypts the note content and returns it as a string.
     */
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
}