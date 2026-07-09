package com.learn.finance.account.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.learn.finance.account.AccountConfiguration
import com.learn.finance.account.model.Account
import com.learn.finance.account.model.AccountStatus
import io.dropwizard.lifecycle.Managed
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.Properties
import java.util.UUID

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * Kafka Account Event Types — Sealed Class Hierarchy
 *
 * KAFKA CONCEPTS DEMONSTRATED:
 *  • Event-driven architecture — publishing domain events
 *  • Topic design — one topic for all account events, differentiated by type
 *
 * KOTLIN CONCEPTS DEMONSTRATED:
 *  • sealed class with data properties in each subclass
 *  • when expression for exhaustive pattern matching
 * ─────────────────────────────────────────────────────────────────────────────
 */
sealed class AccountEvent {
    abstract val eventId: String
    abstract val eventType: String
    abstract val accountId: Long
    abstract val accountNumber: String
    abstract val timestamp: String

    data class AccountCreated(
        override val eventId: String      = UUID.randomUUID().toString(),
        override val eventType: String    = "ACCOUNT_CREATED",
        override val accountId: Long,
        override val accountNumber: String,
        override val timestamp: String    = Instant.now().toString(),
        val customerId: Long,
        val accountType: String,
        val currency: String,
        val initialBalance: String
    ) : AccountEvent()

    data class AccountStatusChanged(
        override val eventId: String      = UUID.randomUUID().toString(),
        override val eventType: String    = "ACCOUNT_STATUS_CHANGED",
        override val accountId: Long,
        override val accountNumber: String,
        override val timestamp: String    = Instant.now().toString(),
        val oldStatus: String,
        val newStatus: String
    ) : AccountEvent()

    data class AccountClosed(
        override val eventId: String      = UUID.randomUUID().toString(),
        override val eventType: String    = "ACCOUNT_CLOSED",
        override val accountId: Long,
        override val accountNumber: String,
        override val timestamp: String    = Instant.now().toString()
    ) : AccountEvent()
}

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * AccountEventProducer — Kafka Producer
 *
 * KAFKA CONCEPTS DEMONSTRATED:
 *  • KafkaProducer lifecycle (create, send, close)
 *  • ProducerConfig settings (bootstrap.servers, key/value serializers, acks)
 *  • ProducerRecord: topic, key (accountNumber for partitioning), value (JSON)
 *  • Asynchronous send with callback
 *  • Message key for ordered delivery per account
 *
 * DROPWIZARD CONCEPT:
 *  • Implements `Managed` so Dropwizard controls the lifecycle (start/stop).
 *
 * KOTLIN CONCEPTS:
 *  • implements interface (Managed)
 *  • Properties block for lazy KafkaProducer initialization
 *  • String templates in log messages
 * ─────────────────────────────────────────────────────────────────────────────
 */
class AccountEventProducer(
    private val kafkaConfig: com.learn.finance.account.KafkaConfiguration
) : Managed {

    private val logger = LoggerFactory.getLogger(AccountEventProducer::class.java)

    // KOTLIN CONCEPT: lazy initialization — producer is created only when first accessed
    private val objectMapper: ObjectMapper = ObjectMapper().apply {
        registerModule(KotlinModule.Builder().build())
    }

    private lateinit var producer: KafkaProducer<String, String>

    /**
     * DROPWIZARD CONCEPT: start() is called when Dropwizard starts.
     * KAFKA CONCEPT: Producer configuration — key settings explained:
     *  - bootstrap.servers: initial contact points for the cluster
     *  - acks=all: wait for all in-sync replicas to acknowledge (durability)
     *  - retries: auto-retry on transient errors
     *  - key.serializer: account number (String) as partition key
     *  - value.serializer: event JSON payload (String)
     */
    override fun start() {
        val props = Properties().apply {
            put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,  kafkaConfig.bootstrapServers)
            put(ProducerConfig.CLIENT_ID_CONFIG,          kafkaConfig.producerClientId)
            put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,   StringSerializer::class.java.name)
            put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
            put(ProducerConfig.ACKS_CONFIG,               "all")
            put(ProducerConfig.RETRIES_CONFIG,            3)
            put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true)
        }
        producer = KafkaProducer(props)
        logger.info("Kafka producer started. Bootstrap: ${kafkaConfig.bootstrapServers}")
    }

    /** DROPWIZARD CONCEPT: stop() is called on graceful shutdown. */
    override fun stop() {
        if (::producer.isInitialized) {
            producer.flush()
            producer.close()
            logger.info("Kafka producer closed.")
        }
    }

    // ── Publish Methods ────────────────────────────────────────────────────────

    fun publishAccountCreated(account: Account) {
        val event = AccountEvent.AccountCreated(
            accountId      = account.id!!,
            accountNumber  = account.accountNumber,
            customerId     = account.customerId,
            accountType    = account.accountType.name,
            currency       = account.currency,
            initialBalance = account.balance.toPlainString()
        )
        publish(event)
    }

    fun publishAccountStatusChanged(account: Account, newStatus: AccountStatus) {
        val event = AccountEvent.AccountStatusChanged(
            accountId     = account.id!!,
            accountNumber = account.accountNumber,
            oldStatus     = account.status.toString(),
            newStatus     = newStatus.toString()
        )
        publish(event)
    }

    fun publishAccountClosed(account: Account) {
        val event = AccountEvent.AccountClosed(
            accountId     = account.id!!,
            accountNumber = account.accountNumber
        )
        publish(event)
    }

    /**
     * KAFKA CONCEPT: Core publish method.
     *  - Key: accountNumber — ensures all events for the same account go to the same partition (ordering)
     *  - Value: JSON-serialized event
     *  - Callback: handles async success/failure without blocking the HTTP thread
     */
    private fun publish(event: AccountEvent) {
        val topic   = kafkaConfig.topicAccountEvents
        val key     = event.accountNumber
        val payload = objectMapper.writeValueAsString(event)

        // KAFKA CONCEPT: ProducerRecord wraps topic + key + value
        val record = ProducerRecord(topic, key, payload)

        // KAFKA CONCEPT: Asynchronous send — does NOT block the calling thread
        producer.send(record) { metadata, exception ->
            // KOTLIN CONCEPT: when expression for null check
            when {
                exception != null ->
                    logger.error("Failed to publish ${event.eventType} to $topic: ${exception.message}")
                else ->
                    logger.debug(
                        "Published ${event.eventType} → topic=$topic, " +
                        "partition=${metadata.partition()}, offset=${metadata.offset()}"
                    )
            }
        }
    }
}
