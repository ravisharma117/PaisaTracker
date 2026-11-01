package com.naxits.paisatracker.presentation.ui.features.parties

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naxits.paisatracker.R
import com.naxits.paisatracker.data.database.entity.PartyKind
import com.naxits.paisatracker.domain.model.Party
import com.naxits.paisatracker.presentation.ui.components.CustomTitleTopAppBar
import com.naxits.paisatracker.presentation.ui.components.ListItem
import com.naxits.paisatracker.presentation.ui.components.ListItemPosition
import com.naxits.paisatracker.presentation.ui.components.toShape
import com.naxits.paisatracker.presentation.ui.features.categories.NavigationContent
import com.naxits.paisatracker.presentation.ui.theme.Dimensions
import com.naxits.paisatracker.presentation.ui.theme.Spacing
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

/** The customers and vendors the user does business with: add, rename, merge, archive. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartiesScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PartiesViewModel = hiltViewModel()
) {
    val rows by viewModel.parties.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val hazeState = remember { HazeState() }
    var editing by remember { mutableStateOf<Party?>(null) }
    var adding by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CustomTitleTopAppBar(
                title = stringResource(R.string.parties_title),
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehavior,
                hazeState = hazeState,
                hasBackButton = true,
                navigationContent = { NavigationContent(onNavigateBack) }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { adding = true }) {
                Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.party_add))
            }
        }
    ) { paddingValues ->
        if (rows.isEmpty()) {
            Text(
                text = stringResource(R.string.parties_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = modifier
                    .padding(Dimensions.Padding.content)
                    .padding(top = paddingValues.calculateTopPadding())
            )
        } else {
            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .hazeSource(state = hazeState),
                contentPadding = PaddingValues(
                    start = Dimensions.Padding.content,
                    end = Dimensions.Padding.content,
                    top = Dimensions.Padding.content + paddingValues.calculateTopPadding(),
                    bottom = 96.dp
                ),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(rows, key = { it.party.id }) { row ->
                    val position = when {
                        rows.size == 1 -> ListItemPosition.Single
                        row == rows.first() -> ListItemPosition.Top
                        row == rows.last() -> ListItemPosition.Bottom
                        else -> ListItemPosition.Middle
                    }
                    ListItem(
                        headline = {
                            Text(
                                text = row.party.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                        },
                        supporting = {
                            Text(
                                text = listOfNotNull(
                                    stringResource(
                                        if (row.party.kind == PartyKind.VENDOR) R.string.party_kind_vendor else R.string.party_kind_customer
                                    ),
                                    pluralStringResource(R.plurals.party_transactions, row.transactionCount, row.transactionCount),
                                    row.party.phone
                                ).joinToString(" • "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        leading = { Icon(Icons.Rounded.Person, contentDescription = null) },
                        onClick = { editing = row.party },
                        shape = position.toShape(),
                        padding = PaddingValues(0.dp)
                    )
                }
            }
        }
    }

    if (adding) {
        PartyEditSheet(
            party = null,
            others = rows.map { it.party },
            onDismiss = { adding = false },
            onSave = { name, kind, phone, notes ->
                viewModel.add(name, kind, phone, notes)
                adding = false
            },
            onArchive = null,
            onMerge = null
        )
    }
    editing?.let { party ->
        PartyEditSheet(
            party = party,
            others = rows.map { it.party }.filter { it.id != party.id },
            onDismiss = { editing = null },
            onSave = { name, kind, phone, notes ->
                viewModel.update(party.copy(name = name, kind = kind, phone = phone, notes = notes))
                editing = null
            },
            onArchive = {
                viewModel.archive(party.id)
                editing = null
            },
            onMerge = { target ->
                viewModel.merge(party.id, target.id)
                editing = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PartyEditSheet(
    party: Party?,
    others: List<Party>,
    onDismiss: () -> Unit,
    onSave: (name: String, kind: String, phone: String?, notes: String?) -> Unit,
    onArchive: (() -> Unit)?,
    onMerge: ((Party) -> Unit)?
) {
    var name by remember { mutableStateOf(party?.name.orEmpty()) }
    var kind by remember { mutableStateOf(party?.kind ?: PartyKind.CUSTOMER) }
    var phone by remember { mutableStateOf(party?.phone.orEmpty()) }
    var notes by remember { mutableStateOf(party?.notes.orEmpty()) }
    var choosingMerge by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(if (party == null) R.string.party_add else R.string.party_edit),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = kind == PartyKind.CUSTOMER,
                    onClick = { kind = PartyKind.CUSTOMER },
                    label = { Text(stringResource(R.string.party_kind_customer)) }
                )
                FilterChip(
                    selected = kind == PartyKind.VENDOR,
                    onClick = { kind = PartyKind.VENDOR },
                    label = { Text(stringResource(R.string.party_kind_vendor)) }
                )
            }
            PartyTextField(name, { name = it }, stringResource(R.string.party_name_label))
            PartyTextField(phone, { phone = it.filter { c -> c.isDigit() || c == '+' || c == ' ' } }, stringResource(R.string.party_phone_label))
            PartyTextField(notes, { notes = it }, stringResource(R.string.party_notes_label), singleLine = false)

            Button(
                onClick = { onSave(name.trim(), kind, phone.trim().ifBlank { null }, notes.trim().ifBlank { null }) },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.party_save)) }

            if (onMerge != null && others.isNotEmpty()) {
                OutlinedButton(onClick = { choosingMerge = !choosingMerge }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.party_merge_into))
                }
                if (choosingMerge) {
                    others.forEach { other ->
                        TextButton(onClick = { onMerge(other) }, modifier = Modifier.fillMaxWidth()) { Text(other.name) }
                    }
                }
            }
            if (onArchive != null) {
                TextButton(onClick = onArchive, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.party_archive), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun PartyTextField(value: String, onValueChange: (String) -> Unit, label: String, singleLine: Boolean = true) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontWeight = FontWeight.SemiBold) },
        singleLine = singleLine,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        )
    )
}
