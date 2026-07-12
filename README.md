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

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/roadmaps` | Generate (or serve cached) roadmap. Rate limited. |
| `GET` | `/api/roadmaps/public/{shareToken}` | Open a shared roadmap. Public, never rate limited. |
| `GET` | `/actuator/health` | Health probe. |

`experienceLevel`: `BEGINNER` `INTERMEDIATE` `ADVANCED` — `goal`: `GENERAL_UNDERSTANDING` `BUILD_A_PROJECT` `SYSTEM_DESIGN_INTERVIEW` `JOB_INTERVIEW` `UNIVERSITY_COURSE`

Errors follow RFC 9457 problem details (`400` validation, `404` unknown token, `429` rate limit, `502` generation failure).

## Architecture

Modular monolith — one Spring Boot application with strict package boundaries, so modules could be extracted later without a rewrite:

```
com.uncomplex
├── roadmap      controller / service / repository / entity / dto / mapper
├── ai           provider-agnostic generation + output validation
├── resource     resource credibility (URL allowlist)
├── ratelimit    Bucket4j servlet filter
├── config       typed @ConfigurationProperties + AI provider wiring
└── exception    RFC 9457 problem-detail handling
```

### Design decisions (and why)

**The AI is treated as an untrusted dependency.** This is the load-bearing design choice:

1. **Structured outputs, not prompt-and-pray.** The Anthropic SDK constrains the model's response to the `RoadmapDraft` JSON schema (derived from Java records). No hand-rolled JSON parsing, no "please respond in JSON" prompting.
2. **Schema-valid ≠ trustworthy.** `RoadmapDraftValidator` re-checks everything the schema can't express: 4–8 prerequisites, non-blank fields, clamped time estimates, ordering. Invalid output is retried once, then fails with a clean `502`.
3. **AI-generated URLs are never trusted.** Every resource link must be `https` on a configurable allowlist of credible domains (official docs, standards bodies, `*.edu`). Anything else is silently dropped — a hallucinated link can never reach a user.

**One AI call per (topic, level, goal) — ever.** Requests are normalized into a cache key (`rate-limiting|beginner|system_design_interview`) with a unique DB constraint. Repeat requests and shared-link opens are pure reads. A race between concurrent first requests is resolved by the constraint, not by locks.

**Anonymous by design (milestone 1).** The unguessable share token *is* the access control, which makes the product fully usable with zero accounts. JWT auth + owned roadmaps + progress tracking are milestone 2 — deliberately cut so milestone 1 ships finished.

**Provider-agnostic AI port.** `AiRoadmapGenerator` is an interface; the Anthropic implementation is selected by configuration, and a deterministic mock keeps the app runnable and testable with no API key and no network.

**Why not microservices/Kafka/Kubernetes?** One team, one database, one deployable. The module boundaries keep extraction possible; the operational cost of distribution buys nothing at this scale.

**In-memory rate limiting** (Bucket4j, per client IP, 10 generations/day) is a documented single-instance trade-off — the filter is one class swap away from a Redis-backed bucket store for multi-replica deployments.

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

The credible-domain allowlist lives in `application.yml` under `app.credibility.allowed-domains`.

## Roadmap (the product's own)

- **Milestone 2** — JWT authentication, owned roadmaps (`GET/PATCH/DELETE /api/roadmaps/{id}`), per-node progress tracking
- **Milestone 3** — React/TypeScript frontend, resource link liveness checking (scheduled `HEAD` probes), Redis-backed rate limiting
- **Later** — per-node regeneration, FR/EN bilingual content, dependency graph view
