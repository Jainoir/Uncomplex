# Uncomplex

**Enter anything you want to learn — Uncomplex builds a credible, shareable roadmap of everything you should understand *first*.**

Most learning tools answer *"how do I learn X?"*. Uncomplex answers the question that actually blocks people: *"why doesn't X make sense to me yet?"* — which is almost always missing prerequisites. You give it a topic, your experience level, and your goal; it returns an ordered checklist of the 4–8 concepts to learn before the topic itself, each with a short explanation, why it matters, a time estimate, and links from credible sources only.

```text
Topic: Rate limiting · Level: BEGINNER · Goal: SYSTEM_DESIGN_INTERVIEW

1. Client-server architecture   (30 min)
2. HTTP requests                (45 min)
3. REST APIs                    (60 min)
4. Caching basics               (45 min)
5. Distributed systems basics   (90 min)
→ now you're ready for rate limiting
```

Every roadmap gets a public share link (`/api/roadmaps/public/rate-limiting-k7p4x`) that anyone can open — no account needed.

## Tech stack

Java 21 · Spring Boot 3.5 · Spring Data JPA / Hibernate · PostgreSQL + Flyway · Anthropic Claude API (structured outputs) · Bucket4j rate limiting · JUnit 5 + Mockito + Testcontainers · Docker · GitHub Actions

## Quick start

**No dependencies at all** (in-memory H2 database + deterministic mock AI):

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

**Full stack** (PostgreSQL via Docker, real AI generation):

```bash
export AI_PROVIDER=anthropic
export ANTHROPIC_API_KEY=sk-ant-...
docker compose up --build
```

Then:

```bash
curl -X POST http://localhost:8080/api/roadmaps \
  -H "Content-Type: application/json" \
  -d '{"topic":"Rate limiting","experienceLevel":"BEGINNER","goal":"SYSTEM_DESIGN_INTERVIEW"}'

# open the share link from the response — no auth required
curl http://localhost:8080/api/roadmaps/public/rate-limiting-k7p4x
```

Run the tests: `./mvnw verify` (the PostgreSQL/Testcontainers test is skipped automatically when Docker is absent and runs in CI).

## API

**Public — no account needed:**

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/roadmaps` | Generate (or serve cached) roadmap. Rate limited. With a JWT, the result is also saved to your library. |
| `GET` | `/api/roadmaps/public/{shareToken}` | Open a shared roadmap. Never rate limited. |
| `POST` | `/api/auth/register` | Create an account (email + password ≥ 8 chars). |
| `POST` | `/api/auth/login` | Get an access token + refresh token. |
| `POST` | `/api/auth/refresh` | Rotate: exchange a refresh token for a fresh pair. |
| `POST` | `/api/auth/logout` | Revoke a refresh token. |
| `GET` | `/actuator/health` | Health probe. |

**Authenticated — `Authorization: Bearer <token>`:**

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/me/roadmaps` | My library, with per-roadmap progress counts. |
| `POST` | `/api/me/roadmaps` | Save any shared roadmap (`{"shareToken": "..."}`) to my library. |
| `GET` | `/api/me/roadmaps/{id}` | One roadmap + my progress overlay (completed node ids, percent). |
| `PUT` | `/api/me/roadmaps/{id}/nodes/{nodeId}/progress` | Mark a node `{"completed": true/false}`. Idempotent. |
| `DELETE` | `/api/me/roadmaps/{id}` | Remove from my library (the shared roadmap survives for others). |

`experienceLevel`: `BEGINNER` `INTERMEDIATE` `ADVANCED` — `goal`: `GENERAL_UNDERSTANDING` `BUILD_A_PROJECT` `SYSTEM_DESIGN_INTERVIEW` `JOB_INTERVIEW` `UNIVERSITY_COURSE`

Errors follow RFC 9457 problem details (`400` validation, `404` unknown token, `429` rate limit, `502` generation failure).

## Architecture

Modular monolith — one Spring Boot application with strict package boundaries, so modules could be extracted later without a rewrite:

```
com.uncomplex
├── roadmap      controller / service / repository / entity / dto / mapper
├── ai           provider-agnostic generation + output validation
├── resource     resource credibility (URL allowlist)
├── auth         register/login, JWT issuing (Spring Security resource server)
├── user         account entity + repository
├── library      saved roadmaps + per-node progress (the "ownership" model)
├── ratelimit    Bucket4j servlet filter
├── config       typed @ConfigurationProperties, security filter chain, AI wiring
└── exception    RFC 9457 problem-detail handling
```

### Design decisions (and why)

**The AI is treated as an untrusted dependency.** This is the load-bearing design choice:

