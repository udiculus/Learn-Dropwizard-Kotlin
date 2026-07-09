package com.learn.finance.account

import com.fasterxml.jackson.annotation.JsonProperty
import io.dropwizard.core.Configuration
import io.dropwizard.db.DataSourceFactory
import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * Dropwizard Configuration — mapped directly from config.yml
 *
 * KOTLIN CONCEPTS DEMONSTRATED:
 *  • Class properties with default values
 *  • lateinit vs nullable types  — use @NotNull + @Valid for non-nullable beans
 *  • Data class alternative      — regular class for mutable config
 *  • @JsonProperty annotation    — maps YAML keys to Kotlin properties
 * ─────────────────────────────────────────────────────────────────────────────
 */
class AccountConfiguration : Configuration() {

    /**
     * Database connection pool configuration.
     * `@Valid` triggers nested bean validation.
     * `@NotNull` prevents null values.
     */
    @Valid
    @NotNull
    @JsonProperty("database")
    val database: DataSourceFactory = DataSourceFactory()

    /**
     * Kafka configuration block.
     * Custom nested configuration class.
     */
    @Valid
    @NotNull
    @JsonProperty("kafka")
    val kafka: KafkaConfiguration = KafkaConfiguration()
}

/**
 * Kafka-specific configuration — a nested configuration class.
 *
 * KOTLIN CONCEPT: Secondary class with default property values.
 * All properties have sensible defaults so tests don't require a full config file.
 */
class KafkaConfiguration {

    @NotEmpty
    @JsonProperty("bootstrapServers")
    var bootstrapServers: String = "localhost:9092"

    @NotEmpty
    @JsonProperty("topicAccountEvents")
    var topicAccountEvents: String = "account.events"

    @NotEmpty
    @JsonProperty("producerClientId")
    var producerClientId: String = "account-service-producer"
}
