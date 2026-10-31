package com.example.callrecorder.network

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.util.Base64
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class GeminiProcessor(
    private val context: Context,
    private val apiKey: String
) {

    companion object {
        private const val TAG = "GeminiProcessor"
        private const val API_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent"
        private const val SUBFOLDER_NAME = "Llamadas_NotebookLM"
        private const val CHANNEL_ID = "channel_call_summary"
        private const val NOTIFICATION_ID = 2001
    }

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    init {
        createNotificationChannel()
    }

    suspend fun processAudioCall(audioFile: File): Result<File> = withContext(Dispatchers.IO) {
        try {
            if (!audioFile.exists() || audioFile.length() == 0L) {
                return@withContext Result.failure(IOException("El archivo de audio no existe o está vacío."))
            }

            val mimeType = when (audioFile.extension.lowercase(Locale.ROOT)) {
                "mp3" -> "audio/mp3"
                "m4a" -> "audio/mp4"
                "aac" -> "audio/aac"
                "wav" -> "audio/wav"
                else -> "audio/mp4"
            }
            val base64Audio = encodeFileToBase64(audioFile)

            val currentDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val promptText = """
                Analiza detalladamente esta llamada telefónica en español. Genera un documento claro y estructurado con el siguiente formato:
                # RESUMEN EJECUTIVO DE LLAMADA
                - Origen: ${audioFile.name}
                - Fecha: $currentDate
                ## 1. Puntos Principales Tratados
                ## 2. Acuerdos y Compromisos
                ## 3. Tareas Pendientes (Action Items)
                ## 4. Transcripción Completa
            """.trimIndent()

            val requestJson = JSONObject().apply {
                val partsArray = JSONArray().apply {
                    put(JSONObject().put("text", promptText))
                    put(JSONObject().put("inlineData", JSONObject().apply {
                        put("mimeType", mimeType)
                        put("data", base64Audio)
                    }))
                }

                val contentsArray = JSONArray().apply {
                    put(JSONObject().put("parts", partsArray))
                }

                put("contents", contentsArray)
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = requestJson.toString().toRequestBody(mediaType)

            val httpRequest = Request.Builder()
                .url(API_URL)
                .addHeader("x-goog-api-key", apiKey)
                .post(requestBody)
                .build()

            Log.d(TAG, "Enviando audio (${audioFile.length() / 1024} KB) a Gemini API...")

            val response = httpClient.newCall(httpRequest).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful || responseBody == null) {
                val errorMsg = "Fallo en la API de Gemini (${response.code}): $responseBody"
                Log.e(TAG, errorMsg)
                return@withContext Result.failure(IOException(errorMsg))
            }

            val extractedSummary = parseGeminiResponse(responseBody)
            val summaryFile = saveSummaryToFile(extractedSummary)
            showSuccessNotification(summaryFile.name)

            Result.success(summaryFile)

        } catch (e: Exception) {
            Log.e(TAG, "Error procesando el audio con Gemini: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun encodeFileToBase64(file: File): String {
        val fileInputStream = FileInputStream(file)
        val bytes = fileInputStream.use { it.readBytes() }
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun parseGeminiResponse(jsonString: String): String {
        val root = JSONObject(jsonString)
        val candidates = root.optJSONArray("candidates")
        if (candidates != null && candidates.length() > 0) {
            val firstCandidate = candidates.getJSONObject(0)
            val content = firstCandidate.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            if (parts != null && parts.length() > 0) {
                val textBuilder = StringBuilder()
                for (i in 0 until parts.length()) {
                    val part = parts.getJSONObject(i)
                    if (part.has("text")) {
                        textBuilder.append(part.getString("text"))
                    }
                }
                return textBuilder.toString()
            }
        }
        throw IOException("La respuesta de Gemini no contiene candidatos con texto válido.")
    }

    private fun saveSummaryToFile(content: String): File {
        val documentsFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val targetDir = File(documentsFolder, SUBFOLDER_NAME)
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val resultFile = File(targetDir, "LLAMADA_${timestamp}_resumen.txt")

        FileOutputStream(resultFile).use { output ->
            output.write(content.toByteArray(Charsets.UTF_8))
        }

        Log.i(TAG, "Resumen guardado exitosamente en: ${resultFile.absolutePath}")
        return resultFile
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Procesamiento de Llamadas"
            val descriptionText = "Notificaciones sobre resúmenes generados por IA"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showSuccessNotification(fileName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionCheck = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            )
            if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "No se tiene permiso POST_NOTIFICATIONS concedido.")
                return
            }
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Resumen de Llamada Generado")
            .setContentText("Archivo listo: $fileName")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("El análisis y transcripción de la llamada se ha completado con éxito y se encuentra guardado en Documents/$SUBFOLDER_NAME/$fileName")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            Log.e(TAG, "Error de seguridad emitiendo la notificación: ${e.message}")
        }
    }
}
