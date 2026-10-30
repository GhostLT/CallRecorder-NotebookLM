package com.example.callrecorder.telephony

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log
import com.example.callrecorder.data.AppPreferences
import com.example.callrecorder.service.CallRecordService

class CallStateReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "CallStateReceiver"
        private var lastState = TelephonyManager.CALL_STATE_IDLE
        private var isIncoming = false
        private var savedNumber: String? = null
    }

    override fun onReceive(context: Context, intent: Intent) {
        val prefs = AppPreferences(context)
        if (!prefs.isGsmEnabled) return

        if (intent.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
            val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

            var state = TelephonyManager.CALL_STATE_IDLE
            if (stateStr == TelephonyManager.EXTRA_STATE_RINGING) {
                state = TelephonyManager.CALL_STATE_RINGING
            } else if (stateStr == TelephonyManager.EXTRA_STATE_OFFHOOK) {
                state = TelephonyManager.CALL_STATE_OFFHOOK
            }

            onCallStateChanged(context, state, incomingNumber)
        }
    }

    private fun onCallStateChanged(context: Context, state: Int, number: String?) {
        if (lastState == state) return

        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                isIncoming = true
                savedNumber = number
                Log.d(TAG, "Llamada entrante sonando: $savedNumber")
            }
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                Log.d(TAG, "Llamada atendida / en curso.")
                CallRecordService.startCallRecording(context, "GSM")
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                Log.d(TAG, "Llamada finalizada.")
                CallRecordService.stopCallRecording(context)
                isIncoming = false
            }
        }
        lastState = state
    }
}
