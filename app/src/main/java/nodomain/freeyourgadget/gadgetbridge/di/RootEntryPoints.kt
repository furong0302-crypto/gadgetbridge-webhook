package nodomain.freeyourgadget.gadgetbridge.di

import android.app.Application
import dagger.hilt.EntryPoints

/**
 * The static way into the app's Hilt component, for classes Hilt can't build through their constructor: those created
 * with `new` or by reflection, such as model classes and subclasses that must keep a no-argument constructor. Such a
 * class looks up what it needs and passes it on through constructors, so the objects it builds don't look up
 * themselves. Only such classes call this, so it doesn't become a new service locator in place of `GBApplication`'s
 * statics.
 *
 * It's static because its callers have no instance or Context to reach Hilt through. The application registers itself
 * in `onCreate` and unregisters in `onTerminate`, so its state belongs to the running application.
 */
object RootEntryPoints {
    @Volatile
    private var application: Application? = null

    fun register(application: Application) {
        this.application = application
    }

    fun unregister() {
        application = null
    }

    /**
     * Returns [entryPoint] from the registered application's component.
     *
     * @throws IllegalStateException if no application is registered.
     */
    fun <T : Any> get(entryPoint: Class<T>): T {
        val application = checkNotNull(application) {
            "Can't get ${entryPoint.name}: no application is registered with RootEntryPoints"
        }
        return EntryPoints.get(application, entryPoint)
    }
}
