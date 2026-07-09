package com.learn.finance.account.service

import com.learn.finance.account.api.CreateCustomerRequest
import com.learn.finance.account.api.UpdateCustomerRequest
import com.learn.finance.account.db.CustomerDao
import com.learn.finance.account.model.Customer
import jakarta.ws.rs.NotFoundException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * CustomerServiceTest — Unit Tests
 *
 * JUNIT 5 CONCEPTS DEMONSTRATED:
 *  • @Test           — marks a test method
 *  • @BeforeEach     — runs before every test (lifecycle)
 *  • @Nested         — groups related tests in an inner class
 *  • @Tag            — categorizes tests for filtering (run with mvn test -Dgroups=unit)
 *  • @DisplayName    — human-readable test names in reports
 *  • assertAll {}    — runs all assertions even if one fails
 *  • assertThrows {} — verifies an exception is thrown
 *
 * MOCKITO CONCEPTS DEMONSTRATED:
 *  • mock()          — creates a mock of an interface/class
 *  • whenever()      — stubs a method call with a return value (mockito-kotlin)
 *  • verify()        — asserts a method was called
 *  • never()         — asserts a method was NOT called
 *  • any()           — argument matcher for any value of a type
 *  • eq()            — argument matcher for an exact value
 *
 * AAA PATTERN:
 *  Each test follows Arrange → Act → Assert
 * ─────────────────────────────────────────────────────────────────────────────
 */
@Tag("unit")
@DisplayName("CustomerService Unit Tests")
class CustomerServiceTest {

    // ── AAA: Arrange (shared setup) ──────────────────────────────────────────

    /**
     * MOCKITO CONCEPT: mock() creates a fully mocked instance.
     * All methods return defaults (null, 0, empty lists) unless stubbed.
     */
    private val customerDao: CustomerDao = mock()
    private lateinit var customerService: CustomerService

    /**
     * JUNIT 5 CONCEPT: @BeforeEach — runs before every @Test method.
     * Use this to reset state, reinitialize mocks, or set common fixtures.
     */
    @BeforeEach
    fun setUp() {
        customerService = CustomerService(customerDao)
    }

    // ── Helper: test fixtures ────────────────────────────────────────────────

    private fun aCustomer(id: Long = 1L, email: String = "john@example.com") = Customer(
        id        = id,
        firstName = "John",
        lastName  = "Doe",
        email     = email,
        phone     = "+1-555-1234",
        status    = "ACTIVE"
    )

    // ════════════════════════════════════════════════════════════════════════
    // Grouped with @Nested: createCustomer tests
    // ════════════════════════════════════════════════════════════════════════

