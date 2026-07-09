package com.learn.finance.account.service

import com.learn.finance.account.api.CreateAccountRequest
import com.learn.finance.account.db.AccountDao
import com.learn.finance.account.db.CustomerDao
import com.learn.finance.account.kafka.AccountEventProducer
import com.learn.finance.account.model.Account
import com.learn.finance.account.model.AccountStatus
import com.learn.finance.account.model.AccountType
import com.learn.finance.account.model.Customer
import jakarta.ws.rs.BadRequestException
import jakarta.ws.rs.NotFoundException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.util.stream.Stream

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * AccountServiceTest — Parameterized Tests + Mockito Intermediate
 *
 * JUNIT 5 CONCEPTS DEMONSTRATED:
 *  • @ExtendWith(MockitoExtension::class)  — enables @Mock and @InjectMocks
 *  • @Mock            — field-level mock declaration (no manual mock() call)
 *  • @InjectMocks     — Mockito injects @Mock fields into the class under test
 *  • @ParameterizedTest  — runs one test with multiple inputs
 *  • @EnumSource      — feeds enum constants as test arguments
 *  • @CsvSource       — feeds inline CSV data as test arguments
 *  • @MethodSource    — feeds arguments from a companion object method
 *  • @Disabled        — skip a test (with reason)
 *
 * MOCKITO INTERMEDIATE CONCEPTS:
 *  • argumentCaptor   — capture arguments passed to mocked methods
 *  • argument matchers: any(), eq(), argThat()
 *  • mock exceptions  — stub a method to throw
 *  • verify with times/atLeastOnce
 * ─────────────────────────────────────────────────────────────────────────────
 */
@Tag("unit")
@DisplayName("AccountService Unit Tests")
@ExtendWith(MockitoExtension::class)           // MOCKITO CONCEPT: enables annotation-based mocks
class AccountServiceTest {

    // ── MOCKITO INTERMEDIATE: @Mock and @InjectMocks ─────────────────────────

    /**
     * @Mock creates a Mockito mock and injects it into @InjectMocks fields.
     * Equivalent to: `private val accountDao: AccountDao = mock()`
     * but scoped to the test class lifecycle automatically.
     */
    @Mock private lateinit var accountDao: AccountDao
    @Mock private lateinit var customerDao: CustomerDao
    @Mock private lateinit var eventProducer: AccountEventProducer

    /**
     * @InjectMocks creates AccountService and injects the @Mock fields
     * using constructor injection (preferred) or field injection.
     */
    @InjectMocks private lateinit var accountService: AccountService

    // ── Fixtures ─────────────────────────────────────────────────────────────

    private fun aCustomer(id: Long = 1L) = Customer(
        id = id, firstName = "John", lastName = "Doe", email = "john@example.com"
    )

    private fun anAccount(
        id: Long = 10L,
        type: AccountType = AccountType.SAVINGS,
        balance: BigDecimal = BigDecimal("1000.00"),
        status: AccountStatus = AccountStatus.Active
    ) = Account(
        id            = id,
        customerId    = 1L,
        accountNumber = "SAV-000001-1234",
        accountType   = type,
        balance       = balance,
        currency      = "USD",
        status        = status
    )

    // ════════════════════════════════════════════════════════════════════════
    // @EnumSource: test with each AccountType enum value
    // ════════════════════════════════════════════════════════════════════════

    /**
     * JUNIT 5 CONCEPT: @EnumSource feeds each enum constant as a separate test run.
     * The test runs 3 times — once for SAVINGS, CHECKING, INVESTMENT.
     */
    @ParameterizedTest(name = "account type = {0}")
    @EnumSource(AccountType::class)
    @DisplayName("createAccount should succeed for all account types")
    fun `createAccount - all account types`(accountType: AccountType) {
        // ARRANGE
        val request = CreateAccountRequest(
            customerId    = 1L,
            accountType   = accountType,
            currency      = "USD",
            initialDeposit = BigDecimal("500.00")
        )
        val savedAccount = anAccount(id = 99L, type = accountType)

        whenever(customerDao.findById(1L)).thenReturn(aCustomer())
        whenever(accountDao.insert(
            customerId    = any(),
            accountNumber = any(),
            accountType   = eq(accountType.name),
            balance       = any(),
            currency      = any(),
            status        = any()
        )).thenReturn(99L)
        whenever(accountDao.findById(99L)).thenReturn(savedAccount)

        // ACT
        val result = accountService.createAccount(request)

        // ASSERT
        assertEquals(accountType, result.accountType)
        assertNotNull(result.id)
    }

    // ════════════════════════════════════════════════════════════════════════
    // @CsvSource: test balance validation with multiple amounts
    // ════════════════════════════════════════════════════════════════════════

