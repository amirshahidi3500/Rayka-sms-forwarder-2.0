package com.rayka.smsforwarder

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject

/**
 * The reverse direction: the server holds commands that need to reach a buoy, this phone
 * fetches them and sends them out as a real SMS. Mirrors the incoming logic exactly:
 * online -> pull from the main (Supabase) server, offline -> pull from the local server.
 * Both toggles can be on at once, same as incoming.
 */
object OutgoingSync {

    private fun hasSendSmsPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

    /** Pull pending commands and text them out to the buoy. Safe to call on every service tick. */
    fun fetchAndSend(context: Context) {
        if (!hasSendSmsPermission(context)) return

        val onlineOn = Prefs.onlineEnabled
        val offlineOn = Prefs.offlineEnabled

        if (onlineOn && NetworkUtils.isOnline(context) && Prefs.outgoingMainUrl.isNotBlank()) {
            val arr = NetworkUtils.getOutgoingMain()
            if (arr != null) {
                processArray(context, arr, MessageRecord.MODE_ONLINE)
                return // got a real answer from main server this tick, no need to also hit local
            }
        }

        if (offlineOn && Prefs.outgoingLocalUrl.isNotBlank()) {
            val arr = NetworkUtils.getOutgoingLocal()
            if (arr != null) {
                processArray(context, arr, MessageRecord.MODE_OFFLINE)
            }
        }
    }

    private fun processArray(context: Context, arr: JSONArray, mode: String) {
        val db = DbHelper.get(context)
        for (i in 0 until arr.length()) {
            val obj: JSONObject = arr.optJSONObject(i) ?: continue
            val remoteId = obj.optString("id", obj.optString("_id", ""))
            val phone = obj.optString("phone", obj.optString("number", ""))
            val body = obj.optString("message", obj.optString("body", ""))
            if (remoteId.isBlank() || phone.isBlank() || body.isBlank()) continue
            if (db.outgoingAlreadyHandled(remoteId)) continue

            val sent = sendSms(phone, body)
            db.insertOutgoing(remoteId, phone, body, mode, if (sent) "SENT" else "FAILED")
        }
    }

    private fun sendSms(phone: String, body: String): Boolean {
        return try {
            val smsManager = SmsManager.getDefault()
            val parts = smsManager.divideMessage(body)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(phone, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(phone, null, body, null, null)
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}
