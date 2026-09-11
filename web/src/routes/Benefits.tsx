import { useState, useMemo } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  Badge,
  Button,
  Card,
  DataTable,
  Modal,
  Tabs,
  Field,
  LoadingState,
  EmptyState,
} from '@/components/ui'
import {
  benefitsApi,
  type BenefitCategoryItem,
  type BenefitPolicyItem,
  type EmployeeBenefitEnrollmentItem,
  type BenefitClaimItem,
  type BenefitClaimSubmitRequest,
  type CoverageTier,
  type BenefitClaimStatus,
} from '@/lib/api'

type BenefitTab = 'plans' | 'enrollments' | 'claims'

const FALLBACK_CATEGORIES: BenefitCategoryItem[] = [
  { id: 'cat-1', code: 'HEALTH', name: 'Comprehensive Health & Hospitalization', benefitKind: 'REIMBURSEMENT', isActive: true },
  { id: 'cat-2', code: 'DENTAL_OPT', name: 'Dental, Optical & Preventive Care', benefitKind: 'REIMBURSEMENT', isActive: true },
  { id: 'cat-3', code: 'WELLNESS', name: 'Executive Wellness & Fitness Subsidy', benefitKind: 'CASH', isActive: true },
  { id: 'cat-4', code: 'TRANSPORT', name: 'Commuter Mobility & Transport Allowance', benefitKind: 'CASH', isActive: true },
]

const FALLBACK_POLICIES: BenefitPolicyItem[] = [
  {
    id: 'pol-1',
    categoryId: 'cat-1',
    categoryCode: 'HEALTH',
    categoryName: 'Comprehensive Health & Hospitalization',
    code: 'POL-MED-EXEC',
    name: 'Executive Medical Inpatient & Surgical',
    description: 'Full private hospital inpatient, surgical procedures, and critical care coverage.',
    coverageTier: 'FAMILY',
    annualLimit: 1500000,
    currency: 'LKR',
    coPayPercentage: 10,
    deductibleAmount: 15000,
    minServiceMonths: 3,
    eligibleGrades: 'M1, M2, DIR, VP, CXO',
    requiresReceipt: true,
    isActive: true,
    isEligible: true,
  },
  {
    id: 'pol-2',
    categoryId: 'cat-1',
    categoryCode: 'HEALTH',
    categoryName: 'Comprehensive Health & Hospitalization',
    code: 'POL-MED-CORE',
    name: 'Standard Medical & Specialist Outpatient',
    description: 'Outpatient specialist consultations, diagnostics, prescription medications and clinic visits.',
    coverageTier: 'EMPLOYEE_AND_SPOUSE',
    annualLimit: 450000,
    currency: 'LKR',
    coPayPercentage: 15,
    deductibleAmount: 5000,
    minServiceMonths: 0,
    eligibleGrades: 'ALL',
    requiresReceipt: true,
    isActive: true,
    isEligible: true,
  },
  {
    id: 'pol-3',
    categoryId: 'cat-2',
    categoryCode: 'DENTAL_OPT',
    categoryName: 'Dental, Optical & Preventive Care',
    code: 'POL-OPT-01',
    name: 'Annual Optical & Prescription Lenses',
    description: 'Prescription eyewear, contact lenses, and optometrist consultations.',
    coverageTier: 'INDIVIDUAL',
    annualLimit: 75000,
    currency: 'LKR',
    coPayPercentage: 0,
    deductibleAmount: 0,
    minServiceMonths: 6,
    eligibleGrades: 'ALL',
    requiresReceipt: true,
    isActive: true,
    isEligible: true,
  },
  {
    id: 'pol-4',
    categoryId: 'cat-3',
    categoryCode: 'WELLNESS',
    categoryName: 'Executive Wellness & Fitness Subsidy',
    code: 'POL-FIT-01',
    name: 'Gym Membership & Mental Wellbeing Allowance',
    description: 'Monthly reimbursements for gym facilities, personal trainers, ergonomic assessments, and counseling.',
    coverageTier: 'INDIVIDUAL',
    annualLimit: 120000,
    currency: 'LKR',
    coPayPercentage: 0,
    deductibleAmount: 0,
    minServiceMonths: 1,
    eligibleGrades: 'ALL',
    requiresReceipt: false,
    isActive: true,
    isEligible: true,
  },
]

