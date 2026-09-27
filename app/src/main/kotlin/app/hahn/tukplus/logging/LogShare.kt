package app.hahn.tukplus.logging

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import app.hahn.tukplus.BuildConfig
import app.hahn.tukplus.R
import app.hahn.tukplus.core.logging.LogExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Makes a zip of logs and opens the Android share sheet (PLAN.md §9.4). */
class LogShare(private val context: Context, private val logging: AppLogging) {
    private val exporter = LogExporter(logging.store)

    sealed interface What {
        data class Days(val days: List<LocalDate>) : What
        data object ThisSession : What
    }

    /** Makes the zip on a background thread. Gives the share intent. */
    suspend fun prepare(what: What): Intent = withContext(Dispatchers.IO) {
        logging.log.i("debug", "logs_export", "what" to what.toString())
        logging.flushBlocking()
        val dir = File(context.cacheDir, "log-exports").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // Keep only the newest export.
        val stamp = LocalDateTime.now(logging.clock).format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))
        val file = File(dir, "tukplus-logs-$stamp.zip")
        val today = LocalDate.now(logging.clock)
        when (what) {
            is What.Days -> exporter.exportDays(what.days, file, readme(what.toString()))
            What.ThisSession -> exporter.exportSession(
                logging.log.sessionId, listOf(today.minusDays(1), today), file, readme("session ${logging.log.sessionId}"),
            )
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.logs", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Tuk plus logs $stamp")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        Intent.createChooser(send, context.getString(R.string.debug_share_chooser))
    }

    private fun readme(contents: String): String = """
        Tuk plus logs
        Contents: $contents
        Exported: ${LocalDateTime.now(logging.clock)}
        Current session: ${logging.log.sessionId}
        App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) ${BuildConfig.BUILD_TYPE}, commit ${BuildConfig.GIT_COMMIT}
        Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})

        Each .jsonl file has one JSON event per line. The "s" field is the session id.
        Use tools/logview/logview.py in the repository to read the files.
    """.trimIndent() + "\n"
}
