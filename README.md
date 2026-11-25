# Distributed Transactional Double-Entry Ledger & Wallet Service

> A production-grade **double-entry bookkeeping** wallet service built with **Java 21**, **Spring Boot 3**, **PostgreSQL 16**, and **Flyway** — demonstrating mastery over transaction isolation, deadlock prevention, idempotent API design, and containerized integration testing.

---

## 🏗️ Architecture Highlights

| Concept | Implementation |
|---|---|
| **Double-Entry Bookkeeping** | Every transfer creates paired DEBIT + CREDIT ledger entries |
| **Pessimistic Locking** | `SELECT ... FOR UPDATE` for race-condition-free balance updates |
| **Deadlock Prevention** | Deterministic lock ordering (lower account ID locked first) |
| **Idempotent APIs** | `Idempotency-Key` header with SHA-256 payload hashing |
| **Reconciliation Engine** | Verifies snapshot balances match computed ledger totals |
| **Database Integrity** | `CHECK (available_balance >= 0)` enforced at PostgreSQL level |
| **Exact Arithmetic** | `NUMERIC(19,4)` / `BigDecimal` — no floating-point rounding errors |
| **Integration Testing** | Testcontainers with real PostgreSQL (not H2) |

---

## 🚀 Quick Start

### Prerequisites
- Java 21
- Maven 3.9+
- Docker & Docker Compose

### 1. Start PostgreSQL
```bash
cd docker
docker-compose up -d
```

### 2. Run the Application
```bash
mvn clean spring-boot:run
```

### 3. Test the APIs
```bash
# Create account
curl -X POST http://localhost:8080/api/v1/accounts \
  -H "Content-Type: application/json" \
  -d '{"accountHolderName": "John Doe", "email": "john@example.com"}'

# Transfer with idempotency
curl -X POST http://localhost:8080/api/v1/transfers \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: unique-key-123" \
  -d '{"sourceAccountId": 1, "destinationAccountId": 2, "amount": 500.00}'

# View ledger
curl http://localhost:8080/api/v1/transfers/ledger/1

# Reconcile all accounts
curl -X POST http://localhost:8080/api/v1/reconciliation/all
```

---

## 📁 Project Structure

```
ledger-wallet-service/
├── src/main/java/com/sapidblue/ledger/
│   ├── LedgerWalletApplication.java       # Spring Boot entry point
│   ├── config/                             # Web & Jackson configuration
│   ├── controller/                         # REST API controllers
│   │   ├── AccountController.java
│   │   ├── TransferController.java
│   │   └── ReconciliationController.java
│   ├── dto/
│   │   ├── request/                        # Validated request DTOs
│   │   └── response/                       # Response DTOs
│   ├── entity/                             # JPA entities
│   │   ├── Account.java
│   │   ├── LedgerEntry.java
│   │   ├── TransferRecord.java
│   │   ├── IdempotencyRecord.java
│   │   └── ReconciliationLog.java
│   ├── enums/                              # Status enums
│   ├── exception/                          # Custom exceptions + global handler
│   ├── filter/                             # Idempotency filter
│   ├── repository/                         # Spring Data JPA repositories
│   ├── service/                            # Service interfaces
│   │   └── impl/                           # Service implementations
│   └── util/                               # SHA-256 hash utility
├── src/main/resources/
│   ├── application.properties              # Main config
│   └── db/migration/
│       ├── V1__create_core_schema.sql      # Full database DDL
│       └── V2__seed_test_data.sql          # Development seed data
├── src/test/                               # Integration & unit tests
├── docker/
│   └── docker-compose.yml                  # PostgreSQL + pgAdmin
├── Dockerfile                              # Multi-stage Docker build
├── pom.xml                                 # Maven dependencies
└── README.md
```

---

## 📊 Database Schema

### Tables
| Table | Purpose |
|---|---|
| `accounts` | Account snapshots with available/locked balances |
| `ledger_entries` | Immutable append-only double-entry journal |
| `transfer_records` | Links debit and credit legs of each transfer |
| `idempotency_records` | Prevents duplicate request processing |
| `reconciliation_logs` | Tracks balance reconciliation audit runs |

---

## 🔌 API Endpoints

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v1/accounts` | Create a new account |
| `GET` | `/api/v1/accounts/{id}` | Get account details |
| `GET` | `/api/v1/accounts` | List all accounts |
| `POST` | `/api/v1/transfers` | Execute a transfer (idempotent) |
| `POST` | `/api/v1/transfers/deposit` | Deposit funds |
| `GET` | `/api/v1/transfers/ledger/{id}` | Get account ledger history |
| `GET` | `/api/v1/transfers/history/{id}` | Get transfer history |
| `POST` | `/api/v1/reconciliation/{id}` | Reconcile single account |
| `POST` | `/api/v1/reconciliation/all` | Reconcile all accounts |

---

## 🧪 Running Tests

```bash
# Unit tests
mvn test

# Integration tests (requires Docker for Testcontainers)
mvn verify
```

---

## 📄 License

Private — Sapidblue Technologies
