package com.paisetrail.app.trips

import com.paisetrail.app.testutil.FakeTripDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

private class FakeHomeLocationStore : HomeLocationStore {
    private var stored: HomeLocation? = null
    override fun get(): HomeLocation? = stored
    override fun set(lat: Double, lng: Double) {
        stored = HomeLocation(lat, lng)
    }
}

class TripManagerTest {
    private lateinit var tripDao: FakeTripDao
    private lateinit var manager: TripManager

    @Before
    fun setUp() {
        tripDao = FakeTripDao()
        manager = TripManager(tripDao, FakeHomeLocationStore())
    }

    @Test
    fun `starting a trip creates an active trip`() = runTest {
        val trip = manager.startTrip("Ladakh", startAt = 1_000L)

        assertEquals("Ladakh", trip.name)
        assertNull(trip.endAt)
        assertNotNull(manager.getActiveTrip())
    }

    @Test
    fun `starting a trip while one is active returns the existing trip instead of creating a new one`() = runTest {
        val first = manager.startTrip("Ladakh", startAt = 1_000L)
        val second = manager.startTrip("Goa", startAt = 2_000L)

        assertEquals(first.id, second.id)
        assertEquals("Ladakh", second.name)
        assertEquals(1, tripDao.trips.value.size)
    }

    @Test
    fun `ending a trip sets endAt and clears the active trip`() = runTest {
        manager.startTrip("Ladakh", startAt = 1_000L)
        manager.endTrip(endAt = 5_000L)

        assertNull(manager.getActiveTrip())
        assertEquals(5_000L, tripDao.trips.value.single().endAt)
    }

    @Test
    fun `ending a trip when none is active is a no-op`() = runTest {
        manager.endTrip(endAt = 5_000L)

        assertEquals(0, tripDao.trips.value.size)
    }

    @Test
    fun `a new trip can start again after the previous one ended`() = runTest {
        manager.startTrip("Ladakh", startAt = 1_000L)
        manager.endTrip(endAt = 5_000L)
        val second = manager.startTrip("Goa", startAt = 6_000L)

        assertEquals("Goa", second.name)
        assertEquals(2, tripDao.trips.value.size)
    }
}
