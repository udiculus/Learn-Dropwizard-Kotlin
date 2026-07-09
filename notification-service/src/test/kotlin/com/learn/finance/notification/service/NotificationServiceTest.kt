package com.learn.finance.notification.service

import com.learn.finance.notification.db.AuditLogRepository
import com.learn.finance.notification.db.NotificationRepository
import com.learn.finance.notification.model.Notification
import com.learn.finance.notification.model.NotificationStatus
import com.learn.finance.notification.model.countByStatus
import com.learn.finance.notification.model.markAllRead
import com.learn.finance.notification.model.toSummary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.bson.types.ObjectId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * NotificationServiceTest — Testing Kotlin-Specific Patterns
 *
 * KOTLIN-SPECIFIC TESTING PATTERNS DEMONSTRATED:
 *  • Testing suspend functions — using runTest {} (kotlinx-coroutines-test)
 *  • Mocking suspend functions — mockito-kotlin supports this natively
 *  • Testing extension functions — toSummary(), countByStatus(), markAllRead()
 *  • Testing data class — Notification copy(), equals(), toString()
 *  • Testing companion object — NotificationStatus enum values
 *  • Verifying interactions — verify() with argument matchers on suspend calls
 *
 * JUNIT 5 CONCEPTS:
 *  • @Nested for logical grouping
 *  • @ExtendWith(MockitoExtension) for annotation-based mocks
 *  • @Mock / @InjectMocks
 * ─────────────────────────────────────────────────────────────────────────────
 */
@Tag("unit")
@DisplayName("NotificationService Tests")
@ExtendWith(MockitoExtension::class)
@OptIn(ExperimentalCoroutinesApi::class)
class NotificationServiceTest {

    @Mock private lateinit var notificationRepo: NotificationRepository
    @Mock private lateinit var auditLogRepo: AuditLogRepository

    @InjectMocks private lateinit var notificationService: NotificationService

    // ── Fixtures ─────────────────────────────────────────────────────────────

    private fun aNotification(
        eventType: String = "TRANSACTION_COMPLETED",
        accountId: Long   = 42L,
        status: NotificationStatus = NotificationStatus.UNREAD
    ) = Notification(
        id        = ObjectId(),
        eventType = eventType,
        accountId = accountId,
        message   = "A deposit of 500.00 has been completed.",
        status    = status
    )

    // ════════════════════════════════════════════════════════════════════════
    // Testing suspend functions with runTest {}
    // ════════════════════════════════════════════════════════════════════════

