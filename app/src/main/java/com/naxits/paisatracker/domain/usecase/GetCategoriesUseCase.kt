package com.naxits.paisatracker.domain.usecase

import com.naxits.paisatracker.data.database.entity.CategoryEntity
import com.naxits.paisatracker.data.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetCategoriesUseCase @Inject constructor(
    private val categoryRepository: CategoryRepository
) {
    fun execute(): Flow<List<CategoryEntity>> {
        return categoryRepository.getAllCategories()
    }
}