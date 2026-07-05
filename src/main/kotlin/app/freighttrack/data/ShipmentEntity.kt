package app.freighttrack.data

import jakarta.persistence.*

@Entity
@Table(name = "shipment")
open class ShipmentEntity {
    @Id
    @Column(name = "pro_number")
    lateinit var proNumber: String

    @Column(name = "display_pro")
    lateinit var displayPro: String

    @Column(name = "status")
    lateinit var status: String

    lateinit var origin: String
    lateinit var destination: String
    lateinit var shipper: String
    lateinit var consignee: String
    lateinit var commodity: String

    @Column(name = "weight_lbs")
    var weightLbs: Int = 0

    var pieces: Int = 0

    @Column(name = "pickup_time")
    lateinit var pickupTime: String

    @Column(name = "driver_name")
    lateinit var driverName: String

    @Column(name = "driver_phone")
    lateinit var driverPhone: String

    @Column(name = "current_location")
    lateinit var currentLocation: String

    @Column(name = "last_update")
    lateinit var lastUpdate: String

    @Column(name = "estimated_delivery")
    lateinit var estimatedDelivery: String

    @OneToMany(fetch = FetchType.EAGER)
    @JoinColumn(name = "pro_number")
    @OrderBy("seq ASC")
    var events: MutableList<TrackingEventEntity> = mutableListOf()
}
