package app.hahn.tukplus.tools.probe

import app.hahn.tukplus.core.logging.LogLevel
import app.hahn.tukplus.core.logging.Redactor
import app.hahn.tukplus.core.logging.TukLog
import app.hahn.tukplus.core.model.Business
import app.hahn.tukplus.core.model.MenuParser
import app.hahn.tukplus.core.model.OptionGroup
import app.hahn.tukplus.core.model.PageBlobs
import app.hahn.tukplus.core.model.MenuItem
import app.hahn.tukplus.core.model.TukJson
import app.hahn.tukplus.core.model.Workflow
import app.hahn.tukplus.core.model.WorkflowData
import app.hahn.tukplus.core.model.BusinessData
import app.hahn.tukplus.core.network.ApiResult
import app.hahn.tukplus.core.network.DeviceUuid
import app.hahn.tukplus.core.model.LatLon
import app.hahn.tukplus.core.network.TukApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.system.exitProcess

/**
 * Manual check of the live Tuk API (PLAN.md §10). It calls only read-only GET endpoints.
 *
 * Usage:
 *   ./gradlew :tools:api-probe:run
 *   ./gradlew :tools:api-probe:run --args="--menus 40 --record core/model/src/test/resources/fixtures/live"
 *
 * It checks that our models parse every response, lists fields that our models do not
 * know, and prints facts that the pricing and hours code needs (hours formats, package
 * codes, payment options). The exit code is 1 when a check fails.
 */
fun main(args: Array<String>) {
    val options = Options.parse(args)
    val exitCode = runBlocking { Probe(options).run() }
    exitProcess(exitCode)
}

data class Options(val recordDir: File?, val menus: Int, val parallel: Int) {
    companion object {
        fun parse(args: Array<String>): Options {
            var record: File? = null
            var menus = 30
            var parallel = 4
            var i = 0
            while (i < args.size) {
                when (args[i]) {
                    "--record" -> record = File(args[++i])
                    "--menus" -> menus = args[++i].toInt()
                    "--parallel" -> parallel = args[++i].toInt()
                    "--help", "-h" -> {
                        println("Options: --record <dir>  --menus <n> (default 30)  --parallel <n> (default 4)")
                        exitProcess(0)
                    }
                    else -> error("Unknown option ${args[i]}")
                }
                i++
            }
            return Options(record, menus, parallel)
        }
    }
}

private class Probe(private val options: Options) {
    private val checks = ConcurrentLinkedQueue<Check>()
    private val timings = ConcurrentHashMap<String, MutableList<Long>>()
    private val facts = ConcurrentHashMap<String, MutableMap<String, Int>>()
    private val unknownKeys = ConcurrentHashMap<String, MutableMap<String, Int>>()
    private val recorder = options.recordDir?.let { Recorder(it) }
    private val gate = Semaphore(options.parallel)

    private val netLog = object : TukLog {
        override fun log(level: LogLevel, tag: String, event: String, fields: Map<String, Any?>) {
            val path = fields["path"]?.toString() ?: return
            val ms = (fields["ms"] as? Number)?.toLong() ?: return
            timings.getOrPut(Recorder.endpointKey(path)) { mutableListOf() }.let { synchronized(it) { it += ms } }
        }
    }

    private val api: TukApi = run {
        val uuid = DeviceUuid.newV1().toString()
        val client = TukApi.createClient(
            deviceUuid = { uuid },
            userAgent = "TukPlus-ApiProbe/0.1",
            log = netLog,
            redactor = Redactor("probe"),
        )
        TukApi(client, bodyTap = recorder?.let { r -> { url, code, body -> r.save(url, code, body) } })
    }

    suspend fun run(): Int {
        val started = System.currentTimeMillis()
        println("Tuk API probe – read-only calls to ${api.baseUrl}")

        check("version") { api.version() }?.let { println("Backend version: $it"); fact("backend version", it) }

        coroutineScopeAll(
            (TukApi.HOME_PAGES + TukApi.EAT_PAGE).map { page ->
                suspend {
                    check("page $page") { api.pageBlobs(page) }?.let { blobs ->
                        val row = PageBlobs.toRow(page, blobs)
                        blobs.forEach { fact("page blob types", it.blobType.toString()) }
                        row.tiles.forEach { tile -> fact("tile tag kinds", tagKind(tile.tag)) }
                        if (blobs.isNotEmpty() && row.title == null) note("page $page has no list_title")
                    }
                }
            },
        )

        val eateries = check("eateries (Chiang Mai)") { api.eateries(LatLon.CHIANG_MAI) }.orEmpty()
        if (eateries.size < 50) fail("eateries (Chiang Mai)", "only ${eateries.size} shops")
        collectShopFacts(eateries)
        recorder?.let { collectUnknownKeysFromRecording(it) }

        coroutineScopeAll(
            listOf(
                suspend { check("new shops") { api.newShops() }; Unit },
                suspend { check("for you") { api.forYou(null) }; Unit },
                suspend { check("autocomplete business 'pizza'") { api.autocompleteBusiness("pizza") }; Unit },
                suspend { check("autocomplete business 'zzqqxx' (no match)") { api.autocompleteBusiness("zzqqxx") }; Unit },
                suspend { check("menu item search 'pizza'") { api.searchMenuItems("pizza") }; Unit },
            ),
        )

        val handle = eateries.firstNotNullOfOrNull { it.data?.premiumLink?.takeIf(String::isNotBlank) }
        if (handle != null) {
            check("short link $handle") { api.shortLink(handle) }?.let { link ->
                if (link.businessId == null) fail("short link $handle", "no business id in ${link.url}")
            }
        }

        val shops = pickShops(eateries, options.menus)
        println("Checking menus of ${shops.size} shops …")
        coroutineScopeAll(shops.map { shop -> suspend { checkShop(shop) } })

        shops.firstOrNull { it.lat != null && it.lon != null }?.let { shop ->
            val from = LatLon(shop.lat!!, shop.lon!!)
            check("directions") { api.directions(from, LatLon.CHIANG_MAI) }
            check("price check") { api.priceCheck(from, LatLon.CHIANG_MAI) }
        }

        recorder?.writeManifest(facts["backend version"]?.keys?.firstOrNull())
        val report = report(System.currentTimeMillis() - started)
        println(report)
        recorder?.let { File(it.dir, "report.md").writeText(report) }
        return if (checks.any { !it.ok }) 1 else 0
    }

