/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.tooling.core

import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion.Maturity.*
import org.junit.Test
import kotlin.test.*

class KotlinToolingVersionTest {

    @Test
    fun compareMajorVersions() {
        assertTrue(
            KotlinToolingVersion("1.6.0") < KotlinToolingVersion("2.0.0")
        )

        assertTrue(
            KotlinToolingVersion("2.0.0") > KotlinToolingVersion("1.0.0")
        )

        assertEquals(0, KotlinToolingVersion("2.0.0").compareTo(KotlinToolingVersion("2.0.0")))
    }

    @Test
    fun compareMinorVersions() {
        assertTrue(
            KotlinToolingVersion("1.6.20") > KotlinToolingVersion("1.5.30")
        )

        assertTrue(
            KotlinToolingVersion("1.5.30") < KotlinToolingVersion("1.6.20")
        )

        assertTrue(
            KotlinToolingVersion("1.7.20-dev-100") > KotlinToolingVersion("1.6.0")
        )

        assertTrue(
            KotlinToolingVersion("1.7.20-dev-100") > KotlinToolingVersion("1.6")
        )
    }

    @Test
    fun comparePatchVersions() {
        assertTrue(
            KotlinToolingVersion("1.7.20") > KotlinToolingVersion("1.7.10")
        )

        assertTrue(
            KotlinToolingVersion("1.7.10") < KotlinToolingVersion("1.7.20")
        )

        assertTrue(
            KotlinToolingVersion("1.7.10-beta2-200") > KotlinToolingVersion("1.7.0")
        )
    }

    @Test
    fun compareMaturity() {
        assertTrue(
            KotlinToolingVersion("1.7.0") > KotlinToolingVersion("1.7.0-rc")
        )

        assertTrue(
            KotlinToolingVersion("1.7.0-rc") > KotlinToolingVersion("1.7.0-beta")
        )

        assertTrue(
            KotlinToolingVersion("1.7.0-beta") > KotlinToolingVersion("1.7.0-alpha")
        )

        assertTrue(
            KotlinToolingVersion("1.7.0-alpha") > KotlinToolingVersion("1.7.0-m1")
        )

        assertTrue(
            KotlinToolingVersion("1.7.0-m1") > KotlinToolingVersion("1.7.0-dev")
        )

        assertTrue(
            KotlinToolingVersion("1.7.0-dev") > KotlinToolingVersion("1.7.0-snapshot")
        )

        assertEquals(
            0, KotlinToolingVersion("1.7.20-dev").compareTo(KotlinToolingVersion("1.7.20-pub"))
        )
    }

    @Test
    fun compareClassifierNumberAndBuildNumber() {
        assertTrue(
            KotlinToolingVersion("1.6.20-M1") < KotlinToolingVersion("1.6.20")
        )

        assertTrue(
            KotlinToolingVersion("1.6.20") > KotlinToolingVersion("1.6.20-1")
        )

        assertTrue(
            KotlinToolingVersion("1.6.20-1") < KotlinToolingVersion("1.6.20-2")
        )

        assertTrue(
            KotlinToolingVersion("1.6.20-M1") < KotlinToolingVersion("1.6.20-M2")
        )

        assertTrue(
            KotlinToolingVersion("1.6.20-M1-2") > KotlinToolingVersion("1.6.20-M1-1")
        )

        assertTrue(
            KotlinToolingVersion("1.6.20-M1-2") < KotlinToolingVersion("1.6.20-M2-1")
        )

        assertTrue(
            KotlinToolingVersion("1.6.20-M1-2") < KotlinToolingVersion("1.6.20-M2")
        )

        assertTrue(
            KotlinToolingVersion("1.6.20-beta1") > KotlinToolingVersion("1.6.20-beta")
        )

        assertTrue(
            KotlinToolingVersion("1.6.20-M1") > KotlinToolingVersion("1.6.20-M1-1")
        )
    }

    @Test
    fun devBuildsWithCustomWildcardsDoNotInfluenceCompareTo() {
        assertEquals(
            0, KotlinToolingVersion("1.6.20-dev-myWildcard-510").compareTo(KotlinToolingVersion("1.6.20-dev-myOtherWildcard-510"))
        )

        assertEquals(
            0, KotlinToolingVersion("1.6.20-dev-myWildcard-510").compareTo(KotlinToolingVersion("1.6.20-dev-myWildcard2-510"))
        )

        assertEquals(
            0, KotlinToolingVersion("1.6.20-dev-myWildcard1-510").compareTo(KotlinToolingVersion("1.6.20-dev-myWildcard2-510"))
        )
    }

