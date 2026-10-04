package io.github.aaexperiments.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.io.File

class BuildFingerprintTest {
    @Test fun fingerprintCoversVersionBaseAndSortedSplits() {
        val dir = createTempDir(prefix = "aaxp-fingerprint-")
        try {
            val base = File(dir, "base.apk").apply { writeText("base") }
            val a = File(dir, "a.apk").apply { writeText("a") }
            val b = File(dir, "b.apk").apply { writeText("b") }
            val first = BuildFingerprint.compute(10, base, listOf(a, b))
            val reordered = BuildFingerprint.compute(10, base, listOf(b, a))
            assertEquals(first.fingerprintSha256, reordered.fingerprintSha256)
            b.writeText("changed")
            assertNotEquals(first.fingerprintSha256, BuildFingerprint.compute(10, base, listOf(a, b)).fingerprintSha256)
            assertNotEquals(first.fingerprintSha256, BuildFingerprint.compute(11, base, listOf(a, File(dir, "b.apk"))).fingerprintSha256)
        } finally { dir.deleteRecursively() }
    }
}
