#!/usr/bin/env node
/**
 * Production Packaging & Container Validation Script
 *
 * Validates the complete Docker deployment topology without requiring a running Docker daemon:
 *   1. docker-compose.prod.yml (syntax, service topology, dependencies, network segregation, health checks)
 *   2. backend/Dockerfile (multi-stage build, layer extraction, non-root user, Spring Boot 3 launcher)
 *   3. web/Dockerfile (multi-stage Vite build, non-root Nginx runtime, asset copies, healthcheck)
 *   4. web/nginx.conf (SPA try_files, security headers, gzip, caching, reverse-proxy for /v1/ & /iclock/)
 *   5. .env.production.example (variable completeness, security secret instructions)
 *
 * Usage:
 *   node infra/scripts/validate-docker-packaging.mjs [projectRoot]
 */

import { readFileSync, existsSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { createRequire } from 'node:module'

const require = createRequire(import.meta.url)
const yaml = require('js-yaml')

const projectRoot = resolve(process.argv[2] ?? join(import.meta.dirname, '../..'))

let problems = 0
let checksPassed = 0

function fail(category, message) {
  console.error(`  [FAIL] ${category}: ${message}`)
  problems++
}

function pass(category, message) {
  console.log(`  [PASS] ${category}: ${message}`)
  checksPassed++
}

console.log('=============================================================================')
console.log('Validating Production Docker & Deployment Packaging')
console.log(`Target Directory: ${projectRoot}`)
console.log('=============================================================================\n')

// -----------------------------------------------------------------------------
// 1. Validate docker-compose.prod.yml
// -----------------------------------------------------------------------------
console.log('1. Checking docker-compose.prod.yml...')
const composePath = join(projectRoot, 'docker-compose.prod.yml')

if (!existsSync(composePath)) {
  fail('Compose', 'docker-compose.prod.yml not found in project root')
} else {
  try {
    const composeContent = readFileSync(composePath, 'utf8')
    const compose = yaml.load(composeContent)

    if (!compose || typeof compose !== 'object') {
      fail('Compose', 'Failed to parse docker-compose.prod.yml as valid YAML')
    } else {
      pass('Compose', 'Valid YAML syntax')

      const expectedServices = ['postgres', 'redis', 'redpanda', 'minio', 'minio-init', 'backend', 'web']
      const services = compose.services ?? {}

      for (const serviceName of expectedServices) {
        if (!services[serviceName]) {
          fail('Compose', `Missing required service: ${serviceName}`)
        } else {
          pass('Compose', `Service "${serviceName}" defined`)
        }
      }

      // Check networks: internal-net must be internal
      if (!compose.networks?.['internal-net']?.internal) {
        fail('Compose', 'network "internal-net" must have internal: true for isolation')
      } else {
        pass('Compose', 'internal-net is marked internal: true')
      }

      if (!compose.networks?.['public-net']) {
        fail('Compose', 'network "public-net" must be defined')
      } else {
        pass('Compose', 'public-net network defined')
      }

      // Verify postgres isolation (must NOT be on public-net)
      const pgNetworks = services.postgres?.networks ?? []
      if (pgNetworks.includes('public-net')) {
        fail('Compose', 'postgres must not be connected to public-net')
      } else if (pgNetworks.includes('internal-net')) {
        pass('Compose', 'postgres isolated to internal-net')
      }

      // Verify web is connected to public-net and internal-net
      const webNetworks = services.web?.networks ?? []
      if (webNetworks.includes('public-net') && webNetworks.includes('internal-net')) {
        pass('Compose', 'web reverse-proxy bridged between public-net and internal-net')
      } else {
        fail('Compose', 'web service must connect to both public-net and internal-net')
      }

      // Check healthchecks for persistent services
      for (const svc of ['postgres', 'redis', 'redpanda', 'minio', 'backend', 'web']) {
        if (services[svc]?.healthcheck?.test) {
          pass('Compose', `Healthcheck configured for service "${svc}"`)
        } else {
          fail('Compose', `Service "${svc}" is missing a healthcheck test definition`)
        }
      }

      // Check backend depends_on healthy conditions
      const backendDeps = services.backend?.depends_on ?? {}
      if (backendDeps.postgres?.condition === 'service_healthy') {
        pass('Compose', 'backend waits for postgres service_healthy')
      } else {
        fail('Compose', 'backend must wait for postgres with condition: service_healthy')
      }

      if (backendDeps.redis?.condition === 'service_healthy') {
        pass('Compose', 'backend waits for redis service_healthy')
      } else {
        fail('Compose', 'backend must wait for redis with condition: service_healthy')
      }

      if (backendDeps['minio-init']?.condition === 'service_completed_successfully') {
        pass('Compose', 'backend waits for minio-init service_completed_successfully')
      } else {
        fail('Compose', 'backend must wait for minio-init condition: service_completed_successfully')
      }

      // Check web depends on backend healthy
      if (services.web?.depends_on?.backend?.condition === 'service_healthy') {
        pass('Compose', 'web waits for backend service_healthy')
      } else {
        fail('Compose', 'web must wait for backend with condition: service_healthy')
      }
    }
  } catch (err) {
    fail('Compose', `Error reading/parsing compose file: ${err.message}`)
  }
}

console.log('')

// -----------------------------------------------------------------------------
// 2. Validate backend/Dockerfile
// -----------------------------------------------------------------------------
console.log('2. Checking backend/Dockerfile...')
const backendDockerPath = join(projectRoot, 'backend/Dockerfile')

if (!existsSync(backendDockerPath)) {
  fail('Backend Dockerfile', 'backend/Dockerfile not found')
} else {
  const dockerfile = readFileSync(backendDockerPath, 'utf8')

  // Check multi-stage
  const fromMatches = dockerfile.match(/^FROM\s+/gm) ?? []
  if (fromMatches.length >= 2) {
    pass('Backend Dockerfile', `Multi-stage build configured (${fromMatches.length} stages)`)
  } else {
    fail('Backend Dockerfile', 'Dockerfile should use multi-stage builds (at least 2 stages)')
  }

  // Check layertools extraction
  if (dockerfile.includes('jarmode=tools') || dockerfile.includes('jarmode=layertools')) {
    pass('Backend Dockerfile', 'Spring Boot layer extraction configured')
  } else {
    fail('Backend Dockerfile', 'Spring Boot jarmode layertools extraction missing')
  }

  // Check non-root execution
  if (/USER\s+\d+:\d+/.test(dockerfile) || /USER\s+\w+/.test(dockerfile)) {
    pass('Backend Dockerfile', 'Non-root user execution configured')
  } else {
    fail('Backend Dockerfile', 'Missing USER directive for non-root execution')
  }

  // Check Spring Boot 3 JarLauncher
  if (dockerfile.includes('org.springframework.boot.loader.launch.JarLauncher')) {
    pass('Backend Dockerfile', 'Spring Boot 3.4+ JarLauncher entrypoint configured')
  } else {
    fail('Backend Dockerfile', 'Entrypoint should use Spring Boot 3 launcher (org.springframework.boot.loader.launch.JarLauncher)')
  }

  // Check Healthcheck
  if (/HEALTHCHECK\s+/.test(dockerfile)) {
    pass('Backend Dockerfile', 'Container HEALTHCHECK instruction defined')
  } else {
    fail('Backend Dockerfile', 'HEALTHCHECK instruction missing from runtime image')
  }
}

console.log('')

// -----------------------------------------------------------------------------
// 3. Validate web/Dockerfile
// -----------------------------------------------------------------------------
console.log('3. Checking web/Dockerfile...')
const webDockerPath = join(projectRoot, 'web/Dockerfile')

if (!existsSync(webDockerPath)) {
  fail('Web Dockerfile', 'web/Dockerfile not found')
} else {
  const dockerfile = readFileSync(webDockerPath, 'utf8')

  // Check multi-stage
  const fromMatches = dockerfile.match(/^FROM\s+/gm) ?? []
  if (fromMatches.length >= 2) {
    pass('Web Dockerfile', `Multi-stage build configured (${fromMatches.length} stages)`)
  } else {
    fail('Web Dockerfile', 'Dockerfile should use multi-stage builds')
  }

  // Check build step
  if (dockerfile.includes('npm run build') || dockerfile.includes('vite build')) {
    pass('Web Dockerfile', 'Vite SPA production build step present')
  } else {
    fail('Web Dockerfile', 'Missing SPA production build step ("npm run build")')
  }

  // Check non-root nginx user
  if (/USER\s+nginx/.test(dockerfile) || /chown.*nginx/.test(dockerfile)) {
    pass('Web Dockerfile', 'Unprivileged Nginx runtime user configured')
  } else {
    fail('Web Dockerfile', 'Should run as unprivileged user nginx')
  }

  // Check custom nginx.conf copy
  if (dockerfile.includes('nginx.conf')) {
    pass('Web Dockerfile', 'Custom nginx.conf copied into container')
  } else {
    fail('Web Dockerfile', 'Custom nginx.conf must be copied into container')
  }

  // Check Healthcheck
  if (/HEALTHCHECK\s+/.test(dockerfile)) {
    pass('Web Dockerfile', 'Container HEALTHCHECK instruction defined')
  } else {
    fail('Web Dockerfile', 'HEALTHCHECK instruction missing from runtime image')
  }
}

console.log('')

// -----------------------------------------------------------------------------
// 4. Validate web/nginx.conf
// -----------------------------------------------------------------------------
console.log('4. Checking web/nginx.conf...')
const nginxPath = join(projectRoot, 'web/nginx.conf')

if (!existsSync(nginxPath)) {
  fail('Nginx', 'web/nginx.conf not found')
} else {
  const conf = readFileSync(nginxPath, 'utf8')

  // Check gzip
  if (conf.includes('gzip on;')) {
    pass('Nginx', 'Gzip compression enabled')
  } else {
    fail('Nginx', 'gzip compression should be enabled')
  }

  // Check SPA routing fallback
  if (conf.includes('try_files $uri $uri/ /index.html') || conf.includes('try_files $uri /index.html')) {
    pass('Nginx', 'SPA routing fallback configured (try_files ... /index.html)')
  } else {
    fail('Nginx', 'Missing SPA fallback routing: try_files $uri $uri/ /index.html')
  }

  // Check Security headers
  const securityHeaders = [
    'X-Frame-Options',
    'X-Content-Type-Options',
    'X-XSS-Protection',
    'Referrer-Policy',
    'Content-Security-Policy',
  ]

  for (const header of securityHeaders) {
    if (conf.includes(header)) {
      pass('Nginx', `Security header "${header}" present`)
    } else {
      fail('Nginx', `Missing recommended security header: ${header}`)
    }
  }

  // Check Reverse Proxy for backend API /v1/
  if (conf.includes('location /v1/') && conf.includes('proxy_pass http://backend:8080')) {
    pass('Nginx', 'Reverse-proxy configured for /v1/ -> backend:8080')
  } else {
    fail('Nginx', 'Missing reverse-proxy location /v1/ routing to backend:8080')
  }

  // Check ZKTeco ADMS proxy /iclock/
  if (conf.includes('location /iclock/') && conf.includes('proxy_pass http://backend:8080')) {
    pass('Nginx', 'Reverse-proxy configured for /iclock/ biometric push ADMS -> backend:8080')
  } else {
    fail('Nginx', 'Missing reverse-proxy location /iclock/ routing to backend:8080')
  }

  // Check Health check endpoint
  if (conf.includes('location = /healthz') || conf.includes('location /healthz')) {
    pass('Nginx', 'Health check probe /healthz configured')
  } else {
    fail('Nginx', 'Missing health check probe /healthz')
  }

  // Check asset caching
  if (conf.includes('max') || conf.includes('immutable')) {
    pass('Nginx', 'Immutable asset caching configured')
  } else {
    fail('Nginx', 'Missing immutable asset caching for static bundles')
  }
}

console.log('')

// -----------------------------------------------------------------------------
// 5. Validate .env.production.example
// -----------------------------------------------------------------------------
console.log('5. Checking .env.production.example...')
const envExamplePath = join(projectRoot, '.env.production.example')

if (!existsSync(envExamplePath)) {
  fail('Env Example', '.env.production.example not found in project root')
} else {
  const envContent = readFileSync(envExamplePath, 'utf8')

  const requiredVars = [
    'POSTGRES_DB',
    'POSTGRES_USER',
    'POSTGRES_PASSWORD',
    'POSTGRES_APP_USER',
    'POSTGRES_APP_PASSWORD',
    'REDIS_PASSWORD',
    'MINIO_ROOT_USER',
    'MINIO_ROOT_PASSWORD',
    'FIELD_ENCRYPTION_KEY',
    'JWT_PRIVATE_KEY',
    'JWT_PUBLIC_KEY',
    'HTTP_PORT',
  ]

  for (const v of requiredVars) {
    const regex = new RegExp(`^${v}=`, 'm')
    if (regex.test(envContent)) {
      pass('Env Example', `Variable ${v} defined`)
    } else {
      fail('Env Example', `Missing required environment variable definition: ${v}`)
    }
  }

  if (envContent.includes('openssl rand -base64 32')) {
    pass('Env Example', 'Key generation command documented for AES-256 field encryption')
  } else {
    fail('Env Example', 'Missing AES-256 field encryption generation instructions')
  }

  if (envContent.includes('openssl genpkey')) {
    pass('Env Example', 'Key generation commands documented for RSA-2048 JWT signing keypair')
  } else {
    fail('Env Example', 'Missing RSA JWT keypair generation instructions')
  }
}

console.log('')
console.log('=============================================================================')
console.log(`Validation Summary: ${checksPassed} checks passed, ${problems} problems found`)
console.log('=============================================================================')

if (problems > 0) {
  process.exit(1)
} else {
  console.log('All production packaging and containerization checks passed cleanly!\n')
  process.exit(0)
}
