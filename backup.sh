#!/bin/bash
# ==============================================================================
#  PG CRM — Enterprise PostgreSQL Backup Script
#  Intended for execution inside Docker environment via a scheduled cron job.
# ==============================================================================
set -e

# Load configuration from environment variables or use default configurations
DB_HOST=${PGHOST:-"localhost"}
DB_PORT=${PGPORT:-5432}
DB_USER=${PGUSER:-"postgres"}
DB_NAME=${PGDATABASE:-"pgcrmdb"}
DB_PASSWORD=${PGPASSWORD:-"admin"}
S3_BUCKET=${S3_BUCKET:-"pgcrm-backups"}

# Timestamp for unique file naming
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
BACKUP_DIR="/tmp/pg_backups"
BACKUP_FILE="${DB_NAME}_backup_${TIMESTAMP}.sql"
COMPRESSED_FILE="${BACKUP_FILE}.gz"

# Create a local backup directory if it does not already exist
mkdir -p "$BACKUP_DIR"

echo "[$(date)] Starting database backup for '${DB_NAME}'..."

# Run pg_dump to extract the database contents
# We supply database password inline to prevent interactive prompts
export PGPASSWORD="$DB_PASSWORD"
pg_dump -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -F p -f "$BACKUP_DIR/$BACKUP_FILE"

echo "[$(date)] Compressing backup file using gzip..."
gzip -f "$BACKUP_DIR/$BACKUP_FILE"

# Upload to S3 with AWS KMS server-side encryption for security
echo "[$(date)] Uploading compressed backup file to S3 bucket 's3://${S3_BUCKET}'..."
aws s3 cp "$BACKUP_DIR/$COMPRESSED_FILE" "s3://$S3_BUCKET/backups/$COMPRESSED_FILE" --sse aws:kms

# Clean up local temporary file
echo "[$(date)] Cleaning up local backup files..."
rm -f "$BACKUP_DIR/$COMPRESSED_FILE"

echo "[$(date)] Backup process completed successfully!"
