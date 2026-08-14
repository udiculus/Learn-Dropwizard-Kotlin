# 🏦 Finance Learning Microservices

A hands-on project to learn **Kotlin**, **Dropwizard**, **Apache Kafka**, **MySQL**, **MongoDB**, and **JUnit 5 + Mockito** through building real finance-domain microservices.

---

## 🏗️ Architecture

```
┌────────────────────────────────────────────────────────────────────┐
│                         Docker Compose                              │
│                                                                      │
│  ┌──────────────┐    account.events    ┌──────────────────────────┐ │
│  │   Account    │ ──────────────────►  │       Apache Kafka       │ │
│  │   Service    │                      │  + Zookeeper             │ │
│  │  :8080/8081  │ ◄──────────────────  │  Kafka UI: :8090         │ │
│  │   MySQL      │   (consumes for UI)  └────────────┬─────────────┘ │
│  └──────────────┘                                   │               │
│                                                      │               │
│  ┌──────────────┐    transaction.events             │               │
│  │ Transaction  │ ──────────────────►  ─────────────┘               │
│  │   Service    │ ◄── account.events                                │
│  │  :8082/8083  │                                                    │
│  │   MySQL      │                                                    │
│  └──────────────┘                                                    │
│                                                      ▼               │
│  ┌──────────────┐  ◄── account.events + transaction.events          │
│  │ Notification │                                                    │
│  │   Service    │                                                    │
│  │  :8084/8085  │                                                    │
│  │   MongoDB    │                                                    │
│  └──────────────┘                                                    │
└────────────────────────────────────────────────────────────────────┘
```

## 📦 Services

| Service | Port | Admin | Database | Kafka Role |
|---|---|---|---|---|
| **Account Service** | 8080 | 8081 | MySQL | Producer → `account.events` |
| **Transaction Service** | 8082 | 8083 | MySQL | Producer → `transaction.events`, Consumer ← `account.events` |
| **Notification Service** | 8084 | 8085 | MongoDB | Consumer ← both topics |
| **Kafka UI** | 8090 | — | — | Web UI for Kafka topics |

---

## 🚀 Quick Start

### Prerequisites
- Docker Desktop
- JDK 17
- Maven 3.9+

### 1. Start the full stack

```bash
# From the project root
docker-compose up -d

# Watch startup logs
docker-compose logs -f
```

### 2. Verify services are healthy

```bash
curl http://localhost:8081/healthcheck   # Account Service
curl http://localhost:8083/healthcheck   # Transaction Service
curl http://localhost:8085/healthcheck   # Notification Service

# Open Kafka UI in browser
start http://localhost:8090
```

### 3. End-to-end test flow

```bash
# Step 1: Create a customer
curl -X POST http://localhost:8080/api/customers \
  -H "Content-Type: application/json" \
  -d '{"firstName":"Alice","lastName":"Smith","email":"alice@example.com","phone":"+1-555-0001"}'

# Step 2: Create an account
curl -X POST http://localhost:8080/api/accounts \
  -H "Content-Type: application/json" \
  -d '{"customerId":1,"accountType":"SAVINGS","currency":"USD","initialDeposit":0}'

# Step 3: Deposit funds
curl -X POST http://localhost:8082/api/transactions/deposit \
  -H "Content-Type: application/json" \
  -d '{"accountId":1,"amount":1000.00,"currency":"USD","description":"Initial deposit"}'

# Step 4: Withdraw funds
curl -X POST http://localhost:8082/api/transactions/withdraw \
  -H "Content-Type: application/json" \
  -d '{"accountId":1,"amount":200.00,"currency":"USD"}'

# Step 5: Check notifications (should have events from Kafka)
curl "http://localhost:8084/api/notifications?accountId=1"

# Step 6: Check transaction history
curl "http://localhost:8082/api/transactions/history/1"
```

---

## 🧪 Running Tests

```bash
# Run all tests across all modules
mvn test

# Run tests for a specific service
mvn test -pl account-service
mvn test -pl transaction-service
mvn test -pl notification-service

# Run only tagged "unit" tests
mvn test -Dgroups=unit

# Build without tests
mvn package -DskipTests
```

