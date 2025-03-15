package com.example.supersecurenotes

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity

open class ImmersiveActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Non impostare la status bar su trasparente
        //window.statusBarColor = android.graphics.Color.TRANSPARENT
    }
}