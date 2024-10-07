import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

class SecureNotesManager(context: Context) {

    // Usa SharedPreferences come tipo per EncryptedSharedPreferences
    private val sharedPreferences: SharedPreferences = createEncryptedSharedPreferences(context)

    private fun createEncryptedSharedPreferences(context: Context): SharedPreferences {
        // Crea o recupera la chiave principale per la crittografia
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

        // Crea EncryptedSharedPreferences
        return EncryptedSharedPreferences.create(
            "secure_shared_prefs", // Nome del file SharedPreferences
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // Esempio di metodo per salvare una nota
    fun saveNote(key: String, note: String) {
        sharedPreferences.edit().putString(key, note).apply()
    }

    // Esempio di metodo per recuperare una nota
    fun getNote(key: String): String? {
        return sharedPreferences.getString(key, null)
    }
}