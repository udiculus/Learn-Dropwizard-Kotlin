package com.learn.finance.notification

import com.fasterxml.jackson.annotation.JsonProperty
import io.dropwizard.core.Configuration
import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull

class NotificationConfiguration : Configuration() {
    @Valid @NotNull @JsonProperty("mongodb") val mongodb: MongoConfig = MongoConfig()
    @Valid @NotNull @JsonProperty("kafka")   val kafka: KafkaConfig   = KafkaConfig()
}

class MongoConfig {
    @NotEmpty @JsonProperty("host")     var host: String     = "localhost"
    @NotNull  @JsonProperty("port")     var port: Int        = 27017
    @NotEmpty @JsonProperty("database") var database: String = "notifications_db"
    @NotEmpty @JsonProperty("username") var username: String = "root"
    @NotEmpty @JsonProperty("password") var password: String = "rootpassword"
}

class KafkaConfig {
    @NotEmpty @JsonProperty("bootstrapServers")    var bootstrapServers: String    = "localhost:9092"
    @NotEmpty @JsonProperty("topicAccountEvents")  var topicAccountEvents: String  = "account.events"
    @NotEmpty @JsonProperty("topicTransactionEvents") var topicTransactionEvents: String = "transaction.events"
    @NotEmpty @JsonProperty("consumerGroupId")     var consumerGroupId: String     = "notification-service-consumer"
    @NotEmpty @JsonProperty("streamsAppId")        var streamsAppId: String        = "notification-streams-app"
}
