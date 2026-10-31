# CallRecorder NotebookLM (Android + Gemini AI + Google Drive)

Aplicación nativa de Android desarrollada en **Kotlin** y **Jetpack Compose**, diseñada específicamente para la captura, grabación y transcripción inteligente de llamadas telefónicas tradicionales (GSM) y llamadas de voz sobre IP (VoIP) de **WhatsApp**, generando resúmenes estructurados automáticos mediante la API de **Google Gemini (3.8 Flash con cascada a 1.5 Flash y 2.0 Flash Exp)** y sincronizándolos automáticamente con **Google Drive** para su importación directa en **NotebookLM**.

Optimizado para dispositivos **Xiaomi (MIUI / HyperOS)** y compatible con **Android 10 hasta Android 14+ (API 26 a 34)**.

---

## ☁️ Sincronización Automática con Google Drive (NotebookLM)

La app cuenta con sincronización en la nube nativa y transparente:
1. **Conexión con un solo toque:** El usuario conecta su cuenta de Google mediante Google Sign-In con el alcance seguro `DriveScopes.DRIVE_FILE` (solo permite acceder y gestionar los archivos creados por la aplicación).
2. **Carpeta Dedicada:** Crea y mantiene automáticamente la carpeta `NotebookLM_Llamadas` en la raíz de Google Drive.
3. **Flujo 100% Desatendido:**
   - La llamada finaliza ➔ se graba el audio.
   - Gemini transcribe y genera `{nombre_audio}_resumen.txt`.
   - **WorkManager** encola y sube en segundo plano tanto el resumen `.txt` como el audio `.m4a` a `NotebookLM_Llamadas`.
   - Si se corta la conexión, WorkManager reintenta automáticamente en cuanto vuelva el WiFi o los datos.
   - En **NotebookLM**, añades la carpeta o archivos de Drive como fuente y tienes tus análisis listos sin tocar la PC.
4. **Sincronización Manual:** Botón en la app para sincronizar todos los archivos locales pendientes en cualquier momento.

---

## ⚡ Reemplazo Total del Script de Termux

Esta aplicación **elimina por completo la necesidad de Termux, Python o scripts bash en el dispositivo móvil**:
1. **Detección y emparejamiento 1 a 1:** Al finalizar la llamada o procesar un audio (`.m4a`, `.mp3`, `.wav`, `.aac`, `.ogg`, `.amr`), el resumen se guarda con la misma nomenclatura base:
   - Audio: `Documents/Llamadas_NotebookLM/{nombre_base}.m4a`
   - Resumen: `Documents/Llamadas_NotebookLM/{nombre_base}_resumen.txt`
2. **Cascada Inteligente de Modelos (Fallback):**
   - Intento 1: `gemini-3.8-flash`
   - Intento 2 (si el modelo no está disponible o da 404): `gemini-1.5-flash`
   - Intento 3: `gemini-2.0-flash-exp`
3. **Timeout extendido a 180 segundos:** Procesamiento de llamadas extensas sin desconexiones prematuras.
4. **Procesamiento Manual desde la App:** Botón *"Procesar Último Audio Ahora"* y acciones directas por archivo en la lista.

---

## 🚀 Características Principales

- **Detección Dual de Llamadas:**
  - **Llamadas GSM (Tradicionales):** Escucha de eventos de telefonía (`TelephonyManager` / `CallStateReceiver`) para detectar inicio (`OFFHOOK`), llamada entrante (`RINGING`) y fin (`IDLE`).
  - **Llamadas VoIP (WhatsApp):** `AccessibilityService` (`CallAccessibilityService`) especializado en inspeccionar el árbol de vistas y detectar el estado de llamada en curso dentro de `com.whatsapp`.
- **Captura de Audio Optimizada:**
  - Módulo `AudioManagerRecorder` con `MediaRecorder` configurado con fuente `VOICE_COMMUNICATION` (con cancelación de eco por hardware) y fallback a `MIC`.
  - Formato de compresión de alta eficiencia: **MPEG_4 / AAC**, frecuencia de muestreo **16 kHz (Mono)** a **32 kbps**, maximizando inteligibilidad para modelos de procesamiento de lenguaje natural y reduciendo el consumo de datos y almacenamiento.
