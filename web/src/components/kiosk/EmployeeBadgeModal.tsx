import { useMemo, useState } from 'react'
import type { EmployeeBadgeItem } from '@/lib/api'
import { generateSvgBarcode, generateSvgQrCode } from '@/lib/qrcode'

interface EmployeeBadgeModalProps {
  isOpen: boolean
  onClose: () => void
  badges: EmployeeBadgeItem[]
  selectedBadgeCode?: string
  onSimulateScan: (employeeCode: string, payload: string) => void
}

export function EmployeeBadgeModal({
  isOpen,
  onClose,
  badges,
  selectedBadgeCode,
  onSimulateScan,
}: EmployeeBadgeModalProps) {
  const [activeCode, setActiveCode] = useState<string>(
    selectedBadgeCode || badges[0]?.employeeCode || 'E004',
  )
  const [viewSide, setViewSide] = useState<'BOTH' | 'FRONT' | 'BACK'>('BOTH')

  // Find currently selected badge
  const activeBadge = useMemo(() => {
    return (
      badges.find((b) => b.employeeCode === activeCode) ||
      badges[0] || {
        employeeId: 'emp-004',
        employeeCode: 'E004',
        fullName: 'Kasun Fernando',
        department: 'Engineering',
        designation: 'Senior Process Engineer',
        nfcSerial: '04:A2:3B:7C:9E:04',
        barcode: 'E004-8842',
        qrPayload: JSON.stringify({ employeeCode: 'E004', pin: '1234', type: 'HR-BADGE' }),
        issueDate: '2025-01-01',
        expiryDate: '2028-12-31',
        cardType: 'STANDARD' as const,
        status: 'ACTIVE' as const,
        avatarInitials: 'KF',
        bloodGroup: 'O+',
        emergencyContact: '+94 11 234 5678',
      }
    )
  }, [badges, activeCode])

  const qrSvg = useMemo(() => {
    return generateSvgQrCode(activeBadge.qrPayload, {
      size: 25,
      pixelSize: 5,
      fgColor: '#0f172a',
      bgColor: '#ffffff',
    })
  }, [activeBadge.qrPayload])

  const barcodeSvg = useMemo(() => {
    return generateSvgBarcode(activeBadge.barcode, {
      height: 48,
      fgColor: '#0f172a',
      showText: true,
    })
  }, [activeBadge.barcode])

  const handlePrint = () => {
    window.print()
  }

  if (!isOpen) return null

  return (
    <div
      className="badge-modal-backdrop"
      style={{
        position: 'fixed',
        inset: 0,
        backgroundColor: 'rgba(10, 15, 29, 0.88)',
        backdropFilter: 'blur(12px)',
        zIndex: 10000,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: '1.25rem',
        overflowY: 'auto',
      }}
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose()
      }}
    >
      <div
        style={{
          background: '#0f172a',
          border: '1px solid rgba(255, 255, 255, 0.15)',
          borderRadius: '18px',
          width: '100%',
          maxWidth: '920px',
          color: '#f8fafc',
          boxShadow: '0 25px 60px -15px rgba(0, 0, 0, 0.7)',
          overflow: 'hidden',
          display: 'flex',
          flexDirection: 'column',
          maxHeight: '90vh',
        }}
      >
        {/* Modal Header */}
        <div
          style={{
            padding: '1.25rem 1.5rem',
            borderBottom: '1px solid rgba(255, 255, 255, 0.1)',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            background: 'rgba(30, 41, 59, 0.5)',
            flexWrap: 'wrap',
            gap: '1rem',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
            <div
              style={{
                width: '38px',
                height: '38px',
                borderRadius: '10px',
                background: 'linear-gradient(135deg, #0ea5e9 0%, #2563eb 100%)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontSize: '1.2rem',
              }}
            >
              🪪
            </div>
            <div>
              <h2 style={{ margin: 0, fontSize: '1.2rem', fontWeight: 700 }}>
                Digital & Printable CR80 Employee ID Badge
              </h2>
              <div style={{ fontSize: '0.8rem', color: '#94a3b8' }}>
                ISO/IEC 7810 Standard · Vector Scannable QR & RFID/NFC Badge
              </div>
            </div>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
            {/* Select Employee Dropdown */}
            <select
              value={activeCode}
              onChange={(e) => setActiveCode(e.target.value)}
              style={{
                padding: '0.5rem 0.875rem',
                borderRadius: '8px',
                background: '#1e293b',
                border: '1px solid rgba(255, 255, 255, 0.15)',
                color: '#ffffff',
                fontSize: '0.85rem',
                fontWeight: 600,
                cursor: 'pointer',
              }}
            >
              {badges.map((b) => (
                <option key={b.employeeCode} value={b.employeeCode}>
                  {b.employeeCode} - {b.fullName} ({b.department})
                </option>
              ))}
            </select>

            <button
              type="button"
              onClick={onClose}
              style={{
                background: 'transparent',
                border: 'none',
                color: '#94a3b8',
                fontSize: '1.5rem',
                cursor: 'pointer',
                lineHeight: 1,
                padding: '0.25rem 0.5rem',
              }}
              title="Close modal"
            >
              ✕
            </button>
          </div>
        </div>

        {/* Toolbar & View Controls */}
        <div
          style={{
            padding: '0.75rem 1.5rem',
            background: 'rgba(15, 23, 42, 0.7)',
            borderBottom: '1px solid rgba(255, 255, 255, 0.06)',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            flexWrap: 'wrap',
            gap: '0.75rem',
          }}
        >
          {/* View Mode Buttons */}
          <div style={{ display: 'flex', background: 'rgba(0, 0, 0, 0.3)', borderRadius: '8px', padding: '3px' }}>
            <button
              type="button"
              onClick={() => setViewSide('BOTH')}
              style={{
                padding: '0.4rem 0.85rem',
                border: 'none',
                borderRadius: '6px',
                background: viewSide === 'BOTH' ? '#2563eb' : 'transparent',
                color: '#ffffff',
                fontSize: '0.8rem',
                fontWeight: 600,
                cursor: 'pointer',
              }}
            >
              Dual Sides
            </button>
            <button
              type="button"
              onClick={() => setViewSide('FRONT')}
              style={{
                padding: '0.4rem 0.85rem',
                border: 'none',
                borderRadius: '6px',
                background: viewSide === 'FRONT' ? '#2563eb' : 'transparent',
                color: '#ffffff',
                fontSize: '0.8rem',
                fontWeight: 600,
                cursor: 'pointer',
              }}
            >
              Front Only
            </button>
            <button
              type="button"
              onClick={() => setViewSide('BACK')}
              style={{
                padding: '0.4rem 0.85rem',
                border: 'none',
                borderRadius: '6px',
                background: viewSide === 'BACK' ? '#2563eb' : 'transparent',
                color: '#ffffff',
                fontSize: '0.8rem',
                fontWeight: 600,
                cursor: 'pointer',
              }}
            >
              Back (QR / Barcode)
            </button>
          </div>

          {/* Action Buttons */}
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
            <button
              type="button"
              onClick={() => {
                onSimulateScan(activeBadge.employeeCode, activeBadge.qrPayload)
                onClose()
              }}
              style={{
                padding: '0.5rem 1rem',
                background: 'linear-gradient(135deg, #10b981 0%, #059669 100%)',
                border: 'none',
                borderRadius: '8px',
                color: '#ffffff',
                fontSize: '0.85rem',
                fontWeight: 700,
                cursor: 'pointer',
                display: 'flex',
                alignItems: 'center',
                gap: '0.5rem',
                boxShadow: '0 4px 12px rgba(16, 185, 129, 0.3)',
              }}
            >
              <span>⚡ Simulate Kiosk Scan</span>
            </button>

            <button
              type="button"
              onClick={handlePrint}
              style={{
                padding: '0.5rem 1rem',
                background: 'rgba(255, 255, 255, 0.08)',
                border: '1px solid rgba(255, 255, 255, 0.2)',
                borderRadius: '8px',
                color: '#f8fafc',
                fontSize: '0.85rem',
                fontWeight: 600,
                cursor: 'pointer',
                display: 'flex',
                alignItems: 'center',
                gap: '0.4rem',
              }}
            >
              <span>🖨️ Print Badge (CR80)</span>
            </button>
          </div>
        </div>

        {/* Modal Body: The Badge Cards */}
        <div
          className="badge-print-area"
          style={{
            padding: '2rem 1.5rem',
            display: 'flex',
            justifyContent: 'center',
            alignItems: 'center',
            gap: '2.5rem',
            flexWrap: 'wrap',
            background: 'radial-gradient(ellipse at center, #1e293b 0%, #090d16 100%)',
            overflowY: 'auto',
          }}
        >
          {/* ================================================================= */}
          {/* FRONT SIDE CARD (Standard CR80: 3.370 in × 2.125 in / 340px × 214px)*/}
          {/* ================================================================= */}
          {(viewSide === 'BOTH' || viewSide === 'FRONT') && (
            <div
              className="cr80-card cr80-front"
              style={{
                width: '340px',
                height: '214px',
                borderRadius: '12px',
                background: 'linear-gradient(145deg, #091e3a 0%, #061122 55%, #020617 100%)',
                border: '1px solid rgba(56, 189, 248, 0.3)',
                boxShadow: '0 12px 30px rgba(0, 0, 0, 0.6), 0 0 20px rgba(14, 165, 233, 0.15)',
                position: 'relative',
                overflow: 'hidden',
                display: 'flex',
                flexDirection: 'column',
                justifyContent: 'space-between',
                padding: '12px 14px',
                boxSizing: 'border-box',
                color: '#ffffff',
              }}
            >
              {/* Lanyard punch hole preview */}
              <div
                style={{
                  position: 'absolute',
                  top: '6px',
                  left: '50%',
                  transform: 'translateX(-50%)',
                  width: '32px',
                  height: '6px',
                  background: '#090d16',
                  borderRadius: '3px',
                  border: '1px solid rgba(255, 255, 255, 0.2)',
                }}
              />

              {/* Top Row: Company Logo & Contactless Symbol */}
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginTop: '4px' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <div
                    style={{
                      width: '26px',
                      height: '26px',
                      borderRadius: '6px',
                      background: 'linear-gradient(135deg, #38bdf8 0%, #2563eb 100%)',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      fontWeight: 900,
                      fontSize: '0.8rem',
                      color: '#ffffff',
                      boxShadow: '0 2px 6px rgba(56, 189, 248, 0.4)',
                    }}
                  >
                    HR
                  </div>
                  <div>
                    <div style={{ fontSize: '0.65rem', fontWeight: 800, letterSpacing: '0.08em', color: '#38bdf8', textTransform: 'uppercase' }}>
                      LANKA AGRI & APPAREL
                    </div>
                    <div style={{ fontSize: '0.55rem', color: '#94a3b8', letterSpacing: '0.04em' }}>
                      AUTHORISED PERSONNEL
                    </div>
                  </div>
                </div>

                {/* RFID / NFC Wave Icon */}
                <div
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: '4px',
                    background: 'rgba(56, 189, 248, 0.12)',
                    border: '1px solid rgba(56, 189, 248, 0.3)',
                    padding: '2px 6px',
                    borderRadius: '4px',
                    fontSize: '0.6rem',
                    color: '#38bdf8',
                    fontWeight: 700,
                  }}
                  title="NFC MIFARE DESFire EV3 Contactless Badge"
                >
                  <span style={{ fontSize: '0.75rem' }}>🛜</span>
                  <span>NFC 8K</span>
                </div>
              </div>

              {/* Middle Section: Photo & Employee Information */}
              <div style={{ display: 'flex', alignItems: 'center', gap: '12px', margin: '6px 0' }}>
                <div
                  style={{
                    width: '68px',
                    height: '76px',
                    borderRadius: '8px',
                    background: 'linear-gradient(135deg, #1e293b 0%, #0f172a 100%)',
                    border: '2px solid #38bdf8',
                    display: 'flex',
                    flexDirection: 'column',
                    alignItems: 'center',
                    justifyContent: 'center',
                    color: '#38bdf8',
                    fontWeight: 800,
                    fontSize: '1.4rem',
                    boxShadow: '0 4px 10px rgba(0, 0, 0, 0.4)',
                    position: 'relative',
                  }}
                >
                  {activeBadge.avatarInitials}
                  <div
                    style={{
                      position: 'absolute',
                      bottom: '2px',
                      fontSize: '0.5rem',
                      color: '#94a3b8',
                      fontWeight: 600,
                      letterSpacing: '0.05em',
                    }}
                  >
                    PHOTO
                  </div>
                </div>

                <div style={{ flex: 1, minWidth: 0 }}>
                  <div
                    style={{
                      fontSize: '1rem',
                      fontWeight: 800,
                      color: '#ffffff',
                      lineHeight: 1.2,
                      whiteSpace: 'nowrap',
                      overflow: 'hidden',
                      textOverflow: 'ellipsis',
                    }}
                  >
                    {activeBadge.fullName}
                  </div>
                  <div
                    style={{
                      fontSize: '0.7rem',
                      fontWeight: 600,
                      color: '#38bdf8',
                      marginTop: '2px',
                    }}
                  >
                    {activeBadge.designation}
                  </div>
                  <div
                    style={{
                      fontSize: '0.62rem',
                      color: '#94a3b8',
                      marginTop: '1px',
                    }}
                  >
                    Dept: <strong style={{ color: '#e2e8f0' }}>{activeBadge.department}</strong>
                  </div>

                  <div style={{ display: 'flex', gap: '8px', marginTop: '6px' }}>
                    <div
                      style={{
                        background: 'rgba(255, 255, 255, 0.08)',
                        padding: '2px 6px',
                        borderRadius: '4px',
                        fontSize: '0.58rem',
                        fontWeight: 700,
                        color: '#f8fafc',
                      }}
                    >
                      ID: {activeBadge.employeeCode}
                    </div>
                    <div
                      style={{
                        background: activeBadge.cardType === 'EXECUTIVE' ? 'rgba(234, 179, 8, 0.2)' : 'rgba(37, 99, 235, 0.2)',
                        border: `1px solid ${activeBadge.cardType === 'EXECUTIVE' ? '#eab308' : '#3b82f6'}`,
                        padding: '2px 6px',
                        borderRadius: '4px',
                        fontSize: '0.58rem',
                        fontWeight: 700,
                        color: activeBadge.cardType === 'EXECUTIVE' ? '#facc15' : '#60a5fa',
                      }}
                    >
                      {activeBadge.cardType}
                    </div>
                  </div>
                </div>
              </div>

              {/* Bottom Row: Security hologram foil seal & Expiry */}
              <div
                style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  borderTop: '1px solid rgba(255, 255, 255, 0.1)',
                  paddingTop: '6px',
                  fontSize: '0.58rem',
                  color: '#94a3b8',
                }}
              >
                <div>
                  EXP: <strong style={{ color: '#f8fafc' }}>{activeBadge.expiryDate}</strong>
                </div>
                {/* Security hologram sticker */}
                <div
                  style={{
                    background: 'linear-gradient(135deg, #ec4899 0%, #8b5cf6 50%, #06b6d4 100%)',
                    padding: '2px 6px',
                    borderRadius: '3px',
                    fontSize: '0.52rem',
                    fontWeight: 900,
                    color: '#ffffff',
                    letterSpacing: '0.08em',
                    boxShadow: '0 0 8px rgba(139, 92, 246, 0.5)',
                  }}
                >
                  SECURE SEC-88
                </div>
                <div>
                  NFC: <span style={{ fontFamily: 'monospace' }}>{activeBadge.nfcSerial}</span>
                </div>
              </div>
            </div>
          )}

          {/* ================================================================= */}
          {/* BACK SIDE CARD (QR Code, Barcode, Emergency Info, Legal)         */}
          {/* ================================================================= */}
          {(viewSide === 'BOTH' || viewSide === 'BACK') && (
            <div
              className="cr80-card cr80-back"
              style={{
                width: '340px',
                height: '214px',
                borderRadius: '12px',
                background: '#ffffff',
                border: '1px solid #cbd5e1',
                boxShadow: '0 12px 30px rgba(0, 0, 0, 0.6)',
                position: 'relative',
                overflow: 'hidden',
                display: 'flex',
                flexDirection: 'column',
                justifyContent: 'space-between',
                padding: '10px 14px',
                boxSizing: 'border-box',
                color: '#0f172a',
              }}
            >
              {/* Lanyard punch hole preview */}
              <div
                style={{
                  position: 'absolute',
                  top: '6px',
                  left: '50%',
                  transform: 'translateX(-50%)',
                  width: '32px',
                  height: '6px',
                  background: '#e2e8f0',
                  borderRadius: '3px',
                  border: '1px solid #94a3b8',
                }}
              />

              {/* Magnetic stripe simulator */}
              <div
                style={{
                  height: '24px',
                  background: '#1e293b',
                  margin: '-10px -14px 6px -14px',
                }}
              />

              {/* Main Content: Left QR Code, Right Details & Barcode */}
              <div style={{ display: 'flex', gap: '12px', alignItems: 'center' }}>
                {/* Embedded QR Code */}
                <div
                  style={{
                    width: '84px',
                    height: '84px',
                    border: '1px solid #0f172a',
                    borderRadius: '6px',
                    padding: '3px',
                    background: '#ffffff',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                  }}
                  dangerouslySetInnerHTML={{ __html: qrSvg }}
                  title="Scannable Vector QR Code"
                />

                <div style={{ flex: 1, minWidth: 0, fontSize: '0.62rem' }}>
                  <div style={{ fontWeight: 800, fontSize: '0.72rem', color: '#0f172a' }}>
                    KIOSK SMART PASS
                  </div>
                  <div style={{ color: '#475569', marginTop: '2px' }}>
                    Hold QR code 10–20 cm in front of terminal camera or tap against NFC sensor.
                  </div>
                  <div style={{ marginTop: '4px', color: '#0f172a', fontWeight: 600 }}>
                    Emergency: {activeBadge.emergencyContact}
                  </div>
                  <div style={{ color: '#0f172a', fontWeight: 600 }}>
                    Blood Group: {activeBadge.bloodGroup || 'O+'}
                  </div>
                </div>
              </div>

              {/* Code 128 Barcode */}
              <div
                style={{
                  margin: '4px 0',
                  display: 'flex',
                  justifyContent: 'center',
                  background: '#ffffff',
                }}
                dangerouslySetInnerHTML={{ __html: barcodeSvg }}
              />

              {/* Terms of Use footer */}
              <div
                style={{
                  fontSize: '0.48rem',
                  color: '#64748b',
                  textAlign: 'center',
                  lineHeight: 1.2,
                  borderTop: '1px solid #e2e8f0',
                  paddingTop: '3px',
                }}
              >
                Property of Lanka Agri Holdings PLC. If found, please return to any HR or Security station.
              </div>
            </div>
          )}
        </div>

        {/* Print Stylesheet Hook */}
        <style>
          {`
            @media print {
              body * {
                visibility: hidden;
              }
              .badge-modal-backdrop, .badge-print-area, .cr80-card, .cr80-card * {
                visibility: visible;
              }
              .badge-modal-backdrop {
                position: absolute;
                inset: 0;
                background: transparent !important;
                padding: 0 !important;
              }
              .badge-print-area {
                background: transparent !important;
                padding: 10mm !important;
                gap: 15mm !important;
              }
              .cr80-card {
                page-break-inside: avoid;
                -webkit-print-color-adjust: exact !important;
                print-color-adjust: exact !important;
              }
            }
          `}
        </style>
      </div>
    </div>
  )
}
