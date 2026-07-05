package app.freighttrack.domain

import com.fasterxml.jackson.annotation.JsonProperty
import java.io.Serializable

data class ShipmentLookupResult(
    @field:JsonProperty("queriedPro") val queriedPro: String,
    @field:JsonProperty("displayPro") val displayPro: String,
    @field:JsonProperty("state") var state: LookupState = LookupState.PENDING,
    @field:JsonProperty("shipment") var shipment: Shipment? = null,
    @field:JsonProperty("expanded") var expanded: Boolean = false,
    @field:JsonProperty("errorMessage") var errorMessage: String? = null
) : Serializable {
    val found: Boolean get() = state == LookupState.LOADED && shipment != null
}