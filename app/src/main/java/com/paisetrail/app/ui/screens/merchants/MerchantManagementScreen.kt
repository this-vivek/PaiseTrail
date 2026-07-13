package com.paisetrail.app.ui.screens.merchants

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.paisetrail.app.data.db.MerchantEntity
import com.paisetrail.app.ui.components.CategoryDot
import com.paisetrail.app.ui.components.CategoryPickerSheet
import com.paisetrail.app.ui.theme.PaisaShape
import com.paisetrail.app.ui.theme.PaisaSpacing
import com.paisetrail.app.ui.theme.PaisaTheme

/** Define a merchant (and optionally link a VPA to it) from the app instead of waiting for it to
 * be learned automatically (spec 4.2/7.6). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MerchantManagementScreen(onBack: () -> Unit, viewModel: MerchantManagementViewModel = hiltViewModel()) {
    val merchants by viewModel.merchants.collectAsState()
    val categories by viewModel.categories.collectAsState()
    var addingNew by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Merchants", style = PaisaTheme.typography.body) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { addingNew = !addingNew }) {
                        Icon(Icons.Outlined.Add, contentDescription = "Add merchant")
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
                        MerchantEditForm(
                            initial = null,
                            categories = categories,
                            onSave = { name, categoryId, isOnline, vpa ->
                                val error = viewModel.save(null, name, categoryId, isOnline, vpa)
                                if (error == null) addingNew = false
                                error
                            },
                            onCancel = { addingNew = false },
                            onDelete = null,
                        )
                    }
                    HorizontalDivider(color = PaisaTheme.colors.hairline, thickness = 1.dp)
                }
            }
            items(merchants, key = { it.id }) { merchant ->
                MerchantRow(merchant, categories, viewModel)
                HorizontalDivider(color = PaisaTheme.colors.hairline, thickness = 1.dp)
            }
        }
    }
}

@Composable
private fun MerchantRow(
    merchant: MerchantEntity,
    categories: List<com.paisetrail.app.data.db.CategoryEntity>,
    viewModel: MerchantManagementViewModel,
) {
    var expanded by remember(merchant.id) { mutableStateOf(false) }
    var showDeleteConfirm by remember(merchant.id) { mutableStateOf(false) }
    val defaultCategory = categories.firstOrNull { it.id == merchant.defaultCategoryId }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete \"${merchant.canonicalName}\"?", style = PaisaTheme.typography.body) },
            text = {
                Text(
                    "Transactions resolved to this merchant just stop counting toward Top Merchants. This cannot be undone.",
                    style = PaisaTheme.typography.bodySecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; viewModel.delete(merchant) }) {
                    Text("Delete", color = PaisaTheme.colors.negative)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(horizontal = PaisaSpacing.gutter, vertical = PaisaSpacing.normal),
    ) {
        Text(text = merchant.canonicalName, style = PaisaTheme.typography.body, color = PaisaTheme.colors.ink)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            if (defaultCategory != null) {
                CategoryDot(defaultCategory.colorHex, defaultCategory.emoji)
                Text(
                    text = defaultCategory.name,
                    style = PaisaTheme.typography.bodySecondary,
                    color = PaisaTheme.colors.inkMuted,
                    modifier = Modifier.padding(start = PaisaSpacing.tight),
                )
            } else {
                Text(
                    text = if (merchant.isOnline) "Online" else "No default category",
                    style = PaisaTheme.typography.bodySecondary,
                    color = PaisaTheme.colors.inkMuted,
                )
            }
        }
        if (expanded) {
            MerchantEditForm(
                initial = merchant,
                categories = categories,
                onSave = { name, categoryId, isOnline, vpa ->
                    val error = viewModel.save(merchant.id, name, categoryId, isOnline, vpa)
                    if (error == null) expanded = false
                    error
                },
                onCancel = { expanded = false },
                onDelete = { showDeleteConfirm = true },
            )
        }
    }
}

@Composable
private fun MerchantEditForm(
    initial: MerchantEntity?,
    categories: List<com.paisetrail.app.data.db.CategoryEntity>,
    onSave: (name: String, categoryId: Long?, isOnline: Boolean, vpa: String) -> String?,
    onCancel: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    var name by remember { mutableStateOf(initial?.canonicalName ?: "") }
    var categoryId by remember { mutableStateOf(initial?.defaultCategoryId) }
    var isOnline by remember { mutableStateOf(initial?.isOnline ?: false) }
    var vpa by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }
    var showCategoryPicker by remember { mutableStateOf(false) }
    val category = categories.firstOrNull { it.id == categoryId }

    Column(modifier = Modifier.fillMaxWidth().padding(top = PaisaSpacing.tight)) {
        Text(
            text = "Name",
            style = PaisaTheme.typography.overline,
            color = PaisaTheme.colors.inkMuted,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        BasicTextField(
            value = name,
            onValueChange = { name = it },
            textStyle = TextStyle(color = PaisaTheme.colors.ink, fontSize = PaisaTheme.typography.body.fontSize),
            modifier = Modifier.fillMaxWidth().background(PaisaTheme.colors.surface, PaisaShape).padding(PaisaSpacing.tight),
            decorationBox = { inner ->
                if (name.isEmpty()) {
                    Text("Merchant name", style = PaisaTheme.typography.body, color = PaisaTheme.colors.inkMuted)
                }
                inner()
            },
        )

        Text(
            text = "Default category",
            style = PaisaTheme.typography.overline,
            color = PaisaTheme.colors.inkMuted,
            modifier = Modifier.padding(top = PaisaSpacing.normal, bottom = 4.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showCategoryPicker = true }
                .background(PaisaTheme.colors.surface, PaisaShape)
                .padding(PaisaSpacing.tight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CategoryDot(category?.colorHex, category?.emoji)
            Text(
                text = category?.name ?: "None",
                style = PaisaTheme.typography.body,
                color = PaisaTheme.colors.ink,
                modifier = Modifier.padding(start = PaisaSpacing.tight),
            )
        }

        Text(
            text = "Link a VPA (optional)",
            style = PaisaTheme.typography.overline,
            color = PaisaTheme.colors.inkMuted,
            modifier = Modifier.padding(top = PaisaSpacing.normal, bottom = 4.dp),
        )
        BasicTextField(
            value = vpa,
            onValueChange = { vpa = it },
            textStyle = TextStyle(color = PaisaTheme.colors.ink, fontSize = PaisaTheme.typography.body.fontSize),
            modifier = Modifier.fillMaxWidth().background(PaisaTheme.colors.surface, PaisaShape).padding(PaisaSpacing.tight),
            decorationBox = { inner ->
                if (vpa.isEmpty()) {
                    Text("merchant@bank", style = PaisaTheme.typography.body, color = PaisaTheme.colors.inkMuted)
                }
                inner()
            },
        )

        Text(
            text = if (isOnline) "Online merchant — tap to mark in-person" else "In-person merchant — tap to mark online",
            style = PaisaTheme.typography.bodySecondary,
            color = PaisaTheme.colors.accent,
            modifier = Modifier.clickable { isOnline = !isOnline }.padding(top = PaisaSpacing.tight),
        )

        errorText?.let {
            Text(
                text = it,
                style = PaisaTheme.typography.bodySecondary,
                color = PaisaTheme.colors.negative,
                modifier = Modifier.padding(top = PaisaSpacing.tight),
            )
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
                    modifier = Modifier.clickable { errorText = onSave(name, categoryId, isOnline, vpa) },
                )
            }
        }
    }

    if (showCategoryPicker) {
        CategoryPickerSheet(
            categories = categories,
            onDismiss = { showCategoryPicker = false },
            onPick = { pickedName ->
                categoryId = categories.firstOrNull { it.name == pickedName }?.id
                showCategoryPicker = false
            },
        )
    }
}
