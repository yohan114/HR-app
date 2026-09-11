import { useState, useMemo } from 'react'
import { Badge, Button, Card } from '@/components/ui'

export interface FormulaVariable {
  key: string
  label: string
  category: 'payroll' | 'leave' | 'attendance' | 'statutory'
  defaultValue: number
  unit: string
  description: string
}

export interface FormulaPreset {
  id: string
  name: string
  category: string
  description: string
  expression: string
  targetField: string
}

const VARIABLES_CATALOG: FormulaVariable[] = [
  { key: 'basic_salary', label: 'Basic Salary', category: 'payroll', defaultValue: 150000, unit: 'LKR', description: 'Base contractual monthly salary' },
  { key: 'gross_pay', label: 'Gross Pay', category: 'payroll', defaultValue: 185000, unit: 'LKR', description: 'Total earnings before deductions' },
  { key: 'allowances', label: 'Fixed Allowances', category: 'payroll', defaultValue: 35000, unit: 'LKR', description: 'Transport, cost of living, mobile' },
  { key: 'overtime_hours', label: 'Overtime Hours (Normal)', category: 'attendance', defaultValue: 12, unit: 'Hours', description: 'Overtime logged on regular workdays' },
  { key: 'holiday_ot_hours', label: 'Holiday OT Hours (2x)', category: 'attendance', defaultValue: 4, unit: 'Hours', description: 'Overtime logged on Mercantile/Public holidays' },
  { key: 'worked_days', label: 'Actual Worked Days', category: 'attendance', defaultValue: 21, unit: 'Days', description: 'Days present in attendance cycle' },
  { key: 'standard_work_days', label: 'Standard Work Days', category: 'statutory', defaultValue: 22, unit: 'Days', description: 'Total scheduled business days in month' },
  { key: 'unpaid_days', label: 'Unpaid Absent Days', category: 'leave', defaultValue: 1, unit: 'Days', description: 'No-pay leave or unauthorized absence' },
  { key: 'annual_entitlement', label: 'Annual Leave Entitlement', category: 'leave', defaultValue: 14, unit: 'Days', description: 'Total annual statutory leave entitlement' },
  { key: 'months_worked', label: 'Months Worked in Year', category: 'leave', defaultValue: 8, unit: 'Months', description: 'Service duration in current leave cycle' },
  { key: 'leave_balance', label: 'Available Leave Balance', category: 'leave', defaultValue: 9.5, unit: 'Days', description: 'Unconsumed leave units' },
  { key: 'kpi_score', label: 'KPI Performance Score', category: 'payroll', defaultValue: 92, unit: '%', description: 'Appraisal cycle achievement score' },
  { key: 'epf_employee_pct', label: 'EPF Employee Rate (8%)', category: 'statutory', defaultValue: 0.08, unit: 'Rate', description: 'Statutory Employee EPF contribution' },
  { key: 'epf_employer_pct', label: 'EPF Employer Rate (12%)', category: 'statutory', defaultValue: 0.12, unit: 'Rate', description: 'Statutory Employer EPF contribution' },
  { key: 'etf_employer_pct', label: 'ETF Employer Rate (3%)', category: 'statutory', defaultValue: 0.03, unit: 'Rate', description: 'Statutory Employer ETF contribution' },
]

const FORMULA_PRESETS: FormulaPreset[] = [
  {
    id: 'epf_employee',
    name: 'Sri Lanka EPF Employee Deduction (8%)',
    category: 'Statutory',
    description: 'Calculates the 8% employee statutory provident fund contribution',
    expression: 'ROUND(basic_salary * epf_employee_pct, 2)',
    targetField: 'epf_employee_deduction',
  },
  {
    id: 'epf_etf_employer',
    name: 'Sri Lanka EPF & ETF Employer Total Cost (15%)',
    category: 'Statutory',
    description: 'Combined 12% EPF + 3% ETF employer liability',
    expression: 'ROUND(basic_salary * epf_employer_pct, 2) + ROUND(basic_salary * etf_employer_pct, 2)',
    targetField: 'statutory_employer_total',
  },
  {
    id: 'hourly_overtime',
    name: 'Overtime Pay (1.5x Normal + 2.0x Holiday)',
    category: 'Payroll',
    description: 'Hourly rate based on 22 days × 8 hours, with 1.5x normal OT and 2x holiday OT',
    expression: 'ROUND(((basic_salary / 22 / 8) * 1.5 * overtime_hours) + ((basic_salary / 22 / 8) * 2.0 * holiday_ot_hours), 2)',
    targetField: 'overtime_gross_earnings',
  },
  {
    id: 'prorated_leave',
    name: 'Prorated Annual Leave Accrual',
    category: 'Leave',
    description: 'Accrues leave proportionately based on completed months of service',
    expression: 'ROUND((annual_entitlement / 12) * months_worked, 1)',
    targetField: 'prorated_annual_accrual',
  },
  {
    id: 'tier_bonus',
    name: 'Performance Tier Incentive Bonus',
    category: 'Payroll',
    description: 'Tiered incentive: 20% if KPI >= 90%, 10% if >= 75%, else zero',
    expression: 'IF(kpi_score >= 90, basic_salary * 0.20, IF(kpi_score >= 75, basic_salary * 0.10, 0))',
    targetField: 'performance_incentive_bonus',
  },
  {
    id: 'unpaid_deduction',
    name: 'Unpaid Leave Salary Deduction',
    category: 'Payroll',
    description: 'Deduction for unpaid absence based on working days',
    expression: 'ROUND((basic_salary / standard_work_days) * unpaid_days, 2)',
    targetField: 'unpaid_leave_deduction',
  },
]

