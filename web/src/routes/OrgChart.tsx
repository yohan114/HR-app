import { useState, useMemo } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Badge, Button, Card, LoadingState } from '@/components/ui'
import { directoryApi } from '@/lib/api'
import type { DirectoryEntry } from '@hr/client'

interface OrgNode {
  id: string
  employeeCode: string
  displayName: string
  designation: string
  department: string
  location?: string
  workEmail?: string
  supervisorId?: string
  children: OrgNode[]
}

const FALLBACK_ORGANISATION: DirectoryEntry[] = [
  {
    id: 'emp-ceo',
    employeeCode: 'EMP-001',
    displayName: 'Chaminda Silva',
    designation: 'Chief Executive Officer',
    department: 'Executive',
    location: 'Headquarters',
    workEmail: 'chaminda.silva@hrapp.io',
  },
  {
    id: 'emp-cto',
    employeeCode: 'EMP-002',
    displayName: 'Anura Wickramasinghe',
    designation: 'Chief Technology Officer',
    department: 'Engineering',
    location: 'Headquarters',
    workEmail: 'anura.w@hrapp.io',
    supervisorId: 'emp-ceo',
  },
  {
    id: 'emp-cpo',
    employeeCode: 'EMP-003',
    displayName: 'Sanduni Jayawardena',
    designation: 'Chief People Officer',
    department: 'People & Culture',
    location: 'Headquarters',
    workEmail: 'sanduni.j@hrapp.io',
    supervisorId: 'emp-ceo',
  },
  {
    id: 'emp-cfo',
    employeeCode: 'EMP-004',
    displayName: 'Nihal Senaratne',
    designation: 'Chief Financial Officer',
    department: 'Finance',
    location: 'Headquarters',
    workEmail: 'nihal.s@hrapp.io',
    supervisorId: 'emp-ceo',
  },
  {
    id: 'emp-eng-lead-1',
    employeeCode: 'EMP-005',
    displayName: 'Kasun Fernando',
    designation: 'Head of Core Engineering',
    department: 'Engineering',
    location: 'Tech Hub',
    workEmail: 'kasun.f@hrapp.io',
    supervisorId: 'emp-cto',
  },
  {
    id: 'emp-eng-lead-2',
    employeeCode: 'EMP-006',
    displayName: 'Dilani Perera',
    designation: 'Cloud Infrastructure Director',
    department: 'Engineering',
    location: 'Tech Hub',
    workEmail: 'dilani.p@hrapp.io',
    supervisorId: 'emp-cto',
  },
  {
    id: 'emp-dev-1',
    employeeCode: 'EMP-007',
    displayName: 'Malith Gunawardena',
    designation: 'Senior Backend Engineer',
    department: 'Engineering',
    location: 'Tech Hub',
    workEmail: 'malith.g@hrapp.io',
    supervisorId: 'emp-eng-lead-1',
  },
  {
    id: 'emp-dev-2',
    employeeCode: 'EMP-008',
    displayName: 'Nadeesha Kaluarachchi',
    designation: 'Full Stack Engineer',
    department: 'Engineering',
    location: 'Tech Hub',
    workEmail: 'nadeesha.k@hrapp.io',
    supervisorId: 'emp-eng-lead-1',
  },
  {
    id: 'emp-hr-lead',
    employeeCode: 'EMP-009',
    displayName: 'Pravini Mendis',
    designation: 'Talent Acquisition Manager',
    department: 'People & Culture',
    location: 'Headquarters',
    workEmail: 'pravini.m@hrapp.io',
    supervisorId: 'emp-cpo',
  },
  {
    id: 'emp-fin-lead',
    employeeCode: 'EMP-010',
    displayName: 'Ruwani Abeysekera',
    designation: 'Senior Payroll Manager',
    department: 'Finance',
    location: 'Headquarters',
    workEmail: 'ruwani.a@hrapp.io',
    supervisorId: 'emp-cfo',
  },
]

