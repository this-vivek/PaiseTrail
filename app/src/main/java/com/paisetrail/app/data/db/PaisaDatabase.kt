package com.paisetrail.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        RawEventEntity::class,
        TransactionEntity::class,
        BankSmsPatternEntity::class,
        CategoryEntity::class,
        MerchantEntity::class,
        MerchantVpaEntity::class,
        MerchantAliasEntity::class,
        TripEntity::class,
        BudgetEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
@TypeConverters(PaisaTypeConverters::class)
abstract class PaisaDatabase : RoomDatabase() {
    abstract fun rawEventDao(): RawEventDao
    abstract fun transactionDao(): TransactionDao
    abstract fun bankSmsPatternDao(): BankSmsPatternDao
    abstract fun categoryDao(): CategoryDao
    abstract fun merchantDao(): MerchantDao
    abstract fun tripDao(): TripDao
    abstract fun budgetDao(): BudgetDao
}
