# CallRecorder NotebookLM (Android + Gemini AI)

Aplicación nativa de Android desarrollada en **Kotlin** y **Jetpack Compose**, diseñada específicamente para la captura, grabación y transcripción inteligente de llamadas telefónicas tradicionales (GSM) y llamadas de voz sobre IP (VoIP) de **WhatsApp**, generando resúmenes estructurados automáticos mediante la API de **Google Gemini 2.5 Flash** optimizados para su importación directa en **NotebookLM**.

Optimizado para dispositivos **Xiaomi (MIUI / HyperOS)** y compatible con **Android 10 hasta Android 14+ (API 26 a 34)**.

---

## 🚀 Características Principales

- **Detección Dual de Llamadas:**
  - **Llamadas GSM (Tradicionales):** Escucha de eventos de telefonía (`TelephonyManager` / `CallStateReceiver`) para detectar inicio (`OFFHOOK`), llamada entrante (`RINGING`) y fin (`IDLE`).
  - **Llamadas VoIP (WhatsApp):** `AccessibilityService` (`CallAccessibilityService`) especializado en inspeccionar el árbol de vistas y detectar el estado de llamada en curso dentro de `com.whatsapp`.
- **Captura de Audio Optimizada:**
  - Módulo `AudioManagerRecorder` con `MediaRecorder` configurado con fuente `VOICE_COMMUNICATION` (con cancelación de eco por hardware) y fallback a `MIC`.
  - Formato de compresión de alta eficiencia: **MPEG_4 / AAC**, frecuencia de muestreo **16 kHz (Mono)** a **32 kbps**, maximizando inteligibilidad para modelos de procesamiento de lenguaje natural y reduciendo el consumo de datos y almacenamiento.
- **Inteligencia Artificial con Google Gemini 2.5 Flash:**
  - Envío automático de audio mediante payload estructurado `inlineData` en Base64 a `https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent`.
  - Extracción automática de:
    1. **Resumen Ejecutivo** (Origen, fecha y hora).
    2. **Puntos Principales Tratados**.
    3. **Acuerdos y Compromisos**.
    4. **Tareas Pendientes (Action Items)**.
    5. **Transcripción Completa**.
- **Almacenamiento Compatible con Scoped Storage:**
  - Guarda los archivos de audio (`.m4a`) y los resúmenes (`.txt`) en la carpeta pública del dispositivo:  
    `/storage/emulated/0/Documents/Llamadas_NotebookLM/`
- **Seguridad y Cifrado:**
  - Clave de API de Gemini protegida mediante `EncryptedSharedPreferences` con llaves maestras **AES-256 GCM**.
- **Interfaz Moderna en Jetpack Compose:**
  - Diagnóstico guiado de permisos, accesibilidad y ajustes de HyperOS.
  - Explorador y visor integrado de grabaciones y resúmenes con soporte para abrir o compartir vía `FileProvider`.

---

## 🏗️ Arquitectura del Sistema

```
app/src/main/java/com/example/callrecorder/
│
├── audio/
│   └── AudioManagerRecorder.kt      # Control de MediaRecorder, códecs y ciclo de vida de captura
│
├── data/
│   └── AppPreferences.kt            # Almacenamiento seguro cifrado (API Key y toggles)
│
├── network/
│   └── GeminiProcessor.kt           # Cliente OkHttp, serialización JSON y llamada a Gemini 2.5 Flash
│
├── service/
│   ├── CallAccessibilityService.kt  # Detección de eventos de ventana de WhatsApp
│   └── CallRecordService.kt         # Foreground Service coordinador (foregroundServiceType="microphone")
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

## 📋 Requisitos y Permisos

Declarados en `AndroidManifest.xml`:
- `android.permission.RECORD_AUDIO`: Captura de audio desde el micrófono.
- `android.permission.READ_PHONE_STATE`: Monitoreo del estado de llamada GSM.
- `android.permission.FOREGROUND_SERVICE` y `FOREGROUND_SERVICE_MICROPHONE`: Requerido en Android 14+ para grabación persistente.
- `android.permission.POST_NOTIFICATIONS`: Alertas del Foreground Service y notificaciones de resumen listo.
- `android.permission.INTERNET`: Envío del audio a la API de Google Gemini.

---

## 🛠️ Compilación y Generación del APK

### Opción 1: Compilación Automática en la Nube (GitHub Actions)
Este repositorio incluye un flujo de integración continua en `.github/workflows/build-apk.yml`.
Cada vez que se sube un commit a la rama `main`, GitHub compila automáticamente el proyecto en un entorno Ubuntu con el Android SDK oficial y publica el archivo `app-debug.apk` en la pestaña **Releases** y en **Artifacts**.

### Opción 2: Compilación Local con Android Studio
1. Clona el repositorio:
   ```bash
   git clone https://github.com/GhostLT/CallRecorder-NotebookLM.git
   ```
2. Abre la carpeta del proyecto en **Android Studio** (versión Iguana, Jellyfish o superior).
3. Deja que Gradle descargue las dependencias y sincronice el proyecto.
4. Conecta tu teléfono Android con la depuración USB habilitada y pulsa **Run (Shift + F10)** o genera el APK desde:  
   `Build` > `Build Bundle(s) / APK(s)` > `Build APK(s)`.

---

## 📄 Licencia

Este proyecto está bajo la licencia MIT.
