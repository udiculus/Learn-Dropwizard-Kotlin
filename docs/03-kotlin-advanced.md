# Phase 3: Advanced Kotlin (Scope Functions, Coroutines, and Collections)

This guide introduces Kotlin's advanced capabilities: scope functions, async programming with Coroutines, and expressive collections APIs. We reference the implementations in [NotificationService.kt](file:///g:/Workplace/onboarding-insignia/notification-service/src/main/kotlin/com/learn/finance/notification/service/NotificationService.kt) and [TransactionValidator.kt](file:///g:/Workplace/onboarding-insignia/transaction-service/src/main/kotlin/com/learn/finance/transaction/service/TransactionValidator.kt).

---

## 1. Scope Functions

Kotlin provides five "scope functions": `let`, `run`, `with`, `apply`, and `also`. Their only purpose is to execute a block of code within the context of an object. 

They differ in two ways:
1.  How they reference the context object (via `this` or `it`).
2.  What they return (the context object itself or the lambda result).

Here is a summary table:

| Function | Context Object | Return Value | Common Use Case |
| :--- | :--- | :--- | :--- |
| **`let`** | `it` | Lambda result | Null-safe execution, local scoping, mappings |
| **`apply`** | `this` | Context object | Object configuration/initialization |
| **`also`** | `it` | Context object | Side effects, logging, secondary validations |
| **`run`** | `this` | Lambda result | Object configuration + computation |
| **`with`** | `this` | Lambda result | Calling multiple methods on a receiver |

### Examples in Code:

#### `apply` (Object configuration)
```kotlin
// Example from AccountCache: updates cache map, returns the snapshot itself
fun put(snapshot: AccountSnapshot): AccountSnapshot = snapshot.apply {
    cache[accountId] = this
    logger.debug("Cache updated...")
}
```

#### `also` (Side effect / logging)
```kotlin
// Example from CustomerService: returns the created customer after logging
return customer.copy(id = generatedId).also {
    logger.info("Created customer: id=${it.id}")
}
```

#### `let` (Null-safe check)
```kotlin
val email: String? = getEmail()
email?.let {
    sendEmail(it) // runs only if email is not null
}
```

#### `with` (Grouping operations)
```kotlin
// Example from AccountCache: returns map of statistics using cache as receiver
fun getStats(): Map<String, Any> = with(cache) {
    mapOf(
        "totalAccounts" to size,
        "activeAccounts" to values.count { it.isActive() }
    )
}
```

---

## 2. Kotlin Coroutines (Asynchronous Programming)

A **coroutine** is a lightweight thread of execution that can suspend and resume without blocking the actual OS thread. This allows you to write non-blocking asynchronous code in a simple, sequential style.

### Suspend Functions
A function marked with the `suspend` keyword can pause its execution and yield control back to the caller. Suspend functions can only be called from other suspend functions or inside a coroutine builder.

```kotlin
// Example from NotificationService.kt
suspend fun processEvent(eventType: String, accountId: Long, payload: Map<String, Any?>): Notification {
    // saving to DB suspended execution on IO dispatcher
    val saved = notificationRepo.insert(notification) 
    return saved
}
```

### Coroutine Builders
*   **`launch {}`:** "Fire-and-forget" builder. Starts a coroutine in the background and does not return any result (returns a `Job`).
*   **`async {}`:** Starts a coroutine and returns a `Deferred<T>` (similar to a Future), which you can call `.await()` on to retrieve the result.
*   **`runBlocking {}`:** Bridges the blocking code of standard functions/threads with coroutines. It blocks the current thread until all coroutines inside it complete. Primarily used in `main()` entry points or tests.

```kotlin
// Example from NotificationConsumer: runBlocking wraps the blocking Kafka poll loop
runBlocking {
    while (running) {
        val records = consumer.poll(Duration.ofSeconds(1))
        for (record in records) {
            // Launch a new, concurrent coroutine for each event
            coroutineScope.launch {
                processRecord(record.topic(), record.value())
            }
        }
    }
}
```

### Coroutine Context, Scope, and Dispatchers
*   **`CoroutineScope`:** Manages the lifecycle of coroutines. When a scope is cancelled, all coroutines created within it are also cancelled.
*   **`SupervisorJob()`:** A special job pattern. If one child coroutine fails, it does not cancel the entire scope (other children keep running).
*   **`Dispatchers.IO`:** Configures the coroutines to run on a thread pool optimized for blocking I/O operations (database writes, HTTP requests).
*   **`Dispatchers.Default`:** Configures coroutines for CPU-bound tasks (sorting, parsing).

```kotlin
// Example from NotificationService: setup service scope
private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

// Inside a function:
scope.launch {
    // This executes on the Dispatchers.IO thread pool
    auditLogRepo.insert(auditLog)
}
```

---

## 3. Collections API and Transformations

Kotlin provides a rich set of extension functions on standard collections (`List`, `Map`, `Set`), enabling declarative functional programming.

```kotlin
val list = listOf(1, 2, 3, 4, 5)

val evens = list.filter { it % 2 == 0 }           // [2, 4]
val doubled = list.map { it * 2 }                 // [2, 4, 6, 8, 10]
val total = list.fold(0) { acc, num -> acc + num } // 15
```

You can define custom extension functions on collections as well:

```kotlin
// Example from NotificationModels.kt
fun List<Notification>.countByStatus(status: NotificationStatus): Int =
    this.count { it.status == status }
```

---

## 4. Convenience Data Structures: Pairs and Triples

Sometimes you need to return multiple values from a function without creating a dedicated class. Kotlin provides `Pair` and `Triple` for this:

```kotlin
val (success, message) = Pair(true, "Transaction processed successfully")
println("Result: $success, Message: $message")
```

---

## 5. Custom Annotations

Kotlin allows you to define custom annotations for metadata or validation:

```kotlin
// Declaration
annotation class ValidAmount(val min: Double, val max: Double)

// Usage in TransactionValidator.kt
@ValidAmount(min = 0.01, max = 1_000_000.00)
fun validate(...) { ... }
```

---

## Next Steps
Now that you understand advanced Kotlin structures and coroutines, proceed to [Phase 4: Dropwizard Guide](file:///g:/Workplace/onboarding-insignia/docs/04-dropwizard-guide.md) to see how Kotlin interacts with a web service framework!
