'use client';

import React, { useCallback, useEffect, useRef, useState } from 'react';

interface HudCornersProps {
  colorClass?: string;
  sizeClass?: string;
}

/**
 * Purely decorative 4-corner HUD brackets around important cards.
 */
export function HudCorners({
  colorClass = 'border-cyan-400/50',
  sizeClass = 'w-2.5 h-2.5',
}: HudCornersProps) {
  return (
    <div className="pointer-events-none absolute inset-0 overflow-hidden rounded-[inherit]" aria-hidden="true">
      <span className={`absolute top-1.5 left-1.5 ${sizeClass} border-t-[1.5px] border-l-[1.5px] ${colorClass} rounded-tl-[2px]`} />
      <span className={`absolute top-1.5 right-1.5 ${sizeClass} border-t-[1.5px] border-r-[1.5px] ${colorClass} rounded-tr-[2px]`} />
      <span className={`absolute bottom-1.5 left-1.5 ${sizeClass} border-b-[1.5px] border-l-[1.5px] ${colorClass} rounded-bl-[2px]`} />
      <span className={`absolute bottom-1.5 right-1.5 ${sizeClass} border-b-[1.5px] border-r-[1.5px] ${colorClass} rounded-br-[2px]`} />
    </div>
  );
}

interface AnimatedCounterProps {
  value: number;
  durationMs?: number;
  suffix?: string;
}

/**
 * Animates a numeric KPI value when entering the viewport or when the prop value changes.
 * Respects prefers-reduced-motion.
 */
export function AnimatedCounter({ value, durationMs = 700, suffix = '' }: AnimatedCounterProps) {
  const [displayValue, setDisplayValue] = useState<number>(value);
  const [inView, setInView] = useState<boolean>(false);
  const ref = useRef<HTMLSpanElement | null>(null);
  const prevValueRef = useRef<number>(0);

  useEffect(() => {
    const el = ref.current;
    if (!el || typeof IntersectionObserver === 'undefined') {
      const t = window.setTimeout(() => setInView(true), 0);
      return () => window.clearTimeout(t);
    }

    const observer = new IntersectionObserver(
      (entries) => {
        if (entries[0]?.isIntersecting) {
          setInView(true);
          observer.disconnect();
        }
      },
      { threshold: 0.15 }
    );
    observer.observe(el);
    return () => observer.disconnect();
  }, []);

  useEffect(() => {
    if (typeof window === 'undefined' || !inView) return;
    const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    let rafId = 0;
    const fromVal = prevValueRef.current;
    prevValueRef.current = value;

    if (reduced || value === fromVal) {
      rafId = requestAnimationFrame(() => {
        setDisplayValue(value);
      });
      return () => cancelAnimationFrame(rafId);
    }

    const startTime = performance.now();

    const tick = (now: number) => {
      const elapsed = now - startTime;
      const progress = Math.min(elapsed / durationMs, 1);
      const eased = 1 - Math.pow(1 - progress, 3);
      setDisplayValue(Math.round(fromVal + eased * (value - fromVal)));
      if (progress < 1) {
        rafId = requestAnimationFrame(tick);
      } else {
        setDisplayValue(value);
      }
    };

    rafId = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(rafId);
  }, [value, durationMs, inView]);

  return (
    <span ref={ref} className="font-mono tabular-nums inline-block">
      {displayValue}
      {suffix}
    </span>
  );
}

interface SpotlightCardProps {
  children: React.ReactNode;
  className?: string;
}

/**
 * Pure presentation wrapper that tracks mouse coordinates via rAF and updates
 * CSS custom properties (--mx, --my, --spot-opacity) for a subtle cursor spotlight.
 */
export function SpotlightCard({ children, className = '' }: SpotlightCardProps) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const rafRef = useRef<number>(0);

  const handleMouseMove = useCallback((e: React.MouseEvent<HTMLDivElement>) => {
    const el = containerRef.current;
    if (!el) return;
    const clientX = e.clientX;
    const clientY = e.clientY;
    if (rafRef.current) cancelAnimationFrame(rafRef.current);
    rafRef.current = requestAnimationFrame(() => {
      const rect = el.getBoundingClientRect();
      el.style.setProperty('--mx', `${Math.round(clientX - rect.left)}px`);
      el.style.setProperty('--my', `${Math.round(clientY - rect.top)}px`);
      el.style.setProperty('--spot-opacity', '1');
    });
  }, []);

  const handleMouseLeave = useCallback(() => {
    const el = containerRef.current;
    if (!el) return;
    if (rafRef.current) cancelAnimationFrame(rafRef.current);
    el.style.setProperty('--spot-opacity', '0');
  }, []);

  useEffect(() => {
    return () => {
      if (rafRef.current) cancelAnimationFrame(rafRef.current);
    };
  }, []);

  return (
    <div
      ref={containerRef}
      onMouseMove={handleMouseMove}
      onMouseLeave={handleMouseLeave}
      className={`nc-spotlight-card ${className}`}
    >
      {children}
    </div>
  );
}

