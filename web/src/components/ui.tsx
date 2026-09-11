import type { ButtonHTMLAttributes, InputHTMLAttributes, ReactNode } from 'react'
import { useEffect, useId } from 'react'
import './ui.css'

/**
 * Design system primitives.
 *
 * Deliberately small and hand-written rather than pulled from a component library. At this stage
 * the console needs six primitives; adopting a library would mean inheriting its design language,
 * its bundle, and its opinions about theming — all of which we would then fight when the real
 * design system lands in P0-DES-02.
 */

// ---------------------------------------------------------------------------
// Button
// ---------------------------------------------------------------------------

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary' | 'danger' | 'ghost'
  loading?: boolean
}

export function Button({
  variant = 'secondary',
  loading = false,
  children,
  disabled,
  ...rest
}: ButtonProps) {
  return (
    <button
      className={`btn btn--${variant}`}
      disabled={disabled === true || loading}
      // Announces the busy state rather than leaving screen reader users with a button that
      // silently stops responding.
      aria-busy={loading}
      {...rest}
    >
      {loading ? <span className="btn__spinner" aria-hidden="true" /> : null}
      {children}
    </button>
  )
}

// ---------------------------------------------------------------------------
// Field
// ---------------------------------------------------------------------------

interface FieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string
  error?: string | undefined
  hint?: string | undefined
}

/**
 * A labelled input.
 *
 * The label is always rendered and always associated — placeholder-as-label is inaccessible and
 * disappears the moment someone starts typing, which is when they most need it.
 */
export function Field({ label, error, hint, id, children, ...rest }: FieldProps & { children?: React.ReactNode }) {
  const generatedId = useId()
  const fieldId = id ?? generatedId
  const errorId = `${fieldId}-error`
  const hintId = `${fieldId}-hint`

  const describedBy = [error !== undefined ? errorId : null, hint !== undefined ? hintId : null]
    .filter((value): value is string => value !== null)
    .join(' ')

  return (
    <div className="field">
      <label className="field__label" htmlFor={fieldId}>
        {label}
      </label>
      {children ? (
        children
      ) : (
        <input
          id={fieldId}
          className={`field__input${error !== undefined ? ' field__input--error' : ''}`}
          aria-invalid={error !== undefined}
          aria-describedby={describedBy.length > 0 ? describedBy : undefined}
          {...rest}
        />
      )}
      {hint !== undefined ? (
        <p id={hintId} className="field__hint">
          {hint}
        </p>
      ) : null}
      {error !== undefined ? (
        // role="alert" so the message is announced when it appears, not only when focus reaches it.
        <p id={errorId} className="field__error" role="alert">
          {error}
        </p>
      ) : null}
    </div>
  )
}

// ---------------------------------------------------------------------------
// Card
// ---------------------------------------------------------------------------

export function Card({ title, actions, children }: { title?: string; actions?: ReactNode; children: ReactNode }) {
  return (
    <section className="card">
      {title !== undefined || actions !== undefined ? (
        <header className="card__header">
          {title !== undefined ? <h2 className="card__title">{title}</h2> : null}
          {actions !== undefined ? <div className="card__actions">{actions}</div> : null}
        </header>
      ) : null}
      <div className="card__body">{children}</div>
    </section>
  )
}

// ---------------------------------------------------------------------------
// States
//
// Every screen must define all six states (docs/05-screens-ux.md §6). These are the shared
// implementations of four of them, so a screen cannot accidentally ship without one.
// ---------------------------------------------------------------------------

export function LoadingState({ label = 'Loading…' }: { label?: string }) {
  return (
    <div className="state" role="status" aria-live="polite">
      <span className="state__spinner" aria-hidden="true" />
      <p>{label}</p>
    </div>
  )
}

export function EmptyState({
  title,
  description,
  action,
}: {
  title: string
  description: string
  action?: ReactNode
}) {
  return (
    <div className="state">
      <h3 className="state__title">{title}</h3>
      <p className="state__description">{description}</p>
      {action}
    </div>
  )
}

export function ErrorState({
  title = 'Something went wrong',
  description,
  onRetry,
}: {
  title?: string
  description: string
  onRetry?: () => void
}) {
  return (
    <div className="state state--error" role="alert">
      <h3 className="state__title">{title}</h3>
      <p className="state__description">{description}</p>
      {onRetry !== undefined ? (
        <Button variant="secondary" onClick={onRetry}>
          Try again
        </Button>
      ) : null}
    </div>
  )
}

export function NoPermissionState({ permission }: { permission: string }) {
  return (
    <div className="state">
      <h3 className="state__title">You don&rsquo;t have access to this</h3>
      <p className="state__description">
        This page needs the <code>{permission}</code> permission. Ask an administrator if you need it.
      </p>
    </div>
  )
}

// ---------------------------------------------------------------------------
// Table
// ---------------------------------------------------------------------------

