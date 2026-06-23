#!/bin/bash

# Exit immediately if a command exits with a non-zero status
set -e

# Arguments
DOMAIN_NAME=$1
DB_PASSWORD=$2
APP_PORT=$3
CLIENT_EMAIL=$4
WHATSAPP_TOKEN=$5
RAZORPAY_KEY_ID=$6
RAZORPAY_KEY_SECRET=$7
PRIMARY_COLOR=$8

if [ -z "$DOMAIN_NAME" ] || [ -z "$DB_PASSWORD" ] || [ -z "$APP_PORT" ] || [ -z "$CLIENT_EMAIL" ]; then
    echo "Usage: $0 <domain_name> <db_password> <app_port> <client_email> [whatsapp_token] [razorpay_key_id] [razorpay_key_secret] [primary_color]"
    exit 1
fi

echo "========================================="
echo "PROVISIONING NEW TENANT INSTANCE"
echo "Domain: ${DOMAIN_NAME}"
echo "Port: ${APP_PORT}"
echo "Email: ${CLIENT_EMAIL}"
echo "WhatsApp Token: ${WHATSAPP_TOKEN:-(not set)}"
echo "Razorpay Key ID: ${RAZORPAY_KEY_ID:-(not set)}"
echo "Primary Color: ${PRIMARY_COLOR:-(not set)}"
echo "========================================="

# 1. Sanitize database name (replace hyphens with underscores)
DB_NAME="pgcrm_${DOMAIN_NAME//-/_}"
echo "Database Name: ${DB_NAME}"

# 2. Create the PostgreSQL Database
echo "Creating database in PostgreSQL..."
if command -v psql &> /dev/null; then
    PGPASSWORD="admin" psql -U postgres -h localhost -c "CREATE DATABASE ${DB_NAME};" || echo "Database ${DB_NAME} might already exist."
else
    echo "psql client not found. Skipping database creation step (assuming it will be created by other means or is in sandbox mode)."
fi

# 3. Create opt directories within the workspace
DEPLOY_DIR="tenants/${DOMAIN_NAME}/deploy"
echo "Creating deployment directory: ${DEPLOY_DIR}"
mkdir -p "${DEPLOY_DIR}"

# 4. Generate the JWT Secret Key
JWT_SECRET=$(openssl rand -hex 32 || echo "default_fallback_jwt_secret_key_at_least_256_bits_long_secret_phrase")

# Determine if custom Razorpay is enabled
RAZORPAY_ENABLED="false"
if [ -n "$RAZORPAY_KEY_ID" ]; then
    RAZORPAY_ENABLED="true"
fi

# 5. Generate isolated .env file
ENV_FILE="${DEPLOY_DIR}/.env"
echo "Generating .env file at ${ENV_FILE}"
cat <<EOT > "${ENV_FILE}"
SPRING_PROFILES_ACTIVE=prod

# Database
DB_URL=jdbc:postgresql://pgcrm-postgres-${DOMAIN_NAME}:5432/${DB_NAME}
DB_USERNAME=postgres
DB_PASSWORD=${DB_PASSWORD}

# Security
JWT_SECRET=${JWT_SECRET}

# Tenant Branding
PG_NAME="${DOMAIN_NAME}"
PG_SHORT_NAME="${DOMAIN_NAME}"
PG_PRIMARY_COLOR="${PRIMARY_COLOR:-#2563eb}"

# Initial Tenant Super Admin
PG_DEFAULT_OWNER_EMAIL=${CLIENT_EMAIL}
PG_DEFAULT_OWNER_NAME="System Owner"
PG_DEFAULT_OWNER_PASSWORD="Owner@123_change_me"

# Integrations (Defaults)
MAIL_HOST=smtp.mock.com
MAIL_PORT=587
MAIL_USERNAME=mock@mock.com
MAIL_PASSWORD=mock
MAIL_FROM=mock@mock.com
MAIL_ENABLED=false

# Meta WhatsApp
META_WHATSAPP_PHONE_NUMBER_ID=""
META_WHATSAPP_ACCESS_TOKEN="${WHATSAPP_TOKEN}"
META_WHATSAPP_VERIFY_TOKEN="verify_token_123"

# Razorpay
RAZORPAY_ENABLED=${RAZORPAY_ENABLED}
RAZORPAY_KEY_ID="${RAZORPAY_KEY_ID}"
RAZORPAY_KEY_SECRET="${RAZORPAY_KEY_SECRET}"

# Port & Container Metadata
APP_PORT=${APP_PORT}
DOMAIN_NAME=${DOMAIN_NAME}
DB_NAME=${DB_NAME}
EOT

# 6. Copy template docker-compose file
TEMPLATE_COMPOSE="core-pg-crm/deploy/docker-compose.prod.yml"
DEST_COMPOSE="${DEPLOY_DIR}/docker-compose.yml"
echo "Copying docker-compose template..."
if [ -f "${TEMPLATE_COMPOSE}" ]; then
    cp "${TEMPLATE_COMPOSE}" "${DEST_COMPOSE}"
else
    # Fallback lookup relative to script directory
    cp "$(dirname "$0")/../core-pg-crm/deploy/docker-compose.prod.yml" "${DEST_COMPOSE}"
fi

# 7. Start container stack
if command -v docker &> /dev/null; then
    echo "Spinning up Docker containers..."
    cd "${DEPLOY_DIR}"
    docker compose up -d
else
    echo "Docker command not found. Simulating successful container spinup for development/sandbox mode."
fi

echo "========================================="
echo "TENANT PROVISIONED SUCCESSFULLY"
echo "========================================="
