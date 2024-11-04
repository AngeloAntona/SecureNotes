package com.example.supersecurenotes

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

class MainActivity : AppCompatActivity() {

    private lateinit var passwordManager: PasswordManager

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        passwordManager = PasswordManager(applicationContext)
        val passwordEditText = findViewById<EditText>(R.id.passwordEditText)
        val loginButton = findViewById<Button>(R.id.loginButton)

        if (!passwordManager.isPasswordSet()) {
            val intent = Intent(this, SetPasswordActivity::class.java)
            startActivity(intent)
            finish()
            return
        }

        loginButton.setOnClickListener {
            val enteredPassword = passwordEditText.text.toString()

            if (passwordManager.isLockedOut()) {
                Toast.makeText(this, "Account blocked. Try again later.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            if (passwordManager.isPasswordCorrect(enteredPassword)) {
                val app = applicationContext as MyApplication
                app.updateLastActiveTime()
                val intent = Intent(this, NotesActivity::class.java)
                startActivity(intent)
                finish()
            } else {
                Toast.makeText(this, "Wrong password", Toast.LENGTH_SHORT).show()
            }
        }
    }
}