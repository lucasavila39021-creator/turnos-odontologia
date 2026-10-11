#!/bin/sh
set -e

# wait-for-db: espera a mysql:3306 antes de arrancar la app
HOST="${DB_HOST:-mysql}"
PORT="${DB_PORT:-3306}"
MAX_RETRIES=30
RETRY=0

echo "Waiting for MySQL at $HOST:$PORT ..."

until nc -z "$HOST" "$PORT" 2>/dev/null; do
  RETRY=$((RETRY+1))
  if [ "$RETRY" -ge "$MAX_RETRIES" ]; then
    echo "MySQL not reachable after $MAX_RETRIES attempts, exiting"
    exit 1
  fi
  echo "MySQL not ready ($RETRY/$MAX_RETRIES), retrying in 2s..."
  sleep 2
done

echo "MySQL is up — starting app"
exec "$@"
