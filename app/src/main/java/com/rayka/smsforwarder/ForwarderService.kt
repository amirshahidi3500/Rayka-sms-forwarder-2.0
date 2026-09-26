package com.rayka.smsforwarder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import java.util.concurrent.Executors

class ForwarderService : Service() {

    companion object {
        const val CHANNEL_ID = "rayka_forwarder_channel"
        const val NOTIF_ID = 1001
        const val ACTION_KICK = "com.rayka.smsforwarder.ACTION_KICK"

        // Fixed and fast on purpose: the user should never have to tune this — the retry
        // loop (for anything the real-time receiver couldn't deliver right away, plus the
        // outgoing-command check) always runs at this cadence.
        private const val FAST_TICK_MS = 3000L

        fun start(context: Context) {
            Prefs.init(context)
            Prefs.serviceEnabled = true
            val i = Intent(context, ForwarderService::class.java)
            context.startForegroundService(i)
        }

        fun stop(context: Context) {
            Prefs.init(context)
            Prefs.serviceEnabled = false
            context.stopService(Intent(context, ForwarderService::class.java))
        }

        /** Ask a running service to flush the queue immediately, without waiting for the next tick. */
        fun kick(context: Context) {
            if (!Prefs.serviceEnabled) return
            val i = Intent(context, ForwarderService::class.java).apply { action = ACTION_KICK }
            try { context.startForegroundService(i) } catch (_: Exception) { }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private var loopRunning = false

    private val tick = object : Runnable {
        override fun run() {
            executor.execute {
                MessageSync.catchUpMissedSms(applicationContext)
                MessageSync.flushPending(applicationContext)
                OutgoingSync.fetchAndSend(applicationContext)
            }
            handler.postDelayed(this, FAST_TICK_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        createChannel()
        startForeground(NOTIF_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_KICK) {
            executor.execute { MessageSync.flushPending(applicationContext) }
        }
        if (!loopRunning) {
            loopRunning = true
            executor.execute { MessageSync.catchUpMissedSms(applicationContext) }
            handler.postDelayed(tick, FAST_TICK_MS)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        loopRunning = false
        handler.removeCallbacks(tick)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "ارسال تلمتری RAYKA", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "سرویس پس‌زمینه ارسال پیامک به سرور" }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("RAYKA در حال اجراست")
            .setContentText("در حال پایش پیامک‌ها و ارسال به سرور")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .build()
    }
}
