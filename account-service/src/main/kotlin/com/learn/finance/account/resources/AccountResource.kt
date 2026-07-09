package com.learn.finance.account.resources

import com.learn.finance.account.api.AccountResponse
import com.learn.finance.account.api.CreateAccountRequest
import com.learn.finance.account.api.CreateCustomerRequest
import com.learn.finance.account.api.CustomerResponse
import com.learn.finance.account.api.ErrorResponse
import com.learn.finance.account.api.UpdateAccountStatusRequest
import com.learn.finance.account.api.UpdateCustomerRequest
import com.learn.finance.account.service.AccountService
import com.learn.finance.account.service.CustomerService
import jakarta.validation.Valid
import jakarta.ws.rs.BadRequestException
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.DefaultValue
import jakarta.ws.rs.GET
import jakarta.ws.rs.NotFoundException
import jakarta.ws.rs.POST
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * CustomerResource — REST API for Customer operations
 *
 * DROPWIZARD / HTTP CONCEPTS DEMONSTRATED:
 *  • @Path        — URL path mapping
 *  • @GET         — Retrieve resource(s)
 *  • @POST        — Create a new resource
 *  • @PUT         — Update an existing resource (full or partial)
 *  • @DELETE      — Remove a resource
 *  • @PathParam   — Bind URL path segments to method parameters
 *  • @QueryParam  — Bind URL query parameters (?limit=20&offset=0)
 *  • @Valid       — Trigger Jakarta Bean Validation on request bodies
 *  • Response     — Builder for HTTP status codes and payloads
 *  • @Produces    — Declares the response media type (JSON)
 *  • @Consumes    — Declares the accepted request media type (JSON)
 *
 * KOTLIN CONCEPTS:
 *  • map() transform: domain → DTO without exposing internals
 *  • try/catch with when expression for exception routing
 * ─────────────────────────────────────────────────────────────────────────────
 */
