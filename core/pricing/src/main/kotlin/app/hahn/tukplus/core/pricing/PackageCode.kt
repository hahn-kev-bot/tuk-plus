package app.hahn.tukplus.core.pricing

/**
 * Shop package code (`workflow.data.fruit`), web module `84f5` (docs/pricing.md §9).
 * All values are fractions (0.1 = 10 %). NaN where the web code gives NaN (bad codes).
 */
object PackageCode {
    private const val R = "r_"

    /** The eat list shows a "free delivery" tag for these codes. */
    fun hasFreeDelivery(code: String?): Boolean =
        !code.isNullOrEmpty() && (code.startsWith("p_") || code.startsWith("f_") || code.startsWith("thai") || code.startsWith(R))

    /** Part of the basket value that Tuk pays for the delivery. */
    fun deliverySubsidy(code: String?): Double {
        if (code.isNullOrEmpty()) return 0.0
        if (code.startsWith(R) && code.split(R).size > 1) {
            val e = code.split(R)[1].split("_")
            return if (e.size != 2) 0.0 else Js.parseInt(e[1]) / 100
        }
        if (code.startsWith("p_") && code.split("p_").size > 1) return Js.parseInt(code.split("p_")[1].split("_")[0]) / 100
        if (code.startsWith("f_") && code.split("f_").size > 1) return Js.parseInt(code.split("f_")[1].split("_")[0]) / 100
        return when (code) {
            "thai0", "thai0_10", "thai0_15", "thai0_20", "thai0_25" -> 0.0
            "thai10", "thai10_5", "thai1010", "thai10_10", "thai10_0" -> 0.1
            "thai15", "thai15_5", "thai15_10" -> 0.15
            "thai20", "thai20_0", "thai20_5" -> 0.2
            "thai25", "thai25_0" -> 0.25
            else -> 0.0
        }
    }

    /** Part of the basket value that the shop pays to Tuk as a bill (`data.billing`). */
    fun billing(code: String?): Double {
        if (code.isNullOrEmpty()) return 0.0
        if (code.startsWith("p_") && code.split("p_").size > 1) {
            val e = code.split("p_")[1].split("_")
            if (e.size == 2) return Js.parseInt(e[1]) / 100
        }
        for (prefix in listOf("f_", "thai")) {
            if (code.startsWith(prefix) && code.split(prefix).size > 1) {
                val e = code.split(prefix)[1].split("_")
                if (e.size == 2) return Js.parseInt(e[1]) / 100
            }
        }
        return 0.0
    }

    /** Tuk commission as a part of the basket value (`r_` codes only). */
    fun remit(code: String?): Double {
        if (code.isNullOrEmpty()) return 0.0
        if (code.startsWith(R) && code.split(R).size > 1) return Js.parseInt(code.split(R)[1].split("_")[0]) / 100
        return 0.0
    }
}
