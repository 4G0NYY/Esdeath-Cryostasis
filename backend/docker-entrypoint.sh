#!/bin/sh
# Apply migrations, then serve. Migrations are idempotent, so a re-deploy is safe and a
# fresh database is brought fully up to head before the first request.
set -e

echo "Running migrations..."
alembic upgrade head

echo "Starting uvicorn..."
exec uvicorn app.main:app --host 0.0.0.0 --port "${CRYOSTASIS_PORT:-8080}"
