package com.learn.finance.transaction.resources

import com.learn.finance.transaction.api.DepositRequest
import com.learn.finance.transaction.api.TransactionResponse
import com.learn.finance.transaction.api.TransferRequest
import com.learn.finance.transaction.api.WithdrawalRequest
import com.learn.finance.transaction.service.TransactionService
import jakarta.validation.Valid
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DefaultValue
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

@Path("/api/transactions")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class TransactionResource(private val transactionService: TransactionService) {

    @POST @Path("/deposit")
    fun deposit(@Valid request: DepositRequest): Response = try {
        val txn = transactionService.deposit(request.copy(accountId = request.accountId ?: 0L))
        Response.status(Response.Status.CREATED).entity(TransactionResponse.fromDomain(txn)).build()
    } catch (ex: jakarta.ws.rs.BadRequestException) {
        Response.status(400).entity(mapOf("error" to ex.message)).build()
    } catch (ex: jakarta.ws.rs.NotFoundException) {
        Response.status(404).entity(mapOf("error" to ex.message)).build()
    }

    @POST @Path("/withdraw")
    fun withdraw(@Valid request: WithdrawalRequest): Response = try {
        val txn = transactionService.withdraw(request.copy(accountId = request.accountId ?: 0L))
        Response.status(Response.Status.CREATED).entity(TransactionResponse.fromDomain(txn)).build()
    } catch (ex: jakarta.ws.rs.BadRequestException) {
        Response.status(400).entity(mapOf("error" to ex.message)).build()
    }

    @POST @Path("/transfer")
    fun transfer(@Valid request: TransferRequest): Response = try {
        val txn = transactionService.transfer(request)
        Response.status(Response.Status.CREATED).entity(TransactionResponse.fromDomain(txn)).build()
    } catch (ex: jakarta.ws.rs.BadRequestException) {
        Response.status(400).entity(mapOf("error" to ex.message)).build()
    }

    @GET @Path("/history/{accountId}")
    fun history(
        @PathParam("accountId") accountId: Long,
        @QueryParam("limit")  @DefaultValue("20") limit: Int,
        @QueryParam("offset") @DefaultValue("0")  offset: Int
    ): Response {
        val transactions = transactionService.getHistory(accountId, limit, offset)
            .map { TransactionResponse.fromDomain(it) }
        return Response.ok(transactions).build()
    }
}
