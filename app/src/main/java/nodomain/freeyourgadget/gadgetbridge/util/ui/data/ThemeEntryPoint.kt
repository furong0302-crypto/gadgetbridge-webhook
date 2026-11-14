package nodomain.freeyourgadget.gadgetbridge.util.ui.data

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Gives classes that Hilt doesn't create access to the [ThemeRepository].
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ThemeEntryPoint {
    fun themeRepository(): ThemeRepository

    companion object {
        fun themeRepository(context: Context): ThemeRepository = EntryPointAccessors
            .fromApplication(context.applicationContext, ThemeEntryPoint::class.java)
            .themeRepository()
    }
}
