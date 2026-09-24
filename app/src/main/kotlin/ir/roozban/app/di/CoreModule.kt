package ir.roozban.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ir.roozban.core.common.Dispatcher
import ir.roozban.core.common.RoozbanDispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.time.Clock

@Module
@InstallIn(SingletonComponent::class)
object CoreModule {

    /** Unscoped: every consumer reads the current default time zone when it is created. */
    @Provides
    fun clock(): Clock = Clock.systemDefaultZone()

    @Provides
    @Dispatcher(RoozbanDispatchers.IO)
    fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Dispatcher(RoozbanDispatchers.Default)
    fun defaultDispatcher(): CoroutineDispatcher = Dispatchers.Default
}
