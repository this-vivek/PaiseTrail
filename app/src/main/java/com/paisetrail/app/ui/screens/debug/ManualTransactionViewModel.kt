package com.paisetrail.app.ui.screens.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paisetrail.app.data.db.CategoryDao
import com.paisetrail.app.data.db.CategoryEntity
import com.paisetrail.app.debug.DummyTransactionSeeder
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Backs the manual/custom transaction entry screen (spec 7.6 debug tools) — same insertion path
 * ([DummyTransactionSeeder.insert]) as the random-draft screen, so location stamping, trip
 * tagging, and self-transfer detection apply here too. */
@HiltViewModel
class ManualTransactionViewModel @Inject constructor(
    private val seeder: DummyTransactionSeeder,
    categoryDao: CategoryDao,
) : ViewModel() {
    val categories: StateFlow<List<CategoryEntity>> = categoryDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Returns an error message if the input is invalid, null if the transaction was submitted. */
    fun submit(payeeName: String, categoryId: Long?, amountRupeesText: String, occurredAt: Long, onDone: () -> Unit): String? {
        if (payeeName.isBlank()) return "Enter who this payment was to"
        val amountPaise = amountRupeesText.trim().toDoubleOrNull()?.let { (it * 100).toLong() }
        if (amountPaise == null || amountPaise <= 0) return "Enter a valid amount"

        viewModelScope.launch {
            seeder.insert(payeeName.trim(), categoryId, amountPaise, occurredAt)
            onDone()
        }
        return null
    }
}
