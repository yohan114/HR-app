import { useState, useMemo } from 'react'
import { Badge, Button, Card, DataTable, Modal } from '@/components/ui'

export interface LoanProduct {
  id: string
  code: string
  name: string
  description: string
  minAmount: number
  maxAmount: number
  interestRateAnnual: number
  maxTenureMonths: number
  interestType: 'FLAT' | 'REDUCING_BALANCE' | 'ZERO_INTEREST'
  requiresGuarantor: boolean
}

export interface LoanApplication {
  id: string
  applicationNumber: string
  employeeId: string
  employeeName: string
  department: string
  productName: string
  requestedAmount: number
  tenureMonths: number
  estimatedMonthlyEmi: number
  monthlySalary: number
  dtiRatioPercent: number
  purpose: string
  appliedDate: string
  status: 'PENDING_APPROVAL' | 'APPROVED' | 'REJECTED' | 'DISBURSED'
  rejectionReason?: string
}

export interface ActiveLoan {
  id: string
  loanReference: string
  employeeName: string
  productName: string
  disbursedAmount: number
  outstandingPrincipal: number
  monthlyInstallment: number
  paidInstallments: number
  totalInstallments: number
  disbursedDate: string
  nextDueDate: string
  status: 'ACTIVE' | 'SETTLED' | 'DEFAULTED'
}

export interface AmortizationRow {
  installmentNo: number
  dueDate: string
  openingBalance: number
  principalComponent: number
  interestComponent: number
  totalInstallment: number
  closingBalance: number
  status: 'PAID' | 'DUE' | 'UPCOMING'
}

const INITIAL_PRODUCTS: LoanProduct[] = [
  {
    id: 'lp-1',
    code: 'SAL-ADV',
    name: 'Short-Term Salary Advance',
    description: 'Instant zero-interest emergency bridge advance deducted from subsequent month payroll.',
    minAmount: 10000,
    maxAmount: 100000,
    interestRateAnnual: 0,
    maxTenureMonths: 3,
    interestType: 'ZERO_INTEREST',
    requiresGuarantor: false,
  },
  {
    id: 'lp-2',
    code: 'PERS-LOAN',
    name: 'Staff Personal Loan',
    description: 'Concessionary general personal financing for confirmed staff members.',
    minAmount: 50000,
    maxAmount: 1000000,
    interestRateAnnual: 8.5,
    maxTenureMonths: 24,
    interestType: 'REDUCING_BALANCE',
    requiresGuarantor: true,
  },
  {
    id: 'lp-3',
    code: 'EDU-AID',
    name: 'Higher Education & Certification Loan',
    description: 'Subsidized loan for professional postgraduate or executive education degrees.',
    minAmount: 100000,
    maxAmount: 1500000,
    interestRateAnnual: 4.0,
    maxTenureMonths: 36,
    interestType: 'REDUCING_BALANCE',
    requiresGuarantor: false,
  },
  {
    id: 'lp-4',
    code: 'EMERGENCY-MED',
    name: 'Emergency Medical Assistance Loan',
    description: 'Rapid distress relief for hospitalizations exceeding group insurance caps.',
    minAmount: 25000,
    maxAmount: 500000,
    interestRateAnnual: 2.5,
    maxTenureMonths: 18,
    interestType: 'FLAT',
    requiresGuarantor: false,
  },
]

