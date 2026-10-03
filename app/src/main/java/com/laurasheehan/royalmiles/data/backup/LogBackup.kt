package com.laurasheehan.royalmiles.data.backup

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.sqlite.db.SupportSQLiteDatabase
import com.laurasheehan.royalmiles.BuildConfig
import com.laurasheehan.royalmiles.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.time.LocalDate

/** What a backup holds, shown before anything is restored and after anything is saved. */
data class BackupPreview(
    val createdAt: String?,
    val appBuild: String?,
    val databaseVersion: Int,
    /** Set when the backup came from an older database version and was brought up to date. */
    val upgradedFrom: Int?,
    val sessions: Int,
    val completed: Int,
    val firstDate: LocalDate?,
    val lastDate: LocalDate?,
)

sealed interface Inspection {
    /** Checked and ready: [file] is a private, up-to-date copy that restoring will read from. */
    data class Ready(val file: File, val preview: BackupPreview) : Inspection

    /** Not restorable. Nothing was changed. */
    data class Problem(val message: String) : Inspection
}

/**
 * Backs up and restores Laura's training log: sessions, completions, figures, ratings, events,
 * plan settings and her profile. Coach, chat and keys are not part of it.
 *
 * A backup is a standalone SQLite copy of the log with a `backup_manifest` table inside (format,
 * database version, row counts, when and which build made it), so it can be checked, and brought
 * up to date by the app's own upgrade steps if it is from an older version.
 *
 * Restoring never merges: after every check has passed it replaces the whole log in one
 * transaction, so a failure part-way leaves the current log exactly as it was. A copy of the
 * current log is kept first, so a restore can itself be undone.
 */
