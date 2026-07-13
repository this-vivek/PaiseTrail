package com.paisetrail.app.ui.screens.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paisetrail.app.data.db.CategoryDao
import com.paisetrail.app.data.db.CategoryEntity
import com.paisetrail.app.data.db.TransactionDao
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Backs the category management screen (spec 7.7 "category icon") — rename, recolor, re-icon,
 * add, and delete categories. The rule cascade / dashboard / map all read from the same
 * `categories` table, so an edit here is visible everywhere without any other wiring. */
@HiltViewModel
class CategoryManagementViewModel @Inject constructor(
    private val categoryDao: CategoryDao,
    private val transactionDao: TransactionDao,
) : ViewModel() {
    val categories: StateFlow<List<CategoryEntity>> = categoryDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(id: Long?, name: String, emoji: String?, colorHex: String) {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return
        viewModelScope.launch {
            if (id == null) {
                categoryDao.insert(
                    CategoryEntity(
                        name = trimmedName,
                        emoji = emoji?.trim()?.ifBlank { null },
                        colorHex = colorHex,
                        sortOrder = categories.value.size,
                    ),
                )
            } else {
                val existing = categoryDao.getById(id) ?: return@launch
                categoryDao.update(existing.copy(name = trimmedName, emoji = emoji?.trim()?.ifBlank { null }, colorHex = colorHex))
            }
        }
    }

    /** Transactions pointing at this category are cleared back to Uncategorized (spec: never
     * leave a dangling reference) rather than deleted along with the category. */
    fun delete(category: CategoryEntity) {
        viewModelScope.launch {
            transactionDao.clearCategory(category.id)
            categoryDao.delete(category)
        }
    }
}
