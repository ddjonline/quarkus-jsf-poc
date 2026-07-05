package app.freighttrack.data

import jakarta.persistence.*

@Entity
@Table(name = "tracking_event")
open class TrackingEventEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(name = "pro_number")
    lateinit var proNumber: String

    var seq: Int = 0

    @Column(name = "time_label")
    lateinit var timeLabel: String

    lateinit var title: String
    lateinit var location: String
    lateinit var state: String
}
