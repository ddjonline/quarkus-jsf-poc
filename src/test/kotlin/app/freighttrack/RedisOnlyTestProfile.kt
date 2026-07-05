package app.freighttrack

import io.quarkus.test.junit.QuarkusTestProfile

class RedisOnlyTestProfile : QuarkusTestProfile {
    override fun getConfigOverrides(): MutableMap<String, String> = mutableMapOf(
        "quarkus.hibernate-orm.active" to "false"
    )
}