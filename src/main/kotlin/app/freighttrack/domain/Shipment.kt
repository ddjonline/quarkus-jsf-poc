package app.freighttrack.domain

import com.fasterxml.jackson.annotation.JsonProperty
import java.io.Serializable

data class Shipment(
    @field:JsonProperty("proNumber") val proNumber: String,
    @field:JsonProperty("displayPro") val displayPro: String,
    @field:JsonProperty("status") val status: ShipmentStatus,
    @field:JsonProperty("origin") val origin: String,
    @field:JsonProperty("destination") val destination: String,
    @field:JsonProperty("shipper") val shipper: String,
    @field:JsonProperty("consignee") val consignee: String,
    @field:JsonProperty("commodity") val commodity: String,
    @field:JsonProperty("weightLbs") val weightLbs: Int,
    @field:JsonProperty("pieces") val pieces: Int,
    @field:JsonProperty("pickupTime") val pickupTime: String,
    @field:JsonProperty("driverName") val driverName: String,
    @field:JsonProperty("driverPhone") val driverPhone: String,
    @field:JsonProperty("currentLocation") val currentLocation: String,
    @field:JsonProperty("lastUpdate") val lastUpdate: String,
    @field:JsonProperty("estimatedDelivery") val estimatedDelivery: String,
    @field:JsonProperty("timeline") val timeline: List<TrackingEvent> = emptyList()
) : Serializable {
    val routeSummary: String get() = "$origin -> $destination"
}