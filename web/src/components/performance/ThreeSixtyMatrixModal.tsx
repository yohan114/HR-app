import { useEffect, useState } from 'react'
import { Badge, Button, Modal, LoadingState } from '../ui'
import {
  performanceApi,
  type MraMatrixResponse,
  type MraRelationship,
} from '../../lib/api'

interface ThreeSixtyMatrixModalProps {
  appraisalId: string | null
  isOpen: boolean
  onClose: () => void
}

export function ThreeSixtyMatrixModal({
  appraisalId,
  isOpen,
  onClose,
}: ThreeSixtyMatrixModalProps) {
  const [loading, setLoading] = useState(false)
  const [matrixData, setMatrixData] = useState<MraMatrixResponse | null>(null)
  const [error, setError] = useState<string | null>(null)

  // Nomination form state
  const [isNominateOpen, setIsNominateOpen] = useState(false)
  const [nominateForm, setNominateForm] = useState<{
    reviewerEmployeeId: string
    relationship: MraRelationship
    anonymous: boolean
  }>({
    reviewerEmployeeId: 'de300000-0001-4000-8000-000000000003',
    relationship: 'PEER',
    anonymous: true,
  })
  const [nominateLoading, setNominateLoading] = useState(false)

  // Quick evaluation form state
  const [isEvaluateOpen, setIsEvaluateOpen] = useState(false)
  const [evaluatingRequestId, setEvaluatingRequestId] = useState<string | null>(null)
  const [evalForm, setEvalForm] = useState({
    strengths: 'Outstanding system architecture abilities, deep knowledge of distributed services.',
    development: 'Could delegate operational incident handling to junior engineers more proactively.',
    ratings: [
      { competencyId: 'cmp-eng-01', rating: 4.5 },
      { competencyId: 'cmp-eng-02', rating: 4.0 },
      { competencyId: 'cmp-col-01', rating: 4.5 },
    ],
  })
  const [evaluatingLoading, setEvaluatingLoading] = useState(false)

  const loadMatrix = async () => {
    if (!appraisalId) return
    setLoading(true)
    setError(null)
    try {
      const data = await performanceApi.get360Matrix(appraisalId)
      setMatrixData(data)
    } catch (err) {
      console.error('Failed to load 360 matrix', err)
      setError('Unable to load 360 evaluation matrix. Please try again.')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    if (isOpen && appraisalId) {
      loadMatrix()
    } else {
      setMatrixData(null)
      setIsNominateOpen(false)
      setIsEvaluateOpen(false)
    }
  }, [isOpen, appraisalId])

  const handleNominate = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!appraisalId) return
    setNominateLoading(true)
    try {
      await performanceApi.nominate360Reviewer(appraisalId, nominateForm)
      setIsNominateOpen(false)
      await loadMatrix()
    } catch (err) {
      console.error('Failed to nominate reviewer', err)
      alert('Failed to submit reviewer nomination')
    } finally {
      setNominateLoading(false)
    }
  }

  const handleSubmitPeerEvaluation = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!evaluatingRequestId) return
    setEvaluatingLoading(true)
    try {
      await performanceApi.submit360Evaluation(evaluatingRequestId, {
        overallStrengths: evalForm.strengths,
        overallDevelopment: evalForm.development,
        ratings: evalForm.ratings,
      })
      setIsEvaluateOpen(false)
      setEvaluatingRequestId(null)
      await loadMatrix()
    } catch (err) {
      console.error('Failed to submit 360 evaluation', err)
      alert('Failed to submit evaluation')
    } finally {
      setEvaluatingLoading(false)
    }
  }

  const relationshipBadgeTone = (rel: MraRelationship): 'danger' | 'neutral' | 'success' | 'warning' => {
    switch (rel) {
      case 'MANAGER':
        return 'warning'
      case 'PEER':
        return 'neutral'
      case 'SUBORDINATE':
        return 'success'
      case 'CROSS_FUNCTIONAL':
        return 'warning'
      default:
        return 'neutral'
    }
  }

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title="360° Multi-Rater Competency & Performance Matrix"
      size="large"
      actions={
        <div style={{ display: 'flex', justifyContent: 'space-between', width: '100%', alignItems: 'center' }}>
          <div className="text-xs text-muted">
            Cycle: <strong>{matrixData?.cycleName ?? 'Evaluation Cycle'}</strong> • Multi-Rater 360 Feedback
          </div>
          <div style={{ display: 'flex', gap: '8px' }}>
            <Button variant="secondary" onClick={onClose}>
              Close
            </Button>
            <Button
              variant="primary"
              onClick={() => {
                window.print()
              }}
            >
              Print 360 Report
            </Button>
          </div>
        </div>
      }
    >
      {loading ? (
        <LoadingState label="Computing 360 multi-rater scoring matrix and reviewer aggregations..." />
      ) : error ? (
        <div className="calibration-alert-box">{error}</div>
      ) : matrixData ? (
        <div className="three-sixty-modal-content" style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
          {/* Header Summary Banner */}
          <div className="matrix-summary-bar">
            <div>
              <h3 style={{ margin: 0, fontSize: '1.25rem', fontWeight: 700 }}>
                {matrixData.employeeName}
              </h3>
              <div className="text-xs text-muted" style={{ marginTop: '2px' }}>
                Department: <strong>{matrixData.department}</strong> • Evaluation Cycle: <strong>{matrixData.cycleName}</strong>
              </div>
            </div>
            <div className="matrix-summary-score">
              <div style={{ textAlign: 'right' }}>
                <div style={{ fontSize: '0.75rem', fontWeight: 600, color: 'var(--color-on-surface-muted)', textTransform: 'uppercase' }}>
                  Aggregated 360 Score
                </div>
                <div style={{ fontSize: '0.8125rem', color: 'var(--color-success)', fontWeight: 600 }}>
                  ★ High Performer
                </div>
              </div>
              <div className="matrix-score-badge">
                {(
                  matrixData.competencies.reduce((sum, c) => sum + c.overallScore, 0) /
                  (matrixData.competencies.length || 1)
                ).toFixed(2)}
                <span style={{ fontSize: '1rem', color: 'var(--color-on-surface-muted)', fontWeight: 400 }}> / 5.0</span>
              </div>
            </div>
          </div>

          {/* Section: 360 Reviewers & Nominations */}
          <div style={{ border: '1px solid var(--color-outline-variant)', borderRadius: 'var(--radius-card)', padding: '16px', background: 'var(--color-surface-raised)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '12px' }}>
              <div>
                <h4 style={{ margin: 0, fontSize: '0.9375rem', fontWeight: 600 }}>
                  Nominated Reviewers & Multi-Raters ({matrixData.nominations.length})
                </h4>
                <p className="text-xs text-muted" style={{ margin: '2px 0 0 0' }}>
                  Includes direct supervisor, cross-functional collaborators, peers, and direct reports.
                </p>
              </div>
              <Button variant="secondary" onClick={() => setIsNominateOpen(!isNominateOpen)}>
                {isNominateOpen ? 'Cancel' : '+ Nominate Reviewer'}
              </Button>
            </div>

            {/* Inline Nomination Form */}
            {isNominateOpen && (
              <form onSubmit={handleNominate} style={{ background: 'var(--color-surface-variant)', padding: '12px 16px', borderRadius: 'var(--radius-control)', marginBottom: '16px', display: 'flex', gap: '12px', alignItems: 'flex-end', flexWrap: 'wrap' }}>
                <div style={{ flex: '1', minWidth: '180px' }}>
                  <label className="text-xs font-semibold" style={{ display: 'block', marginBottom: '4px' }}>Reviewer</label>
                  <select
                    className="select"
                    style={{ width: '100%' }}
                    value={nominateForm.reviewerEmployeeId}
                    onChange={(e) => setNominateForm({ ...nominateForm, reviewerEmployeeId: e.target.value })}
                  >
                    <option value="de300000-0001-4000-8000-000000000003">Kavinda Silva (Staff Engineer)</option>
                    <option value="de300000-0001-4000-8000-000000000004">Anoma Fernando (Senior PM)</option>
                    <option value="de300000-0001-4000-8000-000000000005">Rohan Wickramasinghe (Lead Architect)</option>
                    <option value="de300000-0001-4000-8000-000000000006">Tharushi Gamage (QA Automation)</option>
                  </select>
                </div>

                <div style={{ width: '160px' }}>
                  <label className="text-xs font-semibold" style={{ display: 'block', marginBottom: '4px' }}>Relationship</label>
                  <select
                    className="select"
                    style={{ width: '100%' }}
                    value={nominateForm.relationship}
                    onChange={(e) => setNominateForm({ ...nominateForm, relationship: e.target.value as MraRelationship })}
                  >
                    <option value="PEER">Peer</option>
                    <option value="SUBORDINATE">Subordinate / Direct Report</option>
                    <option value="CROSS_FUNCTIONAL">Cross-Functional</option>
                    <option value="MANAGER">Manager / Secondary</option>
                  </select>
                </div>

                <div style={{ display: 'flex', alignItems: 'center', gap: '6px', height: '36px' }}>
                  <input
                    type="checkbox"
                    id="mra-anonymous"
                    checked={nominateForm.anonymous}
                    onChange={(e) => setNominateForm({ ...nominateForm, anonymous: e.target.checked })}
                  />
                  <label htmlFor="mra-anonymous" className="text-xs">Anonymous</label>
                </div>

                <Button variant="primary" loading={nominateLoading} type="submit">
                  Send Invitation
                </Button>
              </form>
            )}

            {/* Reviewers List */}
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))', gap: '8px' }}>
              {matrixData.nominations.map((nom) => (
                <div
                  key={nom.id}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    padding: '8px 12px',
                    border: '1px solid var(--color-outline-variant)',
                    borderRadius: 'var(--radius-control)',
                    background: 'var(--color-surface)',
                  }}
                >
                  <div>
                    <div className="font-medium text-sm">{nom.reviewerEmployeeName}</div>
                    <div className="text-xs text-muted">
                      {nom.reviewerTitle} • {nom.department}
                    </div>
                  </div>
                  <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: '4px' }}>
                    <div style={{ display: 'flex', gap: '4px' }}>
                      <Badge tone={relationshipBadgeTone(nom.relationship)}>
                        {nom.relationship.replace('_', ' ')}
                      </Badge>
                      <Badge tone={nom.status === 'COMPLETED' ? 'success' : 'warning'}>
                        {nom.status}
                      </Badge>
                    </div>
                    {nom.status === 'PENDING' && (
                      <button
                        type="button"
                        className="text-xs font-semibold"
                        style={{ background: 'none', border: 'none', color: 'var(--color-brand-primary)', cursor: 'pointer', textDecoration: 'underline', padding: 0 }}
                        onClick={() => {
                          setEvaluatingRequestId(nom.id)
                          setIsEvaluateOpen(true)
                        }}
                      >
                        Submit Feedback →
                      </button>
                    )}
                  </div>
                </div>
              ))}
            </div>
          </div>

          {/* Inline Peer Evaluation Drawer / Form */}
          {isEvaluateOpen && (
            <div style={{ border: '2px solid var(--color-brand-primary)', borderRadius: 'var(--radius-card)', padding: '16px', background: 'var(--color-brand-primary-container)' }}>
              <h4 style={{ margin: '0 0 8px 0', fontSize: '1rem', fontWeight: 700 }}>
                Complete 360 Multi-Rater Evaluation
              </h4>
              <p className="text-xs text-muted" style={{ margin: '0 0 12px 0' }}>
                Your feedback directly populates the competency score matrix and growth plans.
              </p>
              <form onSubmit={handleSubmitPeerEvaluation}>
                <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                  <div>
                    <label className="text-xs font-semibold" style={{ display: 'block', marginBottom: '4px' }}>
                      Key Strengths & High-Impact Contributions
                    </label>
                    <textarea
                      className="input"
                      rows={2}
                      style={{ width: '100%', resize: 'vertical' }}
                      value={evalForm.strengths}
                      onChange={(e) => setEvalForm({ ...evalForm, strengths: e.target.value })}
                      required
                    />
                  </div>
                  <div>
                    <label className="text-xs font-semibold" style={{ display: 'block', marginBottom: '4px' }}>
                      Growth Opportunities & Development Guidance
                    </label>
                    <textarea
                      className="input"
                      rows={2}
                      style={{ width: '100%', resize: 'vertical' }}
                      value={evalForm.development}
                      onChange={(e) => setEvalForm({ ...evalForm, development: e.target.value })}
                      required
                    />
                  </div>
                  <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px' }}>
                    <Button variant="secondary" onClick={() => setIsEvaluateOpen(false)}>
                      Cancel
                    </Button>
                    <Button variant="primary" loading={evaluatingLoading} type="submit">
                      Submit 360 Review
                    </Button>
                  </div>
                </div>
              </form>
            </div>
          )}

          {/* Multi-Rater Competency Score Comparison Matrix */}
          <div>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
              <h4 style={{ margin: 0, fontSize: '0.9375rem', fontWeight: 600 }}>
                Competency Rubric Multi-Rater Breakdown
              </h4>
              <div className="text-xs text-muted">
                Target Benchmark vs. Self, Supervisor, Peer, Subordinate & Cross-Functional Ratings
              </div>
            </div>

            <div className="matrix-table-container">
              <table className="matrix-table">
                <thead>
                  <tr>
                    <th>Competency & Framework</th>
                    <th style={{ textAlign: 'center' }}>Target</th>
                    <th style={{ textAlign: 'center' }}>Self</th>
                    <th style={{ textAlign: 'center' }}>Manager</th>
                    <th style={{ textAlign: 'center' }}>Peers</th>
                    <th style={{ textAlign: 'center' }}>Subord.</th>
                    <th style={{ textAlign: 'center' }}>Cross-Func</th>
                    <th style={{ textAlign: 'center', fontWeight: 700, background: 'var(--color-brand-primary-container)' }}>
                      Overall 360
                    </th>
                    <th style={{ textAlign: 'center' }}>Gap</th>
                  </tr>
                </thead>
                <tbody>
                  {matrixData.competencies.map((c) => {
                    const isPositive = c.gap >= 0
                    return (
                      <tr key={c.competencyId}>
                        <td>
                          <div className="font-medium">{c.name}</div>
                          <div className="text-xs text-muted">
                            {c.code} • {c.groupName}
                          </div>
                        </td>
                        <td style={{ textAlign: 'center', fontWeight: 600 }}>
                          {c.targetLevel.toFixed(1)}
                        </td>
                        <td style={{ textAlign: 'center' }}>{c.selfScore.toFixed(1)}</td>
                        <td style={{ textAlign: 'center' }}>{c.managerScore.toFixed(1)}</td>
                        <td style={{ textAlign: 'center' }}>{c.peerScore.toFixed(1)}</td>
                        <td style={{ textAlign: 'center' }}>{c.subordinateScore.toFixed(1)}</td>
                        <td style={{ textAlign: 'center' }}>{c.crossFunctionalScore.toFixed(1)}</td>
                        <td
                          style={{
                            textAlign: 'center',
                            fontWeight: 700,
                            background: 'var(--color-brand-primary-container)',
                            color: 'var(--color-brand-on-primary-container)',
                          }}
                        >
                          {c.overallScore.toFixed(2)}
                        </td>
                        <td style={{ textAlign: 'center' }}>
                          <span
                            className={`gap-indicator ${
                              isPositive ? 'gap-indicator--positive' : 'gap-indicator--negative'
                            }`}
                          >
                            {isPositive ? `+${c.gap.toFixed(2)}` : c.gap.toFixed(2)}
                          </span>
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
          </div>

          {/* Qualitative Insights: Strengths & Growth Areas */}
          <div className="insights-grid">
            <div className="insight-card insight-card--strengths">
              <div className="insight-card__title" style={{ color: 'var(--color-success)' }}>
                <span>✓</span> Identified Core Strengths & Superpowers
              </div>
              <ul className="insight-list">
                {matrixData.strengths.map((str, idx) => (
                  <li key={idx}>
                    <span style={{ color: 'var(--color-success)', fontWeight: 700 }}>•</span>
                    <span>{str}</span>
                  </li>
                ))}
              </ul>
            </div>

            <div className="insight-card insight-card--development">
              <div className="insight-card__title" style={{ color: 'var(--color-warning)' }}>
                <span>🎯</span> Recommended Development & Growth Areas
              </div>
              <ul className="insight-list">
                {matrixData.developmentAreas.map((dev, idx) => (
                  <li key={idx}>
                    <span style={{ color: 'var(--color-warning)', fontWeight: 700 }}>•</span>
                    <span>{dev}</span>
                  </li>
                ))}
              </ul>
            </div>
          </div>
        </div>
      ) : null}
    </Modal>
  )
}
