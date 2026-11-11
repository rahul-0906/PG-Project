# PG CRM — Coding Standards & Conventions
### Mandatory Code Style Rules for Backend (Java) and Frontend (React/JS)

> [!IMPORTANT]
> All contributors and AI agents MUST follow these standards when writing or modifying code.
> Inconsistent code style leads to review failures and hard-to-maintain codebases.

---

## 1. Java Backend Standards (Spring Boot / Java 23)

### 1.1 Package Structure

```
com.pgcrm
├── config/           # Spring configuration beans (Security, Cache, Jackson, etc.)
├── controller/       # REST controllers — route handling only, no business logic
├── dto/              # Data Transfer Objects for request/response payloads
├── entity/           # JPA entities — database domain models
│   └── enums/        # All Java enums for entity fields
├── exception/        # Custom exceptions and GlobalExceptionHandler
├── mapper/           # MapStruct DTO ↔ Entity converters
├── repository/       # Spring Data JPA repository interfaces
├── scheduler/        # Cron job scheduler classes
├── security/         # JWT filter, UserDetailsService, SecurityConfig
├── seeder/           # Database seeders for development
└── service/          # Business logic services (interface + impl pattern)
    └── impl/         # Service implementations
```

### 1.2 Naming Conventions

| Artifact | Convention | Example |
| :--- | :--- | :--- |
| Classes | `PascalCase` | `GuestService`, `InvoiceController` |
| Methods | `camelCase` | `generateMonthlyInvoices()`, `checkIn()` |
| Constants | `UPPER_SNAKE_CASE` | `MAX_RETRY_ATTEMPTS` |
| Database tables | `snake_case` plural | `guests`, `invoice_line_items` |
| Database columns | `snake_case` | `monthly_rent`, `check_in_date` |
| DTOs | Suffix with `Request`/`Response` | `GuestCheckInRequest`, `InvoiceResponse` |
| Repositories | Suffix with `Repository` | `GuestRepository` |
| Services | Interface + `Impl` class | `InvoiceService`, `InvoiceServiceImpl` |
| Schedulers | Suffix with `Scheduler` | `MonthlyBillingScheduler` |

### 1.3 Mandatory Lombok Annotations

Always use Lombok to avoid boilerplate:

```java
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MyEntity { ... }
```

For services/controllers:
```java
@RequiredArgsConstructor  // for constructor injection
@Slf4j                    // for logging
```

### 1.4 Dependency Injection

- **ALWAYS** use constructor injection via `@RequiredArgsConstructor` (Lombok-generated).
- **NEVER** use `@Autowired` on fields — it makes testing harder.
- **NEVER** use setter injection.

```java
// ✅ CORRECT
@RequiredArgsConstructor
public class GuestService {
    private final GuestRepository guestRepository;
    private final EmailService emailService;
}

// ❌ WRONG — field injection
@Autowired
private GuestRepository guestRepository;
```

### 1.5 Transactions

- Service methods that perform multiple writes MUST be annotated with `@Transactional`.
- Read-only methods should use `@Transactional(readOnly = true)` for performance.
- Never call `@Transactional` methods from within the same class — it bypasses Spring's proxy.

```java
// ✅ CORRECT
@Service
public class InvoiceServiceImpl implements InvoiceService {
    @Override
    @Transactional
    public Invoice generateInvoice(String guestId, int month, int year) { ... }

    @Override
    @Transactional(readOnly = true)
    public List<Invoice> findByGuest(String guestId) { ... }
}
```

### 1.6 Logging

- All classes log via Lombok `@Slf4j`.
- Use structured, readable log messages with placeholders:

```java
log.info("Invoice generated | Guest: {} | Period: {}/{} | Total: ₹{}", guestId, month, year, total);
log.warn("Payment reminder skipped | Guest: {} | Reason: invoice not found", guestId);
log.error("Invoice generation failed | Guest: {} | Error: {}", guest.getId(), e.getMessage());
```

- Log at `DEBUG` level for repository calls, `INFO` for business events, `WARN` for skipped operations, `ERROR` for failures.
- **NEVER log passwords, JWT tokens, or personal data.**

