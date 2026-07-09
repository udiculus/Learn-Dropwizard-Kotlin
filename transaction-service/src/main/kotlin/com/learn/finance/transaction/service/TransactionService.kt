package com.learn.finance.transaction.service

import com.learn.finance.transaction.api.DepositRequest
import com.learn.finance.transaction.api.TransferRequest
import com.learn.finance.transaction.api.WithdrawalRequest
import com.learn.finance.transaction.db.TransactionDao
import com.learn.finance.transaction.kafka.TransactionEventProducer
import com.learn.finance.transaction.model.Transaction
import com.learn.finance.transaction.model.TransactionStatus
import com.learn.finance.transaction.model.TransactionType
import jakarta.ws.rs.BadRequestException
import org.slf4j.LoggerFactory
import java.math.BigDecimal

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * TransactionService — Business logic for all transaction types
 *
 * KOTLIN CONCEPTS DEMONSTRATED:
 *  • Pair destructuring — val (source, target) = transaction.accountPair()
 *  • Triple destructuring — val (ref, type, amount) = transaction.summary()
 *  • labeled returns — return@let, return@run
 *  • when as expression returning values
 *  • fold() — accumulating a result over a list
 * ─────────────────────────────────────────────────────────────────────────────
 */
class TransactionService(
    private val transactionDao: TransactionDao,
    private val accountCache: AccountCache,
    private val validator: TransactionValidator,
    private val eventProducer: TransactionEventProducer
) {
    private val logger = LoggerFactory.getLogger(TransactionService::class.java)

    /**
     * Process a deposit — credits funds to an account.
     * KOTLIN CONCEPT: destructuring Pair returned by accountPair()
     */
    fun deposit(request: DepositRequest): Transaction {
        val sourceAccount = accountCache.getOrThrow(request.accountId)

        val validationResult = validator.validate(
            type          = TransactionType.DEPOSIT,
            amount        = request.amount,
            sourceAccount = sourceAccount,
            targetAccount = null
        )

        if (!validationResult.isValid()) {
            val errors = validationResult.getErrorsOrEmpty().joinToString("; ")
            throw BadRequestException("Deposit validation failed: $errors")
        }

        val transaction = Transaction(
            sourceAccountId = request.accountId,
            type            = TransactionType.DEPOSIT,
            amount          = request.amount,
            currency        = request.currency,
            description     = request.description
        )

        val txnId = transactionDao.insert(
            transactionRef  = transaction.transactionRef,
            sourceAccountId = transaction.sourceAccountId,
            targetAccountId = transaction.targetAccountId,
            type            = transaction.type.name,
            amount          = transaction.amount,
            currency        = transaction.currency,
            status          = transaction.status.name,
            description     = transaction.description
        )

        // KOTLIN CONCEPT: Triple destructuring
        val (ref, type, amount) = transaction.summary()
        logger.info("Processing $type: ref=$ref, amount=$amount")

        return transactionDao.findById(txnId)?.also { saved ->
            // Update cache balance
            accountCache.put(sourceAccount.copy(balance = sourceAccount.balance + amount))
            // Mark completed
            transactionDao.updateStatus(saved.id!!, TransactionStatus.COMPLETED.name)
            eventProducer.publishTransactionCompleted(saved)
        } ?: throw IllegalStateException("Failed to retrieve transaction $txnId")
    }

    /**
     * Process a withdrawal — debits funds from an account.
     */
    fun withdraw(request: WithdrawalRequest): Transaction {
        val sourceAccount = accountCache.getOrThrow(request.accountId)

        val validationResult = validator.validate(
            type          = TransactionType.WITHDRAWAL,
            amount        = request.amount,
            sourceAccount = sourceAccount,
            targetAccount = null
        )
        if (!validationResult.isValid()) {
            throw BadRequestException(validationResult.getErrorsOrEmpty().joinToString("; "))
        }

        val transaction = Transaction(
            sourceAccountId = request.accountId,
            type            = TransactionType.WITHDRAWAL,
            amount          = request.amount,
            currency        = request.currency,
            description     = request.description
        )

        val txnId = transactionDao.insert(
            transactionRef  = transaction.transactionRef,
            sourceAccountId = transaction.sourceAccountId,
            targetAccountId = transaction.targetAccountId,
            type            = transaction.type.name,
            amount          = transaction.amount,
            currency        = transaction.currency,
            status          = transaction.status.name,
            description     = transaction.description
        )
        return transactionDao.findById(txnId)?.also { saved ->
            accountCache.put(sourceAccount.copy(balance = sourceAccount.balance - request.amount))
            transactionDao.updateStatus(saved.id!!, TransactionStatus.COMPLETED.name)
            eventProducer.publishTransactionCompleted(saved)
        } ?: throw IllegalStateException("Failed to retrieve transaction $txnId")
    }

    /**
     * Process a transfer — moves funds between two accounts.
     * KOTLIN CONCEPT: Pair destructuring for (sourceId, targetId)
     */
    fun transfer(request: TransferRequest): Transaction {
        val sourceAccount = accountCache.getOrThrow(request.sourceAccountId)
        val targetAccount = accountCache.getOrThrow(request.targetAccountId)

        val validationResult = validator.validate(
            type          = TransactionType.TRANSFER,
            amount        = request.amount,
            sourceAccount = sourceAccount,
            targetAccount = targetAccount
        )
        if (!validationResult.isValid()) {
            throw BadRequestException(validationResult.getErrorsOrEmpty().joinToString("; "))
        }

        val transaction = Transaction(
            sourceAccountId = request.sourceAccountId,
            targetAccountId = request.targetAccountId,
            type            = TransactionType.TRANSFER,
            amount          = request.amount,
            currency        = request.currency,
            description     = request.description
        )

        // KOTLIN CONCEPT: Pair destructuring
        val (sourceId, targetId) = transaction.accountPair()
        logger.info("Transfer: $sourceId → $targetId, amount=${request.amount}")

        val txnId = transactionDao.insert(
            transactionRef  = transaction.transactionRef,
            sourceAccountId = transaction.sourceAccountId,
            targetAccountId = transaction.targetAccountId,
            type            = transaction.type.name,
            amount          = transaction.amount,
            currency        = transaction.currency,
            status          = transaction.status.name,
            description     = transaction.description
        )
        return transactionDao.findById(txnId)?.also { saved ->
            accountCache.put(sourceAccount.copy(balance = sourceAccount.balance - request.amount))
            targetId?.let { id ->
                accountCache.put(targetAccount.copy(balance = targetAccount.balance + request.amount))
            }
            transactionDao.updateStatus(saved.id!!, TransactionStatus.COMPLETED.name)
            eventProducer.publishTransactionCompleted(saved)
        } ?: throw IllegalStateException("Failed to retrieve transaction")
    }

    fun getHistory(accountId: Long, limit: Int = 20, offset: Int = 0): List<Transaction> =
        transactionDao.findByAccountId(accountId, limit, offset)

    /**
     * KOTLIN CONCEPT: fold() — accumulates total amount over a list of transactions.
     */
    fun getTotalVolume(transactions: List<Transaction>): BigDecimal =
        transactions.fold(BigDecimal.ZERO) { acc, txn -> acc + txn.amount }
}