interface TiltKpiCardProps {
  children: React.ReactNode;
  className?: string;
  testId?: string;
}

/**
 * KPI Card v3 wrapper: subtle 3D tilt (<= 4 deg) + moving specular highlight via rAF.
 * Automatically disables tilt when prefers-reduced-motion: reduce is active.
 */
export function TiltKpiCard({ children, className = '', testId = 'kpi-card-item' }: TiltKpiCardProps) {
  const cardRef = useRef<HTMLDivElement | null>(null);
  const rafRef = useRef<number>(0);

  const handleMouseMove = useCallback((e: React.MouseEvent<HTMLDivElement>) => {
    const el = cardRef.current;
    if (!el) return;
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;

    const clientX = e.clientX;
    const clientY = e.clientY;
    if (rafRef.current) cancelAnimationFrame(rafRef.current);
    rafRef.current = requestAnimationFrame(() => {
      const rect = el.getBoundingClientRect();
      const x = clientX - rect.left;
      const y = clientY - rect.top;
      const nx = (x / Math.max(rect.width, 1)) * 2 - 1; // -1 to 1
      const ny = (y / Math.max(rect.height, 1)) * 2 - 1; // -1 to 1
      const ry = (nx * 3.8).toFixed(2);
      const rx = (-ny * 3.8).toFixed(2);
      el.style.setProperty('--rx', `${rx}deg`);
      el.style.setProperty('--ry', `${ry}deg`);
      el.style.setProperty('--ty', '-2px');
      el.style.setProperty('--sx', `${Math.round(x)}px`);
      el.style.setProperty('--sy', `${Math.round(y)}px`);
      el.style.setProperty('--spec-opacity', '1');
    });
  }, []);

  const handleMouseLeave = useCallback(() => {
    const el = cardRef.current;
    if (!el) return;
    if (rafRef.current) cancelAnimationFrame(rafRef.current);
    el.style.setProperty('--rx', '0deg');
    el.style.setProperty('--ry', '0deg');
    el.style.setProperty('--ty', '0px');
    el.style.setProperty('--spec-opacity', '0');
  }, []);

  useEffect(() => {
    return () => {
      if (rafRef.current) cancelAnimationFrame(rafRef.current);
    };
  }, []);

  return (
    <div
      ref={cardRef}
      data-testid={testId}
      data-nc-target="KPI"
      onMouseMove={handleMouseMove}
      onMouseLeave={handleMouseLeave}
      className={`nc-kpi-v3 ${className}`}
    >
      {children}
    </div>
  );
}

/**
 * Command-center live telemetry status rail for Dashboard headers.
 * Hydration-safe: clock initializes after client mount only, with blinking ':' separators.
 */
