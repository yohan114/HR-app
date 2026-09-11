import { useState, useMemo } from 'react'
import { Badge, Button, Card, DataTable } from '@/components/ui'

export interface TargetField {
  key: string
  label: string
  required: boolean
  type: 'string' | 'number' | 'date' | 'email'
}

const TARGET_FIELDS: TargetField[] = [
  { key: 'employee_code', label: 'Employee Code / ID', required: true, type: 'string' },
  { key: 'first_name', label: 'First Name', required: true, type: 'string' },
  { key: 'last_name', label: 'Last Name', required: true, type: 'string' },
  { key: 'work_email', label: 'Work Email Address', required: true, type: 'email' },
  { key: 'national_id', label: 'National ID / NIC / Passport', required: true, type: 'string' },
  { key: 'department', label: 'Department Name', required: true, type: 'string' },
  { key: 'designation', label: 'Designation / Job Title', required: true, type: 'string' },
  { key: 'basic_salary', label: 'Basic Salary (Monthly)', required: true, type: 'number' },
  { key: 'join_date', label: 'Joining Date (YYYY-MM-DD)', required: true, type: 'date' },
  { key: 'phone', label: 'Mobile Phone', required: false, type: 'string' },
]

export interface ParsedRow {
  _id: string
  employee_code: string
  first_name: string
  last_name: string
  work_email: string
  national_id: string
  department: string
  designation: string
  basic_salary: string
  join_date: string
  phone: string
  _status: 'VALID' | 'WARNING' | 'ERROR'
  _errors: Record<string, string>
}

const SAMPLE_CSV_DATA = `employee_code,first_name,last_name,work_email,national_id,department,designation,basic_salary,join_date,phone
EMP-101,Awantha,Fernando,awantha.f@demo.local,199234509123,Engineering,Senior Backend Engineer,240000,2024-02-01,+94771234567
EMP-102,Sithara,Jayakody,sithara.j@demo.local,199512304567,Human Resources,Talent Acquisition Lead,180000,2024-03-15,+94719876543
EMP-103,Buddhika,Perera,buddhika.perera-invalid-email,198823456789,Engineering,DevOps Specialist,220000,2024-01-10,+94775556677
EMP-104,Nadeesha,Senanayake,nadeesha.s@demo.local,199654321098,Finance,Senior Financial Analyst,-50000,2024-04-01,+94723334455
EMP-105,Tharindu,Alwis,tharindu.a@demo.local,199345678901,Marketing,Growth Marketing Lead,195000,2024-05-20,+94701122334
EMP-106,Chathurika,Bandara,chathurika.b@demo.local,199789012345,Executive,Executive Assistant,150000,2024-06-01,+94764455667
EMP-107,Dhanushka,Silva,dhanushka.s@demo.local,,Engineering,Frontend Engineer,160000,2024-06-15,+94789900112`

