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
  LoadingState,
  Modal,
} from '@/components/ui'
import {
  payrollApi,
  type BankAdvicePayload,
  type BankAdviceResponse,
  type PayGroup,
  type PayPeriod,
  type PayrollResult,
  type PayrollResultLine,
} from '@/lib/api'

export function Payroll() {
  const queryClient = useQueryClient()

  // -------------------------------------------------------------------------
  // Pay Groups & Periods Queries
  // -------------------------------------------------------------------------
  const payGroupsQuery = useQuery({
    queryKey: ['payroll', 'pay-groups'],
    queryFn: () => payrollApi.listPayGroups(),
  })

  const [selectedPayGroupId, setSelectedPayGroupId] = useState<string>('pg-lk-monthly')

  const payPeriodsQuery = useQuery({
    queryKey: ['payroll', 'pay-periods', selectedPayGroupId],
    queryFn: () => payrollApi.listPayPeriods(selectedPayGroupId),
    enabled: Boolean(selectedPayGroupId),
  })

  const [selectedPayPeriodId, setSelectedPayPeriodId] = useState<string>('period-2026-m03')

  // Runs Query
  const runsQuery = useQuery({
    queryKey: ['payroll', 'runs', selectedPayPeriodId],
    queryFn: () => payrollApi.getRuns(selectedPayPeriodId),
    enabled: Boolean(selectedPayPeriodId),
  })

  // Active run for the selected period
  const activeRun = useMemo(() => {
    if (!runsQuery.data?.runs || runsQuery.data.runs.length === 0) return null
    return runsQuery.data.runs[0]
  }, [runsQuery.data])

  // Variance Query
  const varianceQuery = useQuery({
    queryKey: ['payroll', 'variance', activeRun?.id],
    queryFn: () => payrollApi.getVariance(activeRun!.id),
    enabled: Boolean(activeRun?.id),
  })

  // Results list query
  const [departmentFilter, setDepartmentFilter] = useState<string>('ALL')
  const [searchQuery, setSearchQuery] = useState<string>('')

  const resultsQuery = useQuery({
    queryKey: ['payroll', 'results', activeRun?.id, departmentFilter, searchQuery],
    queryFn: () =>
      payrollApi.listResults(activeRun!.id, {
        department: departmentFilter === 'ALL' ? undefined : departmentFilter,
        q: searchQuery.trim() || undefined,
      }),
    enabled: Boolean(activeRun?.id),
  })

  // -------------------------------------------------------------------------
  // Payslip Inspector Drawer State
  // -------------------------------------------------------------------------
  const [inspectingResultId, setInspectingResultId] = useState<string | null>(null)

  const payslipDetailsQuery = useQuery({
    queryKey: ['payroll', 'result-details', activeRun?.id, inspectingResultId],
    queryFn: () => payrollApi.getResultDetails(activeRun!.id, inspectingResultId!),
    enabled: Boolean(activeRun?.id && inspectingResultId),
  })

  // -------------------------------------------------------------------------
  // Run Lifecycle Mutations
  // -------------------------------------------------------------------------
  const [calculationFeedback, setCalculationFeedback] = useState<string | null>(null)

  const calculateMutation = useMutation({
    mutationFn: () =>
      payrollApi.calculateRun({
        payGroupId: selectedPayGroupId,
        payPeriodId: selectedPayPeriodId,
      }),
    onSuccess: (run) => {
      setCalculationFeedback(`Batch calculation completed for ${run.totalEmployees} employees.`)
      void queryClient.invalidateQueries({ queryKey: ['payroll'] })
    },
  })

  const approveMutation = useMutation({
    mutationFn: (runId: string) => payrollApi.approveRun(runId),
    onSuccess: () => {
      setCalculationFeedback('Payroll run approved. Ready for commitment and disbursement.')
      void queryClient.invalidateQueries({ queryKey: ['payroll'] })
    },
  })

  const commitMutation = useMutation({
    mutationFn: (runId: string) => payrollApi.commitRun(runId),
    onSuccess: () => {
      setCalculationFeedback('Payroll committed and published. Bank advice files are now final.')
      setIsCommitRunModalOpen(false)
      void queryClient.invalidateQueries({ queryKey: ['payroll'] })
    },
  })

  const rejectMutation = useMutation({
    mutationFn: ({ runId, reason }: { runId: string; reason: string }) =>
      payrollApi.rejectRun(runId, reason),
    onSuccess: () => {
      setCalculationFeedback('Payroll run returned to draft status for adjustments.')
      setIsRejectRunModalOpen(false)
      setRejectReason('')
      void queryClient.invalidateQueries({ queryKey: ['payroll'] })
    },
  })

  // -------------------------------------------------------------------------
  // Mutation Dialog States
  // -------------------------------------------------------------------------
  const [isApproveRunModalOpen, setIsApproveRunModalOpen] = useState(false)
  const [isRejectRunModalOpen, setIsRejectRunModalOpen] = useState(false)
  const [isCommitRunModalOpen, setIsCommitRunModalOpen] = useState(false)
  const [rejectReason, setRejectReason] = useState('')

  // -------------------------------------------------------------------------
  // Bank Advice File Generator Modal State
  // -------------------------------------------------------------------------
  const [isBankAdviceModalOpen, setIsBankAdviceModalOpen] = useState(false)
  const [adviceFormat, setAdviceFormat] = useState<'CSV_STANDARD' | 'ACH_NACHA'>('CSV_STANDARD')
  const [companyAccount, setCompanyAccount] = useState('9988776655')
  const [paymentDate, setPaymentDate] = useState('2026-03-25')
  const [generatedAdvice, setGeneratedAdvice] = useState<BankAdviceResponse | null>(null)

  const bankAdviceMutation = useMutation({
    mutationFn: (payload: BankAdvicePayload) =>
      payrollApi.generateBankAdvice(activeRun!.id, payload),
    onSuccess: (resp) => {
      setGeneratedAdvice(resp)
    },
  })

  const handleOpenBankAdviceModal = () => {
    setGeneratedAdvice(null)
    setIsBankAdviceModalOpen(true)
    bankAdviceMutation.mutate({
      format: adviceFormat,
      companyAccount,
      paymentDate,
    })
  }

  const handleDownloadFile = () => {
    if (!generatedAdvice) return
    const blob = new Blob([generatedAdvice.content], { type: generatedAdvice.mimeType })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = generatedAdvice.filename
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(url)
  }

  if (payGroupsQuery.isPending || payPeriodsQuery.isPending) {
    return <LoadingState label="Loading payroll cohorts and pay periods…" />
  }

  if (payGroupsQuery.isError) {
    return (
      <QueryErrorState
        error={payGroupsQuery.error}
        onRetry={() => void payGroupsQuery.refetch()}
      />
    )
  }

  const payGroups = payGroupsQuery.data.payGroups
  const payPeriods = payPeriodsQuery.data?.payPeriods ?? []

  return (
    <div className="payroll-page" style={{ display: 'flex', flexDirection: 'column', gap: '1.5rem' }}>
      {/* --------------------------------------------------------------------- */}
      {/* Page Header                                                           */}
      {/* --------------------------------------------------------------------- */}
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
          <h1 style={{ margin: 0, fontSize: '1.75rem', fontWeight: 700 }}>Payroll Run Console</h1>
          <p style={{ margin: '0.25rem 0 0 0', color: 'var(--color-on-surface-muted)' }}>
            Statutory gross-to-net calculation pipeline, variance auditing, itemized payslips, and bank advice export.
          </p>
        </div>

        {activeRun && (
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', flexWrap: 'wrap' }}>
            <Button
              variant="primary"
              loading={calculateMutation.isPending}
              onClick={() => calculateMutation.mutate()}
            >
              Run Batch Calculation
            </Button>

            {activeRun.status === 'CALCULATED' && (
              <>
                <Button
                  variant="secondary"
                  onClick={() => setIsApproveRunModalOpen(true)}
                >
                  Approve Payroll
                </Button>
                <Button
                  variant="ghost"
                  onClick={() => setIsRejectRunModalOpen(true)}
                >
                  Reject / Return
                </Button>
              </>
            )}

            {activeRun.status === 'APPROVED' && (
              <>
                <Button
                  variant="secondary"
                  onClick={() => setIsCommitRunModalOpen(true)}
                >
                  Commit & Publish
                </Button>
                <Button
                  variant="ghost"
                  onClick={() => setIsRejectRunModalOpen(true)}
                >
                  Return to Draft
                </Button>
              </>
            )}

            <Button variant="secondary" onClick={handleOpenBankAdviceModal}>
              Download Bank Advice
            </Button>
          </div>
        )}
      </div>

      {/* --------------------------------------------------------------------- */}
      {/* Cohort & Period Selection Strip                                      */}
      {/* --------------------------------------------------------------------- */}
      <Card>
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
            gap: '1rem',
            alignItems: 'center',
          }}
        >
          {/* Pay Group Select */}
          <div className="field" style={{ margin: 0 }}>
            <label className="field__label" htmlFor="pay-group-select">
              Pay Group Cohort
            </label>
            <select
              id="pay-group-select"
              className="field__input"
              value={selectedPayGroupId}
              onChange={(e) => setSelectedPayGroupId(e.target.value)}
            >
              {payGroups.map((g: PayGroup) => (
                <option key={g.id} value={g.id}>
                  {g.name} ({g.currency})
                </option>
              ))}
            </select>
          </div>

          {/* Pay Period Select */}
          <div className="field" style={{ margin: 0 }}>
            <label className="field__label" htmlFor="pay-period-select">
              Pay Period
            </label>
            <select
              id="pay-period-select"
              className="field__input"
              value={selectedPayPeriodId}
              onChange={(e) => setSelectedPayPeriodId(e.target.value)}
            >
              {payPeriods.map((p: PayPeriod) => (
                <option key={p.id} value={p.id}>
                  {p.code}: {p.startDate} → {p.endDate} [{p.status}]
                </option>
              ))}
            </select>
          </div>

          {/* Run Status & Timestamp */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.25rem' }}>
            <span style={{ fontSize: '0.8125rem', fontWeight: 500, color: 'var(--color-on-surface-muted)' }}>
              Calculation Lifecycle
            </span>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              {activeRun ? (
                <>
                  <Badge
                    tone={
                      activeRun.status === 'COMMITTED'
                        ? 'success'
                        : activeRun.status === 'APPROVED'
                          ? 'neutral'
                          : activeRun.status === 'CALCULATED'
                            ? 'warning'
                            : 'neutral'
                    }
                  >
                    {activeRun.status === 'CALCULATED'
                      ? 'CALCULATED — PENDING APPROVAL'
                      : activeRun.status}
                  </Badge>
                  <span style={{ fontSize: '0.75rem', color: 'var(--color-on-surface-muted)' }}>
                    Run #{activeRun.runNumber}
                  </span>
                </>
              ) : (
                <Badge tone="neutral">DRAFT</Badge>
              )}
            </div>
          </div>
        </div>

        {calculationFeedback && (
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
            <span>✓ {calculationFeedback}</span>
            <button
              type="button"
              style={{ background: 'none', border: 'none', cursor: 'pointer', fontSize: '1rem' }}
              onClick={() => setCalculationFeedback(null)}
            >
              &times;
            </button>
          </div>
        )}
      </Card>

      {/* --------------------------------------------------------------------- */}
      {/* Financial Metrics Cards Strip                                         */}
      {/* --------------------------------------------------------------------- */}
      {activeRun && (
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
            gap: '1rem',
          }}
        >
          {/* Total Net Pay */}
          <Card>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.25rem' }}>
              <span style={{ fontSize: '0.8125rem', color: 'var(--color-on-surface-muted)', fontWeight: 500 }}>
                Total Net Pay (Disbursement)
              </span>
              <span
                style={{
                  fontSize: '1.75rem',
                  fontWeight: 800,
                  color: 'var(--color-brand-primary)',
                  letterSpacing: '-0.02em',
                  fontVariantNumeric: 'tabular-nums',
                }}
              >
                LKR {activeRun.totalNet.toLocaleString(undefined, { minimumFractionDigits: 2 })}
              </span>
              <span style={{ fontSize: '0.75rem', color: 'var(--color-on-surface-muted)' }}>
                Across {activeRun.totalEmployees} employees
              </span>
            </div>
          </Card>

          {/* Total Gross Pay */}
          <Card>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.25rem' }}>
              <span style={{ fontSize: '0.8125rem', color: 'var(--color-on-surface-muted)', fontWeight: 500 }}>
                Total Gross Pay
              </span>
              <span
                style={{
                  fontSize: '1.75rem',
                  fontWeight: 700,
                  color: 'var(--color-on-surface)',
                  letterSpacing: '-0.02em',
                  fontVariantNumeric: 'tabular-nums',
                }}
              >
                LKR {activeRun.totalGross.toLocaleString(undefined, { minimumFractionDigits: 2 })}
              </span>
              <span style={{ fontSize: '0.75rem', color: 'var(--color-on-surface-muted)' }}>
                Basic salary + allowances + bonuses
              </span>
            </div>
          </Card>

          {/* Statutory Employee Deductions */}
          <Card>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.25rem' }}>
              <span style={{ fontSize: '0.8125rem', color: 'var(--color-on-surface-muted)', fontWeight: 500 }}>
                Employee Statutory (EPF 8%)
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
                LKR {activeRun.totalStatutoryEmployee.toLocaleString(undefined, { minimumFractionDigits: 2 })}
              </span>
              <span style={{ fontSize: '0.75rem', color: 'var(--color-on-surface-muted)' }}>
                Withheld from employees
              </span>
            </div>
          </Card>

          {/* Employer Statutory Contributions */}
          <Card>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.25rem' }}>
              <span style={{ fontSize: '0.8125rem', color: 'var(--color-on-surface-muted)', fontWeight: 500 }}>
                Employer Statutory (EPF 12% + ETF 3%)
              </span>
              <span
                style={{
                  fontSize: '1.75rem',
                  fontWeight: 700,
                  color: '#3B82F6',
                  letterSpacing: '-0.02em',
                  fontVariantNumeric: 'tabular-nums',
                }}
              >
                LKR {activeRun.totalStatutoryEmployer.toLocaleString(undefined, { minimumFractionDigits: 2 })}
              </span>
              <span style={{ fontSize: '0.75rem', color: 'var(--color-on-surface-muted)' }}>
                Statutory company liability
              </span>
            </div>
          </Card>

          {/* Total Tax Withheld */}
          <Card>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.25rem' }}>
              <span style={{ fontSize: '0.8125rem', color: 'var(--color-on-surface-muted)', fontWeight: 500 }}>
                Income Tax (APIT Slabs)
              </span>
              <span
                style={{
                  fontSize: '1.75rem',
                  fontWeight: 700,
                  color: '#F59E0B',
                  letterSpacing: '-0.02em',
                  fontVariantNumeric: 'tabular-nums',
                }}
              >
                LKR {activeRun.totalTax.toLocaleString(undefined, { minimumFractionDigits: 2 })}
              </span>
              <span style={{ fontSize: '0.75rem', color: 'var(--color-on-surface-muted)' }}>
                Remitted to Inland Revenue
              </span>
            </div>
          </Card>
        </div>
      )}

      {/* --------------------------------------------------------------------- */}
      {/* Variance & Anomaly Alert Card                                         */}
      {/* --------------------------------------------------------------------- */}
      {varianceQuery.data && (
        <Card>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '0.5rem' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                <span style={{ fontSize: '1.25rem' }}>🔍</span>
                <h3 style={{ margin: 0, fontSize: '1.0625rem', fontWeight: 600 }}>
                  Prior-Period Variance & Anomaly Audit
                </h3>
              </div>

              <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', fontSize: '0.8125rem' }}>
                <span>
                  Gross Variance:{' '}
                  <strong style={{ color: varianceQuery.data.grossVariancePercent >= 0 ? '#10B981' : '#EF4444' }}>
                    {varianceQuery.data.grossVariancePercent >= 0 ? '+' : ''}
                    {varianceQuery.data.grossVariancePercent}%
                  </strong>
                </span>
                <span>
                  Net Variance:{' '}
                  <strong style={{ color: varianceQuery.data.netVariancePercent >= 0 ? '#10B981' : '#EF4444' }}>
                    {varianceQuery.data.netVariancePercent >= 0 ? '+' : ''}
                    {varianceQuery.data.netVariancePercent}%
                  </strong>
                </span>
                <Badge tone={varianceQuery.data.anomalies.length > 0 ? 'warning' : 'success'}>
                  {varianceQuery.data.anomalies.length} Flagged Anomalies
                </Badge>
              </div>
            </div>

            {varianceQuery.data.anomalies.length > 0 && (
              <div
                style={{
                  background: 'var(--color-surface-variant)',
                  border: '1px solid var(--color-outline)',
                  borderRadius: '6px',
                  overflow: 'hidden',
                }}
              >
                <table className="table" style={{ fontSize: '0.8125rem' }}>
                  <caption className="sr-only">Anomalous variances against prior month</caption>
                  <thead>
                    <tr>
                      <th scope="col">Employee</th>
                      <th scope="col" className="numeric">Prior Gross</th>
                      <th scope="col" className="numeric">Current Gross</th>
                      <th scope="col" className="numeric">Variance %</th>
                      <th scope="col">Audit Root Cause</th>
                    </tr>
                  </thead>
                  <tbody>
                    {varianceQuery.data.anomalies.map((item) => (
                      <tr key={item.employeeCode}>
                        <td>
                          <strong>{item.employeeName}</strong> ({item.employeeCode})
                        </td>
                        <td className="numeric">
                          LKR {item.previousGross.toLocaleString(undefined, { minimumFractionDigits: 2 })}
                        </td>
                        <td className="numeric">
                          <strong>
                            LKR {item.currentGross.toLocaleString(undefined, { minimumFractionDigits: 2 })}
                          </strong>
                        </td>
                        <td className="numeric">
                          <span
                            style={{
                              color: item.variancePercent > 15 ? '#D97706' : undefined,
                              fontWeight: 700,
                            }}
                          >
                            +{item.variancePercent.toFixed(2)}%
                          </span>
                        </td>
                        <td>
                          <span style={{ color: 'var(--color-on-surface-muted)' }}>
                            {item.reasons.join(' · ')}
                          </span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </Card>
      )}

      {/* --------------------------------------------------------------------- */}
      {/* Employee Pay Results Table                                            */}
      {/* --------------------------------------------------------------------- */}
      <Card>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          {/* Controls bar */}
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
              <input
                type="search"
                className="field__input"
                style={{ minHeight: '36px', width: '240px' }}
                placeholder="Search by code or name…"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
              />

              <select
                className="field__input"
                style={{ minHeight: '36px', padding: '0 0.5rem' }}
                value={departmentFilter}
                onChange={(e) => setDepartmentFilter(e.target.value)}
              >
                <option value="ALL">All Departments</option>
                <option value="ENG">Engineering</option>
                <option value="HR">People & Culture</option>
                <option value="FIN">Finance</option>
                <option value="SLS">Sales</option>
                <option value="OPS">Operations</option>
              </select>
            </div>

            <span style={{ fontSize: '0.8125rem', color: 'var(--color-on-surface-muted)' }}>
              Showing {resultsQuery.data?.results.length ?? 0} payslip records
            </span>
          </div>

          {resultsQuery.isPending && <LoadingState label="Loading employee pay results…" />}
          {resultsQuery.isError && (
            <QueryErrorState
              error={resultsQuery.error}
              onRetry={() => void resultsQuery.refetch()}
            />
          )}

          {resultsQuery.isSuccess && resultsQuery.data.results.length === 0 && (
            <EmptyState
              title="No payroll records match"
              description="No employee payroll calculations found matching your search criteria."
            />
          )}

          {resultsQuery.isSuccess && resultsQuery.data.results.length > 0 && (
            <DataTable<PayrollResult>
              caption="Employee Payroll Results"
              rows={resultsQuery.data.results}
              rowKey={(r) => r.id}
              columns={[
                {
                  header: 'Employee',
                  render: (r) => (
                    <div>
                      <div style={{ fontWeight: 600, fontSize: '0.875rem' }}>{r.employeeName}</div>
                      <div style={{ fontSize: '0.75rem', color: 'var(--color-on-surface-muted)' }}>
                        {r.employeeCode} · {r.designation}
                      </div>
                    </div>
                  ),
                },
                {
                  header: 'Basic Salary',
                  numeric: true,
                  render: (r) => (
                    <span style={{ fontVariantNumeric: 'tabular-nums' }}>
                      {r.basicSalary.toLocaleString(undefined, { minimumFractionDigits: 2 })}
                    </span>
                  ),
                },
                {
                  header: 'Gross Pay',
                  numeric: true,
                  render: (r) => (
                    <span style={{ fontVariantNumeric: 'tabular-nums', fontWeight: 600 }}>
                      {r.grossPay.toLocaleString(undefined, { minimumFractionDigits: 2 })}
                    </span>
                  ),
                },
                {
                  header: 'EPF (8%)',
                  numeric: true,
                  render: (r) => (
                    <span style={{ fontVariantNumeric: 'tabular-nums', color: '#EF4444' }}>
                      -{r.totalStatutoryEmployee.toLocaleString(undefined, { minimumFractionDigits: 2 })}
                    </span>
                  ),
                },
                {
                  header: 'APIT Tax',
                  numeric: true,
                  render: (r) => (
                    <span style={{ fontVariantNumeric: 'tabular-nums', color: '#F59E0B' }}>
                      {r.taxWithheld > 0
                        ? `-${r.taxWithheld.toLocaleString(undefined, { minimumFractionDigits: 2 })}`
                        : '0.00'}
                    </span>
                  ),
                },
                {
                  header: 'Net Pay',
                  numeric: true,
                  render: (r) => (
                    <strong
                      style={{
                        fontVariantNumeric: 'tabular-nums',
                        fontSize: '0.9375rem',
                        color: 'var(--color-brand-primary)',
                      }}
                    >
                      LKR {r.netPay.toLocaleString(undefined, { minimumFractionDigits: 2 })}
                    </strong>
                  ),
                },
                {
                  header: 'Status',
                  render: (r) => (
                    <Badge tone={r.paymentStatus === 'PAID' ? 'success' : 'neutral'}>
                      {r.paymentStatus}
                    </Badge>
                  ),
                },
                {
                  header: 'Action',
                  render: (r) => (
                    <Button
                      variant="ghost"
                      style={{ padding: '0 0.5rem', minHeight: '32px', fontSize: '0.8125rem' }}
                      onClick={() => setInspectingResultId(r.id)}
                    >
                      Inspect
                    </Button>
                  ),
                },
              ]}
            />
          )}
        </div>
      </Card>

      {/* --------------------------------------------------------------------- */}
      {/* Payslip Inspector Drawer                                              */}
      {/* --------------------------------------------------------------------- */}
      <Drawer
        isOpen={Boolean(inspectingResultId)}
        onClose={() => setInspectingResultId(null)}
        title={
          payslipDetailsQuery.data?.result
            ? `Payslip: ${payslipDetailsQuery.data.result.employeeName} (${payslipDetailsQuery.data.result.employeeCode})`
            : 'Employee Payslip'
        }
        actions={
          <Button variant="secondary" onClick={() => setInspectingResultId(null)}>
            Close Payslip
          </Button>
        }
      >
        {payslipDetailsQuery.isPending && <LoadingState label="Loading itemized payslip breakdown…" />}
        {payslipDetailsQuery.isError && (
          <QueryErrorState
            error={payslipDetailsQuery.error}
            onRetry={() => void payslipDetailsQuery.refetch()}
          />
        )}

        {payslipDetailsQuery.data && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
            {/* Header info */}
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(2, 1fr)',
                gap: '0.75rem',
                padding: '0.875rem',
                background: 'var(--color-surface-variant)',
                borderRadius: '8px',
                fontSize: '0.8125rem',
              }}
            >
              <div>
                <span style={{ color: 'var(--color-on-surface-muted)' }}>Department: </span>
                <strong>{payslipDetailsQuery.data.result.department}</strong>
              </div>
              <div>
                <span style={{ color: 'var(--color-on-surface-muted)' }}>Designation: </span>
                <strong>{payslipDetailsQuery.data.result.designation}</strong>
              </div>
              <div>
                <span style={{ color: 'var(--color-on-surface-muted)' }}>Currency: </span>
                <strong>{payslipDetailsQuery.data.result.currency}</strong>
              </div>
              <div>
                <span style={{ color: 'var(--color-on-surface-muted)' }}>Payment Status: </span>
                <Badge tone={payslipDetailsQuery.data.result.paymentStatus === 'PAID' ? 'success' : 'neutral'}>
                  {payslipDetailsQuery.data.result.paymentStatus}
                </Badge>
              </div>
            </div>

            {/* Gross to Net Mathematical Banner */}
            <div
              style={{
                padding: '0.875rem',
                background: 'var(--color-brand-primary-container, rgba(99, 102, 241, 0.08))',
                border: '1px solid var(--color-brand-primary)',
                borderRadius: '8px',
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
              }}
            >
              <div>
                <span style={{ fontSize: '0.75rem', color: 'var(--color-on-surface-muted)' }}>
                  Take-Home Net Pay
                </span>
                <div
                  style={{
                    fontSize: '1.5rem',
                    fontWeight: 800,
                    color: 'var(--color-brand-primary)',
                    fontVariantNumeric: 'tabular-nums',
                  }}
                >
                  LKR {payslipDetailsQuery.data.result.netPay.toLocaleString(undefined, { minimumFractionDigits: 2 })}
                </div>
              </div>

              <div style={{ textAlign: 'right', fontSize: '0.75rem', color: 'var(--color-on-surface-muted)' }}>
                <div>Gross: LKR {payslipDetailsQuery.data.result.grossPay.toLocaleString()}</div>
                <div>Deductions: LKR {(payslipDetailsQuery.data.result.totalStatutoryEmployee + payslipDetailsQuery.data.result.taxWithheld + payslipDetailsQuery.data.result.totalVoluntaryDeductions).toLocaleString()}</div>
              </div>
            </div>

            {/* Itemized Lines Grouped */}
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
              <h4 style={{ margin: 0, fontSize: '0.9375rem', fontWeight: 600 }}>
                Itemized Lines & Calculation Traces
              </h4>

              <div
                style={{
                  border: '1px solid var(--color-outline)',
                  borderRadius: '6px',
                  overflow: 'hidden',
                }}
              >
                <table className="table" style={{ fontSize: '0.8125rem' }}>
                  <caption className="sr-only">Itemized payslip breakdown</caption>
                  <thead>
                    <tr>
                      <th scope="col">Line Category</th>
                      <th scope="col">Description</th>
                      <th scope="col" className="numeric">Amount</th>
                    </tr>
                  </thead>
                  <tbody>
                    {payslipDetailsQuery.data.lines.map((line: PayrollResultLine) => {
                      const isEarning = line.lineCategory === 'EARNING'
                      const isEmployer = line.lineCategory === 'EMPLOYER_CONTRIBUTION'
                      const color = isEarning ? '#10B981' : isEmployer ? '#3B82F6' : '#EF4444'

                      return (
                        <tr key={line.id}>
                          <td>
                            <Badge
                              tone={
                                isEarning
                                  ? 'success'
                                  : isEmployer
                                    ? 'neutral'
                                    : 'danger'
                              }
                            >
                              {line.lineCategory}
                            </Badge>
                          </td>
                          <td>
                            <div style={{ fontWeight: 500 }}>{line.itemName}</div>
                            {line.calculationTrace && (
                              <div
                                style={{
                                  fontSize: '0.6875rem',
                                  color: 'var(--color-on-surface-muted)',
                                  fontFamily: 'monospace',
                                  marginTop: '2px',
                                }}
                              >
                                {line.calculationTrace}
                              </div>
                            )}
                          </td>
                          <td className="numeric">
                            <span style={{ fontWeight: 700, color, fontVariantNumeric: 'tabular-nums' }}>
                              {isEarning ? '+' : isEmployer ? '·' : '-'}
                              {line.amount.toLocaleString(undefined, { minimumFractionDigits: 2 })}
                            </span>
                          </td>
                        </tr>
                      )
                    })}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        )}
      </Drawer>

      {/* --------------------------------------------------------------------- */}
      {/* Bank Advice File Export Modal                                         */}
      {/* --------------------------------------------------------------------- */}
      <Modal
        isOpen={isBankAdviceModalOpen}
        onClose={() => setIsBankAdviceModalOpen(false)}
        title="Download Bank Advice Disbursement File"
        size="large"
        actions={
          <>
            <Button variant="secondary" onClick={() => setIsBankAdviceModalOpen(false)}>
              Close
            </Button>
            <Button
              variant="primary"
              disabled={!generatedAdvice}
              onClick={handleDownloadFile}
            >
              Download {generatedAdvice?.filename || 'File'}
            </Button>
          </>
        }
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          <p style={{ margin: 0, fontSize: '0.875rem', color: 'var(--color-on-surface-muted)' }}>
            Generate and export cryptographically hashed bank advice advice files for electronic clearing and automated funds disbursement.
          </p>

          <div style={{ display: 'grid', gridTemplateColumns: '1.2fr 1fr 1fr', gap: '1rem' }}>
            <div className="field">
              <label className="field__label" htmlFor="advice-format">
                File Clearing Format
              </label>
              <select
                id="advice-format"
                className="field__input"
                value={adviceFormat}
                onChange={(e) => {
                  const val = e.target.value as 'CSV_STANDARD' | 'ACH_NACHA'
                  setAdviceFormat(val)
                  bankAdviceMutation.mutate({
                    format: val,
                    companyAccount,
                    paymentDate,
                  })
                }}
              >
                <option value="CSV_STANDARD">Standard CSV (Commercial Bank of Ceylon / Global)</option>
                <option value="ACH_NACHA">NACHA ACH (Strict 94-Char Fixed-Width Block 10)</option>
              </select>
            </div>

            <div className="field">
              <label className="field__label" htmlFor="disburse-account">
                Disbursement Account
              </label>
              <input
                id="disburse-account"
                className="field__input"
                type="text"
                value={companyAccount}
                onChange={(e) => setCompanyAccount(e.target.value)}
              />
            </div>

            <div className="field">
              <label className="field__label" htmlFor="disburse-date">
                Disbursement Date
              </label>
              <input
                id="disburse-date"
                className="field__input"
                type="date"
                value={paymentDate}
                onChange={(e) => {
                  setPaymentDate(e.target.value)
                  bankAdviceMutation.mutate({
                    format: adviceFormat,
                    companyAccount,
                    paymentDate: e.target.value,
                  })
                }}
              />
            </div>
          </div>

          {bankAdviceMutation.isPending && <LoadingState label="Generating formatted bank advice file…" />}

          {generatedAdvice && (
            <div
              style={{
                background: 'var(--color-surface-variant)',
                border: '1px solid var(--color-outline)',
                borderRadius: '8px',
                padding: '1rem',
                display: 'flex',
                flexDirection: 'column',
                gap: '0.75rem',
              }}
            >
              {/* Batch Hash & Metrics */}
              <div
                style={{
                  display: 'grid',
                  gridTemplateColumns: 'repeat(3, 1fr)',
                  gap: '0.5rem',
                  fontSize: '0.8125rem',
                }}
              >
                <div>
                  <span style={{ color: 'var(--color-on-surface-muted)' }}>Batch Records: </span>
                  <strong>{generatedAdvice.totalRecords} employees</strong>
                </div>
                <div>
                  <span style={{ color: 'var(--color-on-surface-muted)' }}>Total Amount: </span>
                  <strong>LKR {generatedAdvice.totalAmount.toLocaleString(undefined, { minimumFractionDigits: 2 })}</strong>
                </div>
                <div>
                  <span style={{ color: 'var(--color-on-surface-muted)' }}>Filename: </span>
                  <strong>{generatedAdvice.filename}</strong>
                </div>
              </div>

              {/* SHA-256 Checksum */}
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '0.5rem',
                  fontSize: '0.75rem',
                  color: 'var(--color-on-surface-muted)',
                }}
              >
                <span>SHA-256 Batch Hash: </span>
                <code
                  style={{
                    background: 'var(--color-surface)',
                    padding: '2px 6px',
                    borderRadius: '4px',
                    fontFamily: 'monospace',
                    fontSize: '0.6875rem',
                  }}
                >
                  {generatedAdvice.batchHash}
                </code>
              </div>

              {/* File Content Preview */}
              <div style={{ marginTop: '0.25rem' }}>
                <span style={{ fontSize: '0.75rem', fontWeight: 600, color: 'var(--color-on-surface-muted)' }}>
                  File Content Preview:
                </span>
                <pre
                  style={{
                    margin: '0.25rem 0 0 0',
                    padding: '0.625rem',
                    background: 'var(--color-surface)',
                    border: '1px solid var(--color-outline)',
                    borderRadius: '4px',
                    fontSize: '0.6875rem',
                    fontFamily: 'monospace',
                    maxHeight: '140px',
                    overflowY: 'auto',
                    whiteSpace: 'pre-wrap',
                    wordBreak: 'break-all',
                  }}
                >
                  {generatedAdvice.content.slice(0, 800)}
                  {generatedAdvice.content.length > 800 ? '\n… [truncated]' : ''}
                </pre>
              </div>
            </div>
          )}
        </div>
      </Modal>
      {/* Modal: Approve Payroll Run */}
      <Modal
        isOpen={isApproveRunModalOpen}
        onClose={() => setIsApproveRunModalOpen(false)}
        title="Approve Payroll Execution Run"
        size="medium"
        actions={
          <div className="action-bar">
            <Button variant="ghost" onClick={() => setIsApproveRunModalOpen(false)}>
              Cancel
            </Button>
            <Button
              variant="primary"
              loading={approveMutation.isPending}
              onClick={() => {
                if (activeRun) {
                  approveMutation.mutate(activeRun.id)
                  setIsApproveRunModalOpen(false)
                }
              }}
            >
              Authorize & Approve Run
            </Button>
          </div>
        }
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          <p style={{ margin: 0, color: 'var(--color-on-surface-muted)' }}>
            Confirming authorization for batch run <strong>{activeRun?.id}</strong>. This validates all statutory deductions and releases payslips for administrative sign-off.
          </p>
          {activeRun && (
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: '0.75rem', background: 'var(--color-surface-container)', padding: '1rem', borderRadius: '8px' }}>
              <div>
                <span className="text-xs text-muted">Eligible Employees:</span>
                <div className="font-semibold">{activeRun.totalEmployees}</div>
              </div>
              <div>
                <span className="text-xs text-muted">Total Gross Disbursement:</span>
                <div className="font-semibold">{activeRun.totalGross.toLocaleString(undefined, { minimumFractionDigits: 2 })} LKR</div>
              </div>
              <div>
                <span className="text-xs text-muted">Statutory Contributions (EPF/ETF):</span>
                <div className="font-semibold">{(activeRun.totalStatutoryEmployee + activeRun.totalStatutoryEmployer).toLocaleString(undefined, { minimumFractionDigits: 2 })} LKR</div>
              </div>
              <div>
                <span className="text-xs text-muted">Net Bank Payable:</span>
                <div className="font-semibold" style={{ color: '#16a34a' }}>{activeRun.totalNet.toLocaleString(undefined, { minimumFractionDigits: 2 })} LKR</div>
              </div>
            </div>
          )}
        </div>
      </Modal>

      {/* Modal: Reject / Return Run */}
      <Modal
        isOpen={isRejectRunModalOpen}
        onClose={() => setIsRejectRunModalOpen(false)}
        title="Reject / Return Payroll Run"
        size="small"
        actions={
          <div className="action-bar">
            <Button variant="ghost" onClick={() => setIsRejectRunModalOpen(false)}>
              Cancel
            </Button>
            <Button
              variant="danger"
              loading={rejectMutation.isPending}
              onClick={() => {
                if (activeRun) {
                  rejectMutation.mutate({ runId: activeRun.id, reason: rejectReason })
                }
              }}
            >
              Confirm Rejection & Reset
            </Button>
          </div>
        }
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          <p style={{ margin: 0, color: 'var(--color-on-surface-muted)' }}>
            Returning this run to <strong>DRAFT</strong> allows adjusting employee attendance records, bonuses, or loans before re-calculation.
          </p>
          <div className="field">
            <label className="field__label" htmlFor="payroll-reject-reason">
              Reason for Rejection / Recalculation
            </label>
            <textarea
              id="payroll-reject-reason"
              className="field__input"
              rows={3}
              placeholder="e.g. Unapproved overtime in Engineering, missing unpaid leave deductions..."
              value={rejectReason}
              onChange={(e) => setRejectReason(e.target.value)}
            />
          </div>
        </div>
      </Modal>

      {/* Modal: Commit & Publish Run */}
      <Modal
        isOpen={isCommitRunModalOpen}
        onClose={() => setIsCommitRunModalOpen(false)}
        title="Commit & Publish Final Payroll"
        size="small"
        actions={
          <div className="action-bar">
            <Button variant="ghost" onClick={() => setIsCommitRunModalOpen(false)}>
              Cancel
            </Button>
            <Button
              variant="primary"
              loading={commitMutation.isPending}
              onClick={() => {
                if (activeRun) {
                  commitMutation.mutate(activeRun.id)
                }
              }}
            >
              Commit & Finalize
            </Button>
          </div>
        }
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          <p style={{ margin: 0, color: 'var(--color-on-surface-muted)' }}>
            ⚠️ <strong>Irreversible Action</strong>: Committing this payroll run permanently locks the salary register, triggers GL journal sync, and makes digital payslips visible in the Employee Mobile & Self-Service portals.
          </p>
        </div>
      </Modal>
    </div>
  )
}
