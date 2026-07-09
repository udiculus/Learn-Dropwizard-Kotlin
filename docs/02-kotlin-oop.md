# Phase 2: Kotlin OOP and Features

This guide covers Kotlin's object-oriented programming model and safe coding patterns, referencing the implementations in [AccountService.kt](file:///g:/Workplace/onboarding-insignia/account-service/src/main/kotlin/com/learn/finance/account/service/AccountService.kt) and [TransactionValidator.kt](file:///g:/Workplace/onboarding-insignia/transaction-service/src/main/kotlin/com/learn/finance/transaction/service/TransactionValidator.kt).

---

## 1. Classes, Constructors, and Inheritance

### Final by Default
In Kotlin, classes are **final by default** (they cannot be subclassed). If you want to allow inheritance, you must explicitly mark a class as `open`:

```kotlin
open class BaseService { ... }
class AccountService : BaseService() { ... } // Allowed because BaseService is open
```

### Constructors and Initializer Blocks
Kotlin classes have a **primary constructor** as part of the class header. Properties can be declared and initialized directly in the primary constructor:

```kotlin
// Dependency injection via primary constructor properties
class CustomerService(
    private val customerDao: CustomerDao
) {
    // Member properties initialized directly or in init blocks
    private val logger = LoggerFactory.getLogger(CustomerService::class.java)
}
```

If you need initialization logic, use `init` blocks, which run in the order they appear in the class body:

```kotlin
class DatabaseConnection(val url: String) {
    init {
        println("Connecting to database: $url")
    }
}
```

---

## 2. Interfaces and Default Implementations

Interfaces in Kotlin can contain declarations of abstract methods, as well as method implementations. Unlike Java, you do not need a special keyword (like `default`) to provide an implementation in an interface:

```kotlin
interface LoggerProvider {
    val logTag: String // Abstract property

    fun logInfo(message: String) {
        println("[$logTag] INFO: $message") // Default implementation
    }
}
```

---

## 3. In-Depth Null Safety

Kotlin protects you from the dreaded `NullPointerException` (NPE) at compile-time using explicit nullable types and helper operators.

### Safe Call Operator (`?.`)
Executes the property/method access only if the receiver is non-null; otherwise, returns `null`.

```kotlin
val phone: String? = customer.phone
val length: Int? = phone?.length // returns length if phone is non-null, else null
```

### Elvis Operator (`?:`)
Provides a default fallback value if the left-hand side expression is null.

```kotlin
// Example from CustomerService.kt
val phoneText = customer.phone ?: "N/A"
```

The Elvis operator can also be used to return from a function or throw an exception:

```kotlin
// Idiomatic "find or throw" pattern in Kotlin
val customer = customerDao.findById(id) ?: throw NotFoundException("Customer not found")
```

### Non-Null Assertion Operator (`!!`)
Converts any value to a non-null type. If the value is null, it throws an NPE immediately. **Use this sparingly and only when you are 100% sure a value cannot be null.**

```kotlin
val nonNullPhone = customer.phone!! // Throws NullPointerException if phone is null
```

### Smart Casting
The Kotlin compiler is smart enough to cast types automatically once they've been checked:
1.  **Null-checks:** If a nullable variable is checked for null, the compiler smart-casts it to non-nullable within that scope.
2.  **Type checks (`is`):** Once you check a variable's type, it is cast automatically.

```kotlin
val customer: Customer? = findCustomer()
if (customer != null) {
    println(customer.firstName) // Inferred as non-null Customer automatically!
}
```

---

## 4. Extension Functions

Extension functions allow you to add new functions to a class without inheriting from it. Under the hood, they are resolved statically.

To declare an extension function, prefix its name with the receiver type (the class you are extending). Inside the extension function, `this` refers to the receiver object.

```kotlin
// Example from AccountService.kt
fun Account.toLogString(): String =
    "Account[id=$id, number=$accountNumber, balance=$balance]"

// Extension function on a collection type
fun List<Account>.totalBalance(): BigDecimal =
    fold(BigDecimal.ZERO) { acc, account -> acc + account.balance }
```

You can call these extension functions as if they were member methods of the class:

```kotlin
val account = accountDao.findById(1L)
println(account.toLogString())

val accounts: List<Account> = accountDao.findAll()
val total = accounts.totalBalance()
```

---

## 5. Infix Functions

Infix functions allow you to call member or extension functions without using parentheses or dots, creating a DSL-like reading style. An infix function must:
*   Be a member function or extension function.
*   Have exactly one parameter.
*   Be marked with the `infix` keyword.

```kotlin
// Example from AccountModels.kt
infix fun hasSufficientFunds(amount: BigDecimal): Boolean = balance >= amount

// Usage:
if (account hasSufficientFunds withdrawAmount) {
    // transact
}
```

---

## 6. Generics

Kotlin supports generics similar to Java to enforce type safety at compile-time:

```kotlin
// A generic repository interface
interface Repository<T, ID> {
    fun findById(id: ID): T?
    fun save(entity: T): T
}
```

Unlike Java, Kotlin has advanced generic concepts such as declaration-site variance (using the `out` and `in` modifiers) to make collections cleaner to work with, but the basic usage remains straightforward and intuitive.

---

## Next Steps
Now that you have mastered OOP, safe calls, and extensions, proceed to [Phase 3: Kotlin Advanced](file:///g:/Workplace/onboarding-insignia/docs/03-kotlin-advanced.md) to explore scope functions, coroutines, and custom annotations!
