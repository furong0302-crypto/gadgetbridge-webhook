package nodomain.freeyourgadget.gadgetbridge.di

import android.app.Application
import dagger.hilt.internal.GeneratedComponent
import dagger.hilt.internal.GeneratedComponentManager
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RootEntryPointsTest {
    private interface SomeEntryPoint

    private val application = mockk<Application>(moreInterfaces = arrayOf(GeneratedComponentManager::class))

    @After
    fun unregister() {
        RootEntryPoints.unregister()
    }

    @Test
    fun getReturnsTheEntryPointFromTheRegisteredApplicationsComponent() {
        val component = mockk<SomeEntryPoint>(moreInterfaces = arrayOf(GeneratedComponent::class))
        every { (application as GeneratedComponentManager<*>).generatedComponent() } returns component
        RootEntryPoints.register(application)

        assertSame(component, RootEntryPoints.get(SomeEntryPoint::class.java))
    }

    @Test
    fun getWithoutARegisteredApplicationThrowsNamingTheEntryPoint() {
        val e = assertThrows(IllegalStateException::class.java) {
            RootEntryPoints.get(SomeEntryPoint::class.java)
        }

        assertTrue(e.message!!.contains(SomeEntryPoint::class.java.name))
    }

    @Test
    fun getAfterUnregisterThrows() {
        RootEntryPoints.register(application)
        RootEntryPoints.unregister()

        assertThrows(IllegalStateException::class.java) {
            RootEntryPoints.get(SomeEntryPoint::class.java)
        }
    }
}
