package com.paisetrail.app.ui.screens.banks

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.paisetrail.app.capture.sms.PatternInducer
import com.paisetrail.app.data.db.BankSmsPatternEntity
import com.paisetrail.app.ui.theme.ChipShape
import com.paisetrail.app.ui.theme.PaisaSpacing
import com.paisetrail.app.ui.theme.PaisaTheme

/** The five group names [com.paisetrail.app.capture.sms.BankSmsPatternRegistry] actually reads —
 * [PatternInducer] can also emit a "date" group (to keep multiple examples cross-matching without
 * freezing a specific date into the regex as literal text), which isn't one of these and shouldn't
 * be advertised to the user as a "found" field. */
private val USABLE_FIELD_NAMES = setOf("amount", "payee", "vpa", "ref", "acctLast4")

/** Add/edit/delete a bank's SMS pattern from the app (spec 3.2, spec 7.6 debug tools) instead of
 * a code change. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BankPatternManagementScreen(onBack: () -> Unit, viewModel: BankPatternManagementViewModel = hiltViewModel()) {
    val patterns by viewModel.patterns.collectAsState()
    var addingNew by remember { mutableStateOf(false) }
    var addingPatternForBank by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bank SMS patterns", style = PaisaTheme.typography.body) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { addingNew = !addingNew }) {
                        Icon(Icons.Outlined.Add, contentDescription = "Add bank pattern")
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
        val groups = patterns.groupBy { it.bankId }.toSortedMap()

        LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (addingNew) {
                item(key = "new") {
                    Column(modifier = Modifier.fillMaxWidth().padding(PaisaSpacing.gutter)) {
                        BankPatternEditForm(
                            initial = null,
                            presetBankId = null,
                            onSave = { bankId, senderSuffix, regex, enabled ->
                                val error = viewModel.save(null, bankId, senderSuffix, regex, enabled)
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
            groups.forEach { (bankId, patternsForBank) ->
                item(key = "header-$bankId") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = PaisaSpacing.gutter, vertical = PaisaSpacing.tight),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "$bankId · ${patternsForBank.size} pattern${if (patternsForBank.size == 1) "" else "s"}",
                            style = PaisaTheme.typography.label,
                            color = PaisaTheme.colors.inkMuted,
                        )
                        Text(
                            text = "+ Add pattern",
                            style = PaisaTheme.typography.caption,
                            color = PaisaTheme.colors.accent,
                            modifier = Modifier.clickable {
                                addingPatternForBank = if (addingPatternForBank == bankId) null else bankId
                            },
                        )
                    }
                }
                if (addingPatternForBank == bankId) {
                    item(key = "new-for-$bankId") {
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = PaisaSpacing.gutter)) {
                            BankPatternEditForm(
                                initial = null,
                                presetBankId = bankId,
                                onSave = { newBankId, senderSuffix, regex, enabled ->
                                    val error = viewModel.save(null, newBankId, senderSuffix, regex, enabled)
                                    if (error == null) addingPatternForBank = null
                                    error
                                },
                                onCancel = { addingPatternForBank = null },
                                onDelete = null,
                            )
                        }
                        HorizontalDivider(color = PaisaTheme.colors.hairline, thickness = 1.dp)
                    }
                }
                items(patternsForBank, key = { it.id }) { pattern ->
                    BankPatternRow(pattern, viewModel)
                    HorizontalDivider(color = PaisaTheme.colors.hairline, thickness = 1.dp)
                }
            }
        }
    }
}

@Composable
private fun BankPatternRow(pattern: BankSmsPatternEntity, viewModel: BankPatternManagementViewModel) {
    var expanded by remember(pattern.id) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(horizontal = PaisaSpacing.gutter, vertical = PaisaSpacing.normal),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = "Sender contains \"${pattern.senderSuffix}\"",
                style = PaisaTheme.typography.body,
                color = PaisaTheme.colors.ink,
            )
            Text(
                text = if (pattern.enabled) "Enabled" else "Disabled",
                style = PaisaTheme.typography.caption,
                color = if (pattern.enabled) PaisaTheme.colors.inkMuted else PaisaTheme.colors.negative,
                modifier = Modifier.clickable { viewModel.setEnabled(pattern, !pattern.enabled) },
            )
        }
        if (expanded) {
            BankPatternEditForm(
                initial = pattern,
                presetBankId = null,
                onSave = { bankId, senderSuffix, regex, enabled ->
                    val error = viewModel.save(pattern.id, bankId, senderSuffix, regex, enabled)
                    if (error == null) expanded = false
                    error
                },
                onCancel = { expanded = false },
                onDelete = { viewModel.delete(pattern); expanded = false },
            )
        }
    }
}

@Composable
private fun BankPatternEditForm(
    initial: BankSmsPatternEntity?,
    presetBankId: String?,
    onSave: (bankId: String, senderSuffix: String, regex: String, enabled: Boolean) -> String?,
    onCancel: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    var bankId by remember { mutableStateOf(initial?.bankId ?: presetBankId ?: "") }
    var senderSuffix by remember { mutableStateOf(initial?.senderSuffix ?: "") }
    var regex by remember { mutableStateOf(initial?.regex ?: "") }
    var enabled by remember { mutableStateOf(initial?.enabled ?: true) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var examplesText by remember { mutableStateOf("") }
    var suggestSummary by remember { mutableStateOf<String?>(null) }
    var suggestError by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxWidth().padding(top = PaisaSpacing.tight)) {
        FormField(
            label = "Bank id",
            value = bankId,
            onValueChange = { bankId = it },
            enabled = initial == null && presetBankId == null,
            hint = "e.g. HDFC",
        )
        FormField(label = "Sender suffix", value = senderSuffix, onValueChange = { senderSuffix = it }, hint = "e.g. HDFCBK")

        Text(
            text = "Don't want to write the regex yourself?",
            style = PaisaTheme.typography.label,
            color = PaisaTheme.colors.inkMuted,
            modifier = Modifier.padding(top = PaisaSpacing.normal, bottom = 4.dp),
        )
        FormField(
            label = "Learn from example messages",
            value = examplesText,
            onValueChange = {
                examplesText = it
                suggestSummary = null
                suggestError = null
            },
            hint = "Paste one or more real SMS from this bank, separated by a blank line",
            singleLine = false,
        )
        Text(
            text = "Suggest a pattern",
            style = PaisaTheme.typography.body,
            color = PaisaTheme.colors.accent,
            modifier = Modifier
                .clickable {
                    val examples = examplesText.split(Regex("\n\\s*\n")).map { it.trim() }.filter { it.isNotEmpty() }
                    val induced = PatternInducer.induce(examples)
                    if (induced == null) {
                        suggestSummary = null
                        suggestError = "Couldn't find an amount in those messages — paste the full SMS text"
                    } else {
                        regex = induced.regex
                        suggestError = null
                        val knownFields = induced.fields.filter { it in USABLE_FIELD_NAMES }
                        val confidence = if (induced.totalCount > 1) " · matched ${induced.matchedCount}/${induced.totalCount} examples" else ""
                        suggestSummary = "Found: ${knownFields.joinToString(", ")}$confidence"
                    }
                }
                .padding(top = PaisaSpacing.tight),
        )
        suggestSummary?.let {
            Text(
                text = it,
                style = PaisaTheme.typography.caption,
                color = PaisaTheme.colors.inkMuted,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        suggestError?.let {
            Text(
                text = it,
                style = PaisaTheme.typography.caption,
                color = PaisaTheme.colors.negative,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        FormField(
            label = "Regex",
            value = regex,
            onValueChange = { regex = it },
            hint = "Named groups: (?<amount>..) (?<payee>..) (?<vpa>..) (?<ref>..) (?<acctLast4>..)",
            singleLine = false,
        )
        Text(
            text = if (enabled) "Enabled — tap to disable" else "Disabled — tap to enable",
            style = PaisaTheme.typography.caption,
            color = if (enabled) PaisaTheme.colors.accent else PaisaTheme.colors.inkMuted,
            modifier = Modifier
                .clickable { enabled = !enabled }
                .padding(top = PaisaSpacing.tight),
        )
        errorText?.let {
            Text(
                text = it,
                style = PaisaTheme.typography.caption,
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
                    modifier = Modifier.clickable {
                        errorText = onSave(bankId, senderSuffix, regex, enabled)
                    },
                )
            }
        }
    }
}

@Composable
private fun FormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    enabled: Boolean = true,
    singleLine: Boolean = true,
) {
    Text(
        text = label,
        style = PaisaTheme.typography.label,
        color = PaisaTheme.colors.inkMuted,
        modifier = Modifier.padding(top = PaisaSpacing.normal, bottom = 4.dp),
    )
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        textStyle = TextStyle(color = PaisaTheme.colors.ink, fontSize = PaisaTheme.typography.body.fontSize),
        modifier = Modifier
            .fillMaxWidth()
            .background(PaisaTheme.colors.surface1, ChipShape)
            .padding(PaisaSpacing.tight),
        decorationBox = { inner ->
            if (value.isEmpty()) {
                Text(text = hint, style = PaisaTheme.typography.caption, color = PaisaTheme.colors.inkMuted)
            }
            inner()
        },
    )
}
