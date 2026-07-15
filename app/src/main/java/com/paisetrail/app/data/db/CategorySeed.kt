package com.paisetrail.app.data.db

/** Starter category list + colors (spec 6 / 7.7). The first 8 names get the 8 unique
 * `CategoryPalette` hues in order; Health/P2P Transfer/Uncategorized each get their own muted,
 * desaturated tone — distinguishable from each other and from the 8 vivid hues above, but quieter
 * since none of the three is a "main" spend category the way Food or Shopping is. Emoji are the
 * category "icon" (spec 7.7) — kept as data, not a hardcoded name->icon map in the UI layer, so
 * the category management screen can let a user pick a different one per category. */
object CategorySeed {
    private const val AMBER = "#E0A458"
    private const val CORAL = "#E07A5F"
    private const val AZURE = "#5B8DEF"
    private const val GOLD = "#CDB04E"
    private const val EMERALD = "#57B894"
    private const val LILAC = "#A78BDB"
    private const val CYAN = "#4FBBD1"
    private const val ROSE = "#D46A9B"
    private const val MUTED_SAGE = "#5F9E7E"
    private const val MUTED_SLATE = "#6C7BC4"
    private const val GREY = "#787C85"

    private data class SeedCategory(val name: String, val colorHex: String, val emoji: String?)

    private val SEED = listOf(
        SeedCategory("Food", AMBER, "🍔"),
        SeedCategory("Travel", CORAL, "✈️"),
        SeedCategory("Fuel", AZURE, "⛽"),
        SeedCategory("Stay", GOLD, "🏨"),
        SeedCategory("Shopping", EMERALD, "🛍️"),
        SeedCategory("Groceries", LILAC, "🛒"),
        SeedCategory("Bills", CYAN, "🧾"),
        SeedCategory("Entertainment", ROSE, "🎬"),
        SeedCategory("Health", MUTED_SAGE, "💊"),
        SeedCategory("P2P Transfer", MUTED_SLATE, "🤝"),
        SeedCategory("Uncategorized", GREY, "❓"),
    )

    val DEFAULT_CATEGORIES = SEED.mapIndexed { index, seed ->
        CategoryEntity(name = seed.name, colorHex = seed.colorHex, emoji = seed.emoji, sortOrder = index)
    }

    /** (name, emoji) pairs for the one-time-per-launch backfill of existing installs (see
     * [CategoryDao.backfillEmojiIfMissing]) — [DEFAULT_CATEGORIES] alone can't drive that backfill
     * since re-seeding via [CategoryDao.insertAll] is a no-op once a name already exists. */
    val EMOJI_BACKFILL = SEED.mapNotNull { seed -> seed.emoji?.let { seed.name to it } }

    /** (name, colorHex) pairs for the one-time-per-launch correction of installs seeded before
     * Health/P2P Transfer had their own colors (see [CategoryDao.backfillColorIfDefault]). */
    val COLOR_BACKFILL = listOf("Health" to MUTED_SAGE, "P2P Transfer" to MUTED_SLATE)
}