    @Test
    fun maturityWithClassifierNumberAndBuildNumber() {
        assertMaturity(STABLE, "1.6.20")
        assertMaturity(STABLE, "1.6.20-999")
        assertMaturity(STABLE, "1.6.20-release-999")
        assertMaturity(STABLE, "1.6.20-rElEaSe-999")
        assertMaturity(RC, "1.6.20-rc2411-1901")
        assertMaturity(RC, "1.6.20-RC2411-1901")
        assertMaturity(BETA, "1.6.20-beta2411-1901")
        assertMaturity(BETA, "1.6.20-bEtA2411-1901")
        assertMaturity(ALPHA, "1.6.20-alpha2411-1901")
        assertMaturity(ALPHA, "1.6.20-aLpHa2411-1901")
        assertMaturity(MILESTONE, "1.6.20-m2411-1901")
        assertMaturity(MILESTONE, "1.6.20-M2411-1901")
        assertMaturity(DEV, "1.6.20-dev-2411")
        assertMaturity(DEV, "1.6.20-DeV")
        assertMaturity(DEV, "1.6.20-pub-2411")
        assertMaturity(DEV, "1.6.20-pUb")
        assertMaturity(DEV, "1.6.20-dev-google-pr")
        assertMaturity(DEV, "1.6.20-dev-google-pr-510")
        assertMaturity(DEV, "1.8.0-one-two-three-2022")
        assertMaturity(DEV, "1.8.0-one-a1-b2-2022")
        assertMaturity(DEV, "1.8.0-temporary-999")
        assertMaturity(DEV, "1.8.0-snapshot-42", "Looks strange but snapshot is rather special to allow more usages")
        assertMaturity(null, "1.8.0-dev-", "Forbid dash at the end")
        assertMaturity(null, "1.8.0-t-1000", "Forbid too short first classifier")
        assertMaturity(null, "1.8.0-no-1", "First classifier should be strictly longer than 2 letters")
        assertMaturity(null, "1.8.0-dev----999", "Forbid many dashes in a row")
        assertMaturity(null, "1.8.0-999-0", "Forbid fully digit classifier")
        assertMaturity(null, "1.8.0-some-5-0", "Forbid fully digit second classifier")
    }

    @Test
    fun maturityWithAdditionalReleaseSuffix() {
        assertMaturity(MILESTONE, "1.6.20-M1-release")
        assertMaturity(null, "1.6.20-M1-release1")
        assertMaturity(MILESTONE, "1.6.20-M1-release-22")
        assertMaturity(null, "1.6.20-M1-release-", "Forbid handling dash")
        assertMaturity(ALPHA, "1.6.20-alpha-release")
        assertMaturity(ALPHA, "1.6.20-alpha-release1")
        assertMaturity(ALPHA, "1.6.20-alpha-release39")
        assertMaturity(null, "1.6.20-alpha-release-", "Forbid handling dash")
        assertMaturity(BETA, "1.6.20-beta2-release")
        assertMaturity(BETA, "1.6.20-beta2-release1")
        assertMaturity(BETA, "1.6.20-beta2-release-1")
        assertMaturity(null, "1.6.20-beta2-release-", "Forbid handling dash")
        assertMaturity(RC, "1.6.20-rc1-release")
        assertMaturity(RC, "1.6.20-rc1-release1")
        assertMaturity(RC, "1.6.20-rc1-release-1")
        assertMaturity(null, "1.6.20-rc1-release-", "Forbid handling dash")
    }

    @Test
    fun invalidMilestoneVersion() {
        assertMaturity(null, "1.6.20-M")
    }

    @Test
    fun buildNumber() {
        assertBuildNumber(510, "1.6.20-510")
        assertBuildNumber(510, "1.6.20-release-510")
        assertBuildNumber(510, "1.6.20-rc1-510")
        assertBuildNumber(510, "1.6.20-beta1-510")
        assertBuildNumber(510, "1.6.20-alpha1-510")
        assertBuildNumber(510, "1.6.20-m1-510")
        assertBuildNumber(510, "1.6.20-m1-release-510")
        assertBuildNumber(510, "1.6.20-rc1-release-510")
        assertBuildNumber(510, "1.6.20-beta1-release-510")
        assertBuildNumber(510, "1.6.20-alpha1-release-510")

        /* dev */
        assertBuildNumber(510, "1.6.20-dev-510")
        assertBuildNumber(510, "1.6.20-pub-510")
        assertBuildNumber(510, "1.6.20-dev-myWildcard-510")
        assertBuildNumber(510, "1.6.20-pub-myWildcard-510")
        assertBuildNumber(510, "1.6.20-dev-myWildcard1-510")
        assertBuildNumber(510, "1.6.20-pub-myWildcard1-510")
        assertBuildNumber(510, "1.6.20-some-510")
        assertBuildNumber(510, "1.6.20-aaa-a2-a3-510")
        assertBuildNumber(null,"1.6.20-dev-myWildcard510")

        /* dev with - in wildcards */
        assertBuildNumber(510, "1.6.20-dev-google-pr-510")
        assertBuildNumber(510, "1.6.20-dev-google-pr-510")
        assertBuildNumber(510, "1.6.20-dev-google-pr210-510")
        assertBuildNumber(null, "1.6.20-dev-google-pr")
        assertBuildNumber(null, "1.6.20-dev-google-pr510")
    }

