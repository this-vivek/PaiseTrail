package com.paisetrail.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.paisetrail.app.data.db.CategoryEntity
import com.paisetrail.app.ui.theme.PaisaSpacing
import com.paisetrail.app.ui.theme.PaisaTheme
import com.paisetrail.app.ui.theme.SheetShape
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.HazeMaterials

/** The full category list as a bottom sheet — shared by everywhere a transaction gets (re)tagged
 * from scratch (Transactions list, transaction detail) so the funnel into
 * [com.paisetrail.app.enrich.TagConfirmationUseCase] always looks and behaves the same. A grid of
 * tiles rather than a list of rows; the currently selected category (if any) gets an accent ring.
 * [hazeState] is optional — pass the screen's own state (sourced from its scrolling content) to
 * get real backdrop blur; omit it for a plain surface1 sheet until that screen is migrated. */
@Composable
fun CategoryPickerSheet(
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
    header: (@Composable () -> Unit)? = null,
    selectedCategoryName: String? = null,
    hazeState: HazeState? = null,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x99000000))
                .clickable(onClick = onDismiss),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .let { m ->
                    if (hazeState != null) {
                        m.hazeEffect(state = hazeState, style = HazeMaterials.thin(PaisaTheme.colors.surfaceGlass))
                    } else {
                        m.background(PaisaTheme.colors.surface1, SheetShape)
                    }
                },
        ) {
            Column(modifier = Modifier.padding(PaisaSpacing.gutter)) {
                header?.invoke()
                Text(
                    text = "Tag as",
                    style = PaisaTheme.typography.label,
                    color = PaisaTheme.colors.inkMuted,
                    modifier = Modifier.padding(top = if (header != null) PaisaSpacing.normal else 0.dp, bottom = PaisaSpacing.tight),
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxWidth().height(((categories.size / 3 + 1) * 92).dp),
                ) {
                    items(categories, key = { it.id }) { category ->
                        CategoryTile(
                            category = category,
                            selected = category.name == selectedCategoryName,
                            onClick = { onPick(category.name) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryTile(category: CategoryEntity, selected: Boolean, onClick: () -> Unit) {
    val color = parseCategoryColor(category.colorHex)
    Column(
        modifier = Modifier
            .padding(6.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .background(color.copy(alpha = 0.14f), RoundedCornerShape(16.dp))
                .let {
                    if (selected) it.border(2.dp, PaisaTheme.colors.accent, RoundedCornerShape(16.dp)) else it
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = category.emoji ?: category.name.take(1).uppercase(),
                style = PaisaTheme.typography.title,
                color = color,
            )
        }
        Text(
            text = category.name,
            style = PaisaTheme.typography.caption,
            color = PaisaTheme.colors.ink,
            maxLines = 1,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
