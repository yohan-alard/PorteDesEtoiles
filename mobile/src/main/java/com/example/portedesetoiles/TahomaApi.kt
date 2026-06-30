package com.example.portedesetoiles

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.URL
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

private const val TAG = "TahomaApi"

class TahomaApi(private val baseUrl: String, private val token: String) {

    private val sslContext: SSLContext by lazy {
        val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })
        SSLContext.getInstance("TLS").also { it.init(null, trustAll, java.security.SecureRandom()) }
    }

    private fun openConnection(path: String, method: String): HttpsURLConnection {
        val conn = URL("$baseUrl$path").openConnection() as HttpsURLConnection
        conn.sslSocketFactory = sslContext.socketFactory
        conn.hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }
        conn.requestMethod = method
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("Content-Type", "application/json")
        conn.connectTimeout = 8_000
        conn.readTimeout = 8_000
        return conn
    }

    suspend fun getDevicesOrThrow(): List<TahomaDevice> = withContext(Dispatchers.IO) {
        val conn = openConnection("/enduser-mobile-web/1/enduserAPI/setup", "GET")
        conn.connect()
        val code = conn.responseCode
        if (code == 401 || code == 403) throw TahomaAuthException(code)
        if (code != 200) throw TahomaHttpException(code)
        val body = conn.inputStream.bufferedReader().readText()
        conn.disconnect()
        parseDevices(body)
    }

    suspend fun getDevices(): List<TahomaDevice> = runCatching { getDevicesOrThrow() }
        .onFailure { Log.e(TAG, "getDevices failed", it) }
        .getOrDefault(emptyList())

    suspend fun openGate(deviceUrl: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val body = buildOpenCommand(deviceUrl)
            Log.d(TAG, "openGate payload: $body")
            val conn = openConnection("/enduser-mobile-web/1/enduserAPI/exec/apply", "POST")
            conn.doOutput = true
            conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            val resp = runCatching {
                (if (code in 200..299) conn.inputStream else conn.errorStream)
                    .bufferedReader().readText()
            }.getOrDefault("")
            conn.disconnect()
            Log.d(TAG, "openGate HTTP $code — $resp")
            code in 200..299
        }.onFailure { Log.e(TAG, "openGate failed", it) }.getOrDefault(false)
    }

    suspend fun openGateDiagnose(deviceUrl: String): String = withContext(Dispatchers.IO) {
        try {
            val body = buildOpenCommand(deviceUrl)
            val conn = openConnection("/enduser-mobile-web/1/enduserAPI/exec/apply", "POST")
            conn.doOutput = true
            conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            val resp = runCatching {
                (if (code in 200..299) conn.inputStream else conn.errorStream)
                    .bufferedReader().readText()
            }.getOrDefault("")
            conn.disconnect()
            "HTTP $code\n$resp"
        } catch (e: Exception) {
            "${e.javaClass.simpleName}: ${e.message}"
        }
    }

    suspend fun diagnose(): String = withContext(Dispatchers.IO) {
        try {
            val devices = getDevicesOrThrow()
            "✓ Connexion OK\n${devices.size} équipement(s) trouvé(s)"
        } catch (e: TahomaAuthException) {
            "✗ Authentification refusée (HTTP ${e.code})\nVérifiez le token"
        } catch (e: TahomaHttpException) {
            "✗ Erreur serveur HTTP ${e.code}"
        } catch (e: SocketTimeoutException) {
            "✗ Timeout — vérifiez l'IP et que vous êtes sur le même réseau WiFi"
        } catch (e: ConnectException) {
            "✗ Impossible de joindre $baseUrl\n${e.message}"
        } catch (e: SSLHandshakeException) {
            "✗ Erreur SSL\n${e.message}"
        } catch (e: Exception) {
            "✗ ${e.javaClass.simpleName}\n${e.message}"
        }
    }

    private fun buildOpenCommand(deviceUrl: String): String =
        JSONObject().apply {
            put("label", "Open gate")
            put("actions", JSONArray().apply {
                put(JSONObject().apply {
                    put("deviceURL", deviceUrl)
                    put("commands", JSONArray().apply {
                        put(JSONObject().apply {
                            put("name", "open")
                            put("parameters", JSONArray())
                        })
                    })
                })
            })
        }.toString()

    private fun parseDevices(json: String): List<TahomaDevice> = runCatching {
        val arr = JSONObject(json).getJSONArray("devices")
        (0 until arr.length()).map { i ->
            arr.getJSONObject(i).let {
                TahomaDevice(
                    url = it.optString("deviceURL"),
                    label = it.optString("label"),
                    uiClass = it.optString("uiClass"),
                    type = it.optInt("type")
                )
            }
        }
    }.getOrDefault(emptyList())
}

class TahomaAuthException(val code: Int) : Exception("HTTP $code")
class TahomaHttpException(val code: Int) : Exception("HTTP $code")

data class TahomaDevice(val url: String, val label: String, val uiClass: String, val type: Int = 0) {
    val isGate: Boolean get() = uiClass in listOf("Gate", "GarageDoor", "Awning", "ExteriorScreen")
    val isGateway: Boolean get() = type == 5
}
