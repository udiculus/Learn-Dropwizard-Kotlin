package com.learn.finance.account.service

import com.learn.finance.account.api.CreateAccountRequest
import com.learn.finance.account.api.CreateCustomerRequest
import com.learn.finance.account.api.UpdateCustomerRequest
import com.learn.finance.account.db.AccountDao
import com.learn.finance.account.db.CustomerDao
import com.learn.finance.account.kafka.AccountEventProducer
import com.learn.finance.account.kafka.AccountEvent
import com.learn.finance.account.model.Account
import com.learn.finance.account.model.AccountStatus
import com.learn.finance.account.model.AccountType
import com.learn.finance.account.model.Customer
import jakarta.ws.rs.BadRequestException
import jakarta.ws.rs.NotFoundException
import org.slf4j.LoggerFactory
import java.math.BigDecimal

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * CustomerService — Business logic for customer management
 *
 * KOTLIN CONCEPTS DEMONSTRATED:
 *  • Primary constructor with val properties (dependency injection)
 *  • Exception handling (try/catch/throw, custom exceptions)
 *  • Null safety: ?: (Elvis operator), ?. (safe call), !! (non-null assertion)
 *  • when expression as a statement and as an expression
 *  • Extension functions (defined at bottom of file)
 *  • String templates
 *  • let, also scope functions
 * ─────────────────────────────────────────────────────────────────────────────
 */
class CustomerService(
    private val customerDao: CustomerDao
) {

    private val logger = LoggerFactory.getLogger(CustomerService::class.java)

    /**
     * Create a new customer.
     *
     * KOTLIN CONCEPTS:
     *  - also { } scope function: runs a side-effect block and returns the receiver
     *  - require() builtin: throws IllegalArgumentException if condition is false
     *  - ?: Elvis operator: provides a default value if null
     */
    fun createCustomer(request: CreateCustomerRequest): Customer {
        // KOTLIN CONCEPT: require() — precondition check
        require(request.firstName.isNotBlank()) { "First name cannot be blank" }
        require(request.email.isNotBlank()) { "Email cannot be blank" }

        // KOTLIN CONCEPT: smart cast — after null check, compiler knows it's non-null
        if (customerDao.existsByEmail(request.email) > 0) {
            throw BadRequestException("Customer with email '${request.email}' already exists")
        }

        val customer = request.toDomain()

        val generatedId = customerDao.insert(
            firstName = customer.firstName,
            lastName  = customer.lastName,
            email     = customer.email,
            phone     = customer.phone,
            status    = customer.status
        )

        // KOTLIN CONCEPT: copy() on data class — creates a modified clone
        return customer.copy(id = generatedId).also {
            logger.info("Created customer: id=${it.id}, email=${it.email}")
        }
    }

    /**
     * KOTLIN CONCEPT: nullable return type (Customer?)
     * Returns null if not found — callers must handle the null case.
     */
    fun findById(id: Long): Customer? = customerDao.findById(id)

    /**
     * KOTLIN CONCEPT: Elvis operator ?: and NotFoundException
     * The `?: throw` pattern is idiomatic Kotlin for "find or throw".
     */
    fun getById(id: Long): Customer =
        customerDao.findById(id) ?: throw NotFoundException("Customer not found: id=$id")

    fun getAll(limit: Int = 20, offset: Int = 0): List<Customer> =
        customerDao.findAll(limit, offset)

    fun updateCustomer(id: Long, request: UpdateCustomerRequest): Customer {
        val existing = getById(id)  // throws NotFoundException if absent

        val updated = customerDao.update(
            id        = id,
            firstName = request.firstName,
            lastName  = request.lastName,
            phone     = request.phone
        )

        // KOTLIN CONCEPT: when as expression — returns a value
        return when (updated) {
            0    -> throw IllegalStateException("Update failed for customer id=$id")
            else -> getById(id)
        }
    }

    fun deactivateCustomer(id: Long) {
        getById(id)  // validate exists first
        customerDao.deactivate(id)
        logger.info("Deactivated customer id=$id")
    }
}

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * AccountService — Business logic for bank account management
 *
 * KOTLIN CONCEPTS DEMONSTRATED:
 *  • infix function call: account hasSufficientFunds amount
 *  • Kotlin exception handling try/catch/finally
 *  • Nested try blocks
 *  • Type checking: is, !is, as, as?
 * ─────────────────────────────────────────────────────────────────────────────
 */