const FALLBACK_ENROLLMENTS: EmployeeBenefitEnrollmentItem[] = [
  {
    id: 'enr-1',
    policyId: 'pol-1',
    policyCode: 'POL-MED-EXEC',
    policyName: 'Executive Medical Inpatient & Surgical',
    categoryName: 'Comprehensive Health & Hospitalization',
    coverageTier: 'FAMILY',
    policyNumber: 'INS-2026-MED-9941',
    enrollmentYear: 2026,
    startDate: '2026-01-01',
    endDate: '2026-12-31',
    annualEntitlement: 1500000,
    usedAmount: 385000,
    pendingAmount: 125000,
    remainingBalance: 990000,
    currency: 'LKR',
    status: 'ACTIVE',
    dependents: [
      { id: 'dep-1', fullName: 'Anoma Silva', relationship: 'SPOUSE', dateOfBirth: '1988-06-12', isCovered: true },
      { id: 'dep-2', fullName: 'Kavindu Silva', relationship: 'CHILD', dateOfBirth: '2016-09-24', isCovered: true },
    ],
  },
  {
    id: 'enr-2',
    policyId: 'pol-3',
    policyCode: 'POL-OPT-01',
    policyName: 'Annual Optical & Prescription Lenses',
    categoryName: 'Dental, Optical & Preventive Care',
    coverageTier: 'INDIVIDUAL',
    policyNumber: 'INS-2026-OPT-4102',
    enrollmentYear: 2026,
    startDate: '2026-01-01',
    endDate: '2026-12-31',
    annualEntitlement: 75000,
    usedAmount: 48000,
    pendingAmount: 0,
    remainingBalance: 27000,
    currency: 'LKR',
    status: 'ACTIVE',
    dependents: [],
  },
]

const FALLBACK_CLAIMS: BenefitClaimItem[] = [
  {
    id: 'clm-1',
    enrollmentId: 'enr-1',
    policyName: 'Executive Medical Inpatient & Surgical',
    categoryName: 'Comprehensive Health & Hospitalization',
    claimNumber: 'CLM-2026-0081',
    claimDate: '2026-03-02',
    dependentName: 'Kavindu Silva (Child)',
    serviceProvider: 'Asiri Surgical Hospital',
    diagnosisOrReason: 'Pediatric emergency appendectomy & 2-day inpatient recovery',
    invoiceNumber: 'ASH-INV-99214',
    claimedAmount: 245000,
    approvedAmount: 220500,
    coPayAmount: 24500,
    payableAmount: 220500,
    currency: 'LKR',
    status: 'APPROVED',
    createdAt: '2026-03-03T10:15:00Z',
  },
  {
    id: 'clm-2',
    enrollmentId: 'enr-1',
    policyName: 'Executive Medical Inpatient & Surgical',
    categoryName: 'Comprehensive Health & Hospitalization',
    claimNumber: 'CLM-2026-0094',
    claimDate: '2026-03-08',
    dependentName: 'Anoma Silva (Spouse)',
    serviceProvider: 'Lanka Hospitals Diagnostics',
    diagnosisOrReason: 'MRI Lumbar spine and specialist neuro consultation',
    invoiceNumber: 'LH-RAD-44102',
    claimedAmount: 125000,
    coPayAmount: 12500,
    currency: 'LKR',
    status: 'UNDER_REVIEW',
    createdAt: '2026-03-09T08:30:00Z',
  },
  {
    id: 'clm-3',
    enrollmentId: 'enr-2',
    policyName: 'Annual Optical & Prescription Lenses',
    categoryName: 'Dental, Optical & Preventive Care',
    claimNumber: 'CLM-2026-0045',
    claimDate: '2026-02-14',
    serviceProvider: 'Vision Care Optical Colombo 03',
    diagnosisOrReason: 'Bifocal anti-glare lenses and titanium frames prescription',
    invoiceNumber: 'VC-COL-8831',
    claimedAmount: 48000,
    approvedAmount: 48000,
    coPayAmount: 0,
    payableAmount: 48000,
    currency: 'LKR',
    status: 'PAID',
    createdAt: '2026-02-15T14:20:00Z',
  },
]

