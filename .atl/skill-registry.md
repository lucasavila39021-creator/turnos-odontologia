# Skill Registry

**Delegator use only.** Any agent that launches sub-agents reads this registry to resolve compact rules, then injects them directly into sub-agent prompts. Sub-agents do NOT read this registry or individual SKILL.md files.

See `_shared/skill-resolver.md` for the full resolution protocol.

## User Skills

| Trigger | Skill | Path |
|---------|-------|------|
| Use when building or reviewing a Spring Boot backend — REST layer, services, data access, caching, or async work | springboot-patterns | /home/dev/.agents/skills/springboot-patterns/SKILL.md |
| Use when reviewing Spring Security authn/authz, validation, CSRF, secrets, headers, or rate limiting | springboot-security | /home/dev/.agents/skills/springboot-security/SKILL.md |
| Use when writing a schema or data migration, adding a column or index to a large table, planning a rollback, or preparing a zero-downtime deploy | database-migrations | /home/dev/.agents/skills/database-migrations/SKILL.md |
| Use when creating or modifying MySQL tables, indexes, or queries; diagnosing slow/locking behavior; planning migrations; or troubleshooting replication | mysql | /home/dev/.agents/skills/mysql/SKILL.md |
| Guidance for distinctive, intentional visual design when building new UI or reshaping an existing one | frontend-design | /home/dev/.agents/skills/frontend-design/SKILL.md |
| Toolkit for interacting with and testing local web applications using Playwright | webapp-testing | /home/dev/.agents/skills/webapp-testing/SKILL.md |

## Compact Rules

Pre-digested rules per skill. Delegators copy matching blocks into sub-agent prompts as `## Project Standards (auto-resolved)`.

### springboot-patterns
- Controller → Service → Repository layered architecture; keep controllers thin, services focused, repositories simple
- Constructor injection only (no field injection); use DTOs `CreateXxxRequest`/`XxxResponse` with `from()` mapper and Bean Validation `@Valid`
- `ResponseEntity` with correct status (201 CREATED) + `Page<DTO>` via `map()`; central `@ControllerAdvice` for `ApiError` RFC 7807 (`spring.mvc.problemdetails.enabled=true`)
- `@Transactional` on service writes, `@Transactional(readOnly=true)` for queries; configure HikariCP pool/timeouts
- Caching requires `@EnableCaching` + `@Cacheable/@CacheEvict`; async requires `@EnableAsync` + `@Async CompletableFuture`
- SLF4J structured logging `log.info("op key={}", val)` + `OncePerRequestFilter` for request duration; never log secrets
- Rate limiting via `OncePerRequestFilter` + Bucket4j using `request.getRemoteAddr()` (with `ForwardedHeaderFilter` + `server.forward-headers-strategy` behind proxy); exponential-backoff retry for external calls

### springboot-security
- Prefer stateless JWT/opaque tokens; cookies must be `httpOnly, Secure, SameSite=Strict/Lax`; validate via `OncePerRequestFilter` or resource server
- `@EnableMethodSecurity` + `@PreAuthorize("hasRole('ADMIN')")` or `@authz.isOwner(#id)`; deny by default, least privilege
- DTO validation `@Valid` with `@NotBlank/@Email/@Size` + custom validators; never concatenate SQL — use `:param` or derived queries
- Passwords via `PasswordEncoder` bean `BCryptPasswordEncoder(12)`; never store plaintext
- CSRF enabled for session/SSR, disabled + `STATELESS` for pure Bearer APIs; CORS at `SecurityFilterChain` level, never `*` in prod
- Security headers: CSP `default-src 'self'`, `frameOptions sameOrigin`, `referrerPolicy NO_REFERRER`; secrets via `${ENV_VAR}` or Vault, never in code/yaml
- Rate limit with Bucket4j (429 + retry hint); scan deps with OWASP/Snyk, fail build on CVEs; never log tokens/PII

