package com.example.supersecurenotes

import android.content.Context
import android.content.Intent // Aggiungi questa riga per risolvere l'errore
import android.content.SharedPreferences
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Base64
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class ModLockActivity : AppCompatActivity() {

    private lateinit var passwordManager: PasswordManager
    private lateinit var sharedPreferences: SharedPreferences
    private val noteTitlesKey = "noteTitlesKey"
    private val GCM_TAG_LENGTH = 128

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mod_lock)

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

        val oldPasswordEditText = findViewById<EditText>(R.id.previousPasswordEditText)
        val newPasswordEditText = findViewById<EditText>(R.id.newPasswordEditText)
        val passwordStrengthTextView = findViewById<TextView>(R.id.passwordStrengthTextView)
        val confirmPasswordEditText = findViewById<EditText>(R.id.repeatNewPasswordEditText)
        val saveButton = findViewById<Button>(R.id.savePasswordButton)

        // Monitoraggio della forza della nuova password mentre l'utente digita
        newPasswordEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val password = s.toString()
                val (isComplex, message) = checkPasswordComplexity(password)
                passwordStrengthTextView.text = if (isComplex) "Password forte" else message
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        saveButton.setOnClickListener {
            val oldPassword = oldPasswordEditText.text.toString()
            val newPassword = newPasswordEditText.text.toString()
            val confirmPassword = confirmPasswordEditText.text.toString()

            if (passwordManager.isPasswordCorrect(oldPassword)) {
                if (newPassword == confirmPassword) {
                    val (isComplex, message) = checkPasswordComplexity(newPassword)
                    if (isComplex) {
                        // 1. Decrittografa tutte le note con la vecchia chiave di sessione
                        val decryptedNotes = decryptAllNotes()

                        if (decryptedNotes != null) {
                            // 2. Aggiorna la password e deriva la nuova chiave di sessione
                            passwordManager.setPassword(newPassword)
                            passwordManager.isPasswordCorrect(newPassword) // Deriva la nuova chiave di sessione

                            // 3. Ricrittografa tutte le note con la nuova chiave di sessione
                            reEncryptAllNotes(decryptedNotes)

                            Toast.makeText(this, "Password aggiornata con successo", Toast.LENGTH_SHORT).show()
                            finish() // Torna all'attività precedente
                        } else {
                            Toast.makeText(this, "Errore durante la decrittografia delle note", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(this, "Le nuove password non corrispondono", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "La password attuale è errata", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun navigateToLogin() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    // Funzione per verificare la complessità della password
    private fun checkPasswordComplexity(password: String): Pair<Boolean, String> {
        val minLength = 8
        val missingRequirements = mutableListOf<String>()

        if (password.length < minLength) {
            missingRequirements.add("almeno $minLength caratteri")
        }
        if (!password.any { it.isUpperCase() }) {
            missingRequirements.add("una lettera maiuscola")
        }
        if (!password.any { it.isLowerCase() }) {
            missingRequirements.add("una lettera minuscola")
        }
        if (!password.any { it.isDigit() }) {
            missingRequirements.add("un numero")
        }
        if (!password.any { !it.isLetterOrDigit() }) {
            missingRequirements.add("un carattere speciale")
        }

        return if (missingRequirements.isEmpty()) {
            Pair(true, "La password soddisfa tutti i requisiti.")
        } else {
            val message = "La password deve contenere " + missingRequirements.joinToString(", ") + "."
            Pair(false, message)
        }
    }

    // Funzione per decrittografare tutte le note esistenti
    private fun decryptAllNotes(): Map<String, String>? {
        val noteTitles = sharedPreferences.getStringSet(noteTitlesKey, mutableSetOf()) ?: return null
        val decryptedNotes = mutableMapOf<String, String>()

        for (title in noteTitles) {
            val encodedContent = sharedPreferences.getString(title, null)
            if (encodedContent != null) {
                val content = decryptNoteContent(encodedContent)
                if (content != null) {
                    decryptedNotes[title] = content
                } else {
                    return null // Errore durante la decrittografia
                }
            }
        }
        return decryptedNotes
    }

    // Funzione per ricrittografare tutte le note con la nuova chiave di sessione
    private fun reEncryptAllNotes(decryptedNotes: Map<String, String>) {
        for ((title, content) in decryptedNotes) {
            val encryptedContent = encryptNoteContent(content)
            if (encryptedContent != null) {
                val encodedContent = Base64.encodeToString(encryptedContent, Base64.DEFAULT)
                sharedPreferences.edit().putString(title, encodedContent).apply()
            }
        }
    }

    private fun getSessionKey(): ByteArray? {
        return passwordManager.getSessionKey()
    }

    private fun encryptNoteContent(content: String): ByteArray? {
        val key = getSessionKey() ?: return null
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