package com.proyecto_final.triage.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.proyecto_final.triage.theme.Spacing
import com.proyecto_final.triage.utils.claveUbicacion

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SearchableLocationField(
    label: String,
    value: String,
    options: List<String>,
    onOptionSelected: (String) -> Unit,
    placeholder: String,
    enabled: Boolean,
    isError: Boolean,
    errorMessage: String
) {
    var expanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current

    fun closeMenu() {
        expanded = false
        query = ""
    }

    LaunchedEffect(enabled) {
        if (!enabled) closeMenu()
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(Spacing.sm))

        ExposedDropdownMenuBox(
            expanded = expanded && enabled,
            onExpandedChange = { shouldExpand ->
                if (enabled) {
                    if (shouldExpand) {
                        query = ""
                        expanded = true
                    } else {
                        closeMenu()
                    }
                }
            }
        ) {
            OutlinedTextField(
                value = if (expanded) query else value,
                onValueChange = {
                    query = it
                    expanded = true
                },
                enabled = enabled,
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(
                        expanded = expanded && enabled,
                        modifier = Modifier.menuAnchor(
                            ExposedDropdownMenuAnchorType.SecondaryEditable,
                            enabled = enabled
                        )
                    )
                },
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Normal),
                placeholder = {
                    Text(
                        text = if (expanded) "Buscar" else placeholder,
                        style = MaterialTheme.typography.bodyLarge
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, enabled = enabled),
                shape = RoundedCornerShape(12.dp),
                isError = isError,
                supportingText = {
                    if (isError) Text(errorMessage, color = MaterialTheme.colorScheme.error)
                }
            )

            ExposedDropdownMenu(
                expanded = expanded && enabled,
                onDismissRequest = { closeMenu() },
                modifier = Modifier
                    .heightIn(max = 300.dp)
                    .background(MaterialTheme.colorScheme.background),
                shape = RoundedCornerShape(12.dp)
            ) {
                val keyedOptions = remember(options) { options.map { it to claveUbicacion(it) } }
                val normalizedQuery = claveUbicacion(query)
                val filteredOptions = remember(keyedOptions, normalizedQuery) {
                    val prefixMatches = keyedOptions.asSequence().filter { (_, key) ->
                        key.startsWith(normalizedQuery)
                    }
                    val containsMatches = keyedOptions.asSequence().filter { (_, key) ->
                        !key.startsWith(normalizedQuery) && key.contains(normalizedQuery)
                    }
                    (prefixMatches + containsMatches).take(60).map { it.first }.toList()
                }
                val showCustomOption = normalizedQuery.isNotBlank() &&
                    keyedOptions.none { (_, key) -> key == normalizedQuery }

                if (filteredOptions.isEmpty() && !showCustomOption) {
                    Text(
                        text = "No hay ubicaciones disponibles",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(Spacing.md)
                    )
                }

                filteredOptions.forEachIndexed { index, option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = option,
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Normal)
                            )
                        },
                        onClick = {
                            onOptionSelected(option)
                            closeMenu()
                            keyboard?.hide()
                        },
                        colors = MenuDefaults.itemColors(textColor = MaterialTheme.colorScheme.onBackground)
                    )
                    if (index < filteredOptions.lastIndex || showCustomOption) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }

                if (showCustomOption) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Usar “${query.trim()}”",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Normal)
                            )
                        },
                        onClick = {
                            onOptionSelected(query.trim())
                            closeMenu()
                            keyboard?.hide()
                        },
                        colors = MenuDefaults.itemColors(textColor = MaterialTheme.colorScheme.onBackground)
                    )
                }
            }
        }
    }
}
