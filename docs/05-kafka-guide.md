# Phase 5: Kafka Guide (Event-Driven Architecture)

This guide covers Apache Kafka, the messaging broker used for event communication between the microservices in this repository. We reference implementations in [AccountEventProducer.kt](file:///g:/Workplace/onboarding-insignia/account-service/src/main/kotlin/com/learn/finance/account/kafka/AccountEventProducer.kt), [TransactionKafka.kt](file:///g:/Workplace/onboarding-insignia/transaction-service/src/main/kotlin/com/learn/finance/transaction/kafka/TransactionKafka.kt), and [NotificationConsumer.kt](file:///g:/Workplace/onboarding-insignia/notification-service/src/main/kotlin/com/learn/finance/notification/kafka/NotificationConsumer.kt).

---

## 1. Kafka Core Concepts

Apache Kafka is a distributed streaming platform where services publish and consume streams of records.

*   **Producer:** A service that publishes events to Kafka.
*   **Consumer:** A service that reads events from Kafka.
*   **Broker:** A Kafka cluster node where events are stored.
*   **Topic:** A category/feed name to which records are published.
*   **Partition:** Topics are divided into partitions for scalability and parallelism. Order is guaranteed *only within a single partition*.
*   **Offset:** A unique sequential ID assigned to each record within a partition.
*   **Consumer Group:** A group of consumers that cooperate to consume data from a topic. Kafka ensures that each partition is consumed by only one member of the group, enabling horizontal scalability.

---

## 2. Kafka Producer Implementation

Producers publish events by creating a `KafkaProducer` and sending `ProducerRecord` objects.

### Configuration
Key configuration parameters for a reliable, idempotent producer:
*   `BOOTSTRAP_SERVERS_CONFIG`: Location of broker container(s).
*   `KEY_SERIALIZER_CLASS_CONFIG` & `VALUE_SERIALIZER_CLASS_CONFIG`: Serializers to convert keys and values into bytes. We use `StringSerializer`.
*   `ACKS_CONFIG = "all"`: The broker will wait for all in-sync replicas to acknowledge the record before responding.
*   `ENABLE_IDEMPOTENCE_CONFIG = true`: Prevents duplicate writes from network retries.

```kotlin
// Example from TransactionEventProducer in TransactionKafka.kt
val props = Properties().apply {
    put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,      kafkaConfig.bootstrapServers)
    put(ProducerConfig.CLIENT_ID_CONFIG,              kafkaConfig.producerClientId)
    put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,   StringSerializer::class.java.name)
    put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
    put(ProducerConfig.ACKS_CONFIG,                   "all")
    put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG,     true)
}
producer = KafkaProducer(props)
```

### Publishing Messages (Async with Callback)
We serialize our Kotlin domain models or payloads using Jackson to a JSON String, then wrap it in a `ProducerRecord` and send it asynchronously:

```kotlin
val record = ProducerRecord(
    kafkaConfig.topicTransactionEvents,
    transaction.transactionRef, // partition key: ensures same transaction orders land in the same partition
    objectMapper.writeValueAsString(eventPayload)
)

producer.send(record) { metadata, exception ->
    if (exception != null) {
        logger.error("Failed to publish event: ${exception.message}")
    } else {
        logger.debug("Published to partition=${metadata.partition()}, offset=${metadata.offset()}")
    }
}
```

---

## 3. Kafka Consumer Implementation

Consumers read events by subscribing to topics and executing a long-polling loop.

### Configuration
Key configuration parameters for a manual-commit consumer:
*   `GROUP_ID_CONFIG`: Identifies the consumer group.
*   `ENABLE_AUTO_COMMIT_CONFIG = false`: Disable automatic offset commits. Instead, we commit manually after processing the message. This guarantees **at-least-once delivery** and prevents data loss.
*   `AUTO_OFFSET_RESET_CONFIG = "earliest"`: Starts reading from the beginning of the topic if no committed offsets exist.

```kotlin
// Example from AccountEventConsumer in TransactionKafka.kt
val props = Properties().apply {
    put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,  kafkaConfig.bootstrapServers)
    put(ConsumerConfig.GROUP_ID_CONFIG,           kafkaConfig.consumerGroupId)
    put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,   StringDeserializer::class.java.name)
    put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
    put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,  "earliest")
    put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false)
}
consumer = KafkaConsumer(props)
```

### Polling Loop and Manual Offsets Commit
The poll loop runs in a dedicated background thread so it doesn't block the main application thread.

```kotlin
consumer.subscribe(listOf(kafkaConfig.topicAccountEvents))

running = true
consumerThread = Thread({
    while (running) {
        try {
            // Long-polling: blocks up to 1 second waiting for records
            val records = consumer.poll(Duration.ofSeconds(1))
            for (record in records) {
                processEvent(record.value())
            }
            // Commit offsets synchronously after successfully processing the batch
            if (!records.isEmpty) {
                consumer.commitSync()
            }
        } catch (ex: Exception) {
            if (running) logger.error("Error in consumer loop: ${ex.message}", ex)
        }
    }
}, "consumer-thread")
consumerThread.start()
```

---

## 4. Coroutine-Based Consumers

In the `notification-service`, the polling loop is combined with Kotlin Coroutines. Inside the thread execution, a `runBlocking` block bridges the blocking `poll()` and the async coroutines. This allows processing records concurrently using `launch {}`:

```kotlin
// Example from NotificationConsumer.kt
runBlocking {
    while (running) {
        val records = consumer.poll(Duration.ofSeconds(1))
        for (record in records) {
            // Launch concurrent coroutine for each record
            coroutineScope.launch {
                processRecord(record.topic(), record.value())
            }
        }
        if (!records.isEmpty) consumer.commitSync()
    }
}
```

---

## 5. Graceful Consumer Shutdown

A Kafka Consumer is not thread-safe. To stop it from another thread (like the application shutdown hook calling `Managed.stop()`), use the `wakeup()` method. This breaks any blocking `poll()` immediately by throwing a `WakeupException`, allowing the thread to exit the loop and close the consumer client cleanly.

```kotlin
override fun stop() {
    running = false
    consumer.wakeup()       // Interrupts consumer.poll()
    consumerThread.join(5000)
    consumer.close()        // Commits and leaves the group cleanly
    logger.info("Consumer stopped.")
}
```

---

## Next Steps
Now that you have learned about event streaming, proceed to [Phase 6: MySQL Guide](file:///g:/Workplace/onboarding-insignia/docs/06-mysql-guide.md) to explore relational database persistence!
