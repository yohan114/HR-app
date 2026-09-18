import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import type { DirectoryEntry } from '@hr/client'
import { attendanceApi, directoryApi, type PunchType, type RawPunchItem, type EmployeeBadgeItem } from '@/lib/api'
import { EmployeeBadgeModal } from '@/components/kiosk/EmployeeBadgeModal'
import { parseBadgePayload } from '@/lib/qrcode'

// Subtle web audio sound effects for tactile kiosk experience
function playBeep(type: 'tap' | 'success' | 'error') {
  try {
    const ctx = new (window.AudioContext || (window as any).webkitAudioContext)()
    const osc = ctx.createOscillator()
    const gain = ctx.createGain()
    osc.connect(gain)
    gain.connect(ctx.destination)

    if (type === 'tap') {
      osc.type = 'sine'
      osc.frequency.setValueAtTime(600, ctx.currentTime)
      gain.gain.setValueAtTime(0.04, ctx.currentTime)
      gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.05)
      osc.start()
      osc.stop(ctx.currentTime + 0.05)
    } else if (type === 'success') {
      osc.type = 'triangle'
      osc.frequency.setValueAtTime(523.25, ctx.currentTime) // C5
      osc.frequency.setValueAtTime(659.25, ctx.currentTime + 0.08) // E5
      osc.frequency.setValueAtTime(783.99, ctx.currentTime + 0.16) // G5
      gain.gain.setValueAtTime(0.12, ctx.currentTime)
      gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.35)
      osc.start()
      osc.stop(ctx.currentTime + 0.35)
    } else {
      osc.type = 'sawtooth'
      osc.frequency.setValueAtTime(220, ctx.currentTime)
      osc.frequency.setValueAtTime(180, ctx.currentTime + 0.1)
      gain.gain.setValueAtTime(0.12, ctx.currentTime)
      gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.25)
      osc.start()
      osc.stop(ctx.currentTime + 0.25)
    }
  } catch {
    // AudioContext blocked or not supported
  }
}

