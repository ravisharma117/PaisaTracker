package com.naxits.paisatracker.di

import com.naxits.paisatracker.data.manager.AccountKeyStore
import com.naxits.paisatracker.data.manager.PrefsAccountKeyStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AccountModule {

    @Binds
    @Singleton
    abstract fun bindAccountKeyStore(impl: PrefsAccountKeyStore): AccountKeyStore
}
