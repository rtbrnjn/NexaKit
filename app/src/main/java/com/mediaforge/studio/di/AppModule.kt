package com.mediaforge.studio.di

import android.content.Context
import com.mediaforge.studio.ai.AiRouter
import com.mediaforge.studio.ai.GeminiClient
import com.mediaforge.studio.ai.GroqClient
import com.mediaforge.studio.data.Connectivity
import com.mediaforge.studio.data.HistoryStore
import com.mediaforge.studio.data.SettingsStore
import com.mediaforge.studio.data.db.AiHistoryDao
import com.mediaforge.studio.data.db.NexaDatabase
import com.mediaforge.studio.media.ImageCompressor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @AppScope
    fun provideAppScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun provideSettingsStore(
        @ApplicationContext context: Context,
        @AppScope scope: CoroutineScope
    ): SettingsStore = SettingsStore(context, scope)

    @Provides
    @Singleton
    fun provideHistoryStore(@ApplicationContext context: Context): HistoryStore = HistoryStore(context)

    @Provides
    @Singleton
    fun provideConnectivity(@ApplicationContext context: Context): Connectivity = Connectivity(context)

    @Provides
    @Singleton
    fun provideGeminiClient(): GeminiClient = GeminiClient()

    @Provides
    @Singleton
    fun provideGroqClient(): GroqClient = GroqClient()

    @Provides
    @Singleton
    fun provideAiRouter(groq: GroqClient, gemini: GeminiClient): AiRouter = AiRouter(groq, gemini)

    @Provides
    @Singleton
    fun provideNexaDatabase(@ApplicationContext context: Context): NexaDatabase = NexaDatabase.get(context)

    @Provides
    @Singleton
    fun provideAiHistoryDao(db: NexaDatabase): AiHistoryDao = db.aiHistoryDao()

    @Provides
    @Singleton
    fun provideImageCompressor(@ApplicationContext context: Context): ImageCompressor = ImageCompressor(context)
}
