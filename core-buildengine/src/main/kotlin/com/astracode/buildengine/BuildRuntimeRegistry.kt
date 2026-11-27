package com.astracode.buildengine

import java.io.File
import java.util.Properties

enum class BuildRuntimeKind {
    JDK,
    ANDROID_SDK,
    ANDROID_NDK,
}

enum class BuildRuntimeArchitecture {
    ARM64,
    ARM32,
    X86_64,
    X86,
    UNKNOWN;

    companion object {
        fun from(value: String?): BuildRuntimeArchitecture {
            val arch = value.orEmpty().trim().lowercase()
            return when {
                arch in setOf("aarch64", "arm64", "arm64-v8a") || arch.contains("aarch64") ->
                    ARM64
                arch in setOf("arm", "arm32", "armeabi-v7a") || arch.startsWith("armv7") ->
                    ARM32
                arch in setOf("x86_64", "amd64", "x64") -> X86_64
                arch in setOf("x86", "i386", "i686") -> X86
                else -> UNKNOWN
            }
        }
    }
}

enum class BuildRuntimeState {
    READY,
    INVALID,
    INCOMPATIBLE_ARCHITECTURE,
    UNKNOWN_ARCHITECTURE,
}

data class BuildRuntime(
    val id: String,
    val kind: BuildRuntimeKind,
    val version: String,
    val home: File,
    val architecture: BuildRuntimeArchitecture,
    val state: BuildRuntimeState,
    val executable: File?,
    val vendor: String? = null,
) {
    val isUsable: Boolean
        get() = state == BuildRuntimeState.READY && executable?.canExecute() == true
}

/**
 * Discovers installed runtimes without pretending that Android ART is a JDK.
 * A JDK is usable only when its Java binary is executable and its declared
 * architecture matches the host. This class does not download or install JDKs.
 */
class BuildRuntimeRegistry(
    private val runtimeRoot: File,
    private val hostArchitecture: BuildRuntimeArchitecture =
        BuildRuntimeArchitecture.from(System.getProperty("os.arch")),
) {
    fun discoverJdks(): List<BuildRuntime> {
        if (!runtimeRoot.isDirectory) return emptyList()

        val homes = linkedSetOf<File>()
        if (isJdkHome(runtimeRoot)) homes += runtimeRoot
        runtimeRoot.listFiles()
            .orEmpty()
            .filter { it.isDirectory }
            .take(MAX_RUNTIME_DIRECTORIES)
            .forEach { candidate ->
                if (isJdkHome(candidate)) {
                    homes += candidate
                } else {
                    candidate.listFiles()
                        .orEmpty()
                        .filter { it.isDirectory }
                        .take(MAX_RUNTIME_DIRECTORIES)
                        .filter(::isJdkHome)
                        .forEach { homes += it }
                }
            }

        return homes.map(::describeJdk).sortedWith(
            compareBy<BuildRuntime> { it.state.ordinal }
                .thenBy { it.version }
                .thenBy { it.id },
        )
    }

    fun findUsableJdk(): BuildRuntime? =
        discoverJdks().firstOrNull { it.isUsable }

    private fun describeJdk(home: File): BuildRuntime {
        val canonicalHome = runCatching { home.canonicalFile }.getOrDefault(home.absoluteFile)
        val release = readReleaseFile(File(canonicalHome, "release"))
        val executable = File(canonicalHome, if (isWindows()) "bin/java.exe" else "bin/java")
        val declaredArchitecture = release.getProperty("OS_ARCH")
            ?.trim()
            ?.trim('"')
            ?.takeIf { it.isNotBlank() }
            ?: release.getProperty("JAVA_ARCH")
                ?.trim()
                ?.trim('"')
                ?.takeIf { it.isNotBlank() }
        val architecture = BuildRuntimeArchitecture.from(
            declaredArchitecture ?: inferArchitectureFromDirectory(canonicalHome.name),
        )
        val state = when {
            !executable.isFile || !executable.canExecute() -> BuildRuntimeState.INVALID
            architecture == BuildRuntimeArchitecture.UNKNOWN ->
                BuildRuntimeState.UNKNOWN_ARCHITECTURE
            architecture != hostArchitecture -> BuildRuntimeState.INCOMPATIBLE_ARCHITECTURE
            else -> BuildRuntimeState.READY
        }
        val version = release.getProperty("JAVA_VERSION")
            ?.trim('"')
            ?.takeIf { it.isNotBlank() }
            ?: "unknown"
        return BuildRuntime(
            id = canonicalHome.name,
            kind = BuildRuntimeKind.JDK,
            version = version,
            home = canonicalHome,
            architecture = architecture,
            state = state,
            executable = executable.takeIf { it.isFile },
            vendor = release.getProperty("IMPLEMENTOR")?.trim('"'),
        )
    }

    private fun readReleaseFile(file: File): Properties {
        val result = Properties()
        if (!file.isFile || file.length() > MAX_RELEASE_FILE_BYTES) return result
        runCatching {
            file.inputStream().buffered().use { stream -> result.load(stream) }
        }
        return result
    }

    private fun isJdkHome(directory: File): Boolean {
        val javaExecutable = File(directory, if (isWindows()) "bin/java.exe" else "bin/java")
        val javacExecutable = File(directory, if (isWindows()) "bin/javac.exe" else "bin/javac")
        return javaExecutable.isFile && javacExecutable.isFile &&
            File(directory, "release").isFile
    }

    private fun inferArchitectureFromDirectory(name: String): String? {
        val normalized = name.lowercase()
        return when {
            "aarch64" in normalized || "arm64" in normalized -> "aarch64"
            "x86_64" in normalized || "amd64" in normalized -> "x86_64"
            "arm32" in normalized || "armv7" in normalized -> "arm"
            "x86" in normalized -> "x86"
            else -> null
        }
    }

    private fun isWindows(): Boolean =
        System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

    private companion object {
        const val MAX_RUNTIME_DIRECTORIES = 64
        const val MAX_RELEASE_FILE_BYTES = 64 * 1024L
    }
}
