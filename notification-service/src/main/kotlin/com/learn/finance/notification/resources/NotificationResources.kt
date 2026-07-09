package com.learn.finance.notification.resources

import com.learn.finance.notification.model.AuditLog
import com.learn.finance.notification.model.Notification
import com.learn.finance.notification.service.NotificationService
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.DefaultValue
import jakarta.ws.rs.GET
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import kotlinx.coroutines.runBlocking

@Path("/api/notifications")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class NotificationResource(private val notificationService: NotificationService) {

    @GET
    fun getByAccount(
        @QueryParam("accountId") accountId: Long,
        @QueryParam("limit")  @DefaultValue("20") limit: Int,
        @QueryParam("offset") @DefaultValue("0")  offset: Int
    ): Response {
        val notifications = notificationService.getByAccountId(accountId, limit, offset)
        return Response.ok(notifications.map { it.toResponse() }).build()
    }

    @GET @Path("/{id}")
    fun getById(@PathParam("id") id: String): Response {
        val notification = notificationService.getById(id)
            ?: return Response.status(404).entity(mapOf("error" to "Notification not found")).build()
        return Response.ok(notification.toResponse()).build()
    }

    /**
     * KOTLIN COROUTINES: JAX-RS is blocking — use runBlocking to call suspend functions.
     */
    @PUT @Path("/{id}/read")
    fun markAsRead(@PathParam("id") id: String): Response = runBlocking {
        val updated = notificationService.markAsRead(id)
        if (updated) Response.ok(mapOf("message" to "Marked as read")).build()
        else Response.status(404).entity(mapOf("error" to "Notification not found")).build()
    }

    @PUT @Path("/account/{accountId}/read-all")
    fun markAllRead(@PathParam("accountId") accountId: Long): Response = runBlocking {
        val count = notificationService.markAllReadForAccount(accountId)
        Response.ok(mapOf("markedRead" to count)).build()
    }

    @DELETE @Path("/{id}")
    fun delete(@PathParam("id") id: String): Response {
        val deleted = notificationService.deleteById(id)
        return if (deleted) Response.noContent().build()
        else Response.status(404).entity(mapOf("error" to "Not found")).build()
    }

    @GET @Path("/account/{accountId}/unread-count")
    fun unreadCount(@PathParam("accountId") accountId: Long): Response {
        val count = notificationService.getUnreadCount(accountId)
        return Response.ok(mapOf("unreadCount" to count)).build()
    }

    private fun Notification.toResponse() = mapOf(
        "id"        to id.toString(),
        "eventType" to eventType,
        "accountId" to accountId,
        "message"   to message,
        "status"    to status.name,
        "createdAt" to createdAt.toString()
    )
}

@Path("/api/audit-logs")
@Produces(MediaType.APPLICATION_JSON)
class AuditLogResource(private val notificationService: NotificationService) {

    @GET
    fun getAll(
        @QueryParam("limit")  @DefaultValue("50") limit: Int,
        @QueryParam("offset") @DefaultValue("0")  offset: Int
    ): Response {
        val logs = notificationService.getAuditLogs(limit, offset)
        return Response.ok(logs.map { it.toResponse() }).build()
    }

    private fun AuditLog.toResponse() = mapOf(
        "id"         to id.toString(),
        "service"    to service,
        "action"     to action,
        "entityType" to entityType,
        "entityId"   to entityId,
        "timestamp"  to timestamp.toString()
    )
}
