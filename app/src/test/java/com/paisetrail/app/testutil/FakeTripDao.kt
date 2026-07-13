package com.paisetrail.app.testutil

import com.paisetrail.app.data.db.TripDao
import com.paisetrail.app.data.db.TripEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory [TripDao] double shared by tests that don't need real SQLite. */
class FakeTripDao : TripDao {
    val trips = MutableStateFlow<List<TripEntity>>(emptyList())
    private var nextId = 1L

    override suspend fun insert(trip: TripEntity): Long {
        val id = nextId++
        trips.value = trips.value + trip.copy(id = id)
        return id
    }

    override suspend fun update(trip: TripEntity) {
        trips.value = trips.value.map { if (it.id == trip.id) trip else it }
    }

    override suspend fun delete(trip: TripEntity) {
        trips.value = trips.value.filter { it.id != trip.id }
    }

    override suspend fun getById(id: Long): TripEntity? = trips.value.firstOrNull { it.id == id }

    override suspend fun getActiveTrip(): TripEntity? = trips.value.firstOrNull { it.endAt == null }

    override fun observeActiveTrip() = trips.map { list -> list.firstOrNull { it.endAt == null } }

    override fun observeAll() = trips.map { it.sortedByDescending { trip -> trip.startAt } }
}
