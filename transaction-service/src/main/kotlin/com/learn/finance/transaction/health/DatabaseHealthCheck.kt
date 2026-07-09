package com.learn.finance.transaction.health

import com.codahale.metrics.health.HealthCheck
import org.jdbi.v3.core.Jdbi

class DatabaseHealthCheck(private val jdbi: Jdbi) : HealthCheck() {
    override fun check(): Result = try {
        jdbi.withHandle<Int, Exception> { handle ->
            handle.createQuery("SELECT 1").mapTo(Int::class.java).one()
        }
        Result.healthy("MySQL reachable")
    } catch (ex: Exception) {
        Result.unhealthy("MySQL unreachable: ${ex.message}")
    }
}
