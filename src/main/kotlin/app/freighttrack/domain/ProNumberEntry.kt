package app.freighttrack.domain

import com.fasterxml.jackson.annotation.JsonProperty
import java.io.Serializable
import java.util.UUID

data class ProNumberEntry(
    @field:JsonProperty("id") val id: String = UUID.randomUUID().toString(),
    @field:JsonProperty("value") var value: String = "",
    @field:JsonProperty("checked") var checked: Boolean = false
) : Serializable {
    fun isBlank(): Boolean = value.trim().isEmpty()

    fun digits(): String =
        value.trim().removePrefix("PRO-").removePrefix("PRO ").filter { it.isDigit() }

    fun isValid(): Boolean =
        digits().let { it.isNotEmpty() && it.length <= TrackingConstants.PRO_MAX_DIGITS }

    fun normalized(): String = digits().padStart(TrackingConstants.PRO_MAX_DIGITS, '0')
}