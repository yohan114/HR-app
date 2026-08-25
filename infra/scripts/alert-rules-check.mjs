#!/usr/bin/env node
/**
 * Structural checks for Prometheus alert rules.
 *
 * Not a substitute for "promtool check rules", which validates PromQL properly.
 * This is what runs without a Prometheus binary, and it catches the authoring
 * mistakes that actually happen — most of which promtool would not object to
 * anyway, because they are conventions rather than syntax:
 *
 *   1. A severity outside {critical, warning}. Alertmanager routes on this
 *      label, so an unrecognised value silently drops the alert entirely —
 *      the worst possible failure mode for a monitoring rule.
 *   2. A missing summary or description: a page with no information.
 *   3. A critical alert with no runbook link. If it is worth waking someone
 *      for, it is worth telling them what to do about it.
 *   4. Duplicate alert names within a file.
 *   5. Unbalanced parentheses in an expression.
 *
 * Each check was verified against a deliberately introduced fault, so it fails
 * rather than passing vacuously.
 *
 *   npm --prefix infra/scripts ci
 *   node infra/scripts/alert-rules-check.mjs infra/k8s/observability
 */

import { readFileSync, readdirSync } from 'node:fs'
import { join } from 'node:path'
import { createRequire } from 'node:module'

const yaml = createRequire(import.meta.url)('js-yaml')

const directory = process.argv[2] ?? '.'
const VALID_SEVERITIES = ['critical', 'warning']

let problems = 0
let alertCount = 0
const fail = (message) => {
  console.error('FAIL ' + message)
  problems++
}

const ruleFiles = readdirSync(directory).filter((f) => f.startsWith('alerts-') && /\.ya?ml$/.test(f))

if (ruleFiles.length === 0) {
  console.error(`alert-rules-check: no alerts-*.yaml found in ${directory}`)
  process.exit(1)
}

for (const file of ruleFiles) {
  const document = yaml.load(readFileSync(join(directory, file), 'utf8'))

  if (document?.kind !== 'PrometheusRule') {
    fail(`${file}: expected kind PrometheusRule, got "${document?.kind}"`)
    continue
  }

  const seenNames = new Set()

  for (const group of document.spec?.groups ?? []) {
    if (!group.name) fail(`${file}: a group is missing its name`)

    for (const rule of group.rules ?? []) {
      // Recording rules have `record` rather than `alert`; skip them.
      if (!rule.alert) continue
      alertCount++

      if (seenNames.has(rule.alert)) fail(`${file}: duplicate alert name "${rule.alert}"`)
      seenNames.add(rule.alert)

      const severity = rule.labels?.severity
      if (!VALID_SEVERITIES.includes(severity)) {
        fail(`${file}/${rule.alert}: severity must be one of ${VALID_SEVERITIES.join(', ')}, got "${severity}"`)
      }

      if (!rule.annotations?.summary) fail(`${file}/${rule.alert}: missing annotations.summary`)
      if (!rule.annotations?.description) fail(`${file}/${rule.alert}: missing annotations.description`)

      if (severity === 'critical' && !rule.annotations?.runbook) {
        fail(`${file}/${rule.alert}: critical alerts must carry a runbook link`)
      }

      if (typeof rule.expr !== 'string' || rule.expr.trim() === '') {
        fail(`${file}/${rule.alert}: missing expr`)
        continue
      }

      const open = (rule.expr.match(/\(/g) ?? []).length
      const close = (rule.expr.match(/\)/g) ?? []).length
      if (open !== close) {
        fail(`${file}/${rule.alert}: unbalanced parentheses in expr (${open} open, ${close} close)`)
      }
    }
  }
}

console.log(`${alertCount} alerts checked across ${ruleFiles.length} files, ${problems} problem${problems === 1 ? '' : 's'}`)
process.exit(problems === 0 ? 0 : 1)
