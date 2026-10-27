package com.naxits.paisatracker.presentation.ui.features.vendors

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naxits.paisatracker.R
import com.naxits.paisatracker.presentation.ui.components.CategorySelectionSheet
import com.naxits.paisatracker.presentation.ui.components.CustomTitleTopAppBar
import com.naxits.paisatracker.presentation.ui.components.ListItem
import com.naxits.paisatracker.presentation.ui.components.ListItemPosition
import com.naxits.paisatracker.presentation.ui.components.toShape
import com.naxits.paisatracker.presentation.ui.features.categories.NavigationContent
import com.naxits.paisatracker.presentation.ui.theme.Dimensions
import com.naxits.paisatracker.presentation.ui.theme.Spacing
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

/**
 * The companies, firms and brands whose category is applied to every future entry. Suggestions
 * (vendors the user keeps paying under one category) are on top, to add; the vendors already
 * remembered are below, to remove.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RememberedVendorsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RememberedVendorsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val subcategories by viewModel.subcategories.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    val snackbarHost = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val hazeState = remember { HazeState() }

    // Say how many existing entries a newly added vendor changed
    LaunchedEffect(Unit) {
        viewModel.refiled.collect { done ->
            snackbarHost.showSnackbar(
                context.resources.getQuantityString(R.plurals.vendor_refiled, done.entries, done.entries, done.vendorName)
            )
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHost) },
        floatingActionButton = {
            FloatingActionButton(onClick = { adding = true }) {
                Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.vendor_add_new))
            }
        },
        topBar = {
            CustomTitleTopAppBar(
                title = stringResource(R.string.vendors_title),
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehavior,
                hazeState = hazeState,
                hasBackButton = true,
                navigationContent = { NavigationContent(onNavigateBack) }
            )
        }
    ) { paddingValues ->
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
            // Suggestions first
            item(key = "suggested-header") {
                SectionHeader(R.string.vendors_suggested_header, R.string.vendors_suggested_hint)
            }
            if (state.suggestions.isEmpty()) {
                item(key = "suggested-empty") {
                    EmptyNote(if (state.loaded) R.string.vendors_suggested_empty else R.string.vendors_loading)
                }
            }
            itemsIndexed(state.suggestions, key = { _, it -> "s-${it.merchantName}" }) { index, suggestion ->
                ListItem(
                    headline = {
                        Text(
                            text = suggestion.merchantName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    supporting = {
                        Text(
                            text = listOfNotNull(
                                suggestion.category,
                                suggestion.subcategory,
                                pluralStringResource(R.plurals.vendor_entries, suggestion.entryCount, suggestion.entryCount)
                            ).joinToString(" • "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    leading = { Icon(Icons.Rounded.Storefront, contentDescription = null) },
                    trailing = {
                        TextButton(onClick = { viewModel.remember(suggestion) }) {
                            Text(stringResource(R.string.vendor_add))
                        }
                    },
                    shape = ListItemPosition.from(index, state.suggestions.size).toShape(),
                    padding = PaddingValues(0.dp)
                )
            }

            // Then what is remembered already
            item(key = "remembered-header") {
                SectionHeader(R.string.vendors_remembered_header, R.string.vendors_remembered_hint, topSpace = true)
            }
            if (state.remembered.isEmpty()) {
                item(key = "remembered-empty") {
                    EmptyNote(if (state.loaded) R.string.vendors_remembered_empty else R.string.vendors_loading)
                }
            }
            itemsIndexed(state.remembered, key = { _, it -> "r-${it.merchantName}" }) { index, mapping ->
                ListItem(
                    headline = {
                        Text(
                            text = mapping.merchantName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    supporting = {
                        Text(
                            text = listOfNotNull(mapping.category, mapping.subcategory).joinToString(" • "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    leading = { Icon(Icons.Rounded.Storefront, contentDescription = null) },
                    trailing = {
                        IconButton(onClick = { viewModel.forget(mapping) }) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.vendor_remove, mapping.merchantName)
                            )
                        }
                    },
                    shape = ListItemPosition.from(index, state.remembered.size).toShape(),
                    padding = PaddingValues(0.dp)
                )
            }
        }
    }

    if (adding) {
        AddVendorSheet(
            categories = categories,
            subcategories = subcategories,
            onSave = { name, category, subcategory ->
                viewModel.add(name, category, subcategory)
                adding = false
            },
            onDismiss = { adding = false }
        )
    }
}

/** Type a vendor name and choose its category; every entry from that vendor is then filed under it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddVendorSheet(
    categories: List<com.naxits.paisatracker.data.database.entity.CategoryEntity>,
    subcategories: Map<Long, List<com.naxits.paisatracker.data.database.entity.SubcategoryEntity>>,
    onSave: (name: String, category: String, subcategory: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<String?>(null) }
    var subcategory by remember { mutableStateOf<String?>(null) }
    var choosingCategory by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimensions.Padding.content)
                .padding(bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Text(
                text = stringResource(R.string.vendor_add_new),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.vendor_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedButton(
                onClick = { choosingCategory = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = category?.let { listOfNotNull(it, subcategory).joinToString(" • ") }
                        ?: stringResource(R.string.vendor_category_pick)
                )
            }
            Text(
                text = stringResource(R.string.vendor_add_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = { category?.let { onSave(name, it, subcategory) } },
                enabled = name.isNotBlank() && category != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.vendor_save))
            }
        }
    }

    if (choosingCategory) {
        ModalBottomSheet(
            onDismissRequest = { choosingCategory = false },
            dragHandle = { BottomSheetDefaults.DragHandle() },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            CategorySelectionSheet(
                categories = categories,
                subcategoriesMap = subcategories,
                onSelectionComplete = { chosen, sub ->
                    category = chosen.name
                    subcategory = sub?.name
                    choosingCategory = false
                },
                onDismiss = { choosingCategory = false }
            )
        }
    }
}

@Composable
private fun SectionHeader(title: Int, hint: Int, topSpace: Boolean = false) {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier.padding(top = if (topSpace) Spacing.lg else 0.dp, bottom = Spacing.sm, start = Spacing.xs)
    ) {
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = stringResource(hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EmptyNote(text: Int) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.sm)
    )
}
