package tasks

import javax.inject.Inject
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity

abstract class BaseGeneratorTask @Inject constructor(
    providers: ProviderFactory
) : JavaExec() {
    @get:OutputDirectory
    abstract val generatedSourceDirectory: DirectoryProperty

    @get:Input
    abstract val verbose: Property<Boolean>

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Optional
    abstract val inputDirectory: DirectoryProperty

    init {
        val verboseProp = providers.gradleProperty("verboseLogLevel")
            .map { it.toBoolean() }
            .orElse(false)
        verbose.convention(verboseProp)
        jvmArguments.addAll(verbose.map { if (it) listOf("-Dverbose=true") else emptyList() })
    }
}
