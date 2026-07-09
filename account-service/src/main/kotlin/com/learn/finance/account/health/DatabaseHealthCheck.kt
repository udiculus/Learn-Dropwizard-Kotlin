package com.learn.finance.account.health

import com.codahale.metrics.health.HealthCheck
import org.jdbi.v3.core.Jdbi

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * DatabaseHealthCheck — Dropwizard Health Check
 *
 * DROPWIZARD CONCEPT:
 *  • HealthCheck subclass — registered with Dropwizard's health check registry
 *  • Available at GET /healthcheck (admin port 8081)
 *  • Returns healthy/unhealthy with optional message
 *
 * KOTLIN CONCEPT:
 *  • try/catch with return expression
 * ─────────────────────────────────────────────────────────────────────────────
 */
class DatabaseHealthCheck(private val jdbi: Jdbi) : HealthCheck() {

    override fun check(): Result {
        return try {
            jdbi.withHandle<Int, Exception> { handle ->
                handle.createQuery("SELECT 1").mapTo(Int::class.java).one()
            }
            Result.healthy("MySQL is reachable")
        } catch (ex: Exception) {
            Result.unhealthy("MySQL is unreachable: ${ex.message}")
        }
    }
}
