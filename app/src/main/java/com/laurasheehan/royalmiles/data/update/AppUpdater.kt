package com.laurasheehan.royalmiles.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.laurasheehan.royalmiles.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** A newer build published by CI, as described by update.json on the royal-miles-latest release. */
data class AvailableUpdate(
    val versionCode: Int,
    val sha: String,
    val apkUrl: String,
    /** Checked against the download before anything is handed to Android's installer. */
    val sha256: String,
    val builtAt: String?,
)

sealed interface UpdateState {
    /** Up to date, not checked yet, or the check couldn't reach GitHub: nothing to show. */
    data object None : UpdateState
    /** Only after she asked: shown on the build line, never as a card. */
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data object Unreachable : UpdateState
    data class Available(val update: AvailableUpdate) : UpdateState
    data class Downloading(val update: AvailableUpdate, val progress: Float?) : UpdateState
    /** Android needs her to allow installs from Royal Miles once, in Settings. */
    data class NeedsPermission(val update: AvailableUpdate) : UpdateState
    /** Downloaded and verified; Android's install screen has been opened. */
    data class Installing(val update: AvailableUpdate) : UpdateState
    data class Failed(val update: AvailableUpdate, val message: String) : UpdateState
}

/** The wire format written by .github/workflows/android-build.yml. */
object UpdateProtocol {
    const val RELEASE_BASE = "https://github.com/arualaura5/Claude-Work-Android-App/releases/download/royal-miles-latest/"
    const val MANIFEST_URL = RELEASE_BASE + "update.json"

    private val APK_NAME = Regex("""royal-miles-[0-9a-f]{7,40}\.apk""")
    private val SHA256 = Regex("""[0-9a-f]{64}""")

    /** Null for anything malformed: a bad manifest must never lead to downloading something odd. */
    fun parse(json: String): AvailableUpdate? = runCatching {
        val root = JSONObject(json)
        val apk = root.getString("apk").takeIf { APK_NAME.matches(it) } ?: return null
        val sha256 = root.getString("sha256").lowercase().takeIf { SHA256.matches(it) } ?: return null
        AvailableUpdate(
            versionCode = root.getInt("version_code").takeIf { it > 0 } ?: return null,
            sha = root.optString("sha").take(12),
            apkUrl = RELEASE_BASE + apk,
            sha256 = sha256,
            builtAt = root.optString("built_at", "").takeIf { it.isNotBlank() },
        )
    }.getOrNull()

    fun isNewer(update: AvailableUpdate, installedVersionCode: Int): Boolean = update.versionCode > installedVersionCode

    fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
}

/**
 * Checks GitHub for a newer build and installs it on her tap. Android always shows its own
 * install screen for apps from outside the Play Store; that confirmation is hers to give.
 */
class AppUpdater(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<UpdateState>(UpdateState.None)
    val state: StateFlow<UpdateState> = _state.asStateFlow()
    private var job: Job? = null
    private var dismissed = false

    private val apkFile: File get() = File(appContext.cacheDir, "updates/royal-miles-update.apk")

    /**
     * Quietly does nothing when offline: an automatic check is a convenience, never an error.
     * When she asks (`manual`), it says what it found, and shows an update she'd put off.
     */
    fun check(manual: Boolean = false) {
        if (job?.isActive == true) return
        if (manual) {
            dismissed = false
            _state.value = UpdateState.Checking
        }
        job = scope.launch {
            val update = runCatching { UpdateProtocol.parse(fetch(UpdateProtocol.MANIFEST_URL)) }.getOrNull()
            if (update != null && !dismissed && UpdateProtocol.isNewer(update, BuildConfig.VERSION_CODE)) {
                _state.value = UpdateState.Available(update)
            } else {
                _state.value = when {
                    !manual -> UpdateState.None
                    update == null -> UpdateState.Unreachable
                    else -> UpdateState.UpToDate
                }
                // Once an update is installed, its downloaded copy has no further use.
                if (update != null) apkFile.delete()
            }
        }
    }

    /** Hidden until she checks by hand or the app restarts. */
    fun later() {
        dismissed = true
        _state.value = UpdateState.None
    }

    fun install() {
        val update = when (val current = _state.value) {
            is UpdateState.Available -> current.update
            is UpdateState.NeedsPermission -> current.update
            is UpdateState.Failed -> current.update
            is UpdateState.Installing -> current.update
            else -> return
        }
        if (job?.isActive == true) return
        job = scope.launch {
            try {
                if (!verified(update)) {
                    _state.value = UpdateState.Downloading(update, null)
                    download(update)
                }
                if (!appContext.packageManager.canRequestPackageInstalls()) {
                    _state.value = UpdateState.NeedsPermission(update)
                    return@launch
                }
                openInstaller()
                _state.value = UpdateState.Installing(update)
            } catch (error: Exception) {
                apkFile.delete()
                _state.value = UpdateState.Failed(update, error.message ?: "The download didn't finish.")
            }
        }
    }

    /** The one-off "allow installs from Royal Miles" switch. */
    fun openInstallPermission() {
        appContext.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${appContext.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun fetch(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            useCaches = false
        }
        try {
            if (connection.responseCode != 200) error("HTTP ${connection.responseCode}")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun download(update: AvailableUpdate) {
        apkFile.parentFile?.mkdirs()
        val connection = (URL(update.apkUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
        }
        try {
            if (connection.responseCode != 200) error("The download failed (HTTP ${connection.responseCode}).")
            val total = connection.contentLengthLong.takeIf { it > 0 }
            val digest = MessageDigest.getInstance("SHA-256")
            var read = 0L
            var lastShown = 0f
            connection.inputStream.use { input ->
                apkFile.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        digest.update(buffer, 0, n)
                        read += n
                        val progress = total?.let { read.toFloat() / it }
                        if (progress != null && progress - lastShown >= 0.02f) {
                            lastShown = progress
                            _state.value = UpdateState.Downloading(update, progress)
                        }
                    }
                }
            }
            if (UpdateProtocol.hex(digest.digest()) != update.sha256) {
                apkFile.delete()
                error("The download didn't match the published build, so it wasn't installed. Try again.")
            }
        } finally {
            connection.disconnect()
        }
    }

    /** A previous download of this exact build, e.g. before she allowed installs in Settings. */
    private fun verified(update: AvailableUpdate): Boolean {
        if (!apkFile.exists()) return false
        val digest = MessageDigest.getInstance("SHA-256")
        apkFile.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return UpdateProtocol.hex(digest.digest()) == update.sha256
    }

    private fun openInstaller() {
        val uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.updates", apkFile)
        appContext.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
