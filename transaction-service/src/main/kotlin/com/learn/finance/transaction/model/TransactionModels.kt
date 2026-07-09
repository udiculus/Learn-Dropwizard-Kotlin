package com.learn.finance.transaction.model

import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * Transaction Domain Models
 *
 * KOTLIN CONCEPTS DEMONSTRATED:
 *  • enum class with properties
 *  • data class with default values
 *  • Pair and Triple usage
 *  • Comparator implementation
 *  • Annotations (@Target, @Retention)
 *  • apply vs with scope functions
 *  • equality evaluation
 * ─────────────────────────────────────────────────────────────────────────────
 */

// ── Enum: Transaction Type ────────────────────────────────────────────────────
enum class TransactionType(val requiresTarget: Boolean, val displayName: String) {
    DEPOSIT(requiresTarget = false, displayName = "Deposit"),
    WITHDRAWAL(requiresTarget = false, displayName = "Withdrawal"),
    TRANSFER(requiresTarget = true,  displayName = "Transfer");

    fun isDebit(): Boolean = this == WITHDRAWAL || this == TRANSFER
    fun isCredit(): Boolean = this == DEPOSIT
}

// ── Enum: Transaction Status ──────────────────────────────────────────────────
enum class TransactionStatus {
    PENDING, COMPLETED, FAILED, REVERSED
}

// ── Data class: Transaction ───────────────────────────────────────────────────
data class Transaction(
    val id: Long? = null,
    val transactionRef: String = UUID.randomUUID().toString(),
    val sourceAccountId: Long,
    val targetAccountId: Long? = null,
    val type: TransactionType,
    val amount: BigDecimal,
    val currency: String = "USD",
    val status: TransactionStatus = TransactionStatus.PENDING,
    val description: String? = null,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null
) {
    /**
     * KOTLIN CONCEPT: Pair — a simple generic pair of two values.
     * Returns (sourceAccountId, targetAccountId?) as a Pair for routing.
     */
    fun accountPair(): Pair<Long, Long?> = Pair(sourceAccountId, targetAccountId)

    /**
     * KOTLIN CONCEPT: Triple — a simple generic triple of three values.
     * Returns (transactionRef, type, amount) for logging summaries.
     */
    fun summary(): Triple<String, TransactionType, BigDecimal> =
        Triple(transactionRef, type, amount)
}

/**
 * Lightweight account snapshot held in the local AccountCache.
 * Populated by consuming `account.events` from Kafka.
 */
data class AccountSnapshot(
    val accountId: Long,
    val accountNumber: String,
    val customerId: Long,
    val balance: BigDecimal,
    val currency: String,
    val status: String
) {
    fun isActive(): Boolean = status == "ACTIVE"
}

// ── Custom Annotation: @ValidAmount ──────────────────────────────────────────
/**
 * KOTLIN CONCEPT: Custom annotation declaration.
 *  • @Target — restricts where the annotation can be used (functions)
 *  • @Retention — RUNTIME means it's accessible via reflection at runtime
 *  • @MustBeDocumented — included in generated docs
 *
 * This is a marker annotation used by TransactionValidator.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class ValidAmount(
    val min: Double = 0.01,
    val max: Double = 1_000_000.00,
    val message: String = "Amount must be between min and max"
)
