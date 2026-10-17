package com.naxits.paisatracker.presentation.common.icons

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.naxits.paisatracker.domain.model.AccountMeta
import com.naxits.paisatracker.domain.model.AccountOrdering

/**
 * The user's notes on each account (icon, tags, description), keyed like [AccountOrdering.keyOf].
 * Provided once at the top of the app so any account icon can find its override without every
 * screen passing it down.
 */
val LocalAccountMeta = staticCompositionLocalOf<Map<String, AccountMeta>> { emptyMap() }

/** The icon the user picked for this account, or null to use the logo found from its bank name. */
@Composable
fun accountIconOverrideFor(bankName: String, accountLast4: String): String? =
    LocalAccountMeta.current[AccountOrdering.keyOf(bankName, accountLast4)]?.customIconName
