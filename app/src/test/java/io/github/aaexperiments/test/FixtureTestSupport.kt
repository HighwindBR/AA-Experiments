package io.github.aaexperiments.test

import org.junit.Assume.assumeTrue
import java.io.File

internal fun fixtureRootOrSkip(vararg requiredApkmFiles: String): File {
    val root = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
        .firstOrNull { File(it, "app/build.gradle").isFile && File(it, "fixtures/SHA256SUMS").isFile }
    assumeTrue("Could not locate the AA Experiments project root", root != null)

    val fixtureDirectory = File(requireNotNull(root), "fixtures/apks")
    val missing = requiredApkmFiles.filterNot { File(fixtureDirectory, it).isFile }
    assumeTrue("Private APKM fixtures unavailable: ${missing.joinToString()}", missing.isEmpty())
    return requireNotNull(root)
}
