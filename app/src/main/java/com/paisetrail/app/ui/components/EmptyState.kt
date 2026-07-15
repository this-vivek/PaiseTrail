package com.paisetrail.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.paisetrail.app.ui.theme.PaisaSpacing
import com.paisetrail.app.ui.theme.PaisaTheme

/** Every screen's "nothing here yet" state — a fresh install or an empty filter should never
 * look broken (spec §3.9). Replaces bare one-line "No data" text with a consistent, slightly
 * more inviting treatment; still no illustrations/Lottie, just an outlined icon. */
@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.Inbox,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(PaisaSpacing.loose),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PaisaSpacing.tight),
    ) {
        Box(
            modifier = Modifier.size(48.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = PaisaTheme.colors.inkFaint, modifier = Modifier.size(32.dp))
        }
        Text(text = title, style = PaisaTheme.typography.bodyBold, color = PaisaTheme.colors.ink)
        Text(text = body, style = PaisaTheme.typography.caption, color = PaisaTheme.colors.inkMuted)
        if (actionLabel != null && onAction != null) {
            Text(
                text = actionLabel,
                style = PaisaTheme.typography.bodyBold,
                color = PaisaTheme.colors.accent,
                modifier = Modifier
                    .padding(top = PaisaSpacing.tight)
                    .clickable(onClick = onAction),
            )
        }
    }
}
