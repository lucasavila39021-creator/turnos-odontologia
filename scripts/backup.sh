#!/usr/bin/env bash
# Backup MySQL turnos_db vía mysqldump (C-12 task 4.1).
# Uso: scripts/backup.sh [--dry-run] [--help]
# Lee .env (DB_HOST/DB_PORT/DB_NAME/DB_USER/DB_PASSWORD), genera
# backup-YYYY-MM-DD.sql restaurable con: mysql < backup-*.sql
# Nunca commitear .env; el password viaja por MYSQL_PWD (no visible en ps).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

usage() {
  echo "Uso: scripts/backup.sh [--dry-run] [--help]"
  echo "  Genera backup-\$(date +%F).sql con mysqldump usando vars de .env"
  echo "  --dry-run  muestra el comando sin ejecutarlo"
}

DRY_RUN=0
for arg in "$@"; do
  case "$arg" in
    --help|-h) usage; exit 0 ;;
    --dry-run) DRY_RUN=1 ;;
    *) echo "Flag desconocido: $arg" >&2; usage >&2; exit 1 ;;
  esac
done

if [ -f "$ROOT_DIR/.env" ]; then
  set -a
  # shellcheck disable=SC1091
  source "$ROOT_DIR/.env"
  set +a
fi

DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-turnos_db}"
DB_USER="${DB_USER:-turnos_user}"
if [ -z "${DB_PASSWORD:-}" ] || [ "$DB_PASSWORD" = "changeme" ]; then
  echo "WARN: DB_PASSWORD no configurado o placeholder (revisar .env)" >&2
fi

STAMP="$(date +%F)"
OUT="$ROOT_DIR/backup-$STAMP.sql"

CMD="mysqldump -h $DB_HOST -P $DB_PORT -u $DB_USER $DB_NAME"

if [ "$DRY_RUN" -eq 1 ]; then
  echo "[dry-run] $CMD > $OUT"
  echo "[dry-run] restore: mysql -h $DB_HOST -P $DB_PORT -u $DB_USER $DB_NAME < $OUT"
  exit 0
fi

export MYSQL_PWD="${DB_PASSWORD:-}"
# shellcheck disable=SC2086
mysqldump -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" "$DB_NAME" > "$OUT"
unset MYSQL_PWD

echo "Backup OK: $OUT"
echo "Restore: mysql -h $DB_HOST -P $DB_PORT -u $DB_USER $DB_NAME < $OUT"
