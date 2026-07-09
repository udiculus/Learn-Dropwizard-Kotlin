package com.learn.finance.notification.db

import com.learn.finance.notification.model.AuditLog
import com.learn.finance.notification.model.Notification
import com.learn.finance.notification.model.NotificationStatus
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Sorts
import com.mongodb.client.model.Updates
import org.bson.Document
import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * NotificationRepository — MongoDB CRUD Operations
 *
 * MONGODB CONCEPTS DEMONSTRATED:
 *  • Insert documents        — insertOne()
 *  • Query documents         — find() with Filters
 *  • Partial updates         — updateOne() with $set
 *  • Multi update            — updateMany() with $set
 *  • Upsert                  — upsertOne() with upsert=true option
 *  • Delete documents        — deleteOne(), deleteMany()
 *  • Document limits         — limit() and skip() for pagination
 *  • Removing documents      — deleteOne by ID
 *
 * KOTLIN CONCEPTS:
 *  • Extension function to convert Document → Notification
 *  • let scope function for null-safe transformation
 * ─────────────────────────────────────────────────────────────────────────────
 */
class NotificationRepository(private val database: MongoDatabase) {

    private val logger = LoggerFactory.getLogger(NotificationRepository::class.java)
    private val collection = database.getCollection("notifications")

    // ── INSERT ─────────────────────────────────────────────────────────────────

    /**
     * MONGODB CONCEPT: Insert a single document.
     * insertOne() adds one document to the collection.
     */
    fun insert(notification: Notification): Notification {
        val doc = notification.toDocument()
        collection.insertOne(doc)
        logger.debug("Inserted notification: ${notification.id}")
        return notification
    }

    // ── QUERY ──────────────────────────────────────────────────────────────────

    /**
     * MONGODB CONCEPT: Find one document by ID using an equality filter.
     * Filters.eq("_id", id) creates a { _id: ObjectId("...") } query.
     * KOTLIN CONCEPT: let {} transforms nullable result.
     */
    fun findById(id: ObjectId): Notification? =
        collection.find(Filters.eq("_id", id)).firstOrNull()?.toNotification()

    /**
     * MONGODB CONCEPT: Find documents with multiple filters.
     * Filters.and() combines multiple conditions (like SQL AND).
     * Sorts.descending("createdAt") orders results newest first.
     * limit() and skip() implement pagination — document limits.
     */
    fun findByAccountId(
        accountId: Long,
        limit: Int  = 20,
        skip: Int   = 0
    ): List<Notification> =
        collection
            .find(Filters.eq("accountId", accountId))
            .sort(Sorts.descending("createdAt"))
            .skip(skip)
            .limit(limit)              // MONGODB CONCEPT: document limit
            .map { it.toNotification() }
            .toList()

    fun findByStatus(status: NotificationStatus): List<Notification> =
        collection
            .find(Filters.eq("status", status.name))
            .sort(Sorts.descending("createdAt"))
            .map { it.toNotification() }
            .toList()

    // ── UPDATE — PARTIAL UPDATE ────────────────────────────────────────────────

    /**
     * MONGODB CONCEPT: Partial update using $set operator.
     * updateOne() updates only specified fields — does NOT replace the whole document.
     * This is a "partial update" (like SQL UPDATE SET field=value WHERE id=...).
     */
    fun markAsRead(id: ObjectId): Boolean {
        val result = collection.updateOne(
            Filters.eq("_id", id),
            Updates.combine(
                Updates.set("status", NotificationStatus.READ.name),
                Updates.set("updatedAt", Instant.now().toString())
            )
        )
        return result.modifiedCount > 0
    }

    // ── UPDATE — MULTI UPDATE ──────────────────────────────────────────────────

    /**
     * MONGODB CONCEPT: Multi update using updateMany().
     * Updates ALL documents matching the filter in one operation.
     * Equivalent to SQL: UPDATE notifications SET status='READ' WHERE accountId=? AND status='UNREAD'
     */
    fun markAllReadForAccount(accountId: Long): Long {
        val result = collection.updateMany(
            Filters.and(
                Filters.eq("accountId", accountId),
                Filters.eq("status", NotificationStatus.UNREAD.name)
            ),
            Updates.set("status", NotificationStatus.READ.name)
        )
        logger.debug("Marked ${result.modifiedCount} notifications as read for account $accountId")
        return result.modifiedCount
    }

    // ── UPSERT ────────────────────────────────────────────────────────────────

