package com.learn.finance.account.model

import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * Domain Models — Account Service
 *
 * KOTLIN CONCEPTS DEMONSTRATED:
 *  • data class         — auto-generates equals, hashCode, toString, copy
 *  • sealed class       — exhaustive type hierarchy (AccountStatus, AccountType)
 *  • enum class         — fixed set of named constants
 *  • companion object   — factory methods
 *  • nullable types     — phone: String?
 *  • default parameters — balance defaults to BigDecimal.ZERO
 * ─────────────────────────────────────────────────────────────────────────────
 */

// ── Enum: Account Type ────────────────────────────────────────────────────────
/**
 * KOTLIN CONCEPT: enum class
 * Unlike Java enums, Kotlin enum classes can have properties and member functions.
 */
enum class AccountType(val displayName: String) {
    SAVINGS("Savings Account"),
    CHECKING("Checking Account"),
    INVESTMENT("Investment Account");

    /**
     * KOTLIN CONCEPT: member function on enum
     */
    fun isEligibleForOverdraft(): Boolean = this == CHECKING
}

// ── Sealed class: Account Status ──────────────────────────────────────────────
/**
 * KOTLIN CONCEPT: sealed class
 * A sealed class restricts the type hierarchy — all subclasses must be in the same file.
 * This makes `when` expressions exhaustive (compiler error if a case is missed).
 *
 * Contrast with enum: sealed classes can hold different data in each variant.
 */
sealed class AccountStatus {
    object Active    : AccountStatus()
    object Inactive  : AccountStatus()
    object Closed    : AccountStatus()
    object Frozen    : AccountStatus()

    /**
     * KOTLIN CONCEPT: member function on sealed class
     * Returns whether operations are allowed on this status.
     */
    fun canTransact(): Boolean = when (this) {
        is Active   -> true
        is Inactive -> false
        is Closed   -> false
        is Frozen   -> false
    }

    /**
     * KOTLIN CONCEPT: toString override, string template
     */
    override fun toString(): String = when (this) {
        is Active   -> "ACTIVE"
        is Inactive -> "INACTIVE"
        is Closed   -> "CLOSED"
        is Frozen   -> "FROZEN"
    }

    companion object {
        /**
         * KOTLIN CONCEPT: companion object factory method
         * Parses a String into a sealed class instance.
         */
        fun fromString(value: String): AccountStatus = when (value.uppercase()) {
            "ACTIVE"   -> Active
            "INACTIVE" -> Inactive
            "CLOSED"   -> Closed
            "FROZEN"   -> Frozen
            else       -> throw IllegalArgumentException("Unknown account status: $value")
        }
    }
}

// ── Data class: Customer ──────────────────────────────────────────────────────
/**
 * KOTLIN CONCEPT: data class
 * Automatically generates:
 *  - equals() / hashCode() based on all properties
 *  - toString() with property names and values
 *  - copy() for creating modified copies
 *  - componentN() functions for destructuring
 *
 * `phone` is nullable (String?) — it's optional.
 */
data class Customer(
    val id: Long? = null,             // null when not yet persisted
    val firstName: String,
    val lastName: String,
    val email: String,
    val phone: String? = null,        // KOTLIN CONCEPT: nullable type
    val status: String = "ACTIVE",
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null
) {
    /**
     * KOTLIN CONCEPT: computed property (getter only, no backing field)
     * Acts like a method but accessed like a property.
     */
    val fullName: String
        get() = "$firstName $lastName"

    /**
     * KOTLIN CONCEPT: extension-like member function
     * In real code this could also be an extension function on Customer.
     */
    fun isActive(): Boolean = status == "ACTIVE"
}

// ── Data class: Account ───────────────────────────────────────────────────────
/**
 * KOTLIN CONCEPT: data class with default values for optional parameters.
 * Default values allow us to create instances without specifying every field
 * (particularly useful for test fixtures).
 */
data class Account(
    val id: Long? = null,
    val customerId: Long,
    val accountNumber: String,
    val accountType: AccountType,
    val balance: BigDecimal = BigDecimal.ZERO,    // KOTLIN CONCEPT: default parameter
    val currency: String = "USD",
    val status: AccountStatus = AccountStatus.Active,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null
) {
    /**
     * KOTLIN CONCEPT: computed property with custom getter
     */
    val isActive: Boolean
        get() = status is AccountStatus.Active

    /**
     * KOTLIN CONCEPT: infix function
     * Allows calling: account hasSufficientFunds amount
     */
    infix fun hasSufficientFunds(amount: BigDecimal): Boolean =
        balance >= amount

    companion object {
        /**
         * KOTLIN CONCEPT: factory method in companion object
         * Generates a unique account number.
         */
        fun generateAccountNumber(customerId: Long, type: AccountType): String {
            val prefix = when (type) {
                AccountType.SAVINGS    -> "SAV"
                AccountType.CHECKING   -> "CHK"
                AccountType.INVESTMENT -> "INV"
            }
            return "$prefix-${customerId.toString().padStart(6, '0')}-${System.currentTimeMillis() % 10000}"
        }
    }
}
