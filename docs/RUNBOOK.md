# Startup Runbook — Local, QA, Production

How to get Cronos running via Docker in each environment. Every environment ends the same way —
one `docker compose` command — but the setup you do *before* that command differs per environment.
Read [`context.md`](../context.md)'s "Infrastructure & DevOps" section for the *why* behind these
choices; this doc is only the *how*.

**Fastest path:** if you just want it running and don't need the explanation, skip to the
[Quick reference](#quick-reference) table at the bottom and run the matching script in `scripts/`.

## How secrets work here (read this first)

Two independent mechanisms exist in this repo, and every environment below uses exactly one of them:

1. **`.env`** — plain environment variables (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `MAIL_PASSWORD`,
   `JWT_SECRET`), loaded by Docker Compose's `env_file:`. This is what `docker-compose.yml` (local
   dev) uses by default.
2. **`keys.properties`** — an external properties file loaded by the application itself, at JVM
   startup, via `KeysPropertiesEnvironmentPostProcessor`
   (`src/main/java/.../infrastructure/config/secrets`). This is what the `qa` and `prod` Spring
   profiles use, and it's **mandatory** for them — the app refuses to start if the file is missing
   on those profiles, rather than booting with blank secrets. It's also usable locally as an
   alternative to `.env` (see `docker-compose.keys-file.yml`) if you already keep one around.

Never commit either file. Both are `.gitignore`d; only their `*.example` templates are tracked.
If you ever find `.env` or `keys.properties` showing up in `git status` as trackable, stop and fix
`.gitignore` before running any `git add` — that means real secrets are one commit away from
landing in the repository.

---

## Local development

Runs everything in Docker with hot reload: edit a `.java` file, save, and the running app
recompiles and restarts in a few seconds — no manual rebuild.

### 1. Prerequisites

- Docker Desktop (or Docker Engine + the `docker compose` v2 plugin) running.
- A Neon (or any Postgres) connection string. Local dev does **not** run its own Postgres
  container — the primary DB is external, see `docker-compose.yml`'s header comment.

### 2. Configure secrets — pick ONE of the two options below

**Option A — quick start, no existing `keys.properties`:**

```bash
cp .env.example .env
```

Edit `.env` and fill in real values:
- `DB_URL` — your Neon connection string, must end in `?sslmode=require`
- `DB_USERNAME` / `DB_PASSWORD`
- `JWT_SECRET` — any 256-bit base64 string, e.g. `openssl rand -base64 32`
- `MAIL_PASSWORD` — unused locally (dev profile routes mail to Mailpit instead), leave as-is
- `GRAFANA_ADMIN_PASSWORD` — optional, defaults to `admin` if left unset/unchanged

**Option B — you already maintain a `keys.properties` for qa/prod-style local testing:**

Leave `DB_URL`/`DB_USERNAME`/`DB_PASSWORD`/`MAIL_PASSWORD`/`JWT_SECRET` out of `.env` entirely
(only `GRAFANA_ADMIN_PASSWORD` needs to live there, if you want to override it), and instead run
with the extra overlay file:

```bash
CRONOS_KEYS_FILE_HOST=/path/to/your/keys.properties \
  docker compose -f docker-compose.yml -f docker-compose.keys-file.yml watch
```

(This is exactly what `scripts/start-local.sh` does *not* automate — it always uses Option A. Use
the command above directly if you want Option B.)

### 3. Start it

```bash
docker compose watch
```

or:

```bash
./scripts/start-local.sh
```

First run builds the image (a couple of minutes — downloads base images, resolves Maven
dependencies). Subsequent runs are fast. Leave this running in its own terminal; `Ctrl+C` stops it.

### 4. Verify

- API: `curl http://localhost:9191/api/v1/actuator/health` — wait, actuator is on its own port:
  `curl http://localhost:9192/actuator/health` from *inside* the `app` container only (that port is
  intentionally not published to the host — see `context.md`). From the host, hit a real endpoint
  instead, e.g. `POST http://localhost:9191/api/v1/auth/login`.
