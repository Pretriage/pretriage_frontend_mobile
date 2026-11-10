package com.proyecto_final.triage.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.DividerDefaults
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.proyecto_final.triage.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InputDropdownField(
    label: String,
    value: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    options: List<String>,
    onOptionSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    errorMessage: String = "Este campo es obligatorio",
    displayMap: Map<String, String> = emptyMap(),
    placeholder: String? = null,
    bottomSpacing: Dp = Spacing.md
) {
    Column(modifier = modifier.fillMaxWidth()) {

        Text(text = label, style = MaterialTheme.typography.bodyLarge)

        Spacer(modifier = Modifier.height(Spacing.sm))

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = {
                onExpandedChange(!expanded)
            }
        ) {

            OutlinedTextField(
                value = displayMap[value] ?: value,
                onValueChange = {},
                readOnly = true,
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(
                        expanded = expanded
                    )
                },
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Normal
                ),
                placeholder = if (placeholder != null) {
                    {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                } else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                shape = RoundedCornerShape(12.dp),
                isError = isError,
                supportingText = {
                    if (isError) {
                        Text(
                            errorMessage,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { onExpandedChange(false) },
                modifier = Modifier.heightIn(max = 250.dp).background(MaterialTheme.colorScheme.background),
                shape = RoundedCornerShape(12.dp),
            ) {
                options.forEachIndexed { index, option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = option,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.Normal
                                )
                            )
                        },
                        onClick = {
                            onOptionSelected(option)
                            onExpandedChange(false)
                        },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 16.dp,
                            vertical = 8.dp
                        ),
                        colors = MenuDefaults.itemColors(
                            textColor = MaterialTheme.colorScheme.onBackground
                        )
                    )
                    if (index < options.size - 1) {
                        HorizontalDivider(
                            Modifier,
                            DividerDefaults.Thickness,
                            color = androidx.compose.ui.graphics.Color(0xFF5BB8D4).copy(alpha = 0.2f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(bottomSpacing))
    }
}
