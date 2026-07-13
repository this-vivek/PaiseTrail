package com.paisetrail.app.data.db

/** Starter category list + colors (spec 6 / 7.7). The first 8 names get the 8 unique
 * `CategoryPalette` hues in order; Health/P2P Transfer/Uncategorized reuse grey/slate-blue per
 * the design system note that not every category needs a fully unique hue. Emoji are the
 * category "icon" (spec 7.7) — kept as data, not a hardcoded name->icon map in the UI layer, so
 * the category management screen can let a user pick a different one per category. */
object CategorySeed {
    private const val OLIVE = "#8A8B5C"
    private const val CLAY = "#B4715B"
    private const val SLATE_BLUE = "#5C7291"
    private const val SAND = "#BBA36A"
    private const val SAGE = "#7C9473"
    private const val PLUM = "#8B6A8E"
    private const val TEAL = "#5C8F8A"
    private const val GREY = "#8A8D93"

    private data class SeedCategory(val name: String, val colorHex: String, val emoji: String?)

    private val SEED = listOf(
        SeedCategory("Food", OLIVE, "🍔"),
        SeedCategory("Travel", CLAY, "✈️"),
        SeedCategory("Fuel", SLATE_BLUE, "⛽"),
        SeedCategory("Stay", SAND, "🏨"),
        SeedCategory("Shopping", SAGE, "🛍️"),
        SeedCategory("Groceries", PLUM, "🛒"),
        SeedCategory("Bills", TEAL, "🧾"),
        SeedCategory("Entertainment", GREY, "🎬"),
        SeedCategory("Health", SLATE_BLUE, "💊"),
        SeedCategory("P2P Transfer", GREY, "🤝"),
        SeedCategory("Uncategorized", GREY, null),
    )

    val DEFAULT_CATEGORIES = SEED.mapIndexed { index, seed ->
        CategoryEntity(name = seed.name, colorHex = seed.colorHex, emoji = seed.emoji, sortOrder = index)
    }

    /** (name, emoji) pairs for the one-time-per-launch backfill of existing installs (see
     * [CategoryDao.backfillEmojiIfMissing]) — [DEFAULT_CATEGORIES] alone can't drive that backfill
     * since re-seeding via [CategoryDao.insertAll] is a no-op once a name already exists. */
    val EMOJI_BACKFILL = SEED.mapNotNull { seed -> seed.emoji?.let { seed.name to it } }
}
