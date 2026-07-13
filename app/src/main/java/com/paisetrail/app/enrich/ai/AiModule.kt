package com.paisetrail.app.enrich.ai

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class AiModule {
    @Binds
    abstract fun bindCategorySuggester(impl: GeminiNanoCategorySuggester): CategorySuggester
}
