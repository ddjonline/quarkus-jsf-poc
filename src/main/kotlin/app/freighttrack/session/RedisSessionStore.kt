package app.freighttrack.session

import app.freighttrack.domain.TrackingSearch
import io.quarkus.redis.datasource.RedisDataSource
import io.quarkus.redis.datasource.value.ValueCommands
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.time.Duration

@ApplicationScoped
open class RedisSessionStore {

    @Inject
    lateinit var redis: RedisDataSource

    private val cmd: ValueCommands<String, TrackingSearch> by lazy {
        redis.value(String::class.java, TrackingSearch::class.java)
    }

    private val ttl = Duration.ofMinutes(30)

    private fun key(id: String) = "ft:session:$id"

    open fun load(id: String): TrackingSearch {
        return try {
            cmd.get(key(id)) ?: TrackingSearch.fresh()
        } catch (_: Exception) {
            TrackingSearch.fresh()
        }
    }

    open fun save(id: String, search: TrackingSearch) {
        try {
            cmd.setex(key(id), ttl.seconds, search)
        } catch (_: Exception) {}
    }

    open fun clear(id: String) {
        try {
            redis.key().del(key(id))
        } catch (_: Exception) {}
    }
}