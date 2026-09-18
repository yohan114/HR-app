import { useQuery } from '@tanstack/react-query'
import { useCallback, useState } from 'react'
import { Badge, Button, LoadingState, Modal } from '@/components/ui'
import { QueryErrorState } from '@/components/QueryErrorState'
import { payrollApi, type PayslipDocument } from '@/lib/api'

interface OfficialPayslipModalProps {
  isOpen: boolean
  onClose: () => void
  runId: string
  resultId: string | null
  allResultIds?: string[]
  onSelectResultId?: (id: string) => void
}

export function OfficialPayslipModal({
  isOpen,
  onClose,
  runId,
  resultId,
  allResultIds = [],
  onSelectResultId,
}: OfficialPayslipModalProps) {
  const [copiedHash, setCopiedHash] = useState(false)

  const payslipQuery = useQuery({
    queryKey: ['payroll', 'payslip-document', runId, resultId],
    queryFn: () => payrollApi.getPayslipDocument(runId, resultId!),
    enabled: Boolean(isOpen && runId && resultId),
  })

  const payslip: PayslipDocument | undefined = payslipQuery.data

  const currentIndex = resultId && allResultIds.length > 0 ? allResultIds.indexOf(resultId) : -1
  const hasPrevious = currentIndex > 0
  const hasNext = currentIndex >= 0 && currentIndex < allResultIds.length - 1

  const handlePrev = useCallback(() => {
    const prevId = allResultIds[currentIndex - 1]
    if (hasPrevious && onSelectResultId && prevId) {
      onSelectResultId(prevId)
    }
  }, [hasPrevious, onSelectResultId, allResultIds, currentIndex])

  const handleNext = useCallback(() => {
    const nextId = allResultIds[currentIndex + 1]
    if (hasNext && onSelectResultId && nextId) {
      onSelectResultId(nextId)
    }
  }, [hasNext, onSelectResultId, allResultIds, currentIndex])

  // Native Print Handler
  const handlePrint = useCallback(() => {
    window.print()
  }, [])

  // Download self-contained offline HTML payslip
  const handleDownloadHtml = useCallback(() => {
    if (!payslip) return
    const printableElement = document.getElementById('official-payslip-sheet')
    if (!printableElement) return

    const htmlContent = `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <title>Payslip - ${payslip.employee.fullName} (${payslip.employee.employeeCode}) - ${payslip.period.payPeriodName}</title>
  <style>
    body {
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
      background-color: #f8fafc;
      color: #0f172a;
      margin: 0;
      padding: 24px;
    }
    .payslip-container {
      max-width: 840px;
      margin: 0 auto;
      background: #ffffff;
      padding: 32px;
      border: 1px solid #e2e8f0;
      border-radius: 8px;
      position: relative;
      box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1);
    }
    .watermark {
      position: absolute;
      top: 50%;
      left: 50%;
      transform: translate(-50%, -50%) rotate(-35deg);
      font-size: 3rem;
      font-weight: 800;
      color: rgba(15, 23, 42, 0.04);
      white-space: nowrap;
      pointer-events: none;
      user-select: none;
      z-index: 0;
    }
    table { width: 100%; border-collapse: collapse; }
    th, td { padding: 8px 12px; font-size: 0.8125rem; }
    .table-bordered th, .table-bordered td { border: 1px solid #e2e8f0; }
    .numeric { text-align: right; font-variant-numeric: tabular-nums; }
    @media print {
      body { background: transparent; padding: 0; }
      .payslip-container { border: none; box-shadow: none; padding: 0; max-width: 100%; }
      .no-print { display: none !important; }
      @page { size: A4 portrait; margin: 12mm; }
    }
  </style>
</head>
<body>
  <div class="payslip-container">
    <div class="watermark">${payslip.security.confidentialWatermark}</div>
    ${printableElement.innerHTML}
  </div>
</body>
</html>`

    const blob = new Blob([htmlContent], { type: 'text/html' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `Payslip_${payslip.employee.employeeCode}_${payslip.period.payPeriodCode}.html`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
  }, [payslip])

  const handleCopyHash = useCallback(() => {
    if (!payslip) return
    void navigator.clipboard.writeText(payslip.security.verificationHash)
    setCopiedHash(true)
    setTimeout(() => setCopiedHash(false), 2500)
  }, [payslip])

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title={
        payslip
          ? `Official Payslip: ${payslip.employee.fullName} (${payslip.employee.employeeCode})`
          : 'Official Employee Payslip'
      }
      size="large"
      actions={
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', width: '100%' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            {allResultIds.length > 1 && (
              <>
                <Button variant="secondary" onClick={handlePrev} disabled={!hasPrevious}>
                  ◀ Previous
                </Button>
                <span style={{ fontSize: '0.75rem', color: 'var(--color-on-surface-muted)' }}>
                  {currentIndex + 1} of {allResultIds.length}
                </span>
                <Button variant="secondary" onClick={handleNext} disabled={!hasNext}>
                  Next ▶
                </Button>
              </>
            )}
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <Button variant="secondary" onClick={handleDownloadHtml} disabled={!payslip}>
              💾 Download HTML
            </Button>
            <Button variant="primary" onClick={handlePrint} disabled={!payslip}>
              🖨️ Print / Save as PDF
            </Button>
            <Button variant="ghost" onClick={onClose}>
              Close
            </Button>
          </div>
        </div>
      }
    >
      {/* Print Stylesheet injection for pristine A4 exports */}
      <style>{`
        @media print {
          /* Hide UI chrome, modals backdrop, navigation, sidebars */
          body * {
            visibility: hidden;
          }
          #official-payslip-sheet, #official-payslip-sheet * {
            visibility: visible;
          }
          #official-payslip-sheet {
            position: absolute;
            left: 0;
            top: 0;
            width: 100%;
            background: #ffffff !important;
            color: #0f172a !important;
            padding: 0 !important;
            margin: 0 !important;
            border: none !important;
            box-shadow: none !important;
          }
          @page {
            size: A4 portrait;
            margin: 10mm;
          }
        }
      `}</style>

      {payslipQuery.isPending && <LoadingState label="Rendering official payslip certificate…" />}
      {payslipQuery.isError && (
        <QueryErrorState error={payslipQuery.error} onRetry={() => void payslipQuery.refetch()} />
      )}

      {payslip && (
        <div
          id="official-payslip-sheet"
          style={{
            position: 'relative',
            background: 'var(--color-surface, #ffffff)',
            color: 'var(--color-on-surface, #0f172a)',
            padding: '1.5rem',
            borderRadius: '8px',
            border: '1px solid var(--color-outline, #e2e8f0)',
            overflow: 'hidden',
          }}
        >
          {/* Subtle Security Diagonal Watermark Overlay */}
          <div
            style={{
              position: 'absolute',
              top: '50%',
              left: '50%',
              transform: 'translate(-50%, -50%) rotate(-32deg)',
              fontSize: '2.5rem',
              fontWeight: 800,
              color: 'rgba(148, 163, 184, 0.07)',
              letterSpacing: '0.15em',
              whiteSpace: 'nowrap',
              pointerEvents: 'none',
              userSelect: 'none',
              zIndex: 0,
            }}
          >
            {payslip.security.confidentialWatermark}
          </div>

          <div style={{ position: 'relative', zIndex: 1, display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
            {/* ----------------------------------------------------------------- */}
            {/* 1. Corporate Header / Letterhead                                  */}
            {/* ----------------------------------------------------------------- */}
            <div
              style={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'flex-start',
                paddingBottom: '1rem',
                borderBottom: '2px solid var(--color-outline, #cbd5e1)',
              }}
            >
              <div>
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.25rem' }}>
                  <span style={{ fontSize: '1.5rem' }}>🏛️</span>
                  <h2 style={{ margin: 0, fontSize: '1.25rem', fontWeight: 800, letterSpacing: '-0.02em' }}>
                    {payslip.company.legalName}
                  </h2>
                </div>
                <div style={{ fontSize: '0.75rem', color: 'var(--color-on-surface-muted, #64748b)', lineHeight: '1.4' }}>
                  <div>{payslip.company.registeredAddress}, {payslip.company.cityCountry}</div>
                  <div>
                    Reg No: <strong>{payslip.company.registrationNumber}</strong> · TIN:{' '}
                    <strong>{payslip.company.taxIdentificationNumber}</strong>
                  </div>
                  <div>
                    EPF Reg: <strong>{payslip.company.epfEmployerNumber}</strong> · ETF Reg:{' '}
                    <strong>{payslip.company.etfEmployerNumber}</strong> · Tel: {payslip.company.contactPhone}
                  </div>
                </div>
              </div>

              <div style={{ textAlign: 'right' }}>
                <div
                  style={{
                    display: 'inline-block',
                    padding: '0.25rem 0.75rem',
                    background: 'var(--color-brand-primary, #6366f1)',
                    color: '#ffffff',
                    fontWeight: 800,
                    fontSize: '0.875rem',
                    letterSpacing: '0.05em',
                    borderRadius: '4px',
                    marginBottom: '0.25rem',
                  }}
                >
                  SALARY PAYSLIP
                </div>
                <div style={{ fontWeight: 700, fontSize: '0.875rem' }}>{payslip.period.payPeriodName}</div>
                <div style={{ fontSize: '0.75rem', color: 'var(--color-on-surface-muted, #64748b)' }}>
                  Pay Date: <strong>{payslip.period.paymentDate}</strong>
                </div>
                <div style={{ marginTop: '0.25rem' }}>
                  <Badge tone={payslip.period.payrollStatus === 'PAID' ? 'success' : 'neutral'}>
                    {payslip.period.payrollStatus}
                  </Badge>
                </div>
              </div>
            </div>

            {/* ----------------------------------------------------------------- */}
            {/* 2. Employee Master Details Matrix                                 */}
            {/* ----------------------------------------------------------------- */}
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(2, 1fr)',
                gap: '0.5rem 1.5rem',
                padding: '0.75rem 1rem',
                background: 'var(--color-surface-variant, rgba(241, 245, 249, 0.7))',
                borderRadius: '6px',
                border: '1px solid var(--color-outline, #e2e8f0)',
                fontSize: '0.8125rem',
              }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ color: 'var(--color-on-surface-muted, #64748b)' }}>Employee Name:</span>
                <strong>{payslip.employee.fullName}</strong>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ color: 'var(--color-on-surface-muted, #64748b)' }}>Employee Code:</span>
                <code>{payslip.employee.employeeCode}</code>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ color: 'var(--color-on-surface-muted, #64748b)' }}>Designation:</span>
                <span>{payslip.employee.designation}</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ color: 'var(--color-on-surface-muted, #64748b)' }}>Department:</span>
                <span>{payslip.employee.department}</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ color: 'var(--color-on-surface-muted, #64748b)' }}>EPF/ETF Member No:</span>
                <span>{payslip.employee.epfMemberNumber}</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ color: 'var(--color-on-surface-muted, #64748b)' }}>NIC / Passport:</span>
                <span>{payslip.employee.nicPassportNumber}</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ color: 'var(--color-on-surface-muted, #64748b)' }}>Bank Account:</span>
                <span>
                  {payslip.employee.bankName} ({payslip.employee.bankAccountNumberMasked})
                </span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ color: 'var(--color-on-surface-muted, #64748b)' }}>Disbursement:</span>
                <span>{payslip.employee.paymentMethod}</span>
              </div>
            </div>

            {/* ----------------------------------------------------------------- */}
            {/* 3. Dual Ledger: Earnings vs Deductions                            */}
            {/* ----------------------------------------------------------------- */}
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(2, 1fr)',
                gap: '1rem',
                alignItems: 'start',
              }}
            >
              {/* Earnings Table */}
              <div style={{ border: '1px solid var(--color-outline, #e2e8f0)', borderRadius: '6px', overflow: 'hidden' }}>
                <div
                  style={{
                    padding: '0.5rem 0.75rem',
                    background: 'rgba(16, 185, 129, 0.08)',
                    borderBottom: '1px solid var(--color-outline, #e2e8f0)',
                    fontWeight: 700,
                    fontSize: '0.8125rem',
                    color: '#059669',
                    display: 'flex',
                    justifyContent: 'space-between',
                  }}
                >
                  <span>EARNINGS & ALLOWANCES</span>
                  <span>AMOUNT ({payslip.company.currency})</span>
                </div>
                <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.8125rem' }}>
                  <tbody>
                    {payslip.earnings.map((line, i) => (
                      <tr
                        key={line.id || i}
                        style={{ borderBottom: '1px solid var(--color-outline, #f1f5f9)' }}
                      >
                        <td style={{ padding: '0.5rem 0.75rem' }}>
                          <div style={{ fontWeight: 600 }}>{line.description}</div>
                          {line.calculationTrace && (
                            <div style={{ fontSize: '0.6875rem', color: 'var(--color-on-surface-muted, #64748b)' }}>
                              {line.calculationTrace}
                            </div>
                          )}
                        </td>
                        <td
                          style={{
                            padding: '0.5rem 0.75rem',
                            textAlign: 'right',
                            fontVariantNumeric: 'tabular-nums',
                            fontWeight: 600,
                            color: '#059669',
                          }}
                        >
                          +{line.amount.toLocaleString(undefined, { minimumFractionDigits: 2 })}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                  <tfoot>
                    <tr style={{ background: 'var(--color-surface-variant, #f8fafc)', fontWeight: 700 }}>
                      <td style={{ padding: '0.625rem 0.75rem' }}>Gross Total Earnings</td>
                      <td
                        style={{
                          padding: '0.625rem 0.75rem',
                          textAlign: 'right',
                          fontVariantNumeric: 'tabular-nums',
                          color: '#059669',
                          fontSize: '0.875rem',
                        }}
                      >
                        LKR {payslip.totals.grossEarnings.toLocaleString(undefined, { minimumFractionDigits: 2 })}
                      </td>
                    </tr>
                  </tfoot>
                </table>
              </div>

              {/* Deductions Table */}
              <div style={{ border: '1px solid var(--color-outline, #e2e8f0)', borderRadius: '6px', overflow: 'hidden' }}>
                <div
                  style={{
                    padding: '0.5rem 0.75rem',
                    background: 'rgba(239, 68, 68, 0.08)',
                    borderBottom: '1px solid var(--color-outline, #e2e8f0)',
                    fontWeight: 700,
                    fontSize: '0.8125rem',
                    color: '#dc2626',
                    display: 'flex',
                    justifyContent: 'space-between',
                  }}
                >
                  <span>DEDUCTIONS & TAXES</span>
                  <span>AMOUNT ({payslip.company.currency})</span>
                </div>
                <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.8125rem' }}>
                  <tbody>
                    {payslip.deductions.map((line, i) => (
                      <tr
                        key={line.id || i}
                        style={{ borderBottom: '1px solid var(--color-outline, #f1f5f9)' }}
                      >
                        <td style={{ padding: '0.5rem 0.75rem' }}>
                          <div style={{ fontWeight: 600 }}>{line.description}</div>
                          {line.calculationTrace && (
                            <div style={{ fontSize: '0.6875rem', color: 'var(--color-on-surface-muted, #64748b)' }}>
                              {line.calculationTrace}
                            </div>
                          )}
                        </td>
                        <td
                          style={{
                            padding: '0.5rem 0.75rem',
                            textAlign: 'right',
                            fontVariantNumeric: 'tabular-nums',
                            fontWeight: 600,
                            color: '#dc2626',
                          }}
                        >
                          -{line.amount.toLocaleString(undefined, { minimumFractionDigits: 2 })}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                  <tfoot>
                    <tr style={{ background: 'var(--color-surface-variant, #f8fafc)', fontWeight: 700 }}>
                      <td style={{ padding: '0.625rem 0.75rem' }}>Total Deductions</td>
                      <td
                        style={{
                          padding: '0.625rem 0.75rem',
                          textAlign: 'right',
                          fontVariantNumeric: 'tabular-nums',
                          color: '#dc2626',
                          fontSize: '0.875rem',
                        }}
                      >
                        LKR {payslip.totals.totalDeductions.toLocaleString(undefined, { minimumFractionDigits: 2 })}
                      </td>
                    </tr>
                  </tfoot>
                </table>
              </div>
            </div>

            {/* ----------------------------------------------------------------- */}
            {/* 4. Net Take-Home Pay Grand Banner                                 */}
            {/* ----------------------------------------------------------------- */}
            <div
              style={{
                padding: '1rem 1.25rem',
                background: 'linear-gradient(135deg, rgba(99, 102, 241, 0.1) 0%, rgba(56, 189, 248, 0.08) 100%)',
                border: '1.5px solid var(--color-brand-primary, #6366f1)',
                borderRadius: '8px',
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                flexWrap: 'wrap',
                gap: '1rem',
              }}
            >
              <div>
                <span
                  style={{
                    fontSize: '0.75rem',
                    fontWeight: 700,
                    textTransform: 'uppercase',
                    letterSpacing: '0.05em',
                    color: 'var(--color-brand-primary, #6366f1)',
                  }}
                >
                  Net Take-Home Pay
                </span>
                <div
                  style={{
                    fontSize: '1.875rem',
                    fontWeight: 900,
                    color: 'var(--color-brand-primary, #6366f1)',
                    fontVariantNumeric: 'tabular-nums',
                    lineHeight: '1.2',
                  }}
                >
                  {payslip.company.currency} {payslip.totals.netPay.toLocaleString(undefined, { minimumFractionDigits: 2 })}
                </div>
                <div style={{ fontSize: '0.8125rem', color: 'var(--color-on-surface-muted, #64748b)', marginTop: '0.25rem' }}>
                  <strong>In Words: </strong>
                  <em>{payslip.totals.netPayInWords}</em>
                </div>
              </div>

              <div
                style={{
                  textAlign: 'right',
                  fontSize: '0.75rem',
                  color: 'var(--color-on-surface-muted, #64748b)',
                  borderLeft: '1px solid rgba(99, 102, 241, 0.2)',
                  paddingLeft: '1rem',
                }}
              >
                <div>Gross Earnings: <strong>LKR {payslip.totals.grossEarnings.toLocaleString(undefined, { minimumFractionDigits: 2 })}</strong></div>
                <div>Total Deductions: <strong>LKR {payslip.totals.totalDeductions.toLocaleString(undefined, { minimumFractionDigits: 2 })}</strong></div>
                <div>Disbursed via: <strong>{payslip.employee.paymentMethod}</strong></div>
              </div>
            </div>

            {/* ----------------------------------------------------------------- */}
            {/* 5. Employer Statutory Contributions & Cost to Company (CTC)       */}
            {/* ----------------------------------------------------------------- */}
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(4, 1fr)',
                gap: '0.75rem',
                padding: '0.75rem 1rem',
                background: 'var(--color-surface-variant, #f8fafc)',
                border: '1px solid var(--color-outline, #e2e8f0)',
                borderRadius: '6px',
                fontSize: '0.75rem',
              }}
            >
              <div>
                <span style={{ color: 'var(--color-on-surface-muted, #64748b)' }}>EPF Employer (12%):</span>
                <div style={{ fontWeight: 700, fontVariantNumeric: 'tabular-nums', color: '#2563eb' }}>
                  LKR {payslip.totals.employerEpf.toLocaleString(undefined, { minimumFractionDigits: 2 })}
                </div>
              </div>
              <div>
                <span style={{ color: 'var(--color-on-surface-muted, #64748b)' }}>ETF Employer (3%):</span>
                <div style={{ fontWeight: 700, fontVariantNumeric: 'tabular-nums', color: '#2563eb' }}>
                  LKR {payslip.totals.employerEtf.toLocaleString(undefined, { minimumFractionDigits: 2 })}
                </div>
              </div>
              <div>
                <span style={{ color: 'var(--color-on-surface-muted, #64748b)' }}>Total Employer Statutory:</span>
                <div style={{ fontWeight: 700, fontVariantNumeric: 'tabular-nums', color: '#2563eb' }}>
                  LKR {payslip.totals.totalEmployerContributions.toLocaleString(undefined, { minimumFractionDigits: 2 })}
                </div>
              </div>
              <div>
                <span style={{ color: 'var(--color-on-surface-muted, #64748b)' }}>Total Cost To Company (CTC):</span>
                <div style={{ fontWeight: 800, fontVariantNumeric: 'tabular-nums', color: 'var(--color-on-surface, #0f172a)' }}>
                  LKR {payslip.totals.totalCostToCompany.toLocaleString(undefined, { minimumFractionDigits: 2 })}
                </div>
              </div>
            </div>

            {/* ----------------------------------------------------------------- */}
            {/* 6. Year-To-Date (YTD) Cumulative Metrics                          */}
            {/* ----------------------------------------------------------------- */}
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(5, 1fr)',
                gap: '0.5rem',
                padding: '0.625rem 1rem',
                border: '1px dashed var(--color-outline, #cbd5e1)',
                borderRadius: '6px',
                fontSize: '0.6875rem',
                color: 'var(--color-on-surface-muted, #64748b)',
              }}
            >
              <div>
                <span>YTD Gross Pay:</span>
                <div style={{ fontWeight: 700, color: 'var(--color-on-surface, #0f172a)' }}>
                  LKR {payslip.ytd.ytdGrossPay.toLocaleString(undefined, { minimumFractionDigits: 2 })}
                </div>
              </div>
              <div>
                <span>YTD APIT Tax:</span>
                <div style={{ fontWeight: 700, color: 'var(--color-on-surface, #0f172a)' }}>
                  LKR {payslip.ytd.ytdTaxWithheld.toLocaleString(undefined, { minimumFractionDigits: 2 })}
                </div>
              </div>
              <div>
                <span>YTD Employee EPF (8%):</span>
                <div style={{ fontWeight: 700, color: 'var(--color-on-surface, #0f172a)' }}>
                  LKR {payslip.ytd.ytdEmployeeEpf.toLocaleString(undefined, { minimumFractionDigits: 2 })}
                </div>
              </div>
              <div>
                <span>YTD Employer EPF (12%):</span>
                <div style={{ fontWeight: 700, color: 'var(--color-on-surface, #0f172a)' }}>
                  LKR {payslip.ytd.ytdEmployerEpf.toLocaleString(undefined, { minimumFractionDigits: 2 })}
                </div>
              </div>
              <div>
                <span>YTD Net Disbursed:</span>
                <div style={{ fontWeight: 700, color: 'var(--color-on-surface, #0f172a)' }}>
                  LKR {payslip.ytd.ytdNetPay.toLocaleString(undefined, { minimumFractionDigits: 2 })}
                </div>
              </div>
            </div>

            {/* ----------------------------------------------------------------- */}
            {/* 7. Cryptographic Verification Digital Seal & Signatory Footer     */}
            {/* ----------------------------------------------------------------- */}
            <div
              style={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                paddingTop: '0.75rem',
                borderTop: '1px solid var(--color-outline, #e2e8f0)',
                gap: '1rem',
                flexWrap: 'wrap',
              }}
            >
              {/* QR Code & Tamper-Evident Seal */}
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
                <div
                  dangerouslySetInnerHTML={{ __html: payslip.security.qrCodeSvg }}
                  style={{
                    width: '64px',
                    height: '64px',
                    border: '1px solid #cbd5e1',
                    borderRadius: '4px',
                    padding: '2px',
                    background: '#ffffff',
                  }}
                />
                <div style={{ fontSize: '0.6875rem', color: 'var(--color-on-surface-muted, #64748b)' }}>
                  <div style={{ fontWeight: 700, color: 'var(--color-on-surface, #0f172a)' }}>
                    🔒 CRYPTOGRAPHICALLY VERIFIED PAYSLIP
                  </div>
                  <div style={{ fontFamily: 'monospace', fontSize: '0.625rem', marginTop: '2px' }}>
                    Seal: {payslip.security.verificationHash}
                  </div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginTop: '4px' }}>
                    <button
                      type="button"
                      onClick={handleCopyHash}
                      style={{
                        background: 'none',
                        border: 'none',
                        padding: 0,
                        color: 'var(--color-brand-primary, #6366f1)',
                        cursor: 'pointer',
                        fontSize: '0.625rem',
                        textDecoration: 'underline',
                      }}
                    >
                      {copiedHash ? '✓ Hash Copied!' : 'Copy Verification Hash'}
                    </button>
                    <span>·</span>
                    <span>Generated: {new Date(payslip.security.generatedAt).toLocaleDateString()}</span>
                  </div>
                </div>
              </div>

              {/* Authorized Signatory & Legal Statement */}
              <div style={{ textAlign: 'right', fontSize: '0.75rem', color: 'var(--color-on-surface-muted, #64748b)' }}>
                <div style={{ fontWeight: 700, color: 'var(--color-on-surface, #0f172a)' }}>
                  {payslip.security.authorizedSignatory}
                </div>
                <div>{payslip.security.signatoryTitle}</div>
                <div style={{ fontSize: '0.6875rem', fontStyle: 'italic', marginTop: '2px' }}>
                  Computer-generated document under the Electronic Transactions Act.
                </div>
              </div>
            </div>
          </div>
        </div>
      )}
    </Modal>
  )
}
