package com.paisetrail.app.di

import com.paisetrail.app.capture.notification.RedactedNotificationCounter
import com.paisetrail.app.capture.notification.RedactedNotificationCounterImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class CaptureModule {
    @Binds
    abstract fun bindRedactedNotificationCounter(impl: RedactedNotificationCounterImpl): RedactedNotificationCounter
}
