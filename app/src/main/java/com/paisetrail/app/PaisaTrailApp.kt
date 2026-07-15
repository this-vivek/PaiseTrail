package com.paisetrail.app

import android.app.Application
import androidx.work.Configuration
import androidx.hilt.work.HiltWorkerFactory
import com.paisetrail.app.capture.sms.BankSmsPatternSeed
import com.paisetrail.app.data.db.BankSmsPatternDao
import com.paisetrail.app.data.db.CategoryDao
import com.paisetrail.app.data.db.CategorySeed
import com.paisetrail.app.data.db.TransactionDao
import com.paisetrail.app.notif.NotificationChannels
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class PaisaTrailApp : Application(), Configuration.Provider {
    @Inject lateinit var bankSmsPatternDao: BankSmsPatternDao
    @Inject lateinit var categoryDao: CategoryDao
    @Inject lateinit var transactionDao: TransactionDao
    @Inject lateinit var hiltWorkerFactory: HiltWorkerFactory

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(hiltWorkerFactory).build()

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.ensureCreated(this)
        // Both inserts use OnConflictStrategy.IGNORE keyed on a unique column, so re-seeding on
        // every launch is a cheap no-op once the tables are populated (spec 3.2, 6).
        appScope.launch {
            bankSmsPatternDao.insertAll(BankSmsPatternSeed.DEFAULT_PATTERNS)
            categoryDao.insertAll(CategorySeed.DEFAULT_CATEGORIES)
            CategorySeed.EMOJI_BACKFILL.forEach { (name, emoji) -> categoryDao.backfillEmojiIfMissing(name, emoji) }
            CategorySeed.COLOR_BACKFILL.forEach { (name, colorHex) -> categoryDao.backfillColorIfDefault(name, colorHex) }
            transactionDao.markSelfTransfers()
        }
    }
}
