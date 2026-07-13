package com.paisetrail.app.di

import android.content.Context
import androidx.room.Room
import com.paisetrail.app.data.db.BankSmsPatternDao
import com.paisetrail.app.data.db.BudgetDao
import com.paisetrail.app.data.db.CategoryDao
import com.paisetrail.app.data.db.MerchantDao
import com.paisetrail.app.data.db.PaisaDatabase
import com.paisetrail.app.data.db.RawEventDao
import com.paisetrail.app.data.db.TransactionDao
import com.paisetrail.app.data.db.TripDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): PaisaDatabase =
        Room.databaseBuilder(context, PaisaDatabase::class.java, "paisa.db")
            // Pre-release, actively iterating on schema — no real migrations yet (spec 8 #9
            // calls for proper Room migrations before any real backfill data matters).
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideRawEventDao(db: PaisaDatabase): RawEventDao = db.rawEventDao()

    @Provides
    fun provideTransactionDao(db: PaisaDatabase): TransactionDao = db.transactionDao()

    @Provides
    fun provideBankSmsPatternDao(db: PaisaDatabase): BankSmsPatternDao = db.bankSmsPatternDao()

    @Provides
    fun provideCategoryDao(db: PaisaDatabase): CategoryDao = db.categoryDao()

    @Provides
    fun provideMerchantDao(db: PaisaDatabase): MerchantDao = db.merchantDao()

    @Provides
    fun provideTripDao(db: PaisaDatabase): TripDao = db.tripDao()

    @Provides
    fun provideBudgetDao(db: PaisaDatabase): BudgetDao = db.budgetDao()
}
