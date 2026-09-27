package app.hahn.tukplus.core.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** The shops that the user opened last, newest first. Kept in a small file on the device. */
class RecentShops(private val file: File, private val max: Int = 20) {
    private val _ids = MutableStateFlow(read())
    val ids: StateFlow<List<String>> = _ids.asStateFlow()

    @Synchronized
    fun opened(businessId: String) {
        val next = (listOf(businessId) + _ids.value.filter { it != businessId }).take(max)
        _ids.value = next
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(next.joinToString("\n"))
        }
    }

    private fun read(): List<String> =
        runCatching { file.readLines().map { it.trim() }.filter { it.isNotEmpty() } }.getOrDefault(emptyList())
}
