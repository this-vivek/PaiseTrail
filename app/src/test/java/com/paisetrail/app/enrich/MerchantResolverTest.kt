package com.paisetrail.app.enrich

import com.paisetrail.app.data.db.LatLngRow
import com.paisetrail.app.data.db.MerchantDao
import com.paisetrail.app.data.db.MerchantEntity
import com.paisetrail.app.data.db.MerchantVpaEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class FakeMerchantDao : MerchantDao {
    val merchants = mutableMapOf<Long, MerchantEntity>()
    val vpaLinks = mutableMapOf<String, Long>()
    var inPersonLocations: List<LatLngRow> = emptyList()
    private var nextId = 1L

    override suspend fun insert(merchant: MerchantEntity): Long {
        val id = nextId++
        merchants[id] = merchant.copy(id = id)
        return id
    }

    override suspend fun update(merchant: MerchantEntity) {
        merchants[merchant.id] = merchant
    }

    override suspend fun delete(merchant: MerchantEntity) {
        merchants.remove(merchant.id)
    }

    override suspend fun getById(id: Long): MerchantEntity? = merchants[id]

    override suspend fun getAll(): List<MerchantEntity> = merchants.values.toList()

    override fun observeAll() = kotlinx.coroutines.flow.flowOf(merchants.values.toList())

    override suspend fun getByName(canonicalName: String): MerchantEntity? =
        merchants.values.firstOrNull { it.canonicalName == canonicalName }

    override suspend fun linkVpa(link: MerchantVpaEntity) {
        vpaLinks[link.vpa] = link.merchantId
    }

    override suspend fun getMerchantIdForVpa(vpa: String): Long? = vpaLinks[vpa]

    override suspend fun getInPersonLocationsForMerchant(merchantId: Long): List<LatLngRow> = inPersonLocations
}

class MerchantResolverTest {
    private lateinit var dao: FakeMerchantDao
    private lateinit var resolver: MerchantResolver

    @Before
    fun setUp() {
        dao = FakeMerchantDao()
        resolver = MerchantResolver(dao)
    }

    @Test
    fun `exact VPA match returns existing merchant without creating a new one`() = runTest {
        val id = resolver.resolve("Sharma Tea Stall", "sharmatea@ybl")
        val idAgain = resolver.resolve(null, "sharmatea@ybl")

        assertEquals(id, idAgain)
        assertEquals(1, dao.merchants.size)
    }

    @Test
    fun `no existing merchant creates a provisional one and learns the VPA`() = runTest {
        val id = resolver.resolve("Sharma Tea Stall", "sharmatea@ybl")

        assertNotNull(id)
        assertEquals("Sharma Tea Stall", dao.merchants[id]?.canonicalName)
        assertEquals(id, dao.vpaLinks["sharmatea@ybl"])
    }

    @Test
    fun `fuzzy name match reuses an existing merchant for a new VPA`() = runTest {
        val firstId = resolver.resolve("Sharma Tea Stall", "sharmatea@ybl")
        val secondId = resolver.resolve("Sharma Tea Stall", "sharmatea@newbank")

        assertEquals(firstId, secondId)
        assertEquals(1, dao.merchants.size)
        assertEquals(firstId, dao.vpaLinks["sharmatea@newbank"])
    }

    @Test
    fun `dissimilar name does not fuzzy match and creates a new merchant`() = runTest {
        val firstId = resolver.resolve("Sharma Tea Stall", "sharmatea@ybl")
        val secondId = resolver.resolve("Ola Cabs", "ola@ybl")

        assertTrue(firstId != secondId)
        assertEquals(2, dao.merchants.size)
    }

    @Test
    fun `resolve returns null when both payee name and vpa are missing`() = runTest {
        assertNull(resolver.resolve(null, null))
    }

    @Test
    fun `new online merchant is marked isOnline`() = runTest {
        val id = resolver.resolve("Amazon Pay", "amazon@apl")
        assertTrue(dao.merchants[id]?.isOnline == true)
    }

    @Test
    fun `learnCategory sets the merchant default category`() = runTest {
        val id = resolver.resolve("Sharma Tea Stall", "sharmatea@ybl")!!
        resolver.learnCategory(id, categoryId = 42L)

        assertEquals(42L, dao.merchants[id]?.defaultCategoryId)
        assertEquals(42L, resolver.getDefaultCategoryId(id))
    }

    @Test
    fun `home location is set once 3 in-person points cluster within 150m`() = runTest {
        val id = resolver.resolve("Sharma Tea Stall", "sharmatea@ybl")!!
        dao.inPersonLocations = listOf(
            LatLngRow(28.6139, 77.2090),
            LatLngRow(28.6140, 77.2091),
            LatLngRow(28.6138, 77.2089),
        )

        resolver.maybeUpdateHomeLocation(id)

        val merchant = dao.merchants[id]
        assertNotNull(merchant?.homeLat)
        assertNotNull(merchant?.homeLng)
    }

    @Test
    fun `home location is not set with fewer than 3 points`() = runTest {
        val id = resolver.resolve("Sharma Tea Stall", "sharmatea@ybl")!!
        dao.inPersonLocations = listOf(LatLngRow(28.6139, 77.2090), LatLngRow(28.6140, 77.2091))

        resolver.maybeUpdateHomeLocation(id)

        assertNull(dao.merchants[id]?.homeLat)
    }

    @Test
    fun `home location is not set when points are scattered beyond the cluster radius`() = runTest {
        val id = resolver.resolve("Sharma Tea Stall", "sharmatea@ybl")!!
        // Roughly a few km apart — well beyond the 150m cluster radius.
        dao.inPersonLocations = listOf(
            LatLngRow(28.6139, 77.2090),
            LatLngRow(28.7139, 77.3090),
            LatLngRow(28.9139, 77.5090),
        )

        resolver.maybeUpdateHomeLocation(id)

        assertNull(dao.merchants[id]?.homeLat)
    }
}
