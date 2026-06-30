package com.example.portedesetoiles

import android.annotation.SuppressLint
import android.content.Intent
import android.location.Location
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class GateMonitorService : LifecycleService() {

    companion object {
        const val ACTION_START = "start"
        const val ACTION_STOP = "stop"
        const val BROADCAST_STATUS = "com.example.portedesetoiles.STATUS"
        const val EXTRA_STATE = "state"
        const val EXTRA_DISTANCE = "distance_m"
    }

    enum class MonitorState { AT_HOME, AWAY, APPROACHING, AT_GATE }

    private lateinit var prefs: GatePreferences
    private lateinit var fusedClient: FusedLocationProviderClient
    private var state = MonitorState.AT_HOME
    private var lastDistanceM = Float.MAX_VALUE
    private var gateFired = false
    private var monitorJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        prefs = GatePreferences(this)
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        NotificationHelper.createChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(
            NotificationHelper.NOTIF_SERVICE_ID,
            NotificationHelper.buildServiceNotification(this, getString(R.string.status_starting))
        )
        if (monitorJob?.isActive != true) {
            monitorJob = lifecycleScope.launch { monitorLoop() }
        }
        return START_STICKY
    }

    private suspend fun monitorLoop() {
        while (true) {
            if (!prefs.isHomeSet) {
                updateNotification(getString(R.string.status_no_home))
                delay(30_000)
                continue
            }

            val distM = fetchDistanceMeters()
            if (distM == null) {
                delay(intervalMs())
                continue
            }

            updateState(distM)
            broadcastStatus(distM)
            updateNotification(statusText(distM))

            if (state == MonitorState.AT_GATE && !gateFired) {
                gateFired = true
                triggerGate()
            }

            lastDistanceM = distM
            delay(intervalMs())
        }
    }

    private fun updateState(distM: Float) {
        state = when {
            distM <= prefs.gateProximityMeters -> MonitorState.AT_GATE
            distM <= 500f && distM < lastDistanceM -> MonitorState.APPROACHING
            distM <= 500f && state == MonitorState.APPROACHING -> MonitorState.APPROACHING
            distM <= 2_000f -> MonitorState.AT_HOME
            else -> MonitorState.AWAY
        }
        // Reset gate flag once user has moved away
        if (distM > 200f && gateFired) gateFired = false
        // AT_HOME can't be > 600m
        if (distM > 600f && state == MonitorState.AT_HOME) state = MonitorState.AWAY
    }

    private fun intervalMs(): Long = when (state) {
        MonitorState.APPROACHING -> 5_000L
        MonitorState.AT_HOME, MonitorState.AT_GATE -> 60_000L
        MonitorState.AWAY -> when {
            lastDistanceM < 5_000f -> 2 * 60_000L
            lastDistanceM < 10_000f -> 4 * 60_000L
            lastDistanceM < 20_000f -> 7 * 60_000L
            lastDistanceM < 50_000f -> 10 * 60_000L
            else -> 15 * 60_000L
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun fetchDistanceMeters(): Float? = runCatching {
        val priority = if (state == MonitorState.APPROACHING)
            Priority.PRIORITY_HIGH_ACCURACY
        else
            Priority.PRIORITY_BALANCED_POWER_ACCURACY
        fusedClient.getCurrentLocation(priority, null).await()
            ?.let { computeDistance(it) }
    }.getOrNull()

    private fun computeDistance(location: Location): Float {
        val result = FloatArray(1)
        Location.distanceBetween(
            location.latitude, location.longitude,
            prefs.homeLat, prefs.homeLon,
            result
        )
        return result[0]
    }

    private fun triggerGate() {
        NotificationHelper.sendGateAlert(this)
        val p = prefs
        if (p.gateDeviceUrl.isNotEmpty() && p.gatewayIp.isNotEmpty()) {
            lifecycleScope.launch {
                TahomaApi(p.gatewayBaseUrl, p.tahomaToken).openGate(p.gateDeviceUrl)
            }
        }
    }

    private fun updateNotification(text: String) {
        getSystemService(android.app.NotificationManager::class.java)
            .notify(
                NotificationHelper.NOTIF_SERVICE_ID,
                NotificationHelper.buildServiceNotification(this, text)
            )
    }

    private fun broadcastStatus(distM: Float) {
        sendBroadcast(Intent(BROADCAST_STATUS).apply {
            putExtra(EXTRA_STATE, state.name)
            putExtra(EXTRA_DISTANCE, distM)
        })
    }

    private fun statusText(distM: Float): String {
        val d = if (distM >= 1000f) "%.1f km".format(distM / 1000f) else "${distM.toInt()} m"
        return when (state) {
            MonitorState.AT_HOME -> getString(R.string.status_at_home, d)
            MonitorState.AWAY -> getString(R.string.status_away, d)
            MonitorState.APPROACHING -> getString(R.string.status_approaching, d)
            MonitorState.AT_GATE -> getString(R.string.status_at_gate)
        }
    }
}
