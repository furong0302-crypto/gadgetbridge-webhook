package nodomain.freeyourgadget.gadgetbridge.devices

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import nodomain.freeyourgadget.gadgetbridge.di.RootEntryPoints
import nodomain.freeyourgadget.gadgetbridge.model.DeviceService
import nodomain.freeyourgadget.gadgetbridge.util.preferences.ScopedPrefsProvider
import org.junit.rules.ExternalResource

/**
 * Lets a plain JUnit test build coordinators with `new`: makes [RootEntryPoints] give [AbstractDeviceCoordinator]
 * these mocks while the test runs, and restores it afterwards. Stub them in the test that relies on them. A test builds
 * its coordinators in its test methods or `@Before`, not in field initializers, which run before any rule.
 *
 * Temporary, until coordinators take their dependencies through constructors. A coordinator's tests stop needing it
 * once that coordinator, and every class between it and [AbstractDeviceCoordinator], has a constructor that takes the
 * base's dependencies and passes them to its superclass's. The tests then build it with mocks directly.
 */
class MockCoordinatorDependencies : ExternalResource() {
    val deviceService = mockk<DeviceService>()
    val scopedPrefs = mockk<ScopedPrefsProvider>()
    val deviceManager = mockk<DeviceManager>()

    private val entryPoint = mockk<AbstractDeviceCoordinatorEntryPoint> {
        every { deviceService() } returns this@MockCoordinatorDependencies.deviceService
        every { scopedPrefs() } returns this@MockCoordinatorDependencies.scopedPrefs
        every { deviceManager() } returns this@MockCoordinatorDependencies.deviceManager
    }

    override fun before() {
        mockkObject(RootEntryPoints)
        every { RootEntryPoints.get(AbstractDeviceCoordinatorEntryPoint::class.java) } returns entryPoint
    }

    override fun after() {
        unmockkObject(RootEntryPoints)
    }
}