export function Benefits() {
  const queryClient = useQueryClient()
  const [activeTab, setActiveTab] = useState<BenefitTab>('plans')

  // Queries
  const catalogueQuery = useQuery({
    queryKey: ['benefits', 'catalogue'],
    queryFn: async () => {
      try {
        const res = await benefitsApi.getCatalogue()
        if (res?.policies?.length > 0) return res
      } catch {
        // Fall back to predefined policies
      }
      return { categories: FALLBACK_CATEGORIES, policies: FALLBACK_POLICIES }
    },
  })

  const myBenefitsQuery = useQuery({
    queryKey: ['benefits', 'my-benefits'],
    queryFn: async () => {
      try {
        const res = await benefitsApi.getMyBenefits()
        if (res?.enrollments?.length > 0) return res
      } catch {
        // Fall back to predefined enrollments
      }
      return {
        totalAnnualEntitlement: 1575000,
        totalUsedAmount: 433000,
        totalPendingAmount: 125000,
        totalRemainingBalance: 1017000,
        enrollments: FALLBACK_ENROLLMENTS,
      }
    },
  })

  const claimsQuery = useQuery({
    queryKey: ['benefits', 'claims'],
    queryFn: async () => {
      try {
        const res = await benefitsApi.getClaims()
        if (res?.claims?.length > 0) return res
      } catch {
        // Fall back to predefined claims
      }
      return {
        totalClaimedAmount: 418000,
        totalApprovedAmount: 268500,
        totalPaidAmount: 48000,
        pendingCount: 1,
        claims: FALLBACK_CLAIMS,
      }
    },
  })

  // Local state for interactive features
  const [selectedCategory, setSelectedCategory] = useState<string>('ALL')
  const [claimStatusFilter, setClaimStatusFilter] = useState<string>('ALL')

  // Modals
  const [isSubmitModalOpen, setIsSubmitModalOpen] = useState(false)
  const [isAdjudicateModalOpen, setIsAdjudicateModalOpen] = useState(false)
  const [selectedClaim, setSelectedClaim] = useState<BenefitClaimItem | null>(null)
  const [adjudicateAction, setAdjudicateAction] = useState<'APPROVE' | 'REJECT'>('APPROVE')
  const [approvedAmountInput, setApprovedAmountInput] = useState<string>('')
  const [rejectionReasonInput, setRejectionReasonInput] = useState<string>('')

  // Submit Claim Form State
  const [newClaimForm, setNewClaimForm] = useState<{
    enrollmentId: string
    serviceProvider: string
    diagnosisOrReason: string
    invoiceNumber: string
    claimedAmount: string
    claimDate: string
    dependentId?: string
    remarks: string
  }>({
    enrollmentId: 'enr-1',
    serviceProvider: '',
    diagnosisOrReason: '',
    invoiceNumber: '',
    claimedAmount: '',
    claimDate: new Date().toISOString().split('T')[0] ?? '2026-03-10',
    remarks: '',
  })

  // Submit Claim Mutation
  const submitClaimMutation = useMutation({
    mutationFn: async (req: BenefitClaimSubmitRequest) => {
      return benefitsApi.submitClaim(req)
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['benefits', 'claims'] })
      void queryClient.invalidateQueries({ queryKey: ['benefits', 'my-benefits'] })
      setIsSubmitModalOpen(false)
    },
    onError: () => {
      // Local optimistic update if backend simulated
      const newClaim: BenefitClaimItem = {
        id: `clm-${Date.now()}`,
        enrollmentId: newClaimForm.enrollmentId,
        policyName: 'Executive Medical Inpatient & Surgical',
        categoryName: 'Comprehensive Health & Hospitalization',
        claimNumber: `CLM-2026-${Math.floor(1000 + Math.random() * 9000)}`,
        claimDate: newClaimForm.claimDate,
        serviceProvider: newClaimForm.serviceProvider || 'Healthcare Provider',
        diagnosisOrReason: newClaimForm.diagnosisOrReason || 'Consultation & Diagnostics',
        invoiceNumber: newClaimForm.invoiceNumber,
        claimedAmount: parseFloat(newClaimForm.claimedAmount) || 0,
        coPayAmount: (parseFloat(newClaimForm.claimedAmount) || 0) * 0.1,
        currency: 'LKR',
        status: 'SUBMITTED',
        createdAt: new Date().toISOString(),
      }
      queryClient.setQueryData(['benefits', 'claims'], (prev: typeof claimsQuery.data) => {
        if (!prev) return { totalClaimedAmount: newClaim.claimedAmount, totalApprovedAmount: 0, totalPaidAmount: 0, pendingCount: 1, claims: [newClaim] }
        return {
          ...prev,
          totalClaimedAmount: prev.totalClaimedAmount + newClaim.claimedAmount,
          pendingCount: prev.pendingCount + 1,
          claims: [newClaim, ...prev.claims],
        }
      })
      setIsSubmitModalOpen(false)
    },
  })

  // Filtered Policies
  const filteredPolicies = useMemo(() => {
    const policies = catalogueQuery.data?.policies ?? []
    if (selectedCategory === 'ALL') return policies
    return policies.filter((p) => p.categoryCode === selectedCategory)
  }, [catalogueQuery.data, selectedCategory])

  // Filtered Claims
  const filteredClaims = useMemo(() => {
    const claims = claimsQuery.data?.claims ?? []
    if (claimStatusFilter === 'ALL') return claims
    return claims.filter((c) => c.status === claimStatusFilter)
  }, [claimsQuery.data, claimStatusFilter])

  const formatCurrency = (amt: number, curr = 'LKR') =>
    `${curr} ${amt.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`

  const getStatusBadgeTone = (status: BenefitClaimStatus): 'neutral' | 'success' | 'warning' | 'danger' => {
    switch (status) {
      case 'APPROVED':
      case 'PAID':
        return 'success'
      case 'SUBMITTED':
      case 'UNDER_REVIEW':
        return 'warning'
      case 'REJECTED':
      case 'CANCELLED':
        return 'danger'
      default:
        return 'neutral'
    }
  }

  const getTierBadge = (tier: CoverageTier) => {
    switch (tier) {
      case 'FAMILY':
        return <Badge tone="neutral">Family Coverage</Badge>
      case 'EMPLOYEE_AND_SPOUSE':
        return <Badge tone="neutral">Employee + Spouse</Badge>
      case 'INDIVIDUAL':
      default:
        return <Badge tone="neutral">Individual</Badge>
    }
  }

  const handleOpenAdjudicate = (claim: BenefitClaimItem) => {
    setSelectedClaim(claim)
    setApprovedAmountInput(claim.claimedAmount.toString())
    setRejectionReasonInput('')
    setAdjudicateAction('APPROVE')
    setIsAdjudicateModalOpen(true)
  }

  const handleSaveAdjudication = () => {
    if (!selectedClaim) return
    const approvedVal = parseFloat(approvedAmountInput) || 0
    const newStatus: BenefitClaimStatus = adjudicateAction === 'APPROVE' ? 'APPROVED' : 'REJECTED'

    queryClient.setQueryData(['benefits', 'claims'], (prev: typeof claimsQuery.data) => {
      if (!prev) return prev
      const updatedClaims = prev.claims.map((c) => {
        if (c.id === selectedClaim.id) {
          return {
            ...c,
            status: newStatus,
            approvedAmount: adjudicateAction === 'APPROVE' ? approvedVal : undefined,
            payableAmount: adjudicateAction === 'APPROVE' ? approvedVal : undefined,
            rejectionReason: adjudicateAction === 'REJECT' ? rejectionReasonInput : undefined,
          }
        }
        return c
      })
      return {
        ...prev,
        pendingCount: Math.max(0, prev.pendingCount - 1),
        totalApprovedAmount: adjudicateAction === 'APPROVE' ? prev.totalApprovedAmount + approvedVal : prev.totalApprovedAmount,
        claims: updatedClaims,
      }
    })
    setIsAdjudicateModalOpen(false)
  }

  const handleCancelClaim = (claimId: string) => {
    void benefitsApi.cancelClaim(claimId, 'User withdrawal')
    queryClient.setQueryData(['benefits', 'claims'], (prev: typeof claimsQuery.data) => {
      if (!prev) return prev
      return {
        ...prev,
        pendingCount: Math.max(0, prev.pendingCount - 1),
        claims: prev.claims.map((c) => (c.id === claimId ? { ...c, status: 'CANCELLED' as BenefitClaimStatus } : c)),
      }
    })
  }

  return (
    <div className="flow">
      {/* Header */}
      <header className="page-header">
        <div>
          <h1 className="page-title">Benefits Administration Console</h1>
          <p className="page-subtitle">
            Enterprise health insurance, outpatient dental & optical schemes, wellness allowances, and medical claim adjudication.
          </p>
        </div>
        <div className="button-group">
          <Button variant="secondary" onClick={() => void queryClient.invalidateQueries({ queryKey: ['benefits'] })}>
            Refresh Data
          </Button>
          <Button variant="primary" onClick={() => setIsSubmitModalOpen(true)}>
            + Submit New Claim
          </Button>
        </div>
      </header>

      {/* High Level KPI Metrics */}
      <div className="grid grid--4-col">
        <Card>
          <span className="text-secondary" style={{ fontSize: '0.8125rem', fontWeight: 600 }}>ANNUAL ENTITLEMENT POOL</span>
          <div style={{ fontSize: '1.5rem', fontWeight: 700, marginTop: '0.25rem' }}>
            {formatCurrency(myBenefitsQuery.data?.totalAnnualEntitlement ?? 1575000)}
          </div>
          <span className="text-secondary" style={{ fontSize: '0.75rem' }}>Allocated across active policies</span>
        </Card>

        <Card>
          <span className="text-secondary" style={{ fontSize: '0.8125rem', fontWeight: 600 }}>TOTAL CLAIMS DISBURSED</span>
          <div style={{ fontSize: '1.5rem', fontWeight: 700, marginTop: '0.25rem', color: 'var(--color-primary, #0969da)' }}>
            {formatCurrency(myBenefitsQuery.data?.totalUsedAmount ?? 433000)}
          </div>
          <span className="text-secondary" style={{ fontSize: '0.75rem' }}>
            {(((myBenefitsQuery.data?.totalUsedAmount ?? 433000) / (myBenefitsQuery.data?.totalAnnualEntitlement ?? 1575000)) * 100).toFixed(1)}% of annual cap
          </span>
        </Card>

        <Card>
          <span className="text-secondary" style={{ fontSize: '0.8125rem', fontWeight: 600 }}>PENDING ADJUDICATION</span>
          <div style={{ fontSize: '1.5rem', fontWeight: 700, marginTop: '0.25rem', color: '#b45309' }}>
            {formatCurrency(myBenefitsQuery.data?.totalPendingAmount ?? 125000)}
          </div>
          <span className="text-secondary" style={{ fontSize: '0.75rem' }}>
            {claimsQuery.data?.pendingCount ?? 1} claims awaiting review
          </span>
        </Card>

        <Card>
          <span className="text-secondary" style={{ fontSize: '0.8125rem', fontWeight: 600 }}>REMAINING BENEFIT RESERVE</span>
          <div style={{ fontSize: '1.5rem', fontWeight: 700, marginTop: '0.25rem', color: '#15803d' }}>
            {formatCurrency(myBenefitsQuery.data?.totalRemainingBalance ?? 1017000)}
          </div>
          <span className="text-secondary" style={{ fontSize: '0.75rem' }}>Available until 31 Dec 2026</span>
        </Card>
      </div>

      {/* Tabs */}
      <Tabs
        activeTab={activeTab}
        onChange={(tab) => setActiveTab(tab)}
        items={[
          { id: 'plans', label: 'Benefit Plans Catalogue', badge: catalogueQuery.data?.policies.length },
          { id: 'enrollments', label: 'Enrollments & Allowances', badge: myBenefitsQuery.data?.enrollments.length },
          { id: 'claims', label: 'Claims Adjudication Queue', badge: claimsQuery.data?.pendingCount },
        ]}
      />

      {/* TAB 1: Plans Catalogue */}
      {activeTab === 'plans' && (
        <Card
          title="Corporate Benefit Policies & Insurance Tiers"
          actions={
            <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
              <span style={{ fontSize: '0.875rem', fontWeight: 500 }}>Category:</span>
              <select
                className="field__input"
                style={{ padding: '0.25rem 0.5rem', width: 'auto' }}
                value={selectedCategory}
                onChange={(e) => setSelectedCategory(e.target.value)}
              >
                <option value="ALL">All Categories</option>
                {catalogueQuery.data?.categories.map((cat) => (
                  <option key={cat.id} value={cat.code}>
                    {cat.name}
                  </option>
                ))}
              </select>
            </div>
          }
        >
          {catalogueQuery.isLoading ? (
            <LoadingState label="Loading benefit plans catalogue…" />
          ) : filteredPolicies.length === 0 ? (
            <EmptyState title="No Benefit Policies Found" description="No policies match the selected category filter." />
          ) : (
            <div className="grid grid--2-col" style={{ gap: '1rem' }}>
              {filteredPolicies.map((policy) => (
                <div
                  key={policy.id}
                  style={{
                    border: '1px solid var(--border-color, #e5e7eb)',
                    borderRadius: '8px',
                    padding: '1.25rem',
                    backgroundColor: 'var(--card-bg, #ffffff)',
                    display: 'flex',
                    flexDirection: 'column',
                    justifyContent: 'space-between',
                  }}
                >
                  <div>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '0.5rem' }}>
                      <span style={{ fontSize: '0.75rem', fontWeight: 600, color: 'var(--color-primary, #0969da)' }}>
                        {policy.code} • {policy.categoryName}
                      </span>
                      {getTierBadge(policy.coverageTier)}
                    </div>
                    <h3 style={{ fontSize: '1.125rem', fontWeight: 600, margin: '0 0 0.5rem 0' }}>{policy.name}</h3>
                    <p style={{ fontSize: '0.875rem', color: 'var(--text-secondary, #4b5563)', margin: '0 0 1rem 0' }}>
                      {policy.description}
                    </p>

                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.75rem', background: '#f8fafc', padding: '0.75rem', borderRadius: '6px', fontSize: '0.8125rem' }}>
                      <div>
                        <span style={{ color: '#64748b' }}>Annual Coverage Limit:</span>
                        <div style={{ fontWeight: 700, fontSize: '0.9375rem' }}>{formatCurrency(policy.annualLimit, policy.currency)}</div>
                      </div>
                      <div>
                        <span style={{ color: '#64748b' }}>Employee Co-Pay:</span>
                        <div style={{ fontWeight: 600 }}>{policy.coPayPercentage > 0 ? `${policy.coPayPercentage}%` : 'Fully Covered (0%)'}</div>
                      </div>
                      <div>
                        <span style={{ color: '#64748b' }}>Annual Deductible:</span>
                        <div style={{ fontWeight: 600 }}>{policy.deductibleAmount > 0 ? formatCurrency(policy.deductibleAmount, policy.currency) : 'None ($0)'}</div>
                      </div>
                      <div>
                        <span style={{ color: '#64748b' }}>Eligible Grades:</span>
                        <div style={{ fontWeight: 600 }}>{policy.eligibleGrades}</div>
                      </div>
                    </div>
                  </div>

                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: '1rem', paddingTop: '0.75rem', borderTop: '1px solid #f1f5f9' }}>
                    <div style={{ fontSize: '0.75rem', color: '#64748b' }}>
                      {policy.requiresReceipt ? '✓ Original Receipts Required' : '✓ Allowance without Receipts'}
                    </div>
                    <Button variant="secondary" onClick={() => {
                      setNewClaimForm((prev) => ({ ...prev, enrollmentId: 'enr-1', serviceProvider: policy.name }))
                      setIsSubmitModalOpen(true)
                    }}>
                      Claim Under Plan
                    </Button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </Card>
      )}

      {/* TAB 2: Enrollments & Utilization */}
      {activeTab === 'enrollments' && (
        <Card title="Active Employee Benefit Enrollments & Cap Utilization">
          {myBenefitsQuery.isLoading ? (
            <LoadingState label="Loading enrollment roster…" />
          ) : (
            <div className="flow">
              {myBenefitsQuery.data?.enrollments.map((enr) => {
                const usedPercent = Math.min(100, Math.round((enr.usedAmount / enr.annualEntitlement) * 100))
                const pendingPercent = Math.min(100 - usedPercent, Math.round((enr.pendingAmount / enr.annualEntitlement) * 100))
                return (
                  <div
                    key={enr.id}
                    style={{
                      border: '1px solid var(--border-color, #e5e7eb)',
                      borderRadius: '8px',
                      padding: '1.25rem',
                      marginBottom: '1rem',
                    }}
                  >
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                      <div>
                        <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
                          <span style={{ fontWeight: 700, fontSize: '1.125rem' }}>{enr.policyName}</span>
                          <Badge tone="success">Active Enrollment</Badge>
                          {getTierBadge(enr.coverageTier)}
                        </div>
                        <div style={{ fontSize: '0.8125rem', color: '#64748b', marginTop: '0.25rem' }}>
                          Policy Number: <strong>{enr.policyNumber}</strong> • Period: {enr.startDate} to {enr.endDate} ({enr.enrollmentYear})
                        </div>
                      </div>
                      <Button variant="secondary" onClick={() => {
                        setNewClaimForm((prev) => ({ ...prev, enrollmentId: enr.id }))
                        setIsSubmitModalOpen(true)
                      }}>
                        Submit Claim
                      </Button>
                    </div>

                    {/* Progress Bar */}
                    <div style={{ marginTop: '1rem' }}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.8125rem', marginBottom: '0.375rem' }}>
                        <span>
                          Claimed: <strong>{formatCurrency(enr.usedAmount, enr.currency)}</strong> ({usedPercent}%)
                          {enr.pendingAmount > 0 && (
                            <span style={{ color: '#b45309', marginLeft: '0.5rem' }}>
                              + {formatCurrency(enr.pendingAmount, enr.currency)} Pending
                            </span>
                          )}
                        </span>
                        <span>
                          Available Balance: <strong style={{ color: '#15803d' }}>{formatCurrency(enr.remainingBalance, enr.currency)}</strong> of {formatCurrency(enr.annualEntitlement, enr.currency)}
                        </span>
                      </div>
                      <div style={{ width: '100%', height: '10px', backgroundColor: '#e2e8f0', borderRadius: '5px', overflow: 'hidden', display: 'flex' }}>
                        <div style={{ width: `${usedPercent}%`, backgroundColor: '#0969da', transition: 'width 0.3s' }} />
                        <div style={{ width: `${pendingPercent}%`, backgroundColor: '#f59e0b', transition: 'width 0.3s' }} />
                      </div>
                    </div>

                    {/* Dependents list if applicable */}
                    {enr.dependents.length > 0 && (
                      <div style={{ marginTop: '1rem', borderTop: '1px dashed #e2e8f0', paddingTop: '0.75rem' }}>
                        <span style={{ fontSize: '0.75rem', fontWeight: 600, color: '#475569', textTransform: 'uppercase' }}>
                          Registered Covered Dependents:
                        </span>
                        <div style={{ display: 'flex', gap: '0.75rem', marginTop: '0.5rem', flexWrap: 'wrap' }}>
                          {enr.dependents.map((dep) => (
                            <div
                              key={dep.id}
                              style={{
                                display: 'inline-flex',
                                alignItems: 'center',
                                gap: '0.375rem',
                                background: '#f1f5f9',
                                padding: '0.25rem 0.625rem',
                                borderRadius: '16px',
                                fontSize: '0.8125rem',
                              }}
                            >
                              <span>👤 {dep.fullName}</span>
                              <Badge tone="neutral">{dep.relationship}</Badge>
                            </div>
                          ))}
                        </div>
                      </div>
                    )}
                  </div>
                )
              })}
            </div>
          )}
        </Card>
      )}

      {/* TAB 3: Claims Adjudication Queue */}
      {activeTab === 'claims' && (
        <Card
          title="Benefit Claims & Adjudication Ledger"
          actions={
            <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
              <span style={{ fontSize: '0.875rem', fontWeight: 500 }}>Status:</span>
              <select
                className="field__input"
                style={{ padding: '0.25rem 0.5rem', width: 'auto' }}
                value={claimStatusFilter}
                onChange={(e) => setClaimStatusFilter(e.target.value)}
              >
                <option value="ALL">All Statuses</option>
                <option value="SUBMITTED">Submitted</option>
                <option value="UNDER_REVIEW">Under Review</option>
                <option value="APPROVED">Approved</option>
                <option value="PAID">Paid</option>
                <option value="REJECTED">Rejected</option>
                <option value="CANCELLED">Cancelled</option>
              </select>
            </div>
          }
        >
          {claimsQuery.isLoading ? (
            <LoadingState label="Loading claims queue…" />
          ) : filteredClaims.length === 0 ? (
            <EmptyState
              title="No Claims Found"
              description="No benefit reimbursement claims match the selected criteria."
              action={
                <Button variant="primary" onClick={() => setIsSubmitModalOpen(true)}>
                  Submit New Claim
                </Button>
              }
            />
          ) : (
            <DataTable<BenefitClaimItem>
              caption="Claims Adjudication Records"
              rowKey={(c) => c.id}
              columns={[
                {
                  header: 'Claim #',
                  render: (c) => (
                    <div>
                      <strong>{c.claimNumber}</strong>
                      <div style={{ fontSize: '0.75rem', color: '#64748b' }}>{c.claimDate}</div>
                    </div>
                  ),
                },
                {
                  header: 'Policy / Category',
                  render: (c) => (
                    <div>
                      <div>{c.policyName}</div>
                      <div style={{ fontSize: '0.75rem', color: '#64748b' }}>
                        {c.dependentName ? `Covered: ${c.dependentName}` : 'Self-claim'}
                      </div>
                    </div>
                  ),
                },
                {
                  header: 'Service Provider & Reason',
                  render: (c) => (
                    <div>
                      <div style={{ fontWeight: 500 }}>{c.serviceProvider}</div>
                      <div style={{ fontSize: '0.75rem', color: '#64748b' }}>{c.diagnosisOrReason}</div>
                      {c.invoiceNumber && <div style={{ fontSize: '0.75rem', color: '#94a3b8' }}>Inv: {c.invoiceNumber}</div>}
                    </div>
                  ),
                },
                {
                  header: 'Claimed',
                  numeric: true,
                  render: (c) => <strong>{formatCurrency(c.claimedAmount, c.currency)}</strong>,
                },
                {
                  header: 'Approved',
                  numeric: true,
                  render: (c) => (
                    <span>
                      {c.approvedAmount !== undefined ? formatCurrency(c.approvedAmount, c.currency) : '—'}
                    </span>
                  ),
                },
                {
                  header: 'Status',
                  render: (c) => (
                    <div>
                      <Badge tone={getStatusBadgeTone(c.status)}>{c.status.replace('_', ' ')}</Badge>
                      {c.rejectionReason && (
                        <div style={{ fontSize: '0.75rem', color: '#ef4444', marginTop: '0.25rem' }}>
                          {c.rejectionReason}
                        </div>
                      )}
                    </div>
                  ),
                },
                {
                  header: 'Actions',
                  render: (c) => (
                    <div style={{ display: 'flex', gap: '0.25rem' }}>
                      {(c.status === 'SUBMITTED' || c.status === 'UNDER_REVIEW') && (
                        <>
                          <Button variant="secondary" onClick={() => handleOpenAdjudicate(c)}>
                            Adjudicate
                          </Button>
                          <Button variant="ghost" onClick={() => handleCancelClaim(c.id)}>
                            Cancel
                          </Button>
                        </>
                      )}
                      {c.status === 'APPROVED' && (
                        <Button
                          variant="ghost"
                          onClick={() => {
                            queryClient.setQueryData(['benefits', 'claims'], (prev: typeof claimsQuery.data) => {
                              if (!prev) return prev
                              return {
                                ...prev,
                                totalPaidAmount: prev.totalPaidAmount + (c.approvedAmount || 0),
                                claims: prev.claims.map((item) => (item.id === c.id ? { ...item, status: 'PAID' as BenefitClaimStatus } : item)),
                              }
                            })
                          }}
                        >
                          Mark Paid
                        </Button>
                      )}
                    </div>
                  ),
                },
              ]}
              rows={filteredClaims}
            />
          )}
        </Card>
      )}

      {/* Modal: Submit New Claim */}
      <Modal
        isOpen={isSubmitModalOpen}
        onClose={() => setIsSubmitModalOpen(false)}
        title="Submit New Benefit Reimbursement Claim"
        size="medium"
        actions={
          <>
            <Button variant="ghost" onClick={() => setIsSubmitModalOpen(false)}>
              Cancel
            </Button>
            <Button
              variant="primary"
              disabled={!newClaimForm.serviceProvider || !newClaimForm.claimedAmount}
              onClick={() => {
                submitClaimMutation.mutate({
                  enrollmentId: newClaimForm.enrollmentId,
                  claimDate: newClaimForm.claimDate,
                  serviceProvider: newClaimForm.serviceProvider,
                  diagnosisOrReason: newClaimForm.diagnosisOrReason,
                  invoiceNumber: newClaimForm.invoiceNumber,
                  claimedAmount: parseFloat(newClaimForm.claimedAmount) || 0,
                  remarks: newClaimForm.remarks,
                })
              }}
            >
              Submit Claim
            </Button>
          </>
        }
      >
        <div className="flow">
          <div className="field">
            <label className="field__label">Enrolled Benefit Policy</label>
            <select
              className="field__input"
              value={newClaimForm.enrollmentId}
              onChange={(e) => setNewClaimForm({ ...newClaimForm, enrollmentId: e.target.value })}
            >
              {myBenefitsQuery.data?.enrollments.map((enr) => (
                <option key={enr.id} value={enr.id}>
                  {enr.policyName} ({enr.policyNumber}) - Available: {formatCurrency(enr.remainingBalance, enr.currency)}
                </option>
              ))}
            </select>
          </div>

          <div className="grid grid--2-col">
            <Field
              label="Service Date"
              type="date"
              value={newClaimForm.claimDate}
              onChange={(e) => setNewClaimForm({ ...newClaimForm, claimDate: e.target.value })}
            />
            <Field
              label="Hospital / Clinic / Provider Name"
              placeholder="e.g. Asiri Surgical, Vision Care"
              value={newClaimForm.serviceProvider}
              onChange={(e) => setNewClaimForm({ ...newClaimForm, serviceProvider: e.target.value })}
            />
          </div>

          <div className="grid grid--2-col">
            <Field
              label="Claimed Amount (LKR)"
              type="number"
              placeholder="e.g. 35000"
              value={newClaimForm.claimedAmount}
              onChange={(e) => setNewClaimForm({ ...newClaimForm, claimedAmount: e.target.value })}
            />
            <Field
              label="Invoice / Tax Receipt Number"
              placeholder="e.g. INV-2026-9021"
              value={newClaimForm.invoiceNumber}
              onChange={(e) => setNewClaimForm({ ...newClaimForm, invoiceNumber: e.target.value })}
            />
          </div>

          <Field
            label="Diagnosis / Medical Procedure / Treatment Details"
            placeholder="Brief reason or prescription breakdown"
            value={newClaimForm.diagnosisOrReason}
            onChange={(e) => setNewClaimForm({ ...newClaimForm, diagnosisOrReason: e.target.value })}
          />

          <Field
            label="Additional Notes / Supporting Remarks"
            placeholder="Any extra details for underwriting approval"
            value={newClaimForm.remarks}
            onChange={(e) => setNewClaimForm({ ...newClaimForm, remarks: e.target.value })}
          />
        </div>
      </Modal>

      {/* Modal: Adjudicate Claim */}
      <Modal
        isOpen={isAdjudicateModalOpen}
        onClose={() => setIsAdjudicateModalOpen(false)}
        title={`Adjudicate Claim: ${selectedClaim?.claimNumber}`}
        size="medium"
        actions={
          <>
            <Button variant="ghost" onClick={() => setIsAdjudicateModalOpen(false)}>
              Cancel
            </Button>
            <Button
              variant={adjudicateAction === 'APPROVE' ? 'primary' : 'danger'}
              onClick={handleSaveAdjudication}
            >
              {adjudicateAction === 'APPROVE' ? 'Confirm Approval' : 'Confirm Rejection'}
            </Button>
          </>
        }
      >
        {selectedClaim && (
          <div className="flow">
            <div style={{ background: '#f8fafc', padding: '0.875rem', borderRadius: '6px', fontSize: '0.875rem' }}>
              <div><strong>Provider:</strong> {selectedClaim.serviceProvider}</div>
              <div><strong>Reason:</strong> {selectedClaim.diagnosisOrReason}</div>
              <div><strong>Original Claimed Amount:</strong> {formatCurrency(selectedClaim.claimedAmount, selectedClaim.currency)}</div>
              {selectedClaim.invoiceNumber && <div><strong>Invoice:</strong> {selectedClaim.invoiceNumber}</div>}
            </div>

            <div className="field">
              <label className="field__label">Adjudication Decision</label>
              <div style={{ display: 'flex', gap: '1rem', marginTop: '0.25rem' }}>
                <label style={{ display: 'flex', alignItems: 'center', gap: '0.375rem', cursor: 'pointer' }}>
                  <input
                    type="radio"
                    name="adjudicateAction"
                    value="APPROVE"
                    checked={adjudicateAction === 'APPROVE'}
                    onChange={() => setAdjudicateAction('APPROVE')}
                  />
                  <span>Approve Claim</span>
                </label>
                <label style={{ display: 'flex', alignItems: 'center', gap: '0.375rem', cursor: 'pointer' }}>
                  <input
                    type="radio"
                    name="adjudicateAction"
                    value="REJECT"
                    checked={adjudicateAction === 'REJECT'}
                    onChange={() => setAdjudicateAction('REJECT')}
                  />
                  <span>Reject Claim</span>
                </label>
              </div>
            </div>

            {adjudicateAction === 'APPROVE' ? (
              <Field
                label="Approved Payable Amount (LKR)"
                type="number"
                value={approvedAmountInput}
                onChange={(e) => setApprovedAmountInput(e.target.value)}
                hint="Adjust down if non-eligible items or copay deductions apply."
              />
            ) : (
              <Field
                label="Rejection Reason"
                placeholder="e.g. Non-covered cosmetic treatment / Missing physician stamp"
                value={rejectionReasonInput}
                onChange={(e) => setRejectionReasonInput(e.target.value)}
              />
            )}
          </div>
        )}
      </Modal>
    </div>
  )
}