    /**
     * MONGODB CONCEPT: Upsert — insert if not exists, update if exists.
     * Uses ReplaceOptions with upsert=true.
     * Equivalent to SQL: INSERT INTO ... ON DUPLICATE KEY UPDATE ...
     */
    fun upsertByEventTypeAndAccount(notification: Notification): Notification {
        val filter = Filters.and(
            Filters.eq("eventType",  notification.eventType),
            Filters.eq("accountId",  notification.accountId)
        )
        val options = com.mongodb.client.model.ReplaceOptions().upsert(true)
        collection.replaceOne(filter, notification.toDocument(), options)
        logger.debug("Upserted notification: eventType=${notification.eventType}, accountId=${notification.accountId}")
        return notification
    }

    // ── DELETE ────────────────────────────────────────────────────────────────

    /**
     * MONGODB CONCEPT: Delete a single document by ID.
     * deleteOne() removes the first matching document.
     */
    fun deleteById(id: ObjectId): Boolean {
        val result = collection.deleteOne(Filters.eq("_id", id))
        return result.deletedCount > 0
    }

    /**
     * MONGODB CONCEPT: Delete multiple documents (removeDocuments).
     * deleteMany() removes ALL matching documents.
     */
    fun deleteAllForAccount(accountId: Long): Long {
        val result = collection.deleteMany(Filters.eq("accountId", accountId))
        logger.info("Deleted ${result.deletedCount} notifications for account $accountId")
        return result.deletedCount
    }

    fun count(): Long = collection.countDocuments()

    // ── Document <→ Kotlin converters ─────────────────────────────────────────

    private fun Notification.toDocument(): Document = Document().apply {
        put("_id",       id)
        put("eventType", eventType)
        put("accountId", accountId)
        put("message",   message)
        put("status",    status.name)
        put("metadata",  metadata)
        put("createdAt", java.util.Date.from(createdAt))
    }

    /**
     * KOTLIN CONCEPT: Extension function on Document to convert to domain object.
     * Defined as a private extension function inside the repository.
     */
    private fun Document.toNotification(): Notification = Notification(
        id        = getObjectId("_id"),
        eventType = getString("eventType"),
        accountId = getLong("accountId"),
        message   = getString("message"),
        status    = NotificationStatus.valueOf(getString("status") ?: "UNREAD"),
        metadata  = (get("metadata") as? Map<*, *>)
                        ?.filterKeys { it is String }
                        ?.mapKeys { it.key as String }
                        ?.mapValues { it.value?.toString() ?: "" }
                        ?: emptyMap(),
        createdAt = getDate("createdAt")?.toInstant() ?: Instant.now()
    )
}

// ─────────────────────────────────────────────────────────────────────────────

/**
 * AuditLogRepository — MongoDB CRUD for audit trail documents
 */
class AuditLogRepository(private val database: MongoDatabase) {

    private val collection = database.getCollection("audit_logs")

    fun insert(log: AuditLog) {
        val doc = Document().apply {
            put("_id",        log.id)
            put("service",    log.service)
            put("action",     log.action)
            put("entityType", log.entityType)
            put("entityId",   log.entityId)
            put("payload",    Document(log.payload.mapValues { it.value?.toString() }))
            put("timestamp",  java.util.Date.from(log.timestamp))
        }
        collection.insertOne(doc)
    }

    /**
     * MONGODB CONCEPT: Query with multiple filters and sorting.
     */
    fun findByServiceAndAction(service: String, action: String, limit: Int = 50): List<AuditLog> =
        collection
            .find(Filters.and(Filters.eq("service", service), Filters.eq("action", action)))
            .sort(Sorts.descending("timestamp"))
            .limit(limit)
            .map { doc ->
                AuditLog(
                    id         = doc.getObjectId("_id"),
                    service    = doc.getString("service"),
                    action     = doc.getString("action"),
                    entityType = doc.getString("entityType"),
                    entityId   = doc.getString("entityId"),
                    timestamp  = doc.getDate("timestamp")?.toInstant() ?: Instant.now()
                )
            }.toList()

    fun findAll(limit: Int = 50, skip: Int = 0): List<AuditLog> =
        collection
            .find()
            .sort(Sorts.descending("timestamp"))
            .skip(skip)
            .limit(limit)
            .map { doc ->
                AuditLog(
                    id         = doc.getObjectId("_id"),
                    service    = doc.getString("service"),
                    action     = doc.getString("action"),
                    entityType = doc.getString("entityType"),
                    entityId   = doc.getString("entityId"),
                    timestamp  = doc.getDate("timestamp")?.toInstant() ?: Instant.now()
                )
            }.toList()

    fun count(): Long = collection.countDocuments()
}
