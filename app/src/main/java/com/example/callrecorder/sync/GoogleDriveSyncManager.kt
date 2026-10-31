package com.example.callrecorder.sync

import android.content.Context
import android.util.Log
import com.example.callrecorder.data.AppPreferences
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class GoogleDriveSyncManager(private val context: Context) {

    companion object {
        private const val TAG = "GoogleDriveSync"
        const val FOLDER_NAME = "NotebookLM_Llamadas"
    }

    private val prefs = AppPreferences(context)

    /**
     * Obtiene el cliente de Google Drive autenticado con la cuenta actualmente conectada.
     */
    private fun getDriveService(): Drive? {
        val googleAccount = GoogleSignIn.getLastSignedInAccount(context) ?: run {
            Log.w(TAG, "No hay cuenta de Google conectada.")
            return null
        }

        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            listOf(DriveScopes.DRIVE_FILE)
        ).apply {
            selectedAccount = googleAccount.account
        }

        return Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName("CallRecorder NotebookLM").build()
    }

    /**
     * Localiza o crea la carpeta dedicada "NotebookLM_Llamadas" en la raíz de Google Drive.
     */
    @Throws(IOException::class)
    private fun getOrCreateNotebookLMFolder(driveService: Drive): String {
        val cachedFolderId = prefs.driveFolderId
        if (cachedFolderId.isNotBlank()) {
            try {
                val folder = driveService.files().get(cachedFolderId)
                    .setFields("id, trashed")
                    .execute()
                if (folder != null && folder.trashed != true) {
                    return folder.id
                }
            } catch (ignored: Exception) {
                // Si la carpeta fue borrada en la web, se vuelve a buscar o crear
            }
        }

        // Buscar si ya existe por nombre
        val query = "mimeType = 'application/vnd.google-apps.folder' and name = '$FOLDER_NAME' and trashed = false"
        val searchResult = driveService.files().list()
            .setQ(query)
            .setSpaces("drive")
            .setFields("files(id, name)")
            .execute()

        val existingFolder = searchResult.files?.firstOrNull()
        if (existingFolder != null) {
            prefs.driveFolderId = existingFolder.id
            Log.d(TAG, "Carpeta existente encontrada: ${existingFolder.id}")
            return existingFolder.id
        }

        // Crear nueva carpeta en la raíz
        val folderMetadata = com.google.api.services.drive.model.File().apply {
            name = FOLDER_NAME
            mimeType = "application/vnd.google-apps.folder"
        }

        val newFolder = driveService.files().create(folderMetadata)
            .setFields("id")
            .execute()

        prefs.driveFolderId = newFolder.id
        Log.i(TAG, "Nueva carpeta creada en Google Drive con ID: ${newFolder.id}")
        return newFolder.id
    }

    /**
     * Sube un archivo a la carpeta "NotebookLM_Llamadas" en Google Drive con soporte multipart.
     * Si ya existe un archivo con el mismo nombre en la carpeta, lo actualiza.
     *
     * @param file Archivo local a subir (.txt o .m4a).
     * @param mimeType MIME Type del archivo.
     * @return El ID del archivo creado o actualizado en Google Drive.
     */
    suspend fun uploadFile(file: File, mimeType: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!file.exists() || file.length() == 0L) {
                return@withContext Result.failure(IOException("El archivo local no existe o está vacío: ${file.name}"))
            }

            val driveService = getDriveService()
                ?: return@withContext Result.failure(IOException("Inicia sesión con tu cuenta de Google primero."))

            val folderId = getOrCreateNotebookLMFolder(driveService)
            val fileContent = FileContent(mimeType, file)

            // Buscar si ya existe en la carpeta para actualizarlo en vez de duplicarlo
            val searchQuery = "'$folderId' in parents and name = '${file.name}' and trashed = false"
            val existingList = driveService.files().list()
                .setQ(searchQuery)
                .setSpaces("drive")
                .setFields("files(id, name)")
                .execute()

            val existingFile = existingList.files?.firstOrNull()

            val uploadedFile = if (existingFile != null) {
                Log.d(TAG, "Actualizando archivo existente en Drive: ${file.name} (ID: ${existingFile.id})")
                driveService.files().update(existingFile.id, null, fileContent)
                    .setFields("id, name, webViewLink")
                    .execute()
            } else {
                Log.d(TAG, "Subiendo nuevo archivo a Drive: ${file.name}")
                val fileMetadata = com.google.api.services.drive.model.File().apply {
                    name = file.name
                    parents = listOf(folderId)
                }
                driveService.files().create(fileMetadata, fileContent)
                    .setFields("id, name, webViewLink")
                    .execute()
            }

            prefs.lastDriveSyncFile = file.name
            prefs.lastDriveSyncTime = System.currentTimeMillis()

            Log.i(TAG, "Subida completada con éxito: ${file.name} -> Drive ID: ${uploadedFile.id}")
            Result.success(uploadedFile.id)

        } catch (e: Exception) {
            Log.e(TAG, "Error subiendo archivo ${file.name} a Google Drive: ${e.message}", e)
            Result.failure(e)
        }
    }

    fun isUserConnected(): Boolean {
        return GoogleSignIn.getLastSignedInAccount(context) != null && prefs.googleAccountEmail.isNotBlank()
    }
}
