package tasks

import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations

abstract class BuildGitChangelogTask : DefaultTask() {
    @get:Inject
    abstract val execOperations: ExecOperations

    @get:InputFiles
    @get:Optional
    abstract val gitFiles: ListProperty<RegularFile>

    @get:InputFile
    @get:Optional
    abstract val gitHead: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Input
    abstract val versionCode: Property<Int>

    @TaskAction
    fun buildChangelog() {
        val outputFile = outputDirectory.get().file("xml/changelog_git.xml").asFile
        outputFile.parentFile.mkdirs()

        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<changelog>\n")

        try {
            val stdout = ByteArrayOutputStream()
            execOperations.exec {
                commandLine("git", "log", "-n", "300", "--pretty=format:%H %s")
                standardOutput = stdout
            }
            val allCommits = stdout.toString(StandardCharsets.UTF_8)

            var commitVersionCode = versionCode.orNull
            var includedCommits = 0
            var includedTranslations = 0

            for (line in allCommits.trim().split("\r?\n".toRegex())) {
                if (includedCommits >= 100) {
                    break
                }

                val parts = line.split(" ", limit = 2)

                val fullCommitHash = parts[0]
                val commitMessage = parts.getOrElse(1) { "" }
                    .replace(Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]"), "")
                val commitHash = fullCommitHash.take(10)
                val currentVersionCode = commitVersionCode?.also { commitVersionCode-- }

                if (commitMessage.contains("Translated using Weblate")) {
                    if (includedTranslations == 0) {
                        sb.append("  <release version=\"${escapeXml(commitHash)}\"")
                        if (currentVersionCode != null) {
                            sb.append(" versioncode=\"$currentVersionCode\"")
                        }
                        sb.append(">\n")
                        sb.append("    <change>${escapeXml("Sync translations from Weblate")}</change>\n")
                        sb.append("  </release>\n")
                    }
                    includedTranslations++
                    continue
                }

                sb.append("  <release version=\"${escapeXml(commitHash)}\"")
                if (currentVersionCode != null) {
                    sb.append(" versioncode=\"$currentVersionCode\"")
                }
                sb.append(">\n")
                sb.append("    <change>${escapeXml(commitMessage)}</change>\n")
                sb.append("  </release>\n")
                includedCommits++
            }
        } catch (ignored: Exception) {
            logger.warn("Skipping git changelog: git is not available")
        }

        sb.append("</changelog>")

        OutputStreamWriter(Files.newOutputStream(outputFile.toPath()), StandardCharsets.UTF_8).use { writer ->
            writer.write(sb.toString())
        }
    }

    companion object {
        fun escapeXml(text: String): String {
            return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;")
        }
    }
}
