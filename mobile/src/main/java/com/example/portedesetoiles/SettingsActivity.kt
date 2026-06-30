package com.example.portedesetoiles

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: GatePreferences
    private lateinit var gatewayIpEdit: TextInputEditText
    private lateinit var gatewayPortEdit: TextInputEditText
    private lateinit var tokenEdit: TextInputEditText
    private lateinit var deviceUrlEdit: TextInputEditText
    private lateinit var homeCoordsText: TextView
    private lateinit var proximityEdit: TextInputEditText
    private var discoveredDevices: List<TahomaDevice> = emptyList()

    private val locationPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) setHomeToCurrentLocation()
        else showSnack(getString(R.string.perm_location_required))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        prefs = GatePreferences(this)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        gatewayIpEdit = findViewById(R.id.gatewayIpEdit)
        gatewayPortEdit = findViewById(R.id.gatewayPortEdit)
        tokenEdit = findViewById(R.id.tokenEdit)
        deviceUrlEdit = findViewById(R.id.deviceUrlEdit)
        homeCoordsText = findViewById(R.id.homeCoordsText)
        proximityEdit = findViewById(R.id.proximityEdit)

        loadPrefs()

        findViewById<Button>(R.id.setHomeButton).setOnClickListener { requestHomeLocation() }
        findViewById<Button>(R.id.discoverButton).setOnClickListener { discoverDevices() }
        findViewById<Button>(R.id.saveButton).setOnClickListener { savePrefs() }
        findViewById<Button>(R.id.testButton).setOnClickListener { testConnection() }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    private fun loadPrefs() {
        gatewayIpEdit.setText(prefs.gatewayIp)
        gatewayPortEdit.setText(prefs.gatewayPort.toString())
        tokenEdit.setText(prefs.tahomaToken)
        deviceUrlEdit.setText(prefs.gateDeviceUrl)
        proximityEdit.setText(prefs.gateProximityMeters.toString())
        updateHomeCoordsDisplay()
    }

    private fun updateHomeCoordsDisplay() {
        homeCoordsText.text = if (prefs.isHomeSet)
            "%.6f, %.6f".format(prefs.homeLat, prefs.homeLon)
        else
            getString(R.string.home_not_set)
    }

    private fun requestHomeLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) setHomeToCurrentLocation()
        else locationPermLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    @SuppressLint("MissingPermission")
    private fun setHomeToCurrentLocation() {
        showSnack(getString(R.string.getting_location))
        com.google.android.gms.location.LocationServices
            .getFusedLocationProviderClient(this)
            .getCurrentLocation(
                com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, null
            ).addOnSuccessListener { location ->
                if (location != null) {
                    prefs.homeLat = location.latitude
                    prefs.homeLon = location.longitude
                    prefs.isHomeSet = true
                    updateHomeCoordsDisplay()
                    showSnack(getString(R.string.home_saved))
                }
            }
    }

    private fun discoverDevices() {
        val ip = gatewayIpEdit.text?.toString()?.trim() ?: return
        val token = tokenEdit.text?.toString()?.trim() ?: return
        val port = gatewayPortEdit.text?.toString()?.toIntOrNull() ?: 8443
        if (ip.isEmpty()) { showSnack(getString(R.string.enter_gateway_ip)); return }
        showSnack(getString(R.string.discovering))
        lifecycleScope.launch {
            val api = TahomaApi("https://$ip:$port", token)
            runCatching { api.getDevicesOrThrow() }
                .onSuccess { devices ->
                    discoveredDevices = devices.filter { !it.isGateway }
                    if (discoveredDevices.isEmpty()) showSnack(getString(R.string.no_devices_found))
                    else showDevicePicker(discoveredDevices.map { "${it.label} (${it.uiClass})" })
                }
                .onFailure { showSnack(api.diagnose()) }
        }
    }

    private fun showDevicePicker(labels: List<String>) {
        AlertDialog.Builder(this)
            .setTitle(R.string.select_device)
            .setItems(labels.toTypedArray()) { _, i ->
                deviceUrlEdit.setText(discoveredDevices[i].url)
            }
            .show()
    }

    private fun testConnection() {
        val ip = gatewayIpEdit.text?.toString()?.trim() ?: return
        val token = tokenEdit.text?.toString()?.trim() ?: return
        val port = gatewayPortEdit.text?.toString()?.toIntOrNull() ?: 8443
        if (ip.isEmpty()) { showSnack(getString(R.string.enter_gateway_ip)); return }
        lifecycleScope.launch {
            val msg = TahomaApi("https://$ip:$port", token).diagnose()
            AlertDialog.Builder(this@SettingsActivity)
                .setTitle("Diagnostic TaHoma")
                .setMessage(msg)
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun savePrefs() {
        prefs.gatewayIp = gatewayIpEdit.text?.toString()?.trim() ?: ""
        prefs.gatewayPort = gatewayPortEdit.text?.toString()?.toIntOrNull() ?: 8443
        prefs.tahomaToken = tokenEdit.text?.toString()?.trim() ?: ""
        prefs.gateDeviceUrl = deviceUrlEdit.text?.toString()?.trim() ?: ""
        prefs.gateProximityMeters = proximityEdit.text?.toString()?.toIntOrNull() ?: 30
        showSnack(getString(R.string.saved))
        finish()
    }

    private fun showSnack(msg: String) {
        Snackbar.make(gatewayIpEdit, msg, Snackbar.LENGTH_SHORT).show()
    }
}
