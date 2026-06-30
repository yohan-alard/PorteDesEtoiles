package com.example.portedesetoiles

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class OpenGateReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_OPEN = "com.example.portedesetoiles.OPEN_GATE"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_OPEN) return
        val prefs = GatePreferences(context)
        if (prefs.gateDeviceUrl.isEmpty() || prefs.gatewayIp.isEmpty()) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            TahomaApi(prefs.gatewayBaseUrl, prefs.tahomaToken)
                .openGate(prefs.gateDeviceUrl)
            pending.finish()
        }
    }
}
