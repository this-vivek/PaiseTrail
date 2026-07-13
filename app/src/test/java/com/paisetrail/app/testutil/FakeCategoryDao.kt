package com.paisetrail.app.testutil

import com.paisetrail.app.data.db.CategoryDao
import com.paisetrail.app.data.db.CategoryEntity
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory [CategoryDao] double — mirrors the unique-name-IGNORE seeding semantics of the real
 * DAO closely enough for tests that don't need real SQLite. */
class FakeCategoryDao : CategoryDao {
    val categories = MutableStateFlow<List<CategoryEntity>>(emptyList())
    private var nextId = 1L

    override suspend fun insertAll(categories: List<CategoryEntity>) {
        for (category in categories) {
            if (this.categories.value.any { it.name == category.name }) continue
            val id = nextId++
            this.categories.value = this.categories.value + category.copy(id = id)
        }
    }

    override suspend fun insert(category: CategoryEntity): Long {
        val id = nextId++
        categories.value = categories.value + category.copy(id = id)
        return id
    }

    override suspend fun update(category: CategoryEntity) {
        categories.value = categories.value.map { if (it.id == category.id) category else it }
    }

    override suspend fun delete(category: CategoryEntity) {
        categories.value = categories.value.filterNot { it.id == category.id }
    }

    override fun observeAll() = categories

    override suspend fun getAll(): List<CategoryEntity> = categories.value

    override suspend fun getByName(name: String): CategoryEntity? = categories.value.firstOrNull { it.name == name }

    override suspend fun getById(id: Long): CategoryEntity? = categories.value.firstOrNull { it.id == id }

    override suspend fun backfillEmojiIfMissing(name: String, emoji: String) {
        categories.value = categories.value.map {
            if (it.name == name && it.emoji == null) it.copy(emoji = emoji) else it
        }
    }
}
