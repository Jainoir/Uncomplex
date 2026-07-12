# Uncomplex — Progress Tracker

Status as of **July 12, 2026**. Milestones 1–4 are complete (backend + frontend),
with every commit verified by CI (including PostgreSQL and Redis integration tests
via Testcontainers): [Actions history](https://github.com/Jainoir/Uncomplex/actions).

## ✅ Milestone 1 — Anonymous generate / persist / share

- [x] Spring Boot 3.5 / Java 21 project with Maven wrapper
- [x] AI generation via Anthropic structured outputs (schema derived from Java records)
- [x] Provider-agnostic `AiRoadmapGenerator` port + deterministic mock (runs with no API key)
- [x] Output validation: 4–8 prerequisite bounds, clamped time estimates, retry on invalid output
- [x] Resource credibility allowlist — hallucinated / non-credible URLs never persisted
- [x] Relational model (`roadmap` → `roadmap_node` → `node_resource`) with Flyway `V1`
- [x] One AI call per topic/level/goal ever — cache key + DB unique constraint (race-safe)
- [x] Public share links (`/api/roadmaps/public/{shareToken}`), no account required
- [x] Per-client rate limiting on generation (Bucket4j), RFC 9457 problem-detail errors
- [x] `local` profile (H2 in-memory) — zero-dependency dev; PostgreSQL for real deployments
- [x] Dockerfile + docker-compose + GitHub Actions CI
- [x] Tests: unit + MockMvc integration + Testcontainers PostgreSQL (CI)

## ✅ Milestone 2 — Accounts, library, progress

- [x] Register / login with BCrypt password hashing (Flyway `V2`)
- [x] Stateless HS256 JWTs via Spring Security resource server (no third-party JWT lib)
- [x] Identical 401 for unknown email vs wrong password (no account enumeration)
- [x] Ownership model: shared roadmaps stay immutable; users own **library membership**
      (`saved_roadmap`) and **per-node progress** (`node_progress`)
- [x] `/api/me/roadmaps`: list with progress counts, save by share token, progress
      overlay detail, idempotent node completion, remove from library
- [x] Authenticated generation auto-saves to the caller's library
- [x] End-to-end journey test (two users, independent progress on one shared roadmap)

## ✅ Milestone 3 — Hardening

- [x] Refresh tokens: opaque, SHA-256 hashed at rest, rotation on every refresh (Flyway `V3`)
- [x] Token-reuse detection → revokes **all** of the user's sessions
      (with the `noRollbackFor` fix so the revocation survives the thrown 401)
- [x] Pluggable rate limiting: in-memory Bucket4j (default) or Redis fixed-window
      `INCR`+`EXPIRE` shared across replicas (`RATE_LIMIT_STORE=redis`)
- [x] Redis integration test via Testcontainers (runs in CI)
- [x] Nightly link liveness job: `HEAD` probe with `GET` fallback, `reachable` flag
      surfaced in the API
- [x] Redis service in docker-compose; optional Redis health indicator

## ✅ Milestone 4 — Frontend

- [x] React 19 + TypeScript + Vite app in `frontend/` (dev server proxies `/api` to :8080)
- [x] Landing page: topic + level + goal → generate → navigate to the roadmap
- [x] Roadmap / shared-link view (`/r/{shareToken}`): expandable prerequisite cards with
      explanations, "why first", resources with credibility labels and dead-link warnings,
      copy-share-link button
- [x] Auth (login / register) with JWT storage and automatic refresh-token rotation on 401
- [x] Library page: saved roadmaps with progress bars, remove; "save to my library" on
      shared roadmaps; per-node completion checkboxes with live progress updates
- [x] Backend CORS config for a separately-hosted frontend (`CORS_ALLOWED_ORIGINS`)
- [x] Frontend job in CI (npm ci + typecheck + production build)
- [x] CI badge in README
- [x] Verified end-to-end locally: production build clean, dev proxy → live API smoke-tested

## 🔲 What's left

### Needs my accounts (Claude can't do these alone)

- [x] **Anthropic API key** — created; live generation verified end-to-end on July 12, 2026
      (real Claude roadmap in ~31 s, repeat request served from cache in ~0.02 s).
      Key lives only in the environment, never in the repo.
- [x] **Deploy backend** — live at https://uncomplex-api.onrender.com (Render Blueprint:
      Docker web service + managed PostgreSQL). Verified July 12, 2026: health UP, real
      Claude generation, public share links, cache hits in production. Note: free tier
      sleeps after idle (~1 min wake) and the free database has an expiry date — check
      the Render dashboard before application deadlines.
- [x] **Deploy frontend** — live at https://uncomplex.vercel.app (Vercel, root `frontend/`,
      `VITE_API_BASE_URL` → the Render API, SPA rewrites for share links). CORS verified
      July 12, 2026: cross-origin generation from the Vercel origin returns 201 with the
      correct Access-Control-Allow-Origin header.
- [ ] **GitHub polish** — repo About description + topics
      (`spring-boot`, `java-21`, `react`, `anthropic`, `postgresql`, `redis`)
- [ ] **Résumé update** — suggested line below

> Built Uncomplex, a full-stack learning-roadmap platform (Spring Boot 3.5 / Java 21,
> React + TypeScript) that uses the Anthropic API with schema-constrained structured
> outputs to generate prerequisite learning paths with validated, credibility-filtered
> resources; features JWT auth with refresh-token rotation and reuse detection,
> PostgreSQL/Flyway persistence, Redis-backed distributed rate limiting, public sharing,
> per-user progress tracking, scheduled link liveness checks, 45+ unit/integration tests
> with Testcontainers, and Dockerized CI/CD.

### Later / nice-to-have

- [ ] Per-node "give me a different resource" regeneration
- [ ] FR/EN bilingual content (relevant for Desjardins)
- [ ] Dependency graph view
