# Reliability fixes

Implemented and checked on September 5, 2026.

1. **Concurrent generation:** replaced shared stripes with transaction-scoped PostgreSQL advisory locks using SHA-256-derived 64-bit identifiers, and exact-key JVM locks for local H2. Cached results bypass locking. Duplicate misses wait at most the configured lock-acquisition budget (default one second), then return 503 with Retry-After instead of waiting through another request's AI call. Removed recovery queries inside failed transactions. Failed or rolled-back generation can still be retried.
2. **Concurrent library updates:** serialized saves, removals, and progress changes per user. Repeated simultaneous saves/completions no longer rely on catching uniqueness failures in an already-failed transaction.
3. **Refresh-token consumption:** added a database write lock when loading refresh tokens, preventing two transactions from successfully consuming the same token. Reuse still revokes refresh tokens; existing access JWTs expire normally.
4. **Browser session races:** coordinated simultaneous refresh requests and used the Web Locks API to coordinate tabs where available. Refresh failures caused by temporary server/network problems preserve credentials for retry. A pending refresh cannot restore a logged-out session. Authentication-state events keep the navigation updated.
5. **Public and authentication requests:** shared-roadmap reads, login, and registration omit bearer credentials, so an expired token cannot block these public flows.
6. **Frontend recovery:** library failures display an error and retry button instead of an empty library. Save, progress, remove, and clipboard failures have visible messages. Pending mutations disable controls. Library/progress failures do not hide the public roadmap. Changing share routes resets membership and progress; reloading progress clears stale state.
7. **Abuse protection:** added a separate combined login/register budget (default 30 attempts per client per 15 minutes). Forwarded IP headers are used only behind explicitly trusted proxy CIDRs and traversed from right to left. Generation and authentication budgets remain separate. Counter outages produce a retryable 503.
8. **Redis counter correctness:** replaced separate increment/expiry operations with an atomic Lua script, including expiry repair for a counter without a TTL. Retry-After values are at least one second.
9. **AI/resource accuracy:** null prerequisite entries produce the expected validation failure rather than a null-pointer error. Unchecked links are labelled in the UI. Documentation now distinguishes approved domains from verified pages, successful caching from an unconditional single-call guarantee, and refresh-token revocation from access-JWT expiry.
10. **Regression coverage and CI:** added backend concurrency and proxy/rate-limit tests, frontend API session tests, and Playwright browser tests for error recovery and route-state changes. CI runs frontend lint, API tests, and browser tests in addition to builds and backend tests.
11. **Dependency maintenance:** added Playwright for development and applied compatible npm advisory fixes to the lockfile. npm audit reported zero vulnerabilities after the updates.

## Verification

- Backend: `mvnw.cmd -B -ntp verify` passed; 73 tests discovered, 62 passed, 11 skipped, no failures or errors.
- Frontend API: `npm test` passed all 7 tests.
- Browser: `npm run test:browser` passed all 6 tests using headless Chrome and mocked API responses.
- Frontend production build and lint passed.
- `git diff --check` passed.
- PostgreSQL and Redis Testcontainers checks were skipped locally because Docker was unavailable. The new concurrency suite also runs against PostgreSQL when Docker is available. These infrastructure results and remote CI have not been verified in this session.
- Live Anthropic generation and deployed services were not exercised.

## Deployment and local testing

The V4 migration is retained for compatibility with databases that may already have applied it, but its stripe table is no longer used. PostgreSQL uses transaction-scoped advisory locks; H2 support is intended for the local, single-process in-memory database. No existing application data is rewritten. SHA-256-derived 64-bit lock IDs have a theoretical collision possibility, unlike the easily colliding former 256-stripe scheme; waits are bounded in either case.

Configure `TRUSTED_PROXY_CIDRS` with the actual reverse-proxy network ranges before deployment. The Render Blueprint now requests this value explicitly, and RENDER=true makes an empty value a startup error instead of silently sharing one proxy budget. On local/direct deployments it may remain empty and forwarded headers are ignored. Actual Render proxy ranges and production peer/client resolution still need to be verified; no ranges were guessed. Configure `AUTH_ATTEMPTS_PER_WINDOW` to change the authentication budget. Use the Redis store for budgets shared across API replicas.

Browser setup: from `frontend`, run `npx playwright install chromium`, then `npm run test:browser`. Alternatively set `PLAYWRIGHT_CHROMIUM_EXECUTABLE` to an installed Chrome executable. Cross-tab refresh coordination requires browser support for the Web Locks API; in-page requests are coordinated independently of that support.

Verification used checksum-verified portable Java 21 and Node 22 under the system temporary directory. No permanent Java installation or system PATH change was made.
## Follow-up to external review

- **Malformed forwarding chain fixed:** validate and canonicalize each IPv4/IPv6 literal before passing it to a CIDR matcher. Invalid values such as `1.2.3.4.5`, malformed IPv6, empty entries, and hostnames fall back to the socket peer without producing a 503. Repeated XFF headers are combined in arrival order. IP parsing does not resolve DNS names.
- **Contention corrected:** tests hold a slow generation open while generating a different topic that would have occupied the same old stripe. The second generation completes independently. A separate test proves bounded waits for matching requests, and another proves cache hits bypass the lock. PostgreSQL implementations remain unexecuted locally; the corresponding H2 tests passed.
- **Timeouts added:** Anthropic SDK timeout is 45 seconds per call, SDK automatic retries are disabled, and the existing invalid-output retry policy remains. Provider I/O failures map to generation errors (502). Browser fetches have a 120-second deadline. Busy locks return 503/Retry-After: 1. `LOCK_WAIT_MILLIS` controls the lock-acquisition budget (0..10000 ms, default 1000).
- **Render configuration made explicit, not production-verified:** Render documents that applications see a proxy IP by default. The actual service's socket peer and proxy ranges were not inspected. The Blueprint requires `TRUSTED_PROXY_CIDRS`; on Render an empty value now blocks startup. Do not deploy until the correct ingress ranges are supplied. Do not substitute Render outbound IP ranges or trust-all CIDRs. After configuring them, temporarily set `LOGGING_LEVEL_COM_UNCOMPLEX_RATELIMIT=DEBUG` to compare `peer` and resolved `client` in a rate-limited request's server log, then turn DEBUG off.
- **Redis evidence gap remains explicit:** added real-Redis tests for concurrent increments, positive TTLs, repairing missing expiry, separate authentication/generation budgets, and reopening an expired window. Docker is absent locally, so these tests were compiled but skipped. CI now checks `docker info` before the backend suite; no remote CI result is claimed.

References: [Render client-IP guidance](https://render.com/articles/how-render-handles-ddos-attacks), [PostgreSQL advisory-lock functions](https://www.postgresql.org/docs/15/functions-admin.html).