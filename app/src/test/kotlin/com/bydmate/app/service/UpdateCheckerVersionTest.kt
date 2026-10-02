package com.bydmate.app.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerVersionTest {
    @Test fun higherKomBuildIsNewer() {
        assertTrue(UpdateChecker.isNewerVersion("3.19.3-kom.17", "3.19.3-kom.16"))
        assertTrue(UpdateChecker.isNewerVersion("3.19.3-kom.10", "3.19.3-kom.9"))
    }

    @Test fun newerUpstreamBaseWinsOverKomBuild() {
        assertTrue(UpdateChecker.isNewerVersion("3.19.6-kom.17", "3.19.3-kom.20"))
        assertFalse(UpdateChecker.isNewerVersion("3.19.3-kom.20", "3.19.6-kom.17"))
    }

    @Test fun sameVersionIsNotNewer() {
        assertFalse(UpdateChecker.isNewerVersion("v3.19.3-kom.16", "3.19.3-kom.16"))
    }

    @Test fun versionKeyParsesBaseAndKomBuild() {
        assertEquals(listOf(3, 19, 3, 16), UpdateChecker.versionKey("v3.19.3-kom.16"))
        assertEquals(listOf(3, 19, 0, 0), UpdateChecker.versionKey("3.19"))
    }
}
