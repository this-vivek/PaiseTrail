package com.paisetrail.app.util

import android.util.Log
import com.google.android.gms.tasks.Task
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/** Shared by anything that needs a one-shot Play Services [Task] as a suspend result — location
 * fixes in [com.paisetrail.app.enrich.LocationStampingUseCase] and setting home location in
 * [com.paisetrail.app.ui.screens.trips.TripsViewModel]. */
suspend fun <T> Task<T>.awaitOrNull(): T? = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { result ->
        if (cont.isActive) cont.resume(result)
    }
    addOnFailureListener { e ->
        Log.w("TaskExtensions", "task failed", e)
        if (cont.isActive) cont.resume(null)
    }
    addOnCanceledListener {
        if (cont.isActive) cont.resume(null)
    }
}
