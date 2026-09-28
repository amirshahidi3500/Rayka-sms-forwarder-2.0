package com.rayka.smsforwarder

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DbHelper(context: Context) : SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    companion object {
        private const val DB_NAME = "rayka_messages.db"
        private const val DB_VERSION = 2
        const val TABLE = "messages"
        const val TABLE_OUT = "outgoing_messages"

        @Volatile private var instance: DbHelper? = null
        fun get(context: Context): DbHelper =
            instance ?: synchronized(this) {
                instance ?: DbHelper(context).also { instance = it }
            }

        /** Absolute path of the sqlite file on disk, shown to the user in Settings. */
        fun dbFilePath(context: Context): String =
            context.getDatabasePath(DB_NAME).absolutePath
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE (
                _id INTEGER PRIMARY KEY AUTOINCREMENT,
                sms_key TEXT UNIQUE,
                sender TEXT,
                body TEXT,
                sim INTEGER,
                received_at INTEGER,
                sent_at INTEGER,
                mode TEXT,
                synced_main INTEGER DEFAULT 0,
                synced_local INTEGER DEFAULT 0
            )
            """.trimIndent()
        )
        createOutgoingTable(db)
    }

    private fun createOutgoingTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_OUT (
                _id INTEGER PRIMARY KEY AUTOINCREMENT,
                remote_id TEXT UNIQUE,
                phone TEXT,
                body TEXT,
                fetched_at INTEGER,
                sent_at INTEGER,
                mode TEXT,
                status TEXT
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            createOutgoingTable(db)
        }
    }

    private val lock = Any()

    // ---------- Incoming messages ----------

    /** Insert a new message if smsKey not already present. Returns row id, or -1 if it already existed. */
    fun insertIfNew(sender: String, body: String, sim: Int, receivedAt: Long, smsKey: String): Long {
        synchronized(lock) {
            val db = writableDatabase
            val cv = ContentValues().apply {
                put("sms_key", smsKey)
                put("sender", sender)
                put("body", body)
                put("sim", sim)
                put("received_at", receivedAt)
                put("mode", MessageRecord.MODE_QUEUED)
                put("synced_main", 0)
                put("synced_local", 0)
            }
            return db.insertWithOnConflict(TABLE, null, cv, SQLiteDatabase.CONFLICT_IGNORE)
        }
    }

    fun markSyncedMain(id: Long, sentAt: Long, mode: String) {
        synchronized(lock) {
            val cv = ContentValues().apply {
                put("synced_main", 1)
                put("sent_at", sentAt)
                put("mode", mode)
            }
            writableDatabase.update(TABLE, cv, "_id=?", arrayOf(id.toString()))
        }
    }

    fun markSyncedLocal(id: Long, mode: String) {
        synchronized(lock) {
            val cv = ContentValues().apply {
                put("synced_local", 1)
                put("mode", mode)
            }
            writableDatabase.update(TABLE, cv, "_id=?", arrayOf(id.toString()))
        }
    }

    fun getPendingMain(): List<MessageRecord> {
        synchronized(lock) {
            val list = mutableListOf<MessageRecord>()
            val c = readableDatabase.rawQuery(
                "SELECT * FROM $TABLE WHERE synced_main=0 ORDER BY received_at ASC LIMIT 300", null
            )
            c.use { while (it.moveToNext()) list.add(cursorToRecord(it)) }
            return list
        }
    }

    fun getAll(limit: Int = 500): List<MessageRecord> {
        synchronized(lock) {
            val list = mutableListOf<MessageRecord>()
            val c = readableDatabase.rawQuery(
                "SELECT * FROM $TABLE ORDER BY received_at DESC LIMIT ?", arrayOf(limit.toString())
            )
            c.use { while (it.moveToNext()) list.add(cursorToRecord(it)) }
            return list
        }
    }

    fun counts(): Triple<Int, Int, Int> {
        synchronized(lock) {
            fun q(sql: String): Int {
                readableDatabase.rawQuery(sql, null).use { c -> return if (c.moveToFirst()) c.getInt(0) else 0 }
            }
            val total = q("SELECT COUNT(*) FROM $TABLE")
            val synced = q("SELECT COUNT(*) FROM $TABLE WHERE synced_main=1")
            val queued = q("SELECT COUNT(*) FROM $TABLE WHERE synced_main=0")
            return Triple(total, synced, queued)
        }
    }

    private fun cursorToRecord(c: Cursor): MessageRecord {
        return MessageRecord(
            id = c.getLong(c.getColumnIndexOrThrow("_id")),
            smsKey = c.getString(c.getColumnIndexOrThrow("sms_key")),
            sender = c.getString(c.getColumnIndexOrThrow("sender")),
            body = c.getString(c.getColumnIndexOrThrow("body")),
            sim = c.getInt(c.getColumnIndexOrThrow("sim")),
            receivedAt = c.getLong(c.getColumnIndexOrThrow("received_at")),
            sentAt = c.getLong(c.getColumnIndexOrThrow("sent_at")).let { if (it == 0L) null else it },
            mode = c.getString(c.getColumnIndexOrThrow("mode")) ?: MessageRecord.MODE_QUEUED,
            syncedMain = c.getInt(c.getColumnIndexOrThrow("synced_main")) == 1,
            syncedLocal = c.getInt(c.getColumnIndexOrThrow("synced_local")) == 1
        )
    }

    /** True if the same body from the same sender (any number format) is already stored within [windowMs] of [receivedAt]. */
    fun existsSimilar(sender: String, body: String, receivedAt: Long, windowMs: Long = 600_000L): Boolean {
        synchronized(lock) {
            val target = PhoneUtils.normalize(sender)
            readableDatabase.rawQuery(
                "SELECT sender FROM $TABLE WHERE body=? AND received_at BETWEEN ? AND ?",
                arrayOf(body, (receivedAt - windowMs).toString(), (receivedAt + windowMs).toString())
            ).use { c ->
                while (c.moveToNext()) {
                    if (PhoneUtils.normalize(c.getString(0) ?: "") == target) return true
                }
            }
            return false
        }
    }

    // ---------- Outgoing messages (server -> phone -> SMS to buoy) ----------

    /** Returns true if this remote command id was already handled (prevents double-sending). */
    fun outgoingAlreadyHandled(remoteId: String): Boolean {
        synchronized(lock) {
            readableDatabase.rawQuery(
                "SELECT _id FROM $TABLE_OUT WHERE remote_id=? LIMIT 1", arrayOf(remoteId)
            ).use { return it.moveToFirst() }
        }
    }

    fun insertOutgoing(remoteId: String, phone: String, body: String, mode: String, status: String) {
        synchronized(lock) {
            val cv = ContentValues().apply {
                put("remote_id", remoteId)
                put("phone", phone)
                put("body", body)
                put("fetched_at", System.currentTimeMillis())
                put("sent_at", System.currentTimeMillis())
                put("mode", mode)
                put("status", status)
            }
            writableDatabase.insertWithOnConflict(TABLE_OUT, null, cv, SQLiteDatabase.CONFLICT_IGNORE)
        }
    }

    fun getAllOutgoing(limit: Int = 500): List<OutgoingRecord> {
        synchronized(lock) {
            val list = mutableListOf<OutgoingRecord>()
            val c = readableDatabase.rawQuery(
                "SELECT * FROM $TABLE_OUT ORDER BY fetched_at DESC LIMIT ?", arrayOf(limit.toString())
            )
            c.use {
                while (it.moveToNext()) {
                    list.add(
                        OutgoingRecord(
                            id = it.getLong(it.getColumnIndexOrThrow("_id")),
                            remoteId = it.getString(it.getColumnIndexOrThrow("remote_id")) ?: "",
                            phone = it.getString(it.getColumnIndexOrThrow("phone")) ?: "",
                            body = it.getString(it.getColumnIndexOrThrow("body")) ?: "",
                            fetchedAt = it.getLong(it.getColumnIndexOrThrow("fetched_at")),
                            sentAt = it.getLong(it.getColumnIndexOrThrow("sent_at")),
                            mode = it.getString(it.getColumnIndexOrThrow("mode")) ?: "",
                            status = it.getString(it.getColumnIndexOrThrow("status")) ?: ""
                        )
                    )
                }
            }
            return list
        }
    }
}
