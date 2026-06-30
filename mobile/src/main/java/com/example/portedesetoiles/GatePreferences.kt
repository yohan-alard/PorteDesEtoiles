package com.example.portedesetoiles

import android.content.Context

class GatePreferences(context: Context) {
    private val prefs = context.getSharedPreferences("gate_prefs", Context.MODE_PRIVATE)

    var homeLat: Double
        get() = java.lang.Double.longBitsToDouble(prefs.getLong("home_lat", java.lang.Double.doubleToLongBits(0.0)))
        set(v) = prefs.edit().putLong("home_lat", java.lang.Double.doubleToLongBits(v)).apply()

    var homeLon: Double
        get() = java.lang.Double.longBitsToDouble(prefs.getLong("home_lon", java.lang.Double.doubleToLongBits(0.0)))
        set(v) = prefs.edit().putLong("home_lon", java.lang.Double.doubleToLongBits(v)).apply()

    var isHomeSet: Boolean
        get() = prefs.getBoolean("home_set", false)
        set(v) = prefs.edit().putBoolean("home_set", v).apply()

    var gatewayIp: String
        get() = prefs.getString("gateway_ip", "") ?: ""
        set(v) = prefs.edit().putString("gateway_ip", v).apply()

    var gatewayPort: Int
        get() = prefs.getInt("gateway_port", 8443)
        set(v) = prefs.edit().putInt("gateway_port", v).apply()

    var gateDeviceUrl: String
        get() = prefs.getString("gate_device_url", "") ?: ""
        set(v) = prefs.edit().putString("gate_device_url", v).apply()

    var tahomaToken: String
        get() = prefs.getString("tahoma_token", "6a42da5acd94bacd1bac") ?: "6a42da5acd94bacd1bac"
        set(v) = prefs.edit().putString("tahoma_token", v).apply()

    var serviceEnabled: Boolean
        get() = prefs.getBoolean("service_enabled", false)
        set(v) = prefs.edit().putBoolean("service_enabled", v).apply()

    var gateProximityMeters: Int
        get() = prefs.getInt("gate_proximity_m", 30)
        set(v) = prefs.edit().putInt("gate_proximity_m", v).apply()

    val gatewayBaseUrl: String get() = "https://$gatewayIp:$gatewayPort"
}
