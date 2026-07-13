package com.paisetrail.app.ui.screens.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.paisetrail.app.data.db.CategoryEntity
import com.paisetrail.app.ui.components.CategoryDot
import com.paisetrail.app.ui.theme.CategoryPalette
import com.paisetrail.app.ui.theme.PaisaShape
import com.paisetrail.app.ui.theme.PaisaSpacing
import com.paisetrail.app.ui.theme.PaisaTheme

private val PALETTE = listOf(
    "#8A8B5C" to CategoryPalette.olive,
    "#B4715B" to CategoryPalette.clay,
    "#5C7291" to CategoryPalette.slateBlue,
    "#BBA36A" to CategoryPalette.sand,
    "#7C9473" to CategoryPalette.sage,
    "#8B6A8E" to CategoryPalette.plum,
    "#5C8F8A" to CategoryPalette.teal,
    "#8A8D93" to CategoryPalette.grey,
)

/** Rename, recolor, re-icon, add, or delete a category (spec 7.7 "category icon") — reachable
 * from Settings. Every screen that reads `categories` (Dashboard, Transactions, Map, Trips) picks
 * up an edit immediately since they all observe the same table. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryManagementScreen(onBack: () -> Unit, viewModel: CategoryManagementViewModel = hiltViewModel()) {
    val categories by viewModel.categories.collectAsState()
    var addingNew by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Categories", style = PaisaTheme.typography.body) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { addingNew = !addingNew }) {
                        Icon(Icons.Outlined.Add, contentDescription = "Add category")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PaisaTheme.colors.bg,
                    titleContentColor = PaisaTheme.colors.ink,
                    navigationIconContentColor = PaisaTheme.colors.ink,
                    actionIconContentColor = PaisaTheme.colors.accent,
                ),
            )
        },
        containerColor = PaisaTheme.colors.bg,
    ) { innerPadding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (addingNew) {
                item(key = "new") {
                    Column(modifier = Modifier.fillMaxWidth().padding(PaisaSpacing.gutter)) {
                        CategoryEditForm(
                            initial = null,
                            onSave = { name, emoji, colorHex ->
                                viewModel.save(null, name, emoji, colorHex)
                                addingNew = false
                            },
                            onCancel = { addingNew = false },
                            onDelete = null,
                        )
                    }
                    HorizontalDivider(color = PaisaTheme.colors.hairline, thickness = 1.dp)
                }
            }
            items(categories, key = { it.id }) { category ->
                CategoryRow(category, viewModel)
                HorizontalDivider(color = PaisaTheme.colors.hairline, thickness = 1.dp)
            }
        }
    }
}

@Composable
private fun CategoryRow(category: CategoryEntity, viewModel: CategoryManagementViewModel) {
    var expanded by remember(category.id) { mutableStateOf(false) }
    var showDeleteConfirm by remember(category.id) { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete \"${category.name}\"?", style = PaisaTheme.typography.body) },
            text = {
                Text(
                    "Transactions tagged with this category go back to Uncategorized. This cannot be undone.",
                    style = PaisaTheme.typography.bodySecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; viewModel.delete(category) }) {
                    Text("Delete", color = PaisaTheme.colors.negative)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(horizontal = PaisaSpacing.gutter, vertical = PaisaSpacing.normal),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryDot(category.colorHex, category.emoji)
            Text(
                text = category.name,
                style = PaisaTheme.typography.body,
                color = PaisaTheme.colors.ink,
                modifier = Modifier.padding(start = PaisaSpacing.tight),
            )
        }
        if (expanded) {
            CategoryEditForm(
                initial = category,
                onSave = { name, emoji, colorHex ->
                    viewModel.save(category.id, name, emoji, colorHex)
                    expanded = false
                },
                onCancel = { expanded = false },
                onDelete = { showDeleteConfirm = true },
            )
        }
    }
}

@Composable
private fun CategoryEditForm(
    initial: CategoryEntity?,
    onSave: (name: String, emoji: String?, colorHex: String) -> Unit,
    onCancel: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var emoji by remember { mutableStateOf(initial?.emoji ?: "") }
    var colorHex by remember { mutableStateOf(initial?.colorHex ?: PALETTE.first().first) }

    Column(modifier = Modifier.fillMaxWidth().padding(top = PaisaSpacing.tight)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = emoji,
                onValueChange = { if (it.length <= 2) emoji = it },
                textStyle = TextStyle(color = PaisaTheme.colors.ink, fontSize = PaisaTheme.typography.body.fontSize),
                modifier = Modifier
                    .width(44.dp)
                    .background(PaisaTheme.colors.surface, PaisaShape)
                    .padding(PaisaSpacing.tight),
            )
            Spacer(Modifier.width(PaisaSpacing.tight))
            BasicTextField(
                value = name,
                onValueChange = { name = it },
                textStyle = TextStyle(color = PaisaTheme.colors.ink, fontSize = PaisaTheme.typography.body.fontSize),
                modifier = Modifier
                    .weight(1f)
                    .background(PaisaTheme.colors.surface, PaisaShape)
                    .padding(PaisaSpacing.tight),
                decorationBox = { inner ->
                    if (name.isEmpty()) {
                        Text("Category name", style = PaisaTheme.typography.body, color = PaisaTheme.colors.inkMuted)
                    }
                    inner()
                },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = PaisaSpacing.tight),
            horizontalArrangement = Arrangement.spacedBy(PaisaSpacing.tight),
        ) {
            PALETTE.forEach { (hex, color) ->
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(color, CircleShape)
                        .let {
                            if (hex == colorHex) {
                                it.border(2.dp, PaisaTheme.colors.ink, CircleShape)
                            } else {
                                it
                            }
                        }
                        .clickable { colorHex = hex },
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = PaisaSpacing.normal),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onDelete != null) {
                Text(
                    text = "Delete",
                    style = PaisaTheme.typography.body,
                    color = PaisaTheme.colors.negative,
                    modifier = Modifier.clickable(onClick = onDelete),
                )
            } else {
                Spacer(Modifier.width(1.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(PaisaSpacing.loose)) {
                Text(
                    text = "Cancel",
                    style = PaisaTheme.typography.body,
                    color = PaisaTheme.colors.inkMuted,
                    modifier = Modifier.clickable(onClick = onCancel),
                )
                Text(
                    text = "Save",
                    style = PaisaTheme.typography.body,
                    color = PaisaTheme.colors.accent,
                    modifier = Modifier.clickable { onSave(name, emoji, colorHex) },
                )
            }
        }
    }
}