@Path("/api/customers")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class CustomerResource(
    private val customerService: CustomerService
) {
    private val logger = LoggerFactory.getLogger(CustomerResource::class.java)

    /**
     * HTTP GET /api/customers
     * Returns a paginated list of customers.
     *
     * DROPWIZARD: @QueryParam with @DefaultValue sets defaults for optional params.
     */
    @GET
    fun listCustomers(
        @QueryParam("limit")  @DefaultValue("20") limit: Int,
        @QueryParam("offset") @DefaultValue("0")  offset: Int
    ): Response {
        val customers = customerService.getAll(limit, offset)
            // KOTLIN CONCEPT: map transforms each element in the list
            .map { CustomerResponse.fromDomain(it) }

        return Response.ok(customers).build()
    }

    /**
     * HTTP GET /api/customers/{id}
     * Returns a single customer by ID, or 404 if not found.
     *
     * DROPWIZARD: @PathParam binds the {id} segment to the `id` parameter.
     */
    @GET
    @Path("/{id}")
    fun getCustomer(@PathParam("id") id: Long): Response {
        return try {
            val customer = customerService.getById(id)
            Response.ok(CustomerResponse.fromDomain(customer)).build()
        } catch (ex: NotFoundException) {
            Response.status(Response.Status.NOT_FOUND)
                .entity(ErrorResponse(404, ex.message ?: "Not found"))
                .build()
        }
    }

    /**
     * HTTP POST /api/customers
     * Creates a new customer. Returns 201 Created with the new resource.
     *
     * DROPWIZARD: @Valid triggers validation on the request body.
     */
    @POST
    fun createCustomer(@Valid request: CreateCustomerRequest): Response {
        return try {
            val customer = customerService.createCustomer(request)
            Response.status(Response.Status.CREATED)
                .entity(CustomerResponse.fromDomain(customer))
                .build()
        } catch (ex: BadRequestException) {
            Response.status(Response.Status.BAD_REQUEST)
                .entity(ErrorResponse(400, ex.message ?: "Bad request"))
                .build()
        }
    }

    /**
     * HTTP PUT /api/customers/{id}
     * Updates an existing customer. Returns 200 with the updated resource.
     */
    @PUT
    @Path("/{id}")
    fun updateCustomer(
        @PathParam("id") id: Long,
        @Valid request: UpdateCustomerRequest
    ): Response {
        return try {
            val updated = customerService.updateCustomer(id, request)
            Response.ok(CustomerResponse.fromDomain(updated)).build()
        } catch (ex: NotFoundException) {
            Response.status(Response.Status.NOT_FOUND)
                .entity(ErrorResponse(404, ex.message ?: "Not found"))
                .build()
        }
    }

    /**
     * HTTP DELETE /api/customers/{id}
     * Soft-deletes (deactivates) a customer. Returns 204 No Content.
     */
    @DELETE
    @Path("/{id}")
    fun deactivateCustomer(@PathParam("id") id: Long): Response {
        return try {
            customerService.deactivateCustomer(id)
            Response.noContent().build()
        } catch (ex: NotFoundException) {
            Response.status(Response.Status.NOT_FOUND)
                .entity(ErrorResponse(404, ex.message ?: "Not found"))
                .build()
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────

/**
 * AccountResource — REST API for Account operations
 */
@Path("/api/accounts")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class AccountResource(
    private val accountService: AccountService
) {
    private val logger = LoggerFactory.getLogger(AccountResource::class.java)

    /**
     * HTTP GET /api/accounts/{id}
     */
    @GET
    @Path("/{id}")
    fun getAccount(@PathParam("id") id: Long): Response {
        return try {
            val account = accountService.getAccount(id)
            Response.ok(AccountResponse.fromDomain(account)).build()
        } catch (ex: NotFoundException) {
            Response.status(Response.Status.NOT_FOUND)
                .entity(ErrorResponse(404, ex.message ?: "Not found"))
                .build()
        }
    }

    /**
     * HTTP GET /api/accounts?customerId={id}
     * Returns all accounts for a given customer.
     */
    @GET
    fun getAccountsByCustomer(@QueryParam("customerId") customerId: Long): Response {
        return try {
            val accounts = accountService.getAccountsByCustomer(customerId)
                .map { AccountResponse.fromDomain(it) }
            Response.ok(accounts).build()
        } catch (ex: NotFoundException) {
            Response.status(Response.Status.NOT_FOUND)
                .entity(ErrorResponse(404, ex.message ?: "Not found"))
                .build()
        }
    }

    /**
     * HTTP POST /api/accounts
     * Creates a new bank account.
     */
    @POST
    fun createAccount(@Valid request: CreateAccountRequest): Response {
        return try {
            val account = accountService.createAccount(request)
            Response.status(Response.Status.CREATED)
                .entity(AccountResponse.fromDomain(account))
                .build()
        } catch (ex: BadRequestException) {
            Response.status(Response.Status.BAD_REQUEST)
                .entity(ErrorResponse(400, ex.message ?: "Bad request"))
                .build()
        } catch (ex: NotFoundException) {
            Response.status(Response.Status.NOT_FOUND)
                .entity(ErrorResponse(404, ex.message ?: "Customer not found"))
                .build()
        }
    }

    /**
     * HTTP PUT /api/accounts/{id}/status
     * Updates the status of an account (ACTIVE, FROZEN, INACTIVE).
     */
    @PUT
    @Path("/{id}/status")
    fun updateStatus(
        @PathParam("id") id: Long,
        @Valid request: UpdateAccountStatusRequest
    ): Response {
        return try {
            val account = accountService.updateAccountStatus(id, request.status ?: "")
            Response.ok(AccountResponse.fromDomain(account)).build()
        } catch (ex: BadRequestException) {
            Response.status(Response.Status.BAD_REQUEST)
                .entity(ErrorResponse(400, ex.message ?: "Bad request"))
                .build()
        }
    }

    /**
     * HTTP DELETE /api/accounts/{id}
     * Closes a bank account (only if balance is zero).
     */
    @DELETE
    @Path("/{id}")
    fun closeAccount(@PathParam("id") id: Long): Response {
        return try {
            accountService.closeAccount(id)
            Response.noContent().build()
        } catch (ex: BadRequestException) {
            Response.status(Response.Status.BAD_REQUEST)
                .entity(ErrorResponse(400, ex.message ?: "Bad request"))
                .build()
        } catch (ex: NotFoundException) {
            Response.status(Response.Status.NOT_FOUND)
                .entity(ErrorResponse(404, ex.message ?: "Not found"))
                .build()
        }
    }
}
