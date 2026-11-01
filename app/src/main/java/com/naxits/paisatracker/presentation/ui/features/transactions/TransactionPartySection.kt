package com.naxits.paisatracker.presentation.ui.features.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naxits.paisatracker.R
import com.naxits.paisatracker.data.database.entity.ReviewKind
import com.naxits.paisatracker.data.database.entity.ReviewResolvedBy
import com.naxits.paisatracker.data.database.entity.ReviewStatus
import com.naxits.paisatracker.data.database.entity.TransactionEntity
import com.naxits.paisatracker.data.database.entity.TransactionReviewEntity
import com.naxits.paisatracker.domain.model.AccountOrdering
import com.naxits.paisatracker.domain.model.Party
import com.naxits.paisatracker.domain.service.PartyReviewDetector
import com.naxits.paisatracker.presentation.common.icons.LocalAccountMeta
import com.naxits.paisatracker.presentation.ui.components.PartyAutocompleteField
import com.naxits.paisatracker.presentation.ui.components.SubtitleTag
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Who the transaction was with, the suggestion the app made (with how it was worked out), and
 * what happened to it. Shown for entries on a Business account, and for any entry that has a
 * customer or a review.
 */
@Composable
fun TransactionPartySection(
    transaction: TransactionEntity,
    onPartyChanged: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TransactionPartyViewModel = hiltViewModel()
) {
    LaunchedEffect(transaction.id, transaction.partyId) { viewModel.bind(transaction.id, transaction.partyId) }

    val reviews by viewModel.reviews.collectAsStateWithLifecycle()
    val party by viewModel.currentParty.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()

    val isBusiness = transaction.bankName != null && transaction.accountNumber != null &&
        LocalAccountMeta.current[AccountOrdering.keyOf(transaction.bankName, transaction.accountNumber)]?.isBusiness == true
    val partyReviews = reviews.filter { it.kind == ReviewKind.PARTY }
    if (!isBusiness && partyReviews.isEmpty() && transaction.partyId == null) return

    val pending = partyReviews.firstOrNull { it.isPending }
    var showPicker by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.party_section_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = party?.name ?: stringResource(R.string.party_unknown),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (pending != null) {
                    SubtitleTag(
                        text = stringResource(R.string.review_badge),
                        color = MaterialTheme.colorScheme.error,
                        alpha = 0.15f,
                        textColor = MaterialTheme.colorScheme.error
                    )
                } else {
                    TextButton(onClick = { showPicker = true }) { Text(stringResource(R.string.review_change)) }
                }
            }

            if (pending != null) {
                PendingReviewBlock(
                    review = pending,
                    onConfirm = { viewModel.confirm(pending.id, onPartyChanged) },
                    onChange = { showPicker = true },
                    onDismiss = { viewModel.dismiss(pending.id) }
                )
            }

            partyReviews.filterNot { it.isPending }.forEach { ReviewHistoryLine(it) }
        }
    }

    if (showPicker) {
        PartyPickerSheet(
            suggestions = suggestions,
            onSearch = viewModel::search,
            onDismiss = { showPicker = false },
            onSave = { existing, typed ->
                viewModel.changeParty(pending?.id, existing, typed, onPartyChanged)
                showPicker = false
            }
        )
    }
}

@Composable
private fun PendingReviewBlock(
    review: TransactionReviewEntity,
    onConfirm: () -> Unit,
    onChange: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.review_suggested, review.suggestedValue),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = stringResource(R.string.review_why, review.reason),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (review.confidence > 0) {
            Text(
                text = stringResource(R.string.review_confidence, review.confidence),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onConfirm) { Text(stringResource(R.string.review_confirm)) }
            OutlinedButton(onClick = onChange) { Text(stringResource(R.string.review_change)) }
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.review_dismiss)) }
        }
    }
}

@Composable
private fun ReviewHistoryLine(review: TransactionReviewEntity) {
    val date = review.resolvedAt?.let { formatDate(it) }.orEmpty()
    val value = review.resolvedValue ?: PartyReviewDetector.UNKNOWN
    val text = when {
        review.status == ReviewStatus.DISMISSED -> stringResource(R.string.review_history_dismissed, date)
        review.resolvedBy == ReviewResolvedBy.AUTO_THREE_MONTHS -> stringResource(R.string.review_history_auto, date, value)
        else -> stringResource(R.string.review_history_confirmed, date, value)
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

private fun formatDate(date: LocalDateTime): String =
    date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PartyPickerSheet(
    suggestions: List<Party>,
    onSearch: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: (existing: Party?, typedName: String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf<Party?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.change_party_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            PartyAutocompleteField(
                value = text,
                onValueChange = {
                    text = it
                    picked = null
                    onSearch(it)
                },
                suggestions = suggestions,
                onSelect = {
                    text = it.name
                    picked = it
                },
                selectedPartyId = picked?.id,
                shape = RoundedCornerShape(16.dp)
            )
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = { onSave(picked, text.trim()) },
                enabled = text.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.party_save)) }
        }
    }
}
