#!/usr/bin/env node
/**
 * Production Secret Generator for HR Platform
 *
 * Cryptographically generates all required secrets:
 * - 32-byte AES-256 field encryption key (Base64)
 * - 2048-bit RSA key pair for JWT signing (PKCS#8 private, X.509 public, DER in Base64)
 * - PostgreSQL 16 database owner and application login passwords
 * - Redis 7.4 password
 * - MinIO root password
 *
 * Usage:
 *   node scripts/generate-production-secrets.mjs [--dry-run] [--out .env.production] [--force] [--json]
 */

import { generateKeyPairSync, randomBytes } from 'node:crypto';
import { existsSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';

function generateRandomPassword(length = 32) {
  const charset = 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!@#$%^&*()-_=+[]{}';
  const bytes = randomBytes(length);
  let result = '';
  for (let i = 0; i < length; i++) {
    result += charset[bytes[i] % charset.length];
  }
  return result;
}

function generateAlphanumericPassword(length = 32) {
  const charset = 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789';
  const bytes = randomBytes(length);
  let result = '';
  for (let i = 0; i < length; i++) {
    result += charset[bytes[i] % charset.length];
  }
  return result;
}

function generateFieldEncryptionKey() {
  // 32 bytes (256 bits) for AES-256-GCM
  return randomBytes(32).toString('base64');
}

function generateJwtKeyPair() {
  // Generate 2048-bit RSA key pair in DER format
  const { privateKey, publicKey } = generateKeyPairSync('rsa', {
    modulusLength: 2048,
    publicKeyEncoding: {
      type: 'spki',
      format: 'der',
    },
    privateKeyEncoding: {
      type: 'pkcs8',
      format: 'der',
    },
  });

  return {
    privateKeyBase64: privateKey.toString('base64'),
    publicKeyBase64: publicKey.toString('base64'),
  };
}

function generateAllSecrets() {
  const jwt = generateJwtKeyPair();
  return {
    POSTGRES_DB: 'hr',
    POSTGRES_USER: 'hr_owner',
    POSTGRES_PASSWORD: generateRandomPassword(28),
    POSTGRES_APP_USER: 'hr_app_login',
    POSTGRES_APP_PASSWORD: generateRandomPassword(28),
    REDIS_PASSWORD: generateAlphanumericPassword(36),
    MINIO_ROOT_USER: 'hrminio',
    MINIO_ROOT_PASSWORD: generateRandomPassword(28),
    FIELD_ENCRYPTION_KEY: generateFieldEncryptionKey(),
    JWT_PRIVATE_KEY: jwt.privateKeyBase64,
    JWT_PUBLIC_KEY: jwt.publicKeyBase64,
    HTTP_PORT: '80',
    FCM_PROJECT_ID: 'hr-mobile-app',
    FCM_CREDENTIALS_PATH: '',
    FCM_TOKEN: '',
  };
}

function formatEnvFile(secrets) {
  return `# =============================================================================
# HR Platform - Production Environment Configuration
# Generated on: ${new Date().toISOString()}
#
# IMPORTANT:
#   - NEVER commit this file to version control.
#   - Keep backups in an encrypted vault / KMS / secret manager.
#   - Database role separation (§7):
#       Schema migrations run as: \${POSTGRES_USER} (hr_owner)
#       Application DML runs as:  \${POSTGRES_APP_USER} (hr_app_login)
#       The app MUST NEVER connect as hr_owner; table owners bypass PostgreSQL RLS.
# =============================================================================

# -----------------------------------------------------------------------------
# 1. PostgreSQL 16 (Row-Level Security Architecture)
# -----------------------------------------------------------------------------
POSTGRES_DB=${secrets.POSTGRES_DB}
POSTGRES_USER=${secrets.POSTGRES_USER}
POSTGRES_PASSWORD=${secrets.POSTGRES_PASSWORD}

# Application runtime role (DML only, enforces PostgreSQL Row-Level Security)
POSTGRES_APP_USER=${secrets.POSTGRES_APP_USER}
POSTGRES_APP_PASSWORD=${secrets.POSTGRES_APP_PASSWORD}

# -----------------------------------------------------------------------------
# 2. Redis 7.4 (Token Denylists, Rate Limits & Distributed Locks)
# -----------------------------------------------------------------------------
REDIS_PASSWORD=${secrets.REDIS_PASSWORD}

# -----------------------------------------------------------------------------
# 3. MinIO / S3 Object Storage (Documents, Payslips & Attachments)
# -----------------------------------------------------------------------------
MINIO_ROOT_USER=${secrets.MINIO_ROOT_USER}
MINIO_ROOT_PASSWORD=${secrets.MINIO_ROOT_PASSWORD}

# -----------------------------------------------------------------------------
# 4. Cryptographic Secrets (Field Encryption & JWT Signing)
# -----------------------------------------------------------------------------
# Field Encryption Key: AES-256 (32 bytes base64 encoded)
FIELD_ENCRYPTION_KEY=${secrets.FIELD_ENCRYPTION_KEY}

# JWT RSA Key Pair: 2048-bit PKCS#8 Private Key & X.509 Public Key in Base64 (single-line)
JWT_PRIVATE_KEY=${secrets.JWT_PRIVATE_KEY}
JWT_PUBLIC_KEY=${secrets.JWT_PUBLIC_KEY}

# -----------------------------------------------------------------------------
# 5. Push Notifications (Firebase Cloud Messaging v1)
# -----------------------------------------------------------------------------
FCM_PROJECT_ID=${secrets.FCM_PROJECT_ID}
FCM_CREDENTIALS_PATH=${secrets.FCM_CREDENTIALS_PATH}
FCM_TOKEN=${secrets.FCM_TOKEN}

# -----------------------------------------------------------------------------
# 6. Network & Gateway Configuration
# -----------------------------------------------------------------------------
HTTP_PORT=${secrets.HTTP_PORT}
`;
}

function main() {
  const args = process.argv.slice(2);
  const isDryRun = args.includes('--dry-run');
  const isJson = args.includes('--json');
  const isForce = args.includes('--force');

  let outPath = resolve(process.cwd(), '.env.production');
  const outIndex = args.indexOf('--out');
  if (outIndex !== -1 && args[outIndex + 1]) {
    outPath = resolve(process.cwd(), args[outIndex + 1]);
  }

  const secrets = generateAllSecrets();

  if (isJson) {
    console.log(JSON.stringify(secrets, null, 2));
    return;
  }

  const content = formatEnvFile(secrets);

  if (isDryRun) {
    console.log('--- DRY RUN: Generated secrets below ---');
    console.log(content);
    return;
  }

  if (existsSync(outPath) && !isForce) {
    console.error(`Error: Output file '${outPath}' already exists.`);
    console.error('Use --force to overwrite, or --out <path> to specify another location, or --dry-run.');
    process.exit(1);
  }

  writeFileSync(outPath, content, 'utf8');
  console.log(`Successfully generated production secrets at: ${outPath}`);
  console.log('Generated:');
  console.log('  - 32-byte AES-256 Field Encryption Key');
  console.log('  - 2048-bit RSA PKCS#8 & X.509 Keypair for JWT');
  console.log('  - PostgreSQL owner & app_login credentials');
  console.log('  - Redis 7.4 password');
  console.log('  - MinIO root password');
}

main();
