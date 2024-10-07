import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

class PasswordManager(context: Context) {
    private val sharedPreferences: SharedPreferences

    companion object {
        private const val PASSWORD_KEY = "user_password"
        private const val DEFAULT_PASSWORD = "0000"
    }

    init {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

        sharedPreferences = EncryptedSharedPreferences.create(
            "secure_notes_prefs",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun getPassword(): String {
        return sharedPreferences.getString(PASSWORD_KEY, DEFAULT_PASSWORD) ?: DEFAULT_PASSWORD
    }

    fun setPassword(newPassword: String) {
        sharedPreferences.edit().putString(PASSWORD_KEY, newPassword).apply()
    }

    fun isPasswordCorrect(enteredPassword: String): Boolean {
        return enteredPassword == getPassword()
    }
}