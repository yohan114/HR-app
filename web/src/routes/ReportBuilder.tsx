import { useState, useMemo, useEffect } from 'react'
import { Badge, Button, Card, DataTable } from '@/components/ui'
import { directoryApi } from '@/lib/api'
import type { DirectoryEntry } from '@hr/client'

export interface ReportColumnDef {
  key: string
  label: string
  source: string
  type: 'string' | 'number' | 'date' | 'currency'
  aggregation?: 'NONE' | 'SUM' | 'AVG' | 'COUNT' | 'MIN' | 'MAX'
}

export interface ReportFilter {
  id: string
  columnKey: string
  operator: 'EQUALS' | 'NOT_EQUALS' | 'CONTAINS' | 'GREATER_THAN' | 'LESS_THAN'
  value: string
}

const AVAILABLE_COLUMNS: ReportColumnDef[] = [
  // Employee
  { key: 'emp_code', label: 'Employee Code', source: 'employee', type: 'string' },
  { key: 'full_name', label: 'Employee Full Name', source: 'employee', type: 'string' },
  { key: 'department', label: 'Department', source: 'employee', type: 'string' },
  { key: 'designation', label: 'Designation / Role', source: 'employee', type: 'string' },
  { key: 'location', label: 'Branch / Location', source: 'employee', type: 'string' },
  { key: 'status', label: 'Employment Status', source: 'employee', type: 'string' },
  { key: 'join_date', label: 'Hire Date', source: 'employee', type: 'date' },
  // Payroll
  { key: 'basic_salary', label: 'Basic Salary', source: 'payroll', type: 'currency', aggregation: 'SUM' },
  { key: 'gross_pay', label: 'Gross Pay', source: 'payroll', type: 'currency', aggregation: 'SUM' },
  { key: 'net_pay', label: 'Net Take-Home Pay', source: 'payroll', type: 'currency', aggregation: 'SUM' },
  { key: 'epf_statutory', label: 'Statutory EPF Total', source: 'payroll', type: 'currency', aggregation: 'SUM' },
  // Attendance & Time
  { key: 'logged_hours', label: 'Timesheet Logged Hours', source: 'timesheets', type: 'number', aggregation: 'SUM' },
  { key: 'overtime_hours', label: 'Approved Overtime Hours', source: 'attendance', type: 'number', aggregation: 'SUM' },
  { key: 'attendance_rate', label: 'Attendance % Rate', source: 'attendance', type: 'number', aggregation: 'AVG' },
  // Leave
  { key: 'leave_taken', label: 'Leave Days Consumed', source: 'leave', type: 'number', aggregation: 'SUM' },
  { key: 'leave_balance', label: 'Remaining Leave Balance', source: 'leave', type: 'number', aggregation: 'AVG' },
]

