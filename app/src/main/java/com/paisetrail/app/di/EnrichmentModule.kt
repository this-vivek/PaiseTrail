package com.paisetrail.app.di

import com.paisetrail.app.enrich.TransactionEnrichmentCoordinator
import com.paisetrail.app.enrich.TransactionEnrichmentTrigger
import com.paisetrail.app.interaction.TagPromptNotifier
import com.paisetrail.app.interaction.TagPromptNotifierImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class EnrichmentModule {
    @Binds
    abstract fun bindTransactionEnrichmentTrigger(
        impl: TransactionEnrichmentCoordinator,
    ): TransactionEnrichmentTrigger

    @Binds
    abstract fun bindTagPromptNotifier(impl: TagPromptNotifierImpl): TagPromptNotifier
}