class AccountService(
    private val accountDao: AccountDao,
    private val customerDao: CustomerDao,
    private val eventProducer: AccountEventProducer
) {
    private val logger = LoggerFactory.getLogger(AccountService::class.java)

    fun createAccount(request: CreateAccountRequest): Account {
        val customerId  = request.customerId  ?: throw BadRequestException("customerId required")
        val accountType = request.accountType ?: throw BadRequestException("accountType required")

        // Validate customer exists
        customerDao.findById(customerId)
            ?: throw NotFoundException("Customer not found: id=$customerId")

        // KOTLIN CONCEPT: companion object method call
        val accountNumber = Account.generateAccountNumber(customerId, accountType)

        val generatedId = accountDao.insert(
            customerId    = customerId,
            accountNumber = accountNumber,
            accountType   = accountType.name,
            balance       = request.initialDeposit,
            currency      = request.currency,
            status        = "ACTIVE"
        )

        val account = accountDao.findById(generatedId)
            ?: throw IllegalStateException("Failed to retrieve created account")

        // KOTLIN CONCEPT: try/catch — publish event, swallow Kafka errors gracefully
        try {
            eventProducer.publishAccountCreated(account)
        } catch (ex: Exception) {
            // KOTLIN CONCEPT: string template in exception message
            logger.warn("Failed to publish AccountCreated event for account $accountNumber: ${ex.message}")
        }

        return account
    }

    fun getAccount(id: Long): Account =
        accountDao.findById(id) ?: throw NotFoundException("Account not found: id=$id")

    fun getAccountsByCustomer(customerId: Long): List<Account> {
        customerDao.findById(customerId)
            ?: throw NotFoundException("Customer not found: id=$customerId")
        return accountDao.findByCustomerId(customerId)
    }

    fun updateAccountStatus(id: Long, newStatus: String): Account {
        val account = getAccount(id)

        // KOTLIN CONCEPT: type checking with `is` operator
        val status = AccountStatus.fromString(newStatus)

        // KOTLIN CONCEPT: nested try/catch block
        val rowsUpdated = try {
            accountDao.updateStatus(id, status.toString())
        } catch (ex: IllegalArgumentException) {
            throw BadRequestException("Invalid status: $newStatus")
        }

        if (rowsUpdated == 0) throw IllegalStateException("Status update failed for account id=$id")

        try {
            eventProducer.publishAccountStatusChanged(account, status)
        } catch (ex: Exception) {
            logger.warn("Failed to publish status change event: ${ex.message}")
        } finally {
            logger.info("Account status update attempt completed for id=$id")
        }

        return getAccount(id)
    }

    fun closeAccount(id: Long) {
        val account = getAccount(id)

        // KOTLIN CONCEPT: infix function — reads naturally
        if (account hasSufficientFunds BigDecimal("0.01")) {
            throw BadRequestException("Cannot close account with remaining balance: ${account.balance}")
        }

        accountDao.updateStatus(id, "CLOSED")
        eventProducer.publishAccountClosed(account)
        logger.info("Closed account: ${account.accountNumber}")
    }
}

// ── Extension Functions ───────────────────────────────────────────────────────

/**
 * KOTLIN CONCEPT: Extension function on Account.
 * Adds behavior to a class without modifying or subclassing it.
 * This is defined outside the class — notice no `this` needed for member access.
 */
fun Account.toLogString(): String =
    "Account[id=$id, number=$accountNumber, type=$accountType, balance=$balance $currency, status=$status]"

/**
 * Extension function on List<Account> — higher-order function with lambda.
 */
fun List<Account>.totalBalance(): BigDecimal =
    // KOTLIN CONCEPT: fold — accumulates a result by applying lambda to each element
    fold(BigDecimal.ZERO) { acc, account -> acc + account.balance }
