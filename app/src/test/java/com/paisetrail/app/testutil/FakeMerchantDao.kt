package com.paisetrail.app.testutil

import com.paisetrail.app.data.db.LatLngRow
import com.paisetrail.app.data.db.MerchantDao
import com.paisetrail.app.data.db.MerchantEntity
import com.paisetrail.app.data.db.MerchantVpaEntity
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory [MerchantDao] double — enough of the real semantics (VPA links, name lookup) for
 * tests that don't need real SQLite. */
class FakeMerchantDao : MerchantDao {
    val merchants = MutableStateFlow<List<MerchantEntity>>(emptyList())
    private val vpaLinks = mutableMapOf<String, Long>()
    private var nextId = 1L

    override suspend fun insert(merchant: MerchantEntity): Long {
        val id = nextId++
        merchants.value = merchants.value + merchant.copy(id = id)
        return id
    }

    override suspend fun update(merchant: MerchantEntity) {
        merchants.value = merchants.value.map { if (it.id == merchant.id) merchant else it }
    }

    override suspend fun delete(merchant: MerchantEntity) {
        merchants.value = merchants.value.filterNot { it.id == merchant.id }
    }

    override suspend fun getById(id: Long): MerchantEntity? = merchants.value.firstOrNull { it.id == id }

    override suspend fun getAll(): List<MerchantEntity> = merchants.value

    override fun observeAll() = merchants

    override suspend fun getByName(canonicalName: String): MerchantEntity? =
        merchants.value.firstOrNull { it.canonicalName == canonicalName }

    override suspend fun linkVpa(link: MerchantVpaEntity) {
        vpaLinks[link.vpa] = link.merchantId
    }

    override suspend fun getMerchantIdForVpa(vpa: String): Long? = vpaLinks[vpa]

    override suspend fun getInPersonLocationsForMerchant(merchantId: Long): List<LatLngRow> = emptyList()
}
