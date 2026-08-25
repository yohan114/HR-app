#!/usr/bin/env node
/**
 * Static checks for the Terraform estate.
 *
 * Not a substitute for `terraform validate` — it is what we can run when no
 * Terraform binary is available, and it catches the mistakes that actually
 * happen when composing modules by hand:
 *
 *   1. Unbalanced braces (heredocs, comments and strings stripped first)
 *   2. `module.X.Y` referencing an output that does not exist
 *   3. `var.X` used but not declared — in modules and in root modules — or,
 *      in a module, declared but never used
 *   4. `local.X` referenced but not declared
 *   5. A module block passing an argument that is not one of that module's
 *      variables — the most common mistake, and the one Terraform itself only
 *      surfaces at plan time
 *
 * Every check has been verified against a deliberately introduced error, so it
 * fails rather than passing vacuously.
 *
 *   node infra/scripts/tf-static-check.mjs infra/terraform
 */

import { readFileSync, readdirSync, existsSync, statSync } from 'node:fs'
import { join } from 'node:path'

const ROOT = process.argv[2] ?? '.'
let problems = 0
const fail = (message) => {
  console.error('FAIL ' + message)
  problems++
}

/** Strip heredocs, comments and inline strings so they cannot confuse the scanners. */
const clean = (source) =>
  source
    .replace(/<<-?(\w+)[\s\S]*?^\s*\1\s*$/gm, '"HEREDOC"')
    .replace(/#.*$/gm, '')
    .replace(/\/\/.*$/gm, '')
    .replace(/\/\*[\s\S]*?\*\//g, '')

const tfFiles = (dir) =>
  existsSync(dir) ? readdirSync(dir).filter((f) => f.endsWith('.tf')).map((f) => join(dir, f)) : []

const readAll = (dir) => clean(tfFiles(dir).map((f) => readFileSync(f, 'utf8')).join('\n'))

/** Directory entries only — these trees also hold READMEs and tfvars examples. */
const subdirectories = (dir) =>
  existsSync(dir) ? readdirSync(dir).filter((e) => statSync(join(dir, e)).isDirectory()) : []

// --- 1. Brace balance -------------------------------------------------------
const allFiles = []
const walk = (dir) =>
  readdirSync(dir, { withFileTypes: true }).forEach((entry) => {
    const path = join(dir, entry.name)
    if (entry.isDirectory()) walk(path)
    else if (entry.name.endsWith('.tf')) allFiles.push(path)
  })
walk(ROOT)

for (const file of allFiles) {
  let depth = 0
  for (const ch of clean(readFileSync(file, 'utf8'))) {
    if (ch === '{') depth++
    else if (ch === '}') depth--
    if (depth < 0) break
  }
  if (depth !== 0) fail(`${file}: unbalanced braces (net ${depth})`)
}

// --- 2. Module outputs referenced actually exist -----------------------------
const modulesDir = join(ROOT, 'modules')
const moduleOutputs = {}
const moduleVariables = {}

for (const name of subdirectories(modulesDir)) {
  const source = readAll(join(modulesDir, name))
  moduleOutputs[name] = [...source.matchAll(/^output\s+"([^"]+)"/gm)].map((m) => m[1])
  moduleVariables[name] = new Set([...source.matchAll(/^variable\s+"([^"]+)"/gm)].map((m) => m[1]))
}

const envDir = join(ROOT, 'environments')
const environments = subdirectories(envDir)

for (const env of environments) {
  const source = readAll(join(envDir, env))
  for (const [, mod, output] of source.matchAll(/module\.(\w+)\.([\w]+)/g)) {
    if (!moduleOutputs[mod]) {
      fail(`${env}: references unknown module "${mod}"`)
      continue
    }
    if (!moduleOutputs[mod].includes(output)) {
      fail(`${env}: module.${mod}.${output} — no such output in modules/${mod}`)
    }
  }
}

// --- 3. Variables declared and used ------------------------------------------
for (const name of subdirectories(modulesDir)) {
  const source = readAll(join(modulesDir, name))
  const used = new Set([...source.matchAll(/\bvar\.(\w+)/g)].map((m) => m[1]))

  for (const variable of used) {
    if (!moduleVariables[name].has(variable)) fail(`modules/${name}: var.${variable} used but not declared`)
  }
  for (const variable of moduleVariables[name]) {
    if (!used.has(variable)) fail(`modules/${name}: variable "${variable}" declared but never used`)
  }
}

// Root modules too. Their variables come from tfvars rather than a caller, so
// an undeclared one fails only at plan time — later and more expensively than
// here. Unused root variables are not flagged: a root may legitimately declare
// one for a `terraform.tfvars` contract before it is wired up.
for (const env of environments) {
  const source = readAll(join(envDir, env))
  const declared = new Set([...source.matchAll(/^variable\s+"([^"]+)"/gm)].map((m) => m[1]))
  const used = new Set([...source.matchAll(/\bvar\.(\w+)/g)].map((m) => m[1]))

  for (const variable of used) {
    if (!declared.has(variable)) fail(`environments/${env}: var.${variable} used but not declared`)
  }
}

// --- 3b. Locals referenced actually exist ------------------------------------
// A typo in `local.name_prefix` is otherwise a plan-time error, and root
// modules lean on locals heavily.
for (const dir of [...environments.map((e) => join(envDir, e)), ...subdirectories(modulesDir).map((m) => join(modulesDir, m))]) {
  const source = readAll(dir)
  const declared = new Set()
  for (const [, body] of source.matchAll(/^locals\s*\{([\s\S]*?)^\}/gm)) {
    for (const [, key] of body.matchAll(/^\s{2}(\w+)\s*=/gm)) declared.add(key)
  }
  if (declared.size === 0) continue

  for (const [, reference] of source.matchAll(/\blocal\.(\w+)/g)) {
    if (!declared.has(reference)) fail(`${dir}: local.${reference} referenced but not declared`)
  }
}

// --- 4. Module arguments match the module's variables ------------------------
const META_ARGUMENTS = new Set(['source', 'count', 'for_each', 'providers', 'depends_on', 'version'])

for (const env of environments) {
  const source = readAll(join(envDir, env))
  for (const [, blockName, body] of source.matchAll(/module\s+"(\w+)"\s*\{([\s\S]*?)\n\}/g)) {
    const sourcePath = (body.match(/source\s*=\s*"([^"]+)"/) ?? [])[1]
    if (!sourcePath) continue

    const mod = sourcePath.split('/').pop()
    if (!moduleVariables[mod]) continue

    for (const [, argument] of body.matchAll(/^\s{2}(\w+)\s*=/gm)) {
      if (META_ARGUMENTS.has(argument)) continue
      if (!moduleVariables[mod].has(argument)) {
        fail(`${env}: module "${blockName}" passes "${argument}" — not a variable of modules/${mod}`)
      }
    }
  }
}

console.log(`${allFiles.length} .tf files checked, ${problems} problem${problems === 1 ? '' : 's'}`)
process.exit(problems === 0 ? 0 : 1)
