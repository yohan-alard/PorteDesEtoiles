package com.example.portedesetoiles

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_OPEN_GATE = "open_gate"

        private val COLOR_AT_HOME     = Color.parseColor("#00E5B0")
        private val COLOR_AWAY        = Color.parseColor("#FFB300")
        private val COLOR_APPROACHING = Color.parseColor("#FF6D00")
        private val COLOR_AT_GATE     = Color.parseColor("#69FF47")
    }

    private lateinit var prefs: GatePreferences
    private lateinit var stargateView: StargateView
    private lateinit var indicatorDot: View
    private lateinit var stateText: TextView
    private lateinit var distanceText: TextView
    private lateinit var serviceSwitch: MaterialSwitch

    private var isActivating = false

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val state = intent.getStringExtra(GateMonitorService.EXTRA_STATE) ?: return
            val distM = intent.getFloatExtra(GateMonitorService.EXTRA_DISTANCE, -1f)
            updateStatusUi(state, distM)
        }
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            if (serviceSwitch.isChecked) startMonitorService()
        } else {
            serviceSwitch.isChecked = false
            Snackbar.make(stargateView, R.string.perm_location_required, Snackbar.LENGTH_LONG).show()
        }
    }

    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* notifications are optional */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = GatePreferences(this)

        stargateView  = findViewById(R.id.stargateView)
        indicatorDot  = findViewById(R.id.indicatorDot)
        stateText     = findViewById(R.id.stateText)
        distanceText  = findViewById(R.id.distanceText)
        serviceSwitch = findViewById(R.id.serviceSwitch)

        stargateView.onOpenClicked = { handleOpenGate() }

        serviceSwitch.isChecked = prefs.serviceEnabled
        serviceSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.serviceEnabled = checked
            if (checked) checkPermissionsAndStart() else stopMonitorService()
        }

        findViewById<android.widget.ImageButton>(R.id.settingsButton).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        requestNotificationPermission()
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_GATE, false) == true) handleOpenGate()
    }

    override fun onResume() {
        super.onResume()
        ContextCompat.registerReceiver(
            this, statusReceiver,
            IntentFilter(GateMonitorService.BROADCAST_STATUS),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        fetchCurrentDistance()
    }

    @SuppressLint("MissingPermission")
    private fun fetchCurrentDistance() {
        if (!prefs.isHomeSet) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) return

        LocationServices.getFusedLocationProviderClient(this)
            .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
            .addOnSuccessListener { location ->
                if (location == null) return@addOnSuccessListener
                val gate = Location("").apply {
                    latitude = prefs.homeLat
                    longitude = prefs.homeLon
                }
                val distM = location.distanceTo(gate)
                val distStr = formatDist(distM)
                distanceText.text = distStr
                // Also update the dot color if the service isn't broadcasting
                if (stateText.text.isEmpty()) {
                    val (label, color) = when {
                        distM <= prefs.gateProximityMeters -> Pair(getString(R.string.state_at_gate), COLOR_AT_GATE)
                        distM <= 500f -> Pair(getString(R.string.state_approaching), COLOR_APPROACHING)
                        distM <= 2_000f -> Pair(getString(R.string.state_at_home), COLOR_AT_HOME)
                        else -> Pair(getString(R.string.state_away), COLOR_AWAY)
                    }
                    stateText.text = label
                    indicatorDot.backgroundTintList = ColorStateList.valueOf(color)
                }
            }
    }

    private fun formatDist(distM: Float) = when {
        distM >= 1000f -> "%.1f km".format(distM / 1000f)
        else -> "${distM.toInt()} m"
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(statusReceiver)
    }

    private fun handleOpenGate() {
        if (isActivating) return
        if (!prefs.isHomeSet || prefs.gateDeviceUrl.isEmpty() || prefs.gatewayIp.isEmpty()) {
            Snackbar.make(stargateView, R.string.config_incomplete, Snackbar.LENGTH_LONG)
                .setAction(R.string.settings) { startActivity(Intent(this, SettingsActivity::class.java)) }
                .show()
            return
        }
        isActivating = true
        stargateView.startActivation { _ ->
            lifecycleScope.launch {
                val api = TahomaApi(prefs.gatewayBaseUrl, prefs.tahomaToken)
                val ok = api.openGate(prefs.gateDeviceUrl)
                isActivating = false
                if (!ok) {
                    stargateView.showError()
                    val detail = api.openGateDiagnose(prefs.gateDeviceUrl)
                    androidx.appcompat.app.AlertDialog.Builder(this@MainActivity)
                        .setTitle(R.string.gate_error)
                        .setMessage("Device: ${prefs.gateDeviceUrl}\n\n$detail")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }
    }

    private fun updateStatusUi(state: String, distM: Float) {
        val (label, dotColor) = when (state) {
            GateMonitorService.MonitorState.AT_HOME.name ->
                Pair(getString(R.string.state_at_home), COLOR_AT_HOME)
            GateMonitorService.MonitorState.AWAY.name ->
                Pair(getString(R.string.state_away), COLOR_AWAY)
            GateMonitorService.MonitorState.APPROACHING.name ->
                Pair(getString(R.string.state_approaching), COLOR_APPROACHING)
            GateMonitorService.MonitorState.AT_GATE.name ->
                Pair(getString(R.string.state_at_gate), COLOR_AT_GATE)
            else -> Pair(state, COLOR_AWAY)
        }
        stateText.text = label
        indicatorDot.backgroundTintList = ColorStateList.valueOf(dotColor)
        distanceText.text = if (distM >= 0) formatDist(distM) else ""
    }

    private fun checkPermissionsAndStart() {
        val fineLocation = Manifest.permission.ACCESS_FINE_LOCATION
        if (ContextCompat.checkSelfPermission(this, fineLocation) == PackageManager.PERMISSION_GRANTED) {
            startMonitorService()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(fineLocation, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    private fun startMonitorService() {
        ContextCompat.startForegroundService(
            this,
            Intent(this, GateMonitorService::class.java).apply {
                action = GateMonitorService.ACTION_START
            }
        )
    }

    private fun stopMonitorService() {
        startService(Intent(this, GateMonitorService::class.java).apply {
            action = GateMonitorService.ACTION_STOP
        })
        stargateView.reset()
        stateText.text = ""
        distanceText.text = ""
        indicatorDot.backgroundTintList = ColorStateList.valueOf(Color.DKGRAY)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
