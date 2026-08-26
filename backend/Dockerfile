# One Dockerfile, uvicorn behind the host's TLS terminator (architecture 10). Config is
# 12-factor, so CRYOSTASIS_DATABASE_URL is the only required variable at run time.

# The public site is built here rather than in a CI job of its own, so the image is the single
# thing a deploy pulls and the bundle can never be a version behind the API it reports on. Node
# stays in this stage; the runtime image below carries only the built files.
FROM node:20-slim AS site
WORKDIR /web
# Manifests first so the install layer caches across edits to the site itself. `ci` rather than
# `install` because the lockfile is committed, matching the launcher's frontend job.
COPY web/package.json web/package-lock.json ./
RUN npm ci
COPY web/ ./
RUN npm run build

FROM python:3.12-slim

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PIP_NO_CACHE_DIR=1

WORKDIR /app

# Install dependencies first so the layer caches across code changes. asyncpg is included
# here (unlike the dev extras) because the container always talks to a real Postgres.
# psycopg2-binary is here too and only here: the app runs async on asyncpg, but the boot-time
# `alembic upgrade head` runs on a SYNC driver (migrations/env.py strips +asyncpg), and the
# default postgresql:// dialect resolves to psycopg2. Without it the container boots, then dies
# on the first migration against a real database - which the aiosqlite test suite never hits.
COPY pyproject.toml ./
RUN pip install --no-cache-dir \
    "fastapi>=0.115" "uvicorn[standard]>=0.32" "pydantic>=2.9" "pydantic-settings>=2.6" \
    "sqlalchemy[asyncio]>=2.0.36" "asyncpg>=0.30" "psycopg2-binary>=2.9" "alembic>=1.14" \
    "httpx>=0.27" "pyjwt>=2.9"

COPY app ./app
COPY alembic.ini ./
# Where CRYOSTASIS_SITE_DIR points by default. app/web.py mounts it at the root behind the API
# routes, and treats its absence as "no site" rather than an error, so the API image would still
# boot if this stage were ever dropped.
COPY --from=site /web/dist ./web/dist
COPY docker-entrypoint.sh ./
RUN chmod +x docker-entrypoint.sh

# The platform probes /health.
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s \
    CMD python -c "import urllib.request,sys; sys.exit(0 if urllib.request.urlopen('http://127.0.0.1:8080/health').status==200 else 1)"

# Apply migrations then serve. No create-on-boot; Alembic owns the schema.
ENTRYPOINT ["./docker-entrypoint.sh"]