    /**
     * KOTLIN COROUTINES CONCEPT: `runTest {}` is the correct way to test suspend functions.
     * It runs coroutines synchronously in tests — no real delays, no flakiness.
     * Provided by the `kotlinx-coroutines-test` library.
     */
    @Nested
    @DisplayName("processEvent() — suspend function tests")
    inner class ProcessEventTests {

        @Test
        @DisplayName("should save notification and return it")
        fun `processEvent - happy path`() = runTest {
            // ARRANGE
            val savedNotification = aNotification()
            whenever(notificationRepo.insert(any())).thenReturn(savedNotification)

            // ACT — calling a suspend function inside runTest is allowed
            val result = notificationService.processEvent(
                eventType = "TRANSACTION_COMPLETED",
                accountId = 42L,
                payload   = mapOf("type" to "DEPOSIT", "amount" to "500.00")
            )

            // ASSERT
            assertNotNull(result)
            assertEquals(42L, result.accountId)
            assertEquals("TRANSACTION_COMPLETED", result.eventType)

            // MOCKITO CONCEPT: verify the repository was called
            verify(notificationRepo).insert(any())
        }

        @Test
        @DisplayName("should build correct message for ACCOUNT_CREATED event")
        fun `processEvent - account created message`() = runTest {
            val captor = argumentCaptor<Notification>()
            whenever(notificationRepo.insert(captor.capture())).thenAnswer { captor.firstValue }

            notificationService.processEvent(
                eventType = "ACCOUNT_CREATED",
                accountId = 1L,
                payload   = emptyMap()
            )

            // ASSERT: verify the notification message was set correctly
            assertEquals(
                "Your account has been created successfully.",
                captor.firstValue.message
            )
        }

        @Test
        @DisplayName("should build correct message for STATUS_CHANGED event")
        fun `processEvent - status changed message`() = runTest {
            val captor = argumentCaptor<Notification>()
            whenever(notificationRepo.insert(captor.capture())).thenAnswer { captor.firstValue }

            notificationService.processEvent(
                eventType = "ACCOUNT_STATUS_CHANGED",
                accountId = 1L,
                payload   = mapOf("newStatus" to "FROZEN")
            )

            assertTrue("FROZEN" in captor.firstValue.message)
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Mocking suspend functions
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("markAsRead() — suspend function with mock interactions")
    inner class MarkAsReadTests {

        @Test
        @DisplayName("should return true when notification is marked as read")
        fun `markAsRead - success`() = runTest {
            val id = ObjectId()
            // MOCKITO CONCEPT: mocking a non-suspend method called by a suspend chain
            whenever(notificationRepo.markAsRead(id)).thenReturn(true)

            val result = notificationService.markAsRead(id.toString())

            assertTrue(result)
            verify(notificationRepo).markAsRead(id)
        }

        @Test
        @DisplayName("should return false when notification not found")
        fun `markAsRead - not found`() = runTest {
            val id = ObjectId()
            whenever(notificationRepo.markAsRead(id)).thenReturn(false)

            val result = notificationService.markAsRead(id.toString())

            assertFalse(result)
        }

        @Test
        @DisplayName("should throw IllegalArgumentException for invalid ID format")
        fun `markAsRead - invalid ID`() = runTest {
            try {
                notificationService.markAsRead("not-a-valid-object-id")
                assert(false) { "Should have thrown" }
            } catch (ex: IllegalArgumentException) {
                assertTrue(ex.message?.contains("Invalid notification ID") == true)
            }

            // Verify repository was never called with bad input
            verify(notificationRepo, never()).markAsRead(any())
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Testing extension functions
    // ════════════════════════════════════════════════════════════════════════

    /**
     * KOTLIN CONCEPT: Extension functions are tested like regular functions.
     * The receiver type is just the first argument implicitly.
     */
    @Nested
    @DisplayName("Extension functions")
    inner class ExtensionFunctionTests {

        @Test
        @DisplayName("toSummary() should format notification as string")
        fun `toSummary - formats correctly`() {
            val notification = aNotification(
                eventType = "TRANSACTION_COMPLETED",
                accountId = 99L,
                status    = NotificationStatus.UNREAD
            )

            // KOTLIN CONCEPT: calling extension function on an instance
            val summary = notification.toSummary()

            assertTrue(summary.contains("UNREAD"),                  "Should contain status")
            assertTrue(summary.contains("TRANSACTION_COMPLETED"),   "Should contain event type")
            assertTrue(summary.contains("99"),                      "Should contain account ID")
        }

        @Test
        @DisplayName("countByStatus() should count notifications with matching status")
        fun `countByStatus - counts correctly`() {
            val notifications = listOf(
                aNotification(status = NotificationStatus.UNREAD),
                aNotification(status = NotificationStatus.UNREAD),
                aNotification(status = NotificationStatus.READ),
                aNotification(status = NotificationStatus.ARCHIVED)
            )

            // KOTLIN CONCEPT: calling List extension function
            assertEquals(2, notifications.countByStatus(NotificationStatus.UNREAD))
            assertEquals(1, notifications.countByStatus(NotificationStatus.READ))
            assertEquals(1, notifications.countByStatus(NotificationStatus.ARCHIVED))
        }

        @Test
        @DisplayName("markAllRead() should return a new list with all notifications as READ")
        fun `markAllRead - transforms list`() {
            val notifications = listOf(
                aNotification(status = NotificationStatus.UNREAD),
                aNotification(status = NotificationStatus.UNREAD),
                aNotification(status = NotificationStatus.ARCHIVED)
            )

            // KOTLIN CONCEPT: extension function that returns a new transformed list
            val result = notifications.markAllRead()

            // Original list is unchanged (immutable data class)
            assertEquals(NotificationStatus.UNREAD, notifications[0].status)
            assertEquals(NotificationStatus.UNREAD, notifications[1].status)

            // All in result are READ
            assertTrue(result.all { it.status == NotificationStatus.READ })
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Testing data class: Notification
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Notification data class patterns")
    inner class NotificationDataClassTests {

        @Test
        @DisplayName("copy() should preserve all fields except the one changed")
        fun `copy() - partial modification`() {
            val original = aNotification(status = NotificationStatus.UNREAD)
            val updated  = original.copy(status = NotificationStatus.READ)

            assertEquals(original.id,        updated.id)
            assertEquals(original.eventType, updated.eventType)
            assertEquals(original.accountId, updated.accountId)
            assertEquals(original.message,   updated.message)
            assertEquals(NotificationStatus.READ, updated.status)  // only this changed
        }

        @Test
        @DisplayName("structural equality (==) — same field values means equal")
        fun `equals() - structural equality`() {
            val id = ObjectId()
            val n1 = Notification(id = id, eventType = "A", accountId = 1L, message = "M")
            val n2 = Notification(id = id, eventType = "A", accountId = 1L, message = "M")

            // KOTLIN CONCEPT: == invokes equals() — structural comparison
            assertEquals(n1, n2)
            // KOTLIN CONCEPT: === is referential — these are different objects
            assertFalse(n1 === n2)
        }

        @Test
        @DisplayName("toString() should include all property names and values")
        fun `toString() - auto-generated format`() {
            val notification = aNotification()
            val str = notification.toString()

            // KOTLIN CONCEPT: data class toString() includes property names
            assertTrue(str.contains("eventType"))
            assertTrue(str.contains("accountId"))
            assertTrue(str.contains("message"))
            assertTrue(str.contains("status"))
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Verifying interactions — advanced Mockito patterns
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Verifying interactions")
    inner class VerifyInteractionsTests {

        @Test
        @DisplayName("getUnreadCount should call findByAccountId and filter UNREAD")
        fun `getUnreadCount - verifies repo interaction`() {
            val notifications = listOf(
                aNotification(status = NotificationStatus.UNREAD),
                aNotification(status = NotificationStatus.READ),
                aNotification(status = NotificationStatus.UNREAD)
            )
            whenever(notificationRepo.findByAccountId(eq(5L), any(), any()))
                .thenReturn(notifications)

            val count = notificationService.getUnreadCount(5L)

            assertEquals(2, count)
            // MOCKITO CONCEPT: verify was called with specific argument
            verify(notificationRepo).findByAccountId(eq(5L), any(), any())
        }
    }
}

// Helper: direct eq() import for Long parameters
private fun eq(value: Long) = org.mockito.kotlin.eq(value)
