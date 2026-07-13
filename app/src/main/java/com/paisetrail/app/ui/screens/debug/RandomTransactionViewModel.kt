package com.paisetrail.app.ui.screens.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paisetrail.app.data.db.CategoryDao
import com.paisetrail.app.debug.DummyDraft
import com.paisetrail.app.debug.DummyTransactionSeeder
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DraftPreview(val draft: DummyDraft, val categoryColorHex: String?, val categoryEmoji: String?)

/** Backs the "add random transaction" screen (spec 7.6 debug tools) — a draft can be re-rolled
 * any number of times before it's actually inserted. */
@HiltViewModel
class RandomTransactionViewModel @Inject constructor(
    private val seeder: DummyTransactionSeeder,
    categoryDao: CategoryDao,
) : ViewModel() {
    private val draft = MutableStateFlow(seeder.randomDraft())

    val preview: StateFlow<DraftPreview> = combine(draft, categoryDao.observeAll()) { d, categories ->
        val category = categories.firstOrNull { it.name == d.categoryName }
        DraftPreview(d, category?.colorHex, category?.emoji)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DraftPreview(draft.value, null, null))

    fun regenerate() {
        draft.value = seeder.randomDraft()
    }

    fun confirm(onDone: () -> Unit) {
        viewModelScope.launch {
            seeder.insertDraft(draft.value)
            onDone()
        }
    }
}