    private suspend fun checkShop(shop: Business) {
        val name = "menu ${shop.name} (${shop.id.take(8)})"
        val workflows = check(name) { api.workflowsForBusiness(shop.id) } ?: return
        val commerce = workflows.firstOrNull { it.workflowTypeName == Workflow.TYPE_COMMERCE }
        if (commerce == null) {
            fail(name, "no Commerce workflow")
            return
        }
        val menu = MenuParser.parse(commerce)
        if (menu.entries.isEmpty()) note("$name: menu has no items")
        menu.problems.forEach { note("$name: $it") }
        menu.entries.forEach { entry ->
            unknown("menu item", entry.raw, MenuItem.serializer().descriptor)
            fact("item discount types", entry.item.discountType ?: "(none)")
            entry.item.tags.orEmpty().filter { it.startsWith("*") || it == "member" || it == "dine-in" }.forEach { fact("special item tags", it) }
            if (entry.item.schedule != null && entry.item.schedule.toString() != "null") fact("item schedule values", entry.item.schedule.toString())
            fact("price text shapes", shape(entry.item.price))
        }
        menu.optionGroups.values.forEach { group ->
            unknown("option group", group.raw, OptionGroup.serializer().descriptor)
            fact("option select", group.group.select ?: "(none)")
            fact("option multiple_constraint", group.group.multipleConstraint ?: "(none)")
            if (group.group.condition != null) fact("option conditions", "present")
        }
        workflows.forEach { fact("workflow types per shop", it.workflowTypeName ?: "(none)") }

        gateCall("shop open ${shop.name}") { api.shopOpen(shop.id) }?.let { status ->
            fact("shop_open answers", status.toString().replace(Regex("\\d"), "9").take(90))
        }
        gateCall("delivery ${shop.name}") { api.commerceDelivery(commerce.id) }?.let { delivery ->
            fact("delivery fleet states", delivery.express?.state ?: "(no express)")
            delivery.express?.data?.pricingArray?.let { fact("pricing_array lengths", it.size.toString()) }
            if (delivery.fallback != null) fact("has fallback fleet", "yes")
        }
    }

    private fun collectShopFacts(eateries: List<Business>) {
        for (shop in eateries) {
            val data = shop.data
            data?.hours?.values?.forEach { fact("hours text shapes", shape(it)) }
            fact("hours_type", data?.hoursType ?: "(none)")
            val commerce = shop.commerceWorkflow
            if (commerce == null) {
                fact("shops without Commerce workflow", "count")
                continue
            }
            val wd = commerce.data
            fact("package (fruit) prefixes", wd?.fruit?.substringBefore('_')?.ifBlank { "(empty)" } ?: "(none)")
            wd?.paymentOptions?.forEach { fact("payment_options", it) }
            wd?.fulfilmentOptions?.forEach { fact("fulfilment_options", it) }
            fact("delivery_options.type", wd?.deliveryOptions?.type ?: "(none)")
            wd?.express?.let { fact("express fleets", it) }
            if ((wd?.vat ?: 0.0) > 0) fact("shops with VAT", "count")
            if ((wd?.minOrder ?: 0) > 0) fact("shops with min_order", "count")
        }
    }

    private fun collectUnknownKeysFromRecording(recorder: Recorder) {
        val raw = recorder.read("eateries.json") ?: return
        val array = TukJson.parseToJsonElement(raw).jsonArray
        for (element in array) {
            val obj = element.jsonObject
            unknown("business", obj, Business.serializer().descriptor)
            (obj["data"] as? JsonObject)?.let { unknown("business.data", it, BusinessData.serializer().descriptor) }
            (obj["workflows"] as? JsonArray)?.forEach { wf ->
                (wf.jsonObject["data"] as? JsonObject)?.let { unknown("workflow.data", it, WorkflowData.serializer().descriptor) }
            }
        }
    }

