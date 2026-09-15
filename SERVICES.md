# Services used by Uncomplex

Reviewed against the repository on **15 September 2026**. This is the account and
hosting reference for the finished project. Private dashboard settings, account
owners, and current billing plans have not been checked.

## Main accounts

| Service | What we use it for | Where to manage it | Project reference |
| --- | --- | --- | --- |
| **Render** | Hosts the Java / Spring Boot backend API. | [Render dashboard](https://dashboard.render.com/) — service `uncomplex-api`. | [API health](https://uncomplex-api.onrender.com/actuator/health); [deployment configuration](render.yaml). |
| **Vercel** | Builds and hosts the React frontend. | [Vercel dashboard](https://vercel.com/dashboard) — the project serving `uncomplex.vercel.app`, with root directory `frontend/`. | [Live website](https://uncomplex.vercel.app); [SPA routing](frontend/vercel.json). |
| **Neon** | Hosts PostgreSQL: accounts, generated roadmaps, saved libraries, progress, and refresh-token records. | [Neon console](https://console.neon.tech/) — find the project matching Render's `DB_URL`. The Neon project name is not recorded in the repo. | Connection is supplied to Render through `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` in [the Blueprint](render.yaml). |
| **Anthropic / Claude API** | Generates learning roadmaps when no saved result matches the request. | [Claude Console](https://platform.claude.com/) — API keys, usage, and billing. | Render uses `AI_PROVIDER=anthropic` and `ANTHROPIC_API_KEY`; model settings live in [application.yml](src/main/resources/application.yml). |
| **GitHub / GitHub Actions** | Stores the source and runs backend tests, frontend checks, browser tests, and the database persistence check. | [Jainoir/Uncomplex](https://github.com/Jainoir/Uncomplex); [Actions runs](https://github.com/Jainoir/Uncomplex/actions). | [CI workflow](.github/workflows/ci.yml). |

## Other dependencies

| Dependency | How it is used | Account to remember |
| --- | --- | --- |
| **Google Fonts** | The browser loads **Chakra Petch** and **Share Tech Mono** through the Google Fonts CSS endpoint. | No project credentials are configured; see the import in [index.css](frontend/src/index.css). |
| **Redis** | Optional shared rate limiting. Docker Compose starts Redis locally, and CI tests it. The Render Blueprint does not provision Redis or enable it; the application defaults to in-memory limits. | No hosted Redis provider is recorded. If `RATE_LIMIT_STORE=redis` has been set in the live dashboard, record that provider here. See [Compose](docker-compose.yml) and [application.yml](src/main/resources/application.yml). |
| **Docker / Docker Compose** | Packages the API for Render and runs PostgreSQL, Redis, and the API locally. CI also uses Docker for infrastructure tests. | No project registry credentials are configured; images are referenced in [Dockerfile](Dockerfile) and [Compose](docker-compose.yml). |
| **Cloudflare, through Render** | The repository's proxy configuration notes describe Cloudflare as part of Render's incoming traffic path. | No separately managed Cloudflare account or zone is recorded; see [render.yaml](render.yaml). |

## Settings to remember

### Vercel frontend

- Root directory: `frontend/`.
- Build command: `npm run build`; output directory: `dist`.
- `VITE_API_BASE_URL=https://uncomplex-api.onrender.com` connects the frontend to the API.
- Share links such as `/r/{shareToken}` depend on the rewrite in [vercel.json](frontend/vercel.json).

### Render backend

| Setting | What belongs here |
| --- | --- |
| `DB_URL` | Neon JDBC connection string: `jdbc:postgresql://<host>/<database>?sslmode=require`. The Blueprint specifies the direct endpoint. |
| `DB_USERNAME`, `DB_PASSWORD` | Credentials for that Neon database. |
| `AI_PROVIDER` | `anthropic` for real roadmap generation. |
| `ANTHROPIC_API_KEY` | The API key created in the Claude Console. |
| `JWT_SECRET` | Signing secret; the Blueprint generates this value. |
| `CORS_ALLOWED_ORIGINS` | The Blueprint sets `https://uncomplex.vercel.app,http://localhost:5173`. Update this if the frontend URL changes. |
| `TRUSTED_PROXY_HOPS` | Required proxy configuration for the Render deployment, unless `TRUSTED_PROXY_CIDRS` is configured instead. See [README reliability checks](README.md#reliability-checks). |
| `GENERATIONS_PER_DAY` | The Blueprint sets `10` per client. |

Secret values are stored in the Render environment; this inventory records only
their names. Changing the frontend API URL requires a new frontend build because
[the API client](frontend/src/api.ts) reads it through Vite's build-time environment.

## When returning to the project

- **Deployments and logs:** check Vercel for frontend issues and Render for backend issues; check GitHub Actions for failed builds or tests.
- **Database connection:** compare Render's database variables with the Neon project's connection details after a credential or project change.
- **AI generation:** check the Claude Console for API-key status, usage, and available billing balance or limits.
- **Hosting and costs:** `render.yaml` requests the `free` plan. Check each account's dashboard for its actual plan and usage; Vercel, Neon, and Anthropic billing settings are not stored here.
- **Slow first request:** the frontend starts one health request when the homepage opens to overlap backend startup with the visitor filling in the form. Startup measurements can be repeated using the commands in the [README](README.md#startup-measurements).
- **Account recovery:** keep the sign-in method and recovery details for Render, Vercel, Neon, Anthropic, and GitHub in your password manager.
- **Provider changes:** update this file when adding a service, changing the deployment URLs, or enabling hosted Redis.