- **Inteligencia Artificial con Google Gemini:**
  - Envío automático de audio mediante payload estructurado `inlineData` en Base64.
  - Extracción automática de:
    1. **Resumen Ejecutivo** (Archivo origen, fecha y hora de procesamiento).
    2. **Puntos Principales Tratados**.
    3. **Acuerdos y Compromisos**.
    4. **Tareas Pendientes (Action Items)**.
    5. **Transcripción Completa**.
- **Almacenamiento Compatible con Scoped Storage:**
  - Guarda los archivos en la carpeta pública del dispositivo:  
    `/storage/emulated/0/Documents/Llamadas_NotebookLM/`
- **Seguridad y Cifrado:**
  - Clave de API de Gemini protegida mediante `EncryptedSharedPreferences` con llaves maestras **AES-256 GCM**.
- **Interfaz Moderna en Jetpack Compose:**
  - Diagnóstico guiado de permisos, accesibilidad y ajustes de HyperOS.
  - Gestión y estado de cuenta de Google Drive.
  - Explorador y visor integrado con opciones para abrir, compartir y subir individualmente a Drive vía `FileProvider`.

---

## 🏗️ Arquitectura del Sistema

```
app/src/main/java/com/example/callrecorder/
│
├── audio/
│   └── AudioManagerRecorder.kt      # Control de MediaRecorder, códecs y captura de audio
│
├── data/
│   └── AppPreferences.kt            # Preferencias cifradas (API Key, toggles y cuenta de Google)
│
├── network/
│   └── GeminiProcessor.kt           # OkHttp, serialización JSON, cascada de modelos y trigger de sync
│
├── service/
│   ├── CallAccessibilityService.kt  # Detección de eventos de ventana de WhatsApp
│   └── CallRecordService.kt         # Foreground Service coordinador (foregroundServiceType="microphone")
│
├── sync/
│   ├── GoogleDriveSyncManager.kt    # Cliente Drive REST v3, gestión de carpetas y subida multipart
│   └── DriveUploadWorker.kt         # CoroutineWorker de WorkManager para subidas resilientes en segundo plano
│
├── telephony/
│   └── CallStateReceiver.kt         # Receptor Broadcast de estados de telefonía GSM
│
└── MainActivity.kt                  # UI reactiva en Jetpack Compose (Material 3)
```

---

## ⚙️ Configuración Especial para Xiaomi (MIUI / HyperOS)

Los dispositivos Xiaomi poseen mecanismos estrictos de ahorro de batería que pueden suspender procesos en segundo plano. Para garantizar un funcionamiento ininterrumpido:

1. **Inicio Automático (Autostart):**
   - Ve a `Ajustes` > `Aplicaciones` > `Permisos` > `Inicio automático en segundo plano` y activa **CallRecorder NotebookLM**.
   - *(La app incluye un botón directo en la pantalla principal para abrir este menú).*
2. **Ahorro de Batería:**
   - En los ajustes de la aplicación, cambia el modo de batería a **"Sin restricciones"** (evita que el sistema cierre el Foreground Service al apagar la pantalla).
3. **Bloqueo en Memoria (Candado de Recientes):**
   - Abre la vista de aplicaciones recientes de HyperOS, mantén presionada la app y pulsa el icono de **Candado** para evitar que se limpie de la RAM.
4. **Servicio de Accesibilidad:**
   - En `Ajustes` > `Ajustes adicionales` > `Accesibilidad` > `Servicios descargados`, activa **Call Recorder Accessibility Service** y confirma las advertencias de seguridad del sistema.

---

## 🛠️ Compilación y Generación del APK

### Compilación Automática en la Nube (GitHub Actions)
Este repositorio incluye un flujo de integración continua en `.github/workflows/build-apk.yml`.
Cada vez que se sube un commit a la rama `main`, GitHub compila automáticamente el proyecto en un entorno Ubuntu con el Android SDK oficial y publica el archivo `app-debug.apk` en la pestaña **Releases** y en **Artifacts**.

---

## 📄 Licencia

Este proyecto está bajo la licencia MIT.
