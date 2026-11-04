package com.laurasheehan.nextread.data

import android.content.Context
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Her notebook, in one file in the app's private storage. Android keeps it across app updates
 * (same applicationId, same signing key) and includes it in the phone's own backup.
 *
 * Writes go to a temporary file first and are then renamed over the old one, so a crash
 * mid-write leaves the previous notebook intact rather than a half-written one.
 */
class NotebookStore(context: Context) {

    private val file = File(context.filesDir, "notebook.json")
    private val temp = File(context.filesDir, "notebook.json.tmp")
    private val _notebook = MutableStateFlow(load())
    val notebook: StateFlow<Notebook> = _notebook.asStateFlow()

    @Synchronized
    fun update(change: (Notebook) -> Notebook) {
        val next = change(_notebook.value)
        temp.writeText(NotebookCodec.encode(next))
        if (!temp.renameTo(file)) {
            file.writeText(temp.readText())
            temp.delete()
        }
        _notebook.value = next
    }

    private fun load(): Notebook {
        if (!file.exists()) return Notebook()
        return runCatching { NotebookCodec.decode(file.readText()) }.getOrElse {
            // Never overwrite a notebook that failed to read: keep a copy to recover from.
            file.copyTo(File(file.parentFile, "notebook-unreadable-${System.currentTimeMillis()}.json"), overwrite = true)
            Notebook()
        }
    }
}
