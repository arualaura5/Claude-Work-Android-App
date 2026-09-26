package com.laurasheehan.royalmiles.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateProtocolTest {

    private val sum = "a".repeat(64)

    private fun manifest(
        versionCode: String = "57",
        apk: String = "royal-miles-e1cb16a.apk",
        sha256: String = sum,
    ) = """{"version_code": $versionCode, "sha": "e1cb16a", "apk": "$apk", "sha256": "$sha256", "built_at": "2026-09-26T20:04:23Z"}"""

    @Test
    fun `a published build is read with its download address`() {
        val update = UpdateProtocol.parse(manifest())!!
        assertEquals(57, update.versionCode)
        assertEquals("e1cb16a", update.sha)
        assertEquals(
            "https://github.com/arualaura5/Claude-Work-Android-App/releases/download/royal-miles-latest/royal-miles-e1cb16a.apk",
            update.apkUrl,
        )
        assertEquals(sum, update.sha256)
    }

    @Test
    fun `only a Royal Miles APK on our own release can be downloaded`() {
        assertNull(UpdateProtocol.parse(manifest(apk = "../../evil.apk")))
        assertNull(UpdateProtocol.parse(manifest(apk = "https://example.com/royal-miles-e1cb16a.apk")))
        assertNull(UpdateProtocol.parse(manifest(apk = "royal-miles-e1cb16a.zip")))
    }

    @Test
    fun `a manifest without a proper checksum or version is ignored`() {
        assertNull(UpdateProtocol.parse(manifest(sha256 = "abc")))
        assertNull(UpdateProtocol.parse(manifest(versionCode = "0")))
        assertNull(UpdateProtocol.parse("not json"))
        assertNull(UpdateProtocol.parse("{}"))
    }

    @Test
    fun `only a higher build number counts as an update`() {
        val update = UpdateProtocol.parse(manifest(versionCode = "57"))!!
        assertTrue(UpdateProtocol.isNewer(update, installedVersionCode = 1))
        assertFalse(UpdateProtocol.isNewer(update, installedVersionCode = 57))
        assertFalse(UpdateProtocol.isNewer(update, installedVersionCode = 60))
    }

    @Test
    fun `checksums are compared as lowercase hex`() {
        assertEquals("00ff10", UpdateProtocol.hex(byteArrayOf(0, -1, 16)))
    }
}