- Mailpit (intercepts all outgoing email): http://localhost:8025
- Grafana (logs + metrics): http://localhost:3000 — `admin` / whatever you set
  `GRAFANA_ADMIN_PASSWORD` to (or `admin` if you didn't set it)
- Prometheus: http://localhost:9090 — Status → Targets should show `cronos` as `UP`

### 5. Edit code and watch it reload

Change something under `src/main/java` or `src/main/resources`, save. Compose syncs the file into
the running container and restarts the app process (recompiles via `mvnw spring-boot:run` — not a
full Docker image rebuild). Takes a few seconds; watch the terminal running `docker compose watch`.

Changing `pom.xml` (new dependency) triggers a full image rebuild instead — expected, slower.

### 6. Stop it

`Ctrl+C` in the terminal running `docker compose watch`, then optionally:

```bash
docker compose down          # stop and remove containers, keep data (Redis/Grafana/Loki volumes)
docker compose down -v       # also wipe those volumes — full reset
```

---

## QA

Runs the actual packaged image (not `mvn spring-boot:run`) with `SPRING_PROFILES_ACTIVE=qa`,
against a real (qa) Neon database and a self-hosted Redis. No Mailpit — real SMTP.

### 1. Prerequisites

- Docker + Compose v2, on whatever host runs qa (your machine, or a qa server).
- A `keys.properties` file with real qa values. **This is mandatory** — the class docs on
  `KeysPropertiesEnvironmentPostProcessor` explain why (fail-fast on `qa`/`prod`, no silent
  half-configured boot).

### 2. Create the qa `keys.properties`

```bash
cp keys.properties.example /somewhere/outside/this/repo/qa-keys.properties
chmod 600 /somewhere/outside/this/repo/qa-keys.properties
```

Edit it with real qa values: `DB_URL` (qa Neon branch, `?sslmode=require`), `DB_USERNAME`,
`DB_PASSWORD`, `MAIL_PASSWORD`, `JWT_SECRET`. Put it **outside** this git working directory — it
must never be a path git tracks.

### 3. Start it

```bash
CRONOS_KEYS_FILE_HOST=/somewhere/outside/this/repo/qa-keys.properties \
  docker compose -f docker-compose.qa.yml up -d --build
```

or:

```bash
./scripts/start-qa.sh /somewhere/outside/this/repo/qa-keys.properties
```

`up -d --build` (not `watch`) — qa runs the packaged jar, not hot-reload. To pick up a code
change, re-run the same command; it rebuilds the image and recreates the container.

### 4. Verify

```bash
docker compose -f docker-compose.qa.yml ps
docker compose -f docker-compose.qa.yml logs -f app     # watch it boot; Ctrl+C to stop following
```

Same Grafana (`:3000`)/Prometheus (`:9090`) URLs as local. API on `:9191/api/v1`.

### 5. Stop it

```bash
docker compose -f docker-compose.qa.yml down
```

---

## Production

Same shape as qa, `SPRING_PROFILES_ACTIVE=prod`. Two things are different on purpose, and matter:

- `keys.properties` defaults to `/etc/cronos/keys.properties` inside the container — that's the
  exact path `KeysPropertiesEnvironmentPostProcessor`'s prod fallback already expects (see its
  class docs), so `docker-compose.prod.yml` sets `CRONOS_KEYS_FILE` explicitly to that path rather
  than relying on the implicit default — one less thing to get wrong if that default ever changes.
- `GRAFANA_ADMIN_PASSWORD` has **no default** — `docker-compose.prod.yml` refuses to start without
  it explicitly set (no `admin`/`admin` in prod).

**Before you run this on a real host**, read the `SECURITY` comment block at the top of
`docker-compose.prod.yml` — it covers exposing Grafana/Prometheus/Loki ports publicly, keys.properties
file permissions, and the `gcp-cronos-key.json` build-time baking issue documented in `context.md`.

