package nodomain.freeyourgadget.gadgetbridge.di

import android.app.Application
import dagger.hilt.internal.GeneratedComponent
import dagger.hilt.internal.GeneratedComponentManager
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertSame

class RootEntryPointsTest {
    private interface SomeEntryPoint

    private val application = mockk<Application>(moreInterfaces = arrayOf(GeneratedComponentManager::class))

    @AfterEach
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
        val e = assertFailsWith<IllegalStateException> {
            RootEntryPoints.get(SomeEntryPoint::class.java)
        }

        val message = assertNotNull(e.message)
        assertContains(message, SomeEntryPoint::class.java.name)
    }

    @Test
    fun getAfterUnregisterThrows() {
        RootEntryPoints.register(application)
        RootEntryPoints.unregister()

        assertFailsWith<IllegalStateException> {
            RootEntryPoints.get(SomeEntryPoint::class.java)
        }
    }
}
