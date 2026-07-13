package com.paisetrail.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Seed list + colors: Food, Travel, Fuel, Stay, Shopping, Groceries, Bills, Entertainment,
 * Health, P2P Transfer, Uncategorized (spec 6). [colorHex] values are literal duplicates of
 * `ui.theme.CategoryPalette` — the data layer can't depend on the ui package, and category
 * colors are seeded data rather than a compile-time constant anyway. Unique [name] index lets
 * re-seeding on every app launch stay idempotent via OnConflictStrategy.IGNORE, same as
 * [BankSmsPatternEntity]. */
@Entity(tableName = "categories", indices = [Index(value = ["name"], unique = true)])
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String? = null,
    val colorHex: String,
    val parentId: Long? = null,
    val sortOrder: Int = 0,
)