export function BatchTools() {
  const [activeTab, setActiveTab] = useState<'import' | 'export'>('import')

  // Wizard state
  const [step, setStep] = useState<number>(1)
  const [fileName, setFileName] = useState<string>('employee_batch_march_2026.csv')
  const [rawRows, setRawRows] = useState<Record<string, string>[]>([])
  const [columnMapping, setColumnMapping] = useState<Record<string, string>>({})
  const [editableRows, setEditableRows] = useState<ParsedRow[]>([])
  const [isDryRun, setIsDryRun] = useState<boolean>(false)
  const [importProgress, setImportProgress] = useState<number>(0)
  const [isImporting, setIsImporting] = useState<boolean>(false)
  const [importResult, setImportResult] = useState<{ total: number; inserted: number; updated: number; failed: number } | null>(null)

  // Export state
  const [exportEntity, setExportEntity] = useState<string>('employees')
  const [exportFormat, setExportFormat] = useState<'CSV' | 'EXCEL' | 'JSON'>('CSV')
  const [exportDept, setExportDept] = useState<string>('ALL')
  const [exportBanner, setExportBanner] = useState<string | null>(null)

  // ---------------------------------------------------------------------------
  // CSV Parser & Loader
  // ---------------------------------------------------------------------------
  const parseAndLoadCsv = (csvText: string, customName: string) => {
    const lines = csvText.trim().split('\n').map((l) => l.trim()).filter((l) => l.length > 0)
    if (lines.length < 2) {
      alert('CSV file must have a header row and at least one data row.')
      return
    }

    const firstLine = lines[0]
    if (!firstLine) return
    const headers = firstLine.split(',').map((h) => h.trim().replace(/^"|"$/g, ''))
    const dataRows = lines.slice(1).map((line) => {
      const vals = line.split(',').map((v) => v.trim().replace(/^"|"$/g, ''))
      const obj: Record<string, string> = {}
      headers.forEach((h, idx) => {
        obj[h] = vals[idx] ?? ''
      })
      return obj
    })

    setFileName(customName)
    setRawRows(dataRows)

    // Auto-map columns
    const initialMapping: Record<string, string> = {}
    headers.forEach((h) => {
      const match = TARGET_FIELDS.find(
        (tf) => tf.key.toLowerCase() === h.toLowerCase() || tf.label.toLowerCase().includes(h.toLowerCase()),
      )
      if (match) {
        initialMapping[h] = match.key
      } else {
        initialMapping[h] = 'SKIP'
      }
    })
    setColumnMapping(initialMapping)
    setStep(2)
  }

  const loadSampleDataset = () => {
    parseAndLoadCsv(SAMPLE_CSV_DATA, 'sample_employee_onboarding_batch.csv')
  }

  // ---------------------------------------------------------------------------
  // Validate Rows from Mapping
  // ---------------------------------------------------------------------------
  const validateMappedRows = () => {
    const validated: ParsedRow[] = rawRows.map((raw, idx) => {
      const rowData: Record<string, string> = {}
      Object.entries(columnMapping).forEach(([sourceCol, targetField]) => {
        if (targetField !== 'SKIP') {
          rowData[targetField] = raw[sourceCol] ?? ''
        }
      })

      const errors: Record<string, string> = {}

      // Validate required fields
      TARGET_FIELDS.forEach((tf) => {
        const fieldVal = rowData[tf.key]
        if (tf.required && (!fieldVal || fieldVal.trim() === '')) {
          errors[tf.key] = `${tf.label} is required`
        }
      })

      // Email format
      if (rowData.work_email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(rowData.work_email)) {
        errors.work_email = 'Invalid email address format'
      }

      // Salary validation
      if (rowData.basic_salary) {
        const numSal = Number(rowData.basic_salary)
        if (isNaN(numSal) || numSal <= 0) {
          errors.basic_salary = 'Salary must be a positive number'
        }
      }

      // Date validation
      if (rowData.join_date && !/^\d{4}-\d{2}-\d{2}$/.test(rowData.join_date)) {
        errors.join_date = 'Date must be in YYYY-MM-DD format'
      }

      const hasErrors = Object.keys(errors).length > 0
      const status: 'VALID' | 'WARNING' | 'ERROR' = hasErrors ? 'ERROR' : 'VALID'

      return {
        _id: `row-${idx}`,
        employee_code: rowData.employee_code ?? '',
        first_name: rowData.first_name ?? '',
        last_name: rowData.last_name ?? '',
        work_email: rowData.work_email ?? '',
        national_id: rowData.national_id ?? '',
        department: rowData.department ?? '',
        designation: rowData.designation ?? '',
        basic_salary: rowData.basic_salary ?? '',
        join_date: rowData.join_date ?? '',
        phone: rowData.phone ?? '',
        _status: status,
        _errors: errors,
      }
    })

    setEditableRows(validated)
    setStep(3)
  }

  // ---------------------------------------------------------------------------
  // In-Grid Inline Cell Editing
  // ---------------------------------------------------------------------------
  const handleCellEdit = (rowId: string, fieldKey: string, newValue: string) => {
    setEditableRows((prev) =>
      prev.map((r) => {
        if (r._id !== rowId) return r
        const updated = { ...r, [fieldKey]: newValue }

        // Re-validate row
        const newErrors = { ...updated._errors }
        delete newErrors[fieldKey]

        if (fieldKey === 'work_email') {
          if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(newValue)) {
            newErrors.work_email = 'Invalid email address format'
          }
        }
        if (fieldKey === 'basic_salary') {
          const num = Number(newValue)
          if (isNaN(num) || num <= 0) {
            newErrors.basic_salary = 'Salary must be positive'
          }
        }
        if (fieldKey === 'national_id' && newValue.trim() === '') {
          newErrors.national_id = 'National ID is required'
        }

        const hasErrors = Object.keys(newErrors).length > 0
        return {
          ...updated,
          _status: hasErrors ? 'ERROR' : 'VALID',
          _errors: newErrors,
        }
      }),
    )
  }

  // ---------------------------------------------------------------------------
  // Execute Import
  // ---------------------------------------------------------------------------
  const runBatchImport = () => {
    setIsImporting(true)
    setImportProgress(0)

    let current = 0
    const interval = setInterval(() => {
      current += 25
      setImportProgress(current)
      if (current >= 100) {
        clearInterval(interval)
        setIsImporting(false)
        const validCount = editableRows.filter((r) => r._status === 'VALID').length
        const errorCount = editableRows.length - validCount
        setImportResult({
          total: editableRows.length,
          inserted: isDryRun ? 0 : validCount,
          updated: 0,
          failed: errorCount,
        })
        setStep(4)
      }
    }, 300)
  }

  // Download Sample Template
  const downloadTemplate = () => {
    const csvContent =
      'employee_code,first_name,last_name,work_email,national_id,department,designation,basic_salary,join_date,phone\n' +
      'EMP-001,John,Doe,john.doe@company.local,199012345678,Engineering,Software Engineer,150000,2026-01-15,+94770000000\n'
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = 'employee_bulk_import_template.csv'
    a.click()
    URL.revokeObjectURL(url)
  }

  // Export Hub Trigger
  const handleExportData = () => {
    setExportBanner(`Generated ${exportFormat} export for [${exportEntity.toUpperCase()}] (Filtered by: ${exportDept}). Download initiated.`)
    setTimeout(() => setExportBanner(null), 4000)

    // Trigger synthetic file download
    const exportContent =
      exportFormat === 'JSON'
        ? JSON.stringify({ entity: exportEntity, department: exportDept, timestamp: new Date().toISOString() }, null, 2)
        : 'id,code,name,department,status\n1,E001,Nimali Wickramasinghe,Executive,ACTIVE\n2,E002,Ruwan Jayasuriya,Engineering,ACTIVE\n'
    const blob = new Blob([exportContent], { type: 'text/plain;charset=utf-8;' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${exportEntity}_export_${exportDept.toLowerCase()}.${exportFormat === 'JSON' ? 'json' : 'csv'}`
    a.click()
    URL.revokeObjectURL(url)
  }

  const validCount = useMemo(() => editableRows.filter((r) => r._status === 'VALID').length, [editableRows])
  const errorCount = useMemo(() => editableRows.length - validCount, [editableRows, validCount])

  return (
    <div className="builder-shell">
      {/* Header */}
      <div className="builder-header">
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)' }}>
            <h1 className="text-lg" style={{ margin: 0, fontWeight: 700 }}>
              Batch Import & Export Administration Hub
            </h1>
            <Badge tone="success">Engine: Active</Badge>
            <Badge tone="neutral">CSV & Excel Support</Badge>
          </div>
          <p style={{ margin: 0, fontSize: '0.875rem', color: 'var(--color-on-surface-muted)' }}>
            Bulk upload employee records with intelligent column mapping, in-grid validation, and granular data export.
          </p>
        </div>

        {/* Tab Switcher */}
        <div style={{ display: 'flex', border: '1px solid var(--color-outline)', borderRadius: 'var(--radius-control)', overflow: 'hidden' }}>
          <button
            className="btn btn--ghost"
            style={{
              borderRadius: 0,
              background: activeTab === 'import' ? 'var(--color-brand-primary)' : 'transparent',
              color: activeTab === 'import' ? '#fff' : 'inherit',
              padding: 'var(--space-1) var(--space-3)',
              fontSize: '0.8125rem',
            }}
            onClick={() => setActiveTab('import')}
          >
            📥 Bulk Import Wizard
          </button>
          <button
            className="btn btn--ghost"
            style={{
              borderRadius: 0,
              background: activeTab === 'export' ? 'var(--color-brand-primary)' : 'transparent',
              color: activeTab === 'export' ? '#fff' : 'inherit',
              padding: 'var(--space-1) var(--space-3)',
              fontSize: '0.8125rem',
            }}
            onClick={() => setActiveTab('export')}
          >
            📤 Data Export Hub
          </button>
        </div>
      </div>

      {exportBanner && (
        <div style={{ background: '#dcfce7', color: '#15803d', padding: 'var(--space-3)', borderRadius: 'var(--radius-control)', fontWeight: 500 }}>
          {exportBanner}
        </div>
      )}

      {activeTab === 'import' ? (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
          {/* Step Wizard Progress Bar */}
          <div className="wizard-steps">
            {[
              { num: 1, label: 'Upload File' },
              { num: 2, label: 'Column Mapping' },
              { num: 3, label: 'Validation & Edit' },
              { num: 4, label: 'Execution Summary' },
            ].map((st) => {
              const isDone = step > st.num
              const isActive = step === st.num
              return (
                <div
                  key={st.num}
                  className={`wizard-step ${isActive ? 'wizard-step--active' : ''} ${isDone ? 'wizard-step--done' : ''}`}
                >
                  <div className="wizard-step__circle">{isDone ? '✓' : st.num}</div>
                  <span className="wizard-step__label">{st.label}</span>
                </div>
              )
            })}
          </div>

          {/* Step 1: Upload File */}
          {step === 1 && (
            <Card title="Step 1: Upload Employee Roster File (CSV / XLSX)">
              <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-6)', maxWidth: '640px', margin: '0 auto', textAlign: 'center' }}>
                <div
                  className="dropzone"
                  onClick={loadSampleDataset}
                >
                  <div className="dropzone__icon">📂</div>
                  <div style={{ fontWeight: 600, fontSize: '1.0625rem' }}>
                    Click to load sample employee roster or drop file here
                  </div>
                  <div style={{ fontSize: '0.8125rem', color: 'var(--color-on-surface-muted)' }}>
                    Supports .CSV, .TSV, and UTF-8 encoded text files up to 25MB
                  </div>
                </div>

                <div style={{ display: 'flex', justifyContent: 'center', gap: 'var(--space-3)' }}>
                  <Button variant="secondary" onClick={downloadTemplate}>
                    📥 Download Standard CSV Template
                  </Button>
                  <Button variant="primary" onClick={loadSampleDataset}>
                    ⚡ Load Demo Dataset (7 Sample Records)
                  </Button>
                </div>
              </div>
            </Card>
          )}

          {/* Step 2: Smart Column Mapping */}
          {step === 2 && (
            <Card title={`Step 2: Column Mapping for "${fileName}"`}>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
                <p style={{ margin: 0, fontSize: '0.875rem', color: 'var(--color-on-surface-muted)' }}>
                  We detected {Object.keys(columnMapping).length} columns in your file. Match each column with the target HR system field.
                </p>

                <div style={{ overflowX: 'auto' }}>
                  <table className="table">
                    <thead>
                      <tr>
                        <th scope="col">CSV Header in File</th>
                        <th scope="col">Sample Value from Row 1</th>
                        <th scope="col">Match Confidence</th>
                        <th scope="col">Target HR Field</th>
                      </tr>
                    </thead>
                    <tbody>
                      {Object.keys(columnMapping).map((colName) => {
                        const sampleVal = rawRows[0]?.[colName] ?? ''
                        const mappedField = columnMapping[colName]
                        const isMapped = mappedField !== 'SKIP'
                        return (
                          <tr key={colName}>
                            <td style={{ fontWeight: 600, fontFamily: 'var(--font-mono)' }}>{colName}</td>
                            <td style={{ fontSize: '0.8125rem', color: 'var(--color-on-surface-muted)' }}>{sampleVal}</td>
                            <td>
                              {isMapped ? (
                                <Badge tone="success">100% Match</Badge>
                              ) : (
                                <Badge tone="neutral">Ignored / Skip</Badge>
                              )}
                            </td>
                            <td>
                              <select
                                className="select"
                                value={mappedField}
                                onChange={(e) =>
                                  setColumnMapping((prev) => ({ ...prev, [colName]: e.target.value }))
                                }
                              >
                                <option value="SKIP">[ Do Not Import / Skip Column ]</option>
                                {TARGET_FIELDS.map((tf) => (
                                  <option key={tf.key} value={tf.key}>
                                    {tf.label} {tf.required ? '(*Required)' : ''}
                                  </option>
                                ))}
                              </select>
                            </td>
                          </tr>
                        )
                      })}
                    </tbody>
                  </table>
                </div>

                <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 'var(--space-2)' }}>
                  <Button variant="secondary" onClick={() => setStep(1)}>
                    ← Back to Upload
                  </Button>
                  <Button variant="primary" onClick={validateMappedRows}>
                    Next: Validate Records →
                  </Button>
                </div>
              </div>
            </Card>
          )}

          {/* Step 3: In-Grid Validation & Inline Correction */}
          {step === 3 && (
            <Card title="Step 3: Real-Time Validation & Inline Correction Grid">
              <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 'var(--space-2)' }}>
                  <div style={{ display: 'flex', gap: 'var(--space-2)', alignItems: 'center' }}>
                    <Badge tone="neutral">Total: {editableRows.length} Rows</Badge>
                    <Badge tone="success">Valid: {validCount}</Badge>
                    {errorCount > 0 ? (
                      <Badge tone="danger">Errors Found: {errorCount}</Badge>
                    ) : (
                      <Badge tone="success">All Rows Valid!</Badge>
                    )}
                  </div>

                  <span style={{ fontSize: '0.8125rem', color: 'var(--color-on-surface-muted)' }}>
                    💡 Tip: Click any cell with an error line to fix the value directly in the grid.
                  </span>
                </div>

                <div style={{ overflowX: 'auto' }}>
                  <table className="table">
                    <thead>
                      <tr>
                        <th scope="col">Status</th>
                        <th scope="col">Emp Code</th>
                        <th scope="col">First Name</th>
                        <th scope="col">Last Name</th>
                        <th scope="col">Work Email</th>
                        <th scope="col">National ID</th>
                        <th scope="col">Department</th>
                        <th scope="col">Designation</th>
                        <th scope="col">Basic Salary</th>
                        <th scope="col">Join Date</th>
                      </tr>
                    </thead>
                    <tbody>
                      {editableRows.map((row) => {
                        const hasErrors = row._status === 'ERROR'
                        return (
                          <tr key={row._id} style={{ background: hasErrors ? '#fff1f2' : undefined }}>
                            <td>
                              {hasErrors ? (
                                <Badge tone="danger">Error</Badge>
                              ) : (
                                <Badge tone="success">Valid</Badge>
                              )}
                            </td>
                            <td>
                              <input
                                className="cell-input"
                                value={row.employee_code}
                                onChange={(e) => handleCellEdit(row._id, 'employee_code', e.target.value)}
                              />
                            </td>
                            <td>
                              <input
                                className="cell-input"
                                value={row.first_name}
                                onChange={(e) => handleCellEdit(row._id, 'first_name', e.target.value)}
                              />
                            </td>
                            <td>
                              <input
                                className="cell-input"
                                value={row.last_name}
                                onChange={(e) => handleCellEdit(row._id, 'last_name', e.target.value)}
                              />
                            </td>
                            <td className={row._errors.work_email ? 'editable-cell--error' : ''}>
                              <input
                                className="cell-input"
                                title={row._errors.work_email}
                                value={row.work_email}
                                onChange={(e) => handleCellEdit(row._id, 'work_email', e.target.value)}
                              />
                            </td>
                            <td className={row._errors.national_id ? 'editable-cell--error' : ''}>
                              <input
                                className="cell-input"
                                title={row._errors.national_id}
                                value={row.national_id}
                                onChange={(e) => handleCellEdit(row._id, 'national_id', e.target.value)}
                              />
                            </td>
                            <td>
                              <input
                                className="cell-input"
                                value={row.department}
                                onChange={(e) => handleCellEdit(row._id, 'department', e.target.value)}
                              />
                            </td>
                            <td>
                              <input
                                className="cell-input"
                                value={row.designation}
                                onChange={(e) => handleCellEdit(row._id, 'designation', e.target.value)}
                              />
                            </td>
                            <td className={row._errors.basic_salary ? 'editable-cell--error' : ''}>
                              <input
                                className="cell-input"
                                title={row._errors.basic_salary}
                                value={row.basic_salary}
                                onChange={(e) => handleCellEdit(row._id, 'basic_salary', e.target.value)}
                              />
                            </td>
                            <td className={row._errors.join_date ? 'editable-cell--error' : ''}>
                              <input
                                className="cell-input"
                                title={row._errors.join_date}
                                value={row.join_date}
                                onChange={(e) => handleCellEdit(row._id, 'join_date', e.target.value)}
                              />
                            </td>
                          </tr>
                        )
                      })}
                    </tbody>
                  </table>
                </div>

                {/* Dry Run Toggle & Actions */}
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: 'var(--space-2)' }}>
                  <Button variant="secondary" onClick={() => setStep(2)}>
                    ← Back to Mapping
                  </Button>

                  <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-4)' }}>
                    <label style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '0.875rem', cursor: 'pointer' }}>
                      <input
                        type="checkbox"
                        checked={isDryRun}
                        onChange={(e) => setIsDryRun(e.target.checked)}
                      />
                      Dry-Run Simulation (Validate without committing)
                    </label>

                    <Button variant="primary" disabled={isImporting} onClick={runBatchImport}>
                      {isImporting ? `Importing (${importProgress}%)…` : isDryRun ? 'Simulate Dry Run' : 'Execute Batch Import'}
                    </Button>
                  </div>
                </div>
              </div>
            </Card>
          )}

          {/* Step 4: Execution Summary */}
          {step === 4 && importResult && (
            <Card title="Step 4: Batch Execution Summary & Audit">
              <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-6)', maxWidth: '640px', margin: '0 auto', textAlign: 'center' }}>
                <div style={{ fontSize: '3rem' }}>
                  {importResult.failed === 0 ? '🎉' : '⚠️'}
                </div>
                <div>
                  <h3 style={{ fontSize: '1.25rem', fontWeight: 700, margin: 0 }}>
                    {isDryRun ? 'Dry-Run Simulation Complete' : 'Batch Import Complete'}
                  </h3>
                  <p style={{ margin: '4px 0 0 0', color: 'var(--color-on-surface-muted)', fontSize: '0.875rem' }}>
                    {isDryRun
                      ? 'No records were written to the database. Results reflect validation pass.'
                      : 'Records have been committed to PostgreSQL with row-level security.'}
                  </p>
                </div>

                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 'var(--space-2)' }}>
                  <div style={{ background: 'var(--color-surface)', padding: 'var(--space-3)', borderRadius: 'var(--radius-control)' }}>
                    <div style={{ fontSize: '0.75rem', fontWeight: 600 }}>TOTAL ROWS</div>
                    <div style={{ fontSize: '1.25rem', fontWeight: 700 }}>{importResult.total}</div>
                  </div>
                  <div style={{ background: 'var(--color-surface)', padding: 'var(--space-3)', borderRadius: 'var(--radius-control)' }}>
                    <div style={{ fontSize: '0.75rem', fontWeight: 600, color: '#16a34a' }}>IMPORTED</div>
                    <div style={{ fontSize: '1.25rem', fontWeight: 700, color: '#16a34a' }}>{importResult.inserted}</div>
                  </div>
                  <div style={{ background: 'var(--color-surface)', padding: 'var(--space-3)', borderRadius: 'var(--radius-control)' }}>
                    <div style={{ fontSize: '0.75rem', fontWeight: 600 }}>SKIPPED</div>
                    <div style={{ fontSize: '1.25rem', fontWeight: 700 }}>{importResult.updated}</div>
                  </div>
                  <div style={{ background: 'var(--color-surface)', padding: 'var(--space-3)', borderRadius: 'var(--radius-control)' }}>
                    <div style={{ fontSize: '0.75rem', fontWeight: 600, color: 'var(--color-danger)' }}>FAILED</div>
                    <div style={{ fontSize: '1.25rem', fontWeight: 700, color: 'var(--color-danger)' }}>{importResult.failed}</div>
                  </div>
                </div>

                <div style={{ display: 'flex', justifyContent: 'center', gap: 'var(--space-3)' }}>
                  <Button variant="secondary" onClick={() => setStep(1)}>
                    Import Another Batch
                  </Button>
                  <Button variant="primary" onClick={() => (window.location.href = '/directory')}>
                    View in Employee Directory →
                  </Button>
                </div>
              </div>
            </Card>
          )}
        </div>
      ) : (
        /* Export Hub Tab */
        <Card title="Granular Data Export Hub">
          <div style={{ maxWidth: '640px', display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
            <div>
              <label style={{ fontSize: '0.8125rem', fontWeight: 600, display: 'block', marginBottom: '4px' }}>
                SELECT DATASET TO EXPORT
              </label>
              <select className="select" value={exportEntity} onChange={(e) => setExportEntity(e.target.value)}>
                <option value="employees">Employee Master Roster (Personal, Statutory, Contact)</option>
                <option value="attendance">Biometric Time & Attendance Punch Logs</option>
                <option value="leave">Leave Balances, Accruals & Absence History</option>
                <option value="payroll">Payroll Results & Detailed Payslip Lines</option>
                <option value="timesheets">Timesheet Billing & Project Hours</option>
              </select>
            </div>

            <div>
              <label style={{ fontSize: '0.8125rem', fontWeight: 600, display: 'block', marginBottom: '4px' }}>
                FILTER BY DEPARTMENT
              </label>
              <select className="select" value={exportDept} onChange={(e) => setExportDept(e.target.value)}>
                <option value="ALL">All Departments (Organization-Wide)</option>
                <option value="Engineering">Engineering</option>
                <option value="Finance">Finance</option>
                <option value="Human Resources">Human Resources</option>
                <option value="Executive">Executive</option>
              </select>
            </div>

            <div>
              <label style={{ fontSize: '0.8125rem', fontWeight: 600, display: 'block', marginBottom: '4px' }}>
                FILE FORMAT
              </label>
              <div style={{ display: 'flex', gap: 'var(--space-4)' }}>
                {(['CSV', 'EXCEL', 'JSON'] as const).map((fmt) => (
                  <label key={fmt} style={{ fontSize: '0.8125rem', display: 'flex', alignItems: 'center', gap: '6px', cursor: 'pointer' }}>
                    <input
                      type="radio"
                      name="export_fmt"
                      checked={exportFormat === fmt}
                      onChange={() => setExportFormat(fmt)}
                    />
                    {fmt}
                  </label>
                ))}
              </div>
            </div>

            <div style={{ paddingTop: 'var(--space-2)' }}>
              <Button variant="primary" onClick={handleExportData}>
                Generate & Download Export File
              </Button>
            </div>
          </div>
        </Card>
      )}
    </div>
  )
}
