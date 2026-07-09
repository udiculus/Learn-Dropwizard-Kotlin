package com.learn.finance.transaction.api

import com.fasterxml.jackson.annotation.JsonProperty
import com.learn.finance.transaction.model.Transaction
import com.learn.finance.transaction.model.TransactionStatus
import com.learn.finance.transaction.model.TransactionType
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.time.LocalDateTime

// ── Request DTOs ──────────────────────────────────────────────────────────────

data class DepositRequest(
    @field:NotNull @JsonProperty("accountId")   val accountId: Long = 0L,
    @field:NotNull
    @field:DecimalMin("0.01")                   @JsonProperty("amount")     val amount: BigDecimal = BigDecimal.ZERO,
    @JsonProperty("currency")                   val currency: String = "USD",
    @JsonProperty("description")                val description: String? = null
)

data class WithdrawalRequest(
    @field:NotNull @JsonProperty("accountId")   val accountId: Long = 0L,
    @field:NotNull
    @field:DecimalMin("0.01")                   @JsonProperty("amount")     val amount: BigDecimal = BigDecimal.ZERO,
    @JsonProperty("currency")                   val currency: String = "USD",
    @JsonProperty("description")                val description: String? = null
)

data class TransferRequest(
    @field:NotNull @JsonProperty("sourceAccountId") val sourceAccountId: Long = 0L,
    @field:NotNull @JsonProperty("targetAccountId") val targetAccountId: Long = 0L,
    @field:NotNull
    @field:DecimalMin("0.01")                       @JsonProperty("amount")        val amount: BigDecimal = BigDecimal.ZERO,
    @JsonProperty("currency")                        val currency: String = "USD",
    @JsonProperty("description")                     val description: String? = null
)

// ── Response DTO ──────────────────────────────────────────────────────────────

data class TransactionResponse(
    @JsonProperty("id")               val id: Long,
    @JsonProperty("transactionRef")   val transactionRef: String,
    @JsonProperty("sourceAccountId")  val sourceAccountId: Long,
    @JsonProperty("targetAccountId")  val targetAccountId: Long?,
    @JsonProperty("type")             val type: String,
    @JsonProperty("amount")           val amount: BigDecimal,
    @JsonProperty("currency")         val currency: String,
    @JsonProperty("status")           val status: String,
    @JsonProperty("description")      val description: String?,
    @JsonProperty("createdAt")        val createdAt: LocalDateTime?
) {
    companion object {
        fun fromDomain(txn: Transaction) = TransactionResponse(
            id              = txn.id ?: error("Transaction must have ID"),
            transactionRef  = txn.transactionRef,
            sourceAccountId = txn.sourceAccountId,
            targetAccountId = txn.targetAccountId,
            type            = txn.type.displayName,
            amount          = txn.amount,
            currency        = txn.currency,
            status          = txn.status.name,
            description     = txn.description,
            createdAt       = txn.createdAt
        )
    }
}