    @Test
    fun classifierNumber() {
        assertClassifierNumber(2, "1.6.20-rc2-510")
        assertClassifierNumber(2, "1.6.20-beta2-510")
        assertClassifierNumber(2, "1.6.20-alpha2-510")
        assertClassifierNumber(2, "1.6.20-m2-510")

        assertClassifierNumber(2, "1.6.20-rc2")
        assertClassifierNumber(2, "1.6.20-beta2")
        assertClassifierNumber(2, "1.6.20-alpha2")
        assertClassifierNumber(2, "1.6.20-m2")

        assertClassifierNumber(2, "1.6.20-rc2-release")
        assertClassifierNumber(2, "1.6.20-rc2-release-510")
        assertClassifierNumber(2, "1.6.20-beta2-release")
        assertClassifierNumber(2, "1.6.20-beta2-release-510")
        assertClassifierNumber(2, "1.6.20-alpha2")
        assertClassifierNumber(2, "1.6.20-alpha2-release")
        assertClassifierNumber(2, "1.6.20-alpha2-release-510")
        assertClassifierNumber(2, "1.6.20-m2")
        assertClassifierNumber(2, "1.6.20-m2-release")
        assertClassifierNumber(2, "1.6.20-m2-release-510")

        assertClassifierNumber(null, "1.6.20-dev-510")
        assertClassifierNumber(null, "1.6.20-pub-510")
        assertClassifierNumber(null, "1.6.20-dev-myWildcard-510")
        assertClassifierNumber(null, "1.6.20-pub-myWildcard-510")
        assertClassifierNumber(null, "1.6.20-dev-myWildcard1-510")
        assertClassifierNumber(null, "1.6.20-pub-myWildcard1-510")
        assertClassifierNumber(null, "1.6.20-dev-myWildcard510")
        assertClassifierNumber(null, "1.6.20-dev-google-pr510")
    }

    @Test
    fun toKotlinVersion() {
        assertEquals(KotlinVersion(1, 7, 20), KotlinToolingVersion("1.7.20-rc2-202").toKotlinVersion())
    }

    @Test
    fun toKotlinToolingVersion() {
        assertEquals(KotlinToolingVersion("1.7"), KotlinVersion(1, 7).toKotlinToolingVersion())
    }

    @Test
    fun isMaturity() {
        assertTrue(
            KotlinToolingVersion("1.7").isStable
        )

        assertFalse(
            KotlinToolingVersion("1.7").isPreRelease
        )

        assertTrue(
            KotlinToolingVersion("1.7.0-rc").isRC
        )

        assertTrue(
            KotlinToolingVersion("1.7.0-beta").isBeta
        )

        assertTrue(
            KotlinToolingVersion("1.7.0-alpha").isAlpha
        )

        assertTrue(
            KotlinToolingVersion("1.7.0-m1").isMilestone
        )

        assertTrue(
            KotlinToolingVersion("1.7.0-dev").isDev
        )

        assertTrue(
            KotlinToolingVersion("1.7.0-snapshot").isSnapshot
        )
    }

    @Test
    fun illegalVersionString() {
        assertFailsWith<IllegalArgumentException> { KotlinToolingVersion("x") }
        assertFailsWith<IllegalArgumentException> { KotlinToolingVersion("1.6.20.1") }
        assertNull(KotlinToolingVersionOrNull(""))
        assertNull(KotlinToolingVersionOrNull("x"))
        assertNull(KotlinToolingVersionOrNull("1.6.20.1"))
        assertNull(KotlinToolingVersionOrNull("1.6.20-x"))
    }

    private fun assertBuildNumber(buildNumber: Int?, version: String, message: String? = null) {
        assertEquals(buildNumber, KotlinToolingVersion(version).buildNumber, message)
    }

    private fun assertClassifierNumber(classifierNumber: Int?, version: String, message: String? = null) {
        assertEquals(classifierNumber, KotlinToolingVersion(version).classifierNumber, message)
    }

    private fun assertMaturity(maturity: KotlinToolingVersion.Maturity?, version: String, message: String? = null) {
        if (maturity == null) {
            val exception = assertFailsWith<IllegalArgumentException>(
                "Parsing maturity is expected to be failed for `$version`${message?.let { ". $it" } ?: ""}") {
                KotlinToolingVersion(version)
            }
            assertTrue("maturity" in exception.message.orEmpty().toLowerCase(), "Expected 'maturity' issue mentioned in error message")
        } else {
            assertEquals(maturity, KotlinToolingVersion(version).maturity, message)
        }
    }
}
