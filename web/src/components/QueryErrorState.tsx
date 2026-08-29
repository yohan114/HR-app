import { useEffect, useState } from 'react'
import { ErrorState } from '@/components/ui'
import { extractApiError, humaniseError } from '@/lib/api'

/**
 * Renders a failed query using the server's error envelope.
 *
 * Separate from [ErrorState], which stays presentation-only and knows nothing about the API. This
 * is the piece that reads `error.code` and turns it into something a person can act on.
 *
 * Reading the body is asynchronous, so there is a moment before the specific message is known. It
 * shows the generic line first and replaces it — rather than showing a spinner, because an error
 * that renders as a loading state is worse than an imprecise error.
 */
export function QueryErrorState({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const [message, setMessage] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    void extractApiError(error).then((api) => {
      if (cancelled) return
      setMessage(api !== null ? humaniseError(api.code, api.requestId) : null)
    })
    return () => {
      cancelled = true
    }
  }, [error])

  return (
    <ErrorState
      description={message ?? 'Something went wrong. Please try again.'}
      {...(onRetry !== undefined ? { onRetry } : {})}
    />
  )
}
