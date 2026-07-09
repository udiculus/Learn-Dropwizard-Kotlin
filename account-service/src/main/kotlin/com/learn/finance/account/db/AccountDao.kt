package com.learn.finance.account.db

import com.learn.finance.account.model.Account
import com.learn.finance.account.model.AccountStatus
import com.learn.finance.account.model.AccountType
import com.learn.finance.account.model.Customer
import org.jdbi.v3.core.mapper.RowMapper
import org.jdbi.v3.core.statement.StatementContext
import org.jdbi.v3.sqlobject.config.RegisterRowMapper
import org.jdbi.v3.sqlobject.customizer.Bind
import org.jdbi.v3.sqlobject.customizer.BindBean
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys
import org.jdbi.v3.sqlobject.statement.SqlQuery
import org.jdbi.v3.sqlobject.statement.SqlUpdate
import java.math.BigDecimal
import java.sql.ResultSet
import java.time.LocalDateTime

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * Data Access Objects (DAOs) — JDBI3 SQL Object API
 *
 * KOTLIN CONCEPTS DEMONSTRATED:
 *  • interface with default-method-like abstract declarations
 *  • inner class (RowMapper as nested class)
 *  • Kotlin generics (List<Account>)
 *  • nullable return types (Account?)
 *  • type conversion (String → enum, BigDecimal)
 *
 * DROPWIZARD / JDBI CONCEPTS:
 *  • @SqlQuery / @SqlUpdate annotations for declarative SQL
 *  • @Bind to bind individual parameters
 *  • @BindBean to bind all properties of an object by name
 *  • @GetGeneratedKeys to retrieve the auto-increment ID
 *  • @RegisterRowMapper to associate a result mapper with the DAO
 * ─────────────────────────────────────────────────────────────────────────────
 */

// ── Customer Row Mapper ───────────────────────────────────────────────────────

/**
 * KOTLIN CONCEPT: Class implementing a Java functional interface (RowMapper<T>).
 * Maps a JDBC ResultSet row into a Customer domain object.
 *
 * KOTLIN CONCEPT: `rs.getString("column")` — standard null-safe JDBC access.
 */
class CustomerMapper : RowMapper<Customer> {
    override fun map(rs: ResultSet, ctx: StatementContext): Customer = Customer(
        id        = rs.getLong("id"),
        firstName = rs.getString("first_name"),
        lastName  = rs.getString("last_name"),
        email     = rs.getString("email"),
        phone     = rs.getString("phone"),          // nullable: may be null in DB
        status    = rs.getString("status"),
        createdAt = rs.getObject("created_at", LocalDateTime::class.java),
        updatedAt = rs.getObject("updated_at", LocalDateTime::class.java)
    )
}

// ── Customer DAO ──────────────────────────────────────────────────────────────

@RegisterRowMapper(CustomerMapper::class)
interface CustomerDao {

    /**
     * MySQL DML: INSERT with named parameters.
     * @GetGeneratedKeys returns the auto-increment primary key.
     */
    @SqlUpdate("""
        INSERT INTO customers (first_name, last_name, email, phone, status)
        VALUES (:firstName, :lastName, :email, :phone, :status)
    """)
    @GetGeneratedKeys("id")
    fun insert(
        @Bind("firstName") firstName: String,
        @Bind("lastName")  lastName: String,
        @Bind("email")     email: String,
        @Bind("phone")     phone: String?,
        @Bind("status")    status: String
    ): Long

    /**
     * MySQL DML: SELECT with WHERE clause — searching data.
     * Returns null if not found (nullable return type).
     */
    @SqlQuery("SELECT * FROM customers WHERE id = :id")
    fun findById(@Bind("id") id: Long): Customer?

    /**
     * MySQL DML: SELECT with LIKE — pattern search.
     */
    @SqlQuery("SELECT * FROM customers WHERE email = :email LIMIT 1")
    fun findByEmail(@Bind("email") email: String): Customer?

    /**
     * MySQL DML: SELECT all with status filter.
     * Returns Kotlin List<Customer> — JDBI handles the collection mapping.
     */
    @SqlQuery("SELECT * FROM customers WHERE status = :status ORDER BY created_at DESC")
    fun findByStatus(@Bind("status") status: String): List<Customer>

