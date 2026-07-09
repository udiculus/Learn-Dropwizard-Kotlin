package com.learn.finance.transaction.service

import com.learn.finance.transaction.model.AccountSnapshot
import com.learn.finance.transaction.model.TransactionType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.InjectMocks
import org.mockito.junit.jupiter.MockitoExtension
import java.math.BigDecimal

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * TransactionValidatorTest — Mockito Intermediate + Kotlin-Specific Patterns
 *
 * MOCKITO INTERMEDIATE CONCEPTS:
 *  • @InjectMocks with no dependencies (validator is self-contained)
 *  • argument matchers — any(), eq()
 *  • Verifying interactions
 *
 * KOTLIN-SPECIFIC TESTING PATTERNS:
 *  • Testing data class structural equality
 *  • Testing companion object (ValidationResult)
 *  • Testing sealed class when expressions
 *  • Testing object declarations (ValidationResult.Valid as object)
 *  • Testing extension functions
 * ─────────────────────────────────────────────────────────────────────────────
 */
@Tag("unit")
@DisplayName("TransactionValidator Tests")
@ExtendWith(MockitoExtension::class)
class TransactionValidatorTest {

    @InjectMocks
    private lateinit var validator: TransactionValidator

    // ── Fixtures ─────────────────────────────────────────────────────────────

    private fun activeAccount(
        id: Long = 1L,
        balance: BigDecimal = BigDecimal("1000.00"),
        currency: String = "USD"
    ) = AccountSnapshot(
        accountId     = id,
        accountNumber = "SAV-00000$id-1234",
        customerId    = 10L,
        balance       = balance,
        currency      = currency,
        status        = "ACTIVE"
    )

    private fun inactiveAccount(id: Long = 2L) =
        activeAccount(id).copy(status = "INACTIVE")