export function AttendanceKiosk() {
  const queryClient = useQueryClient()

  // ---------------------------------------------------------------------------
  // Live Clock & Date
  // ---------------------------------------------------------------------------
  const [currentTime, setCurrentTime] = useState<Date>(new Date())
  useEffect(() => {
    const timer = setInterval(() => setCurrentTime(new Date()), 1000)
    return () => clearInterval(timer)
  }, [])

  const formattedTime = useMemo(() => {
    return currentTime.toLocaleTimeString([], {
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      hour12: true,
    })
  }, [currentTime])

  const formattedDate = useMemo(() => {
    return currentTime.toLocaleDateString([], {
      weekday: 'long',
      year: 'numeric',
      month: 'long',
      day: 'numeric',
    })
  }, [currentTime])

  // ---------------------------------------------------------------------------
  // Kiosk Terminal Configuration
  // ---------------------------------------------------------------------------
  const [kioskDeviceId] = useState('KIOSK-FACT-01')
  const [kioskLocation] = useState('Factory Floor · Gate 3 Turnstile')
  const [isFullscreen, setIsFullscreen] = useState(false)

  const toggleFullscreen = () => {
    if (!document.fullscreenElement) {
      document.documentElement.requestFullscreen().catch(() => {})
      setIsFullscreen(true)
    } else {
      document.exitFullscreen().catch(() => {})
      setIsFullscreen(false)
    }
  }

  // ---------------------------------------------------------------------------
  // Employee Selection & Input Mode
  // ---------------------------------------------------------------------------
  const [inputMode, setInputMode] = useState<'KEYPAD' | 'DIRECTORY'>('KEYPAD')
  const [activeInputTarget, setActiveInputTarget] = useState<'CODE' | 'PIN'>('CODE')
  const [employeeCode, setEmployeeCode] = useState('E004') // Pre-filled default for instant demo
  const [pin, setPin] = useState('1234') // Default demo PIN
  const [directorySearch, setDirectorySearch] = useState('')

  // Query employee status when a code is provided
  const employeeStatusQuery = useQuery({
    queryKey: ['kiosk', 'employee-status', employeeCode],
    queryFn: () => attendanceApi.getKioskEmployeeStatus(employeeCode),
    enabled: Boolean(employeeCode.trim().length >= 3),
    retry: false,
  })

  // Directory listing for quick visual selection
  const directoryQuery = useQuery({
    queryKey: ['kiosk', 'directory'],
    queryFn: () => directoryApi.searchDirectory({ limit: 50 }),
  })

  // Recent punches feed
  const recentPunchesQuery = useQuery({
    queryKey: ['kiosk', 'recent-punches', kioskDeviceId],
    queryFn: () => attendanceApi.listKioskRecentPunches(kioskDeviceId),
    refetchInterval: 6000,
  })

  // Badges list for ID badge cards and scanner simulations
  const badgesQuery = useQuery({
    queryKey: ['kiosk', 'badges'],
    queryFn: () => attendanceApi.listBadges(),
  })

  // ---------------------------------------------------------------------------
  // Camera & Snapshot Capture
  // ---------------------------------------------------------------------------
  const videoRef = useRef<HTMLVideoElement | null>(null)
  const [cameraActive, setCameraActive] = useState(false)
  const [cameraError, setCameraError] = useState(false)

  useEffect(() => {
    let stream: MediaStream | null = null
    navigator.mediaDevices
      ?.getUserMedia({ video: { width: { ideal: 640 }, height: { ideal: 480 }, facingMode: 'user' } })
      .then((s) => {
        stream = s
        if (videoRef.current) {
          videoRef.current.srcObject = s
          setCameraActive(true)
        }
      })
      .catch(() => {
        setCameraError(true)
      })

    return () => {
      if (stream) {
        stream.getTracks().forEach((track) => track.stop())
      }
    }
  }, [])

  const captureSnapshot = useCallback((): string => {
    try {
      const canvas = document.createElement('canvas')
      canvas.width = 320
      canvas.height = 240
      const ctx = canvas.getContext('2d')
      if (ctx) {
        if (videoRef.current && cameraActive) {
          ctx.drawImage(videoRef.current, 0, 0, 320, 240)
        } else {
          // Draw simulated audit verification watermark badge
          ctx.fillStyle = '#1e293b'
          ctx.fillRect(0, 0, 320, 240)
          ctx.fillStyle = '#38bdf8'
          ctx.beginPath()
          ctx.arc(160, 100, 45, 0, Math.PI * 2)
          ctx.fill()
          ctx.fillStyle = '#ffffff'
          ctx.font = 'bold 14px sans-serif'
          ctx.textAlign = 'center'
          ctx.fillText(`KIOSK AUDIT SNAPSHOT`, 160, 180)
          ctx.font = '12px sans-serif'
          ctx.fillText(new Date().toISOString(), 160, 205)
        }
        return canvas.toDataURL('image/jpeg', 0.8)
      }
    } catch {
      // fallback
    }
    return 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="320" height="240"><rect width="320" height="240" fill="%231e293b"/><text x="160" y="120" fill="white" text-anchor="middle">KIOSK VERIFIED</text></svg>'
  }, [cameraActive])

  // ---------------------------------------------------------------------------
  // Keypad Handlers
  // ---------------------------------------------------------------------------
  const handleKeypadPress = (val: string) => {
    playBeep('tap')
    if (activeInputTarget === 'CODE') {
      if (employeeCode.length < 6) {
        setEmployeeCode((prev) => prev + val)
      }
    } else {
      if (pin.length < 4) {
        setPin((prev) => prev + val)
      }
    }
  }

  const handleKeypadBackspace = () => {
    playBeep('tap')
    if (activeInputTarget === 'CODE') {
      setEmployeeCode((prev) => prev.slice(0, -1))
    } else {
      setPin((prev) => prev.slice(0, -1))
    }
  }

  const handleKeypadClear = () => {
    playBeep('tap')
    if (activeInputTarget === 'CODE') {
      setEmployeeCode('')
    } else {
      setPin('')
    }
  }

  // ---------------------------------------------------------------------------
  // Punch Execution & Success Modal
  // ---------------------------------------------------------------------------
  const [punchFeedback, setPunchFeedback] = useState<{
    confirmationId: string
    employeeName: string
    employeeCode: string
    department: string
    punchType: PunchType
    punchedAt: string
    greeting: string
    snapshot: string
    shiftName: string
  } | null>(null)

  const [countdown, setCountdown] = useState(5)

  // Auto-reset countdown when confirmation appears
  useEffect(() => {
    if (!punchFeedback) return
    setCountdown(5)
    const interval = setInterval(() => {
      setCountdown((c) => {
        if (c <= 1) {
          clearInterval(interval)
          setPunchFeedback(null)
          setPin('')
          return 5
        }
        return c - 1
      })
    }, 1000)

    return () => clearInterval(interval)
  }, [punchFeedback])

  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  const punchMutation = useMutation({
    mutationFn: async (punchType: PunchType) => {
      setErrorMessage(null)
      const photoSnapshot = captureSnapshot()
      return attendanceApi.submitKioskPunch({
        employeeCode: employeeCode.trim(),
        pin: pin.trim(),
        punchType,
        kioskDeviceId,
        kioskLocation,
        photoSnapshot,
      })
    },
    onSuccess: (resp) => {
      playBeep('success')
      setPunchFeedback({
        confirmationId: resp.confirmationId,
        employeeName: resp.employee.name,
        employeeCode: resp.employee.code,
        department: resp.employee.department,
        punchType: resp.punchType,
        punchedAt: resp.punchedAt,
        greeting: resp.greeting,
        snapshot: captureSnapshot(),
        shiftName: resp.shiftName,
      })
      void queryClient.invalidateQueries({ queryKey: ['kiosk'] })
      void queryClient.invalidateQueries({ queryKey: ['attendance'] })
    },
    onError: (err: any) => {
      playBeep('error')
      setErrorMessage(err.message || 'Verification failed. Please check your PIN.')
    },
  })

  // ---------------------------------------------------------------------------
  // Touchless Badge Scanner & 1-Second Auto-Punch Engine
  // ---------------------------------------------------------------------------
  const [autoPunchEnabled, setAutoPunchEnabled] = useState(true)
  const [isBadgeModalOpen, setIsBadgeModalOpen] = useState(false)
  const [badgeModalCode, setBadgeModalCode] = useState('E004')
  const [lastScanNotice, setLastScanNotice] = useState<{
    code: string
    source: string
    time: string
    action?: string
  } | null>(null)
  const [nfcSupported, setNfcSupported] = useState(false)
  const [nfcReading, setNfcReading] = useState(false)
  const lastScannedTimeRef = useRef<{ code: string; time: number }>({ code: '', time: 0 })

  const handleBadgeScanned = useCallback(
    async (code: string, rawPin?: string, source: string = 'CAMERA QR') => {
      const cleanCode = code.trim().toUpperCase()
      const now = Date.now()
      // Debounce duplicate scans within 2 seconds
      if (
        lastScannedTimeRef.current.code === cleanCode &&
        now - lastScannedTimeRef.current.time < 2000
      ) {
        return
      }
      lastScannedTimeRef.current = { code: cleanCode, time: now }

      playBeep('tap')
      setEmployeeCode(cleanCode)
      setPin(rawPin || '1234')
      setLastScanNotice({
        code: cleanCode,
        source,
        time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' }),
        action: autoPunchEnabled ? '⚡ 1-Sec Auto-Punch' : 'Badge Verified',
      })

      if (autoPunchEnabled) {
        setErrorMessage(null)
        try {
          // Fetch employee current status to determine IN vs OUT
          const status = await attendanceApi.getKioskEmployeeStatus(cleanCode)
          const suggestedType: PunchType = status.currentStatus?.isClockedIn ? 'OUT' : 'IN'
          const photoSnapshot = captureSnapshot()

          const resp = await attendanceApi.submitKioskPunch({
            employeeCode: cleanCode,
            pin: rawPin || 'AUTO_PUNCH',
            punchType: suggestedType,
            kioskDeviceId,
            kioskLocation,
            photoSnapshot,
          })

          playBeep('success')
          setPunchFeedback({
            confirmationId: resp.confirmationId,
            employeeName: resp.employee.name,
            employeeCode: resp.employee.code,
            department: resp.employee.department,
            punchType: resp.punchType,
            punchedAt: resp.punchedAt,
            greeting: resp.greeting,
            snapshot: photoSnapshot,
            shiftName: resp.shiftName,
          })
          void queryClient.invalidateQueries({ queryKey: ['kiosk'] })
          void queryClient.invalidateQueries({ queryKey: ['attendance'] })
        } catch (err: any) {
          playBeep('error')
          setErrorMessage(err.message || `Auto-Punch failed for badge ${cleanCode}`)
        }
      }
    },
    [autoPunchEnabled, captureSnapshot, kioskDeviceId, kioskLocation, queryClient],
  )

  // 1. Continuous Camera Viewfinder QR / Barcode Detector Loop
  useEffect(() => {
    if (!cameraActive || !videoRef.current) return
    let isCancelled = false

    const detectLoop = async () => {
      if (isCancelled || !videoRef.current) return
      if ('BarcodeDetector' in window) {
        try {
          const detector = new (window as any).BarcodeDetector({
            formats: ['qr_code', 'code_128', 'code_39', 'ean_13', 'data_matrix'],
          })
          const barcodes = await detector.detect(videoRef.current)
          if (barcodes && barcodes.length > 0) {
            const raw = barcodes[0].rawValue
            const parsed = parseBadgePayload(raw)
            if (parsed) {
              void handleBadgeScanned(parsed.employeeCode, parsed.pin, 'CAMERA QR')
            }
          }
        } catch {
          // Frame not ready or detector busy
        }
      }
    }

    const interval = setInterval(detectLoop, 350)
    return () => {
      isCancelled = true
      clearInterval(interval)
    }
  }, [cameraActive, handleBadgeScanned])

  // 2. Web NFC Contactless Reader (Android Chrome / Kiosk Tablets)
  useEffect(() => {
    if ('NDEFReader' in window) {
      setNfcSupported(true)
      try {
        const ndef = new (window as any).NDEFReader()
        ndef
          .scan()
          .then(() => {
            setNfcReading(true)
            ndef.onreading = (event: any) => {
              let textPayload = ''
              if (event.message?.records) {
                for (const record of event.message.records) {
                  if (record.recordType === 'text') {
                    const decoder = new TextDecoder(record.encoding || 'utf-8')
                    textPayload = decoder.decode(record.data)
                  }
                }
              }
              const parsed = parseBadgePayload(textPayload || event.serialNumber)
              if (parsed) {
                void handleBadgeScanned(parsed.employeeCode, parsed.pin, 'NFC TAP')
              }
            }
          })
          .catch(() => {
            setNfcReading(false)
          })
      } catch {
        // NDEF not supported in current environment
      }
    }
  }, [handleBadgeScanned])

  // 3. Hardware USB Barcode / QR Wedge Scanner (Fast Keystroke Burst Listener)
  useEffect(() => {
    let keyBuffer = ''
    let lastKeyTime = Date.now()

    const onKeyDown = (e: KeyboardEvent) => {
      // Ignore if user is currently typing in an input element
      const activeEl = document.activeElement
      if (activeEl && (activeEl.tagName === 'INPUT' || activeEl.tagName === 'TEXTAREA')) {
        return
      }

      const now = Date.now()
      const delta = now - lastKeyTime
      lastKeyTime = now

      if (e.key === 'Enter') {
        if (keyBuffer.length >= 3) {
          e.preventDefault()
          const parsed = parseBadgePayload(keyBuffer)
          if (parsed) {
            void handleBadgeScanned(parsed.employeeCode, parsed.pin, 'USB WEDGE')
          }
        }
        keyBuffer = ''
        return
      }

      if (e.key.length === 1) {
        if (delta > 120) {
          // Slow typing, reset buffer
          keyBuffer = e.key
        } else {
          // Rapid hardware wedge stream
          keyBuffer += e.key
        }
      }
    }

    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [handleBadgeScanned])

  // Preset quick workers for fast kiosk testing
  const quickWorkers = [
    { code: 'E004', name: 'Kasun Fernando', dept: 'Engineering', shift: '08:30 - 17:30' },
    { code: 'E001', name: 'Nimali Wickramasinghe', dept: 'Leadership', shift: '08:30 - 17:30' },
    { code: 'E012', name: 'Sanduni Weerasinghe', dept: 'Engineering', shift: '08:30 - 17:30' },
    { code: 'E007', name: 'Anusha Sivakumar', dept: 'Finance', shift: '08:30 - 17:30' },
    { code: 'E011', name: 'Nadeesha Ekanayake', dept: 'Operations', shift: '08:30 - 17:30' },
  ]

  return (
    <div
      className="kiosk-container"
      style={{
        minHeight: '100vh',
        background: 'linear-gradient(135deg, #090d16 0%, #0f172a 100%)',
        color: '#f8fafc',
        fontFamily: 'Inter, system-ui, sans-serif',
        display: 'flex',
        flexDirection: 'column',
        userSelect: 'none',
        padding: '1.25rem',
        boxSizing: 'border-box',
      }}
    >
      {/* --------------------------------------------------------------------- */}
      {/* Kiosk Header Bar                                                      */}
      {/* --------------------------------------------------------------------- */}
      <header
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          padding: '0.875rem 1.5rem',
          background: 'rgba(30, 41, 59, 0.7)',
          backdropFilter: 'blur(12px)',
          borderRadius: '12px',
          border: '1px solid rgba(255, 255, 255, 0.1)',
          marginBottom: '1.25rem',
          flexWrap: 'wrap',
          gap: '1rem',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
          <div
            style={{
              width: '40px',
              height: '40px',
              borderRadius: '10px',
              background: 'linear-gradient(135deg, #3b82f6 0%, #1d4ed8 100%)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontWeight: 800,
              fontSize: '1.25rem',
              color: '#ffffff',
              boxShadow: '0 4px 12px rgba(59, 130, 246, 0.4)',
            }}
          >
            HR
          </div>
          <div>
            <div style={{ fontWeight: 800, fontSize: '1.125rem', letterSpacing: '-0.01em' }}>
              ATTENDANCE KIOSK STATION
            </div>
            <div style={{ fontSize: '0.8125rem', color: '#94a3b8', display: 'flex', alignItems: 'center', gap: '0.5rem', flexWrap: 'wrap' }}>
              <span
                style={{
                  width: '8px',
                  height: '8px',
                  borderRadius: '50%',
                  background: '#22c55e',
                  boxShadow: '0 0 8px #22c55e',
                  display: 'inline-block',
                }}
              />
              <span>{kioskDeviceId}</span> · <span>{kioskLocation}</span>
              <span
                style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: '4px',
                  background: 'rgba(56, 189, 248, 0.12)',
                  border: '1px solid rgba(56, 189, 248, 0.3)',
                  padding: '2px 8px',
                  borderRadius: '999px',
                  fontSize: '0.6875rem',
                  color: '#38bdf8',
                  fontWeight: 600,
                }}
              >
                <span>🛜</span>
                <span>{nfcReading ? 'NFC SENSOR READY' : 'NFC CONTACTLESS'}</span>
              </span>
            </div>
          </div>
        </div>

        {/* Live Clock Display */}
        <div style={{ textAlign: 'center' }}>
          <div
            style={{
              fontSize: '1.875rem',
              fontWeight: 800,
              fontFamily: 'monospace',
              letterSpacing: '0.05em',
              color: '#38bdf8',
              textShadow: '0 0 16px rgba(56, 189, 248, 0.3)',
            }}
          >
            {formattedTime}
          </div>
          <div style={{ fontSize: '0.75rem', color: '#94a3b8', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
            {formattedDate}
          </div>
        </div>

        {/* Kiosk Controls */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.625rem', flexWrap: 'wrap' }}>
          {/* 1-Sec Auto-Punch Toggle */}
          <button
            type="button"
            onClick={() => {
              playBeep('tap')
              setAutoPunchEnabled((prev) => !prev)
            }}
            style={{
              padding: '0.5rem 0.875rem',
              background: autoPunchEnabled
                ? 'linear-gradient(135deg, rgba(16, 185, 129, 0.25) 0%, rgba(5, 150, 105, 0.35) 100%)'
                : 'rgba(255, 255, 255, 0.08)',
              border: `1px solid ${autoPunchEnabled ? '#10b981' : 'rgba(255, 255, 255, 0.15)'}`,
              borderRadius: '8px',
              color: autoPunchEnabled ? '#34d399' : '#94a3b8',
              fontSize: '0.8125rem',
              fontWeight: 700,
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              gap: '0.4rem',
            }}
            title="Automatically executes clock in/out immediately when badge is detected"
          >
            <span style={{ fontSize: '0.9rem' }}>⚡</span>
            <span>{autoPunchEnabled ? 'Auto-Punch: ON (1s)' : 'Auto-Punch: OFF'}</span>
          </button>

          {/* ID Badges & Print Modal Button */}
          <button
            type="button"
            onClick={() => {
              playBeep('tap')
              setBadgeModalCode(employeeCode || 'E004')
              setIsBadgeModalOpen(true)
            }}
            style={{
              padding: '0.5rem 0.875rem',
              background: 'linear-gradient(135deg, #2563eb 0%, #1d4ed8 100%)',
              border: 'none',
              borderRadius: '8px',
              color: '#ffffff',
              fontSize: '0.8125rem',
              fontWeight: 700,
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              gap: '0.4rem',
              boxShadow: '0 2px 8px rgba(37, 99, 235, 0.35)',
            }}
          >
            <span>🪪</span>
            <span>ID Badges & Print</span>
          </button>

          <button
            type="button"
            onClick={toggleFullscreen}
            style={{
              padding: '0.5rem 0.875rem',
              background: 'rgba(255, 255, 255, 0.08)',
              border: '1px solid rgba(255, 255, 255, 0.15)',
              borderRadius: '8px',
              color: '#f8fafc',
              fontSize: '0.8125rem',
              fontWeight: 600,
              cursor: 'pointer',
            }}
          >
            {isFullscreen ? 'Exit Fullscreen' : '⛶ Fullscreen'}
          </button>
          <Link
            to="/attendance"
            style={{
              padding: '0.5rem 0.875rem',
              background: 'rgba(255, 255, 255, 0.05)',
              border: '1px solid rgba(255, 255, 255, 0.1)',
              borderRadius: '8px',
              color: '#94a3b8',
              fontSize: '0.8125rem',
              textDecoration: 'none',
              fontWeight: 500,
            }}
          >
            Console Exit →
          </Link>
        </div>
      </header>

      {/* --------------------------------------------------------------------- */}
      {/* Main Kiosk Content Grid                                               */}
      {/* --------------------------------------------------------------------- */}
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'minmax(340px, 460px) 1fr',
          gap: '1.25rem',
          flex: 1,
        }}
      >
        {/* =================================================================== */}
        {/* Left Column: Touch Keypad & Badge Entry                             */}
        {/* =================================================================== */}
        <div
          style={{
            background: 'rgba(15, 23, 42, 0.8)',
            backdropFilter: 'blur(12px)',
            borderRadius: '16px',
            border: '1px solid rgba(255, 255, 255, 0.1)',
            padding: '1.25rem',
            display: 'flex',
            flexDirection: 'column',
            gap: '1rem',
            boxShadow: '0 8px 32px rgba(0, 0, 0, 0.3)',
          }}
        >
          {/* Mode Switcher */}
          <div
            style={{
              display: 'flex',
              background: 'rgba(0, 0, 0, 0.3)',
              borderRadius: '8px',
              padding: '4px',
            }}
          >
            <button
              type="button"
              onClick={() => setInputMode('KEYPAD')}
              style={{
                flex: 1,
                padding: '0.5rem',
                border: 'none',
                borderRadius: '6px',
                background: inputMode === 'KEYPAD' ? '#2563eb' : 'transparent',
                color: '#ffffff',
                fontWeight: 600,
                fontSize: '0.8125rem',
                cursor: 'pointer',
                transition: 'all 0.15s ease',
              }}
            >
              ⌨️ Numeric Keypad & PIN
            </button>
            <button
              type="button"
              onClick={() => setInputMode('DIRECTORY')}
              style={{
                flex: 1,
                padding: '0.5rem',
                border: 'none',
                borderRadius: '6px',
                background: inputMode === 'DIRECTORY' ? '#2563eb' : 'transparent',
                color: '#ffffff',
                fontWeight: 600,
                fontSize: '0.8125rem',
                cursor: 'pointer',
                transition: 'all 0.15s ease',
              }}
            >
              👥 Worker Directory
            </button>
          </div>

          {inputMode === 'KEYPAD' ? (
            <>
              {/* Target Selectors: Employee Code vs PIN */}
              <div style={{ display: 'grid', gridTemplateColumns: '1.2fr 1fr', gap: '0.75rem' }}>
                <div
                  onClick={() => {
                    playBeep('tap')
                    setActiveInputTarget('CODE')
                  }}
                  style={{
                    padding: '0.75rem',
                    background:
                      activeInputTarget === 'CODE' ? 'rgba(37, 99, 235, 0.2)' : 'rgba(255, 255, 255, 0.04)',
                    border: `2px solid ${activeInputTarget === 'CODE' ? '#3b82f6' : 'rgba(255, 255, 255, 0.1)'}`,
                    borderRadius: '10px',
                    cursor: 'pointer',
                  }}
                >
                  <div style={{ fontSize: '0.6875rem', color: '#94a3b8', textTransform: 'uppercase' }}>
                    1. Employee Code / Badge
                  </div>
                  <div style={{ fontSize: '1.25rem', fontWeight: 800, color: '#f8fafc', marginTop: '2px' }}>
                    {employeeCode || <span style={{ color: '#475569' }}>Type Code</span>}
                  </div>
                </div>

                <div
                  onClick={() => {
                    playBeep('tap')
                    setActiveInputTarget('PIN')
                  }}
                  style={{
                    padding: '0.75rem',
                    background:
                      activeInputTarget === 'PIN' ? 'rgba(37, 99, 235, 0.2)' : 'rgba(255, 255, 255, 0.04)',
                    border: `2px solid ${activeInputTarget === 'PIN' ? '#3b82f6' : 'rgba(255, 255, 255, 0.1)'}`,
                    borderRadius: '10px',
                    cursor: 'pointer',
                  }}
                >
                  <div style={{ fontSize: '0.6875rem', color: '#94a3b8', textTransform: 'uppercase' }}>
                    2. Security PIN
                  </div>
                  <div style={{ fontSize: '1.25rem', fontWeight: 800, color: '#f8fafc', marginTop: '2px', letterSpacing: '0.2em' }}>
                    {pin ? '••••'.slice(0, pin.length) : <span style={{ color: '#475569', letterSpacing: 'normal' }}>4 Digits</span>}
                  </div>
                </div>
              </div>

              {/* Touchscreen Numeric Keypad */}
              <div
                style={{
                  display: 'grid',
                  gridTemplateColumns: 'repeat(3, 1fr)',
                  gap: '0.625rem',
                  flex: 1,
                }}
              >
                {['1', '2', '3', '4', '5', '6', '7', '8', '9'].map((digit) => (
                  <button
                    key={digit}
                    type="button"
                    onClick={() => handleKeypadPress(digit)}
                    style={{
                      padding: '1.125rem 0',
                      fontSize: '1.5rem',
                      fontWeight: 700,
                      color: '#ffffff',
                      background: 'rgba(255, 255, 255, 0.08)',
                      border: '1px solid rgba(255, 255, 255, 0.1)',
                      borderRadius: '12px',
                      cursor: 'pointer',
                      boxShadow: '0 2px 8px rgba(0, 0, 0, 0.2)',
                    }}
                  >
                    {digit}
                  </button>
                ))}
                <button
                  type="button"
                  onClick={handleKeypadClear}
                  style={{
                    padding: '1.125rem 0',
                    fontSize: '0.875rem',
                    fontWeight: 700,
                    color: '#f87171',
                    background: 'rgba(239, 68, 68, 0.1)',
                    border: '1px solid rgba(239, 68, 68, 0.3)',
                    borderRadius: '12px',
                    cursor: 'pointer',
                  }}
                >
                  CLEAR
                </button>
                <button
                  type="button"
                  onClick={() => handleKeypadPress('0')}
                  style={{
                    padding: '1.125rem 0',
                    fontSize: '1.5rem',
                    fontWeight: 700,
                    color: '#ffffff',
                    background: 'rgba(255, 255, 255, 0.08)',
                    border: '1px solid rgba(255, 255, 255, 0.1)',
                    borderRadius: '12px',
                    cursor: 'pointer',
                  }}
                >
                  0
                </button>
                <button
                  type="button"
                  onClick={handleKeypadBackspace}
                  style={{
                    padding: '1.125rem 0',
                    fontSize: '1.25rem',
                    fontWeight: 700,
                    color: '#fbbf24',
                    background: 'rgba(245, 158, 11, 0.1)',
                    border: '1px solid rgba(245, 158, 11, 0.3)',
                    borderRadius: '12px',
                    cursor: 'pointer',
                  }}
                >
                  ⌫
                </button>
              </div>

              {/* Quick Select Shortcut Badge Buttons */}
              <div>
                <div style={{ fontSize: '0.6875rem', color: '#94a3b8', textTransform: 'uppercase', marginBottom: '0.35rem' }}>
                  ⚡ Quick Demo Shift Workers:
                </div>
                <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap' }}>
                  {quickWorkers.map((w) => (
                    <button
                      key={w.code}
                      type="button"
                      onClick={() => {
                        playBeep('tap')
                        setEmployeeCode(w.code)
                        setPin('1234')
                      }}
                      style={{
                        padding: '0.35rem 0.65rem',
                        fontSize: '0.75rem',
                        background:
                          employeeCode === w.code ? 'rgba(56, 189, 248, 0.2)' : 'rgba(255, 255, 255, 0.05)',
                        border: `1px solid ${employeeCode === w.code ? '#38bdf8' : 'rgba(255, 255, 255, 0.1)'}`,
                        borderRadius: '6px',
                        color: employeeCode === w.code ? '#38bdf8' : '#cbd5e1',
                        cursor: 'pointer',
                      }}
                    >
                      <strong>{w.code}</strong> {w.name.split(' ')[0]}
                    </button>
                  ))}
                </div>
              </div>
            </>
          ) : (
            /* Directory Selection Mode */
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem', height: '100%' }}>
              <input
                type="search"
                placeholder="Search worker by name or department..."
                value={directorySearch}
                onChange={(e) => setDirectorySearch(e.target.value)}
                style={{
                  padding: '0.65rem 1rem',
                  background: 'rgba(0, 0, 0, 0.3)',
                  border: '1px solid rgba(255, 255, 255, 0.15)',
                  borderRadius: '8px',
                  color: '#ffffff',
                  fontSize: '0.875rem',
                }}
              />
              <div
                style={{
                  maxHeight: '440px',
                  overflowY: 'auto',
                  display: 'flex',
                  flexDirection: 'column',
                  gap: '0.5rem',
                }}
              >
                {(directoryQuery.data?.items ?? [])
                  .filter(
                    (e: DirectoryEntry) =>
                      !directorySearch ||
                      e.displayName.toLowerCase().includes(directorySearch.toLowerCase()) ||
                      (e.department && e.department.toLowerCase().includes(directorySearch.toLowerCase())) ||
                      e.employeeCode.toLowerCase().includes(directorySearch.toLowerCase()),
                  )
                  .map((e: DirectoryEntry) => (
                    <div
                      key={e.id}
                      onClick={() => {
                        playBeep('tap')
                        setEmployeeCode(e.employeeCode)
                        setPin('1234')
                        setInputMode('KEYPAD')
                      }}
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        padding: '0.625rem 0.875rem',
                        background:
                          employeeCode === e.employeeCode
                            ? 'rgba(56, 189, 248, 0.15)'
                            : 'rgba(255, 255, 255, 0.03)',
                        border: `1px solid ${employeeCode === e.employeeCode ? '#38bdf8' : 'rgba(255, 255, 255, 0.06)'}`,
                        borderRadius: '8px',
                        cursor: 'pointer',
                      }}
                    >
                      <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
                        <div
                          style={{
                            width: '32px',
                            height: '32px',
                            borderRadius: '50%',
                            background: '#334155',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            fontWeight: 700,
                            fontSize: '0.75rem',
                            color: '#38bdf8',
                          }}
                        >
                          {e.displayName.charAt(0)}
                        </div>
                        <div>
                          <div style={{ fontWeight: 600, fontSize: '0.875rem' }}>{e.displayName}</div>
                          <div style={{ fontSize: '0.75rem', color: '#94a3b8' }}>
                            {e.employeeCode} · {e.department ?? 'Staff'}
                          </div>
                        </div>
                      </div>
                      <span style={{ fontSize: '0.75rem', color: '#38bdf8', fontWeight: 600 }}>Select →</span>
                    </div>
                  ))}
              </div>
            </div>
          )}
        </div>

        {/* =================================================================== */}
        {/* Right Column: Shift Awareness, Camera Viewfinder & Clock Actions    */}
        {/* =================================================================== */}
        <div
          style={{
            background: 'rgba(15, 23, 42, 0.8)',
            backdropFilter: 'blur(12px)',
            borderRadius: '16px',
            border: '1px solid rgba(255, 255, 255, 0.1)',
            padding: '1.5rem',
            display: 'flex',
            flexDirection: 'column',
            justifyContent: 'space-between',
            gap: '1.25rem',
            boxShadow: '0 8px 32px rgba(0, 0, 0, 0.3)',
          }}
        >
          {/* Top Section: Matched Employee & Shift Information Card */}
          {employeeStatusQuery.data ? (
            <div
              style={{
                background: 'rgba(30, 41, 59, 0.6)',
                border: '1px solid rgba(255, 255, 255, 0.12)',
                borderRadius: '12px',
                padding: '1rem 1.25rem',
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                flexWrap: 'wrap',
                gap: '1rem',
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
                <div
                  style={{
                    width: '56px',
                    height: '56px',
                    borderRadius: '50%',
                    background: 'linear-gradient(135deg, #3b82f6 0%, #1d4ed8 100%)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontSize: '1.35rem',
                    fontWeight: 800,
                    color: '#ffffff',
                    boxShadow: '0 4px 12px rgba(0, 0, 0, 0.3)',
                  }}
                >
                  {employeeStatusQuery.data.employee.avatarInitials}
                </div>
                <div>
                  <div style={{ fontSize: '1.25rem', fontWeight: 800, color: '#f8fafc' }}>
                    {employeeStatusQuery.data.employee.name}
                  </div>
                  <div style={{ fontSize: '0.8125rem', color: '#94a3b8' }}>
                    Code: <strong>{employeeStatusQuery.data.employee.code}</strong> · {employeeStatusQuery.data.employee.department} · {employeeStatusQuery.data.employee.designation}
                  </div>
                </div>
              </div>

              {/* Assigned Shift Badge */}
              <div style={{ textAlign: 'right' }}>
                <div style={{ fontSize: '0.6875rem', color: '#94a3b8', textTransform: 'uppercase' }}>
                  Scheduled Shift Today:
                </div>
                <div
                  style={{
                    display: 'inline-block',
                    background: 'rgba(37, 99, 235, 0.2)',
                    border: '1px solid #3b82f6',
                    borderRadius: '6px',
                    padding: '3px 10px',
                    fontSize: '0.875rem',
                    fontWeight: 700,
                    color: '#60a5fa',
                    marginTop: '2px',
                  }}
                >
                  {employeeStatusQuery.data.shift?.name || 'General Day (08:30 - 17:30)'}
                </div>
                <div style={{ fontSize: '0.75rem', color: employeeStatusQuery.data.currentStatus.isClockedIn ? '#4ade80' : '#fbbf24', fontWeight: 600, marginTop: '2px' }}>
                  ● {employeeStatusQuery.data.currentStatus.isClockedIn ? 'Currently Clocked In' : 'Not Clocked In'}
                </div>
              </div>
            </div>
          ) : (
            /* Standby Prompt */
            <div
              style={{
                background: 'rgba(30, 41, 59, 0.3)',
                border: '1px dashed rgba(255, 255, 255, 0.15)',
                borderRadius: '12px',
                padding: '1.25rem',
                textAlign: 'center',
                color: '#94a3b8',
              }}
            >
              Enter worker code on keypad or tap from list to identify shift worker.
            </div>
          )}

          {/* Error Banner */}
          {errorMessage && (
            <div
              style={{
                padding: '0.75rem 1rem',
                background: 'rgba(239, 68, 68, 0.15)',
                border: '1px solid #ef4444',
                borderRadius: '8px',
                color: '#fca5a5',
                fontSize: '0.875rem',
                fontWeight: 600,
                display: 'flex',
                alignItems: 'center',
                gap: '0.5rem',
              }}
            >
              <span>⚠️</span>
              <span>{errorMessage}</span>
            </div>
          )}

          {/* Center Section: Webcam Viewfinder Box & QR / Barcode Scanner */}
          <div
            style={{
              position: 'relative',
              width: '100%',
              height: '250px',
              background: '#020617',
              borderRadius: '14px',
              overflow: 'hidden',
              border: '2px solid rgba(56, 189, 248, 0.3)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              boxShadow: '0 0 20px rgba(56, 189, 248, 0.1)',
            }}
          >
            <video
              ref={videoRef}
              style={{
                width: '100%',
                height: '100%',
                objectFit: 'cover',
                transform: 'scaleX(-1)', // mirror for natural kiosk feedback
                display: cameraActive && !cameraError ? 'block' : 'none',
              }}
              autoPlay
              playsInline
              muted
            />

            {(!cameraActive || cameraError) && (
              <div
                style={{
                  display: 'flex',
                  flexDirection: 'column',
                  alignItems: 'center',
                  gap: '0.5rem',
                  color: '#64748b',
                }}
              >
                <div style={{ fontSize: '2.5rem' }}>📷</div>
                <div style={{ fontSize: '0.875rem', fontWeight: 600, color: '#94a3b8' }}>
                  Camera Viewfinder Ready
                </div>
                <div style={{ fontSize: '0.75rem', color: '#64748b' }}>
                  Hold QR Badge 15cm from lens or tap NFC badge
                </div>
              </div>
            )}

            {/* Viewfinder Top HUD Bar */}
            <div
              style={{
                position: 'absolute',
                top: '10px',
                left: '12px',
                right: '12px',
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                pointerEvents: 'none',
                zIndex: 5,
              }}
            >
              <div
                style={{
                  background: 'rgba(15, 23, 42, 0.85)',
                  backdropFilter: 'blur(6px)',
                  padding: '3px 10px',
                  borderRadius: '6px',
                  fontSize: '0.7rem',
                  color: '#38bdf8',
                  fontWeight: 700,
                  display: 'flex',
                  alignItems: 'center',
                  gap: '6px',
                  border: '1px solid rgba(56, 189, 248, 0.3)',
                }}
              >
                <span
                  style={{
                    width: '6px',
                    height: '6px',
                    borderRadius: '50%',
                    background: '#38bdf8',
                    boxShadow: '0 0 8px #38bdf8',
                  }}
                />
                <span>QR SCANNER & NFC TOUCHLESS ZONE</span>
              </div>

              {autoPunchEnabled && (
                <div
                  style={{
                    background: 'rgba(16, 185, 129, 0.25)',
                    border: '1px solid #10b981',
                    padding: '2px 8px',
                    borderRadius: '6px',
                    fontSize: '0.6875rem',
                    color: '#34d399',
                    fontWeight: 700,
                  }}
                >
                  ⚡ AUTO-PUNCH ARMED
                </div>
              )}
            </div>

            {/* Target Reticle Frame & Corner Brackets */}
            <div
              style={{
                position: 'absolute',
                width: '180px',
                height: '180px',
                pointerEvents: 'none',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                zIndex: 4,
              }}
            >
              {/* Corner brackets */}
              <div
                style={{
                  position: 'absolute',
                  top: 0,
                  left: 0,
                  width: '24px',
                  height: '24px',
                  borderTop: '3px solid #38bdf8',
                  borderLeft: '3px solid #38bdf8',
                }}
              />
              <div
                style={{
                  position: 'absolute',
                  top: 0,
                  right: 0,
                  width: '24px',
                  height: '24px',
                  borderTop: '3px solid #38bdf8',
                  borderRight: '3px solid #38bdf8',
                }}
              />
              <div
                style={{
                  position: 'absolute',
                  bottom: 0,
                  left: 0,
                  width: '24px',
                  height: '24px',
                  borderBottom: '3px solid #38bdf8',
                  borderLeft: '3px solid #38bdf8',
                }}
              />
              <div
                style={{
                  position: 'absolute',
                  bottom: 0,
                  right: 0,
                  width: '24px',
                  height: '24px',
                  borderBottom: '3px solid #38bdf8',
                  borderRight: '3px solid #38bdf8',
                }}
              />

              {/* Center subtle framing circle */}
              <div
                style={{
                  width: '130px',
                  height: '130px',
                  border: '1px dashed rgba(56, 189, 248, 0.4)',
                  borderRadius: '12px',
                }}
              />

              {/* Sweeping Cyan Laser Scanning Line */}
              <div
                style={{
                  position: 'absolute',
                  left: '4px',
                  right: '4px',
                  height: '2px',
                  background: 'linear-gradient(90deg, transparent, #38bdf8 25%, #ffffff 50%, #38bdf8 75%, transparent)',
                  boxShadow: '0 0 10px #38bdf8, 0 0 20px #0284c7',
                  animation: 'kioskLaserSweep 2.2s ease-in-out infinite',
                }}
              />
            </div>

            {/* Last Scan Live Feedback Pill */}
            {lastScanNotice && (
              <div
                style={{
                  position: 'absolute',
                  bottom: '36px',
                  background: 'rgba(16, 185, 129, 0.92)',
                  backdropFilter: 'blur(8px)',
                  border: '1px solid #ffffff',
                  borderRadius: '999px',
                  padding: '4px 14px',
                  fontSize: '0.75rem',
                  color: '#ffffff',
                  fontWeight: 700,
                  boxShadow: '0 4px 14px rgba(16, 185, 129, 0.5)',
                  zIndex: 6,
                  display: 'flex',
                  alignItems: 'center',
                  gap: '6px',
                }}
              >
                <span>⚡</span>
                <span>{lastScanNotice.source}: Badge {lastScanNotice.code} Recognized</span>
              </div>
            )}

            {/* Bottom Status Pills */}
            <div
              style={{
                position: 'absolute',
                bottom: '10px',
                left: '12px',
                right: '12px',
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                zIndex: 5,
              }}
            >
              <div
                style={{
                  background: 'rgba(0, 0, 0, 0.65)',
                  backdropFilter: 'blur(6px)',
                  padding: '3px 8px',
                  borderRadius: '4px',
                  fontSize: '0.6875rem',
                  color: '#38bdf8',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '4px',
                  fontWeight: 600,
                }}
              >
                <span
                  style={{
                    width: '6px',
                    height: '6px',
                    borderRadius: '50%',
                    background: cameraActive ? '#22c55e' : '#eab308',
                  }}
                />
                <span>{cameraActive ? 'CAMERA LIVE' : 'SIMULATED CAMERA READY'}</span>
              </div>

              <div
                style={{
                  background: 'rgba(0, 0, 0, 0.65)',
                  backdropFilter: 'blur(6px)',
                  padding: '3px 8px',
                  borderRadius: '4px',
                  fontSize: '0.6875rem',
                  color: '#94a3b8',
                  fontWeight: 500,
                }}
              >
                USB Wedge & NFC Active
              </div>
            </div>
          </div>

          {/* Bottom Section: Primary Punch Action Buttons */}
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.875rem' }}>
            <button
              type="button"
              disabled={punchMutation.isPending || !employeeCode}
              onClick={() => punchMutation.mutate('IN')}
              style={{
                padding: '1.25rem',
                background: 'linear-gradient(135deg, #16a34a 0%, #15803d 100%)',
                border: 'none',
                borderRadius: '12px',
                color: '#ffffff',
                fontSize: '1.125rem',
                fontWeight: 800,
                letterSpacing: '0.02em',
                cursor: 'pointer',
                boxShadow: '0 4px 16px rgba(22, 163, 74, 0.35)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                gap: '0.75rem',
                transition: 'transform 0.1s ease',
              }}
            >
              <span style={{ fontSize: '1.5rem' }}>🟢</span>
              <span>CLOCK IN (START)</span>
            </button>

            <button
              type="button"
              disabled={punchMutation.isPending || !employeeCode}
              onClick={() => punchMutation.mutate('OUT')}
              style={{
                padding: '1.25rem',
                background: 'linear-gradient(135deg, #dc2626 0%, #b91c1c 100%)',
                border: 'none',
                borderRadius: '12px',
                color: '#ffffff',
                fontSize: '1.125rem',
                fontWeight: 800,
                letterSpacing: '0.02em',
                cursor: 'pointer',
                boxShadow: '0 4px 16px rgba(220, 38, 38, 0.35)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                gap: '0.75rem',
                transition: 'transform 0.1s ease',
              }}
            >
              <span style={{ fontSize: '1.5rem' }}>🔴</span>
              <span>CLOCK OUT (FINISH)</span>
            </button>

            <button
              type="button"
              disabled={punchMutation.isPending || !employeeCode}
              onClick={() => punchMutation.mutate('BREAK_IN')}
              style={{
                padding: '0.875rem',
                background: 'rgba(245, 158, 11, 0.15)',
                border: '1px solid #f59e0b',
                borderRadius: '10px',
                color: '#fbbf24',
                fontSize: '0.9375rem',
                fontWeight: 700,
                cursor: 'pointer',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                gap: '0.5rem',
              }}
            >
              <span>☕</span>
              <span>START BREAK</span>
            </button>

            <button
              type="button"
              disabled={punchMutation.isPending || !employeeCode}
              onClick={() => punchMutation.mutate('BREAK_OUT')}
              style={{
                padding: '0.875rem',
                background: 'rgba(59, 130, 246, 0.15)',
                border: '1px solid #3b82f6',
                borderRadius: '10px',
                color: '#60a5fa',
                fontSize: '0.9375rem',
                fontWeight: 700,
                cursor: 'pointer',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                gap: '0.5rem',
              }}
            >
              <span>🔙</span>
              <span>RETURN FROM BREAK</span>
            </button>
          </div>
        </div>
      </div>

      {/* --------------------------------------------------------------------- */}
      {/* Touchless Badge Scanner Test Station & Quick Badges Tray              */}
      {/* --------------------------------------------------------------------- */}
      <section
        style={{
          marginTop: '1.25rem',
          background: 'rgba(15, 23, 42, 0.75)',
          backdropFilter: 'blur(12px)',
          borderRadius: '14px',
          border: '1px solid rgba(56, 189, 248, 0.25)',
          padding: '1rem 1.25rem',
          display: 'flex',
          flexDirection: 'column',
          gap: '0.875rem',
          boxShadow: '0 8px 24px rgba(0, 0, 0, 0.3)',
        }}
      >
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            flexWrap: 'wrap',
            gap: '0.75rem',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.625rem' }}>
            <span style={{ fontSize: '1.25rem' }}>🚀</span>
            <div>
              <div style={{ fontWeight: 800, fontSize: '0.9375rem', color: '#f8fafc', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                <span>TOUCHLESS BADGE SCANNER SIMULATOR & TEST TRAY</span>
                <span
                  style={{
                    background: 'rgba(56, 189, 248, 0.15)',
                    border: '1px solid #38bdf8',
                    color: '#38bdf8',
                    padding: '2px 6px',
                    borderRadius: '4px',
                    fontSize: '0.6875rem',
                    fontWeight: 700,
                  }}
                >
                  ZERO HARDWARE REQUIRED
                </span>
              </div>
              <div style={{ fontSize: '0.75rem', color: '#94a3b8' }}>
                Tap any worker badge to test 1-second auto-clocking, simulate NFC tap, hardware barcode wedge, or preview full CR80 ID card.
              </div>
            </div>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', fontSize: '0.75rem', color: '#94a3b8' }}>
            <span style={{ color: '#38bdf8', fontWeight: 600 }}>Active Engines:</span>
            <span style={{ background: 'rgba(255, 255, 255, 0.05)', padding: '2px 8px', borderRadius: '4px', border: '1px solid rgba(255, 255, 255, 0.1)' }}>
              📷 QR Detector
            </span>
            <span style={{ background: 'rgba(255, 255, 255, 0.05)', padding: '2px 8px', borderRadius: '4px', border: '1px solid rgba(255, 255, 255, 0.1)' }}>
              🛜 Web NFC
            </span>
            <span style={{ background: 'rgba(255, 255, 255, 0.05)', padding: '2px 8px', borderRadius: '4px', border: '1px solid rgba(255, 255, 255, 0.1)' }}>
              🏷️ USB Wedge
            </span>
          </div>
        </div>

        {/* Quick Employee Badges Grid */}
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))',
            gap: '0.75rem',
          }}
        >
          {quickWorkers.map((worker) => (
            <div
              key={worker.code}
              style={{
                background:
                  employeeCode === worker.code
                    ? 'rgba(56, 189, 248, 0.12)'
                    : 'rgba(30, 41, 59, 0.5)',
                border: `1px solid ${
                  employeeCode === worker.code ? '#38bdf8' : 'rgba(255, 255, 255, 0.08)'
                }`,
                borderRadius: '10px',
                padding: '0.75rem 0.875rem',
                display: 'flex',
                flexDirection: 'column',
                gap: '0.5rem',
                transition: 'all 0.15s ease',
              }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.625rem' }}>
                  <div
                    style={{
                      width: '32px',
                      height: '32px',
                      borderRadius: '8px',
                      background: 'linear-gradient(135deg, #1e293b 0%, #0f172a 100%)',
                      border: '1px solid #38bdf8',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      fontWeight: 800,
                      fontSize: '0.8125rem',
                      color: '#38bdf8',
                    }}
                  >
                    {worker.name.split(' ').map((n) => n[0]).join('')}
                  </div>
                  <div>
                    <div style={{ fontWeight: 700, fontSize: '0.875rem', color: '#f8fafc' }}>
                      {worker.name}
                    </div>
                    <div style={{ fontSize: '0.75rem', color: '#94a3b8' }}>
                      {worker.code} · {worker.dept}
                    </div>
                  </div>
                </div>

                <button
                  type="button"
                  onClick={() => {
                    setBadgeModalCode(worker.code)
                    setIsBadgeModalOpen(true)
                  }}
                  style={{
                    background: 'transparent',
                    border: 'none',
                    color: '#38bdf8',
                    cursor: 'pointer',
                    fontSize: '0.75rem',
                    fontWeight: 600,
                    padding: '2px 6px',
                  }}
                  title="View full printable CR80 badge"
                >
                  🪪 View Badge
                </button>
              </div>

              {/* Simulation Action Triggers */}
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.4rem' }}>
                <button
                  type="button"
                  onClick={() => {
                    void handleBadgeScanned(
                      worker.code,
                      '1234',
                      '📷 CAMERA QR SCAN',
                    )
                  }}
                  style={{
                    padding: '0.35rem 0.5rem',
                    background: 'rgba(56, 189, 248, 0.12)',
                    border: '1px solid rgba(56, 189, 248, 0.3)',
                    borderRadius: '6px',
                    color: '#38bdf8',
                    fontSize: '0.75rem',
                    fontWeight: 700,
                    cursor: 'pointer',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    gap: '4px',
                  }}
                  title="Simulates camera detecting QR code"
                >
                  <span>📷 Scan QR</span>
                </button>

                <button
                  type="button"
                  onClick={() => {
                    void handleBadgeScanned(
                      worker.code,
                      '1234',
                      '🛜 NFC BADGE TAP',
                    )
                  }}
                  style={{
                    padding: '0.35rem 0.5rem',
                    background: 'rgba(16, 185, 129, 0.12)',
                    border: '1px solid rgba(16, 185, 129, 0.3)',
                    borderRadius: '6px',
                    color: '#34d399',
                    fontSize: '0.75rem',
                    fontWeight: 700,
                    cursor: 'pointer',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    gap: '4px',
                  }}
                  title="Simulates NFC card tapping tablet sensor"
                >
                  <span>🛜 Tap NFC</span>
                </button>
              </div>
            </div>
          ))}
        </div>
      </section>

      {/* --------------------------------------------------------------------- */}
      {/* Bottom Live Activity Feed Ticker                                     */}
      {/* --------------------------------------------------------------------- */}
      <footer
        style={{
          marginTop: '1.25rem',
          padding: '0.75rem 1.25rem',
          background: 'rgba(15, 23, 42, 0.6)',
          backdropFilter: 'blur(8px)',
          borderRadius: '10px',
          border: '1px solid rgba(255, 255, 255, 0.08)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          flexWrap: 'wrap',
          gap: '0.75rem',
          fontSize: '0.8125rem',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
          <span style={{ color: '#38bdf8', fontWeight: 700 }}>LIVE KIOSK STREAM:</span>
          <span style={{ color: '#94a3b8' }}>Real-time attendance ingestion active</span>
        </div>

        <div style={{ display: 'flex', gap: '1.25rem', overflowX: 'auto' }}>
          {recentPunchesQuery.data?.punches.slice(0, 4).map((p: RawPunchItem) => (
            <div key={p.id} style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              <span
                style={{
                  width: '6px',
                  height: '6px',
                  borderRadius: '50%',
                  background: p.punchType === 'IN' ? '#22c55e' : '#ef4444',
                }}
              />
              <strong>{p.employeeName}</strong>
              <span style={{ color: '#94a3b8', fontSize: '0.75rem' }}>
                ({p.punchType} at{' '}
                {new Date(p.punchedAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })})
              </span>
            </div>
          ))}
        </div>
      </footer>

      {/* --------------------------------------------------------------------- */}
      {/* Fullscreen Success Confirmation Overlay with 5s Auto-Reset            */}
      {/* --------------------------------------------------------------------- */}
      {punchFeedback && (
        <div
          style={{
            position: 'fixed',
            inset: 0,
            background: 'rgba(9, 13, 22, 0.92)',
            backdropFilter: 'blur(16px)',
            zIndex: 9999,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            padding: '1.5rem',
          }}
        >
          <div
            style={{
              maxWidth: '540px',
              width: '100%',
              background: '#0f172a',
              borderRadius: '20px',
              border: '2px solid #22c55e',
              padding: '2.25rem',
              textAlign: 'center',
              boxShadow: '0 20px 60px rgba(34, 197, 94, 0.25)',
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              gap: '1.25rem',
            }}
          >
            {/* Success Check Icon */}
            <div
              style={{
                width: '80px',
                height: '80px',
                borderRadius: '50%',
                background: 'rgba(34, 197, 94, 0.15)',
                border: '3px solid #22c55e',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontSize: '2.5rem',
                color: '#22c55e',
                boxShadow: '0 0 24px rgba(34, 197, 94, 0.5)',
              }}
            >
              ✓
            </div>

            <div>
              <div
                style={{
                  fontSize: '1.75rem',
                  fontWeight: 800,
                  color: '#f8fafc',
                  letterSpacing: '-0.02em',
                }}
              >
                {punchFeedback.employeeName}
              </div>
              <div style={{ fontSize: '0.9375rem', color: '#94a3b8', marginTop: '4px' }}>
                Code: <strong>{punchFeedback.employeeCode}</strong> · {punchFeedback.department}
              </div>
            </div>

            {/* Action & Timestamp Pill */}
            <div
              style={{
                background:
                  punchFeedback.punchType === 'IN'
                    ? 'rgba(34, 197, 94, 0.2)'
                    : 'rgba(239, 68, 68, 0.2)',
                border: `1px solid ${punchFeedback.punchType === 'IN' ? '#22c55e' : '#ef4444'}`,
                borderRadius: '999px',
                padding: '6px 18px',
                fontWeight: 800,
                fontSize: '1rem',
                color: punchFeedback.punchType === 'IN' ? '#4ade80' : '#f87171',
              }}
            >
              {punchFeedback.punchType === 'IN'
                ? '✅ CLOCK IN CONFIRMED'
                : punchFeedback.punchType === 'OUT'
                  ? '🔴 CLOCK OUT CONFIRMED'
                  : punchFeedback.punchType === 'BREAK_IN'
                    ? '☕ BREAK STARTED'
                    : '🔙 RESUMED WORK'}
            </div>

            {/* Photo Snapshot Audit */}
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: '1rem',
                background: 'rgba(255, 255, 255, 0.04)',
                border: '1px solid rgba(255, 255, 255, 0.08)',
                borderRadius: '10px',
                padding: '0.75rem 1rem',
                width: '100%',
                boxSizing: 'border-box',
              }}
            >
              <img
                src={punchFeedback.snapshot}
                alt="Audit Snapshot"
                style={{
                  width: '64px',
                  height: '48px',
                  objectFit: 'cover',
                  borderRadius: '6px',
                  border: '1px solid rgba(255, 255, 255, 0.2)',
                }}
              />
              <div style={{ textAlign: 'left', fontSize: '0.8125rem' }}>
                <div style={{ color: '#e2e8f0', fontWeight: 600 }}>Webcam Audit Snapshot Logged</div>
                <div style={{ color: '#94a3b8', fontSize: '0.75rem' }}>
                  {new Date(punchFeedback.punchedAt).toLocaleTimeString([], {
                    hour: '2-digit',
                    minute: '2-digit',
                    second: '2-digit',
                  })}
                  {' · '}Shift: {punchFeedback.shiftName}
                </div>
              </div>
            </div>

            {/* Auto Reset Progress Bar & Button */}
            <div style={{ width: '100%' }}>
              <div
                style={{
                  fontSize: '0.8125rem',
                  color: '#94a3b8',
                  marginBottom: '0.5rem',
                  fontWeight: 600,
                }}
              >
                Resetting station for next worker in <strong>{countdown} seconds</strong>…
              </div>
              <div
                style={{
                  width: '100%',
                  height: '6px',
                  background: 'rgba(255, 255, 255, 0.1)',
                  borderRadius: '999px',
                  overflow: 'hidden',
                }}
              >
                <div
                  style={{
                    height: '100%',
                    width: `${(countdown / 5) * 100}%`,
                    background: '#22c55e',
                    transition: 'width 1s linear',
                  }}
                />
              </div>
            </div>

            <button
              type="button"
              onClick={() => {
                setPunchFeedback(null)
                setPin('')
              }}
              style={{
                width: '100%',
                padding: '0.875rem',
                background: '#2563eb',
                border: 'none',
                borderRadius: '10px',
                color: '#ffffff',
                fontWeight: 700,
                fontSize: '0.9375rem',
                cursor: 'pointer',
              }}
            >
              Next Worker in Line →
            </button>
          </div>
        </div>
      )}

      {/* --------------------------------------------------------------------- */}
      {/* Interactive CR80 Employee ID Badge Modal (Print & Simulate)           */}
      {/* --------------------------------------------------------------------- */}
      <EmployeeBadgeModal
        isOpen={isBadgeModalOpen}
        onClose={() => setIsBadgeModalOpen(false)}
        badges={badgesQuery.data?.badges || []}
        selectedBadgeCode={badgeModalCode}
        onSimulateScan={(scannedCode, payload) => {
          void handleBadgeScanned(scannedCode, '1234', '🪪 ID BADGE SCAN')
        }}
      />

      {/* Viewfinder Laser Sweep Keyframe Style */}
      <style>
        {`
          @keyframes kioskLaserSweep {
            0% { top: 10%; opacity: 0.5; }
            50% { top: 86%; opacity: 1; }
            100% { top: 10%; opacity: 0.5; }
          }
        `}
      </style>
    </div>
  )
}
