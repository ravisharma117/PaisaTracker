package com.naxits.paisatracker.presentation.ui.features.vendors

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naxits.paisatracker.data.database.dao.MerchantMappingDao
import com.naxits.paisatracker.data.database.entity.CategoryEntity
import com.naxits.paisatracker.data.database.entity.MerchantMappingEntity
import com.naxits.paisatracker.data.database.entity.SubcategoryEntity
import com.naxits.paisatracker.data.preferences.UserPreferencesRepository
import com.naxits.paisatracker.data.repository.CategoryRepository
import com.naxits.paisatracker.data.repository.MerchantMappingRepository
import com.naxits.paisatracker.data.repository.SubcategoryRepository
import com.naxits.paisatracker.domain.service.MerchantKey
import com.naxits.paisatracker.domain.service.MerchantKind
import com.naxits.paisatracker.domain.service.VendorSuggestion
import com.naxits.paisatracker.domain.service.VendorSuggestions
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RememberedVendorsState(
    /** Companies the user keeps paying under one category, offered for remembering. */
    val suggestions: List<VendorSuggestion> = emptyList(),
    /** Vendors whose category is applied to every future entry. */
    val remembered: List<MerchantMappingEntity> = emptyList(),
    val loaded: Boolean = false
)

/** A vendor was remembered and this many of its existing entries were filed under its category. */
data class VendorRefiled(val vendorName: String, val entries: Int)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RememberedVendorsViewModel @Inject constructor(
    private val mappingDao: MerchantMappingDao,
    private val repository: MerchantMappingRepository,
    private val preferences: UserPreferencesRepository,
    categoryRepository: CategoryRepository,
    subcategoryRepository: SubcategoryRepository
) : ViewModel() {

    val state: StateFlow<RememberedVendorsState> =
        combine(repository.getAllMappings(), preferences.ignoredVendorKeys) { mappings, ignored -> mappings to ignored }
            .mapLatest { (mappings, ignored) ->
                val rememberedKeys = mappings.map { it.matchKey ?: MerchantKey.normalize(it.merchantName) }.toSet()
                RememberedVendorsState(
                    suggestions = VendorSuggestions.build(
                        groups = mappingDao.vendorGroups(),
                        rememberedKeys = rememberedKeys,
                        ignoredKeys = ignored,
                        isBusiness = { MerchantKind.isBusiness(it) }
                    ),
                    remembered = mappings.sortedBy { it.merchantName.lowercase() },
                    loaded = true
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RememberedVendorsState())

    /** What a vendor can be filed under: spending categories, for the "Add a vendor" form. */
    val categories: StateFlow<List<CategoryEntity>> = categoryRepository.getExpenseCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val subcategories: StateFlow<Map<Long, List<SubcategoryEntity>>> = subcategoryRepository.subcategoriesMap

    private val _refiled = MutableSharedFlow<VendorRefiled>(extraBufferCapacity = 1)

    /** One event per vendor added, so the screen can say how many entries it updated. */
    val refiled: SharedFlow<VendorRefiled> = _refiled.asSharedFlow()

    /** Add a suggested vendor: its category is applied to every future entry and to all existing ones. */
    fun remember(suggestion: VendorSuggestion) = remember(suggestion.merchantName, suggestion.category, suggestion.subcategory)

    /** Add a vendor the user typed, with the category chosen for it. */
    fun add(name: String, category: String, subcategory: String?) {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || category.isBlank()) return
        remember(trimmed, category, subcategory)
    }

    private fun remember(name: String, category: String, subcategory: String?) {
        viewModelScope.launch {
            val updated = repository.rememberAndRefile(name, category, subcategory)
            preferences.setVendorIgnored(MerchantKey.normalize(name), false)
            _refiled.tryEmit(VendorRefiled(name, updated))
        }
    }

    /** Take the vendor off the list. Entries already saved keep their category; later ones go without it. */
    fun forget(mapping: MerchantMappingEntity) {
        viewModelScope.launch {
            repository.removeMapping(mapping.merchantName)
            // So it is not offered again straight away
            preferences.setVendorIgnored(mapping.matchKey ?: MerchantKey.normalize(mapping.merchantName), true)
        }
    }
}
