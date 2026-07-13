package com.paisetrail.app.ui.screens.banks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paisetrail.app.data.db.BankSmsPatternDao
import com.paisetrail.app.data.db.BankSmsPatternEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Backs the bank SMS pattern management screen (spec 3.2 "data, not code") — add/edit/delete a
 * bank's SMS regex from the app instead of a code change + release. [BankSmsPatternRegistry]
 * re-queries [BankSmsPatternDao.getEnabled] on every SMS, so an edit here applies to the very
 * next incoming message, no restart needed. */
@HiltViewModel
class BankPatternManagementViewModel @Inject constructor(
    private val dao: BankSmsPatternDao,
) : ViewModel() {
    val patterns: StateFlow<List<BankSmsPatternEntity>> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** [existingId] is null when adding a brand new pattern (Room autogenerates an id) — non-null
     * re-saves the same row in place (a bank can have more than one pattern, so this can no longer
     * key off bankId alone; see [BankSmsPatternEntity]). Returns an error message if the input is
     * invalid, null if it saved. */
    fun save(existingId: Long?, bankId: String, senderSuffix: String, regex: String, enabled: Boolean): String? {
        if (bankId.isBlank()) return "Enter a bank id"
        if (senderSuffix.isBlank()) return "Enter the SMS sender suffix (e.g. HDFCBK)"
        if (regex.isBlank()) return "Enter a regex pattern"
        try {
            Regex(regex)
        } catch (e: Exception) {
            return "Invalid regex: ${e.message}"
        }
        if (!regex.contains("(?<amount>")) return "Regex must have a named group (?<amount>...)"

        viewModelScope.launch {
            dao.upsert(
                BankSmsPatternEntity(
                    bankId = bankId.trim(),
                    senderSuffix = senderSuffix.trim(),
                    regex = regex.trim(),
                    enabled = enabled,
                    id = existingId ?: 0,
                ),
            )
        }
        return null
    }

    fun delete(pattern: BankSmsPatternEntity) {
        viewModelScope.launch { dao.delete(pattern) }
    }

    fun setEnabled(pattern: BankSmsPatternEntity, enabled: Boolean) {
        viewModelScope.launch { dao.upsert(pattern.copy(enabled = enabled)) }
    }
}
