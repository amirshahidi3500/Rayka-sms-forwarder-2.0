package com.rayka.smsforwarder

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private const val NAME = "rayka_prefs"
    private lateinit var sp: SharedPreferences

    private const val KEY_MAIN_URL = "main_url"
    private const val KEY_LOCAL_URL = "local_url"
    private const val KEY_SUPABASE_KEY = "supabase_key"
    private const val KEY_ONLINE_ENABLED = "online_enabled"
    private const val KEY_OFFLINE_ENABLED = "offline_enabled"
    private const val KEY_SERVICE_ENABLED = "service_enabled"
    private const val KEY_LAST_SMS_TS = "last_sms_ts"
    private const val KEY_ALLOWED_NUMBERS = "allowed_numbers" // comma separated
    private const val KEY_KEYWORD = "incoming_keyword"
    private const val KEY_OUT_MAIN_URL = "out_main_url"
    private const val KEY_OUT_LOCAL_URL = "out_local_url"

    fun init(ctx: Context) {
        if (!::sp.isInitialized) {
            sp = ctx.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        }
    }

    var mainUrl: String
        get() = sp.getString(KEY_MAIN_URL, "") ?: ""
        set(v) = sp.edit().putString(KEY_MAIN_URL, v).apply()

    var localUrl: String
        get() = sp.getString(KEY_LOCAL_URL, "") ?: ""
        set(v) = sp.edit().putString(KEY_LOCAL_URL, v).apply()

    /** Shared secret / API key for the Supabase edge function (sent as apikey + Bearer header). */
    var supabaseKey: String
        get() = sp.getString(KEY_SUPABASE_KEY, "") ?: ""
        set(v) = sp.edit().putString(KEY_SUPABASE_KEY, v).apply()

    var onlineEnabled: Boolean
        get() = sp.getBoolean(KEY_ONLINE_ENABLED, true)
        set(v) = sp.edit().putBoolean(KEY_ONLINE_ENABLED, v).apply()

    var offlineEnabled: Boolean
        get() = sp.getBoolean(KEY_OFFLINE_ENABLED, true)
        set(v) = sp.edit().putBoolean(KEY_OFFLINE_ENABLED, v).apply()

    var serviceEnabled: Boolean
        get() = sp.getBoolean(KEY_SERVICE_ENABLED, false)
        set(v) = sp.edit().putBoolean(KEY_SERVICE_ENABLED, v).apply()

    // Timestamp (ms) of the newest SMS we have already processed/inserted.
    var lastSmsTimestamp: Long
        get() = sp.getLong(KEY_LAST_SMS_TS, 0L)
        set(v) = sp.edit().putLong(KEY_LAST_SMS_TS, v).apply()

    /** Raw comma-separated list of buoy phone numbers, exactly as entered by the user. */
    var allowedNumbersRaw: String
        get() = sp.getString(KEY_ALLOWED_NUMBERS, "") ?: ""
        set(v) = sp.edit().putString(KEY_ALLOWED_NUMBERS, v).apply()

    fun allowedNumbersList(): List<String> =
        allowedNumbersRaw.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    fun addAllowedNumber(number: String) {
        val list = allowedNumbersList().toMutableList()
        if (list.none { PhoneUtils.normalize(it) == PhoneUtils.normalize(number) }) {
            list.add(number.trim())
            allowedNumbersRaw = list.joinToString(",")
        }
    }

    fun removeAllowedNumber(number: String) {
        val list = allowedNumbersList().filterNot { PhoneUtils.normalize(it) == PhoneUtils.normalize(number) }
        allowedNumbersRaw = list.joinToString(",")
    }

    /** First word every valid buoy SMS must start with. Editable in case the buoy firmware prefix changes. */
    var incomingKeyword: String
        get() = sp.getString(KEY_KEYWORD, "RAYKA") ?: "RAYKA"
        set(v) = sp.edit().putString(KEY_KEYWORD, v.trim()).apply()

    /** Endpoint returning pending outbound commands to text to the buoys (main / Supabase). */
    var outgoingMainUrl: String
        get() = sp.getString(KEY_OUT_MAIN_URL, "") ?: ""
        set(v) = sp.edit().putString(KEY_OUT_MAIN_URL, v).apply()

    /** Same, but the local-network fallback endpoint. */
    var outgoingLocalUrl: String
        get() = sp.getString(KEY_OUT_LOCAL_URL, "") ?: ""
        set(v) = sp.edit().putString(KEY_OUT_LOCAL_URL, v).apply()
}
