package com.par9uet.jm.utils

import org.junit.Assert.assertThrows
import org.junit.Test

class UpdatePackagePolicyTest {
    private val installed = UpdatePackageIdentity("example.reader", 10L, setOf("fixture-cert"))

    @Test
    fun acceptsOnlyNewerBuildSignedBySamePublisher() {
        UpdatePackagePolicy.validate(installed, installed.copy(versionCode = 11L))
    }

    @Test
    fun rejectsDifferentPackageSignatureUnsignedPackageAndDowngrades() {
        val newer = installed.copy(versionCode = 11L)
        for (candidate in listOf(
            newer.copy(packageName = "example.other"),
            newer.copy(signerDigests = setOf("other-cert")),
            newer.copy(signerDigests = emptySet()),
            installed,
            installed.copy(versionCode = 9L),
        )) {
            assertThrows(IllegalArgumentException::class.java) { UpdatePackagePolicy.validate(installed, candidate) }
        }
    }
}
