package app.hahn.tukplus.core.domain

import app.hahn.tukplus.core.model.Blob
import app.hahn.tukplus.core.model.Business
import app.hahn.tukplus.core.model.BusinessData
import app.hahn.tukplus.core.model.Menu
import app.hahn.tukplus.core.model.MenuParser
import app.hahn.tukplus.core.model.PageBlobs
import app.hahn.tukplus.core.model.PageRow
import app.hahn.tukplus.core.model.TukJson
import app.hahn.tukplus.core.model.Workflow
import app.hahn.tukplus.core.model.WorkflowData
import java.io.File
import java.time.LocalDateTime
import java.time.ZonedDateTime
import kotlin.test.fail

/** The responses that tools/api-probe recorded from the live API, and helpers to make test data. */
object Fixtures {
    val dir: File by lazy {
        val path = System.getProperty("fixturesDir") ?: fail("System property fixturesDir is not set")
        File(path).also { if (!it.isDirectory) fail("No fixtures in $it") }
    }

    val eateries: List<Business> by lazy {
        TukJson.decodeFromString<List<Business>>(File(dir, "eateries.json").readText())
    }

    /** All 30 recorded menus, by file name. */
    val menus: Map<String, Menu> by lazy {
        File(dir, "workflows").listFiles().orEmpty().sortedBy { it.name }.associate { file ->
            val workflows = TukJson.decodeFromString<List<Workflow>>(file.readText())
            file.name to MenuParser.parse(workflows.single { it.workflowTypeName == Workflow.TYPE_COMMERCE })
        }
    }

    val pageRows: List<PageRow> by lazy {
        File(dir, "pages").listFiles().orEmpty().sortedBy { it.name }.mapNotNull { file ->
            val text = file.readText().trim()
            if (text == "null") null
            else PageBlobs.toRow(file.nameWithoutExtension, TukJson.decodeFromString<List<Blob>>(text))
        }
    }

    /** A time in Chiang Mai. 2026-09-28 is a Monday. */
    fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): ZonedDateTime =
        ZonedDateTime.of(LocalDateTime.of(year, month, day, hour, minute), ChiangMaiTime.ZONE)

    /** Monday 2026-09-28 at the given time, in Chiang Mai. */
    fun monday(hour: Int, minute: Int = 0): ZonedDateTime = at(2026, 9, 28, hour, minute)

    fun shop(
        id: String = "shop",
        name: String = "Shop",
        hours: Map<String, String?>? = null,
        hoursType: String? = "selected-hours",
        commerce: WorkflowData? = WorkflowData(),
        categories: String? = null,
        searchTerms: String? = null,
        lat: Double? = null,
        lon: Double? = null,
        createdAt: String? = null,
        hidden: Boolean? = null,
        isActive: Boolean = true,
        isDeleted: Boolean = false,
    ): Business = Business(
        id = id,
        name = name,
        lat = lat,
        lon = lon,
        createdAt = createdAt,
        isActive = isActive,
        isDeleted = isDeleted,
        data = BusinessData(
            hidden = hidden,
            hours = hours,
            hoursType = hoursType,
            categories = categories,
            searchTerms = searchTerms,
        ),
        workflows = commerce?.let {
            listOf(Workflow(id = "wf-$id", workflowTypeName = Workflow.TYPE_COMMERCE, data = it))
        },
    )
}
