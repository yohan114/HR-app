import { useEffect, useState } from 'react'
import { Badge, Button, Card, LoadingState, Modal } from '../ui'
import {
  performanceApi,
  type AppraisalCycleItem,
  type NineBoxEmployeeItem,
  type NineBoxMatrixResponse,
} from '../../lib/api'

interface NineBoxMatrixCardProps {
  cycles: AppraisalCycleItem[]
  selectedCycleId?: string
  onCycleChange?: (cycleId: string) => void
}

export function NineBoxMatrixCard({
  cycles,
  selectedCycleId: externalCycleId,
  onCycleChange,
}: NineBoxMatrixCardProps) {
  const [internalCycleId, setInternalCycleId] = useState<string>(
    externalCycleId || cycles[0]?.id || 'eval-2026-annual'
  )
  const activeCycleId = externalCycleId || internalCycleId

  const [selectedDept, setSelectedDept] = useState('ALL')
  const [matrixData, setMatrixData] = useState<NineBoxMatrixResponse | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // Calibration modal state
  const [calibratingEmployee, setCalibratingEmployee] =
    useState<NineBoxEmployeeItem | null>(null)
  const [potentialLevel, setPotentialLevel] = useState<'LOW' | 'MEDIUM' | 'HIGH'>('MEDIUM')
  const [performanceScore, setPerformanceScore] = useState(3.5)
  const [calibratingLoading, setCalibratingLoading] = useState(false)

  const loadMatrix = async (cycleId: string, dept: string) => {
    setLoading(true)
    setError(null)
    try {
      const data = await performanceApi.get9BoxMatrix(cycleId, dept)
      setMatrixData(data)
    } catch (err) {
      console.error('Failed to load 9-box talent matrix', err)
      setError('Unable to load 9-box matrix data.')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    if (activeCycleId) {
      loadMatrix(activeCycleId, selectedDept)
    }
  }, [activeCycleId, selectedDept])

  const handleSelectCycle = (cycleId: string) => {
    setInternalCycleId(cycleId)
    onCycleChange?.(cycleId)
  }

  const handleOpenCalibrate = (emp: NineBoxEmployeeItem) => {
    setCalibratingEmployee(emp)
    setPotentialLevel(emp.potentialLevel)
    setPerformanceScore(emp.performanceScore)
  }

  const handleSaveCalibration = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!calibratingEmployee || !activeCycleId) return
    setCalibratingLoading(true)
    try {
      await performanceApi.update9BoxPosition(activeCycleId, calibratingEmployee.employeeId, {
        potentialLevel,
        performanceScore: Number(performanceScore),
      })
      setCalibratingEmployee(null)
      await loadMatrix(activeCycleId, selectedDept)
    } catch (err) {
      console.error('Failed to calibrate employee position', err)
      alert('Failed to update 9-box calibration position')
    } finally {
      setCalibratingLoading(false)
    }
  }

  // Desired ordering for standard 3x3 layout:
  // Row 1 (High Potential): Enigma, High Potential, Star
  // Row 2 (Medium Potential): Dilemma, Core Contributor, High Performer
  // Row 3 (Low Potential): Underperformer, Effective, Specialist
  const boxOrder = [
    'enigma',
    'high_potential',
    'star',
    'dilemma',
    'core_contributor',
    'high_performer',
    'underperformer',
    'effective',
    'specialist',
  ]

  const getCellByKey = (key: string) => matrixData?.grid.find((c) => c.boxKey === key)

  return (
    <Card
      title="9-Box Talent & Succession Matrix (Performance vs. Potential)"
      actions={
        <div style={{ display: 'flex', gap: '12px', alignItems: 'center' }}>
          <div style={{ display: 'flex', gap: '6px', alignItems: 'center' }}>
            <span className="text-sm text-muted">Cycle:</span>
            <select
              className="select"
              value={activeCycleId}
              onChange={(e) => handleSelectCycle(e.target.value)}
            >
              {cycles.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </select>
          </div>

          <div style={{ display: 'flex', gap: '6px', alignItems: 'center' }}>
            <span className="text-sm text-muted">Department:</span>
            <select
              className="select"
              value={selectedDept}
              onChange={(e) => setSelectedDept(e.target.value)}
            >
              <option value="ALL">All Departments</option>
              <option value="Engineering">Engineering</option>
              <option value="Product">Product</option>
              <option value="Operations">Operations</option>
              <option value="Human Resources">Human Resources</option>
              <option value="Finance">Finance</option>
            </select>
          </div>
        </div>
      }
    >
      {loading ? (
        <LoadingState label="Mapping organizational talent to 9-Box potential and performance matrix..." />
      ) : error ? (
        <div className="calibration-alert-box">{error}</div>
      ) : matrixData ? (
        <div className="nine-box-container">
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <div className="text-xs text-muted">
              Total Evaluated: <strong>{matrixData.totalEmployees} employees</strong> • Click any employee chip to recalibrate potential rating.
            </div>
            <div style={{ display: 'flex', gap: '12px' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '4px', fontSize: '0.75rem' }}>
                <span style={{ width: '10px', height: '10px', borderRadius: '50%', background: '#10b981' }} />
                <span>Stars & Succession</span>
              </div>
              <div style={{ display: 'flex', alignItems: 'center', gap: '4px', fontSize: '0.75rem' }}>
                <span style={{ width: '10px', height: '10px', borderRadius: '50%', background: '#6366f1' }} />
                <span>Core Backbone</span>
              </div>
              <div style={{ display: 'flex', alignItems: 'center', gap: '4px', fontSize: '0.75rem' }}>
                <span style={{ width: '10px', height: '10px', borderRadius: '50%', background: '#ef4444' }} />
                <span>Action Plan Needed</span>
              </div>
            </div>
          </div>

          {/* 9-Box Grid with Outer Axes */}
          <div className="nine-box-matrix-wrapper">
            {/* Y-Axis Label */}
            <div className="nine-box-axis-y">
              <span>Potential (High → Low)</span>
            </div>

            {/* 3x3 Grid of Boxes */}
            <div className="nine-box-grid">
              {boxOrder.map((boxKey) => {
                const cell = getCellByKey(boxKey)
                if (!cell) return null

                return (
                  <div
                    key={cell.boxKey}
                    className="nine-box-cell"
                    style={{ borderTop: `3px solid ${cell.colorTone}` }}
                  >
                    <div className="nine-box-cell__header">
                      <div>
                        <div className="nine-box-cell__title">{cell.title}</div>
                        <div className="nine-box-cell__desc">{cell.description}</div>
                      </div>
                      <span className="nine-box-cell__badge">
                        {cell.employees.length}
                      </span>
                    </div>

                    <div className="nine-box-cell__employees">
                      {cell.employees.length === 0 ? (
                        <div
                          style={{
                            textAlign: 'center',
                            color: 'var(--color-on-surface-muted)',
                            fontSize: '0.6875rem',
                            padding: '16px 0',
                          }}
                        >
                          No employees mapped
                        </div>
                      ) : (
                        cell.employees.map((emp) => (
                          <div
                            key={emp.employeeId}
                            className="nine-box-emp-card"
                            onClick={() => handleOpenCalibrate(emp)}
                            title="Click to recalibrate potential level or score"
                          >
                            <div className="nine-box-emp-info">
                              <div
                                className="nine-box-avatar"
                                style={{
                                  background: cell.colorTone,
                                  color: '#fff',
                                }}
                              >
                                {emp.avatarInitials}
                              </div>
                              <div>
                                <div className="nine-box-emp-name">{emp.fullName}</div>
                                <div className="nine-box-emp-sub">
                                  {emp.designation} • {emp.department}
                                </div>
                              </div>
                            </div>
                            <div style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
                              <span className="nine-box-score-tag">
                                ★ {emp.performanceScore.toFixed(1)}
                              </span>
                            </div>
                          </div>
                        ))
                      )}
                    </div>
                  </div>
                )
              })}
            </div>

            {/* X-Axis Label */}
            <div className="nine-box-axis-x">
              <span>Performance (Low → High)</span>
            </div>
          </div>

          {/* Calibrate Employee Modal */}
          {calibratingEmployee && (
            <Modal
              isOpen={Boolean(calibratingEmployee)}
              onClose={() => setCalibratingEmployee(null)}
              title={`Calibrate 9-Box Position: ${calibratingEmployee.fullName}`}
              size="small"
              actions={
                <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px' }}>
                  <Button variant="secondary" onClick={() => setCalibratingEmployee(null)}>
                    Cancel
                  </Button>
                  <Button
                    variant="primary"
                    loading={calibratingLoading}
                    onClick={handleSaveCalibration}
                  >
                    Save Calibration
                  </Button>
                </div>
              }
            >
              <form onSubmit={handleSaveCalibration} style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
                <div style={{ background: 'var(--color-surface-variant)', padding: '12px', borderRadius: 'var(--radius-control)' }}>
                  <div className="font-semibold">{calibratingEmployee.fullName}</div>
                  <div className="text-xs text-muted">
                    {calibratingEmployee.designation} • {calibratingEmployee.department}
                  </div>
                </div>

                <div>
                  <label className="text-sm font-semibold" style={{ display: 'block', marginBottom: '6px' }}>
                    Assessed Potential Level
                  </label>
                  <select
                    className="select"
                    style={{ width: '100%' }}
                    value={potentialLevel}
                    onChange={(e) => setPotentialLevel(e.target.value as 'LOW' | 'MEDIUM' | 'HIGH')}
                  >
                    <option value="HIGH">High (Demonstrates executive bandwidth & rapid adaptability)</option>
                    <option value="MEDIUM">Medium (Solid growth capability within lateral or stepped roles)</option>
                    <option value="LOW">Low (Best performing in stable, well-defined current domain)</option>
                  </select>
                </div>

                <div>
                  <label className="text-sm font-semibold" style={{ display: 'block', marginBottom: '6px' }}>
                    Performance Score Override ({performanceScore.toFixed(1)} / 5.0)
                  </label>
                  <input
                    type="range"
                    min="1.0"
                    max="5.0"
                    step="0.1"
                    value={performanceScore}
                    onChange={(e) => setPerformanceScore(parseFloat(e.target.value))}
                    style={{ width: '100%' }}
                  />
                  <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.75rem', color: 'var(--color-on-surface-muted)' }}>
                    <span>1.0 (Underperformer)</span>
                    <span>3.0 (Meets)</span>
                    <span>5.0 (Role Model)</span>
                  </div>
                </div>

                <div className="text-xs text-muted" style={{ lineHeight: 1.4 }}>
                  Recalibrating will immediately reposition this employee into the corresponding talent box and update succession planning reports.
                </div>
              </form>
            </Modal>
          )}
        </div>
      ) : null}
    </Card>
  )
}
