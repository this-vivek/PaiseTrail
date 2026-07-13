package com.paisetrail.app.data.db

import androidx.room.TypeConverter

class PaisaTypeConverters {
    @TypeConverter
    fun fromRawEventSource(value: RawEventSource): String = value.name

    @TypeConverter
    fun toRawEventSource(value: String): RawEventSource = RawEventSource.valueOf(value)

    @TypeConverter
    fun fromTxnDirection(value: TxnDirection): String = value.name

    @TypeConverter
    fun toTxnDirection(value: String): TxnDirection = TxnDirection.valueOf(value)

    @TypeConverter
    fun fromTxnStatus(value: TxnStatus): String = value.name

    @TypeConverter
    fun toTxnStatus(value: String): TxnStatus = TxnStatus.valueOf(value)

    @TypeConverter
    fun fromTagSource(value: TagSource): String = value.name

    @TypeConverter
    fun toTagSource(value: String): TagSource = TagSource.valueOf(value)

    @TypeConverter
    fun fromPaymentContext(value: PaymentContext?): String? = value?.name

    @TypeConverter
    fun toPaymentContext(value: String?): PaymentContext? = value?.let { PaymentContext.valueOf(it) }

    @TypeConverter
    fun fromLocationQuality(value: LocationQuality?): String? = value?.name

    @TypeConverter
    fun toLocationQuality(value: String?): LocationQuality? = value?.let { LocationQuality.valueOf(it) }
}