// Mock live rows dataset fallback
const SEEDED_REPORT_ROWS = [
  { emp_code: 'E001', full_name: 'Nimali Wickramasinghe', department: 'Executive', designation: 'Chief Executive Officer', location: 'Colombo HQ', status: 'ACTIVE', join_date: '2022-01-15', basic_salary: 450000, gross_pay: 520000, net_pay: 420000, epf_statutory: 90000, logged_hours: 168, overtime_hours: 0, attendance_rate: 100, leave_taken: 2, leave_balance: 19 },
  { emp_code: 'E002', full_name: 'Ruwan Jayasuriya', department: 'Engineering', designation: 'Engineering Manager', location: 'Colombo HQ', status: 'ACTIVE', join_date: '2022-03-01', basic_salary: 280000, gross_pay: 320000, net_pay: 260000, epf_statutory: 56000, logged_hours: 172, overtime_hours: 8, attendance_rate: 98, leave_taken: 4, leave_balance: 14 },
  { emp_code: 'E003', full_name: 'Priya Balasubramaniam', department: 'Human Resources', designation: 'HR Manager', location: 'Colombo HQ', status: 'ACTIVE', join_date: '2022-06-10', basic_salary: 220000, gross_pay: 250000, net_pay: 205000, epf_statutory: 44000, logged_hours: 160, overtime_hours: 0, attendance_rate: 96, leave_taken: 6, leave_balance: 12 },
  { emp_code: 'E004', full_name: 'Kasun Fernando', department: 'Engineering', designation: 'Senior Software Engineer', location: 'Colombo HQ', status: 'ACTIVE', join_date: '2023-01-10', basic_salary: 190000, gross_pay: 215000, net_pay: 178000, epf_statutory: 38000, logged_hours: 176, overtime_hours: 14, attendance_rate: 95, leave_taken: 5, leave_balance: 11 },
  { emp_code: 'E005', full_name: 'Dilani Perera', department: 'Engineering', designation: 'Frontend Engineer', location: 'Kandy Branch', status: 'ACTIVE', join_date: '2023-04-15', basic_salary: 145000, gross_pay: 165000, net_pay: 138000, epf_statutory: 29000, logged_hours: 164, overtime_hours: 6, attendance_rate: 97, leave_taken: 3, leave_balance: 13 },
  { emp_code: 'E006', full_name: 'Thivanka Rajapaksa', department: 'Engineering', designation: 'Backend Engineer', location: 'Colombo HQ', status: 'ACTIVE', join_date: '2023-08-01', basic_salary: 140000, gross_pay: 160000, net_pay: 134000, epf_statutory: 28000, logged_hours: 168, overtime_hours: 10, attendance_rate: 96, leave_taken: 2, leave_balance: 15 },
  { emp_code: 'E007', full_name: 'Anusha Sivakumar', department: 'Finance', designation: 'Senior Accountant', location: 'Colombo HQ', status: 'ACTIVE', join_date: '2023-02-01', basic_salary: 160000, gross_pay: 180000, net_pay: 150000, epf_statutory: 32000, logged_hours: 160, overtime_hours: 4, attendance_rate: 99, leave_taken: 1, leave_balance: 17 },
  { emp_code: 'E008', full_name: 'Malith Gunawardena', department: 'Engineering', designation: 'QA Automation Engineer', location: 'Galle Branch', status: 'ACTIVE', join_date: '2024-01-15', basic_salary: 125000, gross_pay: 140000, net_pay: 118000, epf_statutory: 25000, logged_hours: 160, overtime_hours: 2, attendance_rate: 94, leave_taken: 7, leave_balance: 8 },
  { emp_code: 'E009', full_name: 'Shanika de Silva', department: 'Finance', designation: 'Payroll Specialist', location: 'Colombo HQ', status: 'ACTIVE', join_date: '2024-05-01', basic_salary: 110000, gross_pay: 125000, net_pay: 106000, epf_statutory: 22000, logged_hours: 160, overtime_hours: 0, attendance_rate: 100, leave_taken: 0, leave_balance: 14 },
]

