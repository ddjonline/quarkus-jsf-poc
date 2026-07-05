package app.freighttrack.service

import app.freighttrack.data.ShipmentRepository
import app.freighttrack.domain.*
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional

@ApplicationScoped
open class ShipmentService {

    @Inject
    lateinit var repo: ShipmentRepository

    @Transactional
    open fun find(pro: String): Shipment? = repo.findByPro(pro)

    @Transactional
    open fun lookup(pros: List<String>): List<ShipmentLookupResult> =
        pros.map { ShipmentRepository.normalizePro(it) }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(TrackingConstants.MAX_PRO_NUMBERS)
            .map { pro ->
                val shipment = repo.findByPro(pro)
                ShipmentLookupResult(
                    queriedPro = pro,
                    displayPro = ShipmentRepository.displayPro(pro),
                    state = if (shipment == null) LookupState.NOT_FOUND else LookupState.LOADED,
                    shipment = shipment
                )
            }
}