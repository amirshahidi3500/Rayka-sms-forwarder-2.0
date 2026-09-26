package com.rayka.smsforwarder

import android.content.Context
import android.provider.Telephony
import org.json.JSONObject

/**
 * Central place for all "what happens to one incoming SMS" logic, shared by the live
 * receiver, the periodic background service, and the boot-time catch-up scan.
 * Every function here does blocking network / DB work -> always call from a
 * background thread, never from the main thread or a BroadcastReceiver directly.
 */
object MessageSync {

    // The "sub_id" column exists in the SMS content provider but isn't exposed
    // as a public constant on Telephony.Sms, so we reference it by raw name.
    private const val COLUMN_SUB_ID = "sub_id"

    private fun payloadFor(r: MessageRecord): JSONObject = JSONObject().apply {
        put("sender", r.sender)
        put("message", r.body)
        put("sim_slot", r.sim)
        put("received_at", r.receivedAt)
    }

    /** True if this SMS should be processed at all: sender is an allowed buoy number (or list empty) and body starts with the configured keyword. */
    private fun passesFilter(sender: String, body: String): Boolean {
        if (!PhoneUtils.matches(sender, Prefs.allowedNumbersList())) return false
        val keyword = Prefs.incomingKeyword.trim()
        if (keyword.isEmpty()) return true
        return body.trim().startsWith(keyword, ignoreCase = true)
    }

    /** Insert (if new and allowed) and immediately try to deliver a single incoming SMS. */
    fun handleIncoming(context: Context, sender: String, body: String, sim: Int, receivedAt: Long, smsKey: String) {
        if (receivedAt > Prefs.lastSmsTimestamp) Prefs.lastSmsTimestamp = receivedAt
        if (!passesFilter(sender, body)) return // not from an allowed number, or wrong prefix

        val db = DbHelper.get(context)
        val id = db.insertIfNew(sender, body, sim, receivedAt, smsKey)
        if (id == -1L) return // duplicate, already known
        val record = MessageRecord(id, smsKey, sender, body, sim, receivedAt, null, MessageRecord.MODE_QUEUED, false, false)
        attemptDeliver(context, record)
    }

    /** Try main server, then local server, according to the two independent toggles. Fails fast (short timeouts) so a burst of SMS never queues up. */
    fun attemptDeliver(context: Context, record: MessageRecord) {
        val db = DbHelper.get(context)
        val payload = payloadFor(record)
        val onlineOn = Prefs.onlineEnabled
        val offlineOn = Prefs.offlineEnabled

        var deliveredMain = false
        if (onlineOn && NetworkUtils.isOnline(context)) {
            deliveredMain = NetworkUtils.postJsonMain(payload)
            if (deliveredMain) {
                val mode = if (record.syncedLocal) "QUEUED_THEN_SENT" else MessageRecord.MODE_ONLINE
                db.markSyncedMain(record.id, System.currentTimeMillis(), mode)
                return
            }
        }

        if (!deliveredMain && offlineOn && !record.syncedLocal) {
            val deliveredLocal = NetworkUtils.postJsonLocal(payload)
            if (deliveredLocal) {
                db.markSyncedLocal(record.id, MessageRecord.MODE_OFFLINE)
            }
        }
    }

    /** Re-try every message not yet confirmed on the main server. Called on every fast service tick. */
    fun flushPending(context: Context) {
        val db = DbHelper.get(context)
        for (record in db.getPendingMain()) {
            attemptDeliver(context, record)
        }
    }

    /**
     * Compares the last message we ever processed against the phone's SMS inbox and
     * ingests anything newer — this is what recovers messages that arrived while the
     * phone/app was completely off. Safe to call every time the app or service starts.
     */
    fun catchUpMissedSms(context: Context) {
        val since = Prefs.lastSmsTimestamp
        val projection = arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, COLUMN_SUB_ID)
        try {
            context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                projection,
                "${Telephony.Sms.DATE} > ?",
                arrayOf(since.toString()),
                "${Telephony.Sms.DATE} ASC LIMIT 500"
            )?.use { c ->
                val idxId = c.getColumnIndex(Telephony.Sms._ID)
                val idxAddr = c.getColumnIndex(Telephony.Sms.ADDRESS)
                val idxBody = c.getColumnIndex(Telephony.Sms.BODY)
                val idxDate = c.getColumnIndex(Telephony.Sms.DATE)
                val idxSub = c.getColumnIndex(COLUMN_SUB_ID)
                while (c.moveToNext()) {
                    val smsId = if (idxId >= 0) c.getString(idxId) else c.position.toString()
                    val addr = if (idxAddr >= 0) c.getString(idxAddr) ?: "" else ""
                    val body = if (idxBody >= 0) c.getString(idxBody) ?: "" else ""
                    val date = if (idxDate >= 0) c.getLong(idxDate) else System.currentTimeMillis()
                    val sub = if (idxSub >= 0) c.getInt(idxSub) else -1
                    handleIncoming(context, addr, body, sub, date, "content:$smsId")
                }
            }
        } catch (_: SecurityException) {
            // READ_SMS permission not granted yet — nothing to catch up on.
        }
    }
}
