package com.paisetrail.app.ui.screens.trips

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.paisetrail.app.data.db.TransactionDao
import com.paisetrail.app.data.db.TripDao
import com.paisetrail.app.data.db.TripEntity
import com.paisetrail.app.trips.HomeLocationStore
import com.paisetrail.app.trips.TripManager
import com.paisetrail.app.util.awaitOrNull
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TripSummary(val trip: TripEntity, val totalPaise: Long, val transactionCount: Int)

@HiltViewModel
class TripsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tripManager: TripManager,
    private val tripDao: TripDao,
    private val transactionDao: TransactionDao,
    private val homeLocationStore: HomeLocationStore,
    private val fusedLocationClient: FusedLocationProviderClient,
) : ViewModel() {
    val activeTrip: StateFlow<TripEntity?> = tripDao.observeActiveTrip()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val pastTrips: StateFlow<List<TripSummary>> = tripDao.observeAll()
        .map { trips -> trips.filter { it.endAt != null } }
        .combine(transactionDao.observeTripSpend()) { trips, spend ->
            val spendByTripId = spend.associateBy { it.tripId }
            trips.map { trip ->
                val row = spendByTripId[trip.id]
                TripSummary(trip, row?.amountPaise ?: 0L, row?.count ?: 0)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _hasHomeLocation = MutableStateFlow(homeLocationStore.get() != null)
    val hasHomeLocation: StateFlow<Boolean> = _hasHomeLocation

    fun startTrip(name: String) {
        viewModelScope.launch { tripManager.startTrip(name.ifBlank { "Trip" }, System.currentTimeMillis()) }
    }

    fun endTrip() {
        viewModelScope.launch { tripManager.endTrip(System.currentTimeMillis()) }
    }

    /** Unlinks the trip's transactions (they fall back into the regular Transactions list) then
     * deletes the trip itself (spec 7.4 "delete trip"). */
    fun deleteTrip(trip: TripEntity) {
        viewModelScope.launch {
            transactionDao.clearTrip(trip.id)
            tripDao.delete(trip)
        }
    }

    @SuppressLint("MissingPermission")
    fun setHomeLocationFromCurrentFix() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        viewModelScope.launch {
            val location = fusedLocationClient.lastLocation.awaitOrNull() ?: return@launch
            homeLocationStore.set(location.latitude, location.longitude)
            _hasHomeLocation.value = true
        }
    }
}
