package ir.roozban.ai.runtime.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ir.roozban.ai.runtime.ModelManager
import ir.roozban.core.domain.DownloadSettings

@Module
@InstallIn(SingletonComponent::class)
abstract class RuntimeModule {
    @Binds
    abstract fun downloadSettings(impl: ModelManager): DownloadSettings
}
