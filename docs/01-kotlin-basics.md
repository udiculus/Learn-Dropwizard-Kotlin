# Phase 1: Kotlin Basics

Welcome to the foundation of your Kotlin learning path! Kotlin is a modern, statically typed language designed to be fully interoperable with Java, but with a major focus on safety, conciseness, and developer happiness.

This guide covers the fundamental building blocks of Kotlin, referencing the models found in [AccountModels.kt](file:///g:/Workplace/onboarding-insignia/account-service/src/main/kotlin/com/learn/finance/account/model/AccountModels.kt).

---

## 1. Variables and Type Inference

In Kotlin, variable declarations start with either `val` (value) or `var` (variable):

*   **`val` (Read-only / Immutable):** Assigned once and cannot be reassigned. Similar to `final` in Java. **Always prefer `val` by default.**
*   **`var` (Mutable):** Can be reassigned.

```kotlin
val id: Long = 42L         // Read-only
// id = 43L                // Compiler Error!

var status: String = "ACTIVE"
status = "INACTIVE"        // Allowed
```

### Type Inference
Kotlin is statically typed, but you don't always have to write the type explicitly. The compiler automatically infers the type of a variable from its initializer expression:

```kotlin
val name = "John"          // Compiler infers String
val balance = 100.0        // Compiler infers Double
```

---

## 2. Null Safety basics

Null safety is one of Kotlin's headline features. The type system distinguishes between types that can hold `null` and those that cannot.

*   **Non-nullable types:** By default, variables cannot be null.
*   **Nullable types:** Appending `?` to a type allows it to hold `null`.

```kotlin
val email: String = "john@example.com"
// val email: String = null             // Compiler Error!

val phone: String? = null               // Allowed
```

We will dive deeper into advanced null safety operators in [Phase 2: Kotlin OOP](file:///g:/Workplace/onboarding-insignia/docs/02-kotlin-oop.md).

---

## 3. Control Flow

### `if` as an Expression
In Kotlin, `if` is an expression, meaning it returns a value. Because of this, Kotlin has no ternary operator (`condition ? then : else`).

```kotlin
val statusText = if (isActive) "Active Account" else "Disabled Account"
```

### The `when` Expression
The `when` expression replaces Java's `switch` statement and is much more flexible. Like `if`, it can be used as an expression (returns a value) or a statement.

```kotlin
val statusLabel = when (value.uppercase()) {
    "ACTIVE"   -> "Account is Active"
    "INACTIVE" -> "Account is Inactive"
    "CLOSED"   -> "Account is Closed"
    else       -> "Unknown Status"
}
```

---

## 4. Functions

Kotlin functions are declared using the `fun` keyword.

### Basic Function and Single-Expression Functions
For simple functions that return a single expression, you can omit the curly braces and return type, using the `=` assignment operator:

```kotlin
// Standard block syntax
fun isCheckingAccount(type: AccountType): Boolean {
    return type == AccountType.CHECKING
}

// Single-expression syntax (inferred return type)
fun isCheckingAccountSingle(type: AccountType) = type == AccountType.CHECKING
```

### Default Arguments and Named Parameters
Kotlin allows functions to have default parameter values. This reduces the need for method overloading.

```kotlin
data class Account(
    val balance: BigDecimal = BigDecimal.ZERO, // Default parameter
    val currency: String = "USD"
)
```

When calling functions with default arguments, you can also use **named parameters** to make call sites readable and avoid positional errors:

```kotlin
val myAccount = Account(
    currency = "EUR",
    balance = BigDecimal("100.50")
)
```

---

## 5. Classes and Structured Data Types

Kotlin features several specialized class types to reduce boilerplate.

### Data Classes
If you create a class to hold data, mark it with `data`. The compiler automatically generates:
*   `equals()` and `hashCode()` based on all properties.
*   A clean `toString()` listing all properties.
*   A `copy()` function to easily clone objects with modifications.
*   Destructuring declarations (`component1()`, `component2()`, etc.).

```kotlin
// Example from AccountModels.kt
data class Customer(
    val id: Long? = null,
    val firstName: String,
    val lastName: String,
    val email: String,
    val phone: String? = null
)
```

#### The `copy()` Function
Since properties in data classes are typically `val` (immutable), you use the `copy()` function to create a new instance with some modified fields:

```kotlin
val original = Customer(id = 1L, firstName = "John", lastName = "Doe", email = "john@example.com")
val updated = original.copy(firstName = "Jonathan") // ID, lastName, and email remain unchanged
```

### Enum Classes
Like Java, `enum class` represents a fixed set of named constants. However, in Kotlin, they can also define member properties and functions:

```kotlin
// Example from AccountModels.kt
enum class AccountType(val displayName: String) {
    SAVINGS("Savings Account"),
    CHECKING("Checking Account");

    fun isEligibleForOverdraft(): Boolean = this == CHECKING
}
```

### Sealed Classes
A `sealed class` defines a closed class hierarchy. All subclasses must be declared in the same file.
Unlike enums, subclasses of a sealed class can be either `object` singletons or regular `class`es that contain different properties.

When matching against a `sealed class` in a `when` expression, the compiler enforces **exhaustive checks** (you must cover every subclass, otherwise it fails to compile):

```kotlin
// Example from AccountModels.kt
sealed class AccountStatus {
    object Active    : AccountStatus()
    object Inactive  : AccountStatus()
    object Closed    : AccountStatus()
    object Frozen    : AccountStatus()

    fun canTransact(): Boolean = when (this) {
        is Active   -> true
        is Inactive -> false
        is Closed   -> false
        is Frozen   -> false
    }
}
```

---

## 6. Companion Objects

Kotlin does not have a `static` keyword for class members. Instead, you use a `companion object`. Members defined inside a companion object can be called using the class name, similar to static methods in Java:

```kotlin
// Example from AccountModels.kt
data class Account(...) {
    companion object {
        fun generateAccountNumber(customerId: Long, type: AccountType): String {
            return "ACC-$customerId-${System.currentTimeMillis() % 1000}"
        }
    }
}

// Usage:
val newNumber = Account.generateAccountNumber(12345L, AccountType.SAVINGS)
```

---

## Next Steps
Now that you have learned the basics, proceed to [Phase 2: Kotlin OOP](file:///g:/Workplace/onboarding-insignia/docs/02-kotlin-oop.md) to explore constructors, interfaces, Null Safety operators, and Extension Functions!
