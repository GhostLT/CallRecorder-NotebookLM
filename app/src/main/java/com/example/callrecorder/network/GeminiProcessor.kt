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
        private const val BASE_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/"
        private const val SUBFOLDER_NAME = "Llamadas_NotebookLM"
        private const val CHANNEL_ID = "channel_call_summary"
        private const val NOTIFICATION_ID = 2001

        val SUPPORTED_EXTENSIONS = listOf("mp3", "m4a", "wav", "aac", "ogg", "amr")

        // Lista de modelos ordenados por prioridad idéntica al script de Termux
        val CANDIDATE_MODELS = listOf(
            "gemini-3.8-flash",
            "gemini-1.5-flash",
            "gemini-2.0-flash-exp"
        )
    }

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS) // 180 segundos idéntico a Termux
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    init {
        createNotificationChannel()
    }

    /**
     * Procesa el archivo de audio local probando en cascada los modelos disponibles.
     * Guarda el archivo como {nombre_base}_resumen.txt idéntico a la lógica de Termux.
     */
    suspend fun processAudioCall(audioFile: File): Result<File> = withContext(Dispatchers.IO) {
        try {
            if (!audioFile.exists() || audioFile.length() == 0L) {
                return@withContext Result.failure(IOException("El archivo de audio no existe o está vacío."))
            }

            val mimeType = when (audioFile.extension.lowercase(Locale.ROOT)) {
                "mp3" -> "audio/mp3"
                "m4a" -> "audio/m4a"
                "wav" -> "audio/wav"
                "aac" -> "audio/aac"
                "ogg" -> "audio/ogg"
                "amr" -> "audio/amr"
                else -> "audio/m4a"
            }
            val base64Audio = encodeFileToBase64(audioFile)

            val currentDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val promptText = """
                Analiza detalladamente esta llamada telefónica en español. Genera un documento claro y estructurado con el siguiente formato:

                # RESUMEN EJECUTIVO DE LLAMADA
                - Archivo origen: ${audioFile.name}
                - Fecha de procesamiento: $currentDate

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

            var extractedSummary: String? = null
            var successfulModel: String? = null
            var lastErrorMessage: String? = null

            // Cascada de modelos (Prueba 3.8 -> 1.5 -> 2.0-exp)
            for (model in CANDIDATE_MODELS) {
                val url = "$BASE_API_URL$model:generateContent"
                Log.d(TAG, "[*] Intentando con modelo: $model...")

                val httpRequest = Request.Builder()
                    .url(url)
                    .addHeader("x-goog-api-key", apiKey)
                    .post(requestBody)
                    .build()

                try {
                    val response = httpClient.newCall(httpRequest).execute()
                    val responseBody = response.body?.string()

                    if (response.isSuccessful && responseBody != null) {
                        extractedSummary = parseGeminiResponse(responseBody)
                        successfulModel = model
                        Log.i(TAG, "[+] Éxito con modelo: $model")
                        break
                    } else if (response.code == 404) {
                        Log.w(TAG, "[-] Modelo $model no disponible (404), intentando con el siguiente...")
                        continue
                    } else {
                        val err = "Error ${response.code} con modelo $model: $responseBody"
                        Log.e(TAG, err)
                        lastErrorMessage = err
                    }
                } catch (netEx: Exception) {
                    Log.e(TAG, "[!] Error de red con modelo $model: ${netEx.message}")
                    lastErrorMessage = netEx.message
                }
            }

            if (extractedSummary == null) {
                return@withContext Result.failure(
                    IOException("No se pudo procesar con los modelos disponibles. Último error: $lastErrorMessage")
                )
            }

            // Guardar con el mismo nombre base que el audio: {nombre_base}_resumen.txt
            val summaryFile = saveSummaryToFile(audioFile, extractedSummary)
            showSuccessNotification(summaryFile.name, successfulModel ?: "Gemini")

            // Sincronización automática a Google Drive en segundo plano
            val prefs = com.example.callrecorder.data.AppPreferences(context)
            if (prefs.isDriveAutoSyncEnabled && prefs.googleAccountEmail.isNotBlank()) {
                com.example.callrecorder.sync.DriveUploadWorker.enqueueUpload(context, summaryFile, "text/plain")
                com.example.callrecorder.sync.DriveUploadWorker.enqueueUpload(context, audioFile, mimeType)
            }

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

    /**
     * Guarda el resumen con el formato exacto de Termux: {nombre_base}_resumen.txt
     */
    private fun saveSummaryToFile(audioFile: File, content: String): File {
        val targetDir = getWorkingDir()
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }

        val baseName = audioFile.nameWithoutExtension
        val resultFile = File(targetDir, "${baseName}_resumen.txt")

        FileOutputStream(resultFile).use { output ->
            output.write(content.toByteArray(Charsets.UTF_8))
        }

        Log.i(TAG, "Resumen guardado exitosamente en: ${resultFile.absolutePath}")
        return resultFile
    }

    private fun getWorkingDir(): File {
        val documentsFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        return File(documentsFolder, SUBFOLDER_NAME)
    }

    /**
     * Encuentra el último archivo de audio grabado en la carpeta (equivalente a obtener_ultimo_audio de Termux).
     */
    fun getLatestAudioFile(): File? {
        val dir = getWorkingDir()
        if (!dir.exists()) return null
        return dir.listFiles()
            ?.filter { file ->
                val ext = file.extension.lowercase(Locale.ROOT)
                ext in SUPPORTED_EXTENSIONS
            }
            ?.maxByOrNull { it.lastModified() }
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

    private fun showSuccessNotification(fileName: String, modelUsed: String) {
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
            .setContentTitle("Resumen de Llamada Generado ($modelUsed)")
            .setContentText("Archivo listo: $fileName")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Transcripción y resumen completados exitosamente con $modelUsed y guardado en Documents/$SUBFOLDER_NAME/$fileName")
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
