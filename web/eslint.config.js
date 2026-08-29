import js from '@eslint/js'
import reactHooks from 'eslint-plugin-react-hooks'
import tseslint from 'typescript-eslint'

/**
 * Lint rules for the admin console.
 *
 * Deliberately narrow. TypeScript already catches type errors and the build already fails on them,
 * so a rule that restates the compiler earns nothing. What is here catches the things `tsc` cannot
 * see: React's rules of hooks, and the handful of type-aware patterns that compile but are
 * reliably wrong.
 *
 * `react-hooks/exhaustive-deps` is the reason this file exists. A `useEffect` with a missing
 * dependency compiles, builds, ships, and produces a component that renders stale data until
 * something unrelated re-renders it — a bug that is very hard to find by reading and trivial to
 * find with this rule.
 */
export default tseslint.config(
  {
    // Generated and built output. The client is regenerated from the OpenAPI spec on every build,
    // so linting it would report problems nobody can fix in this repository.
    ignores: ['dist/**', 'node_modules/**', '../clients/**'],
  },
  js.configs.recommended,
  ...tseslint.configs.recommendedTypeChecked,
  {
    files: ['**/*.{ts,tsx}'],
    languageOptions: {
      parserOptions: {
        projectService: true,
        tsconfigRootDir: import.meta.dirname,
      },
    },
    plugins: {
      'react-hooks': reactHooks,
    },
    rules: {
      ...reactHooks.configs.recommended.rules,

      // The console talks to a generated client whose types are accurate, so an unchecked `any`
      // is nearly always a mistake rather than a deliberate escape.
      '@typescript-eslint/no-explicit-any': 'error',

      // Promise-returning functions passed where void is expected: an unhandled rejection that
      // never surfaces. Common in React event handlers, and invisible until production.
      '@typescript-eslint/no-misused-promises': 'error',

      // Underscore-prefixed bindings are intentional discards — the profile form destructures one
      // to drop a key from an object.
      '@typescript-eslint/no-unused-vars': [
        'error',
        { argsIgnorePattern: '^_', varsIgnorePattern: '^_' },
      ],
    },
  },
  {
    // Node scripts: plain ESM, no browser globals, no type-aware rules to apply.
    files: ['scripts/**/*.mjs', '*.config.{js,ts}'],
    ...tseslint.configs.disableTypeChecked,
    languageOptions: {
      globals: { process: 'readonly', console: 'readonly' },
    },
  },
)