### 1.7 Exception Handling

- Throw `ResourceNotFoundException(entityName, id)` for missing entities — maps to `404`.
- Use `GlobalExceptionHandler.java` for all exception-to-response mapping.
- Do NOT throw generic `RuntimeException` — use or create a specific exception type.

```java
// ✅ CORRECT
Guest guest = guestRepository.findById(guestId)
    .orElseThrow(() -> new ResourceNotFoundException("Guest", guestId));

// ❌ WRONG
Guest guest = guestRepository.findById(guestId)
    .orElseThrow(() -> new RuntimeException("Guest not found"));
```

### 1.8 Entity Rules

- All entity primary keys are `String` UUID type: `@GeneratedValue(strategy = GenerationType.UUID)`.
- Soft-deleteable entities (Guest, Invoice) use `@SQLDelete` + `@SQLRestriction`.
- **Never call `repository.deleteById()` on Guest or Invoice.**
- Use `@Builder.Default` for collection fields to prevent NPE on new instances:

```java
@Builder.Default
private List<Floor> floors = new ArrayList<>();
```

### 1.9 DTO Rules

- All API request bodies must use annotated DTO classes in `com.pgcrm.dto`.
- Use `@NotNull`, `@Email`, `@Size` etc. for validation.
- Never expose raw JPA entity classes directly in `ResponseEntity<>` responses.
- Conversion between Entity ↔ DTO happens in `com.pgcrm.mapper` via MapStruct.

### 1.10 Database & JPA

- `ddl-auto: validate` — Hibernate validates schema, does NOT alter it.
- Schema changes go through **Flyway migrations only** (see AGENTS.md Rule 2).
- Avoid N+1 query problems — use `@EntityGraph` or `JOIN FETCH` on frequently accessed associations.
- Use `FetchType.LAZY` by default. Use `FetchType.EAGER` only when the child collection is always needed (e.g., `InvoiceLineItem` on `Invoice`).
- Disable `open-in-view` (already configured): `spring.jpa.open-in-view: false`.

---

## 2. Frontend Standards (React 18 / Vite / Tailwind)

### 2.1 Component Rules

- All components are function components using React hooks (no class components).
- File naming: `PascalCase.jsx` (e.g., `GuestDashboard.jsx`).
- One component per file.
- Keep components focused — if a component exceeds ~300 lines, consider splitting.

### 2.2 Naming Conventions

| Artifact | Convention | Example |
| :--- | :--- | :--- |
| Components | `PascalCase.jsx` | `ManagerGuests.jsx` |
| Hook files | `useHookName.js` | `useAuth.js` |
| API modules | `camelCase.js` | `guestApi.js` |
| CSS classes | Tailwind utilities | `className="flex items-center gap-2"` |
| Context | `NounContext.jsx` | `AuthContext.jsx` |

### 2.3 API Calls

- **ALWAYS** use the Axios instance from `src/api/index.js` — never use raw `fetch()` or a new `axios.create()`.
- This ensures JWT injection, 401 token refresh, and 5-second caching work correctly.
- Wrap API calls in `try/catch` blocks inside `useEffect` or event handlers.

```jsx
// ✅ CORRECT — uses shared api instance
import { guestApi } from '../api';

useEffect(() => {
  guestApi.getGuests()
    .then(res => setGuests(res.data))
    .catch(err => console.error('Failed to load guests:', err));
}, []);

// ❌ WRONG — bypasses interceptors
fetch('/api/manager/guests')
  .then(r => r.json())
  .then(setGuests);
```

### 2.4 Authentication Guards

- Use the `PrivateRoute` component in `App.jsx` to protect routes.
- Pass `roles` prop with allowed roles array: `<PrivateRoute roles={['PG_MANAGER', 'PG_OWNER']}>`.
- Use `useAuth()` hook to access `user` from `AuthContext`.

### 2.5 State Management

- Local component state: `useState`.
- Side effects and data fetching: `useEffect`.
- Global state: React Context only (`AuthContext`, `SystemConfigContext`).
- No Redux, Zustand, or MobX.