export function DataTable<T>({
  columns,
  rows,
  rowKey,
  caption,
}: {
  columns: Array<{ header: string; render: (row: T) => ReactNode; numeric?: boolean }>
  rows: T[]
  rowKey: (row: T) => string
  caption: string
}) {
  return (
    <table className="table">
      {/* Screen readers announce the caption before the table, so a user landing on it by
          keyboard knows what they are in. */}
      <caption className="sr-only">{caption}</caption>
      <thead>
        <tr>
          {columns.map((column) => (
            <th key={column.header} scope="col" className={column.numeric === true ? 'numeric' : undefined}>
              {column.header}
            </th>
          ))}
        </tr>
      </thead>
      <tbody>
        {rows.map((row) => (
          <tr key={rowKey(row)}>
            {columns.map((column) => (
              <td key={column.header} className={column.numeric === true ? 'numeric' : undefined}>
                {column.render(row)}
              </td>
            ))}
          </tr>
        ))}
      </tbody>
    </table>
  )
}

// ---------------------------------------------------------------------------
// Badge
// ---------------------------------------------------------------------------

export function Badge({ tone, children }: { tone: 'neutral' | 'success' | 'warning' | 'danger'; children: ReactNode }) {
  return <span className={`badge badge--${tone}`}>{children}</span>
}

// ---------------------------------------------------------------------------
// Modal
// ---------------------------------------------------------------------------

export function Modal({
  isOpen,
  onClose,
  title,
  children,
  actions,
  size = 'medium',
}: {
  isOpen: boolean
  onClose: () => void
  title: string
  children: ReactNode
  actions?: ReactNode
  size?: 'small' | 'medium' | 'large'
}) {
  const titleId = useId()

  useEffect(() => {
    if (!isOpen) return
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [isOpen, onClose])

  if (!isOpen) return null

  return (
    <div className="modal-backdrop" onClick={onClose} role="presentation">
      <div
        className={`modal modal--${size}`}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        onClick={(e) => e.stopPropagation()}
      >
        <header className="modal__header">
          <h2 id={titleId} className="modal__title">
            {title}
          </h2>
          <button
            type="button"
            className="modal__close"
            onClick={onClose}
            aria-label="Close dialog"
          >
            &times;
          </button>
        </header>
        <div className="modal__body">{children}</div>
        {actions !== undefined && <footer className="modal__footer">{actions}</footer>}
      </div>
    </div>
  )
}

// ---------------------------------------------------------------------------
// Drawer
// ---------------------------------------------------------------------------

export function Drawer({
  isOpen,
  onClose,
  title,
  children,
  actions,
}: {
  isOpen: boolean
  onClose: () => void
  title: string
  children: ReactNode
  actions?: ReactNode
}) {
  const titleId = useId()

  useEffect(() => {
    if (!isOpen) return
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [isOpen, onClose])

  if (!isOpen) return null

  return (
    <div className="drawer-backdrop" onClick={onClose} role="presentation">
      <aside
        className="drawer"
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        onClick={(e) => e.stopPropagation()}
      >
        <header className="drawer__header">
          <h2 id={titleId} className="drawer__title">
            {title}
          </h2>
          <button
            type="button"
            className="modal__close"
            onClick={onClose}
            aria-label="Close panel"
          >
            &times;
          </button>
        </header>
        <div className="drawer__body">{children}</div>
        {actions !== undefined && <footer className="drawer__footer">{actions}</footer>}
      </aside>
    </div>
  )
}

// ---------------------------------------------------------------------------
// Switch
// ---------------------------------------------------------------------------

export function Switch({
  checked,
  onChange,
  label,
  description,
  disabled = false,
  id,
}: {
  checked: boolean
  onChange: (checked: boolean) => void
  label: string
  description?: string
  disabled?: boolean
  id?: string
}) {
  const generatedId = useId()
  const switchId = id ?? generatedId
  const descId = `${switchId}-desc`

  return (
    <div className="switch-row">
      <div className="switch-row__label-group">
        <label htmlFor={switchId} className="switch-row__label">
          {label}
        </label>
        {description !== undefined && (
          <span id={descId} className="switch-row__description">
            {description}
          </span>
        )}
      </div>
      <button
        id={switchId}
        type="button"
        role="switch"
        aria-checked={checked}
        aria-describedby={description !== undefined ? descId : undefined}
        disabled={disabled}
        className={`switch${checked ? ' switch--checked' : ''}`}
        onClick={() => onChange(!checked)}
      >
        <span className="switch__thumb" />
      </button>
    </div>
  )
}

// ---------------------------------------------------------------------------
// Tabs
// ---------------------------------------------------------------------------

export function Tabs<T extends string>({
  items,
  activeTab,
  onChange,
}: {
  items: Array<{ id: T; label: string; badge?: string | number }>
  activeTab: T
  onChange: (id: T) => void
}) {
  return (
    <div className="tabs" role="tablist">
      {items.map((item) => {
        const isActive = item.id === activeTab
        return (
          <button
            key={item.id}
            type="button"
            role="tab"
            aria-selected={isActive}
            className={`tab${isActive ? ' tab--active' : ''}`}
            onClick={() => onChange(item.id)}
          >
            <span>{item.label}</span>
            {item.badge !== undefined && (
              <span className={`tab__badge${isActive ? ' tab__badge--active' : ''}`}>
                {item.badge}
              </span>
            )}
          </button>
        )
      })}
    </div>
  )
}

