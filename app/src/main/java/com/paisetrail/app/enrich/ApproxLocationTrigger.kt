package com.paisetrail.app.enrich

import androidx.work.WorkManager
import javax.inject.Inject
import javax.inject.Singleton

/** Seam between [com.paisetrail.app.export.DataImporter] and the actual WorkManager enqueue, so
 * the importer doesn't depend on WorkManager (or need Robolectric to test) directly — fired when
 * a restored transaction carries a place name/locality but no coordinates. */
fun interface ApproxLocationTrigger {
    fun onMissingCoordinates(txnId: Long)
}

@Singleton
class WorkManagerApproxLocationTrigger @Inject constructor(
    private val workManager: WorkManager,
) : ApproxLocationTrigger {
    override fun onMissingCoordinates(txnId: Long) {
        ForwardGeocodeWorker.enqueue(workManager, txnId)
    }
}
