package com.rayka.smsforwarder

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object NetworkUtils {

    // Kept short on purpose: with several SMS arriving close together we must not let one
    // slow/unreachable request block the others for long. Real delivery is retried by the
    // fast background loop anyway, so failing fast here is what makes the app feel instant.
    private const val DEFAULT_TIMEOUT_MS = 4000
    private const val DEFAULT_TIMEOUT_LOCAL_MS = 2000

    fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /** The edge function checks the shared secret in the X-Tasker-Secret header. */
    private fun secretHeaders(): Map<String, String> {
        val key = Prefs.supabaseKey.trim()
        if (key.isEmpty()) return emptyMap()
        return mapOf("X-Tasker-Secret" to key)
    }

    /** Posts JSON to the main (Supabase) server, automatically attaching the shared secret header. */
    fun postJsonMain(json: JSONObject): Boolean =
        postJson(Prefs.mainUrl, json, DEFAULT_TIMEOUT_MS, secretHeaders())

    /** Posts JSON to the local fallback server. No auth headers — it's on the local network. */
    fun postJsonLocal(json: JSONObject): Boolean =
        postJson(Prefs.localUrl, json, DEFAULT_TIMEOUT_LOCAL_MS, emptyMap())

    fun postJson(
        urlString: String,
        json: JSONObject,
        timeoutMs: Int = DEFAULT_TIMEOUT_MS,
        headers: Map<String, String> = emptyMap()
    ): Boolean {
        if (urlString.isBlank()) return false
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(urlString)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                for ((k, v) in headers) setRequestProperty(k, v)
            }
            conn.outputStream.use { it.write(json.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            code in 200..299
        } catch (e: Exception) {
            false
        } finally {
            conn?.disconnect()
        }
    }

    /** GETs a JSON array from [urlString]. Returns null on any failure. */
    fun getJsonArray(
        urlString: String,
        timeoutMs: Int = DEFAULT_TIMEOUT_MS,
        headers: Map<String, String> = emptyMap()
    ): JSONArray? {
        if (urlString.isBlank()) return null
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(urlString)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                for ((k, v) in headers) setRequestProperty(k, v)
            }
            if (conn.responseCode !in 200..299) return null
            val text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val trimmed = text.trim()
            if (trimmed.startsWith("[")) JSONArray(trimmed) else null
        } catch (e: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    fun getOutgoingMain(): JSONArray? = getJsonArray(Prefs.outgoingMainUrl, DEFAULT_TIMEOUT_MS, secretHeaders())
    fun getOutgoingLocal(): JSONArray? = getJsonArray(Prefs.outgoingLocalUrl, DEFAULT_TIMEOUT_LOCAL_MS, emptyMap())
}