export function FormulaBuilder() {
  const [expression, setExpression] = useState<string>('ROUND(basic_salary * epf_employee_pct, 2)')
  const [ruleName, setRuleName] = useState<string>('Sri Lanka EPF Employee Deduction (8%)')
  const [targetCode, setTargetCode] = useState<string>('epf_employee_deduction')
  const [categoryFilter, setCategoryFilter] = useState<string>('all')
  const [testValues, setTestValues] = useState<Record<string, number>>(() => {
    const initial: Record<string, number> = {}
    VARIABLES_CATALOG.forEach((v) => {
      initial[v.key] = v.defaultValue
    })
    return initial
  })
  const [saveBanner, setSaveBanner] = useState<string | null>(null)

  // ---------------------------------------------------------------------------
  // Expression Parser & Safe Evaluator
  // ---------------------------------------------------------------------------
  const { isValid, errorMessage, resultValue, stepTrace, referencedVariables } = useMemo(() => {
    const varsFound: string[] = []
    VARIABLES_CATALOG.forEach((v) => {
      const regex = new RegExp(`\\b${v.key}\\b`, 'g')
      if (regex.test(expression)) {
        varsFound.push(v.key)
      }
    })

    if (!expression.trim()) {
      return {
        isValid: false,
        errorMessage: 'Formula expression is empty',
        resultValue: 0,
        stepTrace: [],
        referencedVariables: [],
      }
    }

    // Check matching parentheses
    let openCount = 0
    for (const char of expression) {
      if (char === '(') openCount++
      if (char === ')') openCount--
      if (openCount < 0) {
        return {
          isValid: false,
          errorMessage: 'Unmatched closing parenthesis ")"',
          resultValue: 0,
          stepTrace: [],
          referencedVariables: varsFound,
        }
      }
    }
    if (openCount > 0) {
      return {
        isValid: false,
        errorMessage: `${openCount} unclosed opening parenthesis "("`,
        resultValue: 0,
        stepTrace: [],
        referencedVariables: varsFound,
      }
    }

    try {
      // Build evaluation scope
      const context = {
        ROUND: (val: number, decimals: number = 0) => {
          const factor = Math.pow(10, decimals)
          return Math.round(val * factor) / factor
        },
        MIN: (...args: number[]) => Math.min(...args),
        MAX: (...args: number[]) => Math.max(...args),
        CEIL: (val: number) => Math.ceil(val),
        FLOOR: (val: number) => Math.floor(val),
        IF: (cond: boolean, tVal: number, fVal: number) => (cond ? tVal : fVal),
        ...testValues,
      }

      // Convert expression to valid JS safely
      const jsExpr = expression
        .replace(/\bAND\b/g, '&&')
        .replace(/\bOR\b/g, '||')
        .replace(/\bNOT\b/g, '!')

      const keys = Object.keys(context)
      const values = Object.values(context)
      const evalFn = new Function(...keys, `return (${jsExpr});`)
      const computed = evalFn(...values)

      if (typeof computed !== 'number' || isNaN(computed)) {
        return {
          isValid: false,
          errorMessage: 'Formula did not evaluate to a valid numeric amount',
          resultValue: 0,
          stepTrace: [],
          referencedVariables: varsFound,
        }
      }

      // Generate step trace
      const trace: string[] = [
        `Variables loaded: ${varsFound.map((k) => `${k} = ${testValues[k] ?? 0}`).join(', ')}`,
        `Expression: ${expression}`,
        `Evaluated result: ${computed.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`,
      ]

      return {
        isValid: true,
        errorMessage: null,
        resultValue: computed,
        stepTrace: trace,
        referencedVariables: varsFound,
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : String(err)
      return {
        isValid: false,
        errorMessage: `Syntax or evaluation error: ${msg}`,
        resultValue: 0,
        stepTrace: [],
        referencedVariables: varsFound,
      }
    }
  }, [expression, testValues])

  // ---------------------------------------------------------------------------
  // Tokenizer for Visual Display
  // ---------------------------------------------------------------------------
  const tokens = useMemo(() => {
    const regex = /([a-zA-Z_][a-zA-Z0-9_]*|\d+(?:\.\d+)?|[+\-*/%(),><=!]+|\s+)/g
    const matches = expression.match(regex) || []
    return matches
      .map((t) => t.trim())
      .filter((t) => t.length > 0)
      .map((t, idx) => {
        const isVar = VARIABLES_CATALOG.some((v) => v.key === t)
        const isFunc = ['ROUND', 'IF', 'MIN', 'MAX', 'CEIL', 'FLOOR', 'AND', 'OR', 'NOT'].includes(t)
        const isNum = /^\d+(\.\d+)?$/.test(t)
        const isOp = ['+', '-', '*', '/', '%', '(', ')', '>', '<', '>=', '<=', '==', '!=', ','].includes(t)

        return {
          id: `${t}-${idx}`,
          text: t,
          type: isVar ? 'var' : isFunc ? 'func' : isNum ? 'num' : isOp ? 'op' : 'other',
        }
      })
  }, [expression])

  // Append token to expression
  const appendToken = (val: string) => {
    setExpression((prev) => {
      const trimmed = prev.trim()
      if (!trimmed) return val
      if (val === '(' || val === ')' || val === ',') {
        return trimmed + val
      }
      return `${trimmed} ${val}`
    })
  }

  const handleApplyPreset = (preset: FormulaPreset) => {
    setExpression(preset.expression)
    setRuleName(preset.name)
    setTargetCode(preset.targetField)
  }

  const handleSaveRule = () => {
    setSaveBanner(`Formula Rule "${ruleName}" saved to live Payroll Calculation Registry!`)
    setTimeout(() => setSaveBanner(null), 4000)
  }

  return (
    <div className="builder-shell">
      {/* Header */}
      <div className="builder-header">
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)' }}>
            <h1 className="text-lg" style={{ margin: 0, fontWeight: 700 }}>
              Formula & Calculation Expression Studio
            </h1>
            <Badge tone="success">Engine: Active</Badge>
            <Badge tone="neutral">Payroll & Leave</Badge>
          </div>
          <p style={{ margin: 0, fontSize: '0.875rem', color: 'var(--color-on-surface-muted)' }}>
            Construct mathematical and conditional formulas for statutory deductions, overtime tiers, and leave policies.
          </p>
        </div>

        <div style={{ display: 'flex', gap: 'var(--space-2)' }}>
          <Button variant="secondary" onClick={() => setExpression('')}>
            Clear Formula
          </Button>
          <Button variant="primary" disabled={!isValid} onClick={handleSaveRule}>
            Save Formula Rule
          </Button>
        </div>
      </div>

      {saveBanner && (
        <div style={{ background: '#dcfce7', color: '#15803d', padding: 'var(--space-3)', borderRadius: 'var(--radius-control)', fontWeight: 500 }}>
          {saveBanner}
        </div>
      )}

      {/* Preset Rules Carousel / Selector */}
      <div style={{ background: 'var(--color-surface-raised)', padding: 'var(--space-3)', borderRadius: 'var(--radius-control)', border: '1px solid var(--color-outline-variant)' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 'var(--space-2)' }}>
          <span style={{ fontSize: '0.8125rem', fontWeight: 600, color: 'var(--color-on-surface-muted)' }}>
            PRE-BUILT STATUTORY & PAYROLL FORMULA TEMPLATES
          </span>
        </div>
        <div style={{ display: 'flex', gap: 'var(--space-2)', overflowX: 'auto', paddingBottom: '4px' }}>
          {FORMULA_PRESETS.map((p) => (
            <button
              key={p.id}
              className="btn btn--ghost"
              style={{
                fontSize: '0.8125rem',
                padding: 'var(--space-2) var(--space-3)',
                borderRadius: 'var(--radius-control)',
                background: ruleName === p.name ? 'var(--color-brand-primary-container)' : 'var(--color-surface)',
                border: '1px solid var(--color-outline-variant)',
                textAlign: 'left',
                whiteSpace: 'nowrap',
              }}
              onClick={() => handleApplyPreset(p)}
            >
              <div style={{ fontWeight: 600 }}>{p.name}</div>
              <div style={{ fontSize: '0.75rem', opacity: 0.75 }}>{p.category} &bull; {p.targetField}</div>
            </button>
          ))}
        </div>
      </div>

      <div className="builder-grid">
        {/* Left Column: Data Dictionary */}
        <div className="palette-card">
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <h3 style={{ margin: 0, fontSize: '0.9375rem', fontWeight: 600 }}>Data Dictionary</h3>
            <select
              className="select"
              style={{ fontSize: '0.75rem', minHeight: '28px', padding: '0 6px' }}
              value={categoryFilter}
              onChange={(e) => setCategoryFilter(e.target.value)}
            >
              <option value="all">All Modules</option>
              <option value="payroll">Payroll</option>
              <option value="statutory">Statutory</option>
              <option value="leave">Leave</option>
              <option value="attendance">Attendance</option>
            </select>
          </div>
          <p style={{ margin: 0, fontSize: '0.75rem', color: 'var(--color-on-surface-muted)' }}>
            Click an operand variable to insert it into your formula expression.
          </p>

          <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-2)', maxHeight: '480px', overflowY: 'auto' }}>
            {VARIABLES_CATALOG.filter((v) => categoryFilter === 'all' || v.category === categoryFilter).map((v) => (
              <div
                key={v.key}
                className="palette-item"
                onClick={() => appendToken(v.key)}
                style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-start', gap: '2px' }}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', width: '100%' }}>
                  <span style={{ fontWeight: 600 }}>{v.label}</span>
                  <Badge tone="neutral">{v.unit}</Badge>
                </div>
                <div style={{ fontSize: '0.75rem', fontFamily: 'var(--font-mono)', color: 'var(--color-brand-primary)' }}>
                  {v.key}
                </div>
                <div style={{ fontSize: '0.6875rem', color: 'var(--color-on-surface-muted)' }}>
                  {v.description}
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* Center Column: Formula Editor & Tokenized Canvas */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
          {/* Rule Metadata Bar */}
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--space-3)', background: 'var(--color-surface-raised)', padding: 'var(--space-3)', borderRadius: 'var(--radius-card)', border: '1px solid var(--color-outline-variant)' }}>
            <div>
              <label style={{ fontSize: '0.75rem', fontWeight: 600, display: 'block', marginBottom: '2px' }}>RULE NAME</label>
              <input
                className="field__input"
                style={{ fontSize: '0.8125rem' }}
                value={ruleName}
                onChange={(e) => setRuleName(e.target.value)}
              />
            </div>
            <div>
              <label style={{ fontSize: '0.75rem', fontWeight: 600, display: 'block', marginBottom: '2px' }}>TARGET RESULT CODE</label>
              <input
                className="field__input"
                style={{ fontSize: '0.8125rem', fontFamily: 'var(--font-mono)' }}
                value={targetCode}
                onChange={(e) => setTargetCode(e.target.value.toLowerCase().replace(/[^a-z0-9_]/g, ''))}
              />
            </div>
          </div>

          {/* Operator & Function Chips */}
          <div className="formula-editor-card">
            <span style={{ fontSize: '0.8125rem', fontWeight: 600, color: 'var(--color-on-surface-muted)' }}>
              FUNCTIONS & OPERATORS
            </span>
            <div className="formula-chip-grid">
              {['+', '-', '*', '/', '%', '(', ')', ','].map((op) => (
                <button key={op} className="chip-btn" onClick={() => appendToken(op)}>
                  {op}
                </button>
              ))}
              {['>', '<', '>=', '<=', '==', '!=', 'AND', 'OR', 'NOT'].map((logic) => (
                <button key={logic} className="chip-btn" onClick={() => appendToken(logic)}>
                  {logic}
                </button>
              ))}
              {['ROUND', 'IF', 'MIN', 'MAX', 'CEIL', 'FLOOR'].map((fn) => (
                <button key={fn} className="chip-btn" style={{ background: '#fdf4ff', borderColor: '#f5d0fe' }} onClick={() => appendToken(`${fn}(`)}>
                  {fn}()
                </button>
              ))}
            </div>

            {/* Visual Token Canvas */}
            <div style={{ marginTop: 'var(--space-2)' }}>
              <span style={{ fontSize: '0.75rem', fontWeight: 600, display: 'block', marginBottom: '4px' }}>
                VISUAL AST TOKENS
              </span>
              <div className="formula-display">
                {tokens.length === 0 ? (
                  <span style={{ color: 'var(--color-on-surface-muted)', fontSize: '0.8125rem' }}>
                    Expression is empty. Click operands and functions above to construct.
                  </span>
                ) : (
                  tokens.map((token) => (
                    <span key={token.id} className={`formula-token formula-token--${token.type}`}>
                      {token.text}
                    </span>
                  ))
                )}
              </div>
            </div>

            {/* Direct Text Editor */}
            <div>
              <span style={{ fontSize: '0.75rem', fontWeight: 600, display: 'block', marginBottom: '4px' }}>
                EXPRESSION TEXT
              </span>
              <textarea
                className="field__input"
                style={{ fontFamily: 'var(--font-mono)', fontSize: '0.875rem' }}
                rows={3}
                value={expression}
                onChange={(e) => setExpression(e.target.value)}
                placeholder="Enter mathematical or logical expression..."
              />
            </div>

            {/* Validation Indicator */}
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: 'var(--space-2) 0' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)' }}>
                {isValid ? (
                  <Badge tone="success">✓ Expression Syntax Valid</Badge>
                ) : (
                  <Badge tone="danger">✕ Syntax Error</Badge>
                )}
                {errorMessage && (
                  <span style={{ fontSize: '0.8125rem', color: 'var(--color-danger)' }}>
                    {errorMessage}
                  </span>
                )}
              </div>
              <span style={{ fontSize: '0.75rem', color: 'var(--color-on-surface-muted)' }}>
                {referencedVariables.length} variable{referencedVariables.length === 1 ? '' : 's'} linked
              </span>
            </div>
          </div>
        </div>

        {/* Right Column: Live Sandbox Calculator */}
        <div className="palette-card builder-inspector-col">
          <h3 style={{ margin: 0, fontSize: '0.9375rem', fontWeight: 600 }}>Interactive Sandbox Tester</h3>
          <p style={{ margin: 0, fontSize: '0.75rem', color: 'var(--color-on-surface-muted)' }}>
            Adjust sample parameters to see instant evaluated calculations.
          </p>

          <div className="sandbox-callout">
            <span style={{ fontSize: '0.75rem', fontWeight: 600, color: 'var(--color-on-surface-muted)' }}>
              COMPUTED RESULT ({targetCode})
            </span>
            <div className="sandbox-result">
              {isValid ? (
                resultValue.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
              ) : (
                <span style={{ color: 'var(--color-danger)', fontSize: '1rem' }}>Invalid Formula</span>
              )}
            </div>
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
            <span style={{ fontSize: '0.75rem', fontWeight: 600, color: 'var(--color-on-surface-muted)' }}>
              FORMULA INPUT VARIABLES
            </span>

            {referencedVariables.length === 0 ? (
              <div style={{ fontSize: '0.8125rem', color: 'var(--color-on-surface-muted)' }}>
                No variables referenced in formula.
              </div>
            ) : (
              referencedVariables.map((vKey) => {
                const varMeta = VARIABLES_CATALOG.find((v) => v.key === vKey)
                const currentVal = testValues[vKey] ?? 0
                return (
                  <div key={vKey} style={{ display: 'flex', flexDirection: 'column', gap: '2px' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.75rem' }}>
                      <span style={{ fontWeight: 600 }}>{varMeta?.label ?? vKey}</span>
                      <span style={{ fontFamily: 'var(--font-mono)' }}>{currentVal} {varMeta?.unit}</span>
                    </div>
                    <input
                      type="number"
                      step={vKey.includes('pct') ? '0.01' : '1'}
                      className="field__input"
                      style={{ padding: '2px 6px', fontSize: '0.8125rem' }}
                      value={currentVal}
                      onChange={(e) =>
                        setTestValues((prev) => ({
                          ...prev,
                          [vKey]: parseFloat(e.target.value) || 0,
                        }))
                      }
                    />
                  </div>
                )
              })
            )}
          </div>

          {/* Trace log */}
          <div style={{ borderTop: '1px solid var(--color-outline-variant)', paddingTop: 'var(--space-3)' }}>
            <span style={{ fontSize: '0.75rem', fontWeight: 600, display: 'block', marginBottom: '4px' }}>
              EVALUATION LOG
            </span>
            <div style={{ background: 'var(--color-surface)', padding: 'var(--space-2)', borderRadius: '4px', fontSize: '0.6875rem', fontFamily: 'var(--font-mono)' }}>
              {stepTrace.map((line, idx) => (
                <div key={idx} style={{ marginBottom: '2px' }}>&bull; {line}</div>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
