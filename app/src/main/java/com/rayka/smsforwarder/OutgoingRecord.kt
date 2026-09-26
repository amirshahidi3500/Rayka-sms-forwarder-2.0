package com.rayka.smsforwarder

data class OutgoingRecord(
    val id: Long,
    val remoteId: String,
    val phone: String,
    val body: String,
    val fetchedAt: Long,
    val sentAt: Long,
    val mode: String,   // ONLINE or OFFLINE
    val status: String  // SENT or FAILED
)
