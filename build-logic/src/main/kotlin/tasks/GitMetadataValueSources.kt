package tasks

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.process.ExecOperations

interface GitValueSourceParameters : ValueSourceParameters {
    @get:InputFiles
    @get:Optional
    val gitFiles: ListProperty<RegularFile>
}

abstract class GitCommitCountValueSource : ValueSource<Int, GitValueSourceParameters> {
    @get:Inject
    abstract val execOperations: ExecOperations

    override fun obtain(): Int {
        val output = ByteArrayOutputStream()
        execOperations.exec {
            commandLine("git", "rev-list", "HEAD", "--count")
            standardOutput = output
        }
        val count = output.toString(StandardCharsets.UTF_8).trim().toInt()
        check(count > 0) { "Git commit count must be greater than zero, but was $count" }
        return count
    }
}

abstract class GitHashValueSource : ValueSource<String, GitValueSourceParameters> {
    @get:Inject
    abstract val execOperations: ExecOperations

    override fun obtain(): String {
        return try {
            val output = ByteArrayOutputStream()
            execOperations.exec {
                commandLine("git", "rev-parse", "HEAD")
                standardOutput = output
            }
            val hash = output.toString(StandardCharsets.UTF_8).trim()
            hash.take(10)
        } catch (ignored: Exception) {
            ""
        }
    }
}

abstract class GitDirtyFlagValueSource : ValueSource<String, GitValueSourceParameters> {
    @get:Inject
    abstract val execOperations: ExecOperations

    override fun obtain(): String {
        return try {
            val output = ByteArrayOutputStream()
            execOperations.exec {
                commandLine("git", "status", "--porcelain")
                standardOutput = output
            }
            val status = output.toString(StandardCharsets.UTF_8).trim()
            if (status.isBlank()) "" else " (dirty)"
        } catch (ignored: Exception) {
            " (unknown status)"
        }
    }
}
