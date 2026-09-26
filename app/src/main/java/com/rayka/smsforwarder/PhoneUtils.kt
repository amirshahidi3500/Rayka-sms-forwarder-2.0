package com.rayka.smsforwarder

object PhoneUtils {

    /** Normalizes an Iranian mobile number so "+98912...", "0912...", "98912...", "912..." all compare equal. */
    fun normalize(raw: String): String {
        var digits = raw.filter { it.isDigit() }
        if (digits.startsWith("98") && digits.length > 10) {
            digits = "0" + digits.substring(2)
        }
        digits = digits.trimStart('0')
        return if (digits.length > 10) digits.takeLast(10) else digits
    }

    fun matches(sender: String, allowed: List<String>): Boolean {
        if (allowed.isEmpty()) return true // no whitelist configured -> accept all senders
        val n = normalize(sender)
        return allowed.any { normalize(it) == n }
    }
}
