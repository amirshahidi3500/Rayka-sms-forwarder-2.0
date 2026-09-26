package com.rayka.smsforwarder

data class MessageRecord(
    val id: Long,
    val smsKey: String,
    val sender: String,
    val body: String,
    val sim: Int,
    val receivedAt: Long,
    var sentAt: Long?,
    var mode: String,          // ONLINE, OFFLINE, QUEUED
    var syncedMain: Boolean,
    var syncedLocal: Boolean
) {
    companion object {
        const val MODE_ONLINE = "ONLINE"
        const val MODE_OFFLINE = "OFFLINE"
        const val MODE_QUEUED = "QUEUED"
    }
}