const INITIAL_APPLICATIONS: LoanApplication[] = [
  {
    id: 'app-1',
    applicationNumber: 'LA-2026-0038',
    employeeId: 'e-4',
    employeeName: 'Kasun Fernando',
    department: 'Engineering',
    productName: 'Staff Personal Loan',
    requestedAmount: 350000,
    tenureMonths: 18,
    estimatedMonthlyEmi: 20750,
    monthlySalary: 190000,
    dtiRatioPercent: 10.9,
    purpose: 'Home electrical and roof renovation',
    appliedDate: '2026-03-08',
    status: 'PENDING_APPROVAL',
  },
  {
    id: 'app-2',
    applicationNumber: 'LA-2026-0039',
    employeeId: 'e-5',
    employeeName: 'Dilani Perera',
    department: 'Engineering',
    productName: 'Short-Term Salary Advance',
    requestedAmount: 50000,
    tenureMonths: 2,
    estimatedMonthlyEmi: 25000,
    monthlySalary: 145000,
    dtiRatioPercent: 17.2,
    purpose: 'Family travel and relocation deposit',
    appliedDate: '2026-03-09',
    status: 'PENDING_APPROVAL',
  },
  {
    id: 'app-3',
    applicationNumber: 'LA-2026-0035',
    employeeId: 'e-8',
    employeeName: 'Malith Gunawardena',
    department: 'Engineering',
    productName: 'Staff Personal Loan',
    requestedAmount: 400000,
    tenureMonths: 24,
    estimatedMonthlyEmi: 18180,
    monthlySalary: 125000,
    dtiRatioPercent: 14.5,
    purpose: 'Laptop workstation upgrade and setup',
    appliedDate: '2026-02-28',
    status: 'APPROVED',
  },
]

const INITIAL_ACTIVE_LOANS: ActiveLoan[] = [
  {
    id: 'loan-1',
    loanReference: 'LN-2025-0104',
    employeeName: 'Ruwan Jayasuriya',
    productName: 'Staff Personal Loan',
    disbursedAmount: 600000,
    outstandingPrincipal: 285000,
    monthlyInstallment: 27270,
    paidInstallments: 13,
    totalInstallments: 24,
    disbursedDate: '2025-02-15',
    nextDueDate: '2026-03-31',
    status: 'ACTIVE',
  },
  {
    id: 'loan-2',
    loanReference: 'LN-2025-0211',
    employeeName: 'Anusha Sivakumar',
    productName: 'Higher Education & Certification Loan',
    disbursedAmount: 500000,
    outstandingPrincipal: 380000,
    monthlyInstallment: 14760,
    paidInstallments: 9,
    totalInstallments: 36,
    disbursedDate: '2025-06-01',
    nextDueDate: '2026-03-31',
    status: 'ACTIVE',
  },
  {
    id: 'loan-3',
    loanReference: 'LN-2026-0012',
    employeeName: 'Thivanka Rajapaksa',
    productName: 'Short-Term Salary Advance',
    disbursedAmount: 60000,
    outstandingPrincipal: 20000,
    monthlyInstallment: 20000,
    paidInstallments: 2,
    totalInstallments: 3,
    disbursedDate: '2026-01-15',
    nextDueDate: '2026-03-31',
    status: 'ACTIVE',
  },
]