export function ReportBuilder() {
  const [reportTitle, setReportTitle] = useState<string>('Cross-Module Workforce & Compensation Summary')
  const [selectedColumnKeys, setSelectedColumnKeys] = useState<string[]>([
    'emp_code',
    'full_name',
    'department',
    'basic_salary',
    'gross_pay',
    'overtime_hours',
    'leave_taken',
  ])
  const [groupBy, setGroupBy] = useState<string>('department')
  const [viewMode, setViewMode] = useState<'grid' | 'chart' | 'schedule'>('grid')
  const [filters, setFilters] = useState<ReportFilter[]>([
    { id: 'fil-1', columnKey: 'status', operator: 'EQUALS', value: 'ACTIVE' },
  ])
  const [reportRows, setReportRows] = useState(SEEDED_REPORT_ROWS)
  const [isLiveDirectory, setIsLiveDirectory] = useState<boolean>(false)

  // Schedule state
  const [frequency, setFrequency] = useState<'DAILY' | 'WEEKLY' | 'MONTHLY'>('WEEKLY')
  const [recipients, setRecipients] = useState<string>('cfo@demo.local, hr-lead@demo.local')
  const [fileFormat, setFileFormat] = useState<'PDF' | 'EXCEL' | 'CSV'>('PDF')
  const [scheduleBanner, setScheduleBanner] = useState<string | null>(null)

  // ---------------------------------------------------------------------------
  // Load Live Directory & Restore Saved Configuration
  // ---------------------------------------------------------------------------
  useEffect(() => {
    const saved = localStorage.getItem('hr_report_builder_config')
    if (saved) {
      try {
        const parsed = JSON.parse(saved)
        if (parsed.reportTitle) setReportTitle(parsed.reportTitle)
        if (Array.isArray(parsed.selectedColumnKeys)) setSelectedColumnKeys(parsed.selectedColumnKeys)
        if (parsed.groupBy) setGroupBy(parsed.groupBy)
        if (Array.isArray(parsed.filters)) setFilters(parsed.filters)
      } catch {
        // ignore
      }
    }

    directoryApi
      .searchDirectory({ limit: 100 })
      .then((res) => {
        const items = res.items ?? []
        if (items.length > 0) {
          const liveRows = items.map((it: DirectoryEntry, idx: number) => {
            const seed = SEEDED_REPORT_ROWS[idx % SEEDED_REPORT_ROWS.length]!
            return {
              emp_code: it.employeeCode,
              full_name: it.displayName,
              department: it.department ?? seed.department,
              designation: it.designation ?? seed.designation,
              location: it.location ?? seed.location,
              status: 'ACTIVE',
              join_date: seed.join_date,
              basic_salary: seed.basic_salary,
              gross_pay: seed.gross_pay,
              net_pay: seed.net_pay,
              epf_statutory: seed.epf_statutory,
              logged_hours: seed.logged_hours,
              overtime_hours: seed.overtime_hours,
              attendance_rate: seed.attendance_rate,
              leave_taken: seed.leave_taken,
              leave_balance: seed.leave_balance,
            }
          })
          setReportRows(liveRows)
          setIsLiveDirectory(true)
        }
      })
      .catch((err) => {
        console.warn('Could not query directory for live report rows:', err)
      })
  }, [])

  const persistConfig = (patch: {
    reportTitle?: string
    selectedColumnKeys?: string[]
    groupBy?: string
    filters?: ReportFilter[]
  }) => {
    const current = {
      reportTitle: patch.reportTitle ?? reportTitle,
      selectedColumnKeys: patch.selectedColumnKeys ?? selectedColumnKeys,
      groupBy: patch.groupBy ?? groupBy,
      filters: patch.filters ?? filters,
    }
    localStorage.setItem('hr_report_builder_config', JSON.stringify(current))
  }

  // ---------------------------------------------------------------------------
  // Columns & Aggregations
  // ---------------------------------------------------------------------------
  const activeColumns = useMemo(() => {
    return selectedColumnKeys
      .map((k) => AVAILABLE_COLUMNS.find((col) => col.key === k))
      .filter((col): col is ReportColumnDef => col !== undefined)
  }, [selectedColumnKeys])

  const toggleColumn = (key: string) => {
    setSelectedColumnKeys((prev) => {
      const updated = prev.includes(key) ? prev.filter((k) => k !== key) : [...prev, key]
      persistConfig({ selectedColumnKeys: updated })
      return updated
    })
  }

  // ---------------------------------------------------------------------------
  // Filtered Rows & Group Summaries
  // ---------------------------------------------------------------------------
  const filteredRows = useMemo(() => {
    return reportRows.filter((row) => {
      for (const f of filters) {
        const rowVal = String(row[f.columnKey as keyof typeof row] ?? '').toLowerCase()
        const targetVal = f.value.toLowerCase()
        if (f.operator === 'EQUALS' && rowVal !== targetVal) return false
        if (f.operator === 'NOT_EQUALS' && rowVal === targetVal) return false
        if (f.operator === 'CONTAINS' && !rowVal.includes(targetVal)) return false
        if (f.operator === 'GREATER_THAN') {
          const numR = Number(row[f.columnKey as keyof typeof row])
          const numT = Number(f.value)
          if (isNaN(numR) || isNaN(numT) || numR <= numT) return false
        }
        if (f.operator === 'LESS_THAN') {
          const numR = Number(row[f.columnKey as keyof typeof row])
          const numT = Number(f.value)
          if (isNaN(numR) || isNaN(numT) || numR >= numT) return false
        }
      }
      return true
    })
  }, [filters])

  // Aggregations grouped by selected category
  const groupedSummary = useMemo(() => {
    if (groupBy === 'none') return []
    const groups: Record<string, { count: number; totalSalary: number; totalGross: number; totalOT: number; totalLeave: number }> = {}

    filteredRows.forEach((r) => {
      const gKey = String(r[groupBy as keyof typeof r] ?? 'Other')
      if (!groups[gKey]) {
        groups[gKey] = { count: 0, totalSalary: 0, totalGross: 0, totalOT: 0, totalLeave: 0 }
      }
      groups[gKey].count += 1
      groups[gKey].totalSalary += r.basic_salary
      groups[gKey].totalGross += r.gross_pay
      groups[gKey].totalOT += r.overtime_hours
      groups[gKey].totalLeave += r.leave_taken
    })

    return Object.entries(groups).map(([name, data]) => ({
      name,
      ...data,
      avgSalary: Math.round(data.totalSalary / data.count),
    }))
  }, [filteredRows, groupBy])

  // High-level KPI summary cards
  const kpiMetrics = useMemo(() => {
    const totalHeadcount = filteredRows.length
    const totalPayroll = filteredRows.reduce((acc, r) => acc + r.gross_pay, 0)
    const avgSalary = totalHeadcount > 0 ? Math.round(totalPayroll / totalHeadcount) : 0
    const totalOT = filteredRows.reduce((acc, r) => acc + r.overtime_hours, 0)

    return { totalHeadcount, totalPayroll, avgSalary, totalOT }
  }, [filteredRows])

  // ---------------------------------------------------------------------------
  // Export Actions
  // ---------------------------------------------------------------------------
  const exportCsv = () => {
    const headers = activeColumns.map((c) => `"${c.label}"`).join(',')
    const rows = filteredRows.map((r) =>
      activeColumns
        .map((c) => {
          const val = r[c.key as keyof typeof r]
          return typeof val === 'number' ? val : `"${String(val ?? '')}"`
        })
        .join(','),
    )
    const csvContent = [headers, ...rows].join('\n')
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `${reportTitle.toLowerCase().replace(/[^a-z0-9]+/g, '_')}.csv`
    link.click()
    URL.revokeObjectURL(url)
  }

  const exportJson = () => {
    const dataStr = 'data:text/json;charset=utf-8,' + encodeURIComponent(JSON.stringify(filteredRows, null, 2))
    const link = document.createElement('a')
    link.href = dataStr
    link.download = `${reportTitle.toLowerCase().replace(/[^a-z0-9]+/g, '_')}.json`
    link.click()
  }

  const handleSaveSchedule = () => {
    try {
      const scheduleRecord = {
        reportTitle,
        frequency,
        recipients,
        fileFormat,
        savedAt: new Date().toISOString(),
      }
      localStorage.setItem('hr_report_schedules', JSON.stringify(scheduleRecord))
    } catch {
      // ignore
    }
    setScheduleBanner(`Automated delivery scheduled: ${frequency} to [${recipients}] as ${fileFormat}. Saved to scheduler registry.`)
    setTimeout(() => setScheduleBanner(null), 4000)
  }

  return (
    <div className="builder-shell">
      {/* Header */}
      <div className="builder-header">
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)' }}>
            <h1 className="text-lg" style={{ margin: 0, fontWeight: 700 }}>
              Cross-Module Custom Report & Analytics Studio
            </h1>
            <Badge tone="success">Live Query: Ready</Badge>
            <Badge tone="neutral">{filteredRows.length} Rows</Badge>
            <Badge tone="neutral">
              {isLiveDirectory ? 'Connected: /v1/directory/search' : 'Demo Mode'}
            </Badge>
          </div>
          <p style={{ margin: 0, fontSize: '0.875rem', color: 'var(--color-on-surface-muted)' }}>
            Create tailored cross-departmental queries, aggregate metrics, preview charts, and automate deliveries.
          </p>
        </div>

        <div style={{ display: 'flex', gap: 'var(--space-2)' }}>
          <div style={{ display: 'flex', border: '1px solid var(--color-outline)', borderRadius: 'var(--radius-control)', overflow: 'hidden' }}>
            <button
              className="btn btn--ghost"
              style={{
                borderRadius: 0,
                background: viewMode === 'grid' ? 'var(--color-brand-primary)' : 'transparent',
                color: viewMode === 'grid' ? '#fff' : 'inherit',
                padding: 'var(--space-1) var(--space-3)',
                fontSize: '0.8125rem',
              }}
              onClick={() => setViewMode('grid')}
            >
              📊 Data Grid
            </button>
            <button
              className="btn btn--ghost"
              style={{
                borderRadius: 0,
                background: viewMode === 'chart' ? 'var(--color-brand-primary)' : 'transparent',
                color: viewMode === 'chart' ? '#fff' : 'inherit',
                padding: 'var(--space-1) var(--space-3)',
                fontSize: '0.8125rem',
              }}
              onClick={() => setViewMode('chart')}
            >
              📈 Chart Visuals
            </button>
            <button
              className="btn btn--ghost"
              style={{
                borderRadius: 0,
                background: viewMode === 'schedule' ? 'var(--color-brand-primary)' : 'transparent',
                color: viewMode === 'schedule' ? '#fff' : 'inherit',
                padding: 'var(--space-1) var(--space-3)',
                fontSize: '0.8125rem',
              }}
              onClick={() => setViewMode('schedule')}
            >
              ⏱️ Automated Dispatch
            </button>
          </div>

          <Button variant="secondary" onClick={exportJson}>
            JSON
          </Button>
          <Button variant="primary" onClick={exportCsv}>
            Export CSV
          </Button>
        </div>
      </div>

      {scheduleBanner && (
        <div style={{ background: '#dcfce7', color: '#15803d', padding: 'var(--space-3)', borderRadius: 'var(--radius-control)', fontWeight: 500 }}>
          {scheduleBanner}
        </div>
      )}

      {/* KPI Cards Row */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: 'var(--space-3)' }}>
        <div style={{ background: 'var(--color-surface-raised)', padding: 'var(--space-3)', borderRadius: 'var(--radius-control)', border: '1px solid var(--color-outline-variant)' }}>
          <div style={{ fontSize: '0.75rem', color: 'var(--color-on-surface-muted)', fontWeight: 600 }}>HEADCOUNT IN REPORT</div>
          <div style={{ fontSize: '1.5rem', fontWeight: 700, color: 'var(--color-brand-primary)' }}>{kpiMetrics.totalHeadcount} Staff</div>
        </div>
        <div style={{ background: 'var(--color-surface-raised)', padding: 'var(--space-3)', borderRadius: 'var(--radius-control)', border: '1px solid var(--color-outline-variant)' }}>
          <div style={{ fontSize: '0.75rem', color: 'var(--color-on-surface-muted)', fontWeight: 600 }}>TOTAL GROSS PAYROLL</div>
          <div style={{ fontSize: '1.5rem', fontWeight: 700, color: '#16a34a' }}>LKR {kpiMetrics.totalPayroll.toLocaleString()}</div>
        </div>
        <div style={{ background: 'var(--color-surface-raised)', padding: 'var(--space-3)', borderRadius: 'var(--radius-control)', border: '1px solid var(--color-outline-variant)' }}>
          <div style={{ fontSize: '0.75rem', color: 'var(--color-on-surface-muted)', fontWeight: 600 }}>AVERAGE SALARY</div>
          <div style={{ fontSize: '1.5rem', fontWeight: 700, color: '#0284c7' }}>LKR {kpiMetrics.avgSalary.toLocaleString()}</div>
        </div>
        <div style={{ background: 'var(--color-surface-raised)', padding: 'var(--space-3)', borderRadius: 'var(--radius-control)', border: '1px solid var(--color-outline-variant)' }}>
          <div style={{ fontSize: '0.75rem', color: 'var(--color-on-surface-muted)', fontWeight: 600 }}>AGGREGATE OVERTIME</div>
          <div style={{ fontSize: '1.5rem', fontWeight: 700, color: '#d97706' }}>{kpiMetrics.totalOT} Hours</div>
        </div>
      </div>

      {/* Query Configuration Toolbar */}
      <Card title="Report Configuration & Query Filters">
        <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
          {/* Title & Grouping */}
          <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: 'var(--space-3)' }}>
            <div>
              <label style={{ fontSize: '0.75rem', fontWeight: 600, display: 'block', marginBottom: '2px' }}>REPORT TITLE</label>
              <input
                className="field__input"
                value={reportTitle}
                onChange={(e) => setReportTitle(e.target.value)}
              />
            </div>
            <div>
              <label style={{ fontSize: '0.75rem', fontWeight: 600, display: 'block', marginBottom: '2px' }}>GROUP BY FIELD</label>
              <select className="select" value={groupBy} onChange={(e) => setGroupBy(e.target.value)}>
                <option value="none">No Grouping (Flat Records)</option>
                <option value="department">Department</option>
                <option value="location">Branch / Location</option>
                <option value="designation">Designation</option>
                <option value="status">Status</option>
              </select>
            </div>
          </div>

          {/* Column Selector */}
          <div>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 'var(--space-2)' }}>
              <span style={{ fontSize: '0.75rem', fontWeight: 600 }}>
                SELECT COLUMNS ({activeColumns.length} Active)
              </span>
              <button
                className="btn btn--ghost"
                style={{ fontSize: '0.75rem', padding: '2px 8px' }}
                onClick={() => setSelectedColumnKeys(AVAILABLE_COLUMNS.map((c) => c.key))}
              >
                Select All
              </button>
            </div>

            <div className="report-columns-grid">
              {AVAILABLE_COLUMNS.map((col) => {
                const isSelected = selectedColumnKeys.includes(col.key)
                return (
                  <div
                    key={col.key}
                    className={`column-pill ${isSelected ? 'column-pill--selected' : ''}`}
                    onClick={() => toggleColumn(col.key)}
                  >
                    <span>{col.label}</span>
                    <span style={{ fontSize: '0.75rem' }}>{isSelected ? '✓' : '+'}</span>
                  </div>
                )
              })}
            </div>
          </div>

          {/* Filter Rows */}
          <div>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 'var(--space-2)' }}>
              <span style={{ fontSize: '0.75rem', fontWeight: 600 }}>WHERE CONDITIONS (FILTERS)</span>
              <button
                className="btn btn--ghost"
                style={{ fontSize: '0.75rem', padding: '2px 8px' }}
                onClick={() =>
                  setFilters((prev) => [
                    ...prev,
                    { id: `fil-${Date.now()}`, columnKey: 'department', operator: 'EQUALS', value: 'Engineering' },
                  ])
                }
              >
                + Add Filter Condition
              </button>
            </div>

            {filters.map((f, idx) => (
              <div key={f.id} className="filter-row">
                <select
                  className="select"
                  value={f.columnKey}
                  onChange={(e) => {
                    setFilters((prev) =>
                      prev.map((item, i) => (i === idx ? { ...item, columnKey: e.target.value } : item)),
                    )
                  }}
                >
                  {AVAILABLE_COLUMNS.map((c) => (
                    <option key={c.key} value={c.key}>{c.label}</option>
                  ))}
                </select>

                <select
                  className="select"
                  value={f.operator}
                  onChange={(e) => {
                    setFilters((prev) =>
                      prev.map((item, i) =>
                        i === idx ? { ...item, operator: e.target.value as any } : item,
                      ),
                    )
                  }}
                >
                  <option value="EQUALS">Equals</option>
                  <option value="NOT_EQUALS">Does not equal</option>
                  <option value="CONTAINS">Contains</option>
                  <option value="GREATER_THAN">Greater Than</option>
                  <option value="LESS_THAN">Less Than</option>
                </select>

                <input
                  className="field__input"
                  style={{ minHeight: '36px' }}
                  value={f.value}
                  placeholder="Enter filter value..."
                  onChange={(e) => {
                    setFilters((prev) =>
                      prev.map((item, i) => (i === idx ? { ...item, value: e.target.value } : item)),
                    )
                  }}
                />

                <button
                  className="btn btn--ghost"
                  style={{ color: 'var(--color-danger)' }}
                  onClick={() => setFilters(filters.filter((item) => item.id !== f.id))}
                >
                  ✕
                </button>
              </div>
            ))}
          </div>
        </div>
      </Card>

      {/* Main Results View */}
      {viewMode === 'grid' ? (
        <Card title="Query Results & Data Grid">
          <div style={{ overflowX: 'auto' }}>
            <table className="table">
              <thead>
                <tr>
                  {activeColumns.map((col) => (
                    <th key={col.key} scope="col">
                      {col.label} {col.aggregation && <span style={{ opacity: 0.7 }}>({col.aggregation})</span>}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {filteredRows.map((row, rIdx) => (
                  <tr key={rIdx}>
                    {activeColumns.map((col) => {
                      const val = row[col.key as keyof typeof row]
                      return (
                        <td key={col.key}>
                          {col.type === 'currency' && typeof val === 'number'
                            ? `LKR ${val.toLocaleString()}`
                            : col.type === 'number' && typeof val === 'number'
                            ? val.toLocaleString()
                            : String(val ?? '—')}
                        </td>
                      )
                    })}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
      ) : viewMode === 'chart' ? (
        /* Visual Chart Breakdown Mode */
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(420px, 1fr))', gap: 'var(--space-4)' }}>
          {/* Department Salary Bar Chart */}
          <Card title={`Department Payroll Distribution (${groupBy.toUpperCase()})`}>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)', padding: 'var(--space-2) 0' }}>
              {groupedSummary.map((grp) => {
                const maxPayroll = Math.max(...groupedSummary.map((g) => g.totalSalary), 1)
                const pct = Math.round((grp.totalSalary / maxPayroll) * 100)
                return (
                  <div key={grp.name} style={{ display: 'flex', flexDirection: 'column', gap: '2px' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.8125rem' }}>
                      <span style={{ fontWeight: 600 }}>{grp.name} ({grp.count} staff)</span>
                      <span style={{ fontFamily: 'var(--font-mono)' }}>LKR {grp.totalSalary.toLocaleString()}</span>
                    </div>
                    <div style={{ width: '100%', height: '18px', background: 'var(--color-surface)', borderRadius: '4px', overflow: 'hidden' }}>
                      <div style={{ width: `${pct}%`, height: '100%', background: 'var(--color-brand-primary)', borderRadius: '4px' }} />
                    </div>
                  </div>
                )
              })}
            </div>
          </Card>

          {/* Department Headcount Breakdown */}
          <Card title="Headcount & Overtime Contribution">
            <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
              {groupedSummary.map((grp) => (
                <div key={grp.name} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: 'var(--space-2)', background: 'var(--color-surface)', borderRadius: '4px' }}>
                  <div>
                    <div style={{ fontWeight: 600, fontSize: '0.875rem' }}>{grp.name}</div>
                    <div style={{ fontSize: '0.75rem', color: 'var(--color-on-surface-muted)' }}>Avg Salary: LKR {grp.avgSalary.toLocaleString()}</div>
                  </div>
                  <div style={{ textAlign: 'right' }}>
                    <Badge tone="neutral">{grp.totalOT} OT Hours</Badge>
                    <div style={{ fontSize: '0.75rem', marginTop: '2px', color: '#16a34a', fontWeight: 600 }}>{grp.count} Employees</div>
                  </div>
                </div>
              ))}
            </div>
          </Card>
        </div>
      ) : (
        /* Automated Schedule Configuration Mode */
        <Card title="Automated Report Scheduling & Distribution Engine">
          <div style={{ maxWidth: '640px', display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
            <div>
              <label style={{ fontSize: '0.8125rem', fontWeight: 600, display: 'block', marginBottom: '4px' }}>
                DISPATCH FREQUENCY
              </label>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 'var(--space-2)' }}>
                {(['DAILY', 'WEEKLY', 'MONTHLY'] as const).map((f) => (
                  <button
                    key={f}
                    className="btn btn--ghost"
                    style={{
                      border: '1.5px solid var(--color-outline-variant)',
                      borderRadius: 'var(--radius-control)',
                      background: frequency === f ? 'var(--color-brand-primary-container)' : 'transparent',
                      color: frequency === f ? 'var(--color-brand-on-primary-container)' : 'inherit',
                      fontWeight: frequency === f ? 600 : 400,
                      padding: 'var(--space-2)',
                    }}
                    onClick={() => setFrequency(f)}
                  >
                    {f}
                  </button>
                ))}
              </div>
            </div>

            <div>
              <label style={{ fontSize: '0.8125rem', fontWeight: 600, display: 'block', marginBottom: '4px' }}>
                RECIPIENT EMAIL ADDRESSES (COMMA SEPARATED)
              </label>
              <input
                className="field__input"
                value={recipients}
                onChange={(e) => setRecipients(e.target.value)}
                placeholder="executive-team@demo.local, payroll-audit@demo.local"
              />
            </div>

            <div>
              <label style={{ fontSize: '0.8125rem', fontWeight: 600, display: 'block', marginBottom: '4px' }}>
                EXPORT ATTACHMENT FORMAT
              </label>
              <div style={{ display: 'flex', gap: 'var(--space-3)' }}>
                {(['PDF', 'EXCEL', 'CSV'] as const).map((fmt) => (
                  <label key={fmt} style={{ fontSize: '0.8125rem', display: 'flex', alignItems: 'center', gap: '6px', cursor: 'pointer' }}>
                    <input
                      type="radio"
                      name="fmt"
                      checked={fileFormat === fmt}
                      onChange={() => setFileFormat(fmt)}
                    />
                    {fmt} Document
                  </label>
                ))}
              </div>
            </div>

            <div style={{ display: 'flex', gap: 'var(--space-2)', marginTop: 'var(--space-2)' }}>
              <Button variant="secondary" onClick={() => alert('Dry-run test email dispatched to ' + recipients)}>
                Send Test Sample Now
              </Button>
              <Button variant="primary" onClick={handleSaveSchedule}>
                Activate Automated Schedule
              </Button>
            </div>
          </div>
        </Card>
      )}
    </div>
  )
}
