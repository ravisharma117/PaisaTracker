package com.naxits.paisatracker.di

import android.content.Context
import com.naxits.paisatracker.data.service.LiteRtLmServiceImpl
import com.naxits.paisatracker.domain.service.LlmService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LlmModule {

    @Provides
    @Singleton
    fun provideLlmService(
        @ApplicationContext context: Context
    ): LlmService {
        return LiteRtLmServiceImpl(context)
    }
}
