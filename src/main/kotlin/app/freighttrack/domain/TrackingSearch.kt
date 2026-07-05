package app.freighttrack.domain

import com.fasterxml.jackson.annotation.JsonProperty
import java.io.Serializable

data class TrackingSearch(
    @field:JsonProperty("entries") var entries: MutableList<ProNumberEntry>,
    @field:JsonProperty("results") var results: MutableList<ShipmentLookupResult> = mutableListOf()
) : Serializable {
    val count: Int get() = entries.size
    val hasResults: Boolean get() = results.isNotEmpty()
    val foundCount: Int get() = results.count { it.found }

    companion object {
        fun fresh(): TrackingSearch = TrackingSearch(entries = mutableListOf(ProNumberEntry()))
    }
}