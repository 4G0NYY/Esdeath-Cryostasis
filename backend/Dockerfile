# One Dockerfile, uvicorn behind the host's TLS terminator (architecture 10). Config is
# 12-factor, so CRYOSTASIS_DATABASE_URL is the only required variable at run time.
FROM python:3.12-slim

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PIP_NO_CACHE_DIR=1

WORKDIR /app

# Install dependencies first so the layer caches across code changes. asyncpg is included
# here (unlike the dev extras) because the container always talks to a real Postgres.
COPY pyproject.toml ./
RUN pip install --no-cache-dir \
    "fastapi>=0.115" "uvicorn[standard]>=0.32" "pydantic>=2.9" "pydantic-settings>=2.6" \
    "sqlalchemy[asyncio]>=2.0.36" "asyncpg>=0.30" "alembic>=1.14" "httpx>=0.27" "pyjwt>=2.9"

COPY app ./app
COPY alembic.ini ./
COPY docker-entrypoint.sh ./
RUN chmod +x docker-entrypoint.sh

# The platform probes /health.
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s \
    CMD python -c "import urllib.request,sys; sys.exit(0 if urllib.request.urlopen('http://127.0.0.1:8080/health').status==200 else 1)"

# Apply migrations then serve. No create-on-boot; Alembic owns the schema.
ENTRYPOINT ["./docker-entrypoint.sh"]
