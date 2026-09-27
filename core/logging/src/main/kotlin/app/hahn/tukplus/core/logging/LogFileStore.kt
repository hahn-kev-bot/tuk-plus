package app.hahn.tukplus.core.logging

import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * The log folder. One file per day: `2026-09-27.jsonl`. When a file reaches
 * [maxBytesPerFile], writing continues in `2026-09-27.2.jsonl`, then `.3`, and so on.
 * Only the first [maxFilesPerDay] files of a day are written; after that, events of
 * that day are dropped (and one warning is written, see [FileLogSink]).
 */
class LogFileStore(
    val directory: File,
    val maxBytesPerFile: Long = 10L * 1024 * 1024,
    val maxFilesPerDay: Int = 3,
    val retentionDays: Int = 14,
) {
    init {
        directory.mkdirs()
    }

    fun fileFor(day: LocalDate, part: Int): File =
        File(directory, if (part <= 1) "$day.jsonl" else "$day.$part.jsonl")

    /** The file to write to for [day], or null when the day has used all its files. */
    fun writableFile(day: LocalDate): File? {
        for (part in 1..maxFilesPerDay) {
            val file = fileFor(day, part)
            if (!file.exists() || file.length() < maxBytesPerFile) return file
        }
        return null
    }

    /** All log files of [day], in write order. */
    fun filesFor(day: LocalDate): List<File> =
        (1..maxFilesPerDay).map { fileFor(day, it) }.filter { it.exists() }

    /** Days that have log files, newest first. */
    fun days(): List<LocalDate> =
        directory.listFiles().orEmpty().mapNotNull { dayOf(it) }.distinct().sortedDescending()

    /** Deletes files older than [retentionDays] before [today]. Gives the deleted files. */
    fun deleteOld(today: LocalDate): List<File> {
        val oldest = today.minusDays((retentionDays - 1).toLong())
        return directory.listFiles().orEmpty().filter { file ->
            val day = dayOf(file)
            day != null && day.isBefore(oldest) && file.delete()
        }
    }

    private fun dayOf(file: File): LocalDate? {
        if (!file.name.endsWith(".jsonl")) return null
        return try {
            LocalDate.parse(file.name.substringBefore('.'))
        } catch (_: DateTimeParseException) {
            null
        }
    }
}
