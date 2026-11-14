package nodomain.freeyourgadget.gadgetbridge.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import nodomain.freeyourgadget.gadgetbridge.activities.debug.data.DatabaseDebugRepository
import nodomain.freeyourgadget.gadgetbridge.activities.debug.data.DatabaseDebugRepositoryImpl
import nodomain.freeyourgadget.gadgetbridge.util.ui.data.ThemeRepository
import nodomain.freeyourgadget.gadgetbridge.util.ui.data.ThemeRepositoryImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    abstract fun bindDatabaseDebugRepository(impl: DatabaseDebugRepositoryImpl): DatabaseDebugRepository

    @Binds
    @Singleton
    abstract fun bindThemeRepository(impl: ThemeRepositoryImpl): ThemeRepository
}
