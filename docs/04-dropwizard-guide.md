# Phase 4: Dropwizard Guide (Web Services Framework)

This guide covers Dropwizard, the lightweight framework used to develop the REST services in this onboarding codebase. We reference implementations in [AccountApplication.kt](file:///g:/Workplace/onboarding-insignia/account-service/src/main/kotlin/com/learn/finance/account/AccountApplication.kt), [AccountConfiguration.kt](file:///g:/Workplace/onboarding-insignia/account-service/src/main/kotlin/com/learn/finance/account/AccountConfiguration.kt), and the resource files.

---

## 1. What is Dropwizard?

Dropwizard is an opinionated framework that bundles stable, mature Java libraries into a single, cohesive package for building high-performance RESTful web services. Its core components include:

*   **Jetty:** Embedded HTTP servlet container (no need for external Tomcat/Wildfly servers).
*   **Jersey:** JAX-RS implementation for building RESTful web services.
*   **Jackson:** JSON serializer/deserializer.
*   **Metrics:** Monitoring/metrics tracking.
*   **JDBI:** Easy SQL mapping library.

---

## 2. Dropwizard Configuration

Dropwizard maps a YAML configuration file to a subclass of `Configuration`.

### Config Class Binding
We use Jackson annotations to bind YAML keys to Kotlin variables, along with Jakarta Validation annotations to enforce rules during startup.

```kotlin
// Example from AccountConfiguration.kt
class AccountConfiguration : Configuration() {

    @Valid
    @NotNull
    @JsonProperty("database")
    val database: DataSourceFactory = DataSourceFactory()

    @Valid
    @NotNull
    @JsonProperty("kafka")
    val kafka: KafkaConfiguration = KafkaConfiguration()
}
```

### Environment Variable Substitution
To allow configuring Docker containers through environment variables, we initialize a `SubstitutingSourceProvider` in the bootstrap phase. This replaces `${ENV_VAR:-default}` place-holders in `config.yml` with host environment variables.

```kotlin
override fun initialize(bootstrap: Bootstrap<AccountConfiguration>) {
    bootstrap.configurationSourceProvider = SubstitutingSourceProvider(
        bootstrap.configurationSourceProvider,
        EnvironmentVariableSubstitutor(false) // false means do not throw error if var is missing
    )
}
```

---

## 3. Dropwizard Application Lifecycle

Every Dropwizard service has an application class that extends `Application<T>` and provides the main entry point:

```kotlin
class AccountApplication : Application<AccountConfiguration>() {

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            // Run the application
            AccountApplication().run(*args)
        }
    }

    override fun getName(): String = "account-service"

    override fun initialize(bootstrap: Bootstrap<AccountConfiguration>) {
        // 1. Enable Jackson Kotlin Module (crucial for data class serialization)
        bootstrap.objectMapper.registerModule(KotlinModule.Builder().build())
    }

    override fun run(configuration: AccountConfiguration, environment: Environment) {
        // 2. Wire up databases, producers, and consumers
        // 3. Register Jersey resources
        // 4. Register Health Checks
    }
}
```

---

## 4. JAX-RS REST Resources

REST endpoints are declared as Jersey resources. Annotations define the URLs, methods, and formatting:

```kotlin
// Example inspired by AccountResource.kt
@Path("/api/accounts")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class AccountResource(private val accountService: AccountService) {

    @GET
    @Path("/{id}")
    fun getAccount(@PathParam("id") id: Long): Response {
        val account = accountService.getAccount(id)
        return Response.ok(account).build()
    }

    @POST
    fun createAccount(request: CreateAccountRequest): Response {
        val created = accountService.createAccount(request)
        return Response.status(Response.Status.CREATED).entity(created).build()
    }
}
```

### Exception Mapping
When exceptions occur in business logic, they can be mapped to HTTP status codes by throwing standard JAX-RS exceptions:
*   `jakarta.ws.rs.NotFoundException` $\rightarrow$ returns `404 Not Found`.
*   `jakarta.ws.rs.BadRequestException` $\rightarrow$ returns `400 Bad Request`.
*   `jakarta.ws.rs.InternalServerErrorException` $\rightarrow$ returns `500 Internal Server Error`.

---

## 5. Managed Objects (Lifecycle management)

If you have background threads, connection pools, or consumer loops (like Kafka Consumers or Producers), they must start when the application starts and stop cleanly when it shuts down. 

Dropwizard handles this via the `Managed` interface. Simply implement `Managed` and register your object in the environment lifecycle:

```kotlin
// Example from AccountEventProducer.kt / NotificationConsumer.kt
class AccountEventProducer(...) : Managed {

    override fun start() {
        // Initialize connections/threads
        producer = KafkaProducer(props)
    }

    override fun stop() {
        // Close resources gracefully
        producer.close()
    }
}

// Inside Application.run():
val eventProducer = AccountEventProducer(configuration.kafka)
environment.lifecycle().manage(eventProducer) // Registers the managed lifecycle object
```

---

## 6. Health Checks

Health checks are run periodically by the admin port (`8081` / `8083` / `8085` depending on service) to determine if a service is healthy.

Write a health check by extending `HealthCheck` from Codahale Metrics and implementing `check()`:

```kotlin
// Example from DatabaseHealthCheck.kt
class DatabaseHealthCheck(private val jdbi: Jdbi) : HealthCheck() {
    override fun check(): Result {
        return try {
            jdbi.withHandle<Unit, Exception> { handle ->
                handle.execute("SELECT 1")
            }
            Result.healthy("MySQL connection is healthy")
        } catch (e: Exception) {
            Result.unhealthy("Failed to query database: ${e.message}")
        }
    }
}

// Inside Application.run():
environment.healthChecks().register("database", DatabaseHealthCheck(jdbi))
```

---

## Next Steps
Now that you understand the API layer and application setup, proceed to [Phase 5: Kafka Guide](file:///g:/Workplace/onboarding-insignia/docs/05-kafka-guide.md) to explore event-driven services!
