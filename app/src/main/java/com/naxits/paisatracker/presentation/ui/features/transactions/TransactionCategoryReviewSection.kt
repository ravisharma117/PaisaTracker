package com.naxits.paisatracker.presentation.ui.features.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.naxits.paisatracker.presentation.ui.components.SubtitleTag
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The category the app suggested because the entry looks like the next one in a series, why it
 * thinks so, and what happened to earlier suggestions. Shown only when a category review exists.
 */
@Composable
fun TransactionCategoryReviewSection(
    transaction: TransactionEntity,
    onChangeCategory: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TransactionPartyViewModel = hiltViewModel()
) {
    LaunchedEffect(transaction.id, transaction.partyId) { viewModel.bind(transaction.id, transaction.partyId) }

    val reviews by viewModel.reviews.collectAsStateWithLifecycle()
    val categoryReviews = reviews.filter { it.kind == ReviewKind.CATEGORY }
    if (categoryReviews.isEmpty()) return

    val pending = categoryReviews.firstOrNull { it.isPending }

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
                        text = stringResource(R.string.category_review_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = transaction.category,
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
                }
            }

            if (pending != null) {
                Text(
                    text = stringResource(R.string.review_why, pending.reason),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (pending.confidence > 0) {
                    Text(
                        text = stringResource(R.string.review_confidence, pending.confidence),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { viewModel.confirmCategory(pending.id) }) { Text(stringResource(R.string.review_confirm)) }
                    OutlinedButton(onClick = onChangeCategory) { Text(stringResource(R.string.review_change)) }
                    TextButton(onClick = { viewModel.dismiss(pending.id) }) { Text(stringResource(R.string.review_dismiss)) }
                }
            }

            categoryReviews.filterNot { it.isPending }.forEach { CategoryHistoryLine(it) }
        }
    }
}

@Composable
private fun CategoryHistoryLine(review: TransactionReviewEntity) {
    val date = review.resolvedAt?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)).orEmpty()
    val value = review.resolvedValue ?: review.suggestedValue
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
