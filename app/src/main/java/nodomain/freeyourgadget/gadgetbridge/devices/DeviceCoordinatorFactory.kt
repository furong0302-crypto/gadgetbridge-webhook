package nodomain.freeyourgadget.gadgetbridge.devices

import nodomain.freeyourgadget.gadgetbridge.model.DeviceType
import java.util.function.Supplier
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds one coordinator per [DeviceType] for the life of the application. Each is built from its type's recipe on
 * first request. Callers get coordinators through [DeviceType.getDeviceCoordinator].
 */
@Singleton
class DeviceCoordinatorFactory @Inject constructor() {
    private val coordinators = ConcurrentHashMap<DeviceType, DeviceCoordinator>()

    /**
     * Returns [type]'s coordinator, building it with [builder] if this is the first request for [type].
     *
     * Building happens while the cache is being updated, so neither the coordinator's constructor nor the dependencies
     * it looks up may request a coordinator.
     */
    fun get(type: DeviceType, builder: Supplier<out DeviceCoordinator>): DeviceCoordinator =
        coordinators.computeIfAbsent(type) { builder.get() }
}