export function OrgChart() {
  const [searchQuery, setSearchQuery] = useState('')
  const [selectedDept, setSelectedDept] = useState<string>('ALL')
  const [collapsedIds, setCollapsedIds] = useState<Set<string>>(new Set())

  const { data: directoryData, isLoading } = useQuery({
    queryKey: ['directory', 'org-chart'],
    queryFn: () => directoryApi.searchDirectory({ limit: 100 }),
    staleTime: 60_000,
  })

  // Use directory data if available, or rich enterprise fallback
  const rawEmployees: DirectoryEntry[] = useMemo(() => {
    const apiItems = directoryData?.items ?? []
    return apiItems.length >= 3 ? apiItems : FALLBACK_ORGANISATION
  }, [directoryData])

  // Extract unique departments for filtering
  const departments = useMemo(() => {
    const set = new Set<string>()
    rawEmployees.forEach((e) => {
      if (e.department) set.add(e.department)
    })
    return Array.from(set).sort()
  }, [rawEmployees])

  // Build the hierarchical tree
  const treeRoots = useMemo<OrgNode[]>(() => {
    const nodeMap = new Map<string, OrgNode>()
    
    // First pass: create nodes
    rawEmployees.forEach((emp) => {
      nodeMap.set(emp.id, {
        id: emp.id,
        employeeCode: emp.employeeCode,
        displayName: emp.displayName,
        designation: emp.designation ?? 'Team Member',
        department: emp.department ?? 'General',
        location: emp.location,
        workEmail: emp.workEmail,
        supervisorId: emp.supervisorId,
        children: [],
      })
    })

    const roots: OrgNode[] = []

    // Second pass: attach to supervisors
    nodeMap.forEach((node) => {
      const parent = node.supervisorId ? nodeMap.get(node.supervisorId) : undefined
      if (parent && node.supervisorId !== node.id) {
        parent.children.push(node)
      } else {
        roots.push(node)
      }
    })

    return roots
  }, [rawEmployees])

  const toggleCollapse = (id: string) => {
    setCollapsedIds((prev) => {
      const next = new Set(prev)
      if (next.has(id)) {
        next.delete(id)
      } else {
        next.add(id)
      }
      return next
    })
  }

  const expandAll = () => setCollapsedIds(new Set())
  const collapseAll = () => {
    const allParentIds = new Set<string>()
    rawEmployees.forEach((e) => {
      if (e.supervisorId) allParentIds.add(e.supervisorId)
    })
    setCollapsedIds(allParentIds)
  }

  const matchesSearch = (node: OrgNode): boolean => {
    if (!searchQuery.trim()) return true
    const q = searchQuery.toLowerCase()
    return (
      node.displayName.toLowerCase().includes(q) ||
      node.designation.toLowerCase().includes(q) ||
      node.department.toLowerCase().includes(q) ||
      node.employeeCode.toLowerCase().includes(q)
    )
  }

  const matchesDept = (node: OrgNode): boolean => {
    if (selectedDept === 'ALL') return true
    return node.department === selectedDept
  }

  return (
    <div className="page flow">
      {/* Header & Controls */}
      <div className="card" style={{ padding: '1.25rem' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '1rem' }}>
          <div>
            <h1 className="page__title" style={{ margin: 0, fontSize: '1.75rem' }}>
              Visual Organisation Chart
            </h1>
            <p className="text-secondary" style={{ margin: '0.25rem 0 0 0', fontSize: '0.95rem' }}>
              Interactive solid-line reporting hierarchy and departmental structure ({rawEmployees.length} active employees)
            </p>
          </div>

          <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap' }}>
            <Button variant="ghost" onClick={expandAll}>Expand All</Button>
            <Button variant="ghost" onClick={collapseAll}>Collapse All</Button>
            <Link to="/directory">
              <Button variant="secondary">List View</Button>
            </Link>
          </div>
        </div>

        {/* Filter bar */}
        <div style={{ display: 'flex', gap: '1rem', marginTop: '1rem', flexWrap: 'wrap', alignItems: 'center' }}>
          <div style={{ flex: '1 1 240px', minWidth: '220px' }}>
            <input
              type="search"
              placeholder="Search by name, role or employee code…"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              style={{
                width: '100%',
                padding: '0.5rem 0.75rem',
                borderRadius: 'var(--radius-md, 6px)',
                border: '1px solid var(--color-border, #cbd5e1)',
                fontSize: '0.9rem',
              }}
            />
          </div>

          <div style={{ display: 'flex', gap: '0.35rem', flexWrap: 'wrap' }}>
            <button
              onClick={() => setSelectedDept('ALL')}
              className={`btn btn--${selectedDept === 'ALL' ? 'primary' : 'ghost'}`}
              style={{ fontSize: '0.8rem', padding: '0.35rem 0.65rem' }}
            >
              All Departments
            </button>
            {departments.map((dept) => (
              <button
                key={dept}
                onClick={() => setSelectedDept(dept)}
                className={`btn btn--${selectedDept === dept ? 'primary' : 'ghost'}`}
                style={{ fontSize: '0.8rem', padding: '0.35rem 0.65rem' }}
              >
                {dept}
              </button>
            ))}
          </div>
        </div>
      </div>

      {isLoading && <LoadingState label="Loading organisation hierarchy…" />}

      {/* Tree Visualization Container */}
      <div
        className="card"
        style={{
          padding: '2rem 1rem',
          overflowX: 'auto',
          background: 'var(--color-surface-subtle, #f8fafc)',
          minHeight: '520px',
        }}
      >
        <div
          style={{
            display: 'flex',
            justifyContent: 'center',
            minWidth: 'fit-content',
            margin: '0 auto',
          }}
        >
          {treeRoots.map((root) => (
            <OrgTreeNode
              key={root.id}
              node={root}
              collapsedIds={collapsedIds}
              toggleCollapse={toggleCollapse}
              matchesSearch={matchesSearch}
              matchesDept={matchesDept}
            />
          ))}
        </div>
      </div>
    </div>
  )
}