---

## 📚 Learning Path

Work through the services in order — each introduces new concepts:

### Phase 1 — Foundation
- Read `docs/01-kotlin-basics.md` — Kotlin syntax, types, control flow, functions
- Explore `account-service/src/main/kotlin/.../model/AccountModels.kt` — data class, sealed class, enum

### Phase 2 — Account Service (OOP + Dropwizard + MySQL)
- Read `docs/02-kotlin-oop.md` — classes, interfaces, null safety, generics
- Read `docs/04-dropwizard-guide.md` — application structure, HTTP methods
- Read `docs/06-mysql-guide.md` — DDL and DML
- Study the DAO, service, and resource layers

### Phase 3 — Transaction Service (Kafka + Advanced Kotlin)
- Read `docs/05-kafka-guide.md` — producers, consumers, streams
- Study `kafka/TransactionKafka.kt` — consumer poll loop, manual offset commit
- Study `service/TransactionValidator.kt` — Comparator, apply/with, scope functions

### Phase 4 — Notification Service (Coroutines + MongoDB)
- Read `docs/03-kotlin-advanced.md` — coroutines, scope functions
- Read `docs/07-mongodb-guide.md` — MongoDB CRUD
- Study `service/NotificationService.kt` — suspend functions, CoroutineScope
- Study `db/NotificationRepository.kt` — insert, update, upsert, delete, multi-update

### Phase 5 — Testing
- Read `docs/08-testing-guide.md`
- Study all `*Test.kt` files in each service's `src/test` directory

---

## 📁 Project Structure

```
onboarding/
├── pom.xml                          # Parent Maven POM
├── docker-compose.yml               # Full stack orchestration
├── db/migration/
│   ├── V1__create_accounts_table.sql
│   ├── V2__create_transactions_table.sql
│   └── init-mongo.js
├── docs/                            # Learning guides
├── account-service/
│   ├── pom.xml
│   ├── Dockerfile
│   ├── config.yml
│   └── src/main/kotlin/.../account/
│       ├── AccountApplication.kt    ← entry point
│       ├── AccountConfiguration.kt  ← Dropwizard config
│       ├── api/                     ← request/response DTOs
│       ├── model/                   ← domain models
│       ├── db/                      ← JDBI DAOs
│       ├── service/                 ← business logic
│       ├── resources/               ← JAX-RS endpoints
│       ├── kafka/                   ← Kafka producer
│       └── health/                  ← health checks
├── transaction-service/             ← same structure + consumer
└── notification-service/            ← MongoDB + coroutines + consumer
```

---

## 🗺️ Syllabus Coverage

| Topic Area | Where to Look |
|---|---|
| Kotlin basics (types, control flow, functions) | `AccountModels.kt`, `AccountService.kt` |
| Kotlin OOP (classes, sealed, enum, generics) | `AccountModels.kt`, `TransactionValidator.kt` |
| Kotlin advanced (Pair, Triple, apply/with, annotations) | `TransactionModels.kt`, `TransactionValidator.kt` |
| Kotlin coroutines | `NotificationService.kt`, `NotificationConsumer.kt` |
| Dropwizard (HTTP methods, core structure) | All `*Application.kt`, `*Resource.kt` files |
| Kafka Producer | `AccountEventProducer.kt`, `TransactionKafka.kt` |
| Kafka Consumer | `AccountEventConsumer.kt` in transaction-service, `NotificationConsumer.kt` |
| Kafka Streams | `NotificationConsumer.kt` |
| MySQL DDL | `V1__create_accounts_table.sql`, `V2__create_transactions_table.sql` |
| MySQL DML | `AccountDao.kt`, `TransactionDao.kt` |
| MongoDB CRUD | `NotificationRepository.kt` |
| JUnit 5 foundations | `CustomerServiceTest.kt` |
| JUnit 5 parameterized | `AccountServiceTest.kt` |
| Mockito basics | `CustomerServiceTest.kt` |
| Mockito intermediate | `AccountServiceTest.kt`, `TransactionValidatorTest.kt` |
| Testing Kotlin patterns | `NotificationServiceTest.kt` |