    /**
     * JUNIT 5 CONCEPT: @Nested groups logically related tests.
     * Nested classes share the outer class's @BeforeEach hooks.
     */
    @Nested
    @DisplayName("createCustomer()")
    inner class CreateCustomerTests {

        @Test
        @DisplayName("should create customer and return with generated ID")
        fun `createCustomer - happy path`() {
            // ARRANGE
            val request = CreateCustomerRequest(
                firstName = "John",
                lastName  = "Doe",
                email     = "john@example.com",
                phone     = "+1-555-1234"
            )

            // MOCKITO CONCEPT: whenever().thenReturn() stubs a method
            whenever(customerDao.existsByEmail(eq("john@example.com"))).thenReturn(0)
            whenever(customerDao.insert(
                firstName = any(),
                lastName  = any(),
                email     = any(),
                phone     = any(),
                status    = any()
            )).thenReturn(42L)

            // ARRANGE: the DAO returns the saved customer
            whenever(customerDao.findById(42L)).thenReturn(aCustomer(id = 42L))

            // ACT
            val result = customerService.createCustomer(request)

            // ASSERT
            // JUNIT 5 CONCEPT: assertAll — all assertions run even if one fails
            assertAll("created customer",
                { assertNotNull(result.id) },
                { assertEquals("John", result.firstName) },
                { assertEquals("Doe",  result.lastName) },
                { assertEquals("john@example.com", result.email) }
            )

            // MOCKITO CONCEPT: verify() asserts that a method was called
            verify(customerDao).existsByEmail("john@example.com")
            verify(customerDao).insert(
                firstName = "John",
                lastName  = "Doe",
                email     = "john@example.com",
                phone     = "+1-555-1234",
                status    = "ACTIVE"
            )
        }

        @Test
        @DisplayName("should throw BadRequestException when email already exists")
        fun `createCustomer - duplicate email throws`() {
            // ARRANGE
            val request = CreateCustomerRequest(
                firstName = "Jane",
                lastName  = "Smith",
                email     = "existing@example.com"
            )
            whenever(customerDao.existsByEmail("existing@example.com")).thenReturn(1)

            // ASSERT: assertThrows verifies the exception type
            assertThrows(jakarta.ws.rs.BadRequestException::class.java) {
                customerService.createCustomer(request)
            }

            // MOCKITO CONCEPT: verify with never() — insert should NOT be called
            verify(customerDao, never()).insert(
                firstName = any(),
                lastName  = any(),
                email     = any(),
                phone     = any(),
                status    = any()
            )
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when firstName is blank")
        fun `createCustomer - blank firstName throws`() {
            val request = CreateCustomerRequest(firstName = "  ", lastName = "Doe", email = "a@b.com")

            assertThrows(IllegalArgumentException::class.java) {
                customerService.createCustomer(request)
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Grouped with @Nested: getById tests
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getById()")
    inner class GetByIdTests {

        @Test
        @DisplayName("should return customer when found")
        fun `getById - found`() {
            val expected = aCustomer(id = 1L)
            whenever(customerDao.findById(1L)).thenReturn(expected)

            val result = customerService.getById(1L)

            assertEquals(expected, result)
            verify(customerDao).findById(1L)
        }

        @Test
        @DisplayName("should throw NotFoundException when customer does not exist")
        fun `getById - not found throws`() {
            whenever(customerDao.findById(999L)).thenReturn(null)

            assertThrows(NotFoundException::class.java) {
                customerService.getById(999L)
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Grouped with @Nested: updateCustomer tests
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("updateCustomer()")
    inner class UpdateCustomerTests {

        @Test
        @DisplayName("should update customer fields and return updated domain object")
        fun `updateCustomer - happy path`() {
            // ARRANGE
            val existing = aCustomer(id = 1L)
            val updated  = existing.copy(firstName = "Jonathan", phone = "+1-999-0000")
            val request  = UpdateCustomerRequest(firstName = "Jonathan", lastName = "Doe", phone = "+1-999-0000")

            whenever(customerDao.findById(1L)).thenReturn(existing, updated)
            whenever(customerDao.update(
                id        = eq(1L),
                firstName = any(),
                lastName  = any(),
                phone     = any()
            )).thenReturn(1)

            // ACT
            val result = customerService.updateCustomer(1L, request)

            // ASSERT
            assertEquals("Jonathan", result.firstName)
            assertEquals("+1-999-0000", result.phone)

            // MOCKITO CONCEPT: verify argument values
            verify(customerDao).update(
                id        = 1L,
                firstName = "Jonathan",
                lastName  = "Doe",
                phone     = "+1-999-0000"
            )
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Miscellaneous / lifecycle tests
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("deactivateCustomer()")
    inner class DeactivateTests {

        @Test
        @DisplayName("should call deactivate when customer exists")
        fun `deactivateCustomer - success`() {
            whenever(customerDao.findById(1L)).thenReturn(aCustomer())

            customerService.deactivateCustomer(1L)

            verify(customerDao).deactivate(1L)
        }

        @Test
        @DisplayName("should throw NotFoundException and never call deactivate")
        fun `deactivateCustomer - not found`() {
            whenever(customerDao.findById(99L)).thenReturn(null)

            assertThrows(NotFoundException::class.java) {
                customerService.deactivateCustomer(99L)
            }

            verify(customerDao, never()).deactivate(any())
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Domain model / extension function tests
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Customer data class properties")
    inner class CustomerModelTests {

        @Test
        @DisplayName("fullName computed property should concatenate first and last name")
        fun `fullName - computed correctly`() {
            val customer = Customer(
                firstName = "Alice",
                lastName  = "Wonder",
                email     = "alice@example.com"
            )
            // KOTLIN CONCEPT: testing a computed property (custom getter)
            assertEquals("Alice Wonder", customer.fullName)
        }

        @Test
        @DisplayName("data class copy() should create a modified clone")
        fun `copy() - only changes specified fields`() {
            val original = aCustomer()
            val copy     = original.copy(firstName = "Changed")

            // JUNIT 5: assertAll runs all assertions
            assertAll(
                { assertEquals("Changed",          copy.firstName) },
                { assertEquals(original.lastName,  copy.lastName) },
                { assertEquals(original.email,     copy.email) }
            )
        }

        @Test
        @DisplayName("data class equals() should be value-based, not reference-based")
        fun `equals() - structural equality`() {
            val c1 = aCustomer(id = 1L)
            val c2 = aCustomer(id = 1L)

            // KOTLIN CONCEPT: == calls equals() (structural), not reference equality
            assertTrue(c1 == c2)
            // KOTLIN CONCEPT: === is reference equality — different objects
            assertFalse(c1 === c2)
        }

        @Test
        @DisplayName("isActive() should return true for ACTIVE status")
        fun `isActive() - active customer`() {
            val customer = aCustomer().copy(status = "ACTIVE")
            assertTrue(customer.isActive())
        }

        @Test
        @DisplayName("isActive() should return false for INACTIVE status")
        fun `isActive() - inactive customer`() {
            val customer = aCustomer().copy(status = "INACTIVE")
            assertFalse(customer.isActive())
        }
    }
}