export function Loans() {
  const [activeTab, setActiveTab] = useState<'applications' | 'active' | 'products'>('applications')

  const [products, setProducts] = useState<LoanProduct[]>(INITIAL_PRODUCTS)
  const [applications, setApplications] = useState<LoanApplication[]>(INITIAL_APPLICATIONS)
  const [activeLoans, setActiveLoans] = useState<ActiveLoan[]>(INITIAL_ACTIVE_LOANS)

  // Amortization modal
  const [selectedLoanForSchedule, setSelectedLoanForSchedule] = useState<ActiveLoan | null>(null)
  const [isScheduleModalOpen, setIsScheduleModalOpen] = useState<boolean>(false)

  // New Application Modal
  const [isApplyModalOpen, setIsApplyModalOpen] = useState<boolean>(false)
  const [bannerMessage, setBannerMessage] = useState<string | null>(null)

  const [newApplication, setNewApplication] = useState({
    employeeName: 'Shanika de Silva',
    department: 'Finance',
    productId: 'lp-1',
    requestedAmount: 50000,
    tenureMonths: 2,
    purpose: 'Urgent medical prescription advance',
  })

  // KPI calculations
  const totalDisbursed = useMemo(() => activeLoans.reduce((sum, l) => sum + l.disbursedAmount, 0), [activeLoans])
  const totalOutstanding = useMemo(() => activeLoans.reduce((sum, l) => sum + l.outstandingPrincipal, 0), [activeLoans])
  const monthlyRecovery = useMemo(() => activeLoans.reduce((sum, l) => sum + l.monthlyInstallment, 0), [activeLoans])

  // Generate dynamic amortization schedule
  const sampleAmortizationSchedule = useMemo<AmortizationRow[]>(() => {
    if (!selectedLoanForSchedule) return []
    const rows: AmortizationRow[] = []
    let balance = selectedLoanForSchedule.disbursedAmount
    const emi = selectedLoanForSchedule.monthlyInstallment
    const total = selectedLoanForSchedule.totalInstallments
    const paid = selectedLoanForSchedule.paidInstallments

    for (let i = 1; i <= total; i++) {
      const interest = Math.round(balance * (0.085 / 12))
      const principal = Math.min(emi - interest, balance)
      const closeBal = Math.max(balance - principal, 0)
      const isPaid = i <= paid
      const isCurrent = i === paid + 1

      rows.push({
        installmentNo: i,
        dueDate: `2026-${String(Math.min(i + 2, 12)).padStart(2, '0')}-28`,
        openingBalance: balance,
        principalComponent: principal,
        interestComponent: interest,
        totalInstallment: emi,
        closingBalance: closeBal,
        status: isPaid ? 'PAID' : isCurrent ? 'DUE' : 'UPCOMING',
      })
      balance = closeBal
    }
    return rows
  }, [selectedLoanForSchedule])

  // Actions
  const handleApproveApplication = (id: string) => {
    setApplications((prev) =>
      prev.map((app) => (app.id === id ? { ...app, status: 'APPROVED' } : app)),
    )
    setBannerMessage('Loan application approved for payroll disbursement.')
    setTimeout(() => setBannerMessage(null), 3500)
  }

  const handleRejectApplication = (id: string) => {
    const reason = prompt('Please specify rejection reason:') || 'Policy eligibility not met'
    setApplications((prev) =>
      prev.map((app) => (app.id === id ? { ...app, status: 'REJECTED', rejectionReason: reason } : app)),
    )
    setBannerMessage('Loan application rejected.')
    setTimeout(() => setBannerMessage(null), 3500)
  }

  const handleCreateApplication = () => {
    const prod = products.find((p) => p.id === newApplication.productId)
    if (!prod) return

    const created: LoanApplication = {
      id: `app-${Date.now()}`,
      applicationNumber: `LA-2026-00${applications.length + 40}`,
      employeeId: 'e-9',
      employeeName: newApplication.employeeName,
      department: newApplication.department,
      productName: prod.name,
      requestedAmount: newApplication.requestedAmount,
      tenureMonths: newApplication.tenureMonths,
      estimatedMonthlyEmi: Math.round(newApplication.requestedAmount / newApplication.tenureMonths),
      monthlySalary: 110000,
      dtiRatioPercent: Math.round(((newApplication.requestedAmount / newApplication.tenureMonths) / 110000) * 100),
      purpose: newApplication.purpose,
      appliedDate: '2026-03-10',
      status: 'PENDING_APPROVAL',
    }

    setApplications((prev) => [created, ...prev])
    setIsApplyModalOpen(false)
    setBannerMessage(`Application ${created.applicationNumber} created successfully.`)
    setTimeout(() => setBannerMessage(null), 3500)
  }

  return (
    <div className="builder-shell">
      {/* Header */}
      <div className="builder-header">
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)' }}>
            <h1 className="text-lg" style={{ margin: 0, fontWeight: 700 }}>
              Staff Loans & Salary Advances Administration
            </h1>
            <Badge tone="success">Active Portfolio</Badge>
            <Badge tone="neutral">{activeLoans.length} Loans Active</Badge>
          </div>
          <p style={{ margin: 0, fontSize: '0.875rem', color: 'var(--color-on-surface-muted)' }}>
            Configure loan policies, review debt-to-income limits, approve applications, and track payroll deductions.
          </p>
        </div>

        <div style={{ display: 'flex', gap: 'var(--space-2)' }}>
          <Button variant="primary" onClick={() => setIsApplyModalOpen(true)}>
            + New Loan Request
          </Button>
        </div>
      </div>

      {bannerMessage && (
        <div style={{ background: '#dcfce7', color: '#15803d', padding: 'var(--space-3)', borderRadius: 'var(--radius-control)', fontWeight: 500 }}>
          {bannerMessage}
        </div>
      )}

      {/* KPI Cards */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: 'var(--space-3)' }}>
        <div style={{ background: 'var(--color-surface-raised)', padding: 'var(--space-3)', borderRadius: 'var(--radius-control)', border: '1px solid var(--color-outline-variant)' }}>
          <div style={{ fontSize: '0.75rem', color: 'var(--color-on-surface-muted)', fontWeight: 600 }}>TOTAL DISBURSED PORTFOLIO</div>
          <div style={{ fontSize: '1.5rem', fontWeight: 700, color: 'var(--color-brand-primary)' }}>
            LKR {totalDisbursed.toLocaleString()}
          </div>
        </div>
        <div style={{ background: 'var(--color-surface-raised)', padding: 'var(--space-3)', borderRadius: 'var(--radius-control)', border: '1px solid var(--color-outline-variant)' }}>
          <div style={{ fontSize: '0.75rem', color: 'var(--color-on-surface-muted)', fontWeight: 600 }}>CURRENT OUTSTANDING PRINCIPAL</div>
          <div style={{ fontSize: '1.5rem', fontWeight: 700, color: '#d97706' }}>
            LKR {totalOutstanding.toLocaleString()}
          </div>
        </div>
        <div style={{ background: 'var(--color-surface-raised)', padding: 'var(--space-3)', borderRadius: 'var(--radius-control)', border: '1px solid var(--color-outline-variant)' }}>
          <div style={{ fontSize: '0.75rem', color: 'var(--color-on-surface-muted)', fontWeight: 600 }}>MONTHLY PAYROLL RECOVERY</div>
          <div style={{ fontSize: '1.5rem', fontWeight: 700, color: '#16a34a' }}>
            LKR {monthlyRecovery.toLocaleString()}
          </div>
        </div>
        <div style={{ background: 'var(--color-surface-raised)', padding: 'var(--space-3)', borderRadius: 'var(--radius-control)', border: '1px solid var(--color-outline-variant)' }}>
          <div style={{ fontSize: '0.75rem', color: 'var(--color-on-surface-muted)', fontWeight: 600 }}>PENDING APPLICATIONS</div>
          <div style={{ fontSize: '1.5rem', fontWeight: 700, color: '#0284c7' }}>
            {applications.filter((a) => a.status === 'PENDING_APPROVAL').length} Requests
          </div>
        </div>
      </div>

      {/* Tab Navigation */}
      <div style={{ display: 'flex', borderBottom: '1px solid var(--color-outline-variant)', gap: 'var(--space-2)' }}>
        {[
          { key: 'applications', label: `Applications Queue (${applications.filter((a) => a.status === 'PENDING_APPROVAL').length} Pending)` },
          { key: 'active', label: `Active Loans Portfolio (${activeLoans.length})` },
          { key: 'products', label: `Loan Products & Rules (${products.length})` },
        ].map((t) => (
          <button
            key={t.key}
            className="btn btn--ghost"
            style={{
              borderBottom: activeTab === t.key ? '2px solid var(--color-brand-primary)' : 'none',
              borderRadius: 0,
              fontWeight: activeTab === t.key ? 700 : 500,
              color: activeTab === t.key ? 'var(--color-brand-primary)' : 'inherit',
              padding: 'var(--space-2) var(--space-4)',
            }}
            onClick={() => setActiveTab(t.key as any)}
          >
            {t.label}
          </button>
        ))}
      </div>

      {/* 1. Applications Queue Tab */}
      {activeTab === 'applications' && (
        <Card title="Loan Applications & Underwriting Review">
          <div style={{ overflowX: 'auto' }}>
            <table className="table">
              <thead>
                <tr>
                  <th scope="col">App #</th>
                  <th scope="col">Employee Name</th>
                  <th scope="col">Product</th>
                  <th scope="col">Amount</th>
                  <th scope="col">Tenure</th>
                  <th scope="col">Monthly EMI</th>
                  <th scope="col">DTI Ratio</th>
                  <th scope="col">Purpose</th>
                  <th scope="col">Status</th>
                  <th scope="col">Actions</th>
                </tr>
              </thead>
              <tbody>
                {applications.map((app) => (
                  <tr key={app.id}>
                    <td style={{ fontWeight: 600, fontFamily: 'var(--font-mono)' }}>{app.applicationNumber}</td>
                    <td>
                      <div style={{ fontWeight: 600 }}>{app.employeeName}</div>
                      <div style={{ fontSize: '0.75rem', color: 'var(--color-on-surface-muted)' }}>{app.department}</div>
                    </td>
                    <td>{app.productName}</td>
                    <td style={{ fontWeight: 600 }}>LKR {app.requestedAmount.toLocaleString()}</td>
                    <td>{app.tenureMonths} Mo</td>
                    <td style={{ fontWeight: 600 }}>LKR {app.estimatedMonthlyEmi.toLocaleString()}</td>
                    <td>
                      <Badge tone={app.dtiRatioPercent > 30 ? 'danger' : app.dtiRatioPercent > 20 ? 'warning' : 'success'}>
                        {app.dtiRatioPercent}% DTI
                      </Badge>
                    </td>
                    <td style={{ fontSize: '0.8125rem' }}>{app.purpose}</td>
                    <td>
                      <Badge tone={app.status === 'APPROVED' ? 'success' : app.status === 'REJECTED' ? 'danger' : 'warning'}>
                        {app.status}
                      </Badge>
                    </td>
                    <td>
                      {app.status === 'PENDING_APPROVAL' ? (
                        <div style={{ display: 'flex', gap: 'var(--space-1)' }}>
                          <Button
                            variant="primary"
                            style={{ padding: '2px 8px', fontSize: '0.75rem' }}
                            onClick={() => handleApproveApplication(app.id)}
                          >
                            Approve
                          </Button>
                          <Button
                            variant="danger"
                            style={{ padding: '2px 8px', fontSize: '0.75rem' }}
                            onClick={() => handleRejectApplication(app.id)}
                          >
                            Reject
                          </Button>
                        </div>
                      ) : (
                        <span style={{ fontSize: '0.75rem', color: 'var(--color-on-surface-muted)' }}>
                          {app.status === 'REJECTED' ? `Reason: ${app.rejectionReason}` : 'Approved'}
                        </span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
      )}

      {/* 2. Active Loans Portfolio Tab */}
      {activeTab === 'active' && (
        <Card title="Disbursed Loans & Payroll Amortization Register">
          <div style={{ overflowX: 'auto' }}>
            <table className="table">
              <thead>
                <tr>
                  <th scope="col">Loan Ref</th>
                  <th scope="col">Borrower</th>
                  <th scope="col">Product Type</th>
                  <th scope="col">Original Principal</th>
                  <th scope="col">Outstanding Balance</th>
                  <th scope="col">Installment (EMI)</th>
                  <th scope="col">Repayment Progress</th>
                  <th scope="col">Next Due Date</th>
                  <th scope="col">Status</th>
                  <th scope="col">Schedule</th>
                </tr>
              </thead>
              <tbody>
                {activeLoans.map((l) => {
                  const pct = Math.round((l.paidInstallments / l.totalInstallments) * 100)
                  return (
                    <tr key={l.id}>
                      <td style={{ fontWeight: 600, fontFamily: 'var(--font-mono)' }}>{l.loanReference}</td>
                      <td style={{ fontWeight: 600 }}>{l.employeeName}</td>
                      <td>{l.productName}</td>
                      <td>LKR {l.disbursedAmount.toLocaleString()}</td>
                      <td style={{ fontWeight: 600, color: '#d97706' }}>LKR {l.outstandingPrincipal.toLocaleString()}</td>
                      <td style={{ fontWeight: 600 }}>LKR {l.monthlyInstallment.toLocaleString()}</td>
                      <td>
                        <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)' }}>
                          <div style={{ width: '80px', height: '8px', background: 'var(--color-surface)', borderRadius: '4px', overflow: 'hidden' }}>
                            <div style={{ width: `${pct}%`, height: '100%', background: 'var(--color-success)' }} />
                          </div>
                          <span style={{ fontSize: '0.75rem' }}>{l.paidInstallments}/{l.totalInstallments}</span>
                        </div>
                      </td>
                      <td style={{ fontSize: '0.8125rem' }}>{l.nextDueDate}</td>
                      <td>
                        <Badge tone="success">{l.status}</Badge>
                      </td>
                      <td>
                        <Button
                          variant="secondary"
                          style={{ padding: '2px 8px', fontSize: '0.75rem' }}
                          onClick={() => {
                            setSelectedLoanForSchedule(l)
                            setIsScheduleModalOpen(true)
                          }}
                        >
                          View Schedule
                        </Button>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        </Card>
      )}

      {/* 3. Loan Products Tab */}
      {activeTab === 'products' && (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(300px, 1fr))', gap: 'var(--space-4)' }}>
          {products.map((p) => (
            <Card key={p.id}>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-2)' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                  <Badge tone={p.interestRateAnnual === 0 ? 'success' : 'neutral'}>
                    {p.interestRateAnnual === 0 ? '0% Interest' : `${p.interestRateAnnual}% Annual`}
                  </Badge>
                  <span style={{ fontSize: '0.75rem', fontFamily: 'var(--font-mono)', fontWeight: 600 }}>
                    {p.code}
                  </span>
                </div>

                <h3 style={{ margin: '4px 0', fontSize: '1.0625rem', fontWeight: 600, color: 'var(--color-brand-primary)' }}>
                  {p.name}
                </h3>

                <p style={{ margin: 0, fontSize: '0.8125rem', color: 'var(--color-on-surface-muted)', minHeight: '36px' }}>
                  {p.description}
                </p>

                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--space-2)', fontSize: '0.75rem', borderTop: '1px solid var(--color-outline-variant)', paddingTop: 'var(--space-2)' }}>
                  <div>
                    <span style={{ color: 'var(--color-on-surface-muted)' }}>Min Amount:</span>{' '}
                    <strong>LKR {p.minAmount.toLocaleString()}</strong>
                  </div>
                  <div>
                    <span style={{ color: 'var(--color-on-surface-muted)' }}>Max Limit:</span>{' '}
                    <strong>LKR {p.maxAmount.toLocaleString()}</strong>
                  </div>
                  <div>
                    <span style={{ color: 'var(--color-on-surface-muted)' }}>Max Tenure:</span>{' '}
                    <strong>{p.maxTenureMonths} Months</strong>
                  </div>
                  <div>
                    <span style={{ color: 'var(--color-on-surface-muted)' }}>Method:</span>{' '}
                    <strong>{p.interestType.replace('_', ' ')}</strong>
                  </div>
                </div>
              </div>
            </Card>
          ))}
        </div>
      )}

      {/* Amortization Schedule Modal */}
      {isScheduleModalOpen && selectedLoanForSchedule && (
        <Modal
          isOpen={isScheduleModalOpen}
          onClose={() => setIsScheduleModalOpen(false)}
          title={`Amortization Schedule: ${selectedLoanForSchedule.loanReference} (${selectedLoanForSchedule.employeeName})`}
        >
          <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', background: 'var(--color-surface)', padding: 'var(--space-3)', borderRadius: 'var(--radius-control)' }}>
              <div>
                <span style={{ fontSize: '0.75rem', color: 'var(--color-on-surface-muted)' }}>Original Disbursed</span>
                <div style={{ fontWeight: 700 }}>LKR {selectedLoanForSchedule.disbursedAmount.toLocaleString()}</div>
              </div>
              <div>
                <span style={{ fontSize: '0.75rem', color: 'var(--color-on-surface-muted)' }}>Remaining Principal</span>
                <div style={{ fontWeight: 700, color: '#d97706' }}>LKR {selectedLoanForSchedule.outstandingPrincipal.toLocaleString()}</div>
              </div>
              <div>
                <span style={{ fontSize: '0.75rem', color: 'var(--color-on-surface-muted)' }}>Monthly Payroll Deduction</span>
                <div style={{ fontWeight: 700, color: '#16a34a' }}>LKR {selectedLoanForSchedule.monthlyInstallment.toLocaleString()}</div>
              </div>
            </div>

            <div style={{ maxHeight: '380px', overflowY: 'auto' }}>
              <table className="table">
                <thead>
                  <tr>
                    <th scope="col">#</th>
                    <th scope="col">Due Date</th>
                    <th scope="col">Principal</th>
                    <th scope="col">Interest</th>
                    <th scope="col">Total EMI</th>
                    <th scope="col">Ending Balance</th>
                    <th scope="col">Status</th>
                  </tr>
                </thead>
                <tbody>
                  {sampleAmortizationSchedule.map((row) => (
                    <tr key={row.installmentNo}>
                      <td>{row.installmentNo}</td>
                      <td>{row.dueDate}</td>
                      <td>LKR {row.principalComponent.toLocaleString()}</td>
                      <td>LKR {row.interestComponent.toLocaleString()}</td>
                      <td style={{ fontWeight: 600 }}>LKR {row.totalInstallment.toLocaleString()}</td>
                      <td>LKR {row.closingBalance.toLocaleString()}</td>
                      <td>
                        <Badge tone={row.status === 'PAID' ? 'success' : row.status === 'DUE' ? 'warning' : 'neutral'}>
                          {row.status}
                        </Badge>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 'var(--space-2)' }}>
              <Button variant="secondary" onClick={() => setIsScheduleModalOpen(false)}>Close</Button>
            </div>
          </div>
        </Modal>
      )}

      {/* New Application Modal */}
      {isApplyModalOpen && (
        <Modal isOpen={isApplyModalOpen} onClose={() => setIsApplyModalOpen(false)} title="Submit Staff Loan Request">
          <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
            <div>
              <label style={{ fontSize: '0.8125rem', fontWeight: 600 }}>Applicant Employee</label>
              <input
                className="field__input"
                value={newApplication.employeeName}
                onChange={(e) => setNewApplication({ ...newApplication, employeeName: e.target.value })}
              />
            </div>
            <div>
              <label style={{ fontSize: '0.8125rem', fontWeight: 600 }}>Loan Product</label>
              <select
                className="select"
                value={newApplication.productId}
                onChange={(e) => setNewApplication({ ...newApplication, productId: e.target.value })}
              >
                {products.map((p) => (
                  <option key={p.id} value={p.id}>{p.name} (Max LKR {p.maxAmount.toLocaleString()})</option>
                ))}
              </select>
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--space-2)' }}>
              <div>
                <label style={{ fontSize: '0.8125rem', fontWeight: 600 }}>Requested Amount (LKR)</label>
                <input
                  type="number"
                  className="field__input"
                  value={newApplication.requestedAmount}
                  onChange={(e) => setNewApplication({ ...newApplication, requestedAmount: Number(e.target.value) })}
                />
              </div>
              <div>
                <label style={{ fontSize: '0.8125rem', fontWeight: 600 }}>Tenure (Months)</label>
                <input
                  type="number"
                  className="field__input"
                  value={newApplication.tenureMonths}
                  onChange={(e) => setNewApplication({ ...newApplication, tenureMonths: Number(e.target.value) })}
                />
              </div>
            </div>
            <div>
              <label style={{ fontSize: '0.8125rem', fontWeight: 600 }}>Purpose & Justification</label>
              <textarea
                className="field__input"
                rows={3}
                value={newApplication.purpose}
                onChange={(e) => setNewApplication({ ...newApplication, purpose: e.target.value })}
              />
            </div>
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 'var(--space-2)', marginTop: 'var(--space-2)' }}>
              <Button variant="secondary" onClick={() => setIsApplyModalOpen(false)}>Cancel</Button>
              <Button variant="primary" onClick={handleCreateApplication}>Submit Application</Button>
            </div>
          </div>
        </Modal>
      )}
    </div>
  )
}