    // ════════════════════════════════════════════════════════════════════════
    // Testing sealed class: ValidationResult
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("ValidationResult sealed class behavior")
    inner class ValidationResultTests {

        @Test
        @DisplayName("Valid object is a singleton — referential equality holds")
        fun `Valid - is a singleton object`() {
            // KOTLIN CONCEPT: object declarations are singletons
            // === is referential equality — both point to the same object
            assertTrue(ValidationResult.Valid === ValidationResult.Valid)
        }

        @Test
        @DisplayName("Valid.isValid() returns true")
        fun `Valid - isValid returns true`() {
            assertTrue(ValidationResult.Valid.isValid())
        }

        @Test
        @DisplayName("Invalid.isValid() returns false")
        fun `Invalid - isValid returns false`() {
            val result = ValidationResult.Invalid(listOf("Error 1"))
            assertFalse(result.isValid())
        }

        @Test
        @DisplayName("Invalid data class structural equality — same errors = equal")
        fun `Invalid - structural equality`() {
            // KOTLIN CONCEPT: data class == compares by value, not reference
            val r1 = ValidationResult.Invalid(listOf("Error A", "Error B"))
            val r2 = ValidationResult.Invalid(listOf("Error A", "Error B"))

            assertEquals(r1, r2)           // structural equality
            assertFalse(r1 === r2)         // referential inequality
        }

        @Test
        @DisplayName("getErrorsOrEmpty() returns empty list for Valid")
        fun `getErrorsOrEmpty - Valid returns empty`() {
            val errors = ValidationResult.Valid.getErrorsOrEmpty()
            assertTrue(errors.isEmpty())
        }

        @Test
        @DisplayName("getErrorsOrEmpty() returns error list for Invalid")
        fun `getErrorsOrEmpty - Invalid returns errors`() {
            val expected = listOf("Insufficient funds", "Account inactive")
            val result = ValidationResult.Invalid(expected).getErrorsOrEmpty()
            assertEquals(expected, result)
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Testing companion object constants (TransactionValidator)
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Companion object constants")
    inner class CompanionObjectTests {

        @Test
        @DisplayName("MIN_AMOUNT should be 0.01")
        fun `MIN_AMOUNT - correct value`() {
            // KOTLIN CONCEPT: accessing companion object property
            assertEquals(BigDecimal("0.01"), TransactionValidator.MIN_AMOUNT)
        }

        @Test
        @DisplayName("MAX_AMOUNT should be 1,000,000.00")
        fun `MAX_AMOUNT - correct value`() {
            assertEquals(BigDecimal("1000000.00"), TransactionValidator.MAX_AMOUNT)
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Parameterized: deposit validations
    // ════════════════════════════════════════════════════════════════════════

    @ParameterizedTest(name = "amount={0} → valid={1}")
    @CsvSource(
        "0.01,    true",
        "500.00,  true",
        "1000000.00, true",
        "0.00,    false",
        "-10.00,  false",
        "1000001.00, false"
    )
    @DisplayName("DEPOSIT validation with various amounts")
    fun `validate - deposit amount range`(amount: BigDecimal, expectedValid: Boolean) {
        val result = validator.validate(
            type          = TransactionType.DEPOSIT,
            amount        = amount,
            sourceAccount = activeAccount(),
            targetAccount = null
        )
        assertEquals(expectedValid, result.isValid(),
            "Deposit of $amount should be ${if (expectedValid) "valid" else "invalid"}")
    }

    // ════════════════════════════════════════════════════════════════════════
    // Withdrawal validation
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("WITHDRAWAL validation")
    inner class WithdrawalValidationTests {

        @Test
        @DisplayName("valid withdrawal within balance succeeds")
        fun `withdraw - sufficient funds`() {
            val result = validator.validate(
                type          = TransactionType.WITHDRAWAL,
                amount        = BigDecimal("500.00"),
                sourceAccount = activeAccount(balance = BigDecimal("1000.00")),
                targetAccount = null
            )
            assertTrue(result.isValid())
        }

        @Test
        @DisplayName("withdrawal exceeding balance fails")
        fun `withdraw - insufficient funds`() {
            val result = validator.validate(
                type          = TransactionType.WITHDRAWAL,
                amount        = BigDecimal("1500.00"),
                sourceAccount = activeAccount(balance = BigDecimal("1000.00")),
                targetAccount = null
            )
            assertFalse(result.isValid())
            assertTrue(result.getErrorsOrEmpty().any { "Insufficient funds" in it })
        }

        @Test
        @DisplayName("withdrawal from inactive account fails")
        fun `withdraw - inactive account`() {
            val result = validator.validate(
                type          = TransactionType.WITHDRAWAL,
                amount        = BigDecimal("100.00"),
                sourceAccount = inactiveAccount(),
                targetAccount = null
            )
            assertFalse(result.isValid())
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Transfer validation
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("TRANSFER validation")
    inner class TransferValidationTests {

        @Test
        @DisplayName("valid transfer between active accounts succeeds")
        fun `transfer - happy path`() {
            val result = validator.validate(
                type          = TransactionType.TRANSFER,
                amount        = BigDecimal("200.00"),
                sourceAccount = activeAccount(id = 1L, balance = BigDecimal("500.00")),
                targetAccount = activeAccount(id = 2L)
            )
            assertTrue(result.isValid())
        }

        @Test
        @DisplayName("transfer to same account fails")
        fun `transfer - same account`() {
            val account = activeAccount(id = 1L)
            val result  = validator.validate(
                type          = TransactionType.TRANSFER,
                amount        = BigDecimal("100.00"),
                sourceAccount = account,
                targetAccount = account   // same object — same accountId
            )
            assertFalse(result.isValid())
            assertTrue(result.getErrorsOrEmpty().any { "different" in it })
        }

        @Test
        @DisplayName("transfer with currency mismatch fails")
        fun `transfer - currency mismatch`() {
            val result = validator.validate(
                type          = TransactionType.TRANSFER,
                amount        = BigDecimal("100.00"),
                sourceAccount = activeAccount(id = 1L, currency = "USD"),
                targetAccount = activeAccount(id = 2L, currency = "EUR")
            )
            assertFalse(result.isValid())
            assertTrue(result.getErrorsOrEmpty().any { "Currency mismatch" in it })
        }

        @Test
        @DisplayName("transfer with null target account fails")
        fun `transfer - null target`() {
            val result = validator.validate(
                type          = TransactionType.TRANSFER,
                amount        = BigDecimal("100.00"),
                sourceAccount = activeAccount(),
                targetAccount = null
            )
            assertFalse(result.isValid())
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Testing data class: AccountSnapshot
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("AccountSnapshot data class")
    inner class AccountSnapshotTests {

        @Test
        @DisplayName("copy() preserves unchanged fields")
        fun `copy - preserves fields`() {
            val original = activeAccount(id = 5L, balance = BigDecimal("999.00"))
            val updated  = original.copy(balance = BigDecimal("799.00"))

            assertEquals(original.accountId, updated.accountId)
            assertEquals(original.accountNumber, updated.accountNumber)
            assertEquals(BigDecimal("799.00"), updated.balance)
        }

        @Test
        @DisplayName("isActive() returns true for ACTIVE status")
        fun `isActive - active`() {
            assertTrue(activeAccount().isActive())
        }

        @Test
        @DisplayName("isActive() returns false for INACTIVE status")
        fun `isActive - inactive`() {
            assertFalse(inactiveAccount().isActive())
        }
    }
}
