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
## Follow-up: proxy identification on Render

The previous instruction — supply `TRUSTED_PROXY_CIDRS` with Render's ingress ranges — is not
practically satisfiable, so it has been replaced rather than left as a deployment blocker.

Render's own documentation states that all inbound traffic passes through Cloudflare's network
before reaching the application, and that "because traffic passes through Cloudflare and
Render's load balancers, your app sees the proxy's IP by default." There are therefore at least
two proxy layers, and Render does not publish the ingress addresses of either. (Its documented
IP ranges are *outbound* only and must not be substituted.) A CIDR allowlist cannot be filled
in correctly against infrastructure whose addresses are undocumented and subject to change.

`TRUSTED_PROXY_HOPS` was added as the alternative. It trusts a fixed *number* of proxies rather
than their addresses: with N hops the client is the Nth entry from the right of the forwarded
chain. This is correct for the same reason the right-to-left walk is — each proxy appends the
peer it received from, so entries further left are client-supplied. Cloudflare appends rather
than replaces, so the *first* entry is attacker-controlled and must never be read as the client,
a common misconfiguration.

Behaviour: a chain shorter than the configured hop count, or a malformed entry at the trusted
position, falls back to the socket peer instead of a spoofable value. `TRUSTED_PROXY_CIDRS`
still works and takes effect when hops is 0. On Render, startup now fails unless one of the two
is configured. Bounds are 0..10.

Still unverified: the actual hop count for this service. It is one deploy away — set
`LOGGING_LEVEL_COM_UNCOMPLEX_RATELIMIT=DEBUG`, POST to `/api/roadmaps`, and read the
`x-forwarded-for` value now included in the debug line. No value has been guessed, and the
Blueprint requires it to be entered explicitly.

Verification: `mvnw.cmd -B -ntp verify` passed; 83 tests, 72 passed, 11 skipped (unchanged
Testcontainers suites), no failures. Seven new tests cover hop selection, the spoofed prefix,
two-proxy chains, short chains, a missing header, a malformed entry at the trusted hop, the
startup guard, and the bounds check.

References: [Render DDoS/client IP guidance](https://render.com/articles/how-render-handles-ddos-attacks),
[Cloudflare HTTP headers](https://developers.cloudflare.com/fundamentals/reference/http-headers/).

## External audit follow-up — 6 September 2026

An independent audit of commit `4077555` found eight issues. All are fixed on this branch;
each has a regression test except the Docker one, which is verified in CI instead.

- **Cache key overflowed its column.** `topic` and `context` each accept 120 characters, so
  `topic|context|level|goal` reaches 266 against a `VARCHAR(255)` column — a 500 on input the
  API had already accepted. V5 widens the column to 600 rather than hashing or truncating the
  key, because changing the key format would orphan every roadmap already generated.
- **Passwords between 73 and 100 characters returned 500.** BCrypt refuses more than 72
  *bytes*, which a character count cannot express: 40 accented characters are 80 bytes. Added
  a `@MaxUtf8Bytes` constraint so these are rejected as validation errors.
- **Docker Compose mounted the wrong path for PostgreSQL 18.** 18 moved `PGDATA` to
  `/var/lib/postgresql/18/docker`, so the volume belongs on `/var/lib/postgresql`; the old
  `/var/lib/postgresql/data` mount left the real data directory in the container's writable
  layer, where it did not survive recreation. Nobody working on this has Docker locally, which
  is why it went unnoticed, so CI now starts the Compose database, writes a row, recreates the
  container and reads the row back.
- **Leaving during generation dragged the user back.** Navigation is now guarded by a mount
  flag, and the progress note no longer claims that leaving cancels the request, which was
  never true. The flag is set on mount as well as cleared on unmount — StrictMode runs effects
  mount/cleanup/mount, and initialising it alone leaves it false forever.
- **A failed account deletion destroyed the session.** Credentials were cleared in a `finally`,
  so a transient 503 logged the user out of an account that still existed and the offered retry
  required signing in first. They are now cleared on success, or on a 401.
- **A logout in another tab left the previous library on screen.** The library route is keyed on
  the account, like the roadmap route already was.
- **The in-memory rate limiter never evicted anything.** Client addresses were retained for the
  process lifetime, growing with unique visitors and contradicting the privacy policy. Entries
  are now swept once a full window passes with no further requests from that address; the
  privacy page describes the real behaviour.
- **Long emails overflowed phone viewports.** The header wraps, the email truncates, and below
  420px it is hidden. Regression tests assert no horizontal overflow at 320px and 390px.

Verification: 97 backend tests (9 new), 16 browser tests (5 new), 7 frontend API tests, lint
and production build. PostgreSQL and Redis Testcontainers suites and the Compose check run in
CI, not locally — Docker is unavailable here.

### Follow-up: eviction race in the in-memory limiter

The eviction added above introduced a concurrency bug, found by a second review.

`entrySet().removeIf` on a `ConcurrentHashMap` guards removal with
`replaceNode(k, null, v)` — remove only if the value is still the one the predicate saw.
`Entry` is mutated in place, so an entry refreshed between the predicate and the removal
still matched that value and was dropped anyway. The client's next request then built a
brand-new full bucket, handing back an allowance it had already spent inside its own window.

The sweep now rechecks and removes each key inside `computeIfPresent`, which takes the same
per-key lock as `tryConsume`'s `compute`, so the expiry test and the removal cannot straddle
a concurrent refresh.

No regression test accompanies this one, deliberately. The harmful interleaving needs a
refresh to land in the microseconds between `removeIf`'s predicate and its internal
`replaceNode` call. Two attempts — a hand-advanced clock, then a real-time multi-threaded
stress run — both passed against the known-broken implementation, which makes them worse
than no test at all. The reviewer's reproduction needed reflection to pause the sweeping
thread mid-call; that is a sound verification technique but too dependent on JDK internals
to commit to CI. `computeIfPresent` closes the race by construction instead.

The privacy page previously said counters are "discarded once a full limit window has
passed". Cleanup actually runs on a later request, at most once per window, so an idle
service keeps an eligible counter until traffic resumes. The page now says that.
