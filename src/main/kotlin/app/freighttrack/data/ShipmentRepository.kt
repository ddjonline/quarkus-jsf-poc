package app.freighttrack.data

import app.freighttrack.domain.*
import io.quarkus.hibernate.orm.panache.kotlin.PanacheRepositoryBase
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
open class ShipmentRepository : PanacheRepositoryBase<ShipmentEntity, String> {

    open fun findByPro(pro: String): Shipment? =
        findById(pro.normalizePro())?.toDomain()

    open fun all(): List<Shipment> = listAll().map { it.toDomain() }

    companion object {
        fun normalizePro(input: String): String {
            val digits = input.trim()
                .removePrefix("PRO-")
                .removePrefix("PRO ")
                .filter { it.isDigit() }
            return if (digits.isBlank()) "" else digits.padStart(TrackingConstants.PRO_MAX_DIGITS, '0')
        }

        fun displayPro(input: String) = "PRO-${input.trimStart('0').ifEmpty { "0" }}"

        fun toDomain(entity: ShipmentEntity) = Shipment(
            proNumber = entity.proNumber,
            displayPro = entity.displayPro,
            status = ShipmentStatus.valueOf(entity.status),
            origin = entity.origin, destination = entity.destination,
            shipper = entity.shipper, consignee = entity.consignee, commodity = entity.commodity,
            weightLbs = entity.weightLbs, pieces = entity.pieces,
            pickupTime = entity.pickupTime, driverName = entity.driverName, driverPhone = entity.driverPhone,
            currentLocation = entity.currentLocation, lastUpdate = entity.lastUpdate,
            estimatedDelivery = entity.estimatedDelivery,
            timeline = entity.events.map { toDomain(it) }
        )

        fun toDomain(entity: TrackingEventEntity) = TrackingEvent(
            timeLabel = entity.timeLabel, title = entity.title, location = entity.location,
            state = TrackingEventState.valueOf(entity.state)
        )
    }
}

private fun String.normalizePro() = ShipmentRepository.normalizePro(this)
private fun ShipmentEntity.toDomain() = ShipmentRepository.toDomain(this)
private fun TrackingEventEntity.toDomain() = ShipmentRepository.toDomain(this)