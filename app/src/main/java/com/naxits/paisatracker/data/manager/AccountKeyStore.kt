package com.naxits.paisatracker.data.manager

import android.content.Context
import androidx.core.content.edit
import com.naxits.paisatracker.data.preferences.UserPreferencesRepository
import com.naxits.paisatracker.domain.model.AccountOrdering
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Preferences that remember an account by its key (see [AccountOrdering.keyOf]): its place in the
 * user's order, whether it is hidden, and whether it is the main account.
 */
interface AccountKeyStore {
    /** Moves everything remembered under [oldKey] to [newKey]. */
    suspend fun rename(oldKey: String, newKey: String)
}

@Singleton
class PrefsAccountKeyStore @Inject constructor(
    @ApplicationContext context: Context,
    private val userPreferencesRepository: UserPreferencesRepository
) : AccountKeyStore {

    private val sharedPrefs = context.getSharedPreferences("account_prefs", Context.MODE_PRIVATE)

    override suspend fun rename(oldKey: String, newKey: String) {
        if (oldKey == newKey) return

        val order = userPreferencesRepository.accountOrder.first()
        val renamed = AccountOrdering.rename(order, oldKey, newKey)
        if (renamed != order) userPreferencesRepository.updateAccountOrder(renamed)

        val hidden = sharedPrefs.getStringSet("hidden_accounts", emptySet()).orEmpty()
        if (oldKey in hidden) {
            // The set returned by getStringSet must not be modified, so write a copy
            sharedPrefs.edit { putStringSet("hidden_accounts", hidden - oldKey + newKey) }
        }

        if (sharedPrefs.getString("main_account", null) == oldKey) {
            sharedPrefs.edit { putString("main_account", newKey) }
        }
    }
}
