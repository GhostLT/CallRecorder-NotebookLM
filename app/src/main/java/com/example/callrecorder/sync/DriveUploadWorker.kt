package com.example.callrecorder.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import java.io.File
import java.util.concurrent.TimeUnit

class DriveUploadWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "DriveUploadWorker"
        private const val CHANNEL_ID = "channel_drive_sync"
        private const val NOTIFICATION_ID = 3001

        const val KEY_FILE_PATH = "KEY_FILE_PATH"
        const val KEY_MIME_TYPE = "KEY_MIME_TYPE"

        fun enqueueUpload(context: Context, file: File, mimeType: String) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val inputData = workDataOf(
                KEY_FILE_PATH to file.absolutePath,
                KEY_MIME_TYPE to mimeType
            )

            val uploadWork = OneTimeWorkRequestBuilder<DriveUploadWorker>()
                .setConstraints(constraints)
                .setInputData(inputData)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "drive_upload_${file.name}",
                ExistingWorkPolicy.REPLACE,
                uploadWork
            )
            Log.d(TAG, "Encolada subida a Google Drive para: ${file.name}")
        }
    }

    override suspend fun doWork(): Result {
        val filePath = inputData.getString(KEY_FILE_PATH) ?: return Result.failure()
        val mimeType = inputData.getString(KEY_MIME_TYPE) ?: "text/plain"

        val file = File(filePath)
        if (!file.exists() || file.length() == 0L) {
            Log.w(TAG, "El archivo a subir no existe: $filePath")
            return Result.failure()
        }

        val syncManager = GoogleDriveSyncManager(appContext)
        val uploadResult = syncManager.uploadFile(file, mimeType)

        return if (uploadResult.isSuccess) {
            Log.i(TAG, "DriveUploadWorker exitoso para: ${file.name}")
            showSyncNotification(file.name)
            Result.success()
        } else {
            val error = uploadResult.exceptionOrNull()
            Log.e(TAG, "DriveUploadWorker falló para ${file.name}: ${error?.message}")
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    private fun showSyncNotification(fileName: String) {
        createNotificationChannel()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionCheck = ContextCompat.checkSelfPermission(
                appContext,
                android.Manifest.permission.POST_NOTIFICATIONS
            )
            if (permissionCheck != PackageManager.PERMISSION_GRANTED) return
        }

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle("Sincronizado con Google Drive")
            .setContentText("Listo en NotebookLM: $fileName")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Se ha subido automáticamente a la carpeta 'NotebookLM_Llamadas' de Google Drive: $fileName")
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(appContext).notify(NOTIFICATION_ID, notification)
        } catch (ignored: SecurityException) {}
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Sincronización con Google Drive",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notificaciones sobre archivos sincronizados con Google Drive para NotebookLM"
            }
            val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
