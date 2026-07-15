package com.paisetrail.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class TxnDirection { DEBIT, CREDIT }
enum class TxnStatus { CONFIRMED, REFUNDED, SUSPECT_DUP, SELF_TRANSFER }
enum class TagSource { USER, AUTO_HIGH, AUTO_LOW, AUTO_AI, NONE }
enum class PaymentContext { IN_PERSON, ONLINE, P2P }
/** [APPROXIMATE] is a geocoded-from-text estimate (place/locality name -> coordinates), not a
 * device GPS fix — city/neighborhood accuracy at best, used when a real fix never existed (an
 * imported backup, or historical data) but a place name did. */
enum class LocationQuality { GOOD, STALE, MISSING, APPROXIMATE }

@Entity(
    tableName = "transactions",
    indices = [
        Index("occurredAt"),
        Index(value = ["upiRef"], unique = true),
        Index("merchantId"),
        Index("tripId"),
    ],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountPaise: Long,
    val direction: TxnDirection,
    val status: TxnStatus = TxnStatus.CONFIRMED,
    val payeeNameRaw: String? = null,
    val vpa: String? = null,
    val upiRef: String? = null,
    val bankAcctLast4: String? = null,
    val occurredAt: Long,
    val merchantId: Long? = null,
    val categoryId: Long? = null,
    val tagSource: TagSource = TagSource.NONE,
    val paymentContext: PaymentContext? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val accuracyM: Double? = null,
    val locationQuality: LocationQuality? = null,
    val placeName: String? = null,
    val locality: String? = null,
    val tripId: Long? = null,
    val note: String? = null,
    val refundOfTxnId: Long? = null,
)
