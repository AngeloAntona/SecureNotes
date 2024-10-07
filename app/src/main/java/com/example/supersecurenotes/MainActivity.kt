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
        // Install the splash screen
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        secureNotesManager = PasswordManager(this)
        val passwordEditText = findViewById<EditText>(R.id.passwordEditText)
        val loginButton = findViewById<Button>(R.id.loginButton)

        // Handle the login button click
        loginButton.setOnClickListener {
            val enteredPassword = passwordEditText.text.toString()

            // Verify if the entered password is correct using PasswordManager
            if (secureNotesManager.isPasswordCorrect(enteredPassword)) {
                // If the password is correct, start the NotesActivity
                val intent = Intent(this, NotesActivity::class.java)
                startActivity(intent)
            } else {
                Toast.makeText(this, "Incorrect password", Toast.LENGTH_SHORT).show()
            }
        }
    }
}