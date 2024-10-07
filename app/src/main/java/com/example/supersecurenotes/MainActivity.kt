package com.example.supersecurenotes

import PasswordManager
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

class MainActivity : AppCompatActivity() {

    private lateinit var secureNotesManager: PasswordManager

    override fun onCreate(savedInstanceState: Bundle?) {
        // Installa lo splash screen
        installSplashScreen() // Funzionalità mantenuta dal vecchio codice

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Inizializza il gestore delle password dinamico
        secureNotesManager = PasswordManager(this)

        // Trova il campo di testo e il pulsante nel layout
        val passwordEditText = findViewById<EditText>(R.id.passwordEditText)
        val loginButton = findViewById<Button>(R.id.loginButton)

        // Gestione del clic sul pulsante di login
        loginButton.setOnClickListener {
            val enteredPassword = passwordEditText.text.toString()

            // Verifica se la password inserita è corretta usando il PasswordManager
            if (secureNotesManager.isPasswordCorrect(enteredPassword)) {
                // Se la password è corretta, avvia la NotesActivity
                val intent = Intent(this, NotesActivity::class.java)
                startActivity(intent)
            } else {
                // Mostra un messaggio di errore
                Toast.makeText(this, "Password errata", Toast.LENGTH_SHORT).show()
            }
        }
    }
}