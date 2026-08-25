#!/usr/bin/env node
/**
 * Generates platform token files from design/tokens.json.
 *
 * ## Why
 *
 * Android, web and iOS each need the same values in different syntax. Typed by
 * hand three times they drift — someone adjusts a hex for a contrast fix on
 * web, nobody touches Android, and the products stop looking like one company.
 * The divergence is never caught in review because no single diff shows both.
 *
 * ## What it also does
 *
 * Checks colour contrast before writing anything. A contrast failure is an
 * accessibility bug that ships silently: it looks fine to whoever chose the
 * colour, and is unreadable for someone else. Catching it here means it cannot
 * reach a screen. It found a real one on first run — see the outline/
 * outlineVariant split in tokens.json.
 *
 *   node design/generate.mjs           write the files
 *   node design/generate.mjs --check   fail if they are stale (CI)
 */

import { readFileSync, writeFileSync, mkdirSync } from 'node:fs'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..')
const checkOnly = process.argv.includes('--check')

const tokens = JSON.parse(readFileSync(join(ROOT, 'design', 'tokens.json'), 'utf8'))

let problems = 0
const fail = (message) => {
  console.error(`design: ${message}`)
  problems++
}

const entries = (object) => Object.entries(object).filter(([key]) => !key.startsWith('$'))

// ---------------------------------------------------------------------------
// Contrast checking (WCAG 2.2 relative luminance)
// ---------------------------------------------------------------------------

const srgbToLinear = (channel) => {
  const c = channel / 255
  return c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4
}

const luminance = (hex) => {
  const [r, g, b] = [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16))
  return 0.2126 * srgbToLinear(r) + 0.7152 * srgbToLinear(g) + 0.0722 * srgbToLinear(b)
}

const contrastRatio = (a, b) => {
  const [lighter, darker] = [luminance(a), luminance(b)].sort((x, y) => y - x)
  return (lighter + 0.05) / (darker + 0.05)
}

/**
 * Foreground/background pairs that must meet WCAG AA.
 *
 * Only pairs the UI genuinely renders. Checking every combination would flag
 * pairs nobody puts together and train people to ignore the output.
 */
const CONTRAST_PAIRS = [
  ['onSurface', 'surface', 'normal'],
  ['onSurface', 'surfaceRaised', 'normal'],
  ['onSurfaceMuted', 'surface', 'normal'],
  ['onSurfaceMuted', 'surfaceVariant', 'normal'],
  ['brandOnPrimary', 'brandPrimary', 'normal'],
  ['brandOnPrimaryContainer', 'brandPrimaryContainer', 'normal'],
  ['onDanger', 'danger', 'normal'],
  // Semantic colours carry status *text*, not just a swatch, so they need
  // text-grade contrast.
  ['success', 'surface', 'normal'],
  ['warning', 'surface', 'normal'],
  ['danger', 'surface', 'normal'],
  // WCAG 1.4.11 holds the boundary of a user interface component to 3:1 — an
  // input border, a focus ring, a toggle outline.
  ['outline', 'surface', 'large'],
  ['outline', 'surfaceRaised', 'large'],
  // outlineVariant is deliberately NOT checked. It is for decorative
  // separators such as table row dividers, where a 3:1 line is visually heavy
  // and makes dense data harder to read rather than easier. Using it for an
  // interactive border is the mistake to watch for in review.
]

for (const scheme of ['light', 'dark']) {
  const palette = tokens.color[scheme]

  for (const [foreground, background, size] of CONTRAST_PAIRS) {
    if (!palette[foreground] || !palette[background]) {
      fail(`${scheme}: contrast pair references unknown token ${foreground} or ${background}`)
      continue
    }

    const required =
      size === 'large'
        ? tokens.accessibility.minContrastLargeText
        : tokens.accessibility.minContrastNormalText

    const ratio = contrastRatio(palette[foreground], palette[background])
    if (ratio < required) {
      fail(
        `${scheme}: ${foreground} on ${background} is ${ratio.toFixed(2)}:1, ` +
          `below the ${required}:1 required (${palette[foreground]} on ${palette[background]})`,
      )
    }
  }
}

// Light and dark must define the same keys, or one platform renders a
// transparent colour in one theme and nobody notices until a screenshot.
const lightKeys = Object.keys(tokens.color.light).sort()
const darkKeys = Object.keys(tokens.color.dark).sort()
if (JSON.stringify(lightKeys) !== JSON.stringify(darkKeys)) {
  fail(
    'light and dark palettes differ — ' +
      `missing in dark: [${lightKeys.filter((k) => !darkKeys.includes(k))}], ` +
      `missing in light: [${darkKeys.filter((k) => !lightKeys.includes(k))}]`,
  )
}

if (problems > 0) {
  console.error(`\ndesign: ${problems} problem${problems === 1 ? '' : 's'} — nothing generated.`)
  process.exit(1)
}

// ---------------------------------------------------------------------------
// Emitters
// ---------------------------------------------------------------------------

const BANNER = [
  'GENERATED from design/tokens.json — do not edit.',
  'Run `node design/generate.mjs` after changing a token.',
  'CI fails if this file is stale.',
]

const camelToKebab = (s) => s.replace(/([a-z0-9])([A-Z])/g, '$1-$2').toLowerCase()
const indent = (text, spaces) =>
  text
    .split('\n')
    .map((line) => ' '.repeat(spaces) + line)
    .join('\n')

