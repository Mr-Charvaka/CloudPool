#!/bin/bash
set -euo pipefail

# CloudPool PostgreSQL Backup Script
# Usage: ./backup-postgres.sh [database-name]
# Requires: pg_dump, aws CLI, env vars POSTGRES_USER, POSTGRES_PASSWORD, POSTGRES_HOST

DB="${1:-cloudpool}"
DATE=$(date +%Y-%m-%d)
DUMP_FILE="cloudpool-${DATE}.sql.gz"
ENCRYPTED_FILE="${DUMP_FILE}.gpg"
S3_BUCKET="${S3_BACKUP_BUCKET:-cloudpool-backups}"

echo "[backup] Starting backup of database: ${DB}"

# Dump + compress
PGPASSWORD="${POSTGRES_PASSWORD}" pg_dump \
    -h "${POSTGRES_HOST:-localhost}" \
    -U "${POSTGRES_USER:-cloudpool}" \
    -d "${DB}" \
    --no-owner \
    --no-acl \
    --format=custom \
    | gzip > "/tmp/${DUMP_FILE}"

echo "[backup] Dump size: $(du -h /tmp/${DUMP_FILE} | cut -f1)"

# Encrypt with GPG (key must be configured)
if [ -n "${GPG_RECIPIENT:-}" ]; then
    gpg --batch --yes --encrypt \
        --recipient "${GPG_RECIPIENT}" \
        --output "/tmp/${ENCRYPTED_FILE}" \
        "/tmp/${DUMP_FILE}"
    UPLOAD_FILE="${ENCRYPTED_FILE}"
    rm -f "/tmp/${DUMP_FILE}"
else
    UPLOAD_FILE="${DUMP_FILE}"
fi

# Upload to S3
aws s3 cp "/tmp/${UPLOAD_FILE}" "s3://${S3_BUCKET}/daily/${DATE}/${UPLOAD_FILE}" \
    --storage-class STANDARD_IA

echo "[backup] Uploaded to s3://${S3_BUCKET}/daily/${DATE}/${UPLOAD_FILE}"

# Cleanup
rm -f "/tmp/${DUMP_FILE}" "/tmp/${ENCRYPTED_FILE}"

# Retention: delete backups older than 90 days
aws s3 ls "s3://${S3_BUCKET}/daily/" | while read -r line; do
    backup_date=$(echo "$line" | awk '{print $1}')
    if [[ "$backup_date" < "$(date -d '90 days ago' +%Y-%m-%d)" ]]; then
        aws s3 rm "s3://${S3_BUCKET}/daily/${backup_date}/" --recursive
    fi
done

echo "[backup] Complete"
