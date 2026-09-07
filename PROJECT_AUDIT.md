**Project assessment — 6 September 2026**

Reviewed commit `4077555`. The core MVP features described in README.md and PROGRESS.md are implemented and work through the main local user journey. The project is not fully reliable yet: the review found a Docker startup configuration problem, reproducible input failures, browser recovery issues, and a mobile layout problem. No application source was changed during this review.

**Verification performed**

| Check | Result |
| --- | --- |
| Backend Maven `verify` | Build passed: 88 tests discovered, 77 passed, 11 skipped, no failures/errors. |
| Frontend API tests | All 7 passed. |
| Frontend lint and production build | Both passed, including TypeScript compilation. |
| Existing Playwright tests | All 11 passed with installed headless Chrome. The test dev server required manual shutdown to finish teardown in this environment. |
| Actual browser connected to local API | Registration, generation, automatic library save, resources, completion checkboxes, progress after reload, anonymous shared view, library removal, and account deletion passed. No page JavaScript errors. |
| Local HTTP journey | Generation/cache reuse, public reads, registration, save, progress, listing, refresh rotation, removal, account deletion, and retention of the public roadmap after deletion passed. |
| Published services | Website and API health returned HTTP 200; health reported UP. One registration check also succeeded against the deployed API. |

Local generation used deterministic mock AI and H2. Docker is unavailable, so the PostgreSQL and Redis Testcontainers suites were skipped. Real Anthropic generation, production proxy configuration, Docker Compose startup, and remote CI were not verified. Successful health checks do not establish those results.

The Windows Maven wrapper failed before starting Maven with `Cannot index into a null array`. Verification succeeded using its cached Maven 3.9.9 distribution directly, portable Java 21, and an explicit existing Maven repository path. This is a local setup limitation worth addressing separately.

**Findings, in recommended repair order**

1. **High: Docker Compose uses the wrong PostgreSQL 18 volume location.**

   `docker-compose.yml:3` selects `postgres:18-alpine`, while line 11 mounts `pgdata` at `/var/lib/postgresql/data`. PostgreSQL 18's official image uses `/var/lib/postgresql/18/docker` for data and expects the volume at `/var/lib/postgresql`. Its entrypoint detects the old, unused mount and rejects clean initialization. The API depends on a healthy database, so this blocks the documented Compose startup path. This is a configuration finding verified against the [official image documentation](https://github.com/docker-library/docs/blob/master/postgres/README.md#pgdata) and [entrypoint](https://github.com/docker-library/postgres/blob/master/docker-entrypoint.sh), rather than a locally executed Docker result. Update the mount and verify initialization and persistence; inspect/migrate any existing database before changing its layout.

2. **Medium: valid topic/context lengths produce HTTP 500.**

   `GenerateRoadmapRequest.java:13` and `:23` allow 120 characters each. `CacheKeys.java:31` concatenates both values with level and goal, but `V1__init.sql:3` defines a 255-character cache column. Reproduction: topic `a` repeated 120 times, context `b` repeated 120 times, level BEGINNER, goal BUILD_A_PROJECT. The API returned 500; the server logged a 266-character cache key exceeding `VARCHAR(255)`. Align the key representation and database capacity with valid inputs through a new migration or a compatible bounded key strategy.

3. **Medium: accepted registration passwords can fail with HTTP 500.**

   `auth/dto/RegisterRequest.java:11` accepts 8–100 characters, while the configured BCrypt encoder rejects passwords exceeding 72 UTF-8 bytes. Both a 73-character ASCII password and 40 copies of `é` reproduced HTTP 500. The exception was `password cannot be more than 72 bytes`. Validate the actual encoder limit and return a useful validation error, or adopt an encoding strategy that supports the advertised range. Update the form guidance to match.

