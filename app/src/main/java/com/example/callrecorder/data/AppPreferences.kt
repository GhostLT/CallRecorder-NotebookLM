package com.example.callrecorder.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

class AppPreferences(context: Context) {

    private val sharedPreferences: SharedPreferences

    companion object {
        private const val PREFS_FILE = "secure_call_recorder_prefs"
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
        private const val KEY_RECORD_GSM = "record_gsm"
        private const val KEY_RECORD_WHATSAPP = "record_whatsapp"
    }

    init {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        sharedPreferences = EncryptedSharedPreferences.create(
            PREFS_FILE,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    var geminiApiKey: String
        get() = sharedPreferences.getString(KEY_GEMINI_API_KEY, "") ?: ""
        set(value) = sharedPreferences.edit().putString(KEY_GEMINI_API_KEY, value).apply()

    var isGsmEnabled: Boolean
        get() = sharedPreferences.getBoolean(KEY_RECORD_GSM, true)
        set(value) = sharedPreferences.edit().putBoolean(KEY_RECORD_GSM, value).apply()

    var isWhatsAppEnabled: Boolean
        get() = sharedPreferences.getBoolean(KEY_RECORD_WHATSAPP, true)
        set(value) = sharedPreferences.edit().putBoolean(KEY_RECORD_WHATSAPP, value).apply()
}
