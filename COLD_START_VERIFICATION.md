# Homepage warm-up and startup measurements

Verified locally on 8 September 2026, after PR #8.

Opening the homepage now starts one anonymous `GET /actuator/health` per document
load. The visitor can fill and submit the form while that request is pending.
The probe has a 180-second deadline, bypasses the browser cache, omits credentials,
and silently tolerates failures. React StrictMode, route remounts, and elapsed time
do not create additional probes. There is no periodic keep-alive or AI call.

The development proxy forwards the health request to the backend. The deployed
backend allows that exact health path from its configured frontend origins.

## Lazy initialization decision

Global lazy initialization remains **off**. The local change in time to the first
roadmap was small and the measurements overlap; these results do not establish a
useful improvement on Render.

Three fresh JVM processes per mode, interleaved eager/lazy/lazy/eager/eager/lazy:

| Mode | Started log, median | Health response, median | First roadmap response, median |
| --- | ---: | ---: | ---: |
| Normal startup | 6.185 s | 6.445 s | 6.696 s |
| Lazy startup with eager configuration checks | 5.759 s | 6.270 s | 6.533 s |

First-roadmap samples: normal **6.637, 6.696, 7.342 s**; lazy **6.316, 6.533, 6.962 s**.
The median difference is **0.163 s (about 2.4%)**. This is not a prediction of a
2.4% improvement on Render.

Each measurement starts at process launch, waits for the application startup log,
requests health, generates a fresh mock roadmap, and verifies its public share
link. Timings include HTTP response bodies. The packaged Java 21 application uses
an in-memory H2 database, mock AI, a 384 MiB heap, and no scheduled link checks.
These are JVM restarts on a Windows development machine with warm OS disk caches,
not Render container wake-ups. They exclude Neon latency and real AI generation.

The proxy guard already failed before startup with lazy initialization. Without
explicit eager initialization, short JWT secrets and missing Anthropic keys were
deferred. The rate-limit filter, JWT encoder, and Anthropic client are now marked
`@Lazy(false)`, and three integration tests prove that invalid settings still fail
before `SpringApplication.run` returns with lazy initialization enabled.

## Validation

- Backend `mvn verify`: **102 discovered, 91 passed, 11 Docker-dependent tests skipped**.
- Frontend API tests: **13 passed**.
- Browser suite: **18 passed**, including generation during a pending warm-up,
  route remounts, no periodic requests, and session preservation on probe failure.
- Lint and production build passed.
- Allowed-origin health requests return `200` and the CORS header; unlisted origins
  remain rejected. The startup benchmark also checked CORS over real local HTTP.

## Reproduce

With Java 21 and Node 22 available, from the repository root:

```sh
./mvnw -B verify
node scripts/benchmark-startup.mjs
node scripts/benchmark-startup.mjs --guards
```

On Windows, use `mvnw.cmd` or an installed Maven executable. The script honors
`JAVA_HOME` or an explicit `BENCHMARK_JAVA` executable path. Logs and JSON results
are written under `target/`. It contacts only localhost and uses mock AI.

Render end-to-end measurements require deploying the change and comparing repeated
idle wake-ups. Until that is measured, the homepage probe overlaps startup with
the visitor reading and typing; it does not guarantee an instant first generation.
