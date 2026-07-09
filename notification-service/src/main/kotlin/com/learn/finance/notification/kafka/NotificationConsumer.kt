package com.learn.finance.notification.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.learn.finance.notification.KafkaConfig
import com.learn.finance.notification.service.NotificationService
import io.dropwizard.lifecycle.Managed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.Properties

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * NotificationConsumer — Coroutine-based Kafka Consumer
 *
 * KAFKA CONCEPTS DEMONSTRATED:
 *  • Subscribing to multiple topics simultaneously
 *  • Consumer group for load sharing
 *  • Manual offset commit for reliability
 *  • Graceful shutdown with wakeup()
 *
 * KOTLIN COROUTINES CONCEPTS DEMONSTRATED:
 *  • CoroutineScope for the consumer lifecycle
 *  • launch {} — starts a coroutine for each Kafka record
 *  • suspend function calls inside coroutines
 *  • scope.cancel() for graceful coroutine shutdown
 *  • runBlocking {} — bridges Kafka's blocking poll loop with coroutines
 * ─────────────────────────────────────────────────────────────────────────────
 */
class NotificationConsumer(
    private val kafkaConfig: KafkaConfig,
    private val notificationService: NotificationService
) : Managed {

    private val logger       = LoggerFactory.getLogger(NotificationConsumer::class.java)
    private val objectMapper = ObjectMapper().apply { registerModule(KotlinModule.Builder().build()) }

    // KOTLIN COROUTINES: Scope with SupervisorJob — failures in one consumer don't cancel others
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var consumer: KafkaConsumer<String, String>
    @Volatile private var running = false
    private lateinit var consumerThread: Thread

    override fun start() {
        val props = Properties().apply {
            put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaConfig.bootstrapServers)
            // KAFKA CONCEPT: group.id — this consumer group reads each partition once
            put(ConsumerConfig.GROUP_ID_CONFIG,          kafkaConfig.consumerGroupId)
            put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,   StringDeserializer::class.java.name)
            put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
            put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,  "earliest")
            put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false)
        }

        consumer = KafkaConsumer(props)
        // KAFKA CONCEPT: subscribe to MULTIPLE topics at once
        consumer.subscribe(listOf(kafkaConfig.topicAccountEvents, kafkaConfig.topicTransactionEvents))

        running = true
        consumerThread = Thread({
            // KOTLIN COROUTINES: runBlocking bridges the blocking thread and coroutine world
            runBlocking {
                while (running) {
                    try {
                        val records = consumer.poll(Duration.ofSeconds(1))
                        for (record in records) {
                            val topic = record.topic()
                            logger.debug("Received event from topic=$topic, key=${record.key()}")

                            // KOTLIN COROUTINES: launch a new coroutine for each record
                            // This allows processing records concurrently
                            coroutineScope.launch {
                                processRecord(topic, record.value())
                            }
                        }
                        if (!records.isEmpty) consumer.commitSync()
                    } catch (ex: org.apache.kafka.common.errors.WakeupException) {
                        if (running) throw ex
                    } catch (ex: Exception) {
                        logger.error("Error in consumer loop: ${ex.message}", ex)
                    }
                }
            }
        }, "notification-consumer-thread")

        consumerThread.isDaemon = true
        consumerThread.start()
        logger.info("NotificationConsumer started — subscribed to: ${kafkaConfig.topicAccountEvents}, ${kafkaConfig.topicTransactionEvents}")
    }

    override fun stop() {
        running = false
        consumer.wakeup()
        consumerThread.join(5000)
        consumer.close()
        // KOTLIN COROUTINES: cancel() stops all coroutines in this scope
        coroutineScope.cancel()
        notificationService.cancelScope()
        logger.info("NotificationConsumer stopped.")
    }

    /**
     * KOTLIN COROUTINES: `suspend` function — called inside a coroutine.
     * This can be suspended without blocking the thread.
     */
    private suspend fun processRecord(topic: String, payload: String) {
        try {
            val event     = objectMapper.readValue<Map<String, Any?>>(payload)
            val eventType = event["eventType"] as? String ?: return

            // Extract the primary account ID (either accountId or sourceAccountId)
            val accountId = (event["accountId"] as? Number)?.toLong()
                ?: (event["sourceAccountId"] as? Number)?.toLong()
                ?: return

            // KOTLIN COROUTINES: calling another suspend function
            notificationService.processEvent(eventType, accountId, event)
            logger.debug("Processed $eventType from topic $topic for account $accountId")

            // For transfers, also generate a notification for the target account
            val targetAccountId = (event["targetAccountId"] as? Number)?.toLong()
            if (targetAccountId != null) {
                notificationService.processEvent(eventType, targetAccountId, event)
                logger.debug("Processed $eventType from topic $topic for target account $targetAccountId")
            }
        } catch (ex: Exception) {
            logger.error("Failed to process record from $topic: ${ex.message}")
        }
    }
}
