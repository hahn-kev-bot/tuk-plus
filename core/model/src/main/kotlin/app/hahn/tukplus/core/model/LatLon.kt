package app.hahn.tukplus.core.model

/** A point on the map. [toString] gives the form that the API wants: "lat,lon". */
data class LatLon(val lat: Double, val lon: Double) {
    override fun toString(): String = "$lat,$lon"

    companion object {
        /** Centre of Chiang Mai, the only region of release 1 (PLAN.md §1). */
        val CHIANG_MAI = LatLon(18.796143, 98.979263)
    }
}
