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
import com.example.callrecorder.data.AppPreferences
import com.example.callrecorder.network.GeminiProcessor
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

@Composable
fun MainScreen(prefs: AppPreferences) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var apiKey by remember { mutableStateOf(prefs.geminiApiKey) }
    var gsmEnabled by remember { mutableStateOf(prefs.isGsmEnabled) }
    var wappEnabled by remember { mutableStateOf(prefs.isWhatsAppEnabled) }

    var generatedFiles by remember { mutableStateOf(loadGeneratedFiles()) }
    var isProcessingAI by remember { mutableStateOf(false) }
    var processingStatusMessage by remember { mutableStateOf("") }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        val message = if (allGranted) "Todos los permisos concedidos" else "Algunos permisos fueron denegados"
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
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

        // --- TARJETA 1: API KEY Y AJUSTES ---
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

        // --- TARJETA 2: PROCESAMIENTO DIRECTO (SUSTITUTO DE TERMUX) ---
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
                        text = "Cascada inteligente: Gemini 3.8 Flash ➔ 1.5 Flash ➔ 2.0 Flash Exp. Reemplaza por completo el script de Termux.",
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
                                    Toast.makeText(context, "No hay archivos de audio en Documents/Llamadas_NotebookLM", Toast.LENGTH_SHORT).show()
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

        // --- TARJETA 3: DIAGNÓSTICO Y PERMISOS ---
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
                Text(
                    text = "Grabaciones y Resúmenes",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = { generatedFiles = loadGeneratedFiles() }) {
                    Text("Refrescar")
                }
            }
        }

        if (generatedFiles.isEmpty()) {
            item {
                Text(
                    text = "No hay archivos en Documents/Llamadas_NotebookLM",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
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
                    onOpen = { openFile(context, file) },
                    onShare = { shareFile(context, file) },
                    onProcessAI = { processAudio(file) }
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
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onProcessAI: () -> Unit
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
                // Si es un audio y aún no tiene resumen, mostrar botón de IA directo
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
    val dir = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
        "Llamadas_NotebookLM"
    )
    if (!dir.exists()) return emptyList()
    return dir.listFiles()
        ?.filter { it.extension.lowercase(Locale.ROOT) in (GeminiProcessor.SUPPORTED_EXTENSIONS + "txt") }
        ?.sortedByDescending { it.lastModified() }
        ?: emptyList()
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
