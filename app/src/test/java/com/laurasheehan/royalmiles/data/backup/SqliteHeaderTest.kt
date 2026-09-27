package com.laurasheehan.royalmiles.data.backup

import com.laurasheehan.royalmiles.ui.backup.BackupText
import com.laurasheehan.royalmiles.ui.backup.BackupViewModel
import java.io.File
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SqliteHeaderTest {

    private fun file(bytes: ByteArray) = File.createTempFile("header", ".db").apply {
        writeBytes(bytes)
        deleteOnExit()
    }

    private fun header(userVersion: Int) = ByteArray(4096).also { bytes ->
        "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII).copyInto(bytes)
        bytes[60] = (userVersion ushr 24).toByte()
        bytes[61] = (userVersion ushr 16).toByte()
        bytes[62] = (userVersion ushr 8).toByte()
        bytes[63] = userVersion.toByte()
    }

    @Test
    fun `reads the database version without opening the database`() {
        assertEquals(10, SqliteHeader.userVersion(file(header(10))))
        assertEquals(300, SqliteHeader.userVersion(file(header(300))))
    }

    @Test
    fun `anything that isn't SQLite has no version`() {
        assertNull(SqliteHeader.userVersion(file("not a backup".toByteArray())))
        assertNull(SqliteHeader.userVersion(file(ByteArray(4096))))
        assertNull(SqliteHeader.userVersion(File("does-not-exist.db")))
    }

    @Test
    fun `an oversized file is refused while copying`() {
        val target = File.createTempFile("copy", ".db").apply { deleteOnExit() }
        assertFalse(BackupFiles.copyLimited(ByteArray(2048).inputStream(), target, limit = 1024))
        assertTrue(BackupFiles.copyLimited(ByteArray(512).inputStream(), target, limit = 1024))
        assertEquals(512, target.length())
    }

    @Test
    fun `a backup is described by what it holds`() {
        val preview = BackupPreview(
            createdAt = "2026-09-27T14:00:00Z",
            appBuild = "abc1234",
            databaseVersion = 10,
            upgradedFrom = null,
            sessions = 312,
            completed = 98,
            firstDate = LocalDate.of(2026, 2, 3),
            lastDate = LocalDate.of(2026, 11, 1),
        )
        assertEquals("312 sessions, 98 done, 3 Feb 2026 to 1 Nov 2026", BackupViewModel.describe(preview))
    }

    @Test
    fun `an old backup is flagged as worth replacing`() {
        val day = 86_400_000L
        assertEquals("today", BackupText.ago(0, 1000))
        assertEquals("yesterday", BackupText.ago(0, day + 1))
        assertEquals("45 days ago, worth saving a new one", BackupText.ago(0, 45 * day + 1))
    }
}
