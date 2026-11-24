package tasks

import java.io.File
import javax.inject.Inject
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory

abstract class GitMetadataExtension @Inject constructor(
    private val providers: ProviderFactory,
    private val layout: ProjectLayout
) {
    private fun resolveGitDir(): File? {
        val root = layout.projectDirectory.asFile.let { dir ->
            var current: File? = dir
            var foundGit: File? = null
            while (current != null) {
                val gitCandidate = File(current, ".git")
                if (gitCandidate.exists()) {
                    foundGit = gitCandidate
                    break
                }
                current = current.parentFile
            }
            foundGit?.parentFile ?: dir
        }
        val gitDir = File(root, ".git")
        return if (gitDir.isDirectory) {
            gitDir
        } else if (gitDir.isFile) {
            val gitDirPath = gitDir.readText().trim().replaceFirst(Regex("^gitdir:\\s*"), "")
            if (File(gitDirPath).isAbsolute) File(gitDirPath) else File(root, gitDirPath).canonicalFile
        } else {
            null
        }
    }

    private fun resolveGitTrackedFiles(): List<File> {
        val gitDir = resolveGitDir() ?: return emptyList()
        val trackedFiles = mutableListOf<File>()
        val headFile = File(gitDir, "HEAD")
        if (headFile.exists()) {
            trackedFiles.add(headFile)
            try {
                val headContent = headFile.readText().trim()
                if (headContent.startsWith("ref:")) {
                    val refRelativePath = headContent.substringAfter("ref:").trim()
                    val refFile = File(gitDir, refRelativePath)
                    if (refFile.exists()) {
                        trackedFiles.add(refFile)
                    }
                }
            } catch (_: Exception) {
            }
        }
        val packedRefs = File(gitDir, "packed-refs")
        if (packedRefs.exists()) {
            trackedFiles.add(packedRefs)
        }
        return trackedFiles
    }

    val gitHeadFileProvider: Provider<File> = providers.provider {
        val gitDir = resolveGitDir()
        if (gitDir != null) {
            val headFile = File(gitDir, "HEAD")
            if (headFile.exists()) headFile else null
        } else {
            null
        }
    }

    val gitHeadRegularFile: Provider<RegularFile> = gitHeadFileProvider.map { f ->
        f?.let { layout.projectDirectory.file(it.absolutePath) }
    }

    val gitTrackedFilesProvider: Provider<List<File>> = providers.provider {
        resolveGitTrackedFiles()
    }

    val gitFiles: Provider<List<RegularFile>> = gitTrackedFilesProvider.map { files ->
        files.map { layout.projectDirectory.file(it.absolutePath) }
    }

    val commitCount: Provider<Int> = providers.of(GitCommitCountValueSource::class.java) {
        parameters.gitFiles.set(gitFiles)
    }

    val gitHashShort: Provider<String> = providers.of(GitHashValueSource::class.java) {
        parameters.gitFiles.set(gitFiles)
    }

    val gitDirtyFlag: Provider<String> = providers.of(GitDirtyFlagValueSource::class.java) {
        parameters.gitFiles.set(gitFiles)
    }
}