    /** Takes shops with a Commerce workflow, mixing package kinds so that each kind is checked. */
    private fun pickShops(eateries: List<Business>, count: Int): List<Business> {
        val candidates = eateries.filter { it.commerceWorkflow != null && it.data?.hidden != true }.sortedBy { it.id }
        val byPackage = candidates.groupBy { it.commerceWorkflow?.data?.fruit?.substringBefore('_') ?: "" }.values.map { it.toMutableList() }
        val picked = mutableListOf<Business>()
        while (picked.size < count && byPackage.any { it.isNotEmpty() }) {
            for (group in byPackage) if (group.isNotEmpty() && picked.size < count) picked += group.removeAt(0)
        }
        return picked
    }

    // ---- Check helpers

    private suspend fun <T> check(name: String, call: suspend () -> ApiResult<T>): T? = gateCall(name, call)

    private suspend fun <T> gateCall(name: String, call: suspend () -> ApiResult<T>): T? = gate.withPermit {
        when (val result = call()) {
            is ApiResult.Success -> {
                checks += Check(name, true, "", result.durationMs)
                result.value
            }
            is ApiResult.Failure -> {
                val detail = when (result) {
                    is ApiResult.ParseError -> "${result.message} | body starts: ${result.snippet.take(160)}"
                    else -> result.message
                }
                checks += Check(name, false, detail, null)
                null
            }
        }
    }

    private suspend fun coroutineScopeAll(blocks: List<suspend () -> Unit>) = kotlinx.coroutines.coroutineScope {
        blocks.map { async { it() } }.awaitAll()
    }

    private fun fail(name: String, detail: String) {
        checks += Check(name, false, detail, null)
    }

    private val notes = ConcurrentLinkedQueue<String>()
    private fun note(text: String) {
        notes += text
    }

    private fun fact(group: String, value: String) {
        val map = facts.getOrPut(group) { ConcurrentHashMap() }
        map.merge(value, 1, Int::plus)
    }

    private fun unknown(what: String, obj: JsonObject, descriptor: SerialDescriptor) {
        val known = (0 until descriptor.elementsCount).map { JsonNamingStrategy.SnakeCase.serialNameForJson(descriptor, it, descriptor.getElementName(it)) }.toSet()
        val map = unknownKeys.getOrPut(what) { ConcurrentHashMap() }
        obj.keys.filter { it !in known }.forEach { map.merge(it, 1, Int::plus) }
    }

    private fun tagKind(tag: String): String = when {
        tag.contains("http") -> "external link"
        tag.startsWith("@") || tag.startsWith("/@") -> "shop handle"
        tag.startsWith("/shop/") -> "shop id"
        tag.startsWith("/eat") -> "eat filter"
        tag.startsWith("*") -> "preset ${tag.substringBefore('?')}"
        tag.startsWith("/search/") -> "search"
        tag.startsWith("/") -> "other path ${tag.substringBefore('?').take(20)}"
        tag.isBlank() -> "(empty)"
        else -> "plain text"
    }

    /** "0800-1600" → "9999-9999": the form of a text, for format checks. */
    private fun shape(text: String?): String = when {
        text == null -> "(null)"
        text.isEmpty() -> "(empty)"
        else -> text.replace(Regex("\\d"), "9").replace(Regex("[A-Za-z]+"), "a")
    }

    private fun report(totalMs: Long): String = buildString {
        val failed = checks.filter { !it.ok }
        appendLine()
        appendLine("# API probe report")
        appendLine()
        appendLine("- Checks: ${checks.size}, failed: ${failed.size}, time: ${totalMs / 1000.0} s")
        appendLine()
        if (failed.isNotEmpty()) {
            appendLine("## Failed checks")
            appendLine()
            failed.forEach { appendLine("- **${it.name}**: ${it.detail}") }
            appendLine()
        }
        if (notes.isNotEmpty()) {
            appendLine("## Notes")
            appendLine()
            notes.sorted().forEach { appendLine("- $it") }
            appendLine()
        }
        appendLine("## Response times (ms)")
        appendLine()
        appendLine("| Endpoint | Calls | Median | Max |")
        appendLine("|---|---|---|---|")
        timings.toSortedMap().forEach { (key, list) ->
            val sorted = synchronized(list) { list.sorted() }
            appendLine("| `$key` | ${sorted.size} | ${sorted[sorted.size / 2]} | ${sorted.last()} |")
        }
        appendLine()
        appendLine("## Fields that our models do not read")
        appendLine()
        unknownKeys.toSortedMap().forEach { (what, keys) ->
            if (keys.isNotEmpty()) appendLine("- $what: " + keys.entries.sortedByDescending { it.value }.joinToString(", ") { "${it.key} (${it.value})" })
        }
        appendLine()
        appendLine("## Facts")
        appendLine()
        facts.toSortedMap().forEach { (group, values) ->
            appendLine("- $group: " + values.entries.sortedByDescending { it.value }.joinToString(", ") { "`${it.key}` (${it.value})" })
        }
    }
}

private data class Check(val name: String, val ok: Boolean, val detail: String, val ms: Long?)
