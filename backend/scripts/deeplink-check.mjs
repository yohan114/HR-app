#!/usr/bin/env node
/**
 * Compares the `hrapp://` routes the server builds against the ones the mobile clients parse.
 *
 * The server constructs deep links and the clients consume them, in different languages, from
 * separate constant lists. Nothing in either compiler can see the other, so drift is silent: a
 * notification carrying a route the app does not recognise opens to no particular screen, and users
 * do not report "the notification took me somewhere vague" as a bug. It just quietly erodes.
 *
 * iOS is checked when present. It is parked (see PHASE-1-STATUS.md), so its absence is not a
 * failure — but if the file exists it must agree, or unparking iOS starts with a broken feature.
 */
import { readFileSync, existsSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const repoRoot = join(dirname(fileURLToPath(import.meta.url)), '..', '..');

const SOURCES = [
  {
    name: 'backend',
    path: 'backend/src/main/kotlin/com/hr/notification/DeepLink.kt',
    required: true,
  },
  {
    name: 'android',
    path: 'android/app/src/main/kotlin/com/hr/app/ui/navigation/HrNavigation.kt',
    required: true,
  },
  {
    name: 'ios',
    path: 'ios/HR/Navigation/DeepLinks.swift',
    required: false,
  },
];

/**
 * Pulls route literals out of a source file.
 *
 * Matches the interpolated form both languages use — `"$SCHEME://leave/{id}"` in Kotlin,
 * `"\(scheme)://leave/{id}"` in Swift — plus the plain literal, so a file that stops interpolating
 * is still read rather than silently reported as having no routes. Reporting zero routes as
 * agreement is exactly the failure this script exists to prevent.
 */
function extractRoutes(source) {
  const patterns = [
    /"\$SCHEME:\/\/([^"$]*)"/g,          // Kotlin: "$SCHEME://leave/{id}"
    /"\\\(scheme\):\/\/([^"\\]*)"/g,      // Swift:  "\(scheme)://leave/{id}"
    /"hrapp:\/\/([^"$]*)"/g,              // either, written out in full
  ];

  const routes = new Set();
  for (const pattern of patterns) {
    for (const match of source.matchAll(pattern)) {
      // Strip a query string; routes are compared by path shape only.
      const route = match[1].split('?')[0].replace(/\/$/, '');
      // `"$SCHEME://"` on its own is the prefix used by parsing guards, not a destination —
      // a bare scheme names no screen, so it cannot be a route either side is expected to have.
      if (route !== '') routes.add(route);
    }
  }
  return routes;
}

const found = new Map();
const problems = [];

for (const { name, path, required } of SOURCES) {
  const full = join(repoRoot, path);
  if (!existsSync(full)) {
    if (required) problems.push(`${name}: missing ${path}`);
    else console.log(`deeplink-check: ${name} not present, skipped (${path})`);
    continue;
  }

  const routes = extractRoutes(readFileSync(full, 'utf8'));
  if (routes.size === 0) {
    // A parser that quietly stops matching would report every source as agreeing on nothing.
    problems.push(`${name}: no routes found in ${path} — the extractor no longer matches this file`);
    continue;
  }
  found.set(name, routes);
}

const names = [...found.keys()];
if (names.length > 1) {
  const [reference, ...others] = names;
  const referenceRoutes = found.get(reference);

  for (const other of others) {
    const otherRoutes = found.get(other);

    for (const route of referenceRoutes) {
      if (!otherRoutes.has(route)) {
        problems.push(`hrapp://${route} is built by ${reference} but not handled by ${other}`);
      }
    }
    for (const route of otherRoutes) {
      if (!referenceRoutes.has(route)) {
        problems.push(`hrapp://${route} is handled by ${other} but never built by ${reference}`);
      }
    }
  }
}

if (problems.length > 0) {
  console.error('deeplink-check: FAILED');
  for (const problem of problems) console.error(`  - ${problem}`);
  process.exit(1);
}

const routeCount = found.get(names[0])?.size ?? 0;
console.log(`deeplink-check: ${routeCount} routes agree across ${names.join(', ')}`);
