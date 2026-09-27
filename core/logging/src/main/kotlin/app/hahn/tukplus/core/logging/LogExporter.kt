package app.hahn.tukplus.core.logging

import java.io.File
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Makes a zip of log files to share (PLAN.md §9.4). */
class LogExporter(private val store: LogFileStore) {

    /** All files of the given days, plus `README.txt`. Gives the number of log files added. */
    fun exportDays(days: Collection<LocalDate>, target: File, readme: String): Int {
        val files = days.sortedDescending().flatMap { store.filesFor(it) }
        ZipOutputStream(target.outputStream().buffered()).use { zip ->
            zip.putText("README.txt", readme)
            for (file in files) {
                zip.putNextEntry(ZipEntry(file.name))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return files.size
    }

    /**
     * Only the lines of one session, from the given days (a session can go past
     * midnight). Gives the number of lines added.
     */
    fun exportSession(sessionId: String, days: Collection<LocalDate>, target: File, readme: String): Int {
        var count = 0
        ZipOutputStream(target.outputStream().buffered()).use { zip ->
            zip.putText("README.txt", readme)
            zip.putNextEntry(ZipEntry("session-$sessionId.jsonl"))
            val writer = zip.bufferedWriter()
            for (file in days.sorted().flatMap { store.filesFor(it) }) {
                file.useLines { lines ->
                    lines.filter { line -> LogEvents.decode(line)?.let(LogEvents::sessionOf) == sessionId }
                        .forEach { line ->
                            writer.write(line)
                            writer.write("\n")
                            count++
                        }
                }
            }
            writer.flush()
            zip.closeEntry()
        }
        return count
    }

    private fun ZipOutputStream.putText(name: String, text: String) {
        putNextEntry(ZipEntry(name))
        write(text.toByteArray())
        closeEntry()
    }
}
