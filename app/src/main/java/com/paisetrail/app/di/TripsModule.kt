package com.paisetrail.app.di

import com.paisetrail.app.trips.HomeLocationStore
import com.paisetrail.app.trips.HomeLocationStoreImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class TripsModule {
    @Binds
    abstract fun bindHomeLocationStore(impl: HomeLocationStoreImpl): HomeLocationStore
}
