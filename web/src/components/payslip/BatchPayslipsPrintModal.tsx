import { useQuery } from '@tanstack/react-query'
import { useCallback, useState } from 'react'
import { Badge, Button, LoadingState, Modal } from '@/components/ui'
import { QueryErrorState } from '@/components/QueryErrorState'
import { payrollApi, type PayslipDocument } from '@/lib/api'

interface BatchPayslipsPrintModalProps {
  isOpen: boolean
  onClose: () => void
  runId: string
  payPeriodName?: string
}

export function BatchPayslipsPrintModal({
  isOpen,
  onClose,
  runId,
  payPeriodName = 'Current Payroll Cycle',
}: BatchPayslipsPrintModalProps) {
  const [selectedDept, setSelectedDept] = useState<string>('ALL')

  const batchQuery = useQuery({
    queryKey: ['payroll', 'payslips-batch', runId, selectedDept],
    queryFn: () => payrollApi.getPayslipBatch(runId, selectedDept),
    enabled: Boolean(isOpen && runId),
  })

  const payslips: PayslipDocument[] = batchQuery.data?.payslips ?? []

  const handlePrintAll = useCallback(() => {
    window.print()
  }, [])

  const handleDownloadBundleHtml = useCallback(() => {
    if (payslips.length === 0) return

    const container = document.getElementById('batch-payslips-print-container')
    if (!container) return

    const html = `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <title>Batch Payslips - ${payPeriodName}</title>
  <style>
    body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; margin: 0; padding: 20px; background: #f8fafc; }
    .payslip-page { page-break-after: always; background: #ffffff; padding: 24px; margin-bottom: 24px; border: 1px solid #e2e8f0; border-radius: 8px; position: relative; }
    .watermark { position: absolute; top: 50%; left: 50%; transform: translate(-50%, -50%) rotate(-32deg); font-size: 2.5rem; font-weight: 800; color: rgba(148, 163, 184, 0.07); pointer-events: none; }
    @media print {
      body { background: transparent; padding: 0; }
      .payslip-page { border: none; margin: 0; padding: 0; page-break-after: always; }
      @page { size: A4 portrait; margin: 10mm; }
    }
  </style>
</head>
<body>
  ${container.innerHTML}
</body>
</html>`

    const blob = new Blob([html], { type: 'text/html' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `Batch_Payslips_${runId}_${selectedDept}.html`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
  }, [payslips, runId, selectedDept, payPeriodName])

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title={`Batch Payslips Export (${payslips.length} Employees) - ${payPeriodName}`}
      size="large"
      actions={
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', width: '100%' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
            <span style={{ fontSize: '0.8125rem', color: 'var(--color-on-surface-muted)' }}>Department:</span>
            <select
              className="field__input"
              style={{ minHeight: '32px', padding: '0 0.5rem', fontSize: '0.8125rem' }}
              value={selectedDept}
              onChange={(e) => setSelectedDept(e.target.value)}
            >
              <option value="ALL">All Departments ({payslips.length})</option>
              <option value="ENG">Engineering</option>
              <option value="HR">People & Culture</option>
              <option value="FIN">Finance</option>
              <option value="SLS">Sales</option>
              <option value="OPS">Operations</option>
            </select>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <Button variant="secondary" onClick={handleDownloadBundleHtml} disabled={payslips.length === 0}>
              💾 Download Batch HTML
            </Button>
            <Button variant="primary" onClick={handlePrintAll} disabled={payslips.length === 0}>
              🖨️ Print All Payslips (Multi-Page PDF)
            </Button>
            <Button variant="ghost" onClick={onClose}>
              Close
            </Button>
          </div>
        </div>
      }
    >
      {/* Print stylesheet for multi-page batch output */}
      <style>{`
        @media print {
          body * {
            visibility: hidden;
          }
          #batch-payslips-print-container, #batch-payslips-print-container * {
            visibility: visible;
          }
          #batch-payslips-print-container {
            position: absolute;
            left: 0;
            top: 0;
            width: 100%;
            background: transparent !important;
            padding: 0 !important;
            margin: 0 !important;
          }
          .batch-payslip-sheet {
            page-break-after: always !important;
            break-after: page !important;
            border: none !important;
            box-shadow: none !important;
            padding: 10mm !important;
            background: #ffffff !important;
          }
          @page {
            size: A4 portrait;
            margin: 0;
          }
        }
      `}</style>

      {batchQuery.isPending && <LoadingState label="Compiling batch payslip records…" />}
      {batchQuery.isError && (
        <QueryErrorState error={batchQuery.error} onRetry={() => void batchQuery.refetch()} />
      )}

      {batchQuery.isSuccess && (
        <div
          id="batch-payslips-print-container"
          style={{
            display: 'flex',
            flexDirection: 'column',
            gap: '1.5rem',
            maxHeight: '650px',
            overflowY: 'auto',
            paddingRight: '0.5rem',
          }}
        >
          {payslips.map((ps) => (
            <div
              key={ps.id}
              className="batch-payslip-sheet"
              style={{
                position: 'relative',
                background: 'var(--color-surface, #ffffff)',
                color: 'var(--color-on-surface, #0f172a)',
                padding: '1.25rem',
                borderRadius: '8px',
                border: '1px solid var(--color-outline, #e2e8f0)',
                overflow: 'hidden',
              }}
            >
              {/* Diagonal Watermark */}
              <div
                style={{
                  position: 'absolute',
                  top: '50%',
                  left: '50%',
                  transform: 'translate(-50%, -50%) rotate(-32deg)',
                  fontSize: '2rem',
                  fontWeight: 800,
                  color: 'rgba(148, 163, 184, 0.06)',
                  letterSpacing: '0.15em',
                  whiteSpace: 'nowrap',
                  pointerEvents: 'none',
                  userSelect: 'none',
                  zIndex: 0,
                }}
              >
                {ps.security.confidentialWatermark}
              </div>

              <div style={{ position: 'relative', zIndex: 1, display: 'flex', flexDirection: 'column', gap: '0.875rem' }}>
                {/* Header */}
                <div
                  style={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'flex-start',
                    paddingBottom: '0.75rem',
                    borderBottom: '1.5px solid var(--color-outline, #cbd5e1)',
                  }}
                >
                  <div>
                    <h3 style={{ margin: 0, fontSize: '1rem', fontWeight: 800 }}>
                      {ps.company.legalName}
                    </h3>
                    <div style={{ fontSize: '0.6875rem', color: 'var(--color-on-surface-muted, #64748b)' }}>
                      Reg: {ps.company.registrationNumber} · TIN: {ps.company.taxIdentificationNumber} · EPF:{' '}
                      {ps.company.epfEmployerNumber} · ETF: {ps.company.etfEmployerNumber}
                    </div>
                  </div>
                  <div style={{ textAlign: 'right' }}>
                    <div style={{ fontWeight: 700, fontSize: '0.8125rem' }}>{ps.period.payPeriodName}</div>
                    <div style={{ fontSize: '0.6875rem', color: 'var(--color-on-surface-muted, #64748b)' }}>
                      Pay Date: {ps.period.paymentDate}
                    </div>
                  </div>
                </div>

                {/* Employee Matrix */}
                <div
                  style={{
                    display: 'grid',
                    gridTemplateColumns: 'repeat(4, 1fr)',
                    gap: '0.375rem 0.75rem',
                    padding: '0.5rem 0.75rem',
                    background: 'var(--color-surface-variant, rgba(241, 245, 249, 0.7))',
                    borderRadius: '4px',
                    fontSize: '0.75rem',
                  }}
                >
                  <div>
                    <span style={{ color: 'var(--color-on-surface-muted)' }}>Employee: </span>
                    <strong>{ps.employee.fullName}</strong>
                  </div>
                  <div>
                    <span style={{ color: 'var(--color-on-surface-muted)' }}>Code: </span>
                    <code>{ps.employee.employeeCode}</code>
                  </div>
                  <div>
                    <span style={{ color: 'var(--color-on-surface-muted)' }}>Dept: </span>
                    <span>{ps.employee.department}</span>
                  </div>
                  <div>
                    <span style={{ color: 'var(--color-on-surface-muted)' }}>NIC: </span>
                    <span>{ps.employee.nicPassportNumber}</span>
                  </div>
                </div>

                {/* Dual Ledger */}
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.75rem' }}>
                  {/* Earnings */}
                  <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.75rem' }}>
                    <thead>
                      <tr style={{ background: 'rgba(16, 185, 129, 0.08)', color: '#059669', fontWeight: 700 }}>
                        <th style={{ textAlign: 'left', padding: '4px 8px' }}>Earnings</th>
                        <th style={{ textAlign: 'right', padding: '4px 8px' }}>LKR</th>
                      </tr>
                    </thead>
                    <tbody>
                      {ps.earnings.map((e, i) => (
                        <tr key={i} style={{ borderBottom: '1px solid #f1f5f9' }}>
                          <td style={{ padding: '3px 8px' }}>{e.description}</td>
                          <td style={{ textAlign: 'right', padding: '3px 8px', color: '#059669', fontWeight: 600 }}>
                            +{e.amount.toLocaleString(undefined, { minimumFractionDigits: 2 })}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                    <tfoot>
                      <tr style={{ fontWeight: 700, borderTop: '1px solid #cbd5e1' }}>
                        <td style={{ padding: '4px 8px' }}>Gross Earnings</td>
                        <td style={{ textAlign: 'right', padding: '4px 8px', color: '#059669' }}>
                          LKR {ps.totals.grossEarnings.toLocaleString(undefined, { minimumFractionDigits: 2 })}
                        </td>
                      </tr>
                    </tfoot>
                  </table>

                  {/* Deductions */}
                  <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.75rem' }}>
                    <thead>
                      <tr style={{ background: 'rgba(239, 68, 68, 0.08)', color: '#dc2626', fontWeight: 700 }}>
                        <th style={{ textAlign: 'left', padding: '4px 8px' }}>Deductions</th>
                        <th style={{ textAlign: 'right', padding: '4px 8px' }}>LKR</th>
                      </tr>
                    </thead>
                    <tbody>
                      {ps.deductions.map((d, i) => (
                        <tr key={i} style={{ borderBottom: '1px solid #f1f5f9' }}>
                          <td style={{ padding: '3px 8px' }}>{d.description}</td>
                          <td style={{ textAlign: 'right', padding: '3px 8px', color: '#dc2626', fontWeight: 600 }}>
                            -{d.amount.toLocaleString(undefined, { minimumFractionDigits: 2 })}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                    <tfoot>
                      <tr style={{ fontWeight: 700, borderTop: '1px solid #cbd5e1' }}>
                        <td style={{ padding: '4px 8px' }}>Total Deductions</td>
                        <td style={{ textAlign: 'right', padding: '4px 8px', color: '#dc2626' }}>
                          LKR {ps.totals.totalDeductions.toLocaleString(undefined, { minimumFractionDigits: 2 })}
                        </td>
                      </tr>
                    </tfoot>
                  </table>
                </div>

                {/* Net Pay Box */}
                <div
                  style={{
                    padding: '0.5rem 0.75rem',
                    background: 'rgba(99, 102, 241, 0.06)',
                    border: '1px solid var(--color-brand-primary, #6366f1)',
                    borderRadius: '4px',
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                  }}
                >
                  <div>
                    <span style={{ fontSize: '0.6875rem', color: 'var(--color-on-surface-muted)' }}>
                      Net Take-Home Pay:
                    </span>
                    <strong
                      style={{
                        marginLeft: '0.5rem',
                        fontSize: '1rem',
                        color: 'var(--color-brand-primary, #6366f1)',
                        fontVariantNumeric: 'tabular-nums',
                      }}
                    >
                      LKR {ps.totals.netPay.toLocaleString(undefined, { minimumFractionDigits: 2 })}
                    </strong>
                    <div style={{ fontSize: '0.6875rem', fontStyle: 'italic', color: 'var(--color-on-surface-muted)' }}>
                      {ps.totals.netPayInWords}
                    </div>
                  </div>

                  <div style={{ fontSize: '0.6875rem', textAlign: 'right', color: 'var(--color-on-surface-muted)' }}>
                    <div>EPF Er (12%): LKR {ps.totals.employerEpf.toLocaleString()} · ETF (3%): LKR {ps.totals.employerEtf.toLocaleString()}</div>
                    <div>Total CTC: <strong>LKR {ps.totals.totalCostToCompany.toLocaleString()}</strong></div>
                  </div>
                </div>

                {/* Security Footer */}
                <div
                  style={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                    fontSize: '0.625rem',
                    color: 'var(--color-on-surface-muted, #64748b)',
                    paddingTop: '0.375rem',
                    borderTop: '1px solid #f1f5f9',
                  }}
                >
                  <div>🔒 Seal: {ps.security.verificationHash}</div>
                  <div>Authorized Signatory: {ps.security.authorizedSignatory} ({ps.security.signatoryTitle})</div>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </Modal>
  )
}
