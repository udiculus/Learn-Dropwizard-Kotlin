package com.learn.finance.transaction

import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.learn.finance.transaction.db.TransactionDao
import com.learn.finance.transaction.health.DatabaseHealthCheck
import com.learn.finance.transaction.kafka.AccountEventConsumer
import com.learn.finance.transaction.kafka.TransactionEventProducer
import com.learn.finance.transaction.resources.TransactionResource
import com.learn.finance.transaction.service.AccountCache
import com.learn.finance.transaction.service.TransactionService
import com.learn.finance.transaction.service.TransactionValidator
import io.dropwizard.configuration.EnvironmentVariableSubstitutor
import io.dropwizard.configuration.SubstitutingSourceProvider
import io.dropwizard.core.Application
import io.dropwizard.core.setup.Bootstrap
import io.dropwizard.core.setup.Environment
import io.dropwizard.jdbi3.JdbiFactory
import org.jdbi.v3.sqlobject.SqlObjectPlugin

class TransactionApplication : Application<TransactionConfiguration>() {

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            TransactionApplication().run(*args)
        }
    }

    override fun getName(): String = "transaction-service"

    override fun initialize(bootstrap: Bootstrap<TransactionConfiguration>) {
        // Enable ${ENV_VAR:-default} substitution in config.yml
        bootstrap.configurationSourceProvider = SubstitutingSourceProvider(
            bootstrap.configurationSourceProvider,
            EnvironmentVariableSubstitutor(false)
        )
        bootstrap.objectMapper.registerModule(KotlinModule.Builder().build())
        // Schema is managed by SQL scripts in db/migration/ mounted into MySQL via docker-entrypoint-initdb.d
    }

    override fun run(configuration: TransactionConfiguration, environment: Environment) {
        // ── Database ─────────────────────────────────────────────────────────
        val jdbi = JdbiFactory().build(environment, configuration.database, "mysql").apply {
            installPlugin(SqlObjectPlugin())
        }
        val transactionDao = jdbi.onDemand(TransactionDao::class.java)

        // ── Kafka ─────────────────────────────────────────────────────────────
        val eventProducer = TransactionEventProducer(configuration.kafka).also {
            environment.lifecycle().manage(it)
        }

        // ── Shared account cache (updated by Kafka consumer) ──────────────────
        val accountCache = AccountCache()

        val accountConsumer = AccountEventConsumer(configuration.kafka, accountCache).also {
            environment.lifecycle().manage(it)
        }

        // ── Services ──────────────────────────────────────────────────────────
        val validator          = TransactionValidator()
        val transactionService = TransactionService(transactionDao, accountCache, validator, eventProducer)

        // ── Resources ─────────────────────────────────────────────────────────
        environment.jersey().register(TransactionResource(transactionService))

        // ── Health checks ─────────────────────────────────────────────────────
        environment.healthChecks().register("database", DatabaseHealthCheck(jdbi))
    }
}

/**
 * Top-level entry point — Kotlin compiles this into TransactionApplicationKt.main(),
 * which is what the maven-shade-plugin manifest references as the Main-Class.
 */
fun main(args: Array<String>) {
    TransactionApplication.main(args)
}
