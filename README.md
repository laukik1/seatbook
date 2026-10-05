# seatbook — assigned-seat reservation service (Spring Boot 3.4 / Java 21 / MySQL 8)

## Project structure (layered, Spring Data JPA)
```
src/main/java/com/seatbook
├── controller/   HTTP only: routing, auth check, status codes, DTO mapping
├── service/      business rules, transactions, retry, metrics (no SQL)
│     ShowService, ReservationService, HealthService
├── concurrency/  LockCoordinator: in-JVM striped locks (ordered acquisition)
├── repository/   Spring Data JPA interfaces (+ @Lock queries); HealthRepository = plain JDBC probe
├── model/        JPA entities (@Version), embedded ids, converters, enums; dto/ = JSON shapes
├── exception/    ApiException, GlobalExceptionHandler
└── config/       Auth, RequestIdFilter, SeatMetrics
```

## Concurrency model
| layer | mechanism | protects |
|---|---|---|
| 1 | `LockCoordinator` striped `ReentrantLock`s, keys sorted by stripe | queues a stampede in the JVM without holding DB connections (optimisation) |
| 2 | **Pessimistic** `@Lock(PESSIMISTIC_WRITE)` on seat rows (`ORDER BY label`) and on the user counter row | no double-sell, per-user limit, deadlock-free multi-seat (also across instances) |
| 3 | **Optimistic** `@Version` on seats / reservations / counters | concurrent cancels of one reservation; any stale-write bug fails instead of corrupting |
| 4 | PK on `idempotency_keys` | exactly-once per (user, key) across instances |

Failures of layers 2-4 (`ConcurrencyFailureException`, unique violation) are retried in a fresh transaction (max 5, jittered backoff).

## Run locally (clean checkout)
```bash
docker compose up --build        # app on :8080, MySQL on the compose network
./burst.sh http://localhost:8080 # needs JDK 21+ locally (or: make burst BASE_URL=...)
```
Local without Docker: start MySQL 8.0.16+ (create db/user `seatbook`), then `mvn package && java -jar target/seatbook.jar`.

## Config (env vars)
| var | default | |
|---|---|---|
| `PORT` | 8080 | Render/Railway set this |
| `DB_URL` | `jdbc:mysql://localhost:3306/seatbook?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&rewriteBatchedStatements=true` | **JDBC** URL. For managed MySQL use `useSSL=true&requireSSL=true` (drop allowPublicKeyRetrieval if not needed) |
| `DB_USER` / `DB_PASSWORD` | seatbook / seatbook | |
| `DB_POOL` | 30 | keep (pool x instances) < MySQL `max_connections` |
| `ADMIN_TOKEN` | `admin-secret` | **change in prod** |

Schema is created on startup (`schema.sql`, all `IF NOT EXISTS`; Hibernate `ddl-auto=none`). If you ran an earlier version, start from a fresh database (engine and column types changed). UUIDs are stored as CHAR(36). Requires MySQL 8.0.16+ (enforced CHECK constraints).

## Auth
`Authorization: Bearer <token>`. `ADMIN_TOKEN` = admin (only `POST /shows`). Any other token is the user id (`alice` or `user:alice`).
Identity is taken only from the token; body fields like `user_id` are ignored.

## API
```bash
curl -XPOST $URL/shows -H 'Authorization: Bearer admin-secret' -H 'Content-Type: application/json' \
  -d '{"name":"friday-night","seats":["A1","A2","A3"],"price_paise":25000}'
curl -XPOST $URL/shows/$ID/reserve -H 'Authorization: Bearer alice' -H 'Idempotency-Key: abc' \
  -H 'Content-Type: application/json' -d '{"seats":["A1"]}'
curl -XPOST $URL/reservations/$RID/cancel -H 'Authorization: Bearer alice'
curl $URL/shows/$ID            # ?include_seats=false for counts only
```
| outcome | status |
|---|---|
| reserved | 201 |
| retry of same key+same body | 200 + `Idempotent-Replay: true`, original reservation |
| `seat-taken`, `per-user-limit`, `idempotency-conflict` | 409 `{"reason": ...}` |
| unknown seat | 422 |
| bad input / missing key | 400; no/invalid token 401; wrong role / not owner 403 |

**Multi-seat = all-or-nothing** (one transaction; if any seat is unavailable none are taken).
**Release model = explicit cancel** (no auto-expiry); cancel only frees seats still bound to that reservation.

## Observability
- `GET /healthz` liveness · `GET /readyz` readiness (opens a real DB connection; 503 when DB is down)
- `GET /metrics` Prometheus: `reservations_confirmed_total`, `reservations_declined_total{reason=seat-taken|per-user-limit|idempotent-replay|idempotency-conflict|unknown-seat|invalid-request}`, `reservations_cancelled_total`, `seats_available`, `seats_held`, `seats_confirmed`
- Logs: JSON (ECS) on stdout with `request_id` (honours incoming `X-Request-Id`, echoed in response header).

## Burst
`./burst.sh <BASE_URL> [ADMIN_TOKEN]` runs: hot-seat storm (500 users → 1 seat), per-user-limit race, idempotency/spoof/cancel checks, then a 20k-request stampede with retries; prints outcome distribution, final reconciliation, and metrics. Tunables: `SEATS REQUESTS CONCURRENCY HOT_SEATS HOT_USERS`.