export function SystemStatusBar({ moduleLabel = 'NEURAL_MISSION_CTRL' }: { moduleLabel?: string }) {
  const [timeParts, setTimeParts] = useState<{ hh: string; mm: string; ss: string }>({
    hh: '--',
    mm: '--',
    ss: '--',
  });

  useEffect(() => {
    const updateClock = () => {
      const now = new Date();
      const formatted = now.toLocaleTimeString('vi-VN', {
        hour12: false,
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
      });
      const parts = formatted.split(':');
      if (parts.length === 3) {
        setTimeParts({ hh: parts[0], mm: parts[1], ss: parts[2] });
      }
    };
    const initId = window.setTimeout(updateClock, 0);
    const intervalId = window.setInterval(updateClock, 1000);
    return () => {
      window.clearTimeout(initId);
      window.clearInterval(intervalId);
    };
  }, []);

  return (
    <div
      className="inline-flex items-center gap-2 px-2.5 py-1 rounded-md border border-slate-200/80 bg-white/70 dark:border-slate-800 dark:bg-slate-900/60 text-xs shadow-2xs"
      aria-label="Trạng thái hệ thống"
    >
      <span className="inline-flex items-center gap-1.5 font-medium text-emerald-600 dark:text-emerald-400">
        <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse" />
        <span className="text-[11px] font-mono tracking-tight">AST ENGINE &middot; ONLINE</span>
      </span>
      <span className="text-slate-200 dark:text-slate-700" aria-hidden="true">|</span>
      <span className="font-mono text-[10px] text-slate-500 dark:text-slate-400 font-medium hidden sm:inline">
        {moduleLabel}
      </span>
      <span className="text-slate-200 dark:text-slate-700 hidden sm:inline" aria-hidden="true">|</span>
      <span
        className="font-mono text-[11px] text-slate-600 dark:text-slate-300 tabular-nums inline-flex items-center"
        data-testid="live-system-clock"
      >
        <span>{timeParts.hh}</span>
        <span className="nc-colon-blink mx-[1px] text-slate-400">:</span>
        <span>{timeParts.mm}</span>
        <span className="nc-colon-blink mx-[1px] text-slate-400">:</span>
        <span>{timeParts.ss}</span>
        <span className="ml-1 text-[10px] text-slate-400">UTC+7</span>
      </span>
    </div>
  );
}

/**
 * Decorative ultra-thin SVG circuit trace connecting header to main dashboard sections.
 */
export function CircuitTraceDivider() {
  return (
    <div className="pointer-events-none relative w-full h-3 -my-2 overflow-hidden opacity-65" aria-hidden="true">
      <svg viewBox="0 0 1200 12" preserveAspectRatio="none" className="w-full h-full">
        <path
          d="M 0 6 L 180 6 L 192 2 L 540 2 L 552 6 L 1020 6 L 1032 10 L 1200 10"
          fill="none"
          stroke="rgba(37, 99, 235, 0.14)"
          strokeWidth="1"
        />
        <path
          d="M 0 6 L 180 6 L 192 2 L 540 2 L 552 6 L 1020 6 L 1032 10 L 1200 10"
          fill="none"
          stroke="#22D3EE"
          strokeWidth="1.4"
          className="nc-circuit-trace"
        />
        <circle cx="192" cy="2" r="2" fill="#2563EB" fillOpacity="0.4" />
        <circle cx="552" cy="6" r="2" fill="#22D3EE" fillOpacity="0.6" />
        <circle cx="1032" cy="10" r="2" fill="#6366F1" fillOpacity="0.4" />
      </svg>
    </div>
  );
}

/**
 * Decorative semi-circular gauge for the AST Integrity Forensics box.
 * Reflects the exact flaggedCount prop without inventing new metrics.
 */
export function AstIntegrityGauge({ flaggedCount }: { flaggedCount: number }) {
  const isAlert = flaggedCount > 0;
  const arcRatio = isAlert ? Math.min(0.35 + flaggedCount * 0.15, 0.95) : 0.18;
  const radius = 38;
  const semiCircumference = Math.PI * radius;
  const dashOffset = semiCircumference * (1 - arcRatio);
  const strokeColor = isAlert ? '#E11D48' : '#059669';

  return (
    <div className="flex items-center justify-between gap-3 px-3 py-2.5 rounded-lg bg-slate-50/70 dark:bg-slate-800/40 border border-slate-200/70 dark:border-slate-800">
      <div className="relative w-20 h-12 flex items-end justify-center shrink-0" aria-hidden="true">
        <svg viewBox="0 0 96 54" className="w-full h-full overflow-visible">
          <path
            d="M 10 46 A 38 38 0 0 1 86 46"
            fill="none"
            stroke="rgba(148,163,184,0.2)"
            strokeWidth="5"
            strokeLinecap="round"
          />
          <path
            d="M 10 46 A 38 38 0 0 1 86 46"
            fill="none"
            stroke={strokeColor}
            strokeWidth="5"
            strokeLinecap="round"
            strokeDasharray={semiCircumference}
            strokeDashoffset={dashOffset}
            className="transition-all duration-700"
          />
        </svg>
        <span
          className={`absolute bottom-0 font-mono text-sm font-semibold tabular-nums ${
            isAlert ? 'text-rose-600 dark:text-rose-400' : 'text-emerald-700 dark:text-emerald-400'
          }`}
        >
          {flaggedCount}
        </span>
      </div>

      <div className="flex flex-col min-w-0 flex-1">
        <div className="flex items-center gap-1.5">
          <span
            className={`w-1.5 h-1.5 rounded-full shrink-0 ${
              isAlert ? 'bg-rose-500' : 'bg-emerald-500'
            }`}
          />
          <span
            className={`text-xs font-medium truncate ${
              isAlert ? 'text-rose-700 dark:text-rose-400' : 'text-emerald-700 dark:text-emerald-400'
            }`}
          >
            {isAlert ? `Phát hiện ${flaggedCount} cặp nghi vấn` : 'An toàn · Không có vi phạm'}
          </span>
        </div>
        <p
          className="text-[11px] text-slate-500 dark:text-slate-400 line-clamp-1 mt-0.5"
          title="Đối chiếu cấu trúc cây cú pháp AST và dải token tự động."
        >
          Đối chiếu cấu trúc cây cú pháp AST và dải token tự động.
        </p>
      </div>
    </div>
  );
}

