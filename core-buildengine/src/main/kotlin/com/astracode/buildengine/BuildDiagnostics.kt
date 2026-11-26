package com.astracode.buildengine

import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.util.zip.ZipFile

enum class BuildDiagnosticSeverity {
    ERROR,
    WARNING,
    INFO,
}

data class BuildDiagnostic(
    val path: String,
    val line: Int,
    val column: Int?,
    val severity: BuildDiagnosticSeverity,
    val message: String,
    val rawLine: String,
)

class BuildDiagnosticParser {
    private val kotlinDiagnostic = Regex(
        """^(?:e|w):\s*(.+?\.(?:kt|java)):\s*\((\d+),\s*(\d+)\):\s*(.*)$""",
        RegexOption.IGNORE_CASE,
    )
    private val javaDiagnostic = Regex(
        """^(.+?\.(?:java|kt)):(\d+)(?::(\d+))?:\s*(error|warning):\s*(.*)$""",
        RegexOption.IGNORE_CASE,
    )

    fun parse(output: String, workspaceRoot: File): List<BuildDiagnostic> {
        val root = runCatching { workspaceRoot.canonicalFile }.getOrNull() ?: return emptyList()
        if (!root.isDirectory) return emptyList()
        val diagnostics = mutableListOf<BuildDiagnostic>()

        output.lineSequence().forEach { raw ->
            if (diagnostics.size >= MAX_DIAGNOSTICS) return@forEach
            val line = raw.trim()
            val kotlinMatch = kotlinDiagnostic.matchEntire(line)
            if (kotlinMatch != null) {
                val path = resolveWorkspacePath(root, kotlinMatch.groupValues[1]) ?: return@forEach
                diagnostics += BuildDiagnostic(
                    path = path,
                    line = kotlinMatch.groupValues[2].toIntOrNull() ?: return@forEach,
                    column = kotlinMatch.groupValues[3].toIntOrNull(),
                    severity = severityFromPrefix(line),
                    message = kotlinMatch.groupValues[4].trim(),
                    rawLine = raw.take(MAX_RAW_LINE_CHARS),
                )
                return@forEach
            }

            val javaMatch = javaDiagnostic.matchEntire(line) ?: return@forEach
            val path = resolveWorkspacePath(root, javaMatch.groupValues[1]) ?: return@forEach
            diagnostics += BuildDiagnostic(
                path = path,
                line = javaMatch.groupValues[2].toIntOrNull() ?: return@forEach,
                column = javaMatch.groupValues[3].toIntOrNull(),
                severity = if (javaMatch.groupValues[4].equals("warning", true)) {
                    BuildDiagnosticSeverity.WARNING
                } else {
                    BuildDiagnosticSeverity.ERROR
                },
                message = javaMatch.groupValues[5].trim(),
                rawLine = raw.take(MAX_RAW_LINE_CHARS),
            )
        }
        return diagnostics
    }

    private fun severityFromPrefix(raw: String): BuildDiagnosticSeverity =
        if (raw.trimStart().startsWith("w:", ignoreCase = true)) {
            BuildDiagnosticSeverity.WARNING
        } else {
            BuildDiagnosticSeverity.ERROR
        }

    private fun resolveWorkspacePath(root: File, reportedPath: String): String? {
        val normalized = reportedPath.trim().trim('"', '\'')
        val file = runCatching {
            if (normalized.startsWith("file:", ignoreCase = true)) {
                File(URI(normalized))
            } else {
                File(normalized)
            }
        }.getOrElse { return null }

        val canonical = runCatching {
            (if (file.isAbsolute) file else File(root, normalized)).canonicalFile
        }.getOrElse { return null }
        if (!canonical.toPath().normalize().startsWith(root.toPath().normalize())) return null
        return root.toPath().relativize(canonical.toPath()).toString().replace(File.separatorChar, '/')
    }

    private companion object {
        const val MAX_DIAGNOSTICS = 2_000
        const val MAX_RAW_LINE_CHARS = 2_000
    }
}

enum class BuildArtifactKind {
    APK,
    AAB,
    JAR,
    AAR,
    ZIP,
    UNKNOWN,
}

data class BuildArtifact(
    val file: File,
    val kind: BuildArtifactKind,
    val sizeBytes: Long,
    val sha256: String,
    val valid: Boolean,
    val validationMessage: String?,
)

