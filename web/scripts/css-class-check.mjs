#!/usr/bin/env node
/**
 * Every class name used in TSX has a rule in the CSS.
 *
 * Neither `tsc` nor Vite catches this: a typo'd or invented class name compiles, builds, ships,
 * and renders as an unstyled element. The failure is visual only, so it survives every automated
 * check in the pipeline and is found by a person looking at the screen — which is the most
 * expensive way to find it.
 *
 * Deliberately one-directional. It does **not** report CSS rules with no matching usage: those are
 * often intentional (state classes applied by a library, `:hover` variants, styles for markup that
 * has not landed yet), and a checker that fires on them would be muted within a week.
 *
 *   node web/scripts/css-class-check.mjs
 */

import { readFileSync, readdirSync, statSync } from 'node:fs'
import { join } from 'node:path'

const root = process.argv[2] ?? 'web/src'

function filesUnder(dir, extensions) {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name)
    if (statSync(path).isDirectory()) return filesUnder(path, extensions)
    return extensions.some((extension) => name.endsWith(extension)) ? [path] : []
  })
}

// ---------------------------------------------------------------------------
// Defined classes

const defined = new Set()
for (const file of filesUnder(root, ['.css'])) {
  const css = readFileSync(file, 'utf8').replace(/\/\*[\s\S]*?\*\//g, ' ')
  for (const match of css.matchAll(/\.(-?[_a-zA-Z][\w-]*)/g)) {
    defined.add(match[1])
  }
}

// ---------------------------------------------------------------------------
// Used classes
//
// Only literal `className="..."` and the string literals inside a className={...} expression.
// Anything computed is skipped rather than guessed at — a checker that cannot see
// `className={styles[key]}` should say nothing about it, not invent a name.

const CSS_IDENTIFIER = /^-?[_a-zA-Z][\w-]*$/
const JS_LITERALS = new Set(['undefined', 'null', 'true', 'false'])

const used = new Map() // class -> first file that used it

for (const file of filesUnder(root, ['.tsx', '.ts'])) {
  const source = readFileSync(file, 'utf8')

  for (const match of source.matchAll(/className\s*=\s*(?:"([^"]*)"|\{([^}]*)\})/g)) {
    const literal = match[1]
    const expression = match[2]

    const candidates = []
    if (literal !== undefined) {
      candidates.push(literal)
    } else if (expression !== undefined) {
      // Pull every quoted run out of the expression; ignore the rest.
      for (const quoted of expression.matchAll(/['"`]([^'"`]*)['"`]/g)) candidates.push(quoted[1])
    }

    for (const candidate of candidates) {
      for (const name of candidate.split(/\s+/).filter(Boolean)) {
        // A className expression contains operators as well as strings — `cond ? 'a' : 'b'`
        // yields `?` and `:` if you take every token. Only things shaped like a CSS identifier
        // are considered, and the JS literals that share that shape are excluded by name.
        if (!CSS_IDENTIFIER.test(name)) continue
        if (JS_LITERALS.has(name)) continue
        if (!used.has(name)) used.set(name, file)
      }
    }
  }
}

// ---------------------------------------------------------------------------

let problems = 0
for (const [name, file] of [...used].sort()) {
  if (!defined.has(name)) {
    console.error(`FAIL ${file} uses .${name}, which no stylesheet defines`)
    problems++
  }
}

console.log(`${used.size} class names used, ${defined.size} defined, ${problems} undefined`)
process.exit(problems > 0 ? 1 : 0)
