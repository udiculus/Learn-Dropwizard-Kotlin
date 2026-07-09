# Phase 7: MongoDB Guide (NoSQL Persistence)

This guide covers MongoDB (NoSQL) document-based storage and query operations using the MongoDB Sync Driver. We reference implementations in [init-mongo.js](file:///g:/Workplace/onboarding-insignia/db/migration/init-mongo.js) and [NotificationRepository.kt](file:///g:/Workplace/onboarding-insignia/notification-service/src/main/kotlin/com/learn/finance/notification/db/NotificationRepository.kt).

---

## 1. MongoDB Core Concepts

MongoDB is a document-oriented database classified under NoSQL. 

*   **Document:** Instead of rows, data is stored in flexible, JSON-like structures (internally serialized as BSON - Binary JSON).
*   **Collection:** A group of documents (equivalent to a relational table). Collections do not enforce a strict system-wide schema, though validation rules can be configured.
*   **ObjectId:** A unique 12-byte identifier used as the primary key (`_id`) for every document.
*   **Indexes:** Used to optimize query performance (e.g. indexing on search fields).

---

## 2. Seeding and Initialization

We configure our MongoDB instances during container startup via initialization scripts. In `docker-compose.yml`, the script `./db/migration/init-mongo.js` is mounted into `/docker-entrypoint-initdb.d/` as read-only. 

```yaml
# In docker-compose.yml
mongodb:
  image: mongo:7.0
  volumes:
    - mongo-data:/data/db
    - ./db/migration/init-mongo.js:/docker-entrypoint-initdb.d/init-mongo.js:ro
```

When MongoDB initializes, it executes this script to:
1.  Create databases and dedicated application users.
2.  Set up collections with JSON Schema validation rules.
3.  Build indexes on fields like `accountId`, `status`, and `createdAt` to optimize queries.

---

## 3. MongoDB CRUD Operations in Kotlin

In the `notification-service`, we interact with MongoDB using the synchronous database client driver:

```kotlin
// Example from NotificationRepository.kt
class NotificationRepository(private val database: MongoDatabase) {
    private val collection = database.getCollection("notifications")
}
```

### Create (Insert Document)
To insert a document, convert your Kotlin object to a `Document` map and call `insertOne()`:

```kotlin
fun insert(notification: Notification): Notification {
    val doc = notification.toDocument() // helper mapping function
    collection.insertOne(doc)
    return notification
}
```

### Read (Query & Filter Documents)
We query documents using the helper `Filters` API. For performance, we also sort results, skip offsets, and enforce document limits (pagination):

*   `Filters.eq("field", value)`: Equality filter.
*   `Filters.and(f1, f2)`: Logical AND.
*   `Sorts.descending("createdAt")`: Orders results.
*   `skip(skip)` & `limit(limit)`: Implements paging limits.

```kotlin
fun findByAccountId(accountId: Long, limit: Int = 20, skip: Int = 0): List<Notification> =
    collection
        .find(Filters.eq("accountId", accountId))
        .sort(Sorts.descending("createdAt"))
        .skip(skip)
        .limit(limit) // document limit enforcement
        .map { it.toNotification() } // helper to map BSON to Kotlin object
        .toList()
```

### Update (Partial Updates)
We use `updateOne()` with the `$set` operator via the `Updates` utility to perform **partial updates** without replacing the entire document:

```kotlin
fun markAsRead(id: ObjectId): Boolean {
    val result = collection.updateOne(
        Filters.eq("_id", id),
        Updates.combine(
            Updates.set("status", NotificationStatus.READ.name),
            Updates.set("updatedAt", Instant.now().toString())
        )
    )
    return result.modifiedCount > 0
}
```

### Multi-Update
To update multiple records matching a query filter at once, use `updateMany()`:

```kotlin
fun markAllReadForAccount(accountId: Long): Long {
    val result = collection.updateMany(
        Filters.and(
            Filters.eq("accountId", accountId),
            Filters.eq("status", NotificationStatus.UNREAD.name)
        ),
        Updates.set("status", NotificationStatus.READ.name)
    )
    return result.modifiedCount
}
```

### Upsert (Insert-or-Replace)
To update an existing document matching a filter or insert a new one if it doesn't exist, call `replaceOne` with `ReplaceOptions().upsert(true)`:

```kotlin
fun upsertByEventTypeAndAccount(notification: Notification): Notification {
    val filter = Filters.and(
        Filters.eq("eventType",  notification.eventType),
        Filters.eq("accountId",  notification.accountId)
    )
    val options = ReplaceOptions().upsert(true)
    collection.replaceOne(filter, notification.toDocument(), options)
    return notification
}
```

### Delete (Remove Documents)
*   **`deleteOne`**: Removes the first document matching a filter.
*   **`deleteMany`**: Removes all documents matching a filter.

```kotlin
fun deleteById(id: ObjectId): Boolean {
    val result = collection.deleteOne(Filters.eq("_id", id))
    return result.deletedCount > 0
}
```

---

## 4. Mapping between Document and Kotlin Objects

The driver uses the raw `Document` type (which is essentially a Map) to represent BSON. In your repository, map between BSON collections and Kotlin domain classes:

```kotlin
// Map domain class -> BSON Document
private fun Notification.toDocument(): Document = Document().apply {
    put("_id",       id) // ObjectId
    put("eventType", eventType)
    put("accountId", accountId)
    put("message",   message)
    put("status",    status.name)
    put("createdAt", java.util.Date.from(createdAt)) // Convert java.time.Instant to java.util.Date for BSON compatibility
}

// Map BSON Document -> domain class
private fun Document.toNotification(): Notification = Notification(
    id        = getObjectId("_id"),
    eventType = getString("eventType"),
    accountId = getLong("accountId"),
    message   = getString("message"),
    status    = NotificationStatus.valueOf(getString("status") ?: "UNREAD"),
    createdAt = getDate("createdAt")?.toInstant() ?: Instant.now()
)
```

---

## Next Steps
Now that you have completed MongoDB and NoSQL storage, proceed to the final guide: [Phase 8: Testing Guide](file:///g:/Workplace/onboarding-insignia/docs/08-testing-guide.md) to learn how to write unit and mock tests!