class LogBackup(
    private val context: Context,
    private val database: AppDatabase = AppDatabase.getInstance(context),
    private val safetyCopies: SafetyCopies = SafetyCopies(context),
) {
    /** Saves a backup to a file she chose. */
    suspend fun exportTo(uri: Uri): BackupPreview = withContext(Dispatchers.IO) {
        val out = context.contentResolver.openOutputStream(uri, "wt") ?: error("Couldn't open that file for writing.")
        out.use { export(it) }
    }

    suspend fun export(out: OutputStream): BackupPreview = withContext(Dispatchers.IO) {
        val snapshot = File(context.cacheDir, "backup-out.db")
        try {
            writeSnapshot(snapshot)
            snapshot.inputStream().use { it.copyTo(out) }
            out.flush()
            preview(snapshot, upgradedFrom = null)
        } finally {
            BackupFiles.delete(snapshot)
        }
    }

    suspend fun inspect(uri: Uri): Inspection = withContext(Dispatchers.IO) {
        val input = runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()
            ?: return@withContext Inspection.Problem("Couldn't open that file.")
        input.use { inspect(it) }
    }

    suspend fun inspect(input: InputStream): Inspection = withContext(Dispatchers.IO) {
        val candidate = File(context.cacheDir, "restore-candidate.db")
        BackupFiles.delete(candidate)
        val copied = BackupFiles.copyLimited(input, candidate, MAX_BYTES)
        if (!copied) {
            BackupFiles.delete(candidate)
            return@withContext Inspection.Problem("That file is far too large to be a Royal Miles backup.")
        }
        inspectFile(candidate)
    }

    /** A safety copy kept on the phone, checked the same way as any other backup. */
    suspend fun inspect(copy: SafetyCopy): Inspection = withContext(Dispatchers.IO) {
        copy.file.inputStream().use { inspect(it) }
    }

    /**
     * Replaces the whole training log with a checked backup. Keeps a copy of the current log
     * first; if that copy can't be made, nothing is replaced.
     */
    suspend fun restore(ready: Inspection.Ready): BackupPreview = withContext(Dispatchers.IO) {
        safetyCopies.keep(SafetyCopies.BEFORE_RESTORE) { target -> writeSnapshot(target) }
        val source = SQLiteDatabase.openDatabase(ready.file.path, null, READ_ONLY)
        try {
            val live = database.openHelper.writableDatabase
            database.runInTransaction(Runnable {
                for (table in logTables(live)) {
                    val columns = columns(live, table)
                    val sourceColumns = columns(source, table)
                    check(sourceColumns == columns) { "The backup's $table table doesn't match this version." }
                    live.execSQL("DELETE FROM `$table`")
                    source.rawQuery("SELECT ${columns.joinToString { "`$it`" }} FROM `$table`", null).use { rows ->
                        while (rows.moveToNext()) live.insert(table, SQLiteDatabase.CONFLICT_ABORT, rows.toValues())
                    }
                }
            })
        } finally {
            source.close()
        }
        BackupFiles.delete(ready.file)
        ready.preview
    }

    /** A copy of the live log kept on the phone, restorable from Back up & restore. */
    suspend fun keepSafetyCopy(kind: String) = withContext(Dispatchers.IO) {
        safetyCopies.keep(kind) { target -> writeSnapshot(target) }
    }

    /** Throws away a checked candidate she decided not to restore. */
    fun discard(ready: Inspection.Ready) = BackupFiles.delete(ready.file)

    /**
     * A consistent copy of the live log, read inside one transaction so nothing can change
     * half-way through, with the manifest stamped in.
     */
    internal fun writeSnapshot(target: File) {
        BackupFiles.delete(target)
        target.parentFile?.mkdirs()
        val copy = SQLiteDatabase.openDatabase(target.path, null, SQLiteDatabase.CREATE_IF_NECESSARY or SQLiteDatabase.NO_LOCALIZED_COLLATORS)
        try {
            copy.beginTransaction()
            try {
                val live = database.openHelper.writableDatabase
                database.runInTransaction(Runnable {
                    live.query(
                        "SELECT type, sql FROM sqlite_master WHERE sql IS NOT NULL AND name NOT LIKE 'sqlite_%' " +
                            "AND name <> 'android_metadata' ORDER BY CASE type WHEN 'table' THEN 0 ELSE 1 END",
                    ).use { schema -> while (schema.moveToNext()) copy.execSQL(schema.getString(1)) }
                    for (table in allTables(live)) {
                        live.query("SELECT * FROM `$table`").use { rows ->
                            while (rows.moveToNext()) copy.insertOrThrow(table, null, rows.toValues())
                        }
                    }
                    copy.version = live.version
                })
                Manifest.stamp(copy, BackupFiles.KIND_BACKUP)
                copy.setTransactionSuccessful()
            } finally {
                copy.endTransaction()
            }
        } finally {
            copy.close()
        }
    }

    internal suspend fun inspectFile(candidate: File): Inspection {
        val version = SqliteHeader.userVersion(candidate)
            ?: return problem(candidate, "That file isn't a Royal Miles backup.")
        if (version > AppDatabase.VERSION) {
            return problem(
                candidate,
                "This backup was made by a newer version of Royal Miles (database v$version). Update the app, then restore it.",
            )
        }
        if (version < 1) return problem(candidate, "That file isn't a Royal Miles backup.")

        val manifest = try {
            SQLiteDatabase.openDatabase(candidate.path, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.NO_LOCALIZED_COLLATORS).use { db ->
                val integrity = db.rawQuery("PRAGMA integrity_check", null).use { if (it.moveToFirst()) it.getString(0) else null }
                if (integrity != "ok") return problem(candidate, "This backup is damaged, so it wasn't restored.")
                Manifest.read(db) ?: return problem(candidate, "That file isn't a Royal Miles backup (it has no backup record).")
            }
        } catch (error: Exception) {
            return problem(candidate, "That file isn't a Royal Miles backup.")
        }
        if (manifest[Manifest.FORMAT] != Manifest.FORMAT_NAME) return problem(candidate, "That file isn't a Royal Miles backup.")

        // Opening it through Room runs the same upgrade steps the app runs on the phone, then
        // checks every table has exactly the shape this version expects.
        val room = AppDatabase.open(context, candidate.absolutePath)
        return try {
            room.openHelper.writableDatabase
            val sessions = room.openHelper.readableDatabase.query("SELECT COUNT(*) FROM sessions").use { it.moveToFirst(); it.getInt(0) }
            val expected = manifest[Manifest.countKey("sessions")]?.toIntOrNull()
            if (expected != sessions) {
                return problem(candidate, "This backup is incomplete (it should hold $expected sessions but has $sessions), so it wasn't restored.")
            }
            val orphans = room.openHelper.readableDatabase.query(
                "SELECT COUNT(*) FROM sessions WHERE eventId NOT IN (SELECT id FROM events)",
            ).use { it.moveToFirst(); it.getInt(0) }
            if (orphans > 0) return problem(candidate, "This backup has sessions that belong to no event, so it wasn't restored.")
            // Every row has to read back through the app's own types (dates, session types).
            room.sessionDao().getAll()
            room.eventDao().getAll()
            room.planMetaDao().get()
            room.athleteProfileDao().observe().first()
            room.close()
            Inspection.Ready(candidate, preview(candidate, upgradedFrom = version.takeIf { it < AppDatabase.VERSION }))
        } catch (error: Exception) {
            problem(candidate, "This version of Royal Miles couldn't read that backup (${error.message ?: error.javaClass.simpleName}).")
        } finally {
            if (room.isOpen) room.close()
        }
    }

    private fun problem(candidate: File, message: String): Inspection.Problem {
        BackupFiles.delete(candidate)
        return Inspection.Problem(message)
    }

    private fun preview(file: File, upgradedFrom: Int?): BackupPreview =
        SQLiteDatabase.openDatabase(file.path, null, READ_ONLY).use { db ->
            val manifest = Manifest.read(db).orEmpty()
            db.rawQuery(
                "SELECT COUNT(*), SUM(isCompleted), MIN(date), MAX(date) FROM sessions",
                null,
            ).use { row ->
                row.moveToFirst()
                BackupPreview(
                    createdAt = manifest[Manifest.CREATED_AT],
                    appBuild = manifest[Manifest.APP_BUILD],
                    databaseVersion = db.version,
                    upgradedFrom = upgradedFrom,
                    sessions = row.getInt(0),
                    completed = if (row.isNull(1)) 0 else row.getInt(1),
                    firstDate = row.getString(2)?.let(LocalDate::parse),
                    lastDate = row.getString(3)?.let(LocalDate::parse),
                )
            }
        }

    companion object {
        private const val MAX_BYTES = 64L * 1024 * 1024
        private const val READ_ONLY = SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS

        /** Tables that hold the log, as opposed to Room's own bookkeeping. */
        private val NOT_LOG = setOf("room_master_table", "android_metadata", Manifest.TABLE)

        private fun allTables(db: SupportSQLiteDatabase): List<String> =
            db.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' AND name <> 'android_metadata'")
                .use { rows -> buildList { while (rows.moveToNext()) add(rows.getString(0)) } }

        private fun logTables(db: SupportSQLiteDatabase): List<String> = allTables(db).filterNot { it in NOT_LOG }

        private fun columns(db: SupportSQLiteDatabase, table: String): List<String> =
            db.query("PRAGMA table_info(`$table`)").use { rows -> buildList { while (rows.moveToNext()) add(rows.getString(1)) } }

        private fun columns(db: SQLiteDatabase, table: String): List<String> =
            db.rawQuery("PRAGMA table_info(`$table`)", null).use { rows -> buildList { while (rows.moveToNext()) add(rows.getString(1)) } }
    }
}

