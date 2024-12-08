package com.example.supersecurenotes

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class SetPasswordActivity : AppCompatActivity() {

    private lateinit var passwordManager: PasswordManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.set_password)

        passwordManager = PasswordManager(applicationContext)

        val newPasswordEditText = findViewById<EditText>(R.id.newPasswordEditText)
        val passwordStrengthTextView = findViewById<TextView>(R.id.passwordStrengthTextView)
        val confirmPasswordEditText = findViewById<EditText>(R.id.confirmPasswordEditText)
        val setPasswordButton = findViewById<Button>(R.id.setPasswordButton)

        newPasswordEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val password = s.toString()
                val (isComplex, message) = checkPasswordComplexity(password)
                passwordStrengthTextView.text = if (isComplex) "Strong password" else message
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        setPasswordButton.setOnClickListener {
            val newPassword = newPasswordEditText.text.toString()
            val confirmPassword = confirmPasswordEditText.text.toString()

            if (newPassword == confirmPassword) {
                val (isComplex, message) = checkPasswordComplexity(newPassword)
                if (isComplex) {
                    passwordManager.setPassword(newPassword)
                    passwordManager.isPasswordCorrect(newPassword)
                    val app = applicationContext as MyApplication
                    app.updateLastActiveTime()
                    Toast.makeText(this, "Password set successfully", Toast.LENGTH_SHORT).show()

                    val intent = Intent(this, MainActivity::class.java)
                    startActivity(intent)
                    finish()
                } else {
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkPasswordComplexity(password: String): Pair<Boolean, String> {
        val minLength = 8
        val missingRequirements = mutableListOf<String>()

        if (password.length < minLength) {
            missingRequirements.add("at least $minLength characters")
        }
        if (!password.any { it.isUpperCase() }) {
            missingRequirements.add("a capital letter")
        }
        if (!password.any { it.isLowerCase() }) {
            missingRequirements.add("a lowercase letter")
        }
        if (!password.any { it.isDigit() }) {
            missingRequirements.add("a number")
        }
        if (!password.any { !it.isLetterOrDigit() }) {
            missingRequirements.add("a special character")
        }

        return if (missingRequirements.isEmpty()) {
            Pair(true, "The password meets all the requirements.")
        } else {
            val message = "Password must contain " + missingRequirements.joinToString(", ") + "."
            Pair(false, message)
        }
    }
}