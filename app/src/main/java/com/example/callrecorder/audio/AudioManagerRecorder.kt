package com.example.callrecorder.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AudioManagerRecorder(private val context: Context) {

    companion object {
        private const val TAG = "AudioManagerRecorder"
        private const val SUBFOLDER_NAME = "Llamadas_NotebookLM"
    }

    enum class CallType {
        GSM,
        WAPP
    }

    private var mediaRecorder: MediaRecorder? = null
    private var currentOutputFile: File? = null
    private var isRecording = false

    @Synchronized
    fun startRecording(
        callType: CallType,
        onError: (Exception) -> Unit = {}
    ) {
        if (isRecording) {
            Log.w(TAG, "Ya existe una grabación en curso.")
            return
        }

        try {
            val destinationDir = getOutputDirectory()
            if (!destinationDir.exists() && !destinationDir.mkdirs()) {
                throw IOException("No se pudo crear el directorio de destino: ${destinationDir.absolutePath}")
            }

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "LLAMADA_${callType.name}_$timestamp.m4a"
            currentOutputFile = File(destinationDir, fileName)

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(16000)
                setAudioChannels(1)
                setAudioEncodingBitRate(32000)
                setOutputFile(currentOutputFile?.absolutePath)

                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "Error en MediaRecorder: what=$what, extra=$extra")
                    stopRecording(onError = onError)
                }

                prepare()
                start()
            }

            isRecording = true
            Log.d(TAG, "Grabación iniciada: ${currentOutputFile?.absolutePath}")

        } catch (e: Exception) {
            Log.e(TAG, "Fallo al iniciar grabación: ${e.message}", e)
            cleanup()
            onError(e)
        }
    }

    @Synchronized
    fun stopRecording(
        onComplete: (File) -> Unit = {},
        onError: (Exception) -> Unit = {}
    ) {
        if (!isRecording) {
            Log.w(TAG, "stopRecording llamado sin grabación activa.")
            return
        }

        try {
            mediaRecorder?.apply {
                stop()
                reset()
                release()
            }
            mediaRecorder = null
            isRecording = false

            val resultFile = currentOutputFile
            if (resultFile != null && resultFile.exists() && resultFile.length() > 0) {
                Log.d(TAG, "Grabación finalizada: ${resultFile.absolutePath}")
                onComplete(resultFile)
            } else {
                resultFile?.delete()
                onError(IOException("El archivo resultante no contiene datos válidos."))
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error finalizando grabación: ${e.message}", e)
            currentOutputFile?.delete()
            onError(e)
        } finally {
            cleanup()
        }
    }

    @Synchronized
    fun release() {
        cleanup()
    }

    private fun cleanup() {
        try {
            mediaRecorder?.release()
        } catch (ignored: Exception) {}
        mediaRecorder = null
        isRecording = false
    }

    private fun getOutputDirectory(): File {
        val documentsFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        return File(documentsFolder, SUBFOLDER_NAME)
    }

    fun isCurrentlyRecording(): Boolean = isRecording
}
