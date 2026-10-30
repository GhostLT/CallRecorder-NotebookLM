package com.example.callrecorder.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.callrecorder.audio.AudioManagerRecorder
import com.example.callrecorder.data.AppPreferences
import com.example.callrecorder.network.GeminiProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class CallRecordService : Service() {

    companion object {
        private const val TAG = "CallRecordService"
        private const val CHANNEL_ID = "channel_call_recorder_fg"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_START_RECORDING = "ACTION_START_RECORDING"
        const val ACTION_STOP_RECORDING = "ACTION_STOP_RECORDING"
        const val EXTRA_CALL_TYPE = "EXTRA_CALL_TYPE"

        fun startCallRecording(context: Context, callType: String) {
            val intent = Intent(context, CallRecordService::class.java).apply {
                action = ACTION_START_RECORDING
                putExtra(EXTRA_CALL_TYPE, callType)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopCallRecording(context: Context) {
            val intent = Intent(context, CallRecordService::class.java).apply {
                action = ACTION_STOP_RECORDING
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var audioRecorder: AudioManagerRecorder
    private lateinit var appPreferences: AppPreferences

    override fun onCreate() {
        super.onCreate()
        audioRecorder = AudioManagerRecorder(applicationContext)
        appPreferences = AppPreferences(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_RECORDING -> {
                val callTypeStr = intent.getStringExtra(EXTRA_CALL_TYPE) ?: "GSM"
                val callType = if (callTypeStr == "WAPP") {
                    AudioManagerRecorder.CallType.WAPP
                } else {
                    AudioManagerRecorder.CallType.GSM
                }
                startForeground(NOTIFICATION_ID, buildForegroundNotification("Grabando llamada activa ($callTypeStr)..."))
                audioRecorder.startRecording(
                    callType = callType,
                    onError = { e ->
                        Log.e(TAG, "Error iniciando grabación: ${e.message}")
                        stopSelf()
                    }
                )
            }
            ACTION_STOP_RECORDING -> {
                audioRecorder.stopRecording(
                    onComplete = { audioFile ->
                        Log.i(TAG, "Grabación finalizada: ${audioFile.absolutePath}")
                        processWithGemini(audioFile)
                    },
                    onError = { e ->
                        Log.e(TAG, "Error deteniendo grabación: ${e.message}")
                        stopSelf()
                    }
                )
            }
        }
        return START_NOT_STICKY
    }

    private fun processWithGemini(audioFile: java.io.File) {
        val apiKey = appPreferences.geminiApiKey
        if (apiKey.isBlank()) {
            Log.w(TAG, "API Key de Gemini no configurada.")
            stopSelf()
            return
        }

        serviceScope.launch {
            val processor = GeminiProcessor(applicationContext, apiKey)
            processor.processAudioCall(audioFile)
            stopSelf()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Servicio de Grabación Activa",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mantiene el servicio en primer plano para asegurar la captura en Xiaomi/HyperOS"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(content: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CallRecorder NotebookLM")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        audioRecorder.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
