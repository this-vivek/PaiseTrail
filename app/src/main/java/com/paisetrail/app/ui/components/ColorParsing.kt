package com.paisetrail.app.ui.components

import androidx.compose.ui.graphics.Color

/** Category colors are seeded as hex strings (data layer can't depend on ui.theme — see
 * [com.paisetrail.app.data.db.CategoryEntity]'s note) — this is the one place that parses them
 * back into a Compose [Color], falling back to grey for anything malformed or missing. */
fun parseCategoryColor(colorHex: String?): Color =
    colorHex?.let {
        try {
            Color(android.graphics.Color.parseColor(it))
        } catch (e: IllegalArgumentException) {
            Color.Gray
        }
    } ?: Color.Gray