4. **Medium: leaving generation does not cancel it and can unexpectedly change the page.**

   `frontend/src/pages/LandingPage.tsx:29` awaits generation and unconditionally navigates at line 36. There is no cancellation or unmount guard, although `GenerationProgress.tsx:44` tells the user that leaving cancels the request. Browser reproduction: start generation, navigate to Privacy, then complete the delayed generation response. The browser leaves Privacy and opens the roadmap. Guard navigation after unmount and define cancellation behavior accurately; aborting the browser request alone does not guarantee cancellation of server-side AI work.

5. **Medium: temporary account-deletion failures destroy the local session.**

   `frontend/src/api.ts:233` clears credentials in `finally`, including when DELETE `/api/me` fails. With a mocked 503, the UI showed “Could not delete your account. Please try again.” while both access and refresh tokens were already gone. The account remains, and the promised retry requires signing in again. Preserve the session on transient deletion failures; clear it when deletion succeeds or authentication is conclusively invalid.

6. **Medium: another tab's logout leaves the previous account's library visible.**

   `frontend/src/App.tsx:53` mounts LibraryPage independently of the current account, and `LibraryPage.tsx:39` reloads only for navigation/retry changes. In a two-tab reproduction, clearing credentials in tab B updated tab A's header to “Log in,” but tab A continued displaying the previous account's saved roadmap and progress. Subscribe library state to account changes and clear/reload it when the account changes. The backend still enforces authorization; this finding concerns stale browser state, not demonstrated unauthorized server access.

7. **Medium: the default in-memory rate limiter never removes client identifiers.**

   `ratelimit/InMemoryRateLimiter.java:16` keeps an unbounded `ConcurrentHashMap`; line 29 adds each client and there is no eviction. Refilling a bucket does not remove its IP key. This retains client identifiers for the process lifetime and grows memory with unique visitors, contrary to the privacy page's claim that counters expire within a day. This is established by code inspection. Add bounded retention that preserves the intended rate-limit behavior, or use the Redis implementation with TTLs and matching operational configuration.

8. **Medium: signed-in pages can overflow phone-width screens.**

   During the actual browser journey, the roadmap's document width was 481 px at both 320 px and 390 px viewports, using the valid long audit email shown in the screenshot. Widths of 768 px and 1440 px fit. A focused landing-page reproduction confirmed the navigation and logout button extending to x=481 and the email to x=438 on a 390 px viewport. `frontend/src/index.css:77`, `:109`, and `:124` provide no adequate small-screen handling for the header's navigation and email. A short-email control fit at 390 px, so this is content-dependent rather than universal. Add responsive wrapping/truncation and verify long emails plus expanded roadmap cards. Screenshots: [audit-mobile.png](target/audit-mobile.png), [audit-roadmap.png](target/audit-roadmap.png); measurements: [audit-ui.log](target/audit-ui.log).

**Completeness and documentation**

Anonymous generation/sharing, accounts, library membership, per-node progress, refresh tokens, configurable rate limiting, link-health scheduling, privacy/disclosure pages, and account deletion are present. Per-node regeneration, bilingual content, and a dependency graph remain explicitly deferred features. GitHub profile polish and résumé work are also listed as unfinished, but do not block the application.

The README's deployment section still describes an expiring Render database, while `render.yaml` and the privacy page describe Neon. PROGRESS.md and parts of FIXES.md contain older test counts and verification dates. These should be reconciled after the functional fixes; an old completed checklist should not substitute for current verification.

**Review artifacts and side effects**

The backend log is [audit-backend.log](target/audit-backend.log). A reusable browser smoke script and its output are [audit-ui.mjs](target/audit-ui.mjs) and [audit-ui.log](target/audit-ui.log). These target-directory artifacts are ignored by Git and may be removed by a clean build.

An existing localhost development server was configured to use the deployed API. A browser smoke check reached it while testing the documented localhost URL. The known temporary account `browser-audit-1788751806416@example.com` was deleted successfully (204); another attempted address returned 401 on login. One successful `audit-debug-…@example.com` registration remains on the live API because that check did not capture its full generated address or retain its session. No live AI generation was requested. Subsequent functional checks used an explicitly addressed local server with mock AI. Local test accounts and roadmaps were confined to its disposable in-memory database.
