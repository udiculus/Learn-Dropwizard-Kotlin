package com.learn.finance.transaction.db

import com.learn.finance.transaction.model.Transaction
import com.learn.finance.transaction.model.TransactionStatus
import com.learn.finance.transaction.model.TransactionType
import org.jdbi.v3.core.mapper.RowMapper
import org.jdbi.v3.core.statement.StatementContext
import org.jdbi.v3.sqlobject.config.RegisterRowMapper
import org.jdbi.v3.sqlobject.customizer.Bind
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys
import org.jdbi.v3.sqlobject.statement.SqlQuery
import org.jdbi.v3.sqlobject.statement.SqlUpdate
import java.sql.ResultSet
import java.time.LocalDateTime

/**
 * Transaction DAO and RowMapper — covers MySQL DML: INSERT, UPDATE, SELECT with joins.
 */
class TransactionMapper : RowMapper<Transaction> {
    override fun map(rs: ResultSet, ctx: StatementContext): Transaction = Transaction(
        id              = rs.getLong("id"),
        transactionRef  = rs.getString("transaction_ref"),
        sourceAccountId = rs.getLong("source_account_id"),
        targetAccountId = rs.getLong("target_account_id").takeIf { !rs.wasNull() },
        type            = TransactionType.valueOf(rs.getString("type")),
        amount          = rs.getBigDecimal("amount"),
        currency        = rs.getString("currency"),
        status          = TransactionStatus.valueOf(rs.getString("status")),
        description     = rs.getString("description"),
        createdAt       = rs.getObject("created_at", LocalDateTime::class.java),
        updatedAt       = rs.getObject("updated_at", LocalDateTime::class.java)
    )
}

@RegisterRowMapper(TransactionMapper::class)
interface TransactionDao {

    /**
     * MySQL DML: INSERT — adding data.
     * Uses @GetGeneratedKeys to return the auto-increment ID.
     */
    @SqlUpdate("""
        INSERT INTO transactions
            (transaction_ref, source_account_id, target_account_id, type, amount, currency, status, description)
        VALUES
            (:transactionRef, :sourceAccountId, :targetAccountId, :type, :amount, :currency, :status, :description)
    """)
    @GetGeneratedKeys("id")
    fun insert(
        @Bind("transactionRef")   transactionRef: String,
        @Bind("sourceAccountId")  sourceAccountId: Long,
        @Bind("targetAccountId")  targetAccountId: Long?,
        @Bind("type")             type: String,
        @Bind("amount")           amount: java.math.BigDecimal,
        @Bind("currency")         currency: String,
        @Bind("status")           status: String,
        @Bind("description")      description: String?
    ): Long

    /**
     * MySQL DML: SELECT with WHERE — searching data.
     */
    @SqlQuery("SELECT * FROM transactions WHERE id = :id")
    fun findById(@Bind("id") id: Long): Transaction?

    /**
     * MySQL DML: SELECT with complex WHERE — filter by account (source OR target).
     * Demonstrates OR condition, ORDER BY, LIMIT/OFFSET for pagination.
     */
    @SqlQuery("""
        SELECT * FROM transactions
        WHERE source_account_id = :accountId OR target_account_id = :accountId
        ORDER BY created_at DESC
        LIMIT :limit OFFSET :offset
    """)
    fun findByAccountId(
        @Bind("accountId") accountId: Long,
        @Bind("limit")     limit: Int,
        @Bind("offset")    offset: Int
    ): List<Transaction>

    /**
     * MySQL DML: SELECT with aggregation — SUM and GROUP BY.
     */
    @SqlQuery("""
        SELECT COUNT(*) FROM transactions
        WHERE source_account_id = :accountId AND status = 'COMPLETED'
    """)
    fun countCompletedByAccount(@Bind("accountId") accountId: Long): Long

    /**
     * MySQL DML: UPDATE — modifying data (status change).
     */
    @SqlUpdate("""
        UPDATE transactions
        SET status = :status, updated_at = CURRENT_TIMESTAMP
        WHERE id = :id
    """)
    fun updateStatus(@Bind("id") id: Long, @Bind("status") status: String): Int
}