1. **Structured outputs, not prompt-and-pray.** The Anthropic SDK constrains the model's response to the `RoadmapDraft` JSON schema (derived from Java records). No hand-rolled JSON parsing, no "please respond in JSON" prompting.
2. **Schema-valid ≠ trustworthy.** `RoadmapDraftValidator` re-checks everything the schema can't express: 4–8 prerequisites, non-blank fields, clamped time estimates, ordering. Invalid output is retried once, then fails with a clean `502`.
3. **AI-generated URLs are never trusted.** Every resource link must be `https` on a configurable allowlist of credible domains (official docs, standards bodies, `*.edu`). Anything else is silently dropped — a hallucinated link can never reach a user.

**One AI call per (topic, level, goal) — ever.** Requests are normalized into a cache key (`rate-limiting|beginner|system_design_interview`) with a unique DB constraint. Repeat requests and shared-link opens are pure reads. A race between concurrent first requests is resolved by the constraint, not by locks.

**Anonymous-first, accounts optional.** The unguessable share token *is* the access control for reading, so the product works with zero accounts. Accounts (JWT, stateless HS256 via Spring Security's resource-server support, BCrypt passwords) add a *library*: which roadmaps you follow and which nodes you've completed.

**Users own membership and progress — never the roadmap.** Because roadmaps are cached and shared across users, `PATCH /api/roadmaps/{id}` would let one user edit what another is reading. So shared roadmaps are immutable; ownership is modeled as a `saved_roadmap` join row plus per-user `node_progress` rows. Deleting "your" roadmap removes it from your library without touching anyone else's. Login failures return the same 401 for unknown email and wrong password (no account enumeration).

**Provider-agnostic AI port.** `AiRoadmapGenerator` is an interface; the Anthropic implementation is selected by configuration, and a deterministic mock keeps the app runnable and testable with no API key and no network.

**Why not microservices/Kafka/Kubernetes?** One team, one database, one deployable. The module boundaries keep extraction possible; the operational cost of distribution buys nothing at this scale.

**Rate limiting is pluggable** (`app.rate-limit.store`): an in-memory Bucket4j token bucket for single-instance deployments (default), or a Redis fixed-window counter (atomic `INCR` + first-write `EXPIRE`) shared across replicas — docker-compose runs the Redis mode. The trade-off is documented in the code: the fixed window permits a brief boundary burst but keeps the hot path to one round trip with no Lua scripting.

**Refresh tokens rotate, and reuse is treated as theft.** Login returns a short-lived JWT plus an opaque refresh token (only its SHA-256 hash is stored). Every `/api/auth/refresh` revokes the presented token and issues a new pair; presenting an already-rotated token is a replay signal, so *all* of that user's sessions are revoked.

**Dead links get caught after the fact.** The credibility allowlist filters URLs at generation time; a nightly scheduled job (`HEAD` probe, `GET` fallback for servers that reject `HEAD`) marks resources `reachable: true/false` in the API so a link that dies later is flagged instead of silently served.

## Testing strategy

| Layer | Tool | What it proves |
|---|---|---|
| Unit | JUnit 5 + Mockito | Validator rules, allowlist edge cases (incl. suffix-spoofed domains), cache-hit short-circuits the AI call, retry-then-fail behavior |
| Integration | `@SpringBootTest` + MockMvc + H2 | Full HTTP flow: generate → share → open link, validation errors, 404s, 429 rate limiting |
| Real database | Testcontainers PostgreSQL | Flyway migration + JPA mappings against actual Postgres (auto-skipped without Docker, runs in CI) |

## Configuration

| Variable | Default | Purpose |
|---|---|---|
| `AI_PROVIDER` | `mock` | `anthropic` for real generation |
| `ANTHROPIC_API_KEY` | — | Required when `AI_PROVIDER=anthropic` |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | localhost Postgres | Database connection |
| `GENERATIONS_PER_DAY` | `10` | Per-client generation limit |
| `JWT_SECRET` | dev-only default | HS256 signing key (≥ 32 bytes). **Set this in every deployed environment.** |
| `TOKEN_TTL_MINUTES` | `60` | Access-token lifetime |
| `REFRESH_TOKEN_TTL_DAYS` | `30` | Refresh-token lifetime |
| `RATE_LIMIT_STORE` | `memory` | `redis` for multi-replica deployments (uses `SPRING_DATA_REDIS_HOST`/`_PORT`) |
| `LINK_HEALTH_ENABLED` | `true` | Nightly resource-link liveness probing |

The credible-domain allowlist lives in `application.yml` under `app.credibility.allowed-domains`.

## Roadmap (the product's own)

- ~~**Milestone 1** — anonymous generate / persist / share~~ ✅
- ~~**Milestone 2** — JWT authentication, saved-roadmap library, per-node progress tracking~~ ✅
- ~~**Milestone 3** — refresh token rotation with reuse detection, Redis-backed rate limiting, scheduled link liveness checking~~ ✅
- **Milestone 4** — React/TypeScript frontend
- **Later** — per-node regeneration, FR/EN bilingual content, dependency graph view
