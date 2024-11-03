package com.example.supersecurenotes

import android.app.Application

class MyApplication : Application() {

    var sessionKey: ByteArray? = null
    var lastActiveTime: Long = System.currentTimeMillis()

    companion object {
        private const val SESSION_TIMEOUT_DURATION = 5 * 60 * 1000 // 5 minuti
    }

    // Verifica se la sessione è scaduta
    fun isSessionExpired(): Boolean {
        return (System.currentTimeMillis() - lastActiveTime) > SESSION_TIMEOUT_DURATION || sessionKey == null
    }

    // Aggiorna l'orario di ultima attività
    fun updateLastActiveTime() {
        lastActiveTime = System.currentTimeMillis()
    }

    // Pulisce la sessione alla scadenza
    fun clearSession() {
        sessionKey = null
        lastActiveTime = 0L
    }
}