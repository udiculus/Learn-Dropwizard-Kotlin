package com.learn.finance.notification.model

import org.bson.types.ObjectId
import java.time.Instant

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * Notification Domain Models — MongoDB Documents
 *
 * KOTLIN CONCEPTS DEMONSTRATED:
 *  • data class for MongoDB document mapping
 *  • enum class for status
 *  • nullable types for optional fields
 *  • Default values for optional properties
 * ─────────────────────────────────────────────────────────────────────────────
 */

enum class NotificationStatus { UNREAD, READ, ARCHIVED }

data class Notification(
    val id: ObjectId = ObjectId(),             // MongoDB _id — auto-generated
    val eventType: String,
    val accountId: Long,
    val message: String,
    val status: NotificationStatus = NotificationStatus.UNREAD,
    val metadata: Map<String, String> = emptyMap(),
    val createdAt: Instant = Instant.now()
)

data class AuditLog(
    val id: ObjectId = ObjectId(),
    val service: String,
    val action: String,
    val entityType: String,
    val entityId: String,
    val payload: Map<String, Any?> = emptyMap(),
    val timestamp: Instant = Instant.now()
)

// ── Extension functions on domain models ─────────────────────────────────────

/**
 * KOTLIN CONCEPT: Extension function — adds a method to Notification
 * without modifying or subclassing the class.
 */
fun Notification.toSummary(): String =
    "[${status}] ${eventType} for account #${accountId}: ${message.take(50)}"

/**
 * Extension function on List<Notification>
 */
fun List<Notification>.countByStatus(status: NotificationStatus): Int =
    count { it.status == status }

fun List<Notification>.markAllRead(): List<Notification> =
    map { it.copy(status = NotificationStatus.READ) }
