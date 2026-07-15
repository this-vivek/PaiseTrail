package com.paisetrail.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(categories: List<CategoryEntity>)

    /** User-created categories from the category management screen (spec 7.6/7.7). */
    @Insert
    suspend fun insert(category: CategoryEntity): Long

    @Update
    suspend fun update(category: CategoryEntity)

    @Delete
    suspend fun delete(category: CategoryEntity)

    @Query("SELECT * FROM categories ORDER BY sortOrder ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY sortOrder ASC")
    suspend fun getAll(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): CategoryEntity?

    @Query("SELECT * FROM categories WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): CategoryEntity?

    /** Seeded emoji icons (spec 7.7 "category icon") applied once per category name, and only if
     * nothing's there yet — a user who customizes a category's icon via the management screen
     * must not have it silently overwritten by the next app launch's re-seed. */
    @Query("UPDATE categories SET emoji = :emoji WHERE name = :name AND emoji IS NULL")
    suspend fun backfillEmojiIfMissing(name: String, emoji: String)

    /** One-time correction for installs seeded before Health/P2P Transfer got their own distinct
     * colors (see [CategorySeed]) — sharing the same grey as Uncategorized was a real ambiguity
     * once the pie chart went from a thin ring to bold filled wedges. Only touches a category
     * still on that old shared grey, so a user's own recolor via the management screen is
     * untouched — same "don't overwrite a customization" guard as [backfillEmojiIfMissing]. */
    @Query("UPDATE categories SET colorHex = :colorHex WHERE name = :name AND colorHex = '#9AA0B0'")
    suspend fun backfillColorIfDefault(name: String, colorHex: String)
}
