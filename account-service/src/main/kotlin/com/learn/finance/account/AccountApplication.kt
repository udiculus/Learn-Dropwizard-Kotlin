package com.learn.finance.account

import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.learn.finance.account.db.AccountDao
import com.learn.finance.account.db.CustomerDao
import com.learn.finance.account.health.DatabaseHealthCheck
import com.learn.finance.account.kafka.AccountEventProducer
import com.learn.finance.account.resources.AccountResource
import com.learn.finance.account.resources.CustomerResource
import com.learn.finance.account.service.AccountService
import com.learn.finance.account.service.CustomerService
import io.dropwizard.configuration.EnvironmentVariableSubstitutor
import io.dropwizard.configuration.SubstitutingSourceProvider
import io.dropwizard.core.Application
import io.dropwizard.core.setup.Bootstrap
import io.dropwizard.core.setup.Environment
import io.dropwizard.jdbi3.JdbiFactory
import org.jdbi.v3.sqlobject.SqlObjectPlugin

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * Account Service — Dropwizard Application Entry Point
 *
 * KOTLIN CONCEPTS DEMONSTRATED:
 *  • companion object  — holds the `main` entry point (replaces Java static)
 *  • class inheritance — AccountApplication extends Application<T>
 *  • function syntax   — override fun, top-level fun
 *  • string templates  — "${variable}" interpolation
 *  • Unit return type  — Kotlin's equivalent of void
 * ─────────────────────────────────────────────────────────────────────────────
 */
class AccountApplication : Application<AccountConfiguration>() {

    companion object {
        /**
         * Main entry point.
         * In Kotlin, `main` is a top-level function — no class wrapper needed.
         * The companion object lets us call it as a static-like function.
         */
        @JvmStatic
        fun main(args: Array<String>) {
            AccountApplication().run(*args)
        }
    }

    override fun getName(): String = "account-service"

    /**
     * Bootstrap: register bundles and modules before the application starts.
     * This is where you add Dropwizard bundles (e.g. migrations, assets).
     */
    override fun initialize(bootstrap: Bootstrap<AccountConfiguration>) {
        // Enable ${ENV_VAR:-default} substitution in config.yml
        bootstrap.configurationSourceProvider = SubstitutingSourceProvider(
            bootstrap.configurationSourceProvider,
            EnvironmentVariableSubstitutor(false)
        )
        // Register the Kotlin Jackson module so data classes serialize correctly
        bootstrap.objectMapper.registerModule(KotlinModule.Builder().build())
        // Schema is managed by SQL scripts in db/migration/ mounted into MySQL via docker-entrypoint-initdb.d
    }

    /**
     * Run: wire up all resources, health checks, and managed objects.
     *
     * KOTLIN CONCEPT: `apply` scope function — configures an object and returns it.
     */
    override fun run(configuration: AccountConfiguration, environment: Environment) {
        // ── Database setup with JDBI ─────────────────────────────────────────
        val factory = JdbiFactory()
        val jdbi = factory.build(environment, configuration.database, "mysql").apply {
            installPlugin(SqlObjectPlugin())
        }

        val accountDao  = jdbi.onDemand(AccountDao::class.java)
        val customerDao = jdbi.onDemand(CustomerDao::class.java)

        // ── Kafka Producer ───────────────────────────────────────────────────
        val eventProducer = AccountEventProducer(configuration.kafka).also {
            // Register as a managed lifecycle object so Dropwizard starts/stops it
            environment.lifecycle().manage(it)
        }

        // ── Services ─────────────────────────────────────────────────────────
        val customerService = CustomerService(customerDao)
        val accountService  = AccountService(accountDao, customerDao, eventProducer)

        // ── REST Resources ───────────────────────────────────────────────────
        environment.jersey().apply {
            register(CustomerResource(customerService))
            register(AccountResource(accountService))
        }

        // ── Health Checks ────────────────────────────────────────────────────
        environment.healthChecks().register(
            "database",
            DatabaseHealthCheck(jdbi)
        )

        println("✅ Account Service started. Listening on port ${configuration.getServerFactory()}")
    }
}

/**
 * Top-level entry point — Kotlin compiles this into AccountApplicationKt.main(),
 * which is what the maven-shade-plugin manifest references as the Main-Class.
 */
fun main(args: Array<String>) {
    AccountApplication.main(args)
}
