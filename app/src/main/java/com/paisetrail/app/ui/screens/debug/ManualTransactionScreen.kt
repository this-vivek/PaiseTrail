package com.paisetrail.app.ui.screens.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.paisetrail.app.ui.components.CategoryDot
import com.paisetrail.app.ui.components.CategoryPickerSheet
import com.paisetrail.app.ui.theme.ChipShape
import com.paisetrail.app.ui.theme.PaisaSpacing
import com.paisetrail.app.ui.theme.PaisaTheme
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val DATE_FORMAT = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")

/** Debug tool (spec 7.6): manually create a transaction with your own amount, payee, category,
 * and date/time — for scenarios a random draft won't reliably produce. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualTransactionScreen(onDone: () -> Unit, viewModel: ManualTransactionViewModel = hiltViewModel()) {
    val categories by viewModel.categories.collectAsState()

    var payeeName by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf<Long?>(null) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var time by remember { mutableStateOf(LocalTime.now()) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var showCategoryPicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val selectedCategory = categories.firstOrNull { it.id == selectedCategoryId }

    Column(modifier = Modifier.fillMaxSize().padding(PaisaSpacing.gutter)) {
        Text(
            text = "← Cancel",
            style = PaisaTheme.typography.body,
            color = PaisaTheme.colors.accent,
            modifier = Modifier.clickable(onClick = onDone),
        )
        Text(
            text = "Custom transaction",
            style = PaisaTheme.typography.amountL,
            color = PaisaTheme.colors.ink,
            modifier = Modifier.padding(top = PaisaSpacing.normal, bottom = PaisaSpacing.loose),
        )

        FieldLabel("Paid to")
        BasicTextField(
            value = payeeName,
            onValueChange = { payeeName = it },
            textStyle = TextStyle(color = PaisaTheme.colors.ink, fontSize = PaisaTheme.typography.body.fontSize),
            modifier = Modifier
                .fillMaxWidth()
                .background(PaisaTheme.colors.surface1, ChipShape)
                .padding(PaisaSpacing.tight),
            decorationBox = { inner ->
                if (payeeName.isEmpty()) {
                    Text("Person or merchant name", style = PaisaTheme.typography.body, color = PaisaTheme.colors.inkMuted)
                }
                inner()
            },
        )

        FieldLabel("Amount")
        BasicTextField(
            value = amountText,
            onValueChange = { amountText = it },
            textStyle = TextStyle(color = PaisaTheme.colors.ink, fontSize = PaisaTheme.typography.body.fontSize),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier
                .fillMaxWidth()
                .background(PaisaTheme.colors.surface1, ChipShape)
                .padding(PaisaSpacing.tight),
            decorationBox = { inner ->
                if (amountText.isEmpty()) {
                    Text("₹0", style = PaisaTheme.typography.body, color = PaisaTheme.colors.inkMuted)
                }
                inner()
            },
        )

        FieldLabel("Category")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showCategoryPicker = true }
                .background(PaisaTheme.colors.surface1, ChipShape)
                .padding(PaisaSpacing.tight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CategoryDot(selectedCategory?.colorHex, selectedCategory?.emoji)
            Text(
                text = selectedCategory?.name ?: "Uncategorized",
                style = PaisaTheme.typography.body,
                color = PaisaTheme.colors.ink,
                modifier = Modifier.padding(start = PaisaSpacing.tight),
            )
        }

        FieldLabel("When")
        Row {
            Text(
                text = DATE_FORMAT.format(date),
                style = PaisaTheme.typography.body,
                color = PaisaTheme.colors.ink,
                modifier = Modifier
                    .clickable { showDatePicker = true }
                    .background(PaisaTheme.colors.surface1, ChipShape)
                    .padding(PaisaSpacing.tight),
            )
            Text(
                text = TIME_FORMAT.format(time),
                style = PaisaTheme.typography.body,
                color = PaisaTheme.colors.ink,
                modifier = Modifier
                    .clickable { showTimePicker = true }
                    .padding(start = PaisaSpacing.tight)
                    .background(PaisaTheme.colors.surface1, ChipShape)
                    .padding(PaisaSpacing.tight),
            )
        }

        errorText?.let {
            Text(
                text = it,
                style = PaisaTheme.typography.caption,
                color = PaisaTheme.colors.negative,
                modifier = Modifier.padding(top = PaisaSpacing.tight),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = PaisaSpacing.loose)
                .background(PaisaTheme.colors.accent, RoundedCornerShape(50))
                .clickable {
                    val occurredAt = date.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    errorText = viewModel.submit(payeeName, selectedCategoryId, amountText, occurredAt, onDone)
                }
                .padding(vertical = PaisaSpacing.tight),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "Add transaction", style = PaisaTheme.typography.body, color = PaisaTheme.colors.bg)
        }
    }

    if (showCategoryPicker) {
        CategoryPickerSheet(
            categories = categories,
            onDismiss = { showCategoryPicker = false },
            onPick = { name ->
                selectedCategoryId = categories.firstOrNull { it.name == name }?.id
                showCategoryPicker = false
            },
        )
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        date = Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = state)
        }
    }

    if (showTimePicker) {
        val state = rememberTimePickerState(initialHour = time.hour, initialMinute = time.minute)
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    time = LocalTime.of(state.hour, state.minute)
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Cancel") } },
            text = { TimePicker(state = state) },
        )
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = PaisaTheme.typography.label,
        color = PaisaTheme.colors.inkMuted,
        modifier = Modifier.padding(top = PaisaSpacing.normal, bottom = 4.dp),
    )
}
