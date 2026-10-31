package com.example.callrecorder

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.callrecorder.data.AppPreferences
import com.example.callrecorder.network.GeminiProcessor
import com.example.callrecorder.sync.DriveUploadWorker
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private lateinit var prefs: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = AppPreferences(applicationContext)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(prefs = prefs)
                }
            }
        }
    }
}

fun hasAllFilesPermission(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        true
    }
}

@Composable
fun MainScreen(prefs: AppPreferences) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    var apiKey by remember { mutableStateOf(prefs.geminiApiKey) }
    var gsmEnabled by remember { mutableStateOf(prefs.isGsmEnabled) }
    var wappEnabled by remember { mutableStateOf(prefs.isWhatsAppEnabled) }

    // Estados de Google Drive
    var googleEmail by remember { mutableStateOf(prefs.googleAccountEmail) }
    var driveAutoSyncEnabled by remember { mutableStateOf(prefs.isDriveAutoSyncEnabled) }
    var lastSyncFile by remember { mutableStateOf(prefs.lastDriveSyncFile) }

    // Estado de archivos y permisos
    var generatedFiles by remember { mutableStateOf(loadGeneratedFiles()) }
    var allFilesGranted by remember { mutableStateOf(hasAllFilesPermission()) }
    var isProcessingAI by remember { mutableStateOf(false) }
    var processingStatusMessage by remember { mutableStateOf("") }

    // Recargar archivos automáticamente cuando la app vuelve al primer plano
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                generatedFiles = loadGeneratedFiles()
                allFilesGranted = hasAllFilesPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Cliente de Google Sign-In configurado con permiso DRIVE_FILE
    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_FILE))
            .build()
    }
    val googleSignInClient = remember { GoogleSignIn.getClient(context, gso) }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val email = account?.email ?: ""
            prefs.googleAccountEmail = email
            googleEmail = email
            Toast.makeText(context, "Conectado a Google: $email", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Error conectando con Google: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        val message = if (allGranted) "Permisos básicos concedidos" else "Algunos permisos fueron denegados"
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        generatedFiles = loadGeneratedFiles()
        allFilesGranted = hasAllFilesPermission()
    }

    fun processAudio(audioFile: File) {
        if (apiKey.isBlank()) {
            Toast.makeText(context, "Ingresa primero tu Gemini API Key", Toast.LENGTH_LONG).show()
            return
        }
        isProcessingAI = true
        processingStatusMessage = "Procesando ${audioFile.name} con Gemini..."

        coroutineScope.launch {
            val processor = GeminiProcessor(context, apiKey.trim())
            val result = processor.processAudioCall(audioFile)

            withContext(Dispatchers.Main) {
                isProcessingAI = false
                result.onSuccess { summaryFile ->
                    Toast.makeText(context, "¡Resumen listo: ${summaryFile.name}!", Toast.LENGTH_LONG).show()
                    generatedFiles = loadGeneratedFiles()
                }.onFailure { err ->
                    Toast.makeText(context, "Error: ${err.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "CallRecorder NotebookLM",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        // --- TARJETA 1: API KEY Y AJUSTES DE LLAMADA ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Configuración de Gemini API", fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("Gemini API Key") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            prefs.geminiApiKey = apiKey.trim()
                            Toast.makeText(context, "API Key guardada de forma segura", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Guardar Key")
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Captura de llamadas GSM")
                        Switch(
                            checked = gsmEnabled,
                            onCheckedChange = {
                                gsmEnabled = it
                                prefs.isGsmEnabled = it
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Captura de llamadas WhatsApp")
                        Switch(
                            checked = wappEnabled,
                            onCheckedChange = {
                                wappEnabled = it
                                prefs.isWhatsAppEnabled = it
                            }
                        )
                    }
                }
            }
        }

        // --- TARJETA 2: SINCRONIZACIÓN AUTOMÁTICA CON GOOGLE DRIVE ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CloudSync,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Google Drive (NotebookLM)",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Sube automáticamente resúmenes y audios a la carpeta 'NotebookLM_Llamadas' en tu Drive.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (googleEmail.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF2E7D32),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Conectado como: $googleEmail",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Subida automática a Drive", fontSize = 14.sp)
                            Switch(
                                checked = driveAutoSyncEnabled,
                                onCheckedChange = {
                                    driveAutoSyncEnabled = it
                                    prefs.isDriveAutoSyncEnabled = it
                                }
                            )
                        }

                        if (lastSyncFile.isNotBlank()) {
                            Text(
                                text = "Último subido: $lastSyncFile",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    val files = loadGeneratedFiles()
                                    var count = 0
                                    for (f in files) {
                                        val mime = if (f.extension == "txt") "text/plain" else "audio/mp4"
                                        DriveUploadWorker.enqueueUpload(context, f, mime)
                                        count++
                                    }
                                    Toast.makeText(context, "Sincronizando $count archivo(s) con Drive...", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(imageVector = Icons.Default.CloudUpload, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Sincronizar Todo", fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    googleSignInClient.signOut().addOnCompleteListener {
                                        prefs.googleAccountEmail = ""
                                        googleEmail = ""
                                        Toast.makeText(context, "Cuenta desconectada", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            ) {
                                Text("Desconectar", fontSize = 12.sp)
                            }
                        }
                    } else {
                        Button(
                            onClick = {
                                googleSignInLauncher.launch(googleSignInClient.signInIntent)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(imageVector = Icons.Default.CloudSync, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Conectar cuenta de Google Drive")
                        }
                    }
                }
            }
        }

        // --- TARJETA 3: PROCESADOR IA MANUAL ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Procesador IA Integrado",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Cascada: Gemini 3.8 Flash ➔ 1.5 Flash ➔ 2.0 Flash Exp. Reemplaza el script de Termux.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (isProcessingAI) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = processingStatusMessage,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    } else {
                        Button(
                            onClick = {
                                val processor = GeminiProcessor(context, apiKey.trim())
                                val latestAudio = processor.getLatestAudioFile()
                                if (latestAudio != null) {
                                    processAudio(latestAudio)
                                } else {
                                    Toast.makeText(context, "No se detectaron audios en Documents/Llamadas_NotebookLM", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Procesar Último Audio Ahora")
                        }
                    }
                }
            }
        }

        // --- TARJETA 4: DIAGNÓSTICO Y PERMISOS ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Diagnóstico y Permisos", fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            val perms = mutableListOf(
                                Manifest.permission.RECORD_AUDIO,
                                Manifest.permission.READ_PHONE_STATE
                            )
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                perms.add(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            permissionsLauncher.launch(perms.toTypedArray())
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("1. Conceder Permisos Básicos")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("2. Activar Servicio de Accesibilidad")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = { openXiaomiSettings(context) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("3. Ajustes de HyperOS / MIUI")
                    }

                    // Permiso de acceso a todos los archivos (para leer archivos creados por Termux)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Spacer(modifier = Modifier.height(8.dp))
                        if (allFilesGranted) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF2E7D32),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Acceso a todos los archivos concedido",
                                    fontSize = 13.sp,
                                    color = Color(0xFF2E7D32),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        } else {
                            Button(
                                onClick = {
                                    try {
                                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                            data = Uri.parse("package:${context.packageName}")
                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                        context.startActivity(intent)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                            ) {
                                Icon(imageVector = Icons.Default.FolderOpen, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("4. Permitir Acceso a Archivos de Termux")
                            }
                        }
                    }
                }
            }
        }

        // --- SECCIÓN: ARCHIVOS GENERADOS ---
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Grabaciones y Resúmenes",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${generatedFiles.size} archivo(s) encontrados",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                TextButton(onClick = { generatedFiles = loadGeneratedFiles() }) {
                    Text("Refrescar")
                }
            }
        }

        if (generatedFiles.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "No se detectaron archivos en Documents/Llamadas_NotebookLM",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        if (!allFilesGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "💡 Pulsa arriba el botón '4. Permitir Acceso a Archivos de Termux' para que la app pueda leer los archivos generados por otras apps.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        } else {
            items(generatedFiles) { file ->
                val isAudio = file.extension.lowercase(Locale.ROOT) in GeminiProcessor.SUPPORTED_EXTENSIONS
                val hasSummary = if (isAudio) {
                    File(file.parentFile, "${file.nameWithoutExtension}_resumen.txt").exists()
                } else false

                FileItemRow(
                    file = file,
                    isAudio = isAudio,
                    hasSummary = hasSummary,
                    isProcessing = isProcessingAI,
                    isDriveConnected = googleEmail.isNotBlank(),
                    onOpen = { openFile(context, file) },
                    onShare = { shareFile(context, file) },
                    onProcessAI = { processAudio(file) },
                    onUploadDrive = {
                        val mime = if (file.extension == "txt") "text/plain" else "audio/mp4"
                        DriveUploadWorker.enqueueUpload(context, file, mime)
                        Toast.makeText(context, "Subiendo ${file.name} a Google Drive...", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }
}

@Composable
fun FileItemRow(
    file: File,
    isAudio: Boolean,
    hasSummary: Boolean,
    isProcessing: Boolean,
    isDriveConnected: Boolean,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onProcessAI: () -> Unit,
    onUploadDrive: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    maxLines = 1
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${(file.length() / 1024)} KB | ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(file.lastModified()))}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                    if (isAudio && hasSummary) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Resumen listo",
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "Resumido",
                            fontSize = 11.sp,
                            color = Color(0xFF2E7D32),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isAudio && !hasSummary) {
                    IconButton(
                        onClick = onProcessAI,
                        enabled = !isProcessing
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Procesar con IA",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (isDriveConnected) {
                    IconButton(onClick = onUploadDrive) {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = "Subir a Drive",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                IconButton(onClick = onOpen) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Abrir")
                }
                IconButton(onClick = onShare) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = "Compartir")
                }
            }
        }
    }
}

