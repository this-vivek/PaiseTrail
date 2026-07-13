package com.paisetrail.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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

/** The full category list as a bottom sheet — shared by everywhere a transaction gets (re)tagged
 * from scratch (Transactions list, transaction detail) so the funnel into
 * [com.paisetrail.app.enrich.TagConfirmationUseCase] always looks and behaves the same. */
@Composable
fun CategoryPickerSheet(
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
    header: (@Composable () -> Unit)? = null,
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
                .background(PaisaTheme.colors.surface, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
        ) {
            Column(modifier = Modifier.padding(PaisaSpacing.gutter)) {
                header?.invoke()
                Text(
                    text = "Tag as",
                    style = PaisaTheme.typography.overline,
                    color = PaisaTheme.colors.inkMuted,
                    modifier = Modifier.padding(top = if (header != null) PaisaSpacing.normal else 0.dp, bottom = PaisaSpacing.tight),
                )
                categories.forEach { category ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(category.name) }
                            .padding(vertical = PaisaSpacing.tight),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CategoryDot(category.colorHex, category.emoji)
                        Text(
                            text = category.name,
                            style = PaisaTheme.typography.body,
                            color = PaisaTheme.colors.ink,
                            modifier = Modifier.padding(start = PaisaSpacing.tight),
                        )
                    }
                }
            }
        }
    }
}
