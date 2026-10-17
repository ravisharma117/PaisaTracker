package com.naxits.paisatracker.presentation.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.naxits.paisatracker.R
import com.naxits.paisatracker.domain.model.Party

/**
 * A text field that suggests the customers and vendors already saved while the user types, and
 * offers to add a new one when none matches. Picking a suggestion reports it through [onSelect];
 * typing anything else leaves the text for the caller to turn into a new party.
 */
@Composable
fun PartyAutocompleteField(
    value: String,
    onValueChange: (String) -> Unit,
    suggestions: List<Party>,
    onSelect: (Party) -> Unit,
    selectedPartyId: Long?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(4.dp)
) {
    var focused by remember { mutableStateOf(false) }
    var newConfirmed by remember { mutableStateOf(false) }
    val exact = suggestions.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) }
    val showSuggestions = focused && value.isNotBlank() && selectedPartyId == null && !newConfirmed

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        TextField(
            value = value,
            onValueChange = {
                newConfirmed = false
                onValueChange(it)
            },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused },
            label = { Text(stringResource(R.string.customer_label), fontWeight = FontWeight.SemiBold) },
            singleLine = true,
            shape = shape,
            leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
            supportingText = {
                if (value.isNotBlank() && selectedPartyId == null && exact == null) {
                    Text(stringResource(R.string.customer_will_be_added))
                }
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f)
            )
        )

        if (showSuggestions) {
            suggestions.take(5).forEach { party ->
                PartySuggestionRow(
                    icon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                    text = party.name,
                    onClick = {
                        newConfirmed = false
                        onSelect(party)
                    }
                )
            }
            if (exact == null) {
                PartySuggestionRow(
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    text = stringResource(R.string.add_new_customer, value.trim()),
                    onClick = { newConfirmed = true }
                )
            }
        }
    }
}

@Composable
private fun PartySuggestionRow(icon: @Composable () -> Unit, text: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            icon()
            Text(text = text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
