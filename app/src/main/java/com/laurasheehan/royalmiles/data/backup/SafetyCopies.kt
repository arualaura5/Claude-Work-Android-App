package com.laurasheehan.royalmiles.data.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile

/** A copy of the log the app kept by itself, before an upgrade or a restore. */
data class SafetyCopy(val file: File, val kind: String, val createdAtMillis: Long, val label: String)

/**
 * Copies of the training log the app keeps on the phone without being asked: one before any
 * database upgrade runs, and one before any restore replaces the log. The newest few of each are
 * kept in the app's private storage (filesDir/backups), and each can be restored like a backup.
 */
class SafetyCopies(context: Context) {
    private val dir = File(context.applicationContext.filesDir, "backups")
    private val scratch = File(context.applicationContext.cacheDir, "safety-copy")

    /**
     * Called before Room opens the log. If the file on disk is an older version than this build,
     * Room is about to run upgrade steps on it, so it is copied as it stands first. Never stops
     * the app opening: a copy that can't be made is skipped.
     */
    fun beforeUpgrade(database: File, currentVersion: Int) {
        if (!database.exists()) return
        val version = SqliteHeader.userVersion(database) ?: return
        if (version < 1 || version >= currentVersion) return
        runCatching {
            keep("$BEFORE_UPGRADE-v$version-to-v$currentVersion") { target -> consolidate(database, target) }
        }
    }

    /** Writes a copy with [write], then files it under [kind] and prunes older ones. */
    fun keep(kind: String, write: (File) -> Unit): File {
        dir.mkdirs()
        val target = File(dir, "${System.currentTimeMillis()}-$kind.db")
        try {
            write(target)
        } catch (error: Exception) {
            BackupFiles.delete(target)
            throw IllegalStateException("Couldn't keep a copy of your current training log, so nothing was changed.", error)
        }
        prune()
        return target
    }

    fun list(): List<SafetyCopy> = dir.listFiles { file -> file.name.endsWith(".db") }.orEmpty()
        .mapNotNull(::describe)
        .sortedByDescending { it.createdAtMillis }

    /**
     * A closed database may still have recent changes in its -wal file. Copies all three files
     * side by side, lets SQLite fold the log into the main file, and stamps the manifest, so the
     * copy is one self-contained file.
     */
    private fun consolidate(database: File, target: File) {
        BackupFiles.delete(scratch)
        scratch.mkdirs()
        val working = File(scratch, database.name)
        for (suffix in listOf("", "-wal", "-shm")) {
            val source = File(database.path + suffix)
            if (source.exists()) source.copyTo(File(working.path + suffix), overwrite = true)
        }
        SQLiteDatabase.openDatabase(working.path, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.NO_LOCALIZED_COLLATORS).use { db ->
            db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
            db.rawQuery("PRAGMA journal_mode = DELETE", null).use { it.moveToFirst() }
            db.beginTransaction()
            try {
                Manifest.stamp(db, BackupFiles.KIND_SAFETY)
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
        working.copyTo(target, overwrite = true)
        BackupFiles.delete(scratch)
    }

    private fun prune() {
        list().groupBy { it.kind.substringBefore("-v") }.values.forEach { copies ->
            copies.drop(KEEP_EACH).forEach { BackupFiles.delete(it.file) }
        }
    }

    private fun describe(file: File): SafetyCopy? {
        val millis = file.name.substringBefore('-').toLongOrNull() ?: return null
        val kind = file.name.substringAfter('-').removeSuffix(".db")
        val label = when {
            kind.startsWith(BEFORE_UPGRADE) -> {
                val (from, to) = kind.substringAfter("$BEFORE_UPGRADE-v").split("-to-v").let { it.getOrNull(0) to it.getOrNull(1) }
                "Before the app upgraded its database (version $from to $to)"
            }
            kind == BEFORE_RESTORE -> "Before a restore replaced your log"
            kind == BEFORE_PLAN_CHANGE -> "Before your plan moved to Richmond"
            else -> return null
        }
        return SafetyCopy(file, kind, millis, label)
    }

    companion object {
        const val BEFORE_UPGRADE = "before-upgrade"
        const val BEFORE_RESTORE = "before-restore"
        const val BEFORE_PLAN_CHANGE = "before-plan-change"
        private const val KEEP_EACH = 3
    }
}

/** Reads SQLite's own file header, without opening the database. */
internal object SqliteHeader {
    private val MAGIC = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

    /** The database's schema version (PRAGMA user_version), or null if this isn't SQLite at all. */
    fun userVersion(file: File): Int? {
        if (!file.isFile || file.length() < 100) return null
        return RandomAccessFile(file, "r").use { raf ->
            val header = ByteArray(100)
            raf.readFully(header)
            if (!header.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) return null
            // Bytes 60-63: user_version, big-endian.
            ((header[60].toInt() and 0xff) shl 24) or ((header[61].toInt() and 0xff) shl 16) or
                ((header[62].toInt() and 0xff) shl 8) or (header[63].toInt() and 0xff)
        }
    }
}

internal object BackupFiles {
    const val KIND_BACKUP = "backup"
    const val KIND_SAFETY = "safety-copy"

    /** A database file and the journal files SQLite may have left beside it. */
    fun delete(file: File) {
        if (file.isDirectory) {
            file.deleteRecursively()
            return
        }
        for (suffix in listOf("", "-wal", "-shm", "-journal")) File(file.path + suffix).delete()
    }

    /** False if the input is longer than [limit]; a backup is a few megabytes at most. */
    fun copyLimited(input: InputStream, target: File, limit: Long): Boolean {
        target.parentFile?.mkdirs()
        var total = 0L
        target.outputStream().use { out ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                total += n
                if (total > limit) return false
                out.write(buffer, 0, n)
            }
        }
        return true
    }
}
