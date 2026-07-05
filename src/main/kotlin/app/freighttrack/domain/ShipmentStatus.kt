package app.freighttrack.domain

enum class ShipmentStatus(val label: String, val cssClass: String) {
    PICKED_UP("Picked Up", "badge-neutral"),
    IN_TRANSIT("In Transit", "badge-transit"),
    OUT_FOR_DELIVERY("Out for Delivery", "badge-transit"),
    DELIVERED("Delivered", "badge-delivered"),
    EXCEPTION("Exception", "badge-exception")
}
