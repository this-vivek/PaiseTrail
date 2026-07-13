package com.paisetrail.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class RawEventSource { NOTIFICATION, SMS, BACKFILL }

@Entity(
    tableName = "raw_events",
    indices = [Index("postedAt"), Index("txnId")],
)
data class RawEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val source: RawEventSource,
    val packageOrSender: String,
    val fullText: String,
    val postedAt: Long,
    val parsedOk: Boolean,
    val txnId: Long? = null,
)
