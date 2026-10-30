package com.example.callrecorder.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.callrecorder.data.AppPreferences

class CallAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "CallAccessibility"
        private const val WHATSAPP_PACKAGE = "com.whatsapp"
        private var isWhatsAppCallActive = false
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val prefs = AppPreferences(applicationContext)
        if (!prefs.isWhatsAppEnabled) return

        if (event.packageName == WHATSAPP_PACKAGE) {
            val rootNode = rootInActiveWindow ?: return
            checkWhatsAppCallState(rootNode)
        }
    }

    private fun checkWhatsAppCallState(rootNode: AccessibilityNodeInfo) {
        val hasCallIndicators = searchCallKeywords(rootNode)

        if (hasCallIndicators && !isWhatsAppCallActive) {
            isWhatsAppCallActive = true
            Log.d(TAG, "Detectada llamada de WhatsApp iniciada.")
            CallRecordService.startCallRecording(applicationContext, "WAPP")
        } else if (!hasCallIndicators && isWhatsAppCallActive) {
            isWhatsAppCallActive = false
            Log.d(TAG, "Detectada llamada de WhatsApp finalizada.")
            CallRecordService.stopCallRecording(applicationContext)
        }
    }

    private fun searchCallKeywords(node: AccessibilityNodeInfo): Boolean {
        val keywords = listOf(
            "llamada", "call", "en llamada", "llamada de voz",
            "whatsapp call", "llamada en curso", "conectando", "ringing"
        )

        val text = node.text?.toString()?.lowercase() ?: ""
        val contentDesc = node.contentDescription?.toString()?.lowercase() ?: ""

        for (kw in keywords) {
            if (text.contains(kw) || contentDesc.contains(kw)) {
                return true
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null && searchCallKeywords(child)) {
                return true
            }
        }
        return false
    }

    override fun onInterrupt() {
        Log.w(TAG, "Servicio de accesibilidad interrumpido.")
    }
}
