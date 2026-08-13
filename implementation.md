This architectural design document outlines the blueprint for an enterprise-grade Library Management System (LMS). We are approaching this as a distributed desktop application with a robust, layered architecture designed for maintainability, security, and scalability across a large university network.

## 1. Executive Summary & Technology Stack

The system is designed to handle thousands of concurrent transactions (checkouts, returns, catalog searches) while maintaining ACID compliance and providing a responsive, thread-safe user interface.

* **Frontend:** JavaFX (Modern MVC approach via FXML). JavaFX is strictly preferred over Swing for enterprise applications due to its hardware-accelerated rendering pipeline, CSS styling capabilities, and clean separation of view (FXML) from controller logic.
* **Backend Core:** Java 17+ (LTS).
* **Data Access:** Raw JDBC with HikariCP (Connection Pooling).
* **Database:** PostgreSQL 15+ (Ideal for complex relational constraints, concurrent read/write optimization, and full-text search capabilities).
* **Security:** Argon2id for password hashing; Role-Based Access Control (RBAC).

---

## 2. System Architecture (N-Tier Clean Architecture)

To ensure the system remains maintainable over a 10+ year lifecycle, we enforce a strict N-Tier/Clean Architecture. Dependencies only point inward; the UI knows about the Services, but the Services know nothing about the UI.

| Layer | Responsibility | Key Components |
| --- | --- | --- |
| **Presentation (View)** | Renders UI, captures user input. | `.fxml` files, CSS stylesheets. |
| **UI Controllers** | Binds View to backend logic, handles async UI tasks. | `LoginController`, `CheckoutController`. |
| **Service Layer** | Orchestrates business rules, boundary for database transactions. | `CirculationService`, `CatalogService`. |
| **Data Access (DAO)** | Executes SQL queries, maps ResultSets to DTOs/Entities. | `BookDAOImpl`, `MemberDAOImpl`. |
| **Database** | Relational storage, integrity constraints, triggers. | PostgreSQL instance. |

**Concurrency Rule:** All database calls and heavy business logic MUST be executed on background threads (using JavaFX `Task<T>` or `Service<T>`). Only the results are passed back to the JavaFX Application Thread to update the UI, ensuring the application never "freezes" during network latency.

---

Here is an interactive visualization of the database relationships driving the backend:

> **Architectural Insight:** Separating `Loan` and `Fine` ensures that financial auditing can be queried independently of active circulation records.

---

## 3. Database Schema (PostgreSQL)

The database is normalized to 3NF. We utilize UUIDs for primary keys to prevent enumeration attacks and simplify distributed database merging if required in the future.

### Core Tables

| Table Name | Primary Columns | Foreign Keys & Constraints |
| --- | --- | --- |
| `users` | `id` (UUID), `email` (UNIQUE), `password_hash`, `role` | `role` ENUM ('MEMBER', 'LIBRARIAN', 'ADMIN') |
| `books` | `isbn` (PK), `title`, `author`, `total_copies`, `available_copies` | Check: `available_copies <= total_copies` |
| `loans` | `id` (UUID), `checkout_date`, `due_date`, `return_date`, `status` | FK: `user_id`, FK: `isbn`. |
| `fines` | `id` (UUID), `amount`, `paid_status`, `issued_date` | FK: `loan_id` (UNIQUE) |
| `audit_log` | `id` (BIGSERIAL), `action`, `timestamp`, `ip_address` | FK: `user_id` |

---

## 4. Object-Oriented Domain Design

We use rich domain models rather than anemic data structures. Entities encapsulate their own state logic where appropriate.

* **`User` (Abstract Base Class):** Contains `id`, `name`, `email`, and authentication logic.
* **`Member` (Extends User):** Contains `borrowingLimit`, `currentFinesBalance()`, and `List<Loan> activeLoans`.
* **`Librarian` (Extends User):** Contains administrative privileges and audit ID tagging.
* **`Book`:** Manages inventory state. It includes an `isAvailable()` method that checks against its internal `availableCopies` counter.
* **`Loan`:** Represents the transaction. Contains business logic like `calculateOverdueFine()` based on `LocalDate.now()` compared to `dueDate`.

**Interfaces (Dependency Inversion):**
Instead of concrete classes depending on each other, we rely on interfaces.

* `public interface BookRepository`
* `public interface LoanTransactionManager`

This allows the QA team to inject `MockBookRepository` objects during unit testing without needing a live database connection.

---

## 5. Data Access Layer & JDBC Optimization

For an enterprise application, raw JDBC requires strict resource management to prevent memory leaks and database connection exhaustion.

1. **Connection Pooling:** We implement **HikariCP**. Opening a new database connection takes ~30-50ms. HikariCP maintains a pool of active connections, reducing DB access latency to <1ms.
2. **Prepared Statements:** Every query is executed via `PreparedStatement`. String concatenation for SQL is strictly forbidden to prevent SQL Injection (SQLi).
3. **Transaction Management:** A checkout involves two steps: creating a `Loan` record and decrementing `available_copies` in `books`.

Here is the exact structural pattern required for the checkout transaction to ensure ACID compliance:

1. **Disable Auto-Commit:** Critical for multi-step integrity.
Retrieve connection from HikariCP and call `connection.setAutoCommit(false);`.


2. **Row-Level Lock (SELECT FOR UPDATE):**
Execute `SELECT available_copies FROM books WHERE isbn = ? FOR UPDATE;`. This prevents another librarian from checking out the exact same book at the exact same millisecond (race condition).


3. **Insert Loan Record:**
Execute the `INSERT INTO loans` statement.


4. **Update Book Inventory:**
Execute `UPDATE books SET available_copies = available_copies - 1 WHERE isbn = ?;`.


5. **Commit or Rollback:** Exception Handling.
If all steps succeed, `connection.commit();`. If a `SQLException` is caught at any point, execute `connection.rollback();` to revert the entire process, ensuring the database is never left in an inconsistent state.


---

## 6. Security Posture

* **Authentication:** Passwords are never stored in plaintext. We utilize Argon2id (the OWASP recommended hashing algorithm). When a user logs in, the entered password is hashed and compared against the stored hash.
* **Role-Based Access Control (RBAC):** UI elements (like the "Delete Book" button) are dynamically hidden or disabled based on the logged-in user's `Role` enum.
* **Auditing:** Every write operation (INSERT, UPDATE, DELETE) triggers an entry in the `audit_log` table, recording the Librarian's UUID, the action, and the timestamp.