package com.naxits.paisatracker.presentation.ui.features.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.naxits.paisatracker.R
import com.naxits.paisatracker.domain.model.AccountTags

@Composable
private fun metaTextFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f)
)

/** Chips for the preset account tags, any tags the account already has, and a field for new ones. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccountTagsEditor(
    tags: List<String>,
    onTagsChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    var newTag by remember { mutableStateOf("") }
    val shown = AccountTags.normalize(AccountTags.PRESETS + tags)

    fun toggle(tag: String) {
        val has = tags.any { it.equals(tag, ignoreCase = true) }
        onTagsChange(if (has) tags.filterNot { it.equals(tag, ignoreCase = true) } else tags + tag)
    }

    fun addNew() {
        if (newTag.isNotBlank()) onTagsChange(AccountTags.normalize(tags + newTag))
        newTag = ""
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.account_tags_label),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            shown.forEach { tag ->
                FilterChip(
                    selected = tags.any { it.equals(tag, ignoreCase = true) },
                    onClick = { toggle(tag) },
                    label = { Text(tag) }
                )
            }
        }
        TextField(
            value = newTag,
            onValueChange = { newTag = it.take(24) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.account_tag_add_hint)) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = metaTextFieldColors(),
            trailingIcon = {
                IconButton(onClick = ::addNew, enabled = newTag.isNotBlank()) {
                    Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.account_tag_add))
                }
            }
        )
    }
}

@Composable
fun AccountDescriptionField(
    description: String,
    onDescriptionChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    TextField(
        value = description,
        onValueChange = { onDescriptionChange(it.take(200)) },
        modifier = modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.account_description_label), fontWeight = FontWeight.SemiBold) },
        placeholder = { Text(stringResource(R.string.account_description_hint)) },
        minLines = 2,
        maxLines = 4,
        shape = RoundedCornerShape(16.dp),
        colors = metaTextFieldColors()
    )
}
