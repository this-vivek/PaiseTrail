package com.paisetrail.app.enrich

import com.paisetrail.app.data.db.MerchantDao
import com.paisetrail.app.data.db.MerchantEntity
import com.paisetrail.app.data.db.MerchantVpaEntity
import com.paisetrail.app.util.distanceMeters
import javax.inject.Inject
import javax.inject.Singleton

/**
 * VPA → merchant resolution with a learning loop (spec 4.2): exact VPA match first, else a fuzzy
 * name match against known merchants, else a new provisional merchant. A fuzzy match or a brand
 * new merchant both learn the VPA link so the *next* payment from the same VPA is an exact match.
 */
@Singleton
class MerchantResolver @Inject constructor(
    private val merchantDao: MerchantDao,
) {
    suspend fun resolve(payeeName: String?, vpa: String?): Long? {
        if (vpa != null) {
            merchantDao.getMerchantIdForVpa(vpa)?.let { return it }
        }

        if (!payeeName.isNullOrBlank()) {
            val bestMatch = merchantDao.getAll()
                .map { it to nameSimilarity(it.canonicalName, payeeName) }
                .filter { it.second >= FUZZY_MATCH_THRESHOLD }
                .maxByOrNull { it.second }
            if (bestMatch != null) {
                val merchant = bestMatch.first
                if (vpa != null) merchantDao.linkVpa(MerchantVpaEntity(vpa, merchant.id))
                return merchant.id
            }
        }

        if (payeeName.isNullOrBlank() && vpa == null) return null

        val newMerchantId = merchantDao.insert(
            MerchantEntity(
                canonicalName = payeeName ?: vpa!!,
                isOnline = OnlineMerchantHeuristic.isOnlineMerchant(payeeName, vpa),
            ),
        )
        if (vpa != null) merchantDao.linkVpa(MerchantVpaEntity(vpa, newMerchantId))
        return newMerchantId
    }

    suspend fun getDefaultCategoryId(merchantId: Long): Long? = merchantDao.getById(merchantId)?.defaultCategoryId

    /** Learning loop (spec 4.2): once the user confirms a category for any transaction resolved
     * to this merchant, every future payment to the same merchant auto-inherits it via
     * [CategoryGuesser] rule-cascade step 1 — called from wherever a tag gets confirmed
     * ([com.paisetrail.app.interaction.TagActionReceiver], Review Queue). */
    suspend fun learnCategory(merchantId: Long, categoryId: Long) {
        val merchant = merchantDao.getById(merchantId) ?: return
        merchantDao.update(merchant.copy(defaultCategoryId = categoryId))
    }

    /** When >= 3 in-person payments to a merchant cluster within 150m, set its home location as
     * the median — later payments with a bad GPS fix can snap to it (spec 4.2). Deliberately
     * cheap: median-of-cluster, not a real clustering algorithm — good enough for a single
     * physical location a personal expense tracker actually needs. */
    suspend fun maybeUpdateHomeLocation(merchantId: Long) {
        val points = merchantDao.getInPersonLocationsForMerchant(merchantId)
        if (points.size < MIN_POINTS_FOR_HOME) return

        val medianLat = points.map { it.lat }.sorted()[points.size / 2]
        val medianLng = points.map { it.lng }.sorted()[points.size / 2]
        val clustered = points.count { distanceMeters(it.lat, it.lng, medianLat, medianLng) <= CLUSTER_RADIUS_M }
        if (clustered < MIN_POINTS_FOR_HOME) return

        val merchant = merchantDao.getById(merchantId) ?: return
        merchantDao.update(merchant.copy(homeLat = medianLat, homeLng = medianLng))
    }

    /** Rough token-set similarity (Jaccard over normalized word tokens) — not the same algorithm
     * as a fuzzywuzzy-style token_set_ratio, but the same practical goal: order/duplicate-word
     * insensitive name comparison without pulling in a fuzzy-matching library for one use. */
    private fun nameSimilarity(a: String, b: String): Double {
        val tokensA = tokenize(a)
        val tokensB = tokenize(b)
        if (tokensA.isEmpty() || tokensB.isEmpty()) return 0.0
        val intersection = tokensA.intersect(tokensB).size
        val union = tokensA.union(tokensB).size
        return intersection.toDouble() / union
    }

    private fun tokenize(s: String): Set<String> =
        s.lowercase().split(Regex("[^a-z0-9]+")).filterTo(mutableSetOf()) { it.isNotBlank() }

    companion object {
        private const val FUZZY_MATCH_THRESHOLD = 0.85
        private const val MIN_POINTS_FOR_HOME = 3
        private const val CLUSTER_RADIUS_M = 150.0
    }
}
