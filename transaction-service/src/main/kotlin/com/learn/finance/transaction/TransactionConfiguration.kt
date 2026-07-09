package com.learn.finance.transaction

import com.fasterxml.jackson.annotation.JsonProperty
import io.dropwizard.core.Configuration
import io.dropwizard.db.DataSourceFactory
import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull

class TransactionConfiguration : Configuration() {
    @Valid @NotNull @JsonProperty("database")
    val database: DataSourceFactory = DataSourceFactory()

    @Valid @NotNull @JsonProperty("kafka")
    val kafka: KafkaConfig = KafkaConfig()
}

class KafkaConfig {
    @NotEmpty @JsonProperty("bootstrapServers")   var bootstrapServers: String = "localhost:9092"
    @NotEmpty @JsonProperty("topicTransactionEvents") var topicTransactionEvents: String = "transaction.events"
    @NotEmpty @JsonProperty("topicAccountEvents")     var topicAccountEvents: String = "account.events"
    @NotEmpty @JsonProperty("producerClientId")   var producerClientId: String = "transaction-service-producer"
    @NotEmpty @JsonProperty("consumerGroupId")    var consumerGroupId: String = "transaction-service-consumer"
}