private fun loadGeneratedFiles(): List<File> {
    val dir1 = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
        "Llamadas_NotebookLM"
    )
    val dir2 = File("/storage/emulated/0/Documents/Llamadas_NotebookLM")
    val dir3 = File("/sdcard/Documents/Llamadas_NotebookLM")

    val candidates = listOf(dir1, dir2, dir3)
    val targetDir = candidates.firstOrNull { it.exists() && (it.listFiles()?.isNotEmpty() == true) }
        ?: candidates.firstOrNull { it.exists() }
        ?: dir1

    if (!targetDir.exists()) {
        targetDir.mkdirs()
    }

    val files = targetDir.listFiles() ?: return emptyList()
    return files
        .filter { file ->
            file.isFile && file.extension.lowercase(Locale.ROOT) in (GeminiProcessor.SUPPORTED_EXTENSIONS + "txt")
        }
        .sortedByDescending { it.lastModified() }
}

private fun openFile(context: Context, file: File) {
    try {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val mimeType = if (file.extension == "txt") "text/plain" else "audio/*"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "No hay aplicación disponible para abrir este archivo", Toast.LENGTH_SHORT).show()
    }
}

private fun shareFile(context: Context, file: File) {
    try {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val mimeType = if (file.extension == "txt") "text/plain" else "audio/*"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Compartir con"))
    } catch (e: Exception) {
        Toast.makeText(context, "Error al compartir archivo: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun openXiaomiSettings(context: Context) {
    try {
        val intent = Intent().apply {
            component = ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            )
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        val fallbackIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
        context.startActivity(fallbackIntent)
    }
}
