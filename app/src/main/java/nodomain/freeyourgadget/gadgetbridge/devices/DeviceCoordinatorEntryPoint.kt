package nodomain.freeyourgadget.gadgetbridge.devices

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Gives [nodomain.freeyourgadget.gadgetbridge.model.DeviceType] its [DeviceCoordinatorFactory].
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface DeviceCoordinatorEntryPoint {
    fun deviceCoordinatorFactory(): DeviceCoordinatorFactory
}
