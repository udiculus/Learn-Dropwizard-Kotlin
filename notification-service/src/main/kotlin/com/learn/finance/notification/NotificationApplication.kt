package com.learn.finance.notification

import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.learn.finance.notification.db.AuditLogRepository
import com.learn.finance.notification.db.NotificationRepository
import com.learn.finance.notification.health.MongoHealthCheck
import com.learn.finance.notification.kafka.NotificationConsumer
import com.learn.finance.notification.resources.AuditLogResource
import com.learn.finance.notification.resources.NotificationResource
import com.learn.finance.notification.service.NotificationService
import com.mongodb.client.MongoClients
import io.dropwizard.configuration.EnvironmentVariableSubstitutor
import io.dropwizard.configuration.SubstitutingSourceProvider
import io.dropwizard.core.Application
import io.dropwizard.core.setup.Bootstrap
import io.dropwizard.core.setup.Environment

class NotificationApplication : Application<NotificationConfiguration>() {

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            NotificationApplication().run(*args)
        }
    }

    override fun getName(): String = "notification-service"

    override fun initialize(bootstrap: Bootstrap<NotificationConfiguration>) {
        // Enable ${ENV_VAR:-default} substitution in config.yml
        bootstrap.configurationSourceProvider = SubstitutingSourceProvider(
            bootstrap.configurationSourceProvider,
            EnvironmentVariableSubstitutor(false)
        )
        bootstrap.objectMapper.registerModule(KotlinModule.Builder().build())
    }

    override fun run(configuration: NotificationConfiguration, environment: Environment) {
        // ── MongoDB client ────────────────────────────────────────────────────
        val mongoUri = "mongodb://${configuration.mongodb.username}:${configuration.mongodb.password}" +
                       "@${configuration.mongodb.host}:${configuration.mongodb.port}/" +
                       "?authSource=admin"

        val mongoClient = MongoClients.create(mongoUri)
        val database    = mongoClient.getDatabase(configuration.mongodb.database)

        // ── Repositories ──────────────────────────────────────────────────────
        val notificationRepo = NotificationRepository(database)
        val auditLogRepo     = AuditLogRepository(database)

        // ── Service ───────────────────────────────────────────────────────────
        val notificationService = NotificationService(notificationRepo, auditLogRepo)

        // ── Kafka consumer (coroutine-based) ──────────────────────────────────
        val consumer = NotificationConsumer(configuration.kafka, notificationService).also {
            environment.lifecycle().manage(it)
        }

        // ── Resources ─────────────────────────────────────────────────────────
        environment.jersey().apply {
            register(NotificationResource(notificationService))
            register(AuditLogResource(notificationService))
        }

        // ── Health check ──────────────────────────────────────────────────────
        environment.healthChecks().register("mongodb", MongoHealthCheck(mongoClient))
    }
}

/**
 * Top-level entry point — Kotlin compiles this into NotificationApplicationKt.main(),
 * which is what the maven-shade-plugin manifest references as the Main-Class.
 */
fun main(args: Array<String>) {
    NotificationApplication.main(args)
}