### 1. Prerequisites

- Docker + Compose v2 on the production host.
- A `keys.properties` file with real production values, mode `600`, owned by whatever account runs
  Docker, living outside any git-tracked or web-served directory (e.g. `/etc/cronos/keys.properties`
  on the host itself, or wherever your secrets-management tooling drops it).

### 2. Create the production `keys.properties`

```bash
sudo mkdir -p /etc/cronos
sudo cp keys.properties.example /etc/cronos/keys.properties
sudo chmod 600 /etc/cronos/keys.properties
sudo chown <the-user-running-docker> /etc/cronos/keys.properties
```

Edit it with real production values. `kms.provider` should be `gcp`/`aws`/`azure` here, not `local`
— see `keys.properties.example`'s comments and `KmsConfig`.

### 3. Start it

```bash
export CRONOS_KEYS_FILE_HOST=/etc/cronos/keys.properties
export GRAFANA_ADMIN_PASSWORD="$(openssl rand -base64 24)"   # save this, shown once
docker compose -f docker-compose.prod.yml up -d --build
```

or:

```bash
./scripts/start-prod.sh /etc/cronos/keys.properties
```

(The script auto-generates and prints `GRAFANA_ADMIN_PASSWORD` for you if you don't export one.)

### 4. Verify

```bash
docker compose -f docker-compose.prod.yml ps
docker compose -f docker-compose.prod.yml logs -f app
```

### 5. Deploying a new version

```bash
git pull
docker compose -f docker-compose.prod.yml up -d --build
```

Rebuilds the image from the current source and recreates only the containers whose config/image
changed (Redis/Loki/Grafana keep running, keep their data).

### 6. Stop it

```bash
docker compose -f docker-compose.prod.yml down     # keeps volumes (Redis data, Grafana dashboards, logs)
```

---

## Quick reference

| Environment | Config needed | Command | Or run |
|---|---|---|---|
| Local | `.env` from `.env.example` | `docker compose watch` | `./scripts/start-local.sh` |
| QA | `keys.properties` (any path) | `CRONOS_KEYS_FILE_HOST=<path> docker compose -f docker-compose.qa.yml up -d --build` | `./scripts/start-qa.sh <path>` |
| Production | `keys.properties` at `/etc/cronos/keys.properties` | `CRONOS_KEYS_FILE_HOST=<path> docker compose -f docker-compose.prod.yml up -d --build` | `./scripts/start-prod.sh <path>` |

## Troubleshooting

- **`required variable ... is missing a value`** on `docker compose up`/`watch` — you're missing an
  env var the compose file requires (`CRONOS_KEYS_FILE_HOST`, or `GRAFANA_ADMIN_PASSWORD` in prod).
  The error message names which one.
- **App container restarts in a loop** — check `docker compose logs app`. If it's
  `IllegalStateException: No se encontro el archivo de credenciales`, your `keys.properties` path is
  wrong or the file doesn't exist at that path *inside the container* (check the volume mount, not
  just the host path).
- **`AUTHENTICATION_FAILED` on every request, including `/actuator/health`** — this was a real bug
  found while building this stack (see `context.md`'s "Actuator" section): make sure
  `SecurityConfig`'s `permitAll()` list includes `/actuator/**`.
- **Login works but `/users/me` returns 401 right after** — check Redis connectivity
  (`docker compose logs redis`, and `TokenBlacklistService`/`JwtAuthenticationFilter` logs in `app`)
  — this exact symptom was diagnosed earlier from a `RedisConnectionFailureException` when Redis
  wasn't reachable.
- **A correct password suddenly 401s for one specific account** — check `app` logs for `Failed to
  decrypt field due to GCM tag mismatch`. That means `email`/`twoFactorSecret` for that row can't be
  decrypted with the current data-encryption-key (rotated/lost key, or genuine corruption) — see
  `context.md`'s "Bypass JPA entity hydration" ADR. Not a bug in the request; a data problem on that
  one row.
