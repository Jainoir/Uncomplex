**PR #7 verification — 7 September 2026**

Reviewed `audit-fixes` at `22f4695cdc13c2ded52a845d0bddc2fdec1c0f46`, matching the current head of [PR #7](https://github.com/Jainoir/Uncomplex/pull/7). Seven of the eight original findings are resolved. The rate-limiter change fixes lifetime growth under continued traffic, but introduces a reproducible concurrency bug, so that item needs another correction.

| Original finding | Verification |
| --- | --- |
| 1. PostgreSQL 18 volume | Fixed. Correct parent-directory mount. CI actually initialized the Compose database, wrote a row, recreated the container, and reported `rows after recreate: 1`. |
| 2. Long topic/context cache key | Fixed. V5 widens the existing column without changing stored keys; the entity matches. Both new maximum-length regressions pass. CI applied V5 to real PostgreSQL. |
| 3. BCrypt password limit | Fixed. UTF-8 byte validation rejects 73 ASCII bytes and 40 accented characters with 400; 72 bytes remains accepted. |
| 4. Navigation after leaving generation | Fixed. The mount guard handles StrictMode and prevents a late response from navigating after unmount. Existing generation tests and the new regression pass. |
| 5. Failed deletion clears credentials | Fixed. Transient errors preserve the session; the new 503 browser regression passes. |
| 6. Cross-tab logout leaves library visible | Fixed. The library remounts on account changes. In addition to the committed simulated-event test, a separate check with two actual browser tabs confirmed that a storage event clears the library and redirects to authentication. |
| 7. In-memory limiter retention | Partially fixed. Eviction exists, but concurrent requests can lose their active bucket and obtain extra requests; details below. |
| 8. Mobile overflow with long email | Fixed. The committed 320/390 px tests pass. Additional checks with the longer original audit email and five realistic prerequisite names fit at 320, 390, and 480 px. |

**Remaining issue: eviction can reset an active client's budget (medium priority).**

In `src/main/java/com/uncomplex/ratelimit/InMemoryRateLimiter.java:71`, `entrySet().removeIf(...)` tests an entry's timestamp and then removes that entry. Meanwhile, `tryConsume` at lines 51–54 mutates the existing Entry object in place. The removal's identity check therefore cannot distinguish a newly active entry from the previously idle one it examined.

The failing interleaving is:

1. Cleanup sees client A's old timestamp and decides it is expired.
2. A concurrent request updates the same entry's `lastSeen` and consumes its refilled allowance.
3. Cleanup resumes and removes that same object even though it is now active.
4. A subsequent request creates a new full bucket and receives another allowance immediately.

A verification-only Java probe orchestrated this ordering using the actual map lock and unchanged production limiter code. With capacity one per second, it confirmed that the refreshed allowance was consumed and a further request denied before allowing cleanup to finish. The very next request then succeeded. Output:

```text
Sweeper paused at: java.base/java.util.concurrent.ConcurrentHashMap.replaceNode(ConcurrentHashMap.java:1123)
Recently active A retained after concurrent sweep: false
Extra request allowed within same one-second window: true
Time since consuming the refilled budget: 0 ms
```

Reproduction source: [AuditLimiterRace.java](target/AuditLimiterRace.java). Captured output: [audit-fixes-limiter-race.log](target/audit-fixes-limiter-race.log). These files are ignored build artifacts. The probe uses a one-second real-time window to avoid waiting a production day, and reflection only to control the ordering of concurrent operations. It does not modify the production implementation.

Recheck expiry and remove under the same per-key synchronization used by updates, for example with `computeIfPresent`. Add a concurrent expiry/update regression. The four new limiter tests are sequential and do not cover this interleaving.

There is also a small retention-wording caveat: cleanup runs only on a later request, at most once per window. An idle process does not automatically discard entries when their window ends, and an eligible entry can wait for another sweep. The privacy page's “discarded once a full limit window has passed” wording should describe this deferred cleanup if that behavior is retained.

**Independent test results**

The [GitHub Actions run](https://github.com/Jainoir/Uncomplex/actions/runs/34081312334) completed successfully. I read the job logs, rather than relying on the PR description:

- Backend CI: **97 tests, zero failures, zero errors, zero skips**, including real PostgreSQL and Redis. V5 was successfully applied.
- Compose CI: healthy database startup and persistence after container recreation passed. This exercises the database service, not a complete production deployment.
- Frontend CI: **16 browser tests passed**; API tests, lint, and production build passed.
- Fresh local runs: **86 backend tests passed, 11 skipped** because Docker is unavailable; **16 browser tests and 7 frontend API tests passed**; lint and build passed. The browser test dev server required manual shutdown during teardown in this environment, as in the earlier audit.

The Maven wrapper still fails with `Cannot index into a null array` in this execution environment. Its cached Maven distribution runs the full build successfully when invoked directly. This supports treating the wrapper observation as environment-specific, not as an application defect or evidence that Claude's local runs failed.

README's database description now agrees with the Neon configuration. PR #7 was still open and unmerged when checked. This verifies the branch and its CI; it does not establish that these changes have reached the live deployment. No application source was changed and no production accounts or roadmaps were created during this verification.
