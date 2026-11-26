package com.astracode.buildengine

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BuildRuntimeRegistryTest {
    @Test
    fun discoversAnExecutableJdkForMatchingArm64Architecture() {
        val root = createTempDir(prefix = "astracode-jdks-")
        try {
            val jdk = createJdk(root, "jdk-21-arm64", "aarch64")
            val runtime = BuildRuntimeRegistry(root, BuildRuntimeArchitecture.ARM64)
                .discoverJdks()
                .single()

            assertEquals("21.0.1", runtime.version)
            assertEquals(BuildRuntimeArchitecture.ARM64, runtime.architecture)
            assertEquals(BuildRuntimeState.READY, runtime.state)
            assertTrue(runtime.isUsable)
            assertEquals(jdk.canonicalFile, runtime.home.canonicalFile)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun doesNotSelectAnIncompatibleJdkArchitecture() {
        val root = createTempDir(prefix = "astracode-jdks-")
        try {
            createJdk(root, "jdk-x64", "x86_64")
            val runtime = BuildRuntimeRegistry(root, BuildRuntimeArchitecture.ARM64)
                .discoverJdks()
                .single()

            assertEquals(BuildRuntimeState.INCOMPATIBLE_ARCHITECTURE, runtime.state)
            assertFalse(runtime.isUsable)
            assertEquals(null, BuildRuntimeRegistry(root, BuildRuntimeArchitecture.ARM64).findUsableJdk())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun ignoresIncompleteJdkDirectories() {
        val root = createTempDir(prefix = "astracode-jdks-")
        try {
            val bin = File(root, "partial/bin")
            assertTrue(bin.mkdirs())
            File(bin, "java").writeText("not a complete JDK")
            val discovered = BuildRuntimeRegistry(root, BuildRuntimeArchitecture.ARM64).discoverJdks()
            assertTrue(discovered.isEmpty())
        } finally {
            root.deleteRecursively()
        }
    }

    private fun createJdk(root: File, name: String, arch: String): File {
        val home = File(root, name)
        val bin = File(home, "bin")
        assertTrue(bin.mkdirs())
        val java = File(bin, "java")
        val javac = File(bin, "javac")
        java.writeText("#!/bin/sh\nexit 0\n")
        javac.writeText("#!/bin/sh\nexit 0\n")
        assertTrue(java.setExecutable(true))
        assertTrue(javac.setExecutable(true))
        File(home, "release").writeText(
            "JAVA_VERSION=\"21.0.1\"\nOS_ARCH=\"$arch\"\nIMPLEMENTOR=\"Test JDK\"\n",
        )
        assertNotNull(java)
        return home
    }
}
