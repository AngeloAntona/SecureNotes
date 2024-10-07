package com.example.supersecurenotes

import PasswordManager
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class ModLockActivity : AppCompatActivity() {

    private lateinit var secureNotesManager: PasswordManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mod_lock)

        secureNotesManager = PasswordManager(this)

        val oldPasswordEditText = findViewById<EditText>(R.id.previousPasswordEditText)
        val newPasswordEditText = findViewById<EditText>(R.id.newPasswordEditText)
        val confirmPasswordEditText = findViewById<EditText>(R.id.repeatNewPasswordEditText)
        val saveButton = findViewById<Button>(R.id.savePasswordButton)

        saveButton.setOnClickListener {
            val oldPassword = oldPasswordEditText.text.toString()
            val newPassword = newPasswordEditText.text.toString()
            val confirmPassword = confirmPasswordEditText.text.toString()

            if (secureNotesManager.isPasswordCorrect(oldPassword)) {
                if (newPassword == confirmPassword) {
                    secureNotesManager.setPassword(newPassword)
                    Toast.makeText(this, "Password aggiornata con successo", Toast.LENGTH_SHORT).show()
                    finish() // Torna all'activity precedente
                } else {
                    Toast.makeText(this, "Le nuove password non corrispondono", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "La password precedente è errata", Toast.LENGTH_SHORT).show()
            }
        }
    }
}