### database-migrations
- Every change is a migration; migrations are forward-only and immutable once deployed — create new migration to fix
- Never mix DDL and DML in one migration; new columns must be nullable or with default (never `NOT NULL` without default)
- Use `CREATE INDEX CONCURRENTLY` (outside tx) for existing tables; batch backfills (5k-10k rows, `SKIP LOCKED`, `bulk_update`)
- Zero-downtime renames via expand-contract: 1 EXPAND add column/table + dual-write → 2 MIGRATE backfill + read new → 3 CONTRACT drop old
- Flyway: `V{n}__name.sql` + `migrate deploy` (`validate` in prod); Django: `makemigrations`/`migrate`/`SeparateDatabaseAndState` for deferred drops
- Always include UP and DOWN (or mark irreversible); test against prod-sized data; document rollback plan
- `golang-migrate`/`Kysely`/`Prisma`: use `Kysely<any>` in migrations, `--create-only` for custom SQL; never edit deployed SQL

### mysql
- `BIGINT UNSIGNED AUTO_INCREMENT` PK for OLTP; avoid random UUID as clustered PK — put UUID in secondary unique col if needed
- Always `utf8mb4`/`utf8mb4_0900_ai_ci`; prefer `NOT NULL`, `DATETIME` over `TIMESTAMP`; use lookup tables over `ENUM`; 3NF then denormalize only for hot paths
- Composite indexes: equality columns first, then range/sort (leftmost prefix); range predicate stops later columns; secondary indexes include PK implicitly
- Query: check `EXPLAIN` for `ALL/filesort/temporary`; cursor pagination not `OFFSET`; no functions on indexed cols in `WHERE`; batch inserts 500-5000 rows
- `REPEATABLE READ` default (gap locks) → use `READ COMMITTED` for high contention; consistent row access order + retry 1213 with backoff; `SELECT ... FOR UPDATE` sparingly; I/O outside tx
- Online DDL `ALGORITHM=INPLACE` when possible; test on replica; tune HikariCP pooling; monitor `performance_schema` to drop unused indexes (`count_read=0`)
- Never run destructive drops/deletes without explicit human approval; include rollback + post-deploy verification for prod changes

### frontend-design
- Ground design in odontologia subject matter; plan tokens first: 4-6 hex palette, 1-2 typefaces with distinct roles, layout ASCII wireframe, principles
- Typography personality: one family or two distinct; scale per Elements of Typographic Style; <80ch lines; no single-word accent, no ALL-CAPS labels, no `—` fragment labels
- Visual structure encodes information — borders/dividers only when meaningful; numbered markers (01/02) only for true sequences
- Reject generic defaults: cream #F4F1EA+terracotta, black+acid green, SaaS identical cards, middle-dot meta, tinted near-black, monospace data labels
- One memorable element, rest quiet; single page-load motion max; motion only answers user action; responsive mobile-first, visible keyboard focus, respect `prefers-reduced-motion`, a11y palette
- Copy is design: active voice, plain verbs, sentence case, CTA = outcome ("Save changes"); errors give fix, empty states invite action; cohesion across flow

### webapp-testing
- Python Playwright `sync_playwright()` + `p.chromium.launch(headless=True)`; always `page.goto()` → `wait_for_load_state('networkidle')` before DOM inspect → actions → `browser.close()`
- Reconnaissance-then-action: screenshot `page.screenshot(full_page=True)` + `page.content()`/`locator().all()` to discover selectors before acting
- Server lifecycle via `python scripts/with_server.py --server "cmd" --port N -- python script.py` (supports multiple `--server`); always `--help` first, treat script as black box
- Descriptive selectors: `text=`, `role=`, CSS/ID; explicit waits `wait_for_selector`/`wait_for_timeout`; never inspect before `networkidle` on dynamic apps
- Bundle scripts/examples in `scripts/` and `examples/` (`element_discovery.py`, `console_logging.py`) — reuse before writing custom logic
- Headless Chromium only; close browser; handle single and multi-server (backend+frontend) via `with_server.py`

## Project Conventions

| File | Path | Notes |
|------|------|-------|
| (none) | — | No AGENTS.md / CLAUDE.md / .cursorrules / GEMINI.md found in project root |

Read the convention files listed above for project-specific patterns and rules. All referenced paths have been extracted — no need to read index files to discover more.
