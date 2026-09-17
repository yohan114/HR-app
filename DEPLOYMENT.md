# HR Platform — Production Deployment Runbook

Complete guide to provisioning, deploying, and operating the HR Platform in production.

---

## 1. System Architecture & Topology

The HR Platform is designed as a secure, multi-tenant modular monolith:

```
                          [ Internet / Clients ]
                                    │
                       (HTTPS / Reverse Proxy: Nginx)
                                    │
            ┌───────────────────────┴───────────────────────┐
            │                                               │
   [ Web Console SPA ]                             [ Spring Boot API ]
   (React 19 + TypeScript)                         (Port 8080 / JVM 21)
                                                            │
    ┌───────────────────┬───────────────────┬───────────────┴───────────────┐
    │                   │                   │                               │
[ PostgreSQL 16 ]  [ Redis 7.4 ]     [ MinIO / S3 ]                  [ Redpanda / Kafka ]
  - 173 Tables       - Denylist        - Payslips                      - Domain Events
  - 28 Migrations    - Rate Limits     - Document Vault                - Push Notifications
  - RLS Isolation    - Locks           - Attachments
```

---

## 2. Secrets & Environment Configuration

### 2.1 Generate Cryptographic Secrets
Run the automated secrets generator:
```bash
node scripts/generate-production-secrets.mjs --out .env.production --force
```

This generates:
* **`FIELD_ENCRYPTION_KEY`**: 32-byte AES-256 key (Base64) for encrypting sensitive profile columns.
* **`JWT_PRIVATE_KEY` / `JWT_PUBLIC_KEY`**: 2048-bit RSA key pair in Base64 (PKCS#8 and X.509 DER format).
* **High-entropy passwords** for PostgreSQL owner, runtime application user, Redis, and MinIO.

> [!CAUTION]
> Keep `.env.production` in an encrypted secrets vault (e.g. AWS Secrets Manager, HashiCorp Vault). Never commit `.env.production` to version control.

---

## 3. PostgreSQL 16 Database Provisioning

### 3.1 The 3-Role Security Architecture (§7)
PostgreSQL table owners automatically bypass Row-Level Security (RLS) policies. To guarantee strict multi-tenant isolation:
* **`hr_owner`**: Schema owner. Runs Flyway migrations.
* **`hr_app`**: NOLOGIN group role granted DML privileges.
* **`hr_app_login`**: The actual database user configured in the application runtime. Belongs to `hr_app`, does not own tables, and strictly enforces RLS policies.

### 3.2 Provisioning Methods

#### Method A: Cloud Managed Database (AWS RDS, Supabase, Neon, Cloud SQL)
1. Generate the initialization script with your generated passwords:
   ```bash
   node scripts/setup-live-database.mjs --sql
   ```
2. Run the generated SQL in your Cloud Query Editor (or via `psql` connected as superuser):
   ```bash
   node scripts/setup-live-database.mjs --sql | psql -h <db-host> -U postgres -d hr
   ```

#### Method B: Production Docker Compose Stack
Launch the isolated database container:
```bash
docker compose -f docker-compose.prod.yml --env-file .env.production up -d postgres
```
The container automatically mounts `infra/postgres/init/01-roles.sql` to configure the roles on startup.

---

## 4. Running Database Migrations

The backend uses Flyway to manage the database schema. When pointed at your PostgreSQL instance in `prod` mode, the backend applies all 28 migrations automatically on startup:

```bash
cd backend
./gradlew bootRun --args='--spring.profiles.active=prod'
```

### Verified Schema State:
* **28 Migrations (`V1` to `V28`)**
* **173 Relational Tables** (169 strictly isolated by `tenant_id` RLS)
* **123 Role-Based Permission Keys**
* **7 Standard System Roles** (`ADMIN`, `HR_ADMIN`, `MANAGER`, `EMPLOYEE`, `FINANCE`, `RECRUITER`, `AUDITOR`)

---

## 5. Deploying the Full Stack with Docker

To deploy the entire production stack (PostgreSQL, Redis, Redpanda, MinIO, Spring Boot Backend, and Nginx Web Console):

```bash
# 1. Ensure production secrets are generated
node scripts/generate-production-secrets.mjs --out .env.production

# 2. Launch all services
docker compose -f docker-compose.prod.yml --env-file .env.production up -d
```

### Service Health Checks:
```bash
# Check running containers
docker compose -f docker-compose.prod.yml ps

# View backend startup logs
docker compose -f docker-compose.prod.yml logs -f backend
```

---

## 6. Standalone Backend & Web Deployment

If deploying to separate application servers or Kubernetes:

### 6.1 Backend (Spring Boot 3)
```bash
cd backend
./gradlew build -x test
java -Dspring.profiles.active=prod -jar build/libs/backend-0.0.1-SNAPSHOT.jar
```

### 6.2 Web Console (React 19 SPA)
```bash
cd web
npm install
npm run build
```
Deploy the generated `web/dist/` directory to Nginx, Cloudflare Pages, AWS S3 + CloudFront, or any static hosting provider.

#### Nginx Configuration Snippet:
```nginx
server {
    listen 80;
    server_name console.hrapp.io;

    root /var/www/hr-admin/dist;
    index index.html;

    # Single-page application routing
    location / {
        try_files $uri $uri/ /index.html;
    }

    # Reverse proxy API requests to backend
    location /v1/ {
        proxy_pass http://backend:8080/v1/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

---

## 7. Android Mobile App Release

1. **Keystore Configuration**: Provide signing credentials via environment variables:
   ```bash
   export KEYSTORE_FILE="/path/to/release.keystore"
   export KEYSTORE_PASSWORD="keystore-password"
   export KEY_ALIAS="hr-release-key"
   export KEY_PASSWORD="key-password"
   ```
2. **Build Signed Release APKs with ABI Splits**:
   ```bash
   cd android
   ./gradlew :app:assembleRelease :app:checkReleaseApkSize
   ```
   Generates optimized APK splits (`arm64-v8a`, `armeabi-v7a`, `x86_64`) all under the 25 MB budget.

---

## 8. Post-Deployment Verification Checklist

- [ ] Verify database connectivity and extensions:
  ```sql
  SELECT extname, extversion FROM pg_extension WHERE extname IN ('ltree', 'btree_gin', 'pgcrypto');
  ```
- [ ] Confirm table ownership and RLS separation:
  ```sql
  SELECT tableowner, count(*) FROM pg_tables WHERE schemaname = 'public' GROUP BY tableowner;
  ```
  *(Should show `hr_owner` as table owner, with runtime app logging in as `hr_app_login`)*.
- [ ] Verify API health:
  ```bash
  curl -I https://api.hrapp.io/actuator/health
  ```
- [ ] Run benchmark load test:
  ```bash
  node scripts/load-test-dashboard.mjs --target https://api.hrapp.io/v1/dashboard --token <admin-jwt>
  ```
- [ ] Test mobile deep links (`hrapp://leave/app-123`, `hrapp://attendance/punch`).
