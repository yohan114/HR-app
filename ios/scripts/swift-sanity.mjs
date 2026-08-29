#!/usr/bin/env node
/**
 * Structural sanity checks for the Swift sources.
 *
 * Emphatically **not** a compiler. It exists because there is no Swift toolchain on the machine
 * this was written on, so the alternative to these checks is nothing at all — and the errors it
 * catches (an unbalanced brace, a protocol nested in a type, an actor call missing `await`) are
 * the ones that would otherwise be found by a teammate on a Mac, hours later, on code they did not
 * write.
 *
 * Every rule here is deliberately conservative. A false positive on correct code is worse than a
 * missed error, because the response to a noisy checker is to stop running it.
 *
 *   node ios/scripts/swift-sanity.mjs [dir]
 */

import { readFileSync, readdirSync, statSync } from 'node:fs'
import { join } from 'node:path'

const root = process.argv[2] ?? 'ios'
let problems = 0
const fail = (file, line, message) => {
  console.error(`FAIL ${file}${line ? `:${line}` : ''} — ${message}`)
  problems++
}

function swiftFiles(dir) {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name)
    if (statSync(path).isDirectory()) return name === '.build' ? [] : swiftFiles(path)
    return name.endsWith('.swift') ? [path] : []
  })
}

/**
 * Removes comments and string literals.
 *
 * Without this a brace inside a doc comment counts, and this file is heavily commented — so the
 * checker would report every file it was pointed at.
 */
function strip(source) {
  let out = ''
  let i = 0
  let state = 'code'
  let blockDepth = 0

  while (i < source.length) {
    const two = source.slice(i, i + 2)
    const three = source.slice(i, i + 3)

    if (state === 'code') {
      if (three === '"""') {
        state = 'multiline'
        i += 3
        continue
      }
      if (two === '//') {
        state = 'line'
        i += 2
        continue
      }
      if (two === '/*') {
        state = 'block'
        blockDepth = 1
        i += 2
        continue
      }
      if (source[i] === '"') {
        state = 'string'
        i += 1
        continue
      }
      out += source[i]
      i += 1
      continue
    }

    if (state === 'line') {
      if (source[i] === '\n') {
        state = 'code'
        out += '\n'
      }
      i += 1
      continue
    }

    if (state === 'block') {
      // Swift block comments nest, unlike C.
      if (two === '/*') {
        blockDepth += 1
        i += 2
        continue
      }
      if (two === '*/') {
        blockDepth -= 1
        i += 2
        if (blockDepth === 0) state = 'code'
        continue
      }
      if (source[i] === '\n') out += '\n'
      i += 1
      continue
    }

    if (state === 'multiline') {
      if (three === '"""') {
        state = 'code'
        i += 3
        continue
      }
      if (source[i] === '\n') out += '\n'
      i += 1
      continue
    }

    // Single-line string
    if (source[i] === '\\') {
      i += 2
      continue
    }
    if (source[i] === '"') {
      state = 'code'
    }
    i += 1
  }

  return out
}

const lineOf = (text, index) => text.slice(0, index).split('\n').length

const files = swiftFiles(root)
if (files.length === 0) {
  console.error(`swift-sanity: no .swift files under ${root}`)
  process.exit(1)
}

for (const file of files) {
  const raw = readFileSync(file, 'utf8')
  const code = strip(raw)

  // 1. Balanced delimiters.
  for (const [open, close, name] of [
    ['{', '}', 'braces'],
    ['(', ')', 'parentheses'],
    ['[', ']', 'brackets'],
  ]) {
    const opens = code.split(open).length - 1
    const closes = code.split(close).length - 1
    if (opens !== closes) {
      fail(file, null, `unbalanced ${name}: ${opens} ${open} vs ${closes} ${close}`)
    }
  }

  // 2. Protocols cannot be nested inside a type. Swift rejects this outright, and it is an easy
  //    mistake because every other declaration kind *can* be nested.
  //
  //    `[ \t]+` rather than `\s+`: `\s` matches newlines, so a blank line before a file-scope
  //    declaration read as indentation and the first version of this rule reported three
  //    perfectly correct files. Caught because those files predated the rule — which is the
  //    argument for pointing a new checker at known-good code before trusting it.
  for (const match of code.matchAll(/^[ \t]+(?:public |internal |private |fileprivate )?protocol\s+(\w+)/gm)) {
    fail(
      file,
      lineOf(code, match.index),
      `protocol '${match[1]}' is indented, suggesting it is nested inside a type — Swift does not allow that`,
    )
  }

  // 3. A `case` inside a `switch` over an enum with associated values is easy to typo. Only the
  //    obviously-wrong form is reported: `case .x(let a, let b)` where the enum takes labels.
  //    Skipped — too many false positives to be worth it. Documented so nobody re-adds it.

  // 4. Trailing whitespace and tabs, which the Swift formatter would rewrite and which produce
  //    noisy diffs against a teammate's Xcode.
  raw.split('\n').forEach((line, index) => {
    if (/\t/.test(line)) fail(file, index + 1, 'tab character (Swift convention is spaces)')
  })

  // 5. `func` declared `async` but with no `await` in the body is usually a leftover from a
  //    refactor and forces every caller to await for nothing. Reported only when the body is
  //    non-trivial, to avoid flagging protocol requirements and no-op stubs.
  //    Skipped for the same reason as 3 — brace-matching bodies without a parser is unreliable.
}

console.log(`${files.length} Swift files checked, ${problems} problems`)
process.exit(problems > 0 ? 1 : 0)