    /**
     * JUNIT 5 CONCEPT: @CsvSource provides inline CSV rows as arguments.
     * Each row becomes one test invocation.
     */
    @ParameterizedTest(name = "balance={0}, expectedSufficient={1}")
    @CsvSource(
        "1000.00, true",
        "500.00,  true",
        "0.01,    true",
        "0.00,    false",
        "-1.00,   false"
    )
    @DisplayName("hasSufficientFunds should correctly evaluate balance")
    fun `hasSufficientFunds - parameterized`(balance: BigDecimal, expectedSufficient: Boolean) {
        val account = anAccount(balance = balance)
        val checkAmount = BigDecimal("0.01")

        // KOTLIN CONCEPT: infix function — readable assertion
        val result = account hasSufficientFunds checkAmount

        assertEquals(expectedSufficient, result,
            "Account with balance $balance should ${if (expectedSufficient) "" else "NOT "}have sufficient funds")
    }

    // ════════════════════════════════════════════════════════════════════════
    // @MethodSource: complex argument sets from a companion object
    // ════════════════════════════════════════════════════════════════════════

    /**
     * JUNIT 5 CONCEPT: @MethodSource references a method that provides a Stream of Arguments.
     * The method must be in the companion object (acts as static in JVM).
     */
    @ParameterizedTest(name = "status={0}, canTransact={1}")
    @MethodSource("accountStatusProvider")
    @DisplayName("AccountStatus.canTransact should match expected behavior")
    fun `AccountStatus - canTransact parameterized`(status: AccountStatus, canTransact: Boolean) {
        assertEquals(canTransact, status.canTransact(),
            "Status $status should ${if (canTransact) "" else "NOT "}allow transactions")
    }

    companion object {
        /**
         * JUNIT 5 CONCEPT: @MethodSource provider — must be JVM-static.
         * KOTLIN CONCEPT: companion object @JvmStatic makes it visible as a static method.
         */
        @JvmStatic
        fun accountStatusProvider(): Stream<Arguments> = Stream.of(
            Arguments.of(AccountStatus.Active,   true),
            Arguments.of(AccountStatus.Inactive, false),
            Arguments.of(AccountStatus.Closed,   false),
            Arguments.of(AccountStatus.Frozen,   false)
        )
    }

    // ════════════════════════════════════════════════════════════════════════
    // Mockito Intermediate: argument captor
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("createAccount - argument verification")
    inner class CreateAccountArgumentCaptor {

        @Test
        @DisplayName("should insert account with correct account number prefix")
        fun `createAccount - verifies accountNumber prefix via captor`() {
            // ARRANGE
            val request = CreateAccountRequest(
                customerId    = 1L,
                accountType   = AccountType.SAVINGS,
                initialDeposit = BigDecimal.ZERO
            )
            whenever(customerDao.findById(1L)).thenReturn(aCustomer())
            whenever(accountDao.insert(any(), any(), any(), any(), any(), any())).thenReturn(1L)
            whenever(accountDao.findById(1L)).thenReturn(anAccount())

            // MOCKITO CONCEPT: argumentCaptor — captures the value passed to a mock
            val accountNumberCaptor = argumentCaptor<String>()

            // ACT
            accountService.createAccount(request)

            // ASSERT: verify the captured argument
            verify(accountDao).insert(
                customerId    = any(),
                accountNumber = accountNumberCaptor.capture(),
                accountType   = any(),
                balance       = any(),
                currency      = any(),
                status        = any()
            )
            assertTrue(
                accountNumberCaptor.firstValue.startsWith("SAV"),
                "SAVINGS account number should start with SAV"
            )
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Mockito Intermediate: mock exceptions
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Exception scenarios")
    inner class ExceptionTests {

        @Test
        @DisplayName("should throw NotFoundException when customer not found")
        fun `createAccount - customer not found`() {
            val request = CreateAccountRequest(customerId = 999L, accountType = AccountType.SAVINGS)
            // MOCKITO CONCEPT: return null to simulate "not found"
            whenever(customerDao.findById(999L)).thenReturn(null)

            assertThrows<NotFoundException> {
                accountService.createAccount(request)
            }
        }

        @Test
        @DisplayName("should handle Kafka producer exception gracefully")
        fun `createAccount - kafka failure is swallowed`() {
            // ARRANGE
            val request = CreateAccountRequest(customerId = 1L, accountType = AccountType.CHECKING)
            val account = anAccount(type = AccountType.CHECKING)

            whenever(customerDao.findById(1L)).thenReturn(aCustomer())
            whenever(accountDao.insert(any(), any(), any(), any(), any(), any())).thenReturn(10L)
            whenever(accountDao.findById(10L)).thenReturn(account)

            // MOCKITO CONCEPT: stub to throw an exception
            whenever(eventProducer.publishAccountCreated(any()))
                .thenThrow(RuntimeException("Kafka broker unavailable"))

            // ACT — should NOT throw despite Kafka failure (graceful degradation)
            val result = accountService.createAccount(request)

            // ASSERT — account was still created
            assertNotNull(result)
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // @Disabled: placeholder for future integration tests
    // ════════════════════════════════════════════════════════════════════════

    /**
     * JUNIT 5 CONCEPT: @Disabled skips a test.
     * Use this to mark tests you plan to implement later, or that require
     * external dependencies (like a running database).
     */
    @Test
    @Disabled("Requires running MySQL — implement as integration test")
    fun `integration - createAccount with real database`() {
        // TODO: implement with Testcontainers
    }
}