/**
 * Futuristic Radar / AST Empty State for tables and lists with rotating radar sweep.
 */
export function RadarEmptyState({ message }: { message: string }) {
  return (
    <div className="py-10 px-4 flex flex-col items-center justify-center text-center gap-3">
      <div
        className="relative w-14 h-14 rounded-2xl bg-gradient-to-br from-blue-50 via-cyan-50/70 to-indigo-50 border border-blue-200/60 flex items-center justify-center shadow-2xs overflow-hidden"
        aria-hidden="true"
      >
        <svg viewBox="0 0 48 48" className="w-9 h-9">
          <circle cx="24" cy="24" r="19" fill="none" stroke="#2563EB" strokeOpacity="0.2" strokeWidth="1.5" />
          <circle
            cx="24"
            cy="24"
            r="12"
            fill="none"
            stroke="#22D3EE"
            strokeOpacity="0.4"
            strokeWidth="1.5"
            strokeDasharray="3 3"
          />
          <line x1="24" y1="5" x2="24" y2="43" stroke="#2563EB" strokeOpacity="0.16" strokeWidth="1" />
          <line x1="5" y1="24" x2="43" y2="24" stroke="#2563EB" strokeOpacity="0.16" strokeWidth="1" />
          <g
            className="nc-anim-orbit-cw"
            style={{ transformOrigin: '24px 24px', animation: 'nc-orbit-cw 6s linear infinite' }}
          >
            <line x1="24" y1="24" x2="38" y2="10" stroke="#2563EB" strokeWidth="1.75" strokeLinecap="round" />
            <circle cx="34" cy="14" r="2" fill="#22D3EE" />
          </g>
          <circle cx="24" cy="24" r="3" fill="#2563EB" />
        </svg>
      </div>
      <p className="text-xs text-slate-500 font-medium max-w-md leading-relaxed">{message}</p>
    </div>
  );
}

/**
 * Ambient Light Holographic background layer for Dashboard workspaces
 * (#F8FAFC base + subtle blueprint grid + 3 slow-drifting aurora blobs <= 0.06 opacity).
 */
export function WorkspaceAmbientBg() {
  return (
    <div className="pointer-events-none fixed inset-0 z-0 overflow-hidden" aria-hidden="true">
      <div className="absolute inset-0 nc-workspace-grid" />
      <div
        className="nc-anim-aurora absolute -top-32 right-0 w-[460px] h-[460px] rounded-full blur-[120px] opacity-[0.055]"
        style={{
          background: 'radial-gradient(circle, #2563EB 0%, #22D3EE 60%, transparent 100%)',
          animation: 'nc-aurora-drift 68s ease-in-out infinite',
        }}
      />
      <div
        className="nc-anim-aurora absolute bottom-0 left-64 w-[420px] h-[420px] rounded-full blur-[120px] opacity-[0.05]"
        style={{
          background: 'radial-gradient(circle, #22D3EE 0%, #6366F1 60%, transparent 100%)',
          animation: 'nc-aurora-drift 82s ease-in-out infinite reverse',
        }}
      />
      <div
        className="nc-anim-aurora absolute top-1/3 left-1/2 -translate-x-1/2 w-[380px] h-[380px] rounded-full blur-[130px] opacity-[0.04]"
        style={{
          background: 'radial-gradient(circle, #6366F1 0%, #2563EB 65%, transparent 100%)',
          animation: 'nc-aurora-drift 74s ease-in-out infinite',
        }}
      />
    </div>
  );
}
