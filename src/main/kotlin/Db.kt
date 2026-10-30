package com.tahir.lost_and_found_app


import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.config.ApplicationConfig
import java.sql.Connection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object Db {
    private lateinit var ds: HikariDataSource

    fun init(config: ApplicationConfig) {
        val hc = HikariConfig().apply {
            jdbcUrl = config.property("db.url").getString()
            username = config.property("db.user").getString()
            password = config.property("db.password").getString()
            maximumPoolSize = 5
            poolName = "oracle-pool"
        }
        ds = HikariDataSource(hc)
    }

    // JDBC is blocking, so every DB call runs on the IO dispatcher.
    suspend fun <T> query(block: (Connection) -> T): T =
        withContext(Dispatchers.IO) { ds.connection.use(block) }
}