/** One row as ContentValues, keeping each value's own SQLite type. */
internal fun Cursor.toValues(): ContentValues = ContentValues().also { values ->
    for (i in 0 until columnCount) {
        val name = getColumnName(i)
        when (getType(i)) {
            Cursor.FIELD_TYPE_NULL -> values.putNull(name)
            Cursor.FIELD_TYPE_INTEGER -> values.put(name, getLong(i))
            Cursor.FIELD_TYPE_FLOAT -> values.put(name, getDouble(i))
            Cursor.FIELD_TYPE_BLOB -> values.put(name, getBlob(i))
            else -> values.put(name, getString(i))
        }
    }
}

/** The record inside every backup and safety copy. */
internal object Manifest {
    const val TABLE = "backup_manifest"
    const val FORMAT = "format"
    const val FORMAT_NAME = "royal-miles-training-log"
    const val FORMAT_VERSION = "format_version"
    const val KIND = "kind"
    const val CREATED_AT = "created_at"
    const val APP_BUILD = "app_build"
    const val DATABASE_VERSION = "database_version"

    fun countKey(table: String) = "count:$table"

    /** Writes the manifest, counting the rows actually in [db] rather than trusting the caller. */
    fun stamp(db: SQLiteDatabase, kind: String) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE")
        db.execSQL("CREATE TABLE $TABLE (key TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL)")
        val entries = mutableMapOf(
            FORMAT to FORMAT_NAME,
            FORMAT_VERSION to "1",
            KIND to kind,
            CREATED_AT to Instant.now().toString(),
            APP_BUILD to BuildConfig.GIT_SHA,
            DATABASE_VERSION to db.version.toString(),
        )
        val tables = db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' AND name NOT IN ('android_metadata', '$TABLE')",
            null,
        ).use { rows -> buildList { while (rows.moveToNext()) add(rows.getString(0)) } }
        for (table in tables) {
            entries[countKey(table)] = db.rawQuery("SELECT COUNT(*) FROM `$table`", null).use { it.moveToFirst(); it.getLong(0).toString() }
        }
        entries.forEach { (key, value) ->
            db.insertOrThrow(TABLE, null, ContentValues().apply { put("key", key); put("value", value) })
        }
    }

    fun read(db: SQLiteDatabase): Map<String, String>? {
        val present = db.rawQuery("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = '$TABLE'", null).use { it.moveToFirst() }
        if (!present) return null
        return db.rawQuery("SELECT key, value FROM $TABLE", null).use { rows ->
            buildMap { while (rows.moveToNext()) put(rows.getString(0), rows.getString(1)) }
        }
    }
}
