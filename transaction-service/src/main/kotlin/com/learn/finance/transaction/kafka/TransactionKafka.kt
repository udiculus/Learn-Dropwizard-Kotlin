package com.learn.finance.transaction.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.learn.finance.transaction.TransactionConfiguration
import com.learn.finance.transaction.model.AccountSnapshot
import com.learn.finance.transaction.model.Transaction
import com.learn.finance.transaction.service.AccountCache
import io.dropwizard.lifecycle.Managed
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.Properties
import java.util.UUID

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * AccountEventConsumer — Kafka Consumer for account.events
 *
 * KAFKA CONCEPTS DEMONSTRATED:
 *  • KafkaConsumer configuration (bootstrap.servers, group.id, offset reset)
 *  • subscribe() — subscribe to a list of topics
 *  • poll() — long-polling for new records
 *  • Manual offset commit (commitSync)
 *  • Consumer group ID — enables horizontal scaling
 *  • Consumer thread lifecycle (Managed)
 *
 * KOTLIN CONCEPTS:
 *  • Thread + Runnable — running the poll loop on a background thread
 *  • @Volatile flag for safe thread termination
 * ─────────────────────────────────────────────────────────────────────────────
 */
class AccountEventConsumer(
    private val kafkaConfig: com.learn.finance.transaction.KafkaConfig,
    private val accountCache: AccountCache
) : Managed {

    private val logger = LoggerFactory.getLogger(AccountEventConsumer::class.java)
    private val objectMapper = ObjectMapper().apply { registerModule(KotlinModule.Builder().build()) }

    private lateinit var consumer: KafkaConsumer<String, String>

    // KOTLIN CONCEPT: @Volatile — visible across threads immediately
    @Volatile private var running = false
    private lateinit var consumerThread: Thread

    override fun start() {
        val props = Properties().apply {
            put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,  kafkaConfig.bootstrapServers)
            // KAFKA CONCEPT: group.id — consumers with same group share partition load
            put(ConsumerConfig.GROUP_ID_CONFIG,           kafkaConfig.consumerGroupId)
            put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,   StringDeserializer::class.java.name)
            put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
            // KAFKA CONCEPT: auto.offset.reset — start from earliest when no committed offset exists
            put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,  "earliest")
            // KAFKA CONCEPT: enable.auto.commit=false — we commit manually for reliability
            put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false)
        }

        consumer = KafkaConsumer(props)
        // KAFKA CONCEPT: subscribe to a list of topics
        consumer.subscribe(listOf(kafkaConfig.topicAccountEvents))

        running = true
        // KAFKA CONCEPT: poll loop runs in a dedicated background thread
        consumerThread = Thread({
            while (running) {
                try {
                    // KAFKA CONCEPT: poll(timeout) — blocks up to 1s waiting for records
                    val records = consumer.poll(Duration.ofSeconds(1))
                    for (record in records) {
                        logger.debug("Consumed account event: key=${record.key()}, offset=${record.offset()}")
                        processEvent(record.value())
                    }
                    // KAFKA CONCEPT: commitSync — ensures offsets are committed only after processing
                    if (!records.isEmpty) consumer.commitSync()
                } catch (ex: Exception) {
                    if (running) logger.error("Error consuming account event: ${ex.message}", ex)
                }
            }
        }, "account-event-consumer-thread")

        consumerThread.isDaemon = true
        consumerThread.start()
        logger.info("AccountEventConsumer started, subscribing to: ${kafkaConfig.topicAccountEvents}")
    }

    override fun stop() {
        running = false
        consumer.wakeup()   // KAFKA CONCEPT: wakeup() interrupts a blocking poll()
        consumerThread.join(5000)
        consumer.close()
        logger.info("AccountEventConsumer stopped.")
    }

    private fun processEvent(payload: String) {
        try {
            val event = objectMapper.readValue<Map<String, Any>>(payload)
            val eventType = event["eventType"] as? String ?: return

            when (eventType) {
                "ACCOUNT_CREATED", "ACCOUNT_STATUS_CHANGED" -> {
                    val snapshot = AccountSnapshot(
                        accountId     = (event["accountId"] as? Number)?.toLong() ?: return,
                        accountNumber = event["accountNumber"] as? String ?: "",
                        customerId    = (event["customerId"] as? Number)?.toLong() ?: 0L,
                        balance       = java.math.BigDecimal(event["initialBalance"] as? String ?: "0"),
                        currency      = event["currency"] as? String ?: "USD",
                        status        = event["newStatus"] as? String ?: "ACTIVE"
                    )
                    accountCache.put(snapshot)
                    logger.debug("Updated account cache: ${snapshot.accountNumber}")
                }
                "ACCOUNT_CLOSED" -> {
                    val accountId = (event["accountId"] as? Number)?.toLong() ?: return
                    accountCache.remove(accountId)
                }
                else -> logger.debug("Unhandled account event type: $eventType")
            }
        } catch (ex: Exception) {
            logger.error("Failed to process account event: ${ex.message}")
        }
    }
}

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * TransactionEventProducer — Kafka Producer for transaction.events
 * ─────────────────────────────────────────────────────────────────────────────
 */
class TransactionEventProducer(
    private val kafkaConfig: com.learn.finance.transaction.KafkaConfig
) : Managed {

    private val logger = LoggerFactory.getLogger(TransactionEventProducer::class.java)
    private val objectMapper = ObjectMapper().apply { registerModule(KotlinModule.Builder().build()) }
    private lateinit var producer: KafkaProducer<String, String>

    override fun start() {
        val props = Properties().apply {
            put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,      kafkaConfig.bootstrapServers)
            put(ProducerConfig.CLIENT_ID_CONFIG,              kafkaConfig.producerClientId)
            put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,   StringSerializer::class.java.name)
            put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
            put(ProducerConfig.ACKS_CONFIG,                   "all")
            put(ProducerConfig.RETRIES_CONFIG,                3)
            put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG,     true)
        }
        producer = KafkaProducer(props)
        logger.info("TransactionEventProducer started.")
    }

    override fun stop() {
        if (::producer.isInitialized) {
            producer.flush()
            producer.close()
        }
    }

    fun publishTransactionCompleted(transaction: Transaction) {
        val event = mapOf(
            "eventId"         to UUID.randomUUID().toString(),
            "eventType"       to "TRANSACTION_COMPLETED",
            "transactionRef"  to transaction.transactionRef,
            "transactionId"   to transaction.id,
            "sourceAccountId" to transaction.sourceAccountId,
            "targetAccountId" to transaction.targetAccountId,
            "type"            to transaction.type.name,
            "amount"          to transaction.amount.toPlainString(),
            "currency"        to transaction.currency,
            "timestamp"       to java.time.Instant.now().toString()
        )

        val record = ProducerRecord(
            kafkaConfig.topicTransactionEvents,
            transaction.transactionRef,         // key: transaction ref for partitioning
            objectMapper.writeValueAsString(event)
        )

        producer.send(record) { metadata, exception ->
            if (exception != null)
                logger.error("Failed to publish transaction event: ${exception.message}")
            else
                logger.debug("Transaction event published → partition=${metadata.partition()}, offset=${metadata.offset()}")
        }
    }
}
