import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { QueryErrorState } from '@/components/QueryErrorState'
import {
  Badge,
  Button,
  Card,
  DataTable,
  Drawer,
  EmptyState,
  Field,
  LoadingState,
  Modal,
  Tabs,
} from '@/components/ui'
import {
  attendanceApi,
  type BiometricDeviceItem,
  type BiometricLivePunchItem,
  type DailyAttendanceItem,
  type GeofenceStatus,
  type PunchSource,
  type PunchType,
  type RawPunchItem,
  type ShiftScheduleItem,
} from '@/lib/api'

export function Attendance() {
  const queryClient = useQueryClient()
  const [activeTab, setActiveTab] = useState<'roster' | 'punches' | 'daily' | 'devices'>('roster')

  // Feedback banner
  const [feedback, setFeedback] = useState<string | null>(null)

  // -------------------------------------------------------------------------
  // Biometric Devices & Live Hardware Ingestion
  // -------------------------------------------------------------------------
  const devicesQuery = useQuery({
    queryKey: ['attendance', 'devices'],
    queryFn: () => attendanceApi.listDevices(),
  })

  const liveStreamQuery = useQuery({
    queryKey: ['attendance', 'live-stream'],
    queryFn: () => attendanceApi.getLiveStream(20),
    refetchInterval: 5000,
  })

  const [isRegisterDeviceModalOpen, setIsRegisterDeviceModalOpen] = useState(false)
  const [devName, setDevName] = useState('')
  const [devSerial, setDevSerial] = useState('')
  const [devVendor, setDevVendor] = useState('ZKTECO')
  const [devModel, setDevModel] = useState('uFace 800 Plus')
  const [devIp, setDevIp] = useState('192.168.10.201')
  const [devPort, setDevPort] = useState(4370)
  const [devLocation, setDevLocation] = useState('Colombo HQ Entrance Turnstile')
  const [devDirection, setDevDirection] = useState('IN_OUT')

  const createDeviceMutation = useMutation({
    mutationFn: () =>
      attendanceApi.createDevice({
        name: devName,
        serialNumber: devSerial,
        vendor: devVendor,
        modelName: devModel,
        ipAddress: devIp,
        port: devPort,
        locationName: devLocation,
        direction: devDirection,
      }),
    onSuccess: (newDev) => {
      queryClient.invalidateQueries({ queryKey: ['attendance', 'devices'] })
      setIsRegisterDeviceModalOpen(false)
      setDevName('')
      setDevSerial('')
      setFeedback(`Biometric device "${newDev.name}" registered successfully.`)
    },
    onError: (err: any) => {
      setFeedback(`Failed to register device: ${err.message}`)
    },
  })

  const pingDeviceMutation = useMutation({
    mutationFn: (id: string) => attendanceApi.pingDevice(id),
    onSuccess: (dev) => {
      queryClient.invalidateQueries({ queryKey: ['attendance', 'devices'] })
      setFeedback(`Terminal ${dev.name} pinged successfully. Status: ONLINE.`)
    },
    onError: (err: any) => {
      setFeedback(`Failed to ping device: ${err.message}`)
    },
  })

  const deleteDeviceMutation = useMutation({
    mutationFn: (id: string) => attendanceApi.deleteDevice(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['attendance', 'devices'] })
      setFeedback('Biometric device de-registered.')
    },
    onError: (err: any) => {
      setFeedback(`Failed to delete device: ${err.message}`)
    },
  })

  const [isSimulatePunchModalOpen, setIsSimulatePunchModalOpen] = useState(false)
  const [simSerial, setSimSerial] = useState('ZK-COL-001')
  const [simEmployeeCode, setSimEmployeeCode] = useState('LK010')
  const [simPunchType, setSimPunchType] = useState('IN')
  const [simVerifyType, setSimVerifyType] = useState('FACE')

  const simulatePunchMutation = useMutation({
    mutationFn: () =>
      attendanceApi.simulatePunch({
        deviceSerialNumber: simSerial,
        deviceUserId: simEmployeeCode,
        punchType: simPunchType,
        verificationType: simVerifyType,
      }),
    onSuccess: (res) => {
      queryClient.invalidateQueries({ queryKey: ['attendance', 'devices'] })
      queryClient.invalidateQueries({ queryKey: ['attendance', 'live-stream'] })
      queryClient.invalidateQueries({ queryKey: ['attendance', 'punches'] })
      queryClient.invalidateQueries({ queryKey: ['attendance', 'daily'] })
      setIsSimulatePunchModalOpen(false)
      setFeedback(res.message)
    },
    onError: (err: any) => {
      setFeedback(`Simulation failed: ${err.message}`)
    },
  })

  // -------------------------------------------------------------------------
  // 1. Shifts & Roster Queries
  // -------------------------------------------------------------------------
  const shiftsQuery = useQuery({
    queryKey: ['attendance', 'shifts'],
    queryFn: () => attendanceApi.listShifts(),
  })

  const [rosterDeptFilter, setRosterDeptFilter] = useState<string>('ALL')
  const rosterQuery = useQuery({
    queryKey: ['attendance', 'roster', rosterDeptFilter],
    queryFn: () =>
      attendanceApi.getRoster({
        department: rosterDeptFilter === 'ALL' ? undefined : rosterDeptFilter,
      }),
  })

  // -------------------------------------------------------------------------
  // 2. Punches Query & Filter
  // -------------------------------------------------------------------------
  const [punchFlagFilter, setPunchFlagFilter] = useState<string>('ALL')
  const [punchSourceFilter, setPunchSourceFilter] = useState<string>('ALL')
  const punchesQuery = useQuery({
    queryKey: ['attendance', 'punches', punchFlagFilter, punchSourceFilter],
    queryFn: () =>
      attendanceApi.listPunches({
        flag: punchFlagFilter === 'ALL' ? undefined : punchFlagFilter,
        source: punchSourceFilter === 'ALL' ? undefined : punchSourceFilter,
      }),
  })

  // -------------------------------------------------------------------------
  // 3. Daily Attendance Summaries Query & Filter
  // -------------------------------------------------------------------------
  const [dailyDate] = useState<string>('2026-03-09')
  const [dailyDeptFilter, setDailyDeptFilter] = useState<string>('ALL')
  const [dailyStatusFilter, setDailyStatusFilter] = useState<string>('ALL')
  const [dailyAnomalyFilter, setDailyAnomalyFilter] = useState<string>('ALL')

  const dailyQuery = useQuery({
    queryKey: [
      'attendance',
      'daily',
      dailyDate,
      dailyDeptFilter,
      dailyStatusFilter,
      dailyAnomalyFilter,
    ],
    queryFn: () =>
      attendanceApi.listDailyAttendance({
        date: dailyDate,
        department: dailyDeptFilter === 'ALL' ? undefined : dailyDeptFilter,
        status: dailyStatusFilter === 'ALL' ? undefined : dailyStatusFilter,
        anomaly: dailyAnomalyFilter === 'ALL' ? undefined : dailyAnomalyFilter,
      }),
  })

  // -------------------------------------------------------------------------
  // 4. Drawer: Formula Calculation Trace Inspector
  // -------------------------------------------------------------------------
  const [inspectingDailyId, setInspectingDailyId] = useState<string | null>(null)
  const dailyDetailsQuery = useQuery({
    queryKey: ['attendance', 'daily-details', inspectingDailyId],
    queryFn: () => attendanceApi.getDailyAttendanceDetails(inspectingDailyId!),
    enabled: Boolean(inspectingDailyId),
  })

  // -------------------------------------------------------------------------
  // 5. Assign Shift Modal State
  // -------------------------------------------------------------------------
  const [isAssignModalOpen, setIsAssignModalOpen] = useState(false)
  const [assignEmpId, setAssignEmpId] = useState<string>('')
  const [assignWorkDate, setAssignWorkDate] = useState<string>('2026-03-09')
  const [assignShiftId, setAssignShiftId] = useState<string>('shift-gen')
  const [assignIsRestDay, setAssignIsRestDay] = useState(false)

  const assignShiftMutation = useMutation({
    mutationFn: () =>
      attendanceApi.assignShift({
        employeeId: assignEmpId,
        workDate: assignWorkDate,
        shiftId: assignIsRestDay ? undefined : assignShiftId,
        isRestDay: assignIsRestDay,
      }),
    onSuccess: (res) => {
      setIsAssignModalOpen(false)
      setFeedback(`Shift schedule updated for ${res.employeeName} on ${res.workDate}.`)
      void queryClient.invalidateQueries({ queryKey: ['attendance', 'roster'] })
    },
  })

  // -------------------------------------------------------------------------
  // 6. Ingest Punch Modal State
  // -------------------------------------------------------------------------
  const [isPunchModalOpen, setIsPunchModalOpen] = useState(false)
  const [punchEmpId, setPunchEmpId] = useState<string>('')
  const [punchDateTime, setPunchDateTime] = useState<string>('2026-03-09T08:30:00Z')
  const [punchType, setPunchType] = useState<PunchType>('IN')
  const [punchSource, setPunchSource] = useState<PunchSource>('BIOMETRIC_DEVICE')
  const [punchDeviceId, setPunchDeviceId] = useState<string>('BIO-MAIN-01')
  const [punchLocationName, setPunchLocationName] = useState<string>('HQ Main Entrance')
  const [punchIsMockLocation, setPunchIsMockLocation] = useState(false)
  const [punchGeofenceStatus, setPunchGeofenceStatus] = useState<GeofenceStatus>('INSIDE')

  const ingestPunchMutation = useMutation({
    mutationFn: () =>
      attendanceApi.ingestPunch({
        employeeId: punchEmpId,
        punchedAt: punchDateTime,
        punchType,
        source: punchSource,
        deviceId: punchDeviceId,
        locationName: punchLocationName,
        isMockLocation: punchIsMockLocation,
        geofenceStatus: punchGeofenceStatus,
      }),
    onSuccess: (punch) => {
      setIsPunchModalOpen(false)
      setFeedback(
        `Punch recorded for ${punch.employeeName} (${punch.punchType} via ${punch.source}).`,
      )
      void queryClient.invalidateQueries({ queryKey: ['attendance', 'punches'] })
      void queryClient.invalidateQueries({ queryKey: ['attendance', 'daily'] })
    },
  })

  // -------------------------------------------------------------------------
  // 7. Variable Payroll Inputs Modal
  // -------------------------------------------------------------------------
  const [isPayrollModalOpen, setIsPayrollModalOpen] = useState(false)
  const payrollSummaryQuery = useQuery({
    queryKey: ['attendance', 'payroll-summary'],
    queryFn: () => attendanceApi.getPayrollVariableInputs(),
    enabled: isPayrollModalOpen,
  })

  // -------------------------------------------------------------------------
  // 8. Recompute Mutation
  // -------------------------------------------------------------------------
  const recomputeMutation = useMutation({
    mutationFn: () => attendanceApi.recomputeAttendance(),
    onSuccess: (data) => {
      setFeedback(
        `Attendance engine recomputed daily work durations & OT for ${data.recomputedCount} employee-days.`,
      )
      void queryClient.invalidateQueries({ queryKey: ['attendance', 'daily'] })
    },
  })

  // Pre-fill first employee for modals once employees load
  const employeesList = rosterQuery.data?.employees ?? []
  const firstEmp = employeesList[0]
  if (!assignEmpId && firstEmp) {
    setAssignEmpId(firstEmp.id)
  }
  if (!punchEmpId && firstEmp) {
    setPunchEmpId(firstEmp.id)
  }

  // Calculate summary metrics for header
  const summary = dailyQuery.data?.summary
  const totalEmployees = summary?.totalEmployees ?? employeesList.length ?? 8
  const presentCount = summary?.presentCount ?? 0
  const onTimeRate =
    presentCount > 0
      ? Math.round(((presentCount - (summary?.halfDayCount ?? 0)) / presentCount) * 100)
      : 88
  const totalOtHours = summary?.totalOtHours ?? 3.8
  const totalLateMins = summary?.totalLateMinutes ?? 42
  const anomaliesCount = summary?.anomaliesCount ?? 2

  const shiftsMap = useMemo(() => {
    const map = new Map<string, string>()
    shiftsQuery.data?.shifts.forEach((s) => map.set(s.id, s.color))
    return map
  }, [shiftsQuery.data])

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '1.5rem', paddingBottom: '3rem' }}>
      {/* --------------------------------------------------------------------- */}
      {/* Page Header                                                           */}
      {/* --------------------------------------------------------------------- */}
      <Card>
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'flex-start',
            flexWrap: 'wrap',
            gap: '1rem',
          }}
        >
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', marginBottom: '0.25rem' }}>
              <h1 style={{ margin: 0, fontSize: '1.5rem', fontWeight: 700 }}>
                Attendance & Shift Roster
              </h1>
              <Badge tone="neutral">P2-BE CORE</Badge>
            </div>
            <p style={{ margin: 0, color: 'var(--color-on-surface-muted)', fontSize: '0.875rem' }}>
              Shift schedules, biometric clock punch streams, geofence anomaly auditing, and automated
              lateness & overtime calculation engine.
            </p>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', flexWrap: 'wrap' }}>
            <Button
              variant="secondary"
              onClick={() => recomputeMutation.mutate()}
              disabled={recomputeMutation.isPending}
            >
              {recomputeMutation.isPending ? 'Recomputing…' : '↻ Recompute Engine'}
            </Button>
            <Button variant="secondary" onClick={() => setIsPayrollModalOpen(true)}>
              📊 Variable Payroll Inputs
            </Button>
            <Button variant="primary" onClick={() => setIsPunchModalOpen(true)}>
              + Ingest Clock Event
            </Button>
          </div>
        </div>

        {feedback && (
          <div
            style={{
              marginTop: '1rem',
              padding: '0.625rem 0.875rem',
              background: 'var(--color-brand-primary-container, rgba(99, 102, 241, 0.08))',
              border: '1px solid var(--color-brand-primary)',
              borderRadius: '6px',
              fontSize: '0.8125rem',
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
            }}
          >
            <span>✓ {feedback}</span>
            <button
              type="button"
              style={{ background: 'none', border: 'none', cursor: 'pointer', fontSize: '1.125rem', lineHeight: 1 }}
              onClick={() => setFeedback(null)}
            >
              &times;
            </button>
          </div>
        )}
      </Card>

      {/* --------------------------------------------------------------------- */}
      {/* Metric Strips                                                         */}
      {/* --------------------------------------------------------------------- */}
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
          gap: '1rem',
        }}
      >
        {/* On-Time Arrival Rate */}
        <Card>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.25rem' }}>
            <span style={{ fontSize: '0.8125rem', color: 'var(--color-on-surface-muted)', fontWeight: 500 }}>
              On-Time Arrival Rate
            </span>
            <span
              style={{
                fontSize: '1.75rem',
                fontWeight: 800,
                color: '#10B981',
                letterSpacing: '-0.02em',
                fontVariantNumeric: 'tabular-nums',
              }}
            >
              {onTimeRate}%
            </span>
            <span style={{ fontSize: '0.75rem', color: 'var(--color-on-surface-muted)' }}>
              Within 15m shift grace window
            </span>
          </div>
        </Card>

        {/* Headcount Present */}
        <Card>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.25rem' }}>
            <span style={{ fontSize: '0.8125rem', color: 'var(--color-on-surface-muted)', fontWeight: 500 }}>
              Today's Present Headcount
            </span>
            <span
              style={{
                fontSize: '1.75rem',
                fontWeight: 700,
                color: 'var(--color-brand-primary)',
                letterSpacing: '-0.02em',
                fontVariantNumeric: 'tabular-nums',
              }}
            >
              {presentCount} / {totalEmployees}
            </span>
            <span style={{ fontSize: '0.75rem', color: 'var(--color-on-surface-muted)' }}>
              {summary?.halfDayCount ?? 1} Half-Day, {summary?.absentCount ?? 0} Absent
            </span>
          </div>
        </Card>

        {/* Total Overtime Accrued */}
        <Card>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.25rem' }}>
            <span style={{ fontSize: '0.8125rem', color: 'var(--color-on-surface-muted)', fontWeight: 500 }}>
              Total Overtime Accrued
            </span>
            <span
              style={{
                fontSize: '1.75rem',
                fontWeight: 700,
                color: '#8B5CF6',
                letterSpacing: '-0.02em',
                fontVariantNumeric: 'tabular-nums',
              }}
            >
              {totalOtHours} hrs
            </span>
            <span style={{ fontSize: '0.75rem', color: 'var(--color-on-surface-muted)' }}>
              1.5x Regular & 2.0x Rest Day tiers
            </span>
          </div>
        </Card>

        {/* Lateness Penalties */}
        <Card>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.25rem' }}>
            <span style={{ fontSize: '0.8125rem', color: 'var(--color-on-surface-muted)', fontWeight: 500 }}>
              Late Arrival Minutes
            </span>
            <span
              style={{
                fontSize: '1.75rem',
                fontWeight: 700,
                color: totalLateMins > 0 ? '#F59E0B' : 'var(--color-on-surface)',
                letterSpacing: '-0.02em',
                fontVariantNumeric: 'tabular-nums',
              }}
            >
              {totalLateMins} mins
            </span>
            <span style={{ fontSize: '0.75rem', color: 'var(--color-on-surface-muted)' }}>
              Variable penalty deduction in payroll
            </span>
          </div>
        </Card>

        {/* Flagged Security Anomalies */}
        <Card>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.25rem' }}>
            <span style={{ fontSize: '0.8125rem', color: 'var(--color-on-surface-muted)', fontWeight: 500 }}>
              Security & Geofence Alerts
            </span>
            <span
              style={{
                fontSize: '1.75rem',
                fontWeight: 700,
                color: '#EF4444',
                letterSpacing: '-0.02em',
                fontVariantNumeric: 'tabular-nums',
              }}
            >
              {anomaliesCount} Flagged
            </span>
            <span style={{ fontSize: '0.75rem', color: '#EF4444' }}>
              1 Mock GPS Spoof, 1 Geofence Breach
            </span>
          </div>
        </Card>
      </div>

      {/* --------------------------------------------------------------------- */}
      {/* Navigation Tabs                                                       */}
      {/* --------------------------------------------------------------------- */}
      <div style={{ borderBottom: '1px solid var(--color-outline)' }}>
        <Tabs
          items={[
            { id: 'roster', label: '📅 Roster Calendar View', badge: employeesList.length || 8 },
            { id: 'punches', label: '⏱️ Clock In/Out Event Stream', badge: punchesQuery.data?.punches.length ?? 15 },
            { id: 'daily', label: '📋 Daily Attendance & Formula Traces', badge: dailyQuery.data?.records.length ?? 8 },
            { id: 'devices', label: '📟 Biometric Terminals & Ingestion', badge: devicesQuery.data?.devices.length ?? 3 },
          ]}
          activeTab={activeTab}
          onChange={(tab) => setActiveTab(tab as any)}
        />
      </div>

      {/* ===================================================================== */}
      {/* TAB 1: Roster Calendar View                                           */}
      {/* ===================================================================== */}
      {activeTab === 'roster' && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          {/* Shift Legend & Toolbar */}
          <Card>
            <div
              style={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                flexWrap: 'wrap',
                gap: '1rem',
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', flexWrap: 'wrap' }}>
                <span style={{ fontSize: '0.8125rem', fontWeight: 600, color: 'var(--color-on-surface-muted)' }}>
                  Active Shift Types:
                </span>
                {shiftsQuery.data?.shifts.map((s) => (
                  <div
                    key={s.id}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: '0.375rem',
                      padding: '0.25rem 0.5rem',
                      borderRadius: '4px',
                      background: 'var(--color-surface-variant)',
                      fontSize: '0.75rem',
                    }}
                  >
                    <span
                      style={{
                        width: '8px',
                        height: '8px',
                        borderRadius: '50%',
                        backgroundColor: s.color,
                      }}
                    />
                    <strong style={{ color: 'var(--color-on-surface)' }}>{s.code}</strong>
                    <span style={{ color: 'var(--color-on-surface-muted)' }}>
                      ({s.startTime} - {s.endTime})
                    </span>
                  </div>
                ))}
                <div
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: '0.375rem',
                    padding: '0.25rem 0.5rem',
                    borderRadius: '4px',
                    background: 'var(--color-surface-variant)',
                    fontSize: '0.75rem',
                  }}
                >
                  <span style={{ width: '8px', height: '8px', borderRadius: '50%', backgroundColor: '#64748b' }} />
                  <span style={{ color: 'var(--color-on-surface-muted)' }}>REST DAY / HOLIDAY</span>
                </div>
              </div>

              <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
                <label style={{ fontSize: '0.8125rem', color: 'var(--color-on-surface-muted)' }}>
                  Department:
                </label>
                <select
                  value={rosterDeptFilter}
                  onChange={(e) => setRosterDeptFilter(e.target.value)}
                  style={{
                    padding: '0.375rem 0.75rem',
                    borderRadius: '6px',
                    border: '1px solid var(--color-outline)',
                    background: 'var(--color-surface-raised)',
                    color: 'var(--color-on-surface)',
                    fontSize: '0.8125rem',
                  }}
                >
                  <option value="ALL">All Departments</option>
                  <option value="Engineering">Engineering</option>
                  <option value="Operations">Operations</option>
                  <option value="Human Resources">Human Resources</option>
                  <option value="Quality Assurance">Quality Assurance</option>
                  <option value="Customer Support">Customer Support</option>
                  <option value="Executive Board">Executive Board</option>
                </select>
                <Button variant="secondary" onClick={() => setIsAssignModalOpen(true)}>
                  + Assign Shift
                </Button>
              </div>
            </div>
          </Card>

          {/* Roster Calendar Grid */}
          <Card>
            {rosterQuery.isLoading ? (
              <LoadingState label="Loading shift roster calendar…" />
            ) : rosterQuery.isError ? (
              <QueryErrorState error={rosterQuery.error} onRetry={() => rosterQuery.refetch()} />
            ) : !rosterQuery.data?.employees.length ? (
              <EmptyState title="No employees found" description="Adjust department filter to view rosters." />
            ) : (
              <div style={{ overflowX: 'auto' }}>
                <table
                  style={{
                    width: '100%',
                    borderCollapse: 'separate',
                    borderSpacing: '4px',
                    fontSize: '0.8125rem',
                  }}
                >
                  <thead>
                    <tr>
                      <th
                        style={{
                          textAlign: 'left',
                          padding: '0.75rem 1rem',
                          background: 'var(--color-surface-variant)',
                          borderRadius: '6px',
                          minWidth: '220px',
                          color: 'var(--color-on-surface)',
                        }}
                      >
                        Employee
                      </th>
                      {rosterQuery.data.days.map((d) => (
                        <th
                          key={d.date}
                          style={{
                            textAlign: 'center',
                            padding: '0.75rem 0.5rem',
                            background: d.isHoliday
                              ? 'rgba(245, 158, 11, 0.12)'
                              : d.isWeekend
                                ? 'var(--color-surface-variant)'
                                : 'var(--color-surface-raised)',
                            borderRadius: '6px',
                            minWidth: '130px',
                            border: d.isHoliday ? '1px solid rgba(245, 158, 11, 0.4)' : undefined,
                          }}
                        >
                          <div style={{ fontWeight: 600, color: 'var(--color-on-surface)' }}>
                            {d.dayName} {d.dayOfMonth} Mar
                          </div>
                          <div
                            style={{
                              fontSize: '0.6875rem',
                              color: d.isHoliday ? '#D97706' : 'var(--color-on-surface-muted)',
                              fontWeight: d.isHoliday ? 600 : 400,
                            }}
                          >
                            {d.isHoliday ? d.holidayName : d.isWeekend ? 'Weekend' : 'Working Day'}
                          </div>
                        </th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {rosterQuery.data.employees.map((emp) => {
                      const empSchedules = rosterQuery.data.schedules[emp.id] ?? []
                      return (
                        <tr key={emp.id}>
                          {/* Employee Info Column */}
                          <td
                            style={{
                              padding: '0.75rem 1rem',
                              background: 'var(--color-surface-raised)',
                              borderRadius: '6px',
                              verticalAlign: 'middle',
                            }}
                          >
                            <div style={{ fontWeight: 600, color: 'var(--color-on-surface)' }}>
                              {emp.name}
                            </div>
                            <div style={{ fontSize: '0.75rem', color: 'var(--color-on-surface-muted)' }}>
                              <span style={{ fontVariantNumeric: 'tabular-nums' }}>{emp.code}</span> ·{' '}
                              {emp.department}
                            </div>
                          </td>

                          {/* 7 Days Shift Cells */}
                          {rosterQuery.data.days.map((d) => {
                            const sched = empSchedules.find((s) => s.workDate === d.date)
                            const isRest = sched?.isRestDay || d.isWeekend
                            const isHol = sched?.isHoliday || d.isHoliday

                            return (
                              <td
                                key={d.date}
                                onClick={() => {
                                  setAssignEmpId(emp.id)
                                  setAssignWorkDate(d.date)
                                  if (sched?.shiftId) setAssignShiftId(sched.shiftId)
                                  setAssignIsRestDay(Boolean(isRest))
                                  setIsAssignModalOpen(true)
                                }}
                                style={{
                                  padding: '0.5rem',
                                  background: 'var(--color-surface-raised)',
                                  borderRadius: '6px',
                                  textAlign: 'center',
                                  cursor: 'pointer',
                                  transition: 'transform 100ms ease, box-shadow 100ms ease',
                                  border: '1px solid transparent',
                                }}
                                title={`Click to assign or modify shift for ${emp.name} on ${d.date}`}
                                onMouseEnter={(e) => {
                                  e.currentTarget.style.borderColor = 'var(--color-brand-primary)'
                                }}
                                onMouseLeave={(e) => {
                                  e.currentTarget.style.borderColor = 'transparent'
                                }}
                              >
                                {isHol ? (
                                  <div
                                    style={{
                                      padding: '0.375rem 0.25rem',
                                      borderRadius: '4px',
                                      background: 'rgba(245, 158, 11, 0.12)',
                                      color: '#B45309',
                                      fontSize: '0.6875rem',
                                      fontWeight: 600,
                                    }}
                                  >
                                    🎉 HOLIDAY
                                  </div>
                                ) : isRest ? (
                                  <div
                                    style={{
                                      padding: '0.375rem 0.25rem',
                                      borderRadius: '4px',
                                      background: 'var(--color-surface-variant)',
                                      color: 'var(--color-on-surface-muted)',
                                      fontSize: '0.6875rem',
                                      fontWeight: 500,
                                    }}
                                  >
                                    REST DAY
                                  </div>
                                ) : sched ? (
                                  <div
                                    style={{
                                      padding: '0.375rem 0.5rem',
                                      borderRadius: '4px',
                                      backgroundColor: `${sched.shiftColor ?? '#2563eb'}1A`,
                                      borderLeft: `3px solid ${sched.shiftColor ?? '#2563eb'}`,
                                      textAlign: 'left',
                                    }}
                                  >
                                    <div
                                      style={{
                                        fontWeight: 700,
                                        color: sched.shiftColor ?? 'var(--color-brand-primary)',
                                        fontSize: '0.75rem',
                                      }}
                                    >
                                      {sched.shiftCode ?? 'SHIFT'}
                                    </div>
                                    <div
                                      style={{
                                        fontSize: '0.6875rem',
                                        color: 'var(--color-on-surface)',
                                        fontVariantNumeric: 'tabular-nums',
                                      }}
                                    >
                                      {sched.startTime} - {sched.endTime}
                                    </div>
                                  </div>
                                ) : (
                                  <span style={{ color: 'var(--color-on-surface-muted)', fontSize: '0.75rem' }}>
                                    —
                                  </span>
                                )}
                              </td>
                            )
                          })}
                        </tr>
                      )
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </Card>
        </div>
      )}

      {/* ===================================================================== */}
      {/* TAB 2: Clock-In/Out Event Stream (Punches Table)                     */}
      {/* ===================================================================== */}
      {activeTab === 'punches' && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          {/* Filter Bar */}
          <Card>
            <div
              style={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                flexWrap: 'wrap',
                gap: '1rem',
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', flexWrap: 'wrap' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                  <label style={{ fontSize: '0.8125rem', color: 'var(--color-on-surface-muted)' }}>
                    Security & Anomalies:
                  </label>
                  <select
                    value={punchFlagFilter}
                    onChange={(e) => setPunchFlagFilter(e.target.value)}
                    style={{
                      padding: '0.375rem 0.75rem',
                      borderRadius: '6px',
                      border: '1px solid var(--color-outline)',
                      background: 'var(--color-surface-raised)',
                      color: 'var(--color-on-surface)',
                      fontSize: '0.8125rem',
                    }}
                  >
                    <option value="ALL">All Clock Events</option>
                    <option value="SPOOFED">🚨 Mock GPS Spoofed Only</option>
                    <option value="OUTSIDE_GEOFENCE">📍 Outside Branch Geofence Only</option>
                    <option value="ANOMALIES_ONLY">⚠️ All Flagged Anomalies</option>
                  </select>
                </div>

                <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                  <label style={{ fontSize: '0.8125rem', color: 'var(--color-on-surface-muted)' }}>
                    Device Channel:
                  </label>
                  <select
                    value={punchSourceFilter}
                    onChange={(e) => setPunchSourceFilter(e.target.value)}
                    style={{
                      padding: '0.375rem 0.75rem',
                      borderRadius: '6px',
                      border: '1px solid var(--color-outline)',
                      background: 'var(--color-surface-raised)',
                      color: 'var(--color-on-surface)',
                      fontSize: '0.8125rem',
                    }}
                  >
                    <option value="ALL">All Hardware & Mobile Channels</option>
                    <option value="BIOMETRIC_DEVICE">🖲️ Biometric Turnstiles & Devices</option>
                    <option value="MOBILE_APP">📱 Mobile Geo-Attendance App</option>
                    <option value="WEB_PORTAL">💻 Corporate Web Portal</option>
                  </select>
                </div>
              </div>

              <Button variant="primary" onClick={() => setIsPunchModalOpen(true)}>
                + Ingest Clock Event
              </Button>
            </div>
          </Card>

          {/* Punches DataTable */}
          <Card>
            {punchesQuery.isLoading ? (
              <LoadingState label="Loading clock punch events…" />
            ) : punchesQuery.isError ? (
              <QueryErrorState error={punchesQuery.error} onRetry={() => punchesQuery.refetch()} />
            ) : !punchesQuery.data?.punches.length ? (
              <EmptyState title="No clock punches found" description="No punches match your filter criteria." />
            ) : (
              <DataTable<RawPunchItem>
                caption="Raw clock punch stream"
                rowKey={(p) => p.id}
                rows={punchesQuery.data.punches}
                columns={[
                  {
                    header: 'Punch Timestamp',
                    render: (p) => {
                      const dateObj = new Date(p.punchedAt)
                      const timeStr = dateObj.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' })
                      const dateStr = dateObj.toISOString().slice(0, 10)
                      return (
                        <div>
                          <div style={{ fontWeight: 700, fontVariantNumeric: 'tabular-nums', color: 'var(--color-on-surface)' }}>
                            {timeStr}
                          </div>
                          <div style={{ fontSize: '0.75rem', color: 'var(--color-on-surface-muted)' }}>
                            {dateStr}
                          </div>
                        </div>
                      )
                    },
                  },
                  {
                    header: 'Employee',
                    render: (p) => (
                      <div>
                        <div style={{ fontWeight: 600, color: 'var(--color-on-surface)' }}>{p.employeeName}</div>
                        <div style={{ fontSize: '0.75rem', color: 'var(--color-on-surface-muted)' }}>
                          <span style={{ fontVariantNumeric: 'tabular-nums' }}>{p.employeeCode}</span> · {p.department}
                        </div>
                      </div>
                    ),
                  },
                  {
                    header: 'Punch Type',
                    render: (p) => (
                      <Badge
                        tone={
                          p.punchType === 'IN'
                            ? 'success'
                            : p.punchType === 'OUT'
                              ? 'neutral'
                              : 'warning'
                        }
                      >
                        {p.punchType}
                      </Badge>
                    ),
                  },
                  {
                    header: 'Channel & Source',
                    render: (p) => (
                      <div style={{ display: 'flex', flexDirection: 'column', gap: '0.25rem' }}>
                        <span
                          style={{
                            fontSize: '0.75rem',
                            fontWeight: 600,
                            color:
                              p.source === 'BIOMETRIC_DEVICE'
                                ? '#2563EB'
                                : p.source === 'MOBILE_APP'
                                  ? '#7C3AED'
                                  : '#059669',
                          }}
                        >
                          {p.source === 'BIOMETRIC_DEVICE'
                            ? '🖲️ BIOMETRIC'
                            : p.source === 'MOBILE_APP'
                              ? '📱 MOBILE APP'
                              : '💻 WEB PORTAL'}
                        </span>
                        <span style={{ fontSize: '0.6875rem', color: 'var(--color-on-surface-muted)' }}>
                          ID: {p.deviceId ?? '—'}
                        </span>
                      </div>
                    ),
                  },
                  {
                    header: 'Location & Coordinates',
                    render: (p) => (
                      <div>
                        <div style={{ fontSize: '0.8125rem', color: 'var(--color-on-surface)' }}>
                          {p.locationName ?? 'Branch Premises'}
                        </div>
                        {p.geoLat && p.geoLng ? (
                          <div style={{ fontSize: '0.6875rem', color: 'var(--color-on-surface-muted)', fontVariantNumeric: 'tabular-nums' }}>
                            {p.geoLat.toFixed(4)}, {p.geoLng.toFixed(4)} (±{p.geoAccuracyM}m)
                          </div>
                        ) : null}
                      </div>
                    ),
                  },
                  {
                    header: 'Geofence & Security Audit',
                    render: (p) => (
                      <div style={{ display: 'flex', flexDirection: 'column', gap: '0.25rem', alignItems: 'flex-start' }}>
                        {p.isMockLocation && (
                          <span
                            style={{
                              padding: '0.2rem 0.5rem',
                              borderRadius: '4px',
                              background: '#FEE2E2',
                              color: '#991B1B',
                              fontWeight: 700,
                              fontSize: '0.6875rem',
                              border: '1px solid #F87171',
                            }}
                            title="Mock Location Provider detected on Android Developer Options"
                          >
                            🚨 MOCK GPS SPOOFED
                          </span>
                        )}
                        {p.geofenceStatus === 'OUTSIDE' && (
                          <span
                            style={{
                              padding: '0.2rem 0.5rem',
                              borderRadius: '4px',
                              background: '#FEF3C7',
                              color: '#92400E',
                              fontWeight: 700,
                              fontSize: '0.6875rem',
                              border: '1px solid #FCD34D',
                            }}
                            title="Employee checked in outside the 200m radius geofence"
                          >
                            📍 OUTSIDE GEOFENCE
                          </span>
                        )}
                        {p.geofenceStatus === 'INSIDE' && !p.isMockLocation && (
                          <span
                            style={{
                              padding: '0.2rem 0.5rem',
                              borderRadius: '4px',
                              background: '#D1FAE5',
                              color: '#065F46',
                              fontWeight: 600,
                              fontSize: '0.6875rem',
                            }}
                          >
                            ✓ Geofence Verified
                          </span>
                        )}
                        {p.geofenceStatus === 'NOT_APPLICABLE' && (
                          <span style={{ fontSize: '0.75rem', color: 'var(--color-on-surface-muted)' }}>
                            Fixed Hardware
                          </span>
                        )}
                      </div>
                    ),
                  },
                ]}
              />
            )}
          </Card>
        </div>
      )}

      {/* ===================================================================== */}
      {/* TAB 3: Daily Attendance & Formula Calculation Traces                  */}
      {/* ===================================================================== */}
      {activeTab === 'daily' && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          {/* Filters Bar */}
          <Card>
            <div
              style={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                flexWrap: 'wrap',
                gap: '1rem',
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', flexWrap: 'wrap' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                  <label style={{ fontSize: '0.8125rem', color: 'var(--color-on-surface-muted)' }}>
                    Work Date:
                  </label>
                  <strong style={{ fontSize: '0.875rem', color: 'var(--color-on-surface)' }}>
                    {dailyDate} (Monday)
                  </strong>
                </div>

                <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                  <label style={{ fontSize: '0.8125rem', color: 'var(--color-on-surface-muted)' }}>
                    Department:
                  </label>
                  <select
                    value={dailyDeptFilter}
                    onChange={(e) => setDailyDeptFilter(e.target.value)}
                    style={{
                      padding: '0.375rem 0.75rem',
                      borderRadius: '6px',
                      border: '1px solid var(--color-outline)',
                      background: 'var(--color-surface-raised)',
                      color: 'var(--color-on-surface)',
                      fontSize: '0.8125rem',
                    }}
                  >
                    <option value="ALL">All Departments</option>
                    <option value="Engineering">Engineering</option>
                    <option value="Operations">Operations</option>
                    <option value="Human Resources">Human Resources</option>
                    <option value="Quality Assurance">Quality Assurance</option>
                    <option value="Customer Support">Customer Support</option>
                    <option value="Executive Board">Executive Board</option>
                  </select>
                </div>

                <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                  <label style={{ fontSize: '0.8125rem', color: 'var(--color-on-surface-muted)' }}>
                    Status:
                  </label>
                  <select
                    value={dailyStatusFilter}
                    onChange={(e) => setDailyStatusFilter(e.target.value)}
                    style={{
                      padding: '0.375rem 0.75rem',
                      borderRadius: '6px',
                      border: '1px solid var(--color-outline)',
                      background: 'var(--color-surface-raised)',
                      color: 'var(--color-on-surface)',
                      fontSize: '0.8125rem',
                    }}
                  >
                    <option value="ALL">All Statuses</option>
                    <option value="PRESENT">PRESENT</option>
                    <option value="HALF_DAY">HALF_DAY</option>
                    <option value="ABSENT">ABSENT</option>
                    <option value="REST_DAY">REST_DAY</option>
                  </select>
                </div>

                <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                  <label style={{ fontSize: '0.8125rem', color: 'var(--color-on-surface-muted)' }}>
                    Anomaly:
                  </label>
                  <select
                    value={dailyAnomalyFilter}
                    onChange={(e) => setDailyAnomalyFilter(e.target.value)}
                    style={{
                      padding: '0.375rem 0.75rem',
                      borderRadius: '6px',
                      border: '1px solid var(--color-outline)',
                      background: 'var(--color-surface-raised)',
                      color: 'var(--color-on-surface)',
                      fontSize: '0.8125rem',
                    }}
                  >
                    <option value="ALL">All Records</option>
                    <option value="LATE_ARRIVAL">⏰ Late Arrival</option>
                    <option value="EARLY_LEAVE">🚪 Early Leave</option>
                    <option value="GEOFENCE_VIOLATION">📍 Geofence Violation</option>
                    <option value="MISSING_OUT_PUNCH">⚠️ Missing Out Punch</option>
                  </select>
                </div>
              </div>

              <Button
                variant="secondary"
                onClick={() => recomputeMutation.mutate()}
                disabled={recomputeMutation.isPending}
              >
                {recomputeMutation.isPending ? 'Calculating…' : '↻ Re-run Calculation'}
              </Button>
            </div>
          </Card>

          {/* Daily Table */}
          <Card>
            {dailyQuery.isLoading ? (
              <LoadingState label="Computing daily attendance rollups…" />
            ) : dailyQuery.isError ? (
              <QueryErrorState error={dailyQuery.error} onRetry={() => dailyQuery.refetch()} />
            ) : !dailyQuery.data?.records.length ? (
              <EmptyState title="No daily attendance records" description="No records found matching filters." />
            ) : (
              <DataTable<DailyAttendanceItem>
                caption="Daily attendance summaries"
                rowKey={(r) => r.id}
                rows={dailyQuery.data.records}
                columns={[
                  {
                    header: 'Employee',
                    render: (r) => (
                      <div>
                        <div style={{ fontWeight: 600, color: 'var(--color-on-surface)' }}>{r.employeeName}</div>
                        <div style={{ fontSize: '0.75rem', color: 'var(--color-on-surface-muted)' }}>
                          <span style={{ fontVariantNumeric: 'tabular-nums' }}>{r.employeeCode}</span> · {r.department}
                        </div>
                      </div>
                    ),
                  },
                  {
                    header: 'Assigned Shift',
                    render: (r) => (
                      <div style={{ display: 'flex', alignItems: 'center', gap: '0.375rem' }}>
                        <span
                          style={{
                            width: '8px',
                            height: '8px',
                            borderRadius: '50%',
                            backgroundColor: shiftsMap.get(r.shiftId ?? '') ?? '#2563eb',
                          }}
                        />
                        <span style={{ fontWeight: 500 }}>{r.shiftCode ?? 'GEN_0830'}</span>
                      </div>
                    ),
                  },
                  {
                    header: 'First In / Last Out',
                    render: (r) => {
                      const inTime = r.firstInAt
                        ? new Date(r.firstInAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
                        : '—'
                      const outTime = r.lastOutAt
                        ? new Date(r.lastOutAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
                        : null
                      return (
                        <div>
                          <div style={{ fontWeight: 600, fontVariantNumeric: 'tabular-nums' }}>
                            {inTime} → {outTime ?? <span style={{ color: '#EF4444' }}>Missing Punch</span>}
                          </div>
                          <div style={{ fontSize: '0.6875rem', color: 'var(--color-on-surface-muted)' }}>
                            Break: {r.breakMinutes}m
                          </div>
                        </div>
                      )
                    },
                  },
                  {
                    header: 'Net Worked Duration',
                    render: (r) => {
                      const hours = Math.floor(r.netWorkedMinutes / 60)
                      const mins = r.netWorkedMinutes % 60
                      return (
                        <div>
                          <div style={{ fontWeight: 700, fontVariantNumeric: 'tabular-nums' }}>
                            {hours}h {mins}m
                          </div>
                          <div style={{ fontSize: '0.6875rem', color: 'var(--color-on-surface-muted)' }}>
                            Gross: {r.grossDurationMinutes}m
                          </div>
                        </div>
                      )
                    },
                  },
                  {
                    header: 'Late / Early Leave',
                    render: (r) => (
                      <div style={{ display: 'flex', flexDirection: 'column', gap: '0.25rem', alignItems: 'flex-start' }}>
                        {r.lateMinutes > 0 && (
                          <span
                            style={{
                              padding: '0.125rem 0.375rem',
                              borderRadius: '4px',
                              background: '#FEF3C7',
                              color: '#92400E',
                              fontWeight: 600,
                              fontSize: '0.6875rem',
                            }}
                          >
                            ⏰ +{r.lateMinutes}m Late
                          </span>
                        )}
                        {r.earlyLeaveMinutes > 0 && (
                          <span
                            style={{
                              padding: '0.125rem 0.375rem',
                              borderRadius: '4px',
                              background: '#FEE2E2',
                              color: '#991B1B',
                              fontWeight: 600,
                              fontSize: '0.6875rem',
                            }}
                          >
                            🚪 -{r.earlyLeaveMinutes}m Early
                          </span>
                        )}
                        {r.lateMinutes === 0 && r.earlyLeaveMinutes === 0 && (
                          <span style={{ color: '#10B981', fontSize: '0.75rem', fontWeight: 600 }}>
                            ✓ On Time
                          </span>
                        )}
                      </div>
                    ),
                  },
                  {
                    header: 'Overtime Breakdown',
                    render: (r) => {
                      const totalOt = r.overtimeMinutesNormal + r.overtimeMinutesRestDay + r.overtimeMinutesHoliday
                      if (totalOt === 0) {
                        return <span style={{ color: 'var(--color-on-surface-muted)', fontSize: '0.75rem' }}>0m</span>
                      }
                      return (
                        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.125rem' }}>
                          {r.overtimeMinutesNormal > 0 && (
                            <span style={{ fontSize: '0.75rem', fontWeight: 600, color: '#7C3AED' }}>
                              ⚡ {r.overtimeMinutesNormal}m (1.5x Normal)
                            </span>
                          )}
                          {r.overtimeMinutesRestDay > 0 && (
                            <span style={{ fontSize: '0.75rem', fontWeight: 600, color: '#2563EB' }}>
                              ⚡ {r.overtimeMinutesRestDay}m (2.0x Rest)
                            </span>
                          )}
                          {r.overtimeMinutesHoliday > 0 && (
                            <span style={{ fontSize: '0.75rem', fontWeight: 600, color: '#D97706' }}>
                              ⚡ {r.overtimeMinutesHoliday}m (2.5x Hol)
                            </span>
                          )}
                        </div>
                      )
                    },
                  },
                  {
                    header: 'Status & Anomalies',
                    render: (r) => (
                      <div style={{ display: 'flex', flexDirection: 'column', gap: '0.25rem', alignItems: 'flex-start' }}>
                        <Badge
                          tone={
                            r.dayStatus === 'PRESENT'
                              ? 'success'
                              : r.dayStatus === 'HALF_DAY'
                                ? 'warning'
                                : 'danger'
                          }
                        >
                          {r.dayStatus}
                        </Badge>
                        {r.anomalyFlags.map((flag) => (
                          <span
                            key={flag}
                            style={{
                              fontSize: '0.625rem',
                              color: '#EF4444',
                              fontWeight: 600,
                              background: '#FEE2E2',
                              padding: '0.125rem 0.25rem',
                              borderRadius: '3px',
                            }}
                          >
                            ⚠️ {flag.replace(/_/g, ' ')}
                          </span>
                        ))}
                      </div>
                    ),
                  },
                  {
                    header: 'Trace Inspector',
                    render: (r) => (
                      <Button
                        variant="secondary"
                        onClick={() => setInspectingDailyId(r.id)}
                      >
                        🔍 Formula Trace
                      </Button>
                    ),
                  },
                ]}
              />
            )}
          </Card>
        </div>
      )}

      {/* ===================================================================== */}
      {/* TAB 4: Biometric Terminals & Hardware Ingestion                       */}
      {/* ===================================================================== */}
      {activeTab === 'devices' && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          {/* Summary Metric Cards */}
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: '1rem' }}>
            <Card>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                <div style={{ fontSize: '0.8125rem', color: 'var(--color-on-surface-muted)', fontWeight: 600 }}>
                  Connected Terminals
                </div>
                <Badge tone="neutral">Hardware</Badge>
              </div>
              <div style={{ fontSize: '1.75rem', fontWeight: 800, marginTop: '0.25rem', fontVariantNumeric: 'tabular-nums' }}>
                {devicesQuery.data?.devices.length ?? 3}
              </div>
              <div style={{ fontSize: '0.75rem', color: 'var(--color-on-surface-muted)', marginTop: '0.25rem' }}>
                ZKTeco ADMS, Hikvision & Suprema
              </div>
            </Card>

            <Card>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                <div style={{ fontSize: '0.8125rem', color: 'var(--color-on-surface-muted)', fontWeight: 600 }}>
                  Terminal Health
                </div>
                <Badge tone="success">● ONLINE</Badge>
              </div>
              <div style={{ fontSize: '1.75rem', fontWeight: 800, marginTop: '0.25rem', color: '#10B981', fontVariantNumeric: 'tabular-nums' }}>
                {devicesQuery.data?.devices.filter(d => d.status === 'ONLINE').length ?? 3} / {devicesQuery.data?.devices.length ?? 3}
              </div>
              <div style={{ fontSize: '0.75rem', color: 'var(--color-on-surface-muted)', marginTop: '0.25rem' }}>
                All devices receiving heartbeats
              </div>
            </Card>

            <Card>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                <div style={{ fontSize: '0.8125rem', color: 'var(--color-on-surface-muted)', fontWeight: 600 }}>
                  Hardware Punches Logged
                </div>
                <Badge tone="neutral">Realtime</Badge>
              </div>
              <div style={{ fontSize: '1.75rem', fontWeight: 800, marginTop: '0.25rem', color: '#3B82F6', fontVariantNumeric: 'tabular-nums' }}>
                {devicesQuery.data?.devices.reduce((acc, d) => acc + d.totalPunchesLogged, 0).toLocaleString() ?? '2,635'}
              </div>
              <div style={{ fontSize: '0.75rem', color: 'var(--color-on-surface-muted)', marginTop: '0.25rem' }}>
                Biometric & RFID badge logs
              </div>
            </Card>

            <Card>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                <div style={{ fontSize: '0.8125rem', color: 'var(--color-on-surface-muted)', fontWeight: 600 }}>
                  Active Server Protocol
                </div>
                <Badge tone="neutral">ADMS Active</Badge>
              </div>
              <div style={{ fontSize: '1.125rem', fontWeight: 700, marginTop: '0.5rem', color: 'var(--color-on-surface)' }}>
                /iclock/cdata
              </div>
              <div style={{ fontSize: '0.75rem', color: 'var(--color-on-surface-muted)', marginTop: '0.25rem' }}>
                Automatic Data Master Server Push
              </div>
            </Card>
          </div>

          {/* Connected Terminals Directory Card */}
          <Card>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem', flexWrap: 'wrap', gap: '0.75rem' }}>
              <div>
                <h3 style={{ margin: 0, fontSize: '1.125rem', fontWeight: 700 }}>
                  Physical Biometric Terminals Registry
                </h3>
                <p style={{ margin: 0, fontSize: '0.8125rem', color: 'var(--color-on-surface-muted)' }}>
                  Manage registered ZKTeco, Hikvision, and Anviz edge attendance devices deployed across offices and turnstiles.
                </p>
              </div>
              <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap' }}>
                <Button variant="secondary" onClick={() => setIsSimulatePunchModalOpen(true)}>
                  ⚡ Simulate Hardware Punch
                </Button>
                <Button variant="primary" onClick={() => setIsRegisterDeviceModalOpen(true)}>
                  + Register Terminal
                </Button>
              </div>
            </div>

            {devicesQuery.isLoading ? (
              <LoadingState label="Loading biometric terminals…" />
            ) : (
              <DataTable<BiometricDeviceItem>
                caption="Registered biometric devices"
                rowKey={(d) => d.id}
                rows={devicesQuery.data?.devices ?? []}
                columns={[
                  {
                    header: 'Device & Model',
                    render: (d) => (
                      <div>
                        <div style={{ fontWeight: 600 }}>{d.name}</div>
                        <div style={{ fontSize: '0.75rem', color: 'var(--color-on-surface-muted)' }}>
                          {d.vendor} • {d.modelName ?? 'Standard ADMS'}
                        </div>
                      </div>
                    ),
                  },
                  {
                    header: 'Serial Number',
                    render: (d) => (
                      <span style={{ fontFamily: 'monospace', fontWeight: 600 }}>{d.serialNumber}</span>
                    ),
                  },
                  {
                    header: 'IP & Port',
                    render: (d) => (
                      <span style={{ fontSize: '0.8125rem', color: 'var(--color-on-surface-muted)', fontVariantNumeric: 'tabular-nums' }}>
                        {d.ipAddress ? `${d.ipAddress}:${d.port}` : 'Cloud Edge Push'}
                      </span>
                    ),
                  },
                  {
                    header: 'Location & Direction',
                    render: (d) => (
                      <div>
                        <div>{d.locationName ?? 'Lobby'}</div>
                        <Badge tone="neutral">
                          {d.direction === 'IN' ? 'Entrance Only' : d.direction === 'OUT' ? 'Exit Only' : 'Two-Way (In/Out)'}
                        </Badge>
                      </div>
                    ),
                  },
                  {
                    header: 'Status',
                    render: (d) => (
                      <Badge tone={d.status === 'ONLINE' ? 'success' : 'neutral'}>
                        {d.status === 'ONLINE' ? '● ONLINE' : d.status}
                      </Badge>
                    ),
                  },
                  {
                    header: 'Punches Logged',
                    render: (d) => (
                      <span style={{ fontWeight: 700, fontVariantNumeric: 'tabular-nums' }}>
                        {d.totalPunchesLogged.toLocaleString()}
                      </span>
                    ),
                  },
                  {
                    header: 'Actions',
                    render: (d) => (
                      <div style={{ display: 'flex', gap: '0.5rem' }}>
                        <Button
                          variant="secondary"
                          onClick={() => pingDeviceMutation.mutate(d.id)}
                          disabled={pingDeviceMutation.isPending}
                        >
                          Ping
                        </Button>
                        <Button
                          variant="secondary"
                          onClick={() => {
                            setSimSerial(d.serialNumber)
                            setIsSimulatePunchModalOpen(true)
                          }}
                        >
                          Test
                        </Button>
                        <Button
                          variant="secondary"
                          onClick={() => {
                            if (window.confirm(`De-register device "${d.name}"?`)) {
                              deleteDeviceMutation.mutate(d.id)
                            }
                          }}
                        >
                          Delete
                        </Button>
                      </div>
                    ),
                  },
                ]}
              />
            )}
          </Card>

          {/* Live Ingestion Feed Card */}
          <Card>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
              <div>
                <h3 style={{ margin: 0, fontSize: '1.125rem', fontWeight: 700, display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                  <span style={{ color: '#EF4444' }}>●</span> Live Biometric Ingestion Stream
                </h3>
                <p style={{ margin: 0, fontSize: '0.8125rem', color: 'var(--color-on-surface-muted)' }}>
                  Incoming real-time punch events received from physical terminals across offices and factory turnstiles.
                </p>
              </div>
              <Button variant="secondary" onClick={() => liveStreamQuery.refetch()}>
                🔄 Refresh Stream
              </Button>
            </div>

            {liveStreamQuery.isLoading ? (
              <LoadingState label="Listening for hardware events…" />
            ) : liveStreamQuery.data?.punches.length ? (
              <DataTable<BiometricLivePunchItem>
                caption="Realtime punch ingestion stream"
                rowKey={(p) => p.id}
                rows={liveStreamQuery.data.punches}
                columns={[
                  {
                    header: 'Time (UTC)',
                    render: (p) => (
                      <span style={{ fontVariantNumeric: 'tabular-nums', fontWeight: 600 }}>
                        {p.punchedAt.replace('T', ' ').substring(0, 19)}
                      </span>
                    ),
                  },
                  {
                    header: 'Employee',
                    render: (p) => (
                      <div>
                        <strong>{p.employeeName}</strong>{' '}
                        <span style={{ color: 'var(--color-on-surface-muted)', fontSize: '0.75rem' }}>
                          ({p.employeeCode})
                        </span>
                        <div style={{ fontSize: '0.75rem', color: 'var(--color-on-surface-muted)' }}>
                          {p.department}
                        </div>
                      </div>
                    ),
                  },
                  {
                    header: 'Direction',
                    render: (p) => (
                      <Badge tone={p.punchType === 'IN' ? 'success' : p.punchType === 'OUT' ? 'warning' : 'neutral'}>
                        {p.punchType}
                      </Badge>
                    ),
                  },
                  {
                    header: 'Verification Method',
                    render: (p) => (
                      <Badge tone="neutral">{p.verificationType}</Badge>
                    ),
                  },
                  {
                    header: 'Terminal Source',
                    render: (p) => (
                      <div>
                        <div style={{ fontWeight: 600 }}>{p.deviceName}</div>
                        <div style={{ fontSize: '0.75rem', color: 'var(--color-on-surface-muted)' }}>
                          {p.deviceSerialNumber} • {p.locationName}
                        </div>
                      </div>
                    ),
                  },
                ]}
              />
            ) : (
              <EmptyState
                title="No Hardware Punches Yet"
                description="Use the 'Simulate Hardware Punch' button above or connect a physical ZKTeco terminal to stream clock events."
              />
            )}
          </Card>
        </div>
      )}

      {/* ===================================================================== */}
      {/* DRAWER: Formula Calculation Trace Inspector                           */}
      {/* ===================================================================== */}
      <Drawer
        isOpen={Boolean(inspectingDailyId)}
        onClose={() => setInspectingDailyId(null)}
        title="Formula Calculation Trace Inspector"
        actions={
          <Button variant="secondary" onClick={() => setInspectingDailyId(null)}>
            Close Inspector
          </Button>
        }
      >
        {dailyDetailsQuery.isLoading ? (
          <LoadingState label="Inspecting calculation trace…" />
        ) : dailyDetailsQuery.data ? (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
            {/* Header info */}
            <div
              style={{
                padding: '1rem',
                borderRadius: '8px',
                background: 'var(--color-surface-variant)',
                border: '1px solid var(--color-outline)',
              }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <div>
                  <h3 style={{ margin: 0, fontSize: '1.125rem', color: 'var(--color-on-surface)' }}>
                    {dailyDetailsQuery.data.employeeName}
                  </h3>
                  <span style={{ fontSize: '0.8125rem', color: 'var(--color-on-surface-muted)' }}>
                    {dailyDetailsQuery.data.employeeCode} · {dailyDetailsQuery.data.department} ·{' '}
                    {dailyDetailsQuery.data.workDate}
                  </span>
                </div>
                <Badge
                  tone={
                    dailyDetailsQuery.data.dayStatus === 'PRESENT'
                      ? 'success'
                      : dailyDetailsQuery.data.dayStatus === 'HALF_DAY'
                        ? 'warning'
                        : 'danger'
                  }
                >
                  {dailyDetailsQuery.data.dayStatus}
                </Badge>
              </div>
            </div>

            {/* Metrics Breakdown Grid */}
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.75rem' }}>
              <div
                style={{
                  padding: '0.75rem',
                  borderRadius: '6px',
                  background: 'var(--color-surface-raised)',
                  border: '1px solid var(--color-outline)',
                }}
              >
                <div style={{ fontSize: '0.75rem', color: 'var(--color-on-surface-muted)' }}>
                  Gross → Net Worked
                </div>
                <div style={{ fontSize: '1.25rem', fontWeight: 700, fontVariantNumeric: 'tabular-nums' }}>
                  {dailyDetailsQuery.data.grossDurationMinutes}m → {dailyDetailsQuery.data.netWorkedMinutes}m
                </div>
                <div style={{ fontSize: '0.6875rem', color: 'var(--color-on-surface-muted)' }}>
                  Break deduction: -{dailyDetailsQuery.data.breakMinutes}m
                </div>
              </div>

              <div
                style={{
                  padding: '0.75rem',
                  borderRadius: '6px',
                  background: 'var(--color-surface-raised)',
                  border: '1px solid var(--color-outline)',
                }}
              >
                <div style={{ fontSize: '0.75rem', color: 'var(--color-on-surface-muted)' }}>
                  Total Overtime Credited
                </div>
                <div style={{ fontSize: '1.25rem', fontWeight: 700, color: '#8B5CF6', fontVariantNumeric: 'tabular-nums' }}>
                  {dailyDetailsQuery.data.overtimeMinutesNormal +
                    dailyDetailsQuery.data.overtimeMinutesRestDay +
                    dailyDetailsQuery.data.overtimeMinutesHoliday}m
                </div>
                <div style={{ fontSize: '0.6875rem', color: 'var(--color-on-surface-muted)' }}>
                  Tiers: {dailyDetailsQuery.data.overtimeMinutesNormal}m (1.5x), {dailyDetailsQuery.data.overtimeMinutesRestDay}m (2.0x)
                </div>
              </div>

              <div
                style={{
                  padding: '0.75rem',
                  borderRadius: '6px',
                  background: 'var(--color-surface-raised)',
                  border: '1px solid var(--color-outline)',
                }}
              >
                <div style={{ fontSize: '0.75rem', color: 'var(--color-on-surface-muted)' }}>
                  Lateness Penalty
                </div>
                <div style={{ fontSize: '1.25rem', fontWeight: 700, color: dailyDetailsQuery.data.lateMinutes > 0 ? '#F59E0B' : '#10B981', fontVariantNumeric: 'tabular-nums' }}>
                  {dailyDetailsQuery.data.lateMinutes > 0 ? `+${dailyDetailsQuery.data.lateMinutes}m Late` : 'None (On-Time)'}
                </div>
                <div style={{ fontSize: '0.6875rem', color: 'var(--color-on-surface-muted)' }}>
                  Grace window: 15 mins
                </div>
              </div>

              <div
                style={{
                  padding: '0.75rem',
                  borderRadius: '6px',
                  background: 'var(--color-surface-raised)',
                  border: '1px solid var(--color-outline)',
                }}
              >
                <div style={{ fontSize: '0.75rem', color: 'var(--color-on-surface-muted)' }}>
                  Early Departure
                </div>
                <div style={{ fontSize: '1.25rem', fontWeight: 700, color: dailyDetailsQuery.data.earlyLeaveMinutes > 0 ? '#EF4444' : '#10B981', fontVariantNumeric: 'tabular-nums' }}>
                  {dailyDetailsQuery.data.earlyLeaveMinutes > 0 ? `-${dailyDetailsQuery.data.earlyLeaveMinutes}m Early` : 'None'}
                </div>
                <div style={{ fontSize: '0.6875rem', color: 'var(--color-on-surface-muted)' }}>
                  Grace window: 10 mins
                </div>
              </div>
            </div>

            {/* Formula Execution Trace Steps */}
            <div>
              <h4 style={{ margin: '0 0 0.75rem 0', fontSize: '0.9375rem', color: 'var(--color-on-surface)' }}>
                Step-by-Step Statutory Engine Evaluation Log:
              </h4>
              <div
                style={{
                  padding: '1rem',
                  borderRadius: '8px',
                  background: '#0F172A',
                  color: '#E2E8F0',
                  fontFamily: 'monospace',
                  fontSize: '0.8125rem',
                  lineHeight: '1.7',
                  whiteSpace: 'pre-wrap',
                }}
              >
                {dailyDetailsQuery.data.calculationTrace ??
                  'No trace log generated for this attendance entry.'}
              </div>
            </div>

            {/* Shift Rules Card */}
            <div
              style={{
                padding: '1rem',
                borderRadius: '8px',
                background: 'var(--color-surface-raised)',
                border: '1px solid var(--color-outline)',
              }}
            >
              <h4 style={{ margin: '0 0 0.5rem 0', fontSize: '0.875rem', color: 'var(--color-on-surface)' }}>
                Shift Policy Rules ({dailyDetailsQuery.data.shiftCode ?? 'GEN_0830'}):
              </h4>
              <ul style={{ margin: 0, paddingLeft: '1.25rem', fontSize: '0.8125rem', color: 'var(--color-on-surface-muted)', display: 'flex', flexDirection: 'column', gap: '0.25rem' }}>
                <li>Standard Shift Work Minutes: 480 mins (8 hours net work).</li>
                <li>Morning Check-in Grace Period: 15 mins (Arrivals up to 08:45 incur 0 late penalty).</li>
                <li>Mandatory Lunch Break Deduction: 60 mins automatically subtracted from duration.</li>
                <li>Overtime Threshold: Requires minimum 30 mins after scheduled shift end to qualify.</li>
                <li>Half-Day Classification: Applied if net worked minutes are between 240m and 479m.</li>
              </ul>
            </div>
          </div>
        ) : (
          <EmptyState title="Record Not Found" description="Could not load calculation trace details." />
        )}
      </Drawer>

      {/* ===================================================================== */}
      {/* MODAL: Ingest Clock Punch                                             */}
      {/* ===================================================================== */}
      <Modal
        isOpen={isPunchModalOpen}
        onClose={() => setIsPunchModalOpen(false)}
        title="Ingest Biometric / Mobile Clock Punch"
        actions={
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.75rem' }}>
            <Button variant="secondary" onClick={() => setIsPunchModalOpen(false)}>
              Cancel
            </Button>
            <Button
              variant="primary"
              onClick={() => ingestPunchMutation.mutate()}
              disabled={ingestPunchMutation.isPending || !punchEmpId}
            >
              {ingestPunchMutation.isPending ? 'Ingesting…' : 'Record Punch'}
            </Button>
          </div>
        }
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          <div className="field">
            <label className="field__label">Employee</label>
            <select
              value={punchEmpId}
              onChange={(e) => setPunchEmpId(e.target.value)}
              className="field__input"
            >
              {employeesList.map((emp) => (
                <option key={emp.id} value={emp.id}>
                  {emp.name} ({emp.code}) — {emp.department}
                </option>
              ))}
            </select>
            <span className="field__hint">Select employee clocking the punch event.</span>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
            <div className="field">
              <label className="field__label">Punch Type</label>
              <select
                value={punchType}
                onChange={(e) => setPunchType(e.target.value as PunchType)}
                className="field__input"
              >
                <option value="IN">IN (Check In)</option>
                <option value="OUT">OUT (Check Out)</option>
                <option value="BREAK_IN">BREAK_IN</option>
                <option value="BREAK_OUT">BREAK_OUT</option>
              </select>
            </div>

            <Field
              label="Punch Time"
              value={punchDateTime}
              onChange={(e) => setPunchDateTime(e.target.value)}
              placeholder="2026-03-09T08:30:00Z"
            />
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
            <div className="field">
              <label className="field__label">Ingestion Channel</label>
              <select
                value={punchSource}
                onChange={(e) => setPunchSource(e.target.value as PunchSource)}
                className="field__input"
              >
                <option value="BIOMETRIC_DEVICE">🖲️ Biometric Hardware Turnstile</option>
                <option value="MOBILE_APP">📱 Mobile Geo-Attendance App</option>
                <option value="WEB_PORTAL">💻 Corporate Web Portal</option>
                <option value="KIOSK">🏢 Reception Tablet Kiosk</option>
              </select>
            </div>

            <Field
              label="Hardware Device ID"
              value={punchDeviceId}
              onChange={(e) => setPunchDeviceId(e.target.value)}
              placeholder="BIO-MAIN-01"
            />
          </div>

          <Field
            label="Location Name"
            value={punchLocationName}
            onChange={(e) => setPunchLocationName(e.target.value)}
            placeholder="HQ Main Entrance"
          />

          {/* Security & Geofence Fraud Simulation Toggles */}
          <div
            style={{
              padding: '0.875rem',
              borderRadius: '6px',
              background: 'var(--color-surface-variant)',
              border: '1px solid var(--color-outline)',
              display: 'flex',
              flexDirection: 'column',
              gap: '0.75rem',
            }}
          >
            <strong style={{ fontSize: '0.8125rem', color: 'var(--color-on-surface)' }}>
              Security & Geofence Audit Simulation:
            </strong>

            <div style={{ display: 'flex', alignItems: 'center', gap: '1.5rem', flexWrap: 'wrap' }}>
              <label style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', cursor: 'pointer', fontSize: '0.8125rem' }}>
                <input
                  type="checkbox"
                  checked={punchIsMockLocation}
                  onChange={(e) => setPunchIsMockLocation(e.target.checked)}
                />
                <span style={{ color: punchIsMockLocation ? '#EF4444' : 'inherit', fontWeight: punchIsMockLocation ? 700 : 400 }}>
                  🚨 Simulate Mock Location (GPS Spoofing)
                </span>
              </label>

              <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                <span style={{ fontSize: '0.8125rem', color: 'var(--color-on-surface-muted)' }}>
                  Geofence:
                </span>
                <select
                  value={punchGeofenceStatus}
                  onChange={(e) => setPunchGeofenceStatus(e.target.value as GeofenceStatus)}
                  style={{
                    padding: '0.25rem 0.5rem',
                    borderRadius: '4px',
                    border: '1px solid var(--color-outline)',
                    fontSize: '0.8125rem',
                  }}
                >
                  <option value="INSIDE">INSIDE Branch Boundary</option>
                  <option value="OUTSIDE">OUTSIDE Branch Boundary</option>
                  <option value="NOT_APPLICABLE">NOT APPLICABLE (Hardware)</option>
                </select>
              </div>
            </div>
          </div>
        </div>
      </Modal>

      {/* ===================================================================== */}
      {/* MODAL: Assign Shift                                                  */}
      {/* ===================================================================== */}
      <Modal
        isOpen={isAssignModalOpen}
        onClose={() => setIsAssignModalOpen(false)}
        title="Assign Employee Shift"
        actions={
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.75rem' }}>
            <Button variant="secondary" onClick={() => setIsAssignModalOpen(false)}>
              Cancel
            </Button>
            <Button
              variant="primary"
              onClick={() => assignShiftMutation.mutate()}
              disabled={assignShiftMutation.isPending || !assignEmpId}
            >
              {assignShiftMutation.isPending ? 'Saving…' : 'Save Shift Assignment'}
            </Button>
          </div>
        }
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          <div className="field">
            <label className="field__label">Employee</label>
            <select
              value={assignEmpId}
              onChange={(e) => setAssignEmpId(e.target.value)}
              className="field__input"
            >
              {employeesList.map((emp) => (
                <option key={emp.id} value={emp.id}>
                  {emp.name} ({emp.code}) — {emp.department}
                </option>
              ))}
            </select>
          </div>

          <Field
            label="Work Date"
            type="date"
            value={assignWorkDate}
            onChange={(e) => setAssignWorkDate(e.target.value)}
          />

          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', margin: '0.25rem 0' }}>
            <input
              type="checkbox"
              id="restDayCheck"
              checked={assignIsRestDay}
              onChange={(e) => setAssignIsRestDay(e.target.checked)}
            />
            <label htmlFor="restDayCheck" style={{ fontSize: '0.875rem', cursor: 'pointer', fontWeight: 500 }}>
              Mark as Scheduled Rest Day
            </label>
          </div>

          {!assignIsRestDay && (
            <div className="field">
              <label className="field__label">Shift Pattern</label>
              <select
                value={assignShiftId}
                onChange={(e) => setAssignShiftId(e.target.value)}
                className="field__input"
              >
                {shiftsQuery.data?.shifts.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.name} ({s.startTime} - {s.endTime})
                  </option>
                ))}
              </select>
            </div>
          )}
        </div>
      </Modal>

      {/* ===================================================================== */}
      {/* MODAL: Variable Payroll Inputs Summary                                */}
      {/* ===================================================================== */}
      <Modal
        isOpen={isPayrollModalOpen}
        onClose={() => setIsPayrollModalOpen(false)}
        title="Attendance → Payroll Statutory Inputs Summary"
        size="large"
        actions={
          <Button variant="secondary" onClick={() => setIsPayrollModalOpen(false)}>
            Close
          </Button>
        }
      >
        {payrollSummaryQuery.isLoading ? (
          <LoadingState label="Loading payroll variable inputs feed…" />
        ) : payrollSummaryQuery.data ? (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
            <p style={{ margin: 0, fontSize: '0.8125rem', color: 'var(--color-on-surface-muted)' }}>
              This summary represents the verified monthly variable feed (overtime earnings, late penalty
              deductions, and unpaid absence days) automatically consumed by the P3 Statutory Payroll
              Calculation Engine.
            </p>

            {/* Total cards */}
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '1rem' }}>
              <div
                style={{
                  padding: '1rem',
                  borderRadius: '6px',
                  background: 'var(--color-surface-variant)',
                }}
              >
                <div style={{ fontSize: '0.75rem', color: 'var(--color-on-surface-muted)' }}>
                  Total Overtime Accrued
                </div>
                <div style={{ fontSize: '1.5rem', fontWeight: 800, color: '#8B5CF6' }}>
                  {payrollSummaryQuery.data.totalNormalOtHours +
                    payrollSummaryQuery.data.totalRestDayOtHours +
                    payrollSummaryQuery.data.totalHolidayOtHours}{' '}
                  hrs
                </div>
                <div style={{ fontSize: '0.75rem', color: 'var(--color-on-surface-muted)' }}>
                  Gross OT Pay: LKR {payrollSummaryQuery.data.totalOtEarningsAmount.toLocaleString()}
                </div>
              </div>

              <div
                style={{
                  padding: '1rem',
                  borderRadius: '6px',
                  background: 'var(--color-surface-variant)',
                }}
              >
                <div style={{ fontSize: '0.75rem', color: 'var(--color-on-surface-muted)' }}>
                  Late Penalty Deductions
                </div>
                <div style={{ fontSize: '1.5rem', fontWeight: 800, color: '#F59E0B' }}>
                  LKR {payrollSummaryQuery.data.totalLatePenaltyDeductionAmount.toLocaleString()}
                </div>
                <div style={{ fontSize: '0.75rem', color: 'var(--color-on-surface-muted)' }}>
                  Across {payrollSummaryQuery.data.employeeCountWithLatePenalty} employees
                </div>
              </div>

              <div
                style={{
                  padding: '1rem',
                  borderRadius: '6px',
                  background: 'var(--color-surface-variant)',
                }}
              >
                <div style={{ fontSize: '0.75rem', color: 'var(--color-on-surface-muted)' }}>
                  Unpaid Absence Days
                </div>
                <div style={{ fontSize: '1.5rem', fontWeight: 800, color: '#EF4444' }}>
                  {payrollSummaryQuery.data.totalUnpaidAbsenceDays} days
                </div>
                <div style={{ fontSize: '0.75rem', color: 'var(--color-on-surface-muted)' }}>
                  Salary proration applied in run
                </div>
              </div>
            </div>

            {/* Employee Breakdown Table */}
            <table
              style={{
                width: '100%',
                borderCollapse: 'collapse',
                fontSize: '0.8125rem',
              }}
            >
              <thead>
                <tr style={{ borderBottom: '1px solid var(--color-outline)', textAlign: 'left' }}>
                  <th style={{ padding: '0.5rem' }}>Employee</th>
                  <th style={{ padding: '0.5rem' }}>Normal OT (1.5x)</th>
                  <th style={{ padding: '0.5rem' }}>Rest Day OT (2.0x)</th>
                  <th style={{ padding: '0.5rem' }}>OT Gross Pay</th>
                  <th style={{ padding: '0.5rem' }}>Late Penalty</th>
                  <th style={{ padding: '0.5rem' }}>Unpaid Days</th>
                </tr>
              </thead>
              <tbody>
                {payrollSummaryQuery.data.items.map((it) => (
                  <tr key={it.employeeId} style={{ borderBottom: '1px solid var(--color-outline)' }}>
                    <td style={{ padding: '0.5rem' }}>
                      <strong>{it.employeeName}</strong> ({it.employeeCode})
                    </td>
                    <td style={{ padding: '0.5rem', fontVariantNumeric: 'tabular-nums' }}>
                      {it.normalOtMinutes > 0 ? `${it.normalOtMinutes}m` : '—'}
                    </td>
                    <td style={{ padding: '0.5rem', fontVariantNumeric: 'tabular-nums' }}>
                      {it.restDayOtMinutes > 0 ? `${it.restDayOtMinutes}m` : '—'}
                    </td>
                    <td style={{ padding: '0.5rem', fontVariantNumeric: 'tabular-nums', fontWeight: 600, color: '#8B5CF6' }}>
                      {it.otGrossEarnings > 0 ? `LKR ${it.otGrossEarnings.toLocaleString()}` : '—'}
                    </td>
                    <td style={{ padding: '0.5rem', fontVariantNumeric: 'tabular-nums', color: '#F59E0B' }}>
                      {it.latePenaltyDeduction > 0 ? `LKR ${it.latePenaltyDeduction.toLocaleString()}` : '—'}
                    </td>
                    <td style={{ padding: '0.5rem', fontVariantNumeric: 'tabular-nums' }}>
                      {it.unpaidAbsenceDays > 0 ? `${it.unpaidAbsenceDays}d` : '—'}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <EmptyState title="No payroll data" description="Unable to load payroll summary." />
        )}
      </Modal>

      {/* ===================================================================== */}
      {/* MODAL: Register Biometric Device Terminal                             */}
      {/* ===================================================================== */}
      <Modal
        isOpen={isRegisterDeviceModalOpen}
        onClose={() => setIsRegisterDeviceModalOpen(false)}
        title="Register Physical Biometric Terminal"
        actions={
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.75rem' }}>
            <Button variant="secondary" onClick={() => setIsRegisterDeviceModalOpen(false)}>
              Cancel
            </Button>
            <Button
              variant="primary"
              onClick={() => createDeviceMutation.mutate()}
              disabled={createDeviceMutation.isPending || !devName || !devSerial}
            >
              {createDeviceMutation.isPending ? 'Registering…' : 'Register Terminal'}
            </Button>
          </div>
        }
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          <div className="field">
            <label className="field__label">Terminal Name</label>
            <input
              type="text"
              value={devName}
              onChange={(e) => setDevName(e.target.value)}
              placeholder="e.g. Factory Main Entrance Turnstile A"
              className="field__input"
            />
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
            <div className="field">
              <label className="field__label">Device Serial Number (Unique)</label>
              <input
                type="text"
                value={devSerial}
                onChange={(e) => setDevSerial(e.target.value)}
                placeholder="e.g. ZK-COL-009"
                className="field__input"
              />
            </div>
            <div className="field">
              <label className="field__label">Vendor / Manufacturer</label>
              <select
                value={devVendor}
                onChange={(e) => setDevVendor(e.target.value)}
                className="field__input"
              >
                <option value="ZKTECO">ZKTeco (ADMS Push)</option>
                <option value="HIKVISION">Hikvision (Face & RFID)</option>
                <option value="SUPREMA">Suprema (BioStation)</option>
                <option value="ANVIZ">Anviz (CrossChex)</option>
                <option value="GENERIC_HTTP">Generic HTTP / IoT Gateway</option>
              </select>
            </div>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
            <div className="field">
              <label className="field__label">Hardware Model</label>
              <input
                type="text"
                value={devModel}
                onChange={(e) => setDevModel(e.target.value)}
                placeholder="e.g. uFace 800 Plus"
                className="field__input"
              />
            </div>
            <div className="field">
              <label className="field__label">Physical Location</label>
              <input
                type="text"
                value={devLocation}
                onChange={(e) => setDevLocation(e.target.value)}
                placeholder="e.g. Colombo Ground Lobby"
                className="field__input"
              />
            </div>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr 1fr', gap: '1rem' }}>
            <div className="field">
              <label className="field__label">IP Address / Gateway Host</label>
              <input
                type="text"
                value={devIp}
                onChange={(e) => setDevIp(e.target.value)}
                placeholder="192.168.1.200"
                className="field__input"
              />
            </div>
            <div className="field">
              <label className="field__label">Port</label>
              <input
                type="number"
                value={devPort}
                onChange={(e) => setDevPort(parseInt(e.target.value, 10) || 4370)}
                className="field__input"
              />
            </div>
            <div className="field">
              <label className="field__label">Direction</label>
              <select
                value={devDirection}
                onChange={(e) => setDevDirection(e.target.value)}
                className="field__input"
              >
                <option value="IN_OUT">Two-Way (In/Out)</option>
                <option value="IN">Entrance Only</option>
                <option value="OUT">Exit Only</option>
              </select>
            </div>
          </div>
        </div>
      </Modal>

      {/* ===================================================================== */}
      {/* MODAL: Simulate Hardware Punch                                        */}
      {/* ===================================================================== */}
      <Modal
        isOpen={isSimulatePunchModalOpen}
        onClose={() => setIsSimulatePunchModalOpen(false)}
        title="Simulate Hardware Biometric Punch"
        actions={
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.75rem' }}>
            <Button variant="secondary" onClick={() => setIsSimulatePunchModalOpen(false)}>
              Cancel
            </Button>
            <Button
              variant="primary"
              onClick={() => simulatePunchMutation.mutate()}
              disabled={simulatePunchMutation.isPending || !simSerial || !simEmployeeCode}
            >
              {simulatePunchMutation.isPending ? 'Simulating…' : '⚡ Fire Biometric Punch'}
            </Button>
          </div>
        }
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          <p style={{ margin: 0, fontSize: '0.875rem', color: 'var(--color-on-surface-muted)' }}>
            Trigger an automated clock-in/out event mimicking a physical ZKTeco or Hikvision terminal push.
            This will record into <code style={{ color: 'var(--color-primary)' }}>raw_punch</code> and immediately recalculate the daily attendance roll-up.
          </p>

          <div className="field">
            <label className="field__label">Select Biometric Terminal</label>
            <select
              value={simSerial}
              onChange={(e) => setSimSerial(e.target.value)}
              className="field__input"
            >
              {devicesQuery.data?.devices.map((d) => (
                <option key={d.id} value={d.serialNumber}>
                  {d.name} ({d.serialNumber}) — {d.locationName ?? 'Lobby'}
                </option>
              ))}
            </select>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
            <div className="field">
              <label className="field__label">Employee Code / Device PIN</label>
              <select
                value={simEmployeeCode}
                onChange={(e) => setSimEmployeeCode(e.target.value)}
                className="field__input"
              >
                {employeesList.map((emp) => (
                  <option key={emp.id} value={emp.code}>
                    {emp.name} ({emp.code}) — {emp.department}
                  </option>
                ))}
              </select>
            </div>
            <div className="field">
              <label className="field__label">Punch Direction</label>
              <select
                value={simPunchType}
                onChange={(e) => setSimPunchType(e.target.value)}
                className="field__input"
              >
                <option value="IN">Clock IN</option>
                <option value="OUT">Clock OUT</option>
                <option value="BREAK_OUT">Break OUT</option>
                <option value="BREAK_IN">Break IN</option>
              </select>
            </div>
          </div>

          <div className="field">
            <label className="field__label">Biometric Verification Modality</label>
            <select
              value={simVerifyType}
              onChange={(e) => setSimVerifyType(e.target.value)}
              className="field__input"
            >
              <option value="FACE">Face Recognition (Camera Sensor)</option>
              <option value="FINGERPRINT">Optical Fingerprint Scanner</option>
              <option value="RFID_CARD">RFID Contactless Smart Badge</option>
              <option value="PALM">Near-Infrared Palm Vein</option>
            </select>
          </div>
        </div>
      </Modal>
    </div>
  )
}
