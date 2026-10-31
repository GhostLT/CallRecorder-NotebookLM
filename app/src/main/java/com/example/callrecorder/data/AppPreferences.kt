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

        // Preferencias de sincronización con Google Drive
        private const val KEY_GOOGLE_ACCOUNT_EMAIL = "google_account_email"
        private const val KEY_DRIVE_FOLDER_ID = "drive_folder_id"
        private const val KEY_AUTO_SYNC_DRIVE = "auto_sync_drive"
        private const val KEY_LAST_SYNC_FILE = "last_sync_file"
        private const val KEY_LAST_SYNC_TIME = "last_sync_time"
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

    var googleAccountEmail: String
        get() = sharedPreferences.getString(KEY_GOOGLE_ACCOUNT_EMAIL, "") ?: ""
        set(value) = sharedPreferences.edit().putString(KEY_GOOGLE_ACCOUNT_EMAIL, value).apply()

    var driveFolderId: String
        get() = sharedPreferences.getString(KEY_DRIVE_FOLDER_ID, "") ?: ""
        set(value) = sharedPreferences.edit().putString(KEY_DRIVE_FOLDER_ID, value).apply()

    var isDriveAutoSyncEnabled: Boolean
        get() = sharedPreferences.getBoolean(KEY_AUTO_SYNC_DRIVE, true)
        set(value) = sharedPreferences.edit().putBoolean(KEY_AUTO_SYNC_DRIVE, value).apply()

    var lastDriveSyncFile: String
        get() = sharedPreferences.getString(KEY_LAST_SYNC_FILE, "") ?: ""
        set(value) = sharedPreferences.edit().putString(KEY_LAST_SYNC_FILE, value).apply()

    var lastDriveSyncTime: Long
        get() = sharedPreferences.getLong(KEY_LAST_SYNC_TIME, 0L)
        set(value) = sharedPreferences.edit().putLong(KEY_LAST_SYNC_TIME, value).apply()
}
