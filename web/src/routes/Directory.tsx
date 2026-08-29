import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Button, Card, DataTable, EmptyState, LoadingState } from '@/components/ui'
import { QueryErrorState } from '@/components/QueryErrorState'
import { directoryApi } from '@/lib/api'
import type { DirectoryEntry } from '@hr/client'

/**
 * Employee directory.
 *
 * Available to every authenticated employee. Finding a colleague's extension is not a privileged
 * operation, and gating it behind a permission would lock most of the workforce out of the feature
 * they open most often.
 *
 * What makes that safe is the narrowness of `DirectoryEntry`: the server never *selects* salary,
 * bank details, identity documents or date of birth for this endpoint, so there is nothing here to
 * leak. Anything more sensitive goes through the profile page, which applies a record check and
 * then field-level permissions.
 */
export function Directory() {
  const [query, setQuery] = useState('')
  const [debouncedQuery, setDebouncedQuery] = useState('')
  const [cursorStack, setCursorStack] = useState<Array<string | undefined>>([undefined])

  // Typing a name should not fire a request per keystroke. 250ms is long enough to collapse a
  // burst of typing and short enough that the results feel like they are tracking the input.
  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedQuery(query)
      // A new search invalidates every page position we had collected.
      setCursorStack([undefined])
    }, 250)
    return () => clearTimeout(timer)
  }, [query])

  const cursor = cursorStack[cursorStack.length - 1]

  const { data, error, isPending, isFetching } = useQuery({
    queryKey: ['directory', debouncedQuery, cursor],
    queryFn: () =>
      directoryApi.searchDirectory({
        query: debouncedQuery === '' ? undefined : debouncedQuery,
        cursor,
        limit: 25,
      }),
    // Without this the table blanks to a spinner between pages, which reads as a page reload
    // rather than a page change.
    placeholderData: keepPreviousData,
  })

  if (error !== null) return <QueryErrorState error={error} />

  const entries = data?.items ?? []

  return (
    <div className="page">
      <h1 className="page__title">Directory</h1>

      <Card>
        <div className="toolbar">
          <label className="field field--inline">
            <span className="field__label">Search</span>
            <input
              className="field__input"
              type="search"
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="Name, designation or department"
              // The results update as you type, so a screen reader needs telling. Without this
              // the table silently changes underneath a user who cannot see it happen.
              aria-controls="directory-results"
            />
          </label>
          {isFetching && <span className="toolbar__hint">Searching…</span>}
        </div>

        <div id="directory-results" aria-live="polite" aria-busy={isFetching}>
          {isPending ? (
            <LoadingState label="Loading the directory…" />
          ) : entries.length === 0 ? (
            <EmptyState
              title={debouncedQuery === '' ? 'No employees yet' : 'No matches'}
              description={
                debouncedQuery === ''
                  ? 'Employees appear here once they have been added.'
                  : `Nothing matched “${debouncedQuery}”.`
              }
            />
          ) : (
            <DataTable<DirectoryEntry>
              caption="Employee directory"
              rows={entries}
              rowKey={(row) => row.id}
              columns={[
                {
                  header: 'Name',
                  render: (row) => (
                    <Link to={`/employees/${row.id}`} className="link">
                      {row.displayName}
                    </Link>
                  ),
                },
                { header: 'Code', render: (row) => <code>{row.employeeCode}</code> },
                // Every one of these may legitimately be absent: the directory projection omits
                // what the caller has no business seeing, and not every employee has a
                // designation or a desk phone.
                { header: 'Designation', render: (row) => row.designation ?? '—' },
                { header: 'Department', render: (row) => row.department ?? '—' },
                { header: 'Location', render: (row) => row.location ?? '—' },
                {
                  header: 'Work email',
                  render: (row) =>
                    row.workEmail !== undefined ? (
                      <a className="link" href={`mailto:${row.workEmail}`}>
                        {row.workEmail}
                      </a>
                    ) : (
                      '—'
                    ),
                },
                { header: 'Mobile', render: (row) => row.mobile ?? '—' },
              ]}
            />
          )}
        </div>

        {/*
          Cursor pagination, so there is no page number and no "last page" — the server does not
          know a total and computing one would mean a second full scan on every request.
          `cursorStack` remembers where we came from, which is the only way back with an opaque
          cursor.
        */}
        <div className="pagination">
          <Button
            variant="secondary"
            disabled={cursorStack.length === 1}
            onClick={() => setCursorStack((stack) => stack.slice(0, -1))}
          >
            Previous
          </Button>
          <Button
            variant="secondary"
            disabled={data?.nextCursor === undefined}
            onClick={() =>
              setCursorStack((stack) =>
                data?.nextCursor !== undefined ? [...stack, data.nextCursor] : stack,
              )
            }
          >
            Next
          </Button>
        </div>
      </Card>
    </div>
  )
}