    /**
     * MySQL DML: SELECT all — returns all customers.
     */
    @SqlQuery("SELECT * FROM customers ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
    fun findAll(@Bind("limit") limit: Int, @Bind("offset") offset: Int): List<Customer>

    /**
     * MySQL DML: UPDATE — modifying data.
     */
    @SqlUpdate("""
        UPDATE customers
        SET first_name = :firstName, last_name = :lastName, phone = :phone
        WHERE id = :id
    """)
    fun update(
        @Bind("id")        id: Long,
        @Bind("firstName") firstName: String,
        @Bind("lastName")  lastName: String,
        @Bind("phone")     phone: String?
    ): Int

    /**
     * MySQL DML: Soft delete — UPDATE status instead of DELETE.
     */
    @SqlUpdate("UPDATE customers SET status = 'INACTIVE' WHERE id = :id")
    fun deactivate(@Bind("id") id: Long): Int

    @SqlQuery("SELECT COUNT(*) FROM customers WHERE email = :email")
    fun existsByEmail(@Bind("email") email: String): Int
}

// ── Account Row Mapper ────────────────────────────────────────────────────────

class AccountMapper : RowMapper<Account> {
    override fun map(rs: ResultSet, ctx: StatementContext): Account = Account(
        id            = rs.getLong("id"),
        customerId    = rs.getLong("customer_id"),
        accountNumber = rs.getString("account_number"),
        // KOTLIN CONCEPT: enum valueOf — string → enum type conversion
        accountType   = AccountType.valueOf(rs.getString("account_type")),
        balance       = rs.getBigDecimal("balance"),
        currency      = rs.getString("currency"),
        // KOTLIN CONCEPT: companion object factory + sealed class
        status        = AccountStatus.fromString(rs.getString("status")),
        createdAt     = rs.getObject("created_at", LocalDateTime::class.java),
        updatedAt     = rs.getObject("updated_at", LocalDateTime::class.java)
    )
}

// ── Account DAO ───────────────────────────────────────────────────────────────

@RegisterRowMapper(AccountMapper::class)
interface AccountDao {

    @SqlUpdate("""
        INSERT INTO accounts (customer_id, account_number, account_type, balance, currency, status)
        VALUES (:customerId, :accountNumber, :accountType, :balance, :currency, :status)
    """)
    @GetGeneratedKeys("id")
    fun insert(
        @Bind("customerId")     customerId: Long,
        @Bind("accountNumber")  accountNumber: String,
        @Bind("accountType")    accountType: String,
        @Bind("balance")        balance: BigDecimal,
        @Bind("currency")       currency: String,
        @Bind("status")         status: String
    ): Long

    @SqlQuery("SELECT * FROM accounts WHERE id = :id")
    fun findById(@Bind("id") id: Long): Account?

    @SqlQuery("SELECT * FROM accounts WHERE account_number = :accountNumber")
    fun findByAccountNumber(@Bind("accountNumber") accountNumber: String): Account?

    @SqlQuery("SELECT * FROM accounts WHERE customer_id = :customerId ORDER BY created_at DESC")
    fun findByCustomerId(@Bind("customerId") customerId: Long): List<Account>

    @SqlQuery("""
        SELECT * FROM accounts
        WHERE status = :status
        ORDER BY created_at DESC
        LIMIT :limit OFFSET :offset
    """)
    fun findByStatus(
        @Bind("status") status: String,
        @Bind("limit")  limit: Int,
        @Bind("offset") offset: Int
    ): List<Account>

    /**
     * MySQL DML: UPDATE — modify balance (used during transactions).
     * DECIMAL arithmetic is done in SQL for precision.
     */
    @SqlUpdate("""
        UPDATE accounts
        SET balance = :balance, updated_at = CURRENT_TIMESTAMP
        WHERE id = :id AND status = 'ACTIVE'
    """)
    fun updateBalance(@Bind("id") id: Long, @Bind("balance") balance: BigDecimal): Int

    @SqlUpdate("""
        UPDATE accounts
        SET status = :status, updated_at = CURRENT_TIMESTAMP
        WHERE id = :id
    """)
    fun updateStatus(@Bind("id") id: Long, @Bind("status") status: String): Int

    @SqlQuery("SELECT COUNT(*) FROM accounts WHERE customer_id = :customerId AND status = 'ACTIVE'")
    fun countActiveByCustomer(@Bind("customerId") customerId: Long): Int
}
