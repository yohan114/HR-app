import { Card, EmptyState, NoPermissionState } from '@/components/ui'
import { useAuth } from '@/lib/auth'

/**
 * Stands in for an admin screen whose API does not exist yet.
 *
 * Deliberately not a fake table of invented rows. A placeholder that looks like a working screen
 * gets demoed as one, and the gap surfaces later as a surprise. This says plainly what is missing
 * and which task delivers it.
 *
 * Note it still enforces the permission check, so the routing and authorisation wiring is
 * exercised now rather than being added along with the real screen.
 */
export function Placeholder({
  title,
  permission,
  blockedBy,
  description,
}: {
  title: string
  permission: string
  blockedBy: string
  description: string
}) {
  const { can } = useAuth()

  if (!can(permission)) {
    return (
      <div className="page">
        <h1 className="page__title">{title}</h1>
        <Card>
          <NoPermissionState permission={permission} />
        </Card>
      </div>
    )
  }

  return (
    <div className="page">
      <h1 className="page__title">{title}</h1>
      <Card>
        <EmptyState
          title="Not built yet"
          description={`${description} Delivered by ${blockedBy}.`}
        />
      </Card>
    </div>
  )
}
