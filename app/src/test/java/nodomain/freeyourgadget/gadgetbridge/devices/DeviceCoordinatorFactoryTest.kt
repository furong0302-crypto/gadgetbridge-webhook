package nodomain.freeyourgadget.gadgetbridge.devices

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import nodomain.freeyourgadget.gadgetbridge.model.DeviceType
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import java.util.function.Supplier

class DeviceCoordinatorFactoryTest {
    private val factory = DeviceCoordinatorFactory()

    private fun builder() = mockk<Supplier<DeviceCoordinator>> {
        every { get() } answers { mockk<AbstractDeviceCoordinator>() }
    }

    @Test
    fun theSameTypeReturnsTheSameInstanceAndBuildsOnce() {
        val builder = builder()

        val first = factory.get(DeviceType.TEST, builder)
        val second = factory.get(DeviceType.TEST, builder)

        assertSame(first, second)
        verify(exactly = 1) { builder.get() }
    }

    @Test
    fun differentTypesGetDifferentInstances() {
        val test = factory.get(DeviceType.TEST, builder())
        val unknown = factory.get(DeviceType.UNKNOWN, builder())

        assertNotSame(test, unknown)
    }
}
