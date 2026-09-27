package app.hahn.tukplus.tools.probe

import okhttp3.HttpUrl
import java.io.File
import java.time.Instant

/**
 * Saves response bodies as test fixtures, without personal data ([FixtureScrubber]).
 * File names depend only on the request, so a new recording replaces the old files.
 */
class Recorder(val dir: File) {
    init {
        dir.mkdirs()
    }

    /** Saves successful bodies, and the HTTP 500 answers of `shop_open` (a closed shop). */
    fun save(url: HttpUrl, code: Int, body: String) {
        val name = fileName(url)
        if (code != 200 && !(code == 500 && name.startsWith("shop_open/"))) return
        val file = File(dir, if (code == 200) name else name.replace(".txt", ".$code.txt"))
        file.parentFile.mkdirs()
        file.writeText(FixtureScrubber.scrubBody(body))
    }

    fun read(name: String): String? = File(dir, name).takeIf { it.exists() }?.readText()

    fun writeManifest(backendVersion: String?) {
        File(dir, "manifest.txt").writeText(
            "Recorded by tools/api-probe\nrecorded_at=${Instant.now()}\nbackend_version=${backendVersion.orEmpty()}\n",
        )
    }

    companion object {
        fun fileName(url: HttpUrl): String {
            val path = url.encodedPath.removePrefix("/prod/tuk/")
            fun q(name: String) = url.queryParameter(name).orEmpty()
            fun safe(text: String) = text.replace(Regex("[^A-Za-z0-9@._-]"), "_")
            return when {
                path == "version" -> "version.txt"
                path == "blob/active" -> "pages/${safe(q("page"))}.json"
                path == "businesses" && q("type") == "eatery" -> "eateries.json"
                path == "businesses" && q("type") == "new_shops" -> "new_shops.json"
                path.startsWith("businesses/") -> "business/${safe(path.removePrefix("businesses/"))}.json"
                path == "recommendations/foryou" -> "foryou.json"
                path == "helpers/shop_open" -> "shop_open/${safe(q("id").ifEmpty { q("handle") })}.txt"
                path.startsWith("short_link/") -> "short_link/${safe(path.removePrefix("short_link/"))}.json"
                path == "autocomplete" && url.queryParameter("business") != null -> "autocomplete_business/${safe(q("business"))}.json"
                path == "search/menu_items" -> "menu_items/${safe(q("text"))}.json"
                path == "workflows" && url.queryParameter("commerce_delivery") != null -> "commerce_delivery/${safe(q("commerce_delivery"))}.json"
                path.startsWith("workflows/") -> "workflows/${safe(path.removePrefix("workflows/"))}.json"
                path == "directions" -> "directions.json"
                path == "delivery/price_check" -> "price_check.json"
                else -> "other/${safe(path)}.txt"
            }
        }

        /** Groups a redacted log path into an endpoint name for the timing table. */
        fun endpointKey(path: String): String {
            val p = path.substringBefore('?')
            val query = path.substringAfter('?', "")
            return when {
                p.startsWith("workflows") && query.contains("commerce_delivery") -> "workflows?commerce_delivery"
                p.startsWith("workflows/") -> "workflows/{businessId}"
                p.startsWith("businesses/") -> "businesses/{id}"
                p == "businesses" -> "businesses?type=" + query.substringAfter("type=").substringBefore('&')
                p.startsWith("short_link/") -> "short_link/{handle}"
                p == "blob/active" -> "blob/active (page)"
                else -> p
            }
        }
    }
}
