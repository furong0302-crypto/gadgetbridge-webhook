package nodomain.freeyourgadget.gadgetbridge.devices

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import nodomain.freeyourgadget.gadgetbridge.model.DeviceService
import nodomain.freeyourgadget.gadgetbridge.util.preferences.ScopedPrefsProvider

/**
 * Gives a coordinator built with `new` the dependencies [AbstractDeviceCoordinator] offers.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AbstractDeviceCoordinatorEntryPoint {
    fun deviceService(): DeviceService

    fun scopedPrefs(): ScopedPrefsProvider

    fun deviceManager(): DeviceManager
}
