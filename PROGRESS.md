# Uncomplex — Progress Tracker

Status as of **July 12, 2026**. Backend milestones 1–3 are complete, with every
commit verified by CI (including PostgreSQL and Redis integration tests via
Testcontainers): [Actions history](https://github.com/Jainoir/Uncomplex/actions).

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

## 🔲 What's left

### Needs my accounts / a decision (Claude can't do these alone)

- [ ] **Anthropic API key** — create at console.anthropic.com, then run with
      `AI_PROVIDER=anthropic` + `ANTHROPIC_API_KEY` to replace the mock generator
- [ ] **Deploy** — Railway/Render from the GitHub repo (Dockerfile is ready) + managed
      PostgreSQL; gives a live URL for the résumé
- [ ] **GitHub polish** — repo About description + topics
      (`spring-boot`, `java-21`, `anthropic`, `postgresql`, `redis`)

### Milestone 4 — Frontend (optional for a backend application)

- [ ] React/TypeScript app: landing page, roadmap view, shared-link view
- [ ] Auth state + progress interactions against the existing API
- [ ] Deploy (Vercel)

### Later / nice-to-have

- [ ] CI badge in README
- [ ] Per-node "give me a different resource" regeneration
- [ ] FR/EN bilingual content (relevant for Desjardins)
- [ ] Dependency graph view
- [ ] Résumé line update: add refresh-token rotation + Redis rate limiting