### 2.6 Branch Selection (Multi-Building Context)

- When making manager API calls that require a building context, include the building ID via the `X-Selected-Branch-Id` header (automatically injected from `sessionStorage.selectedBranchId` by the API interceptor).
- Managers can have access to multiple buildings — always scope queries to the selected building.

### 2.7 Error Handling in UI

- Display user-friendly error messages using toast notifications or inline alert components.
- Never expose raw API error messages or stack traces to end users.
- Use the `error.response?.data?.error` pattern to extract backend error messages.

### 2.8 Code Formatting

- 2-space indentation for JSX/JS files.
- Single quotes for strings.
- Semicolons at end of statements.
- Trailing commas in multiline arrays/objects.

---

## 3. SQL / Flyway Migration Standards

### 3.1 File Naming

```
V{version}__{snake_case_description}.sql
```
Examples:
- `V18__add_laundry_pricing_config.sql`
- `V19__add_guest_photo_url.sql`

### 3.2 Migration File Rules

- **NEVER modify an existing migration file** — Flyway checksums them.
- Each migration file must be **idempotent-safe** (use `IF NOT EXISTS` for table/column adds).
- Migrations run automatically on Spring Boot startup.
- Test migrations locally before committing.

### 3.3 Migration File Template

```sql
-- V18__describe_what_this_does.sql
-- Purpose: Brief description of why this migration exists

-- Add new column (safe: only adds if not present)
ALTER TABLE guests ADD COLUMN IF NOT EXISTS photo_url VARCHAR(500);

-- Create new table
CREATE TABLE IF NOT EXISTS addon_types (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    price NUMERIC(10, 2) NOT NULL,
    building_id UUID NOT NULL REFERENCES buildings(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create index
CREATE INDEX IF NOT EXISTS idx_addon_types_building ON addon_types(building_id);
```

### 3.4 Column Type Conventions

| Java Type | PostgreSQL Type |
| :--- | :--- |
| `String` (UUID PK) | `UUID` with `gen_random_uuid()` |
| `String` (general) | `VARCHAR(n)` |
| `BigDecimal` | `NUMERIC(12, 2)` |
| `boolean` | `BOOLEAN NOT NULL DEFAULT false` |
| `LocalDate` | `DATE` |
| `LocalDateTime` | `TIMESTAMP` |
| `LocalTime` | `TIME` |
| Enum (stored as String) | `VARCHAR(50)` |
| FK references | `UUID NOT NULL REFERENCES parent_table(id)` |

---

## 4. Git Commit Conventions

```
<type>(<scope>): <short description>

[optional body]
```

### Types

| Type | When To Use |
| :--- | :--- |
| `feat` | New feature |
| `fix` | Bug fix |
| `docs` | Documentation changes |
| `refactor` | Code restructuring without behavior change |
| `test` | Adding or updating tests |
| `chore` | Build scripts, deps, config |
| `migration` | New Flyway migration |
| `security` | Security patches |

### Examples

```
feat(billing): add WhatsApp reminder for overdue invoices
fix(auth): prevent refresh token reuse after logout
docs(api): add /api/manager/guests endpoint documentation
migration(V18): add laundry pricing config to building_configs
security(cors): restrict allowed origins to explicit list
```

---

## 5. Testing Standards

### 5.1 Backend Test Location

- Unit tests: `src/test/java/com/pgcrm/`
- Test profile configuration: `src/main/resources/application-test.yml`
- Use `@SpringBootTest` for integration tests, `@WebMvcTest` for controller layer tests.
- Run tests with: `mvn test` (uses H2 in-memory DB via `test` profile).

### 5.2 Frontend Test Location

- Test files: `src/test/`
- Test framework: configured via `package.json` (Vitest or Jest).

### 5.3 What Must Be Tested

| Layer | What to Test |
| :--- | :--- |
| Service | Billing calculation logic (flat-rate, EB split, line item totals) |
| Controller | HTTP status codes and response shapes via `@WebMvcTest` |
| Security | Unauthenticated access returns 401, wrong role returns 403 |
| Scheduler | Invoice generation skipping logic, cron correctness |
