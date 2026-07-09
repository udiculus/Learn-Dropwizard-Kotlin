# Phase 8: Testing Guide (Unit and Mockito Testing)

This guide covers Kotlin testing strategies using JUnit 5 and Mockito. We reference implementations in [CustomerServiceTest.kt](file:///g:/Workplace/onboarding-insignia/account-service/src/test/kotlin/com/learn/finance/account/service/CustomerServiceTest.kt) and [NotificationServiceTest.kt](file:///g:/Workplace/onboarding-insignia/notification-service/src/test/kotlin/com/learn/finance/notification/service/NotificationServiceTest.kt).

---

## 1. JUnit 5 Foundations

JUnit 5 is the standard test runner for this project.

### Core Annotations
*   **`@Test`:** Marks a method as a test.
*   **`@BeforeEach`:** Runs a setup block before every test to reset state or mocks.
*   **`@Nested`:** Allows grouping related tests inside inner classes. Mocks and setup in the outer class are shared by nested classes.
*   **`@Tag`:** Categorizes tests (e.g., `@Tag("unit")` allows running only unit tests via command `mvn test -Dgroups=unit`).
*   **`@DisplayName`:** Defines a human-readable name for tests in execution reports.

### Kotlin-Style Test Method Names
Kotlin allows writing descriptive test method names using spaces wrapped in backticks. This replaces the old camelCase or snake_case styles:

```kotlin
@Test
@DisplayName("should create customer and return with generated ID")
fun `createCustomer - happy path`() {
    // Arrange -> Act -> Assert
}
```

### Assertions
JUnit 5 provides static assertions. In Kotlin, use `assertAll` to run multiple assertions inside a lambda so that all checks execute even if one of them fails:

```kotlin
// Check multiple properties on the result object
assertAll("created customer",
    { assertNotNull(result.id) },
    { assertEquals("John", result.firstName) },
    { assertEquals("john@example.com", result.email) }
)
```

To assert that a block throws a specific exception, use `assertThrows`:

```kotlin
assertThrows(jakarta.ws.rs.BadRequestException::class.java) {
    customerService.createCustomer(requestWithDuplicateEmail)
}
```

---

## 2. Mocking with Mockito-Kotlin

We use the `mockito-kotlin` wrapper library to provide a idiomatic Kotlin DSL on top of Mockito.

### Mock Creation
Create a mock using the inline `mock()` function or by using Mockito annotations:

```kotlin
// Option A: Function declaration
private val customerDao: CustomerDao = mock()

// Option B: Annotation-based (requires @ExtendWith(MockitoExtension::class) on the test class)
@Mock private lateinit var notificationRepo: NotificationRepository
@InjectMocks private lateinit var notificationService: NotificationService
```

### Stubbing (`whenever`)
We specify stub behaviors using the `whenever().thenReturn()` or `whenever().thenAnswer()` syntax:

```kotlin
whenever(customerDao.existsByEmail("john@example.com")).thenReturn(0)

// Stubbing with argument matchers
whenever(customerDao.insert(any(), any(), any(), any(), any())).thenReturn(42L)
```

### Verification (`verify`)
Verify that specific interactions occurred on the mock object:

```kotlin
// Verify that existsByEmail was queried with exact parameter
verify(customerDao).existsByEmail("john@example.com")

// Verify that a method was NEVER called
verify(customerDao, never()).insert(any(), any(), any(), any(), any())
```

Use `argumentCaptor<T>()` to capture parameters passed to a mock for further assertions:

```kotlin
val captor = argumentCaptor<Notification>()
verify(notificationRepo).insert(captor.capture())

assertEquals("Your account has been created successfully.", captor.firstValue.message)
```

---

## 3. Testing Kotlin Coroutines

Testing asynchronous coroutines or `suspend` functions requires special handling. 

### `runTest {}`
Instead of using thread sleeps or blocking calls, wrap the test body in the `runTest {}` builder provided by `kotlinx-coroutines-test`. This runs the coroutines synchronously in the test runner thread, skipping any delays immediately and preventing tests from being flaky:

```kotlin
// Example from NotificationServiceTest.kt
@Test
fun `processEvent - happy path`() = runTest {
    // Arrange
    val savedNotification = aNotification()
    whenever(notificationRepo.insert(any())).thenReturn(savedNotification)

    // Act - calling a suspend function inside runTest is allowed
    val result = notificationService.processEvent(
        eventType = "TRANSACTION_COMPLETED",
        accountId = 42L,
        payload   = mapOf("type" to "DEPOSIT")
    )

    // Assert
    assertNotNull(result)
    verify(notificationRepo).insert(any())
}
```

### Mocking Suspend Functions
Mockito-Kotlin supports mocking suspend functions out of the box. You stub them using the same `whenever` syntax as regular functions; the library takes care of resolving the suspension sequence behind the scenes.

---

## 4. Testing Kotlin Language Constructs

*   **Extension Functions:** Extension functions are tested like standard utility methods. Call them directly on test instances and verify the return value.
*   **Data Class Equality:** Kotlin `==` tests structural equality (calling `equals()`), and `===` tests referential identity (object instances). We assert value equality of immutable data classes:

```kotlin
val c1 = aCustomer(id = 1L)
val c2 = aCustomer(id = 1L)

assertTrue(c1 == c2)  // Structural equality passes
assertFalse(c1 === c2) // Referential identity fails (separate instances)
```

---

## Next Steps
Congratulations, you have completed the learning path! You are now ready to build, run, and write tests for these services!
