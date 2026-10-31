# CallRecorder NotebookLM (Android + Gemini AI)

Aplicación nativa de Android desarrollada en **Kotlin** y **Jetpack Compose**, diseñada específicamente para la captura, grabación y transcripción inteligente de llamadas telefónicas tradicionales (GSM) y llamadas de voz sobre IP (VoIP) de **WhatsApp**, generando resúmenes estructurados automáticos mediante la API de **Google Gemini (3.8 Flash con cascada a 1.5 Flash y 2.0 Flash Exp)** optimizados para su importación directa en **NotebookLM**.

Optimizado para dispositivos **Xiaomi (MIUI / HyperOS)** y compatible con **Android 10 hasta Android 14+ (API 26 a 34)**.

---

## ⚡ Reemplazo Total del Script de Termux

Esta aplicación **elimina por completo la necesidad de Termux, Python o scripts bash en el dispositivo móvil**. 

Toda la lógica que anteriormente requería un script (`procesar_llamadas.py`) ahora vive dentro del motor nativo de la aplicación:
1. **Detección y emparejamiento 1 a 1:** Al finalizar la llamada o procesar un archivo de audio (`.m4a`, `.mp3`, `.wav`, `.aac`, `.ogg`, `.amr`), el resumen se guarda exactamente con la misma nomenclatura base:
   - Audio: `Documents/Llamadas_NotebookLM/{nombre_base}.m4a`
   - Resumen: `Documents/Llamadas_NotebookLM/{nombre_base}_resumen.txt`
2. **Cascada Inteligente de Modelos (Fallback):**
   - Intento 1: `gemini-3.8-flash`
   - Intento 2 (si el modelo no está disponible o da 404): `gemini-1.5-flash`
   - Intento 3: `gemini-2.0-flash-exp`
3. **Timeout extendido a 180 segundos:** Para procesar llamadas extensas sin desconexiones prematuras.
4. **Procesamiento Manual desde la App:** Botón integrado *"Procesar Último Audio Ahora"* y acciones directas por archivo en la lista, permitiendo procesar cualquier audio pendiente con un solo toque y sin abrir ninguna consola.

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
  - Indicador de estado de procesamiento en tiempo real.
  - Explorador y visor integrado con opciones para abrir o compartir vía `FileProvider`.

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
│   └── GeminiProcessor.kt           # OkHttp, serialización JSON, cascada de modelos y guardado 1:1
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

## 🛠️ Compilación y Generación del APK

### Compilación Automática en la Nube (GitHub Actions)
Este repositorio incluye un flujo de integración continua en `.github/workflows/build-apk.yml`.
Cada vez que se sube un commit a la rama `main`, GitHub compila automáticamente el proyecto en un entorno Ubuntu con el Android SDK oficial y publica el archivo `app-debug.apk` en la pestaña **Releases** y en **Artifacts**.

---

## 📄 Licencia

Este proyecto está bajo la licencia MIT.
