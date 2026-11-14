package nodomain.freeyourgadget.gadgetbridge.di

import javax.inject.Qualifier

/**
 * Marks the [kotlinx.coroutines.CoroutineScope] for work that lives as long as the application. It runs on
 * `Dispatchers.Main.immediate`, so move blocking work to the [IoDispatcher].
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
