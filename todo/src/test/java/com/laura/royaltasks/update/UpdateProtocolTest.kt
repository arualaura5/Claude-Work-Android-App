package com.laura.royaltasks.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateProtocolTest {

    private val sum = "a".repeat(64)

    private fun manifest(apk: String = "royal-tasks-431920e.apk", sha256: String = sum, version: Int = 230) =
        """{"version_code":$version,"sha":"431920e","apk":"$apk","sha256":"$sha256","built_at":"2026-10-08T09:01:51Z"}"""

    @Test
    fun `parses the manifest CI writes`() {
        val update = UpdateProtocol.parse(manifest())
        assertNotNull(update)
        assertEquals(230, update!!.versionCode)
        assertEquals(UpdateProtocol.RELEASE_BASE + "royal-tasks-431920e.apk", update.apkUrl)
        assertEquals(sum, update.sha256)
    }

    @Test
    fun `refuses anything that isn't a Royal Tasks build`() {
        assertNull(UpdateProtocol.parse(manifest(apk = "royal-miles-431920e.apk")))
        assertNull(UpdateProtocol.parse(manifest(apk = "../evil.apk")))
        assertNull(UpdateProtocol.parse(manifest(sha256 = "not-a-hash")))
        assertNull(UpdateProtocol.parse(manifest(version = 0)))
        assertNull(UpdateProtocol.parse("not json"))
    }

    @Test
    fun `only a higher version code counts as newer`() {
        val update = UpdateProtocol.parse(manifest(version = 230))!!
        assertTrue(UpdateProtocol.isNewer(update, 1))
        assertFalse(UpdateProtocol.isNewer(update, 230))
        assertFalse(UpdateProtocol.isNewer(update, 231))
    }
}
