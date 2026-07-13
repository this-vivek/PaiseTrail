package com.paisetrail.app.ui.screens.merchants

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paisetrail.app.data.db.CategoryDao
import com.paisetrail.app.data.db.CategoryEntity
import com.paisetrail.app.data.db.MerchantDao
import com.paisetrail.app.data.db.MerchantEntity
import com.paisetrail.app.data.db.MerchantVpaEntity
import com.paisetrail.app.data.db.TransactionDao
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Backs the merchant management screen (spec 4.2/7.6) — define a merchant (and optionally link a
 * VPA to it) from the app instead of waiting for [com.paisetrail.app.enrich.MerchantResolver] to
 * learn one automatically from a real payment. */
@HiltViewModel
class MerchantManagementViewModel @Inject constructor(
    private val merchantDao: MerchantDao,
    private val transactionDao: TransactionDao,
    categoryDao: CategoryDao,
) : ViewModel() {
    val merchants: StateFlow<List<MerchantEntity>> = merchantDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories: StateFlow<List<CategoryEntity>> = categoryDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Returns an error message if the input is invalid, null if it saved. */
    fun save(id: Long?, canonicalName: String, defaultCategoryId: Long?, isOnline: Boolean, vpaToLink: String): String? {
        if (canonicalName.isBlank()) return "Enter a merchant name"

        viewModelScope.launch {
            val merchantId = if (id == null) {
                merchantDao.insert(
                    MerchantEntity(canonicalName = canonicalName.trim(), defaultCategoryId = defaultCategoryId, isOnline = isOnline),
                )
            } else {
                val existing = merchantDao.getById(id) ?: return@launch
                merchantDao.update(
                    existing.copy(canonicalName = canonicalName.trim(), defaultCategoryId = defaultCategoryId, isOnline = isOnline),
                )
                id
            }
            if (vpaToLink.isNotBlank()) {
                merchantDao.linkVpa(MerchantVpaEntity(vpaToLink.trim(), merchantId))
            }
        }
        return null
    }

    /** Transactions pointing at this merchant fall back to unresolved (spec: never leave a
     * dangling reference), same as category deletion. */
    fun delete(merchant: MerchantEntity) {
        viewModelScope.launch {
            transactionDao.clearMerchant(merchant.id)
            merchantDao.delete(merchant)
        }
    }
}