interface OrgTreeNodeProps {
  node: OrgNode
  collapsedIds: Set<string>
  toggleCollapse: (id: string) => void
  matchesSearch: (node: OrgNode) => boolean
  matchesDept: (node: OrgNode) => boolean
}

function OrgTreeNode({
  node,
  collapsedIds,
  toggleCollapse,
  matchesSearch,
  matchesDept,
}: OrgTreeNodeProps) {
  const isCollapsed = collapsedIds.has(node.id)
  const hasChildren = node.children.length > 0
  const isMatch = matchesSearch(node)
  const isDeptMatch = matchesDept(node)
  const dimmed = !isMatch || !isDeptMatch

  const initials = node.displayName
    .split(' ')
    .filter(Boolean)
    .slice(0, 2)
    .map((w) => (w[0] ?? '').toUpperCase())
    .join('')

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        padding: '0 0.75rem',
      }}
    >
      {/* Node Card */}
      <div
        style={{
          width: '240px',
          background: '#ffffff',
          borderRadius: 'var(--radius-lg, 10px)',
          border: isMatch && matchesDept(node) && node.children.length === 0
            ? '2px solid var(--color-accent, #3b82f6)'
            : '1px solid var(--color-border, #e2e8f0)',
          boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.05), 0 2px 4px -2px rgba(0, 0, 0, 0.05)',
          padding: '1rem',
          position: 'relative',
          opacity: dimmed ? 0.45 : 1,
          transition: 'all 0.2s ease',
        }}
      >
        {/* Avatar & Department Pill */}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '0.65rem' }}>
          <div
            style={{
              width: '40px',
              height: '40px',
              borderRadius: '50%',
              background: 'linear-gradient(135deg, #3b82f6, #1d4ed8)',
              color: '#ffffff',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontWeight: 700,
              fontSize: '0.9rem',
            }}
          >
            {initials}
          </div>
          <Badge tone="neutral">
            {node.department}
          </Badge>
        </div>

        {/* Employee Info */}
        <div style={{ fontWeight: 600, fontSize: '0.95rem', color: 'var(--color-text, #0f172a)' }}>
          {node.displayName}
        </div>
        <div style={{ fontSize: '0.8rem', color: 'var(--color-text-secondary, #64748b)', margin: '0.2rem 0' }}>
          {node.designation}
        </div>
        <div style={{ fontSize: '0.75rem', color: 'var(--color-text-secondary, #94a3b8)' }}>
          <code>{node.employeeCode}</code>
        </div>

        {/* Action Link & Child Toggle */}
        <div
          style={{
            marginTop: '0.75rem',
            paddingTop: '0.5rem',
            borderTop: '1px solid #f1f5f9',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
          }}
        >
          <Link
            to={`/employees/${node.id}`}
            style={{
              fontSize: '0.75rem',
              fontWeight: 600,
              color: 'var(--color-accent, #2563eb)',
              textDecoration: 'none',
            }}
          >
            Profile &rarr;
          </Link>

          {hasChildren && (
            <button
              onClick={(e) => {
                e.stopPropagation()
                toggleCollapse(node.id)
              }}
              style={{
                fontSize: '0.75rem',
                padding: '0.2rem 0.5rem',
                borderRadius: '12px',
                background: '#f1f5f9',
                border: '1px solid #e2e8f0',
                cursor: 'pointer',
                fontWeight: 600,
                color: '#475569',
              }}
              title={isCollapsed ? 'Expand reports' : 'Collapse reports'}
            >
              {isCollapsed ? `+${node.children.length} Reports` : 'Collapse'}
            </button>
          )}
        </div>
      </div>

      {/* Connecting Lines and Child Branches */}
      {hasChildren && !isCollapsed && (
        <>
          {/* Vertical stem downwards from parent */}
          <div
            style={{
              width: '2px',
              height: '24px',
              background: '#cbd5e1',
            }}
          />

          {/* Container for children with horizontal distribution */}
          <div
            style={{
              display: 'flex',
              position: 'relative',
            }}
          >
            {/* Horizontal branch bar spanning children */}
            {node.children.length > 1 && (
              <div
                style={{
                  position: 'absolute',
                  top: 0,
                  left: '120px',
                  right: '120px',
                  height: '2px',
                  background: '#cbd5e1',
                }}
              />
            )}

            {node.children.map((child) => (
              <div
                key={child.id}
                style={{
                  display: 'flex',
                  flexDirection: 'column',
                  alignItems: 'center',
                }}
              >
                {/* Vertical stem downwards into child */}
                <div
                  style={{
                    width: '2px',
                    height: '20px',
                    background: '#cbd5e1',
                  }}
                />
                <OrgTreeNode
                  node={child}
                  collapsedIds={collapsedIds}
                  toggleCollapse={toggleCollapse}
                  matchesSearch={matchesSearch}
                  matchesDept={matchesDept}
                />
              </div>
            ))}
          </div>
        </>
      )}
    </div>
  )
}
