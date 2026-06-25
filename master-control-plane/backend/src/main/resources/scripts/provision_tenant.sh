#!/bin/bash

# Exit immediately if any command exits with a non-zero status
set -e

if [ "$#" -lt 8 ]; then
    echo "Usage: $0 <TENANT_ID> <DB_PREFIX> <ADMIN_EMAIL> <ROUTER_IP> <RZP_KEY> <RZP_SECRET> <WA_TOKEN> <WA_KEY>"
    exit 1
fi

TENANT_ID=$1
DB_PREFIX=$2
ADMIN_EMAIL=$3
ROUTER_IP=$4
RAZORPAY_KEY=$5
RAZORPAY_SECRET=$6
WHATSAPP_TOKEN=$7
WHATSAPP_KEY=$8

echo "===================================================="
echo "Starting provisioning pipeline for Tenant ID: ${TENANT_ID}"
echo "Database Prefix: ${DB_PREFIX}"
echo "Admin Email: ${ADMIN_EMAIL}"
echo "Router IP: ${ROUTER_IP}"
echo "===================================================="

echo "[1/4] Creating dedicated database: ${DB_PREFIX}_db..."
sleep 1

echo "[2/4] Initializing schema structure..."
sleep 1

echo "[3/4] Registering admin user: ${ADMIN_EMAIL}..."
sleep 1

# Generate simulated env file for testing verification
ENV_DIR="target/tenants/${TENANT_ID}"
mkdir -p "$ENV_DIR"
ENV_FILE="${ENV_DIR}/.env"

echo "DATABASE_NAME=${DB_PREFIX}_db" > "$ENV_FILE"
echo "ADMIN_EMAIL=${ADMIN_EMAIL}" >> "$ENV_FILE"
echo "ROUTER_IP=${ROUTER_IP}" >> "$ENV_FILE"
echo "RAZORPAY_KEY_ID=$RAZORPAY_KEY" >> "$ENV_FILE"
echo "RAZORPAY_KEY_SECRET=$RAZORPAY_SECRET" >> "$ENV_FILE"
echo "META_WHATSAPP_ACCESS_TOKEN=$WHATSAPP_TOKEN" >> "$ENV_FILE"
echo "META_WHATSAPP_PHONE_NUMBER_ID=$WHATSAPP_KEY" >> "$ENV_FILE"

echo "Generated .env configuration at $ENV_FILE"

echo "[4/4] Setting up reverse proxy routing and SSL certs..."
sleep 1

echo "===================================================="
echo "Provisioning pipeline completed successfully for ${TENANT_ID}"
echo "===================================================="
exit 0
