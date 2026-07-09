package com.learn.finance.notification.health

import com.codahale.metrics.health.HealthCheck
import com.mongodb.client.MongoClient

class MongoHealthCheck(private val mongoClient: MongoClient) : HealthCheck() {
    override fun check(): Result = try {
        mongoClient.getDatabase("admin").runCommand(org.bson.Document("ping", 1))
        Result.healthy("MongoDB is reachable")
    } catch (ex: Exception) {
        Result.unhealthy("MongoDB is unreachable: ${ex.message}")
    }
}