/**
 * Finds likely build outputs and performs structural checks. A passing archive
 * check does not prove that an APK/AAB is signed, installable, or Play-ready.
 */
class BuildArtifactScanner(
    private val maximumFiles: Int = 2_000,
    private val maximumArtifactBytes: Long = 1024L * 1024L * 1024L,
) {
    init {
        require(maximumFiles in 1..20_000)
        require(maximumArtifactBytes in 1..(4L * 1024L * 1024L * 1024L))
    }

    fun scan(buildOutputDirectory: File): List<BuildArtifact> {
        if (!buildOutputDirectory.isDirectory) return emptyList()
        val root = runCatching { buildOutputDirectory.canonicalFile }.getOrNull() ?: return emptyList()
        val candidates = mutableListOf<File>()
        val pending = ArrayDeque<File>()
        pending.add(root)
        var visited = 0

        while (pending.isNotEmpty() && candidates.size < maximumFiles && visited < maximumFiles * 10) {
            val directory = pending.removeLast()
            for (entry in directory.listFiles().orEmpty()) {
                visited++
                if (visited > maximumFiles * 10) break
                val canonical = runCatching { entry.canonicalFile }.getOrNull() ?: continue
                if (!canonical.toPath().normalize().startsWith(root.toPath().normalize())) continue

                when {
                    canonical.isDirectory && !canonical.isHidden -> pending.add(canonical)
                    canonical.isFile && canonical.length() in 1..maximumArtifactBytes &&
                        artifactKind(canonical) != BuildArtifactKind.UNKNOWN -> candidates += canonical
                }
                if (candidates.size >= maximumFiles) break
            }
        }

        return candidates.distinctBy { it.absolutePath }
            .map(::inspect)
            .sortedByDescending { it.file.lastModified() }
    }

    private fun inspect(file: File): BuildArtifact {
        val kind = artifactKind(file)
        val validation = validateArchive(file, kind)
        return BuildArtifact(
            file = file,
            kind = kind,
            sizeBytes = file.length(),
            sha256 = sha256(file),
            valid = validation == null,
            validationMessage = validation,
        )
    }

    private fun validateArchive(file: File, kind: BuildArtifactKind): String? {
        if (kind == BuildArtifactKind.UNKNOWN) return "Unsupported artifact type"
        return runCatching {
            ZipFile(file).use { zip ->
                val entries = zip.entries()
                var count = 0
                var hasManifest = false
                var hasBundleConfig = false
                var hasJarContent = false
                var hasClassesJar = false
                while (entries.hasMoreElements() && count < MAX_ZIP_ENTRIES) {
                    val entry = entries.nextElement()
                    count++
                    when (entry.name) {
                        "AndroidManifest.xml",
                        "base/manifest/AndroidManifest.xml" -> hasManifest = true
                        "BundleConfig.pb" -> hasBundleConfig = true
                        "META-INF/MANIFEST.MF" -> hasJarContent = true
                        "classes.jar" -> hasClassesJar = true
                    }
                    if (entry.name.endsWith(".class")) hasJarContent = true
                }
                when (kind) {
                    BuildArtifactKind.APK ->
                        if (!hasManifest) "APK is missing AndroidManifest.xml" else null
                    BuildArtifactKind.AAB ->
                        if (!hasBundleConfig || !hasManifest) {
                            "AAB is missing BundleConfig.pb or the base Android manifest"
                        } else null
                    BuildArtifactKind.JAR ->
                        if (!hasJarContent) "JAR has no manifest or class entries" else null
                    BuildArtifactKind.AAR ->
                        if (!hasClassesJar) "AAR is missing classes.jar" else null
                    BuildArtifactKind.ZIP ->
                        if (count == 0) "ZIP archive is empty" else null
                    BuildArtifactKind.UNKNOWN -> "Unsupported artifact type"
                }
            }
        }.getOrElse { "Artifact is not a readable ZIP archive: " + (it.message ?: "invalid archive") }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun artifactKind(file: File): BuildArtifactKind =
        when (file.extension.lowercase()) {
            "apk" -> BuildArtifactKind.APK
            "aab" -> BuildArtifactKind.AAB
            "jar" -> BuildArtifactKind.JAR
            "aar" -> BuildArtifactKind.AAR
            "zip" -> BuildArtifactKind.ZIP
            else -> BuildArtifactKind.UNKNOWN
        }

    private companion object {
        const val MAX_ZIP_ENTRIES = 100_000
    }
}
