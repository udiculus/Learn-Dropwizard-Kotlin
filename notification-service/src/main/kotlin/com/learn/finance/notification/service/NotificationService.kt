package com.learn.finance.notification.service

import com.learn.finance.notification.db.AuditLogRepository
import com.learn.finance.notification.db.NotificationRepository
import com.learn.finance.notification.model.AuditLog
import com.learn.finance.notification.model.Notification
import com.learn.finance.notification.model.NotificationStatus
import com.learn.finance.notification.model.countByStatus
import com.learn.finance.notification.model.toSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * NotificationService — Business Logic with Kotlin Coroutines
 *
 * KOTLIN COROUTINES CONCEPTS DEMONSTRATED:
 *  • CoroutineScope — defines the lifetime of launched coroutines
 *  • SupervisorJob  — if one child coroutine fails, others keep running
 *  • Dispatchers.IO — coroutine dispatcher for blocking I/O (MongoDB calls)
 *  • launch {}      — fire-and-forget coroutine (no return value needed)
 *  • suspend fun    — function that can be suspended and resumed
 *  • runBlocking {} — bridges blocking code and coroutines (used in tests)
 *
 * KOTLIN CONCEPTS:
 *  • Extension functions called: toSummary(), countByStatus()
 *  • let {} scope function for null-safe chaining
 * ─────────────────────────────────────────────────────────────────────────────
 */
class NotificationService(
    private val notificationRepo: NotificationRepository,
    private val auditLogRepo: AuditLogRepository
) {
    private val logger = LoggerFactory.getLogger(NotificationService::class.java)

    /**
     * KOTLIN COROUTINES: CoroutineScope with SupervisorJob.
     * SupervisorJob means child failures don't cancel the scope.
     * Dispatchers.IO is optimized for blocking I/O operations.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * KOTLIN COROUTINES: `suspend fun` — can be suspended during execution.
     * A suspend function must be called from a coroutine or another suspend function.
     *
     * This processes a Kafka event payload and:
     *  1. Saves a notification to MongoDB
     *  2. Launches a separate coroutine (fire-and-forget) to save the audit log
     */
    suspend fun processEvent(
        eventType: String,
        accountId: Long,
        payload: Map<String, Any?>
    ): Notification {
        val message = buildMessage(eventType, payload)

        val notification = Notification(
            eventType = eventType,
            accountId = accountId,
            message   = message,
            metadata  = payload.mapValues { it.value?.toString() ?: "" }
        )

        // Save notification (blocking I/O — but we're on Dispatchers.IO)
        val saved = notificationRepo.insert(notification)

        // KOTLIN COROUTINES: launch {} fires a non-blocking background coroutine
        // Audit log saving doesn't block the response
        scope.launch {
            val auditLog = AuditLog(
                service    = "notification-service",
                action     = "PROCESS_EVENT",
                entityType = "notification",
                entityId   = saved.id.toString(),
                payload    = payload,
                timestamp  = Instant.now()
            )
            auditLogRepo.insert(auditLog)
            logger.debug("Audit log saved for event: $eventType")
        }

        // KOTLIN CONCEPT: calling extension function toSummary() on the saved notification
        logger.info("Processed event: ${saved.toSummary()}")
        return saved
    }

    /**
     * KOTLIN COROUTINES: Another suspend function — marks a notification as read.
     * Demonstrates suspend → suspend call chain.
     */
    suspend fun markAsRead(id: String): Boolean {
        val objectId = runCatching { ObjectId(id) }
            .getOrElse { throw IllegalArgumentException("Invalid notification ID: $id") }
        return notificationRepo.markAsRead(objectId)
    }

    suspend fun markAllReadForAccount(accountId: Long): Long =
        notificationRepo.markAllReadForAccount(accountId)

    fun getByAccountId(accountId: Long, limit: Int = 20, offset: Int = 0): List<Notification> =
        notificationRepo.findByAccountId(accountId, limit, offset)

    fun getById(id: String): Notification? {
        val objectId = runCatching { ObjectId(id) }.getOrElse { return null }
        return notificationRepo.findById(objectId)
    }

    fun deleteById(id: String): Boolean {
        val objectId = runCatching { ObjectId(id) }.getOrElse { return false }
        return notificationRepo.deleteById(objectId)
    }

    fun getAuditLogs(limit: Int = 50, offset: Int = 0): List<AuditLog> =
        auditLogRepo.findAll(limit, offset)

    /**
     * KOTLIN CONCEPT: calling List extension function countByStatus()
     */
    fun getUnreadCount(accountId: Long): Int {
        val notifications = notificationRepo.findByAccountId(accountId, limit = 1000)
        return notifications.countByStatus(NotificationStatus.UNREAD)
    }

    private fun buildMessage(eventType: String, payload: Map<String, Any?>): String = when (eventType) {
        "ACCOUNT_CREATED"       -> "Your account has been created successfully."
        "ACCOUNT_STATUS_CHANGED"-> "Your account status changed to: ${payload["newStatus"]}"
        "ACCOUNT_CLOSED"        -> "Your account has been closed."
        "TRANSACTION_COMPLETED" -> {
            val type   = payload["type"] as? String ?: "transaction"
            val amount = payload["amount"] as? String ?: "unknown"
            "A $type of $amount has been completed on your account."
        }
        else -> "You have a new notification: $eventType"
    }

    fun cancelScope() {
        // Called when service is shutting down
        (scope.coroutineContext[kotlinx.coroutines.Job])?.cancel()
    }
}
