import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useState } from 'react'
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
  type EpfCFormResponse,
  type EtfScheduleResponse,
  type PayGroup,
  type PayPeriod,
  type PayrollResult,
  type PayrollResultLine,
  type T10CertificateData,
} from '@/lib/api'
import { OfficialPayslipModal } from '@/components/payslip/OfficialPayslipModal'
import { BatchPayslipsPrintModal } from '@/components/payslip/BatchPayslipsPrintModal'

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
  const [selectedOfficialPayslipResultId, setSelectedOfficialPayslipResultId] = useState<string | null>(null)
  const [isBatchPayslipsModalOpen, setIsBatchPayslipsModalOpen] = useState(false)

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

  // -------------------------------------------------------------------------
  // Statutory Returns (EPF Form C, ETF, T-10 Tax Certificate) State
  // -------------------------------------------------------------------------
  const [isStatutoryModalOpen, setIsStatutoryModalOpen] = useState(false)
  const [activeStatutoryTab, setActiveStatutoryTab] = useState<'EPF' | 'ETF' | 'T10'>('EPF')
  const [selectedT10EmployeeId, setSelectedT10EmployeeId] = useState<string>('')
  const [epfMemberFilter, setEpfMemberFilter] = useState('')
  const [etfMemberFilter, setEtfMemberFilter] = useState('')

  const epfCFormQuery = useQuery({
    queryKey: ['payroll', 'epf-cform', activeRun?.id],
    queryFn: () => payrollApi.getEpfCForm(activeRun!.id),
    enabled: Boolean(activeRun?.id && isStatutoryModalOpen && activeStatutoryTab === 'EPF'),
  })

  const etfScheduleQuery = useQuery({
    queryKey: ['payroll', 'etf-schedule', activeRun?.id],
    queryFn: () => payrollApi.getEtfSchedule(activeRun!.id),
    enabled: Boolean(activeRun?.id && isStatutoryModalOpen && activeStatutoryTab === 'ETF'),
  })

  const t10CertificateQuery = useQuery({
    queryKey: ['payroll', 't10-certificate', activeRun?.id, selectedT10EmployeeId],
    queryFn: () => payrollApi.getT10Certificate(activeRun!.id, selectedT10EmployeeId),
    enabled: Boolean(
      activeRun?.id && isStatutoryModalOpen && activeStatutoryTab === 'T10' && selectedT10EmployeeId,
    ),
  })

  useEffect(() => {
    const firstEmp = resultsQuery.data?.results[0]
    if (!selectedT10EmployeeId && firstEmp) {
      setSelectedT10EmployeeId(firstEmp.employeeId)
    }
  }, [resultsQuery.data, selectedT10EmployeeId])

  const handleDownloadEpfCForm = () => {
    if (!epfCFormQuery.data?.electronicFile) return
    const file = epfCFormQuery.data.electronicFile
    const blob = new Blob([file.content], { type: file.mimeType })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = file.filename
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(url)
  }

  const handleDownloadEtfSchedule = () => {
    if (!etfScheduleQuery.data?.electronicFile) return
    const file = etfScheduleQuery.data.electronicFile
    const blob = new Blob([file.content], { type: file.mimeType })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = file.filename
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
            <Button
              variant="secondary"
              onClick={() => {
                setIsStatutoryModalOpen(true)
                setActiveStatutoryTab('EPF')
              }}
            >
              Statutory Returns (EPF / ETF / T-10)
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

            <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
              <span style={{ fontSize: '0.8125rem', color: 'var(--color-on-surface-muted)' }}>
                Showing {resultsQuery.data?.results.length ?? 0} payslip records
              </span>
              <Button
                variant="secondary"
                style={{ minHeight: '34px', fontSize: '0.8125rem' }}
                onClick={() => setIsBatchPayslipsModalOpen(true)}
                disabled={!activeRun?.id || (resultsQuery.data?.results.length ?? 0) === 0}
              >
                🖨️ Batch Export Payslips ({resultsQuery.data?.results.length ?? 0})
              </Button>
            </div>
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
                    <div style={{ display: 'flex', alignItems: 'center', gap: '0.375rem' }}>
                      <Button
                        variant="secondary"
                        style={{ padding: '0 0.5rem', minHeight: '32px', fontSize: '0.8125rem' }}
                        onClick={() => setSelectedOfficialPayslipResultId(r.id)}
                      >
                        📄 Payslip
                      </Button>
                      <Button
                        variant="ghost"
                        style={{ padding: '0 0.5rem', minHeight: '32px', fontSize: '0.8125rem' }}
                        onClick={() => setInspectingResultId(r.id)}
                      >
                        Inspect
                      </Button>
                    </div>
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
            <Button
              variant="primary"
              style={{ width: '100%' }}
              onClick={() => setSelectedOfficialPayslipResultId(inspectingResultId)}
            >
              📄 Open Official PDF Payslip Document
            </Button>

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

      {/* ===================================================================== */}
      {/* Modal: Statutory Compliance & Tax Returns Engine                      */}
      {/* ===================================================================== */}
      <Modal
        isOpen={isStatutoryModalOpen}
        onClose={() => setIsStatutoryModalOpen(false)}
        title="Statutory Returns & Tax Certification Engine"
        size="large"
        actions={
          <div
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              width: '100%',
              flexWrap: 'wrap',
              gap: '0.75rem',
            }}
          >
            <div style={{ fontSize: '0.8125rem', color: 'var(--color-on-surface-muted)' }}>
              {activeStatutoryTab === 'EPF' && (
                <span>Central Bank of Sri Lanka · Form C Electronic Remittance Schedule</span>
              )}
              {activeStatutoryTab === 'ETF' && (
                <span>Employees' Trust Fund Board · Monthly 3% Remittance Schedule</span>
              )}
              {activeStatutoryTab === 'T10' && (
                <span>Department of Inland Revenue · Section 83 APIT Certificate</span>
              )}
            </div>
            <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
              {activeStatutoryTab === 'EPF' && (
                <Button
                  variant="primary"
                  onClick={handleDownloadEpfCForm}
                  disabled={!epfCFormQuery.data}
                >
                  📥 Download Electronic C-Form (CSV)
                </Button>
              )}
              {activeStatutoryTab === 'ETF' && (
                <Button
                  variant="primary"
                  onClick={handleDownloadEtfSchedule}
                  disabled={!etfScheduleQuery.data}
                >
                  📥 Download ETF Schedule (CSV)
                </Button>
              )}
              {activeStatutoryTab === 'T10' && (
                <Button
                  variant="primary"
                  onClick={() => window.print()}
                  disabled={!t10CertificateQuery.data}
                >
                  🖨️ Print / Save Certificate
                </Button>
              )}
              <Button variant="ghost" onClick={() => setIsStatutoryModalOpen(false)}>
                Close
              </Button>
            </div>
          </div>
        }
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
          {/* Tab Navigation Pill Bar */}
          <div
            style={{
              display: 'flex',
              gap: '0.5rem',
              borderBottom: '1px solid var(--color-outline)',
              paddingBottom: '0.75rem',
              flexWrap: 'wrap',
            }}
          >
            <button
              type="button"
              className={`btn ${activeStatutoryTab === 'EPF' ? 'btn--primary' : 'btn--ghost'}`}
              style={{ fontSize: '0.875rem' }}
              onClick={() => setActiveStatutoryTab('EPF')}
            >
              🏛️ Monthly EPF Form C
            </button>
            <button
              type="button"
              className={`btn ${activeStatutoryTab === 'ETF' ? 'btn--primary' : 'btn--ghost'}`}
              style={{ fontSize: '0.875rem' }}
              onClick={() => setActiveStatutoryTab('ETF')}
            >
              🏦 Monthly ETF Schedule
            </button>
            <button
              type="button"
              className={`btn ${activeStatutoryTab === 'T10' ? 'btn--primary' : 'btn--ghost'}`}
              style={{ fontSize: '0.875rem' }}
              onClick={() => setActiveStatutoryTab('T10')}
            >
              📜 Annual Tax Certificate (Form T-10)
            </button>
          </div>

          {/* ----------------------------------------------------------------- */}
          {/* TAB 1: EPF Form C                                                 */}
          {/* ----------------------------------------------------------------- */}
          {activeStatutoryTab === 'EPF' && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
              {epfCFormQuery.isPending && (
                <LoadingState label="Compiling Central Bank EPF Form C schedule..." />
              )}
              {epfCFormQuery.isError && (
                <QueryErrorState
                  error={epfCFormQuery.error}
                  onRetry={() => void epfCFormQuery.refetch()}
                />
              )}
              {epfCFormQuery.data && (
                <>
                  {/* Employer Header Strip */}
                  <div
                    style={{
                      display: 'flex',
                      justifyContent: 'space-between',
                      alignItems: 'center',
                      flexWrap: 'wrap',
                      gap: '0.75rem',
                      background: 'var(--color-surface-container)',
                      padding: '0.875rem 1rem',
                      borderRadius: '8px',
                      border: '1px solid var(--color-outline)',
                    }}
                  >
                    <div>
                      <div style={{ fontWeight: 700, fontSize: '0.9375rem' }}>
                        {epfCFormQuery.data.employerName}
                      </div>
                      <div style={{ fontSize: '0.75rem', color: 'var(--color-on-surface-muted)' }}>
                        {epfCFormQuery.data.employerAddress}
                      </div>
                    </div>
                    <div style={{ display: 'flex', gap: '1rem', fontSize: '0.8125rem' }}>
                      <div>
                        <span style={{ color: 'var(--color-on-surface-muted)' }}>EPF Reg No: </span>
                        <strong>{epfCFormQuery.data.employerRegistrationNo}</strong>
                      </div>
                      <div>
                        <span style={{ color: 'var(--color-on-surface-muted)' }}>Month: </span>
                        <strong>{epfCFormQuery.data.contributionMonth}</strong>
                      </div>
                      <div>
                        <span style={{ color: 'var(--color-on-surface-muted)' }}>Due Date: </span>
                        <strong>{epfCFormQuery.data.paymentDueDate}</strong>
                      </div>
                      <div>
                        <span style={{ color: 'var(--color-on-surface-muted)' }}>Ref: </span>
                        <code>{epfCFormQuery.data.remittanceRef}</code>
                      </div>
                    </div>
                  </div>

                  {/* Summary KPI Grid */}
                  <div
                    style={{
                      display: 'grid',
                      gridTemplateColumns: 'repeat(auto-fit, minmax(170px, 1fr))',
                      gap: '0.75rem',
                    }}
                  >
                    <div
                      style={{
                        padding: '0.75rem',
                        background: 'var(--color-surface)',
                        border: '1px solid var(--color-outline)',
                        borderRadius: '6px',
                      }}
                    >
                      <span style={{ fontSize: '0.6875rem', color: 'var(--color-on-surface-muted)' }}>
                        Contributory Gross Base
                      </span>
                      <div style={{ fontSize: '1.0625rem', fontWeight: 700, marginTop: '2px' }}>
                        LKR {epfCFormQuery.data.totalContributoryEarnings.toLocaleString(undefined, { minimumFractionDigits: 2 })}
                      </div>
                    </div>
                    <div
                      style={{
                        padding: '0.75rem',
                        background: 'var(--color-surface)',
                        border: '1px solid var(--color-outline)',
                        borderRadius: '6px',
                      }}
                    >
                      <span style={{ fontSize: '0.6875rem', color: 'var(--color-on-surface-muted)' }}>
                        Member Contribution (8%)
                      </span>
                      <div style={{ fontSize: '1.0625rem', fontWeight: 700, marginTop: '2px' }}>
                        LKR {epfCFormQuery.data.totalMemberShare8.toLocaleString(undefined, { minimumFractionDigits: 2 })}
                      </div>
                    </div>
                    <div
                      style={{
                        padding: '0.75rem',
                        background: 'var(--color-surface)',
                        border: '1px solid var(--color-outline)',
                        borderRadius: '6px',
                      }}
                    >
                      <span style={{ fontSize: '0.6875rem', color: 'var(--color-on-surface-muted)' }}>
                        Employer Contribution (12%)
                      </span>
                      <div style={{ fontSize: '1.0625rem', fontWeight: 700, marginTop: '2px' }}>
                        LKR {epfCFormQuery.data.totalEmployerShare12.toLocaleString(undefined, { minimumFractionDigits: 2 })}
                      </div>
                    </div>
                    <div
                      style={{
                        padding: '0.75rem',
                        background: 'rgba(22, 163, 74, 0.08)',
                        border: '1px solid rgba(22, 163, 74, 0.3)',
                        borderRadius: '6px',
                      }}
                    >
                      <span style={{ fontSize: '0.6875rem', color: '#16a34a', fontWeight: 600 }}>
                        Total EPF Remittance (20%)
                      </span>
                      <div style={{ fontSize: '1.0625rem', fontWeight: 700, marginTop: '2px', color: '#16a34a' }}>
                        LKR {epfCFormQuery.data.totalRemittance20.toLocaleString(undefined, { minimumFractionDigits: 2 })}
                      </div>
                    </div>
                    <div
                      style={{
                        padding: '0.75rem',
                        background: 'var(--color-surface)',
                        border: '1px solid var(--color-outline)',
                        borderRadius: '6px',
                      }}
                    >
                      <span style={{ fontSize: '0.6875rem', color: 'var(--color-on-surface-muted)' }}>
                        Contributing Members
                      </span>
                      <div style={{ fontSize: '1.0625rem', fontWeight: 700, marginTop: '2px' }}>
                        {epfCFormQuery.data.memberCount} Members
                      </div>
                    </div>
                  </div>

                  {/* Filter & Member Schedule Table */}
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: '0.25rem' }}>
                    <input
                      type="search"
                      className="field__input"
                      style={{ minHeight: '34px', width: '280px', fontSize: '0.8125rem' }}
                      placeholder="Filter member by name, NIC, or EPF #..."
                      value={epfMemberFilter}
                      onChange={(e) => setEpfMemberFilter(e.target.value)}
                    />
                    <span style={{ fontSize: '0.75rem', color: 'var(--color-on-surface-muted)' }}>
                      Form C electronic records ready for Central Bank gateway upload
                    </span>
                  </div>

                  <div
                    style={{
                      maxHeight: '380px',
                      overflowY: 'auto',
                      border: '1px solid var(--color-outline)',
                      borderRadius: '6px',
                    }}
                  >
                    <table className="table" style={{ fontSize: '0.8125rem', width: '100%' }}>
                      <thead>
                        <tr>
                          <th scope="col">EPF Member #</th>
                          <th scope="col">NIC / National ID</th>
                          <th scope="col">Member Name</th>
                          <th scope="col">Dept</th>
                          <th scope="col" className="numeric">Contributory Base</th>
                          <th scope="col" className="numeric">Member 8%</th>
                          <th scope="col" className="numeric">Employer 12%</th>
                          <th scope="col" className="numeric">Total 20%</th>
                          <th scope="col">Status</th>
                        </tr>
                      </thead>
                      <tbody>
                        {epfCFormQuery.data.members
                          .filter(
                            (m) =>
                              !epfMemberFilter ||
                              m.fullName.toLowerCase().includes(epfMemberFilter.toLowerCase()) ||
                              m.nic.toLowerCase().includes(epfMemberFilter.toLowerCase()) ||
                              m.memberNo.toLowerCase().includes(epfMemberFilter.toLowerCase()),
                          )
                          .map((m) => (
                            <tr key={m.memberNo}>
                              <td>
                                <code>{m.memberNo}</code>
                              </td>
                              <td>{m.nic}</td>
                              <td>
                                <strong>{m.initialsAndSurname}</strong>
                              </td>
                              <td>{m.department}</td>
                              <td className="numeric">
                                LKR {m.contributoryEarnings.toLocaleString(undefined, { minimumFractionDigits: 2 })}
                              </td>
                              <td className="numeric">
                                LKR {m.memberShare8.toLocaleString(undefined, { minimumFractionDigits: 2 })}
                              </td>
                              <td className="numeric">
                                LKR {m.employerShare12.toLocaleString(undefined, { minimumFractionDigits: 2 })}
                              </td>
                              <td className="numeric" style={{ fontWeight: 700, color: '#16a34a' }}>
                                LKR {m.totalContribution20.toLocaleString(undefined, { minimumFractionDigits: 2 })}
                              </td>
                              <td>
                                <Badge
                                  tone={
                                    m.status === 'ACTIVE'
                                      ? 'success'
                                      : m.status === 'NEW'
                                        ? 'warning'
                                        : 'neutral'
                                  }
                                >
                                  {m.status}
                                </Badge>
                              </td>
                            </tr>
                          ))}
                      </tbody>
                    </table>
                  </div>
                </>
              )}
            </div>
          )}

          {/* ----------------------------------------------------------------- */}
          {/* TAB 2: ETF Schedule                                               */}
          {/* ----------------------------------------------------------------- */}
          {activeStatutoryTab === 'ETF' && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
              {etfScheduleQuery.isPending && (
                <LoadingState label="Compiling ETF 3% remittance schedule..." />
              )}
              {etfScheduleQuery.isError && (
                <QueryErrorState
                  error={etfScheduleQuery.error}
                  onRetry={() => void etfScheduleQuery.refetch()}
                />
              )}
              {etfScheduleQuery.data && (
                <>
                  {/* Employer Header Strip */}
                  <div
                    style={{
                      display: 'flex',
                      justifyContent: 'space-between',
                      alignItems: 'center',
                      flexWrap: 'wrap',
                      gap: '0.75rem',
                      background: 'var(--color-surface-container)',
                      padding: '0.875rem 1rem',
                      borderRadius: '8px',
                      border: '1px solid var(--color-outline)',
                    }}
                  >
                    <div>
                      <div style={{ fontWeight: 700, fontSize: '0.9375rem' }}>
                        {etfScheduleQuery.data.employerName}
                      </div>
                      <div style={{ fontSize: '0.75rem', color: 'var(--color-on-surface-muted)' }}>
                        Employees' Trust Fund Board (ETFB) of Sri Lanka
                      </div>
                    </div>
                    <div style={{ display: 'flex', gap: '1rem', fontSize: '0.8125rem' }}>
                      <div>
                        <span style={{ color: 'var(--color-on-surface-muted)' }}>ETF Reg No: </span>
                        <strong>{etfScheduleQuery.data.employerRegistrationNo}</strong>
                      </div>
                      <div>
                        <span style={{ color: 'var(--color-on-surface-muted)' }}>Month: </span>
                        <strong>{etfScheduleQuery.data.contributionMonth}</strong>
                      </div>
                      <div>
                        <span style={{ color: 'var(--color-on-surface-muted)' }}>Statutory Rate: </span>
                        <strong>3.00% (Employer Paid)</strong>
                      </div>
                    </div>
                  </div>

                  {/* Summary KPI Grid */}
                  <div
                    style={{
                      display: 'grid',
                      gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
                      gap: '0.75rem',
                    }}
                  >
                    <div
                      style={{
                        padding: '0.75rem',
                        background: 'var(--color-surface)',
                        border: '1px solid var(--color-outline)',
                        borderRadius: '6px',
                      }}
                    >
                      <span style={{ fontSize: '0.6875rem', color: 'var(--color-on-surface-muted)' }}>
                        Contributory Earnings Base
                      </span>
                      <div style={{ fontSize: '1.125rem', fontWeight: 700, marginTop: '2px' }}>
                        LKR {etfScheduleQuery.data.totalContributoryEarnings.toLocaleString(undefined, { minimumFractionDigits: 2 })}
                      </div>
                    </div>
                    <div
                      style={{
                        padding: '0.75rem',
                        background: 'rgba(37, 99, 235, 0.08)',
                        border: '1px solid rgba(37, 99, 235, 0.3)',
                        borderRadius: '6px',
                      }}
                    >
                      <span style={{ fontSize: '0.6875rem', color: '#2563eb', fontWeight: 600 }}>
                        Total Employer ETF Remittance (3%)
                      </span>
                      <div style={{ fontSize: '1.125rem', fontWeight: 700, marginTop: '2px', color: '#2563eb' }}>
                        LKR {etfScheduleQuery.data.totalEmployerContribution3.toLocaleString(undefined, { minimumFractionDigits: 2 })}
                      </div>
                    </div>
                    <div
                      style={{
                        padding: '0.75rem',
                        background: 'var(--color-surface)',
                        border: '1px solid var(--color-outline)',
                        borderRadius: '6px',
                      }}
                    >
                      <span style={{ fontSize: '0.6875rem', color: 'var(--color-on-surface-muted)' }}>
                        Enrolled Members
                      </span>
                      <div style={{ fontSize: '1.125rem', fontWeight: 700, marginTop: '2px' }}>
                        {etfScheduleQuery.data.memberCount} Members
                      </div>
                    </div>
                  </div>

                  {/* Filter & Member Schedule Table */}
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: '0.25rem' }}>
                    <input
                      type="search"
                      className="field__input"
                      style={{ minHeight: '34px', width: '280px', fontSize: '0.8125rem' }}
                      placeholder="Filter ETF schedule by name, NIC, or EPF #..."
                      value={etfMemberFilter}
                      onChange={(e) => setEtfMemberFilter(e.target.value)}
                    />
                    <span style={{ fontSize: '0.75rem', color: 'var(--color-on-surface-muted)' }}>
                      Form II Remittance format compliant with ETFB e-Services
                    </span>
                  </div>

                  <div
                    style={{
                      maxHeight: '380px',
                      overflowY: 'auto',
                      border: '1px solid var(--color-outline)',
                      borderRadius: '6px',
                    }}
                  >
                    <table className="table" style={{ fontSize: '0.8125rem', width: '100%' }}>
                      <thead>
                        <tr>
                          <th scope="col">Member No</th>
                          <th scope="col">NIC / National ID</th>
                          <th scope="col">Employee Name</th>
                          <th scope="col">Department</th>
                          <th scope="col" className="numeric">Contributory Base</th>
                          <th scope="col" className="numeric">ETF 3% Contribution</th>
                        </tr>
                      </thead>
                      <tbody>
                        {etfScheduleQuery.data.members
                          .filter(
                            (m) =>
                              !etfMemberFilter ||
                              m.fullName.toLowerCase().includes(etfMemberFilter.toLowerCase()) ||
                              m.nic.toLowerCase().includes(etfMemberFilter.toLowerCase()) ||
                              m.memberNo.toLowerCase().includes(etfMemberFilter.toLowerCase()),
                          )
                          .map((m) => (
                            <tr key={m.memberNo}>
                              <td>
                                <code>{m.memberNo}</code>
                              </td>
                              <td>{m.nic}</td>
                              <td>
                                <strong>{m.fullName}</strong>
                              </td>
                              <td>{m.department}</td>
                              <td className="numeric">
                                LKR {m.contributoryEarnings.toLocaleString(undefined, { minimumFractionDigits: 2 })}
                              </td>
                              <td className="numeric" style={{ fontWeight: 700, color: '#2563eb' }}>
                                LKR {m.employerContribution3.toLocaleString(undefined, { minimumFractionDigits: 2 })}
                              </td>
                            </tr>
                          ))}
                      </tbody>
                    </table>
                  </div>
                </>
              )}
            </div>
          )}

          {/* ----------------------------------------------------------------- */}
          {/* TAB 3: Annual Tax Deduction Certificate (Form T-10 / APIT)         */}
          {/* ----------------------------------------------------------------- */}
          {activeStatutoryTab === 'T10' && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
              {/* Employee & Assessment Year Selection Bar */}
              <div
                style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  flexWrap: 'wrap',
                  gap: '1rem',
                  background: 'var(--color-surface-container)',
                  padding: '0.75rem 1rem',
                  borderRadius: '8px',
                }}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', flexWrap: 'wrap' }}>
                  <label htmlFor="t10-employee-select" style={{ fontSize: '0.8125rem', fontWeight: 600 }}>
                    Select Employee:
                  </label>
                  <select
                    id="t10-employee-select"
                    className="field__input"
                    style={{ minHeight: '34px', minWidth: '260px', padding: '0 0.5rem' }}
                    value={selectedT10EmployeeId}
                    onChange={(e) => setSelectedT10EmployeeId(e.target.value)}
                  >
                    {resultsQuery.data?.results.map((r: PayrollResult) => (
                      <option key={r.employeeId} value={r.employeeId}>
                        {r.employeeCode} - {r.employeeName} ({r.designation})
                      </option>
                    ))}
                  </select>
                </div>

                <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                  <Badge tone="neutral">Year of Assessment: 2025/2026</Badge>
                  <Badge tone="success">Inland Revenue Act No. 24 of 2017</Badge>
                </div>
              </div>

              {t10CertificateQuery.isPending && (
                <LoadingState label="Preparing Form T-10 Tax Deduction Certificate..." />
              )}
              {t10CertificateQuery.isError && (
                <QueryErrorState
                  error={t10CertificateQuery.error}
                  onRetry={() => void t10CertificateQuery.refetch()}
                />
              )}
              {t10CertificateQuery.data && (
                <div
                  className="t10-certificate-sheet"
                  style={{
                    background: 'var(--color-surface)',
                    border: '2px solid var(--color-outline)',
                    borderRadius: '8px',
                    padding: '1.5rem',
                    display: 'flex',
                    flexDirection: 'column',
                    gap: '1.25rem',
                    boxShadow: '0 2px 8px rgba(0,0,0,0.05)',
                  }}
                >
                  {/* Official Government / IRD Header */}
                  <div
                    style={{
                      textAlign: 'center',
                      borderBottom: '2px solid var(--color-outline)',
                      paddingBottom: '1rem',
                    }}
                  >
                    <div
                      style={{
                        fontSize: '0.75rem',
                        letterSpacing: '0.08em',
                        textTransform: 'uppercase',
                        color: 'var(--color-on-surface-muted)',
                        fontWeight: 600,
                      }}
                    >
                      Democratic Socialist Republic of Sri Lanka
                    </div>
                    <div style={{ fontSize: '1.125rem', fontWeight: 800, marginTop: '2px', letterSpacing: '0.04em' }}>
                      DEPARTMENT OF INLAND REVENUE
                    </div>
                    <div
                      style={{
                        fontSize: '1rem',
                        fontWeight: 700,
                        marginTop: '4px',
                        color: 'var(--color-primary, #2563eb)',
                      }}
                    >
                      FORM T-10
                    </div>
                    <div style={{ fontSize: '0.875rem', fontWeight: 600, marginTop: '2px' }}>
                      CERTIFICATE OF ADVANCE PERSONAL INCOME TAX (APIT) DEDUCTIONS
                    </div>
                    <div style={{ fontSize: '0.75rem', color: 'var(--color-on-surface-muted)', marginTop: '2px' }}>
                      Issued under Section 83 of the Inland Revenue Act, No. 24 of 2017
                    </div>
                    <div style={{ fontSize: '0.8125rem', fontWeight: 600, marginTop: '4px' }}>
                      Year of Assessment: <strong>{t10CertificateQuery.data.assessmentYear}</strong> ({t10CertificateQuery.data.periodCovered})
                    </div>
                  </div>

                  {/* Employer & Employee Details Grid */}
                  <div
                    style={{
                      display: 'grid',
                      gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))',
                      gap: '1rem',
                      background: 'var(--color-surface-container)',
                      padding: '1rem',
                      borderRadius: '6px',
                      fontSize: '0.8125rem',
                    }}
                  >
                    {/* Left: Employer */}
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '0.35rem' }}>
                      <div
                        style={{
                          fontWeight: 700,
                          fontSize: '0.75rem',
                          textTransform: 'uppercase',
                          color: 'var(--color-on-surface-muted)',
                          borderBottom: '1px solid var(--color-outline)',
                          paddingBottom: '0.25rem',
                        }}
                      >
                        1. Employer Particulars
                      </div>
                      <div>
                        <strong>Name: </strong>
                        {t10CertificateQuery.data.employer.name}
                      </div>
                      <div>
                        <strong>Address: </strong>
                        {t10CertificateQuery.data.employer.address}
                      </div>
                      <div>
                        <strong>Employer TIN: </strong>
                        <code>{t10CertificateQuery.data.employer.tin}</code>
                      </div>
                      <div>
                        <strong>EPF Registration No: </strong>
                        <code>{t10CertificateQuery.data.employer.employerEpfNo}</code>
                      </div>
                    </div>

                    {/* Right: Employee */}
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '0.35rem' }}>
                      <div
                        style={{
                          fontWeight: 700,
                          fontSize: '0.75rem',
                          textTransform: 'uppercase',
                          color: 'var(--color-on-surface-muted)',
                          borderBottom: '1px solid var(--color-outline)',
                          paddingBottom: '0.25rem',
                        }}
                      >
                        2. Employee Particulars
                      </div>
                      <div>
                        <strong>Full Name: </strong>
                        {t10CertificateQuery.data.employee.fullName}
                      </div>
                      <div>
                        <strong>Employee Code / Designation: </strong>
                        {t10CertificateQuery.data.employee.code} — {t10CertificateQuery.data.employee.designation} ({t10CertificateQuery.data.employee.department})
                      </div>
                      <div>
                        <strong>National Identity Card (NIC): </strong>
                        <code>{t10CertificateQuery.data.employee.nic}</code>
                      </div>
                      <div>
                        <strong>Employee Taxpayer ID (TIN): </strong>
                        <code>{t10CertificateQuery.data.employee.tin}</code>
                      </div>
                      <div>
                        <strong>Member EPF No: </strong>
                        <code>{t10CertificateQuery.data.employee.epfNo}</code>
                      </div>
                    </div>
                  </div>

                  {/* 12-Month Schedule Table */}
                  <div>
                    <div style={{ fontSize: '0.75rem', fontWeight: 700, textTransform: 'uppercase', color: 'var(--color-on-surface-muted)', marginBottom: '0.5rem' }}>
                      3. Monthly Remuneration and APIT Deductions Schedule
                    </div>
                    <div style={{ border: '1px solid var(--color-outline)', borderRadius: '6px', overflow: 'hidden' }}>
                      <table className="table" style={{ fontSize: '0.75rem', width: '100%' }}>
                        <thead>
                          <tr>
                            <th scope="col">Calendar Month</th>
                            <th scope="col" className="numeric">Gross Cash (LKR)</th>
                            <th scope="col" className="numeric">Non-Cash Benefits (LKR)</th>
                            <th scope="col" className="numeric">Assessable Pay (LKR)</th>
                            <th scope="col" className="numeric">APIT Tax Deducted (LKR)</th>
                            <th scope="col">Remittance Date</th>
                            <th scope="col">IRD Reference</th>
                          </tr>
                        </thead>
                        <tbody>
                          {t10CertificateQuery.data.monthlySchedule.map((row) => (
                            <tr key={row.periodCode}>
                              <td>
                                <strong>{row.monthName}</strong>
                              </td>
                              <td className="numeric">
                                {row.grossRemuneration.toLocaleString(undefined, { minimumFractionDigits: 2 })}
                              </td>
                              <td className="numeric">
                                {row.nonCashBenefits.toLocaleString(undefined, { minimumFractionDigits: 2 })}
                              </td>
                              <td className="numeric">
                                {row.totalAssessableRemuneration.toLocaleString(undefined, { minimumFractionDigits: 2 })}
                              </td>
                              <td className="numeric" style={{ fontWeight: 700, color: row.apitTaxDeducted > 0 ? '#b91c1c' : undefined }}>
                                {row.apitTaxDeducted.toLocaleString(undefined, { minimumFractionDigits: 2 })}
                              </td>
                              <td>{row.remittanceDate}</td>
                              <td>
                                <code>{row.remittanceRef}</code>
                              </td>
                            </tr>
                          ))}
                        </tbody>
                        <tfoot>
                          <tr style={{ fontWeight: 700, background: 'var(--color-surface-container)' }}>
                            <td>Total / Cumulative</td>
                            <td className="numeric">
                              LKR {t10CertificateQuery.data.totals.annualGrossRemuneration.toLocaleString(undefined, { minimumFractionDigits: 2 })}
                            </td>
                            <td className="numeric">
                              LKR {t10CertificateQuery.data.totals.annualNonCashBenefits.toLocaleString(undefined, { minimumFractionDigits: 2 })}
                            </td>
                            <td className="numeric">
                              LKR {t10CertificateQuery.data.totals.annualAssessableRemuneration.toLocaleString(undefined, { minimumFractionDigits: 2 })}
                            </td>
                            <td className="numeric" style={{ color: '#b91c1c' }}>
                              LKR {t10CertificateQuery.data.totals.annualApitTaxDeducted.toLocaleString(undefined, { minimumFractionDigits: 2 })}
                            </td>
                            <td colSpan={2}>12 Monthly Remittances Completed</td>
                          </tr>
                        </tfoot>
                      </table>
                    </div>
                  </div>

                  {/* Statutory Tax Reconciliation Box */}
                  <div
                    style={{
                      background: 'var(--color-surface-container)',
                      padding: '1rem',
                      borderRadius: '6px',
                      display: 'grid',
                      gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))',
                      gap: '0.75rem',
                      fontSize: '0.8125rem',
                    }}
                  >
                    <div>
                      <span style={{ fontSize: '0.6875rem', color: 'var(--color-on-surface-muted)' }}>
                        Cumulative Assessable Remuneration
                      </span>
                      <div style={{ fontWeight: 700, fontSize: '1rem', marginTop: '2px' }}>
                        LKR {t10CertificateQuery.data.totals.annualAssessableRemuneration.toLocaleString(undefined, { minimumFractionDigits: 2 })}
                      </div>
                    </div>
                    <div>
                      <span style={{ fontSize: '0.6875rem', color: 'var(--color-on-surface-muted)' }}>
                        Statutory Personal Relief (Exemption)
                      </span>
                      <div style={{ fontWeight: 700, fontSize: '1rem', marginTop: '2px', color: '#16a34a' }}>
                        - LKR {t10CertificateQuery.data.totals.statutoryReliefThreshold.toLocaleString(undefined, { minimumFractionDigits: 2 })}
                      </div>
                    </div>
                    <div>
                      <span style={{ fontSize: '0.6875rem', color: 'var(--color-on-surface-muted)' }}>
                        Net Taxable Remuneration
                      </span>
                      <div style={{ fontWeight: 700, fontSize: '1rem', marginTop: '2px' }}>
                        LKR {t10CertificateQuery.data.totals.taxableRemuneration.toLocaleString(undefined, { minimumFractionDigits: 2 })}
                      </div>
                    </div>
                    <div>
                      <span style={{ fontSize: '0.6875rem', color: 'var(--color-on-surface-muted)' }}>
                        Total APIT Deducted & Remitted
                      </span>
                      <div style={{ fontWeight: 700, fontSize: '1rem', marginTop: '2px', color: '#b91c1c' }}>
                        LKR {t10CertificateQuery.data.totals.annualApitTaxDeducted.toLocaleString(undefined, { minimumFractionDigits: 2 })}
                      </div>
                    </div>
                    <div>
                      <span style={{ fontSize: '0.6875rem', color: 'var(--color-on-surface-muted)' }}>
                        Net Remuneration Disbursed
                      </span>
                      <div style={{ fontWeight: 700, fontSize: '1rem', marginTop: '2px' }}>
                        LKR {t10CertificateQuery.data.totals.annualNetPaid.toLocaleString(undefined, { minimumFractionDigits: 2 })}
                      </div>
                    </div>
                  </div>

                  {/* Official Certification Declaration & Signatory Box */}
                  <div
                    style={{
                      borderTop: '1px solid var(--color-outline)',
                      paddingTop: '0.875rem',
                      display: 'flex',
                      justifyContent: 'space-between',
                      alignItems: 'flex-end',
                      flexWrap: 'wrap',
                      gap: '1rem',
                      fontSize: '0.75rem',
                    }}
                  >
                    <div style={{ maxWidth: '440px', color: 'var(--color-on-surface-muted)' }}>
                      <strong>Declaration: </strong>
                      {t10CertificateQuery.data.declaration.statement}
                      <div style={{ marginTop: '0.5rem', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                        <span>Digital Verification Seal: </span>
                        <code style={{ fontSize: '0.6875rem' }}>
                          {t10CertificateQuery.data.declaration.digitalSealHash.slice(0, 24)}…
                        </code>
                      </div>
                    </div>

                    <div style={{ textAlign: 'right' }}>
                      <div
                        style={{
                          borderBottom: '1px dashed var(--color-outline)',
                          width: '180px',
                          marginBottom: '4px',
                          paddingBottom: '2px',
                          fontWeight: 700,
                        }}
                      >
                        {t10CertificateQuery.data.declaration.signatoryName}
                      </div>
                      <div style={{ fontWeight: 600 }}>{t10CertificateQuery.data.declaration.signatoryTitle}</div>
                      <div style={{ color: 'var(--color-on-surface-muted)' }}>
                        Date of Issue: {t10CertificateQuery.data.declaration.issuedDate}
                      </div>
                    </div>
                  </div>
                </div>
              )}
            </div>
          )}
        </div>
      </Modal>

      {/* --------------------------------------------------------------------- */}
      {/* Official Single Payslip Modal with Watermark & Digital Seal           */}
      {/* --------------------------------------------------------------------- */}
      {activeRun && selectedOfficialPayslipResultId && (
        <OfficialPayslipModal
          isOpen={Boolean(selectedOfficialPayslipResultId)}
          onClose={() => setSelectedOfficialPayslipResultId(null)}
          runId={activeRun.id}
          resultId={selectedOfficialPayslipResultId}
          allResultIds={resultsQuery.data?.results.map((r) => r.id) ?? []}
          onSelectResultId={(id) => setSelectedOfficialPayslipResultId(id)}
        />
      )}

      {/* --------------------------------------------------------------------- */}
      {/* Batch Continuous Payslips Print Modal                                 */}
      {/* --------------------------------------------------------------------- */}
      {activeRun && (
        <BatchPayslipsPrintModal
          isOpen={isBatchPayslipsModalOpen}
          onClose={() => setIsBatchPayslipsModalOpen(false)}
          runId={activeRun.id}
          payPeriodName={
            payPeriodsQuery.data?.payPeriods.find((p) => p.id === selectedPayPeriodId)?.code || 'March 2026'
          }
        />
      )}
    </div>
  )
}