function androidTokens() {
  const colors = (scheme) =>
    entries(tokens.color[scheme])
      .map(([name, hex]) => `    val ${name} = Color(0xFF${hex.slice(1).toUpperCase()})`)
      .join('\n')

  // Kotlin identifiers cannot start with a digit, so the numeric scale becomes
  // s1, s2, s4… The name still states the value in 4pt units.
  const spacing = entries(tokens.spacing)
    .map(([name, value]) => `    val s${name} = ${value}.dp`)
    .join('\n')

  const radius = entries(tokens.radius)
    .map(([name, value]) => `    val ${name} = ${value}.dp`)
    .join('\n')

  const type = entries(tokens.typography.scale)
    .map(
      ([name, t]) =>
        `    val ${name} = TypeToken(size = ${t.size}.sp, lineHeight = ${t.lineHeight}.sp, weight = FontWeight(${t.weight}))`,
    )
    .join('\n')

  return `package com.hr.app.ui.theme

${BANNER.map((l) => `// ${l}`).join('\n')}

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object LightColors {
${colors('light')}
}

object DarkColors {
${colors('dark')}
}

/** 4pt base grid. The name states the value: s4 is 16.dp. */
object Spacing {
${spacing}
}

object Radius {
${radius}
}

data class TypeToken(val size: TextUnit, val lineHeight: TextUnit, val weight: FontWeight)

object TypeScale {
${type}
}
`
}

function webTokens() {
  const colors = (scheme) =>
    entries(tokens.color[scheme])
      .map(([name, hex]) => `  --color-${camelToKebab(name)}: ${hex.toLowerCase()};`)
      .join('\n')

  const shadows = (scheme) =>
    entries(tokens.shadow[scheme])
      .map(([name, value]) => `  --shadow-${name}: ${value};`)
      .join('\n')

  const spacing = entries(tokens.spacing)
    .map(([name, value]) => `  --space-${name}: ${value / 16}rem;`)
    .join('\n')

  const radius = entries(tokens.radius)
    .map(([name, value]) => `  --radius-${name}: ${value}px;`)
    .join('\n')

  const fonts = entries(tokens.font)
    .map(([name, value]) => `  --font-${name}: ${value};`)
    .join('\n')

  const type = entries(tokens.typography.scale)
    .flatMap(([name, t]) => [
      `  --text-${camelToKebab(name)}-size: ${t.size / 16}rem;`,
      `  --text-${camelToKebab(name)}-line-height: ${t.lineHeight / 16}rem;`,
      `  --text-${camelToKebab(name)}-weight: ${t.weight};`,
    ])
    .join('\n')

  return `${BANNER.map((l) => `/* ${l} */`).join('\n')}

:root {
  color-scheme: light dark;

${colors('light')}

${shadows('light')}

${spacing}

${radius}

${fonts}

${type}
}

@media (prefers-color-scheme: dark) {
  :root {
${indent(colors('dark'), 2)}

${indent(shadows('dark'), 2)}
  }
}
`
}

function iosTokens() {
  const swiftColor = (hex) => {
    const [r, g, b] = [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16))
    return `Color(red: ${(r / 255).toFixed(4)}, green: ${(g / 255).toFixed(4)}, blue: ${(b / 255).toFixed(4)})`
  }

  const colors = (scheme) =>
    entries(tokens.color[scheme])
      .map(([name, hex]) => `            static let ${name} = ${swiftColor(hex)}`)
      .join('\n')

  const spacing = entries(tokens.spacing)
    .map(([name, value]) => `        static let s${name}: CGFloat = ${value}`)
    .join('\n')

  const radius = entries(tokens.radius)
    .map(([name, value]) => `        static let ${name}: CGFloat = ${value}`)
    .join('\n')

  const type = entries(tokens.typography.scale)
    .map(
      ([name, t]) =>
        `        static let ${name} = TypeToken(size: ${t.size}, lineHeight: ${t.lineHeight}, weight: ${t.weight})`,
    )
    .join('\n')

  return `import SwiftUI

${BANNER.map((l) => `// ${l}`).join('\n')}

public enum DesignTokens {
    public struct TypeToken: Sendable {
        public let size: CGFloat
        public let lineHeight: CGFloat
        public let weight: Int
    }

    public enum Colors {
        public enum Light {
${colors('light')}
        }

        public enum Dark {
${colors('dark')}
        }
    }

    /// 4pt base grid. The name states the value: s4 is 16.
    public enum Spacing {
${spacing}
    }

    public enum Radius {
${radius}
    }

    public enum TypeScale {
${type}
    }
}
`
}

// ---------------------------------------------------------------------------

const outputs = {
  'android/app/src/main/kotlin/com/hr/app/ui/theme/Tokens.kt': androidTokens(),
  'web/src/tokens.css': webTokens(),
  'ios/Sources/HRCore/DesignTokens.swift': iosTokens(),
}

let stale = 0

for (const [relativePath, contents] of Object.entries(outputs)) {
  const path = join(ROOT, relativePath)

  if (checkOnly) {
    let existing = null
    try {
      existing = readFileSync(path, 'utf8')
    } catch {
      // Missing counts as stale.
    }
    if (existing !== contents) {
      console.error(`STALE ${relativePath}`)
      stale++
    }
    continue
  }

  mkdirSync(dirname(path), { recursive: true })
  writeFileSync(path, contents)
  console.log(`wrote ${relativePath}`)
}

if (checkOnly) {
  if (stale > 0) {
    console.error(`\ndesign: ${stale} generated file(s) stale. Run \`node design/generate.mjs\` and commit.`)
    process.exit(1)
  }
  console.log('design: generated token files are up to date')
}
