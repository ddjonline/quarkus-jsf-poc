package app.freighttrack.domain

import com.fasterxml.jackson.annotation.JsonProperty
import java.io.Serializable

data class TrackingEvent(
    @field:JsonProperty("timeLabel") val timeLabel: String,
    @field:JsonProperty("title") val title: String,
    @field:JsonProperty("location") val location: String,
    @field:JsonProperty("state") val state: TrackingEventState
) : Serializable
