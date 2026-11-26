package com.tokipin.app.data

import android.content.Context
import androidx.core.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.time.Instant

/**
 * Offline, account-free storage. Records and the draft live in the app's own files directory as
 * JSON, written atomically so an interrupted write never corrupts what was already saved.
 */
class JournalStore(context: Context) {
    private val dir = File(context.filesDir, "tokipin")
    private val journalFile = AtomicFile(File(dir, "records.json"))
    private val draftFile = AtomicFile(File(dir, "draft.json"))
    private val prefs = context.getSharedPreferences("tokipin.settings", Context.MODE_PRIVATE)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** Throws on a read or parse failure so the app can show the recovery screen instead of an empty home. */
    suspend fun loadJournal(): Journal = withContext(Dispatchers.IO) {
        if (!journalFile.baseFile.exists()) return@withContext Journal()
        val text = journalFile.readFully().decodeToString()
        decodeJournal(text)
    }

    internal fun decodeJournal(text: String): Journal =
        json.decodeFromString(RecordsFile.serializer(), text).toJournal()

    suspend fun saveJournal(journal: Journal) = withContext(Dispatchers.IO) {
        write(journalFile, json.encodeToString(RecordsFile.serializer(), RecordsFile.from(journal)))
    }

    /**
     * Drafts are kept per date, so a draft left on one day never opens on another or gets lost when
     * a different day is edited. [DraftsFile.active] is the day whose editor was open, reopened
     * after the process is killed. A broken file is dropped; saved records are untouched. Reads the
     * single-draft file of earlier builds too.
     */
    suspend fun loadDrafts(): DraftsFile = withContext(Dispatchers.IO) {
        if (!draftFile.baseFile.exists()) return@withContext DraftsFile()
        val text = runCatching { draftFile.readFully().decodeToString() }.getOrNull() ?: return@withContext DraftsFile()
        runCatching { json.decodeFromString(DraftsFile.serializer(), text) }.getOrNull()
            ?: runCatching { json.decodeFromString(Draft.serializer(), text) }.getOrNull()?.let { DraftsFile(it.date, listOf(it)) }
            ?: DraftsFile()
    }

    /** Blocking write; call from a single background writer so states land in order. */
    fun writeDrafts(file: DraftsFile) {
        runCatching {
            synchronized(draftFile) {
                if (file.drafts.isEmpty()) draftFile.delete()
                else write(draftFile, json.encodeToString(DraftsFile.serializer(), file))
            }
        }
    }

    private fun write(file: AtomicFile, text: String) {
        dir.mkdirs()
        val stream = file.startWrite()
        try {
            stream.write(text.encodeToByteArray())
            file.finishWrite(stream)
        } catch (e: IOException) {
            file.failWrite(stream)
            throw e
        }
    }

    var hapticsEnabled: Boolean
        get() = prefs.getBoolean(KEY_HAPTICS, true)
        set(value) = prefs.edit().putBoolean(KEY_HAPTICS, value).apply()

    /** The first launch opens straight into a word; every later launch opens the home. */
    var hasLaunchedBefore: Boolean
        get() = prefs.getBoolean(KEY_LAUNCHED, false)
        set(value) = prefs.edit().putBoolean(KEY_LAUNCHED, value).apply()

    suspend fun export(journal: Journal, out: OutputStream) = withContext(Dispatchers.IO) {
        out.use { it.write(exportJson(journal).encodeToByteArray()) }
    }

    companion object {
        private const val KEY_HAPTICS = "haptics"
        private const val KEY_LAUNCHED = "launched"

        private val exportFormat = Json { prettyPrint = true; encodeDefaults = true }

        /**
         * The stored file and the export share one schema with the iOS app (see README "Data format"):
         * { version: 1, days: [{ date: "yyyy-MM-dd", updatedAt, items: [{ id, kind, value }] }] }.
         * Days are oldest first and items keep their entered order.
         */
        fun exportJson(journal: Journal): String =
            exportFormat.encodeToString(RecordsFile.serializer(), RecordsFile.from(journal))
    }
}

@Serializable
data class DraftsFile(val active: String? = null, val drafts: List<Draft> = emptyList())

@Serializable
private data class RecordsFile(val version: Int = 1, val days: List<DayRecord> = emptyList()) {
    fun toJournal(): Journal = Journal(
        days = days.filter { it.items.isNotEmpty() }.associate { it.date to it.items },
        updatedAt = days.mapNotNull { d -> d.updatedAt?.let { d.date to it } }.toMap(),
    )

    companion object {
        fun from(journal: Journal) = RecordsFile(
            days = journal.days.entries.sortedBy { it.key }.map { (date, items) ->
                DayRecord(date, journal.updatedAt[date] ?: Instant.EPOCH.toString(), items)
            },
        )
    }
}

@Serializable
private data class DayRecord(val date: String, val updatedAt: String? = null, val items: List<Entry>)
