import { useEffect, useState } from 'react'
import { Badge, Card, LoadingState } from '../ui'
import {
  performanceApi,
  type AppraisalCycleItem,
  type RatingDistributionCurveResponse,
} from '../../lib/api'

interface RatingDistributionCardProps {
  cycles: AppraisalCycleItem[]
  selectedCycleId?: string
  onCycleChange?: (cycleId: string) => void
}

export function RatingDistributionCard({
  cycles,
  selectedCycleId: externalCycleId,
  onCycleChange,
}: RatingDistributionCardProps) {
  const [internalCycleId, setInternalCycleId] = useState<string>(
    externalCycleId || cycles[0]?.id || 'eval-2026-annual'
  )
  const activeCycleId = externalCycleId || internalCycleId

  const [distributionData, setDistributionData] =
    useState<RatingDistributionCurveResponse | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const loadDistribution = async (cycleId: string) => {
    setLoading(true)
    setError(null)
    try {
      const data = await performanceApi.getRatingDistribution(cycleId)
      setDistributionData(data)
    } catch (err) {
      console.error('Failed to load rating distribution curve', err)
      setError('Unable to load rating distribution data.')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    if (activeCycleId) {
      loadDistribution(activeCycleId)
    }
  }, [activeCycleId])

  const handleSelectCycle = (cycleId: string) => {
    setInternalCycleId(cycleId)
    onCycleChange?.(cycleId)
  }

  return (
    <Card
      title="Performance Rating Distribution Curve & Calibration"
      actions={
        <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
          <span className="text-sm text-muted">Evaluation Cycle:</span>
          <select
            className="select"
            value={activeCycleId}
            onChange={(e) => handleSelectCycle(e.target.value)}
          >
            {cycles.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name} ({c.status.replace(/_/g, ' ')})
              </option>
            ))}
          </select>
        </div>
      }
    >
      {loading ? (
        <LoadingState label="Analyzing performance rating distribution against standard Bell Curve..." />
      ) : error ? (
        <div className="calibration-alert-box">{error}</div>
      ) : distributionData ? (
        <div className="bell-curve-card">
          {/* Summary KPIs */}
          <div className="kpi-grid" style={{ marginBottom: 0 }}>
            <div className="kpi-card">
              <div className="kpi-card__val">{distributionData.totalCalibrated}</div>
              <div className="kpi-card__lbl">Calibrated Evaluations</div>
            </div>
            <div className="kpi-card">
              <div className="kpi-card__val">{distributionData.averageScore.toFixed(2)}</div>
              <div className="kpi-card__lbl">Overall Average Rating (out of 5.0)</div>
            </div>
            <div className="kpi-card">
              <div
                className="kpi-card__val"
                style={{
                  color:
                    distributionData.calibrationAlerts.length > 0
                      ? 'var(--color-warning)'
                      : 'var(--color-success)',
                }}
              >
                {distributionData.calibrationAlerts.length > 0 ? 'Calibration Needed' : 'Calibrated'}
              </div>
              <div className="kpi-card__lbl">Bell Curve Alignment</div>
            </div>
          </div>

          {/* Calibration Alerts */}
          {distributionData.calibrationAlerts.length > 0 && (
            <div className="calibration-alert-box">
              <div style={{ fontWeight: 600, marginBottom: '4px' }}>
                Calibration Committee Attention Required:
              </div>
              <ul style={{ margin: 0, paddingLeft: '20px' }}>
                {distributionData.calibrationAlerts.map((alert, idx) => (
                  <li key={idx}>{alert}</li>
                ))}
              </ul>
            </div>
          )}

          {/* SVG Bell Curve Visualizer */}
          <div className="bell-curve-svg-container">
            <svg
              viewBox="0 0 700 220"
              style={{ width: '100%', height: '100%', overflow: 'visible' }}
            >
              <defs>
                <linearGradient id="bellGradient" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stopColor="var(--color-brand-primary)" stopOpacity="0.3" />
                  <stop offset="100%" stopColor="var(--color-brand-primary)" stopOpacity="0.02" />
                </linearGradient>
                <linearGradient id="actualGradient" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stopColor="#10b981" stopOpacity="0.6" />
                  <stop offset="100%" stopColor="#10b981" stopOpacity="0.2" />
                </linearGradient>
              </defs>

              {/* Grid Lines */}
              <line x1="50" y1="180" x2="660" y2="180" stroke="var(--color-outline-variant)" strokeWidth="1" />
              <line x1="50" y1="130" x2="660" y2="130" stroke="var(--color-outline-variant)" strokeDasharray="3 3" opacity="0.4" />
              <line x1="50" y1="80" x2="660" y2="80" stroke="var(--color-outline-variant)" strokeDasharray="3 3" opacity="0.4" />
              <line x1="50" y1="30" x2="660" y2="30" stroke="var(--color-outline-variant)" strokeDasharray="3 3" opacity="0.4" />

              {/* Target Bell Curve Area & Line (Normal Gaussian: 5% - 10% - 60% - 20% - 5%) */}
              <path
                d="M 60 180 
                   C 140 180, 180 160, 240 100 
                   C 300 40, 350 20, 370 20 
                   C 390 20, 440 40, 500 100 
                   C 560 160, 600 180, 650 180 Z"
                fill="url(#bellGradient)"
              />
              <path
                d="M 60 180 
                   C 140 180, 180 160, 240 100 
                   C 300 40, 350 20, 370 20 
                   C 390 20, 440 40, 500 100 
                   C 560 160, 600 180, 650 180"
                fill="none"
                stroke="var(--color-brand-primary)"
                strokeWidth="2.5"
                strokeDasharray="4 3"
              />

              {/* Actual Distribution Histogram Bars */}
              {distributionData.bands.map((b, idx) => {
                const xPositions = [110, 230, 360, 490, 600]
                const x = xPositions[idx] ?? 100
                const barWidth = 46
                // Map actual percentage to height (0% -> 0px, 60% -> 150px)
                const barHeight = Math.min(150, (b.actualPercent / 70) * 150)
                const y = 180 - barHeight

                return (
                  <g key={b.bandIndex}>
                    {/* Actual Bar */}
                    <rect
                      x={x - barWidth / 2}
                      y={y}
                      width={barWidth}
                      height={barHeight}
                      rx="4"
                      fill={
                        Math.abs(b.variancePercent) > 6
                          ? 'var(--color-warning)'
                          : 'url(#actualGradient)'
                      }
                      stroke={Math.abs(b.variancePercent) > 6 ? '#d97706' : '#10b981'}
                      strokeWidth="1.5"
                    />
                    {/* Value on top of bar */}
                    <text
                      x={x}
                      y={y - 6}
                      textAnchor="middle"
                      fontSize="11"
                      fontWeight="bold"
                      fill="var(--color-on-surface)"
                    >
                      {b.actualPercent}% ({b.actualCount})
                    </text>
                    {/* X-axis Band Label */}
                    <text
                      x={x}
                      y="198"
                      textAnchor="middle"
                      fontSize="11"
                      fontWeight="600"
                      fill="var(--color-on-surface)"
                    >
                      Band {b.bandIndex}
                    </text>
                    <text
                      x={x}
                      y="212"
                      textAnchor="middle"
                      fontSize="9.5"
                      fill="var(--color-on-surface-muted)"
                    >
                      {b.scoreRange}
                    </text>
                  </g>
                )
              })}

              {/* Chart Legend */}
              <g transform="translate(60, 15)">
                <line x1="0" y1="0" x2="24" y2="0" stroke="var(--color-brand-primary)" strokeWidth="2.5" strokeDasharray="4 3" />
                <text x="30" y="4" fontSize="11" fill="var(--color-on-surface-muted)">Target Normal Distribution (5%-10%-60%-20%-5%)</text>
                <rect x="360" y="-7" width="14" height="14" rx="2" fill="#10b981" opacity="0.6" stroke="#10b981" />
                <text x="382" y="4" fontSize="11" fill="var(--color-on-surface-muted)">Actual Team Distribution (%)</text>
              </g>
            </svg>
          </div>

          {/* Detailed Calibration Table */}
          <div className="matrix-table-container">
            <table className="matrix-table">
              <thead>
                <tr>
                  <th>Performance Rating Band</th>
                  <th>Score Range</th>
                  <th style={{ textAlign: 'center' }}>Target %</th>
                  <th style={{ textAlign: 'center' }}>Actual Count</th>
                  <th style={{ textAlign: 'center' }}>Actual %</th>
                  <th style={{ textAlign: 'center' }}>Variance</th>
                  <th style={{ textAlign: 'center' }}>Calibration Status</th>
                </tr>
              </thead>
              <tbody>
                {distributionData.bands.map((b) => {
                  const isWarning = Math.abs(b.variancePercent) > 6
                  const isPositiveVariance = b.variancePercent > 0
                  return (
                    <tr key={b.bandIndex}>
                      <td className="font-medium">{b.label}</td>
                      <td>
                        <span className="text-muted text-xs font-mono">{b.scoreRange}</span>
                      </td>
                      <td style={{ textAlign: 'center', fontWeight: 600 }}>{b.targetPercent}%</td>
                      <td style={{ textAlign: 'center', fontWeight: 600 }}>{b.actualCount}</td>
                      <td style={{ textAlign: 'center', fontWeight: 700 }}>{b.actualPercent}%</td>
                      <td style={{ textAlign: 'center' }}>
                        <span
                          className={`gap-indicator ${
                            isWarning
                              ? 'gap-indicator--negative'
                              : isPositiveVariance
                                ? 'gap-indicator--positive'
                                : 'gap-indicator--neutral'
                          }`}
                        >
                          {isPositiveVariance ? `+${b.variancePercent}%` : `${b.variancePercent}%`}
                        </span>
                      </td>
                      <td style={{ textAlign: 'center' }}>
                        <Badge tone={isWarning ? 'warning' : 'success'}>
                          {isWarning ? 'Requires Committee Approval' : 'Aligned to Normal Curve'}
                        </Badge>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        </div>
      ) : null}
    </Card>
  )
}
