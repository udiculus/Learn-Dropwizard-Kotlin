package com.learn.finance.transaction.service

import com.learn.finance.transaction.model.AccountSnapshot
import com.learn.finance.transaction.model.Transaction
import com.learn.finance.transaction.model.TransactionType
import com.learn.finance.transaction.model.ValidAmount
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.util.Comparator

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * TransactionValidator — Business rule validation
 *
 * KOTLIN CONCEPTS DEMONSTRATED:
 *  • Comparator — comparing BigDecimal amounts
 *  • apply vs with scope functions
 *  • Custom annotation usage (@ValidAmount)
 *  • Object declaration (singleton)
 *  • Equality evaluation (==, ===, equals)
 * ─────────────────────────────────────────────────────────────────────────────
 */
class TransactionValidator {

    private val logger = LoggerFactory.getLogger(TransactionValidator::class.java)

    /**
     * KOTLIN CONCEPT: Comparator — orders amounts from smallest to largest.
     * Used to check if an amount falls within a range.
     */
    private val amountComparator: Comparator<BigDecimal> = Comparator.naturalOrder()

    companion object {
        val MIN_AMOUNT: BigDecimal = BigDecimal("0.01")
        val MAX_AMOUNT: BigDecimal = BigDecimal("1000000.00")
    }

    /**
     * KOTLIN CONCEPT: @ValidAmount annotation usage.
     * Validates the core rules for a transaction.
     */
    @ValidAmount(min = 0.01, max = 1_000_000.00)
    fun validate(
        type: TransactionType,
        amount: BigDecimal,
        sourceAccount: AccountSnapshot,
        targetAccount: AccountSnapshot?
    ): ValidationResult {

        val errors = mutableListOf<String>()

        // ── Amount range check using Comparator ──────────────────────────────
        if (amountComparator.compare(amount, MIN_AMOUNT) < 0) {
            errors += "Amount $amount is below minimum $MIN_AMOUNT"
        }
        if (amountComparator.compare(amount, MAX_AMOUNT) > 0) {
            errors += "Amount $amount exceeds maximum $MAX_AMOUNT"
        }

        // ── Source account must be active ────────────────────────────────────
        if (!sourceAccount.isActive()) {
            errors += "Source account ${sourceAccount.accountNumber} is not active (status=${sourceAccount.status})"
        }

        // ── Withdrawal / Transfer: check sufficient funds ────────────────────
        if (type == TransactionType.WITHDRAWAL || type == TransactionType.TRANSFER) {
            if (amountComparator.compare(sourceAccount.balance, amount) < 0) {
                errors += "Insufficient funds: balance=${sourceAccount.balance}, required=$amount"
            }
        }

        // ── Transfer: must have a valid target account ───────────────────────
        if (type == TransactionType.TRANSFER) {
            when {
                targetAccount == null ->
                    errors += "Transfer requires a target account"
                !targetAccount.isActive() ->
                    errors += "Target account ${targetAccount.accountNumber} is not active"
                // KOTLIN CONCEPT: structural equality (==) vs referential equality (===)
                sourceAccount.accountId == targetAccount.accountId ->
                    errors += "Source and target accounts must be different"
                // Currency match check
                sourceAccount.currency != targetAccount.currency ->
                    errors += "Currency mismatch: ${sourceAccount.currency} vs ${targetAccount.currency}"
            }
        }

        return if (errors.isEmpty()) ValidationResult.Valid
        else ValidationResult.Invalid(errors)
    }
}

/**
 * KOTLIN CONCEPT: sealed class for validation results.
 * Exhaustive — compiler ensures all cases are handled in `when`.
 */
sealed class ValidationResult {
    object Valid : ValidationResult()
    data class Invalid(val errors: List<String>) : ValidationResult()

    fun isValid(): Boolean = this is Valid

    fun getErrorsOrEmpty(): List<String> = when (this) {
        is Valid   -> emptyList()
        is Invalid -> errors
    }
}

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * AccountCache — In-memory account data (updated by Kafka consumer)
 *
 * KOTLIN CONCEPTS DEMONSTRATED:
 *  • apply scope function — configures the object and returns it
 *  • with scope function — operates on a context object
 *  • Thread-safe map using ConcurrentHashMap
 *  • Elvis operator ?: for missing cache entries
 * ─────────────────────────────────────────────────────────────────────────────
 */
class AccountCache {

    private val logger = LoggerFactory.getLogger(AccountCache::class.java)

    // Thread-safe map: accountId → AccountSnapshot
    private val cache = java.util.concurrent.ConcurrentHashMap<Long, AccountSnapshot>()

    /**
     * KOTLIN CONCEPT: apply scope function.
     * `apply` runs a block on the object and returns the object itself.
     * Use when you want to configure/modify an object and return it.
     */
    fun put(snapshot: AccountSnapshot): AccountSnapshot = snapshot.apply {
        cache[accountId] = this
        logger.debug("Cache updated for account ${accountNumber}: balance=${balance}, status=${status}")
    }

    /**
     * KOTLIN CONCEPT: with scope function.
     * `with` takes an object as a receiver and returns the result of the last expression.
     * Use when you want to call multiple methods on an object and get a result.
     */
    fun getStats(): Map<String, Any> = with(cache) {
        mapOf(
            "totalAccounts" to size,
            "activeAccounts" to values.count { it.isActive() },
            "cachedIds" to keys.toList()
        )
    }

    fun get(accountId: Long): AccountSnapshot? = cache[accountId]

    fun getOrThrow(accountId: Long): AccountSnapshot =
        cache[accountId] ?: throw jakarta.ws.rs.NotFoundException("Account $accountId not found in cache")

    fun remove(accountId: Long) {
        cache.remove(accountId)
    }

    fun size(): Int = cache.size
}
