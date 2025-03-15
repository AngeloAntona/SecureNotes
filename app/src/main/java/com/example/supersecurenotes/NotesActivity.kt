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

    // Chiave per salvare i titoli delle note
    private val noteTitlesKey = "noteTitlesKey"

    // Per GCM
    private val GCM_TAG_LENGTH = 128

    // Flag che indica se siamo in "modalità selezione multipla"
    private var isSelectionModeActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notes)

        // Controllo sessione
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
     * Se siamo in modalità selezione e l'utente preme il tasto Back,
     * usciamo dalla selezione con dissolvenza. Altrimenti comportamento di default.
     */
    override fun onBackPressed() {
        if (isSelectionModeActive) {
            switchSelectionMode(false)
        } else {
            super.onBackPressed()
        }
    }

    /**
     * Inizializza i pulsanti: Nuova Nota (+), Cambia Password (M), e Elimina (D).
     */
    private fun initializeButtons() {
        // Pulsante NUOVA NOTA (+)
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

        // Pulsante MODIFICA (M)
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

        // Pulsante DELETE (D)
        deleteSelectedButton.setOnClickListener {
            showDeleteConfirmationForSelected()
        }
    }

    /**
     * Costruisce/aggiorna la lista delle note in base alla modalità corrente.
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

        if (sortedTitles.isEmpty()) {
            emptyTextView.text = "No notes available"
            emptyTextView.visibility = View.VISIBLE
        } else {
            emptyTextView.visibility = View.GONE
        }

        // Se in selezione multipla, useremo "simple_list_item_multiple_choice"
        // per avere la bullet list (checkbox). Altrimenti "simple_list_item_1".
        val layoutForList = if (isSelectionModeActive) {
            android.R.layout.simple_list_item_multiple_choice
        } else {
            android.R.layout.simple_list_item_1
        }

        val adapter = ArrayAdapter(this, layoutForList, sortedTitles)
        notesListView.adapter = adapter

        // Se in selezione, abilitiamo la choiceMode multipla, altrimenti nessuna selezione
        notesListView.choiceMode = if (isSelectionModeActive) {
            ListView.CHOICE_MODE_MULTIPLE
        } else {
            ListView.CHOICE_MODE_NONE
        }

        // Clic singolo
        notesListView.setOnItemClickListener { _, _, position, _ ->
            if (!isSelectionModeActive) {
                // Apriamo la nota
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
            // In modalità selezione, i checkbox sono gestiti automaticamente
        }

        // Clic lungo: avvia la modalità selezione multipla, se non è già attiva
        notesListView.setOnItemLongClickListener { _, _, position, _ ->
            if (!isSelectionModeActive) {
                switchSelectionMode(true, position)
            }
            true
        }
    }

    /**
     * Mostra il pop-up di conferma (AlertDialog) in stile identico al tuo vecchio pop-up,
     * adattato alla multi-selezione, e che segue il tema chiaro/scuro.
     * Se l'utente preme il tasto Back, il pop-up scompare, ma la modalità selezione rimane attiva.
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
            .setCancelable(true)  // Il dialog è cancelabile
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
        // Non impostiamo un OnCancelListener che disattiva la modalità di selezione,
        // così se si preme Back il dialog scompare ma la modalità rimane attiva.
        alert.show()
    }

    /**
     * Funzione centralizzata per entrare/uscire dalla modalità selezione con dissolvenza.
     * @param enable true per passare a selezione multipla, false per tornare normale.
     * @param positionToCheck, se specificato, seleziona immediatamente quell'item.
     */
    private fun switchSelectionMode(enable: Boolean, positionToCheck: Int? = null) {
        // 1) Fade out della ListView
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
     * Rimuove la nota (e il relativo timestamp) dallo SharedPreferences.
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
     * Decritta il contenuto di una nota, restituendolo come stringa.
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