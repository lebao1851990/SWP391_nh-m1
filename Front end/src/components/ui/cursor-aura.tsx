'use client';

import React, { useEffect, useRef, useState } from 'react';

/**
 * Config switch for Neural Command Center Cursor Aura HUD:
 * - 'off': completely disabled
 * - 'subtle': only Layer 1 (Aura Glow) + Layer 3 (Target Lock corners)
 * - 'full': all 8 layers (Aura Glow, Reticle, Target Lock, Magnetic, Click Ripple, Data Trail, Grid Reveal, Context Awareness)
 */
export const CURSOR_AURA_LEVEL: 'off' | 'subtle' | 'full' = 'full';

type TargetCategory = 'BUTTON' | 'LINK' | 'ROW' | 'KPI' | 'INPUT' | 'TAB' | 'CARD';

interface TargetLockInfo {
  element: HTMLElement;
  category: TargetCategory;
  rect: DOMRect;
  isSubtleInput: boolean;
}

interface ClickRippleItem {
  id: number;
  x: number;
  y: number;
}

const INTERACTIVE_SELECTOR = [
  '[data-testid="kpi-card-item"]',
  'button',
  'a[href]',
  '[role="button"]',
  '[role="tab"]',
  'input',
  'textarea',
  'select',
  'tr.nc-table-row',
  '[data-row]',
  '[data-nc-target]',
].join(', ');

function classifyTarget(el: HTMLElement): { category: TargetCategory; isSubtleInput: boolean } {
  const explicit = el.getAttribute('data-nc-target')?.toUpperCase();
  if (explicit === 'KPI' || el.getAttribute('data-testid') === 'kpi-card-item') {
    return { category: 'KPI', isSubtleInput: false };
  }
  const tag = el.tagName.toLowerCase();
  if (tag === 'input' || tag === 'textarea' || tag === 'select' || el.isContentEditable) {
    return { category: 'INPUT', isSubtleInput: true };
  }
  if (tag === 'tr' || el.hasAttribute('data-row')) {
    return { category: 'ROW', isSubtleInput: false };
  }
  if (el.getAttribute('role') === 'tab') {
    return { category: 'TAB', isSubtleInput: false };
  }
  if (tag === 'a') {
    return { category: 'LINK', isSubtleInput: false };
  }
  if (tag === 'button' || el.getAttribute('role') === 'button') {
    return { category: 'BUTTON', isSubtleInput: false };
  }
  return { category: 'CARD', isSubtleInput: false };
}

export function CursorAura() {
  const [enabled, setEnabled] = useState(false);
  const [ripples, setRipples] = useState<ClickRippleItem[]>([]);

  const rootRef = useRef<HTMLDivElement>(null);
  const auraGlowRef = useRef<HTMLDivElement>(null);
  const gridRevealRef = useRef<HTMLDivElement>(null);
  const reticleWrapRef = useRef<HTMLDivElement>(null);
  const reticleInnerRef = useRef<HTMLDivElement>(null);
  const targetBoxRef = useRef<HTMLDivElement>(null);
  const targetBadgeRef = useRef<HTMLSpanElement>(null);
  const trailRefs = useRef<(HTMLDivElement | null)[]>([]);

  // Check fine pointer + reduced-motion media queries on mount
  useEffect(() => {
    if (CURSOR_AURA_LEVEL === 'off' || typeof window === 'undefined') return;

    const finePointerMq = window.matchMedia('(hover: hover) and (pointer: fine)');
    const reducedMotionMq = window.matchMedia('(prefers-reduced-motion: reduce)');

    const updateAvailability = () => {
      setEnabled(finePointerMq.matches && !reducedMotionMq.matches);
    };

    updateAvailability();
    finePointerMq.addEventListener('change', updateAvailability);
    reducedMotionMq.addEventListener('change', updateAvailability);

    return () => {
      finePointerMq.removeEventListener('change', updateAvailability);
      reducedMotionMq.removeEventListener('change', updateAvailability);
    };
  }, []);

  useEffect(() => {
    if (!enabled || CURSOR_AURA_LEVEL === 'off') return;

    let rafId: number | null = null;
    let isRunning = false;

    // Raw mouse coordinates
    let mouseX = -400;
    let mouseY = -400;
    // Lerped aura coordinates
    let auraX = -400;
    let auraY = -400;
    // Lerped reticle coordinates
    let retX = -400;
    let retY = -400;

    let lastMoveTime = performance.now();
    let isVisible = false;
    let currentTheme: 'light' | 'dark' = 'light';
    let inCodeZone = false;

    // Target lock state
    let lockedTarget: TargetLockInfo | null = null;
    // Magnetic element state
    let magneticEl: HTMLElement | null = null;

    // Trail history (8 points)
    const TRAIL_COUNT = 8;
    const trailPoints = Array.from({ length: TRAIL_COUNT }, () => ({ x: -400, y: -400 }));

    const clearMagnetic = () => {
      if (magneticEl) {
        magneticEl.style.transform = '';
        magneticEl = null;
      }
    };

    const applyTargetLockBox = (info: TargetLockInfo | null) => {
      const box = targetBoxRef.current;
      const badge = targetBadgeRef.current;
      if (!box || !badge) return;

      if (!info) {
        box.style.opacity = '0';
        return;
      }

      const pad = info.category === 'ROW' ? 2 : 4;
      const x = Math.round(info.rect.left - pad);
      const y = Math.round(info.rect.top - pad);
      const w = Math.round(info.rect.width + pad * 2);
      const h = Math.round(info.rect.height + pad * 2);

      box.style.transform = `translate3d(${x}px, ${y}px, 0)`;
      box.style.width = `${w}px`;
      box.style.height = `${h}px`;
      box.style.opacity = '1';
      box.setAttribute('data-subtle-input', info.isSubtleInput ? 'true' : 'false');
      box.setAttribute('data-theme-mode', currentTheme);

      badge.textContent = `TARGET · ${info.category}`;
    };

    const refreshTargetRect = () => {
      if (!lockedTarget) return;
      if (!lockedTarget.element.isConnected) {
        lockedTarget = null;
        applyTargetLockBox(null);
        return;
      }
      lockedTarget.rect = lockedTarget.element.getBoundingClientRect();
      applyTargetLockBox(lockedTarget);
    };

    const tick = (now: number) => {
      if (!isVisible) {
        isRunning = false;
        rafId = null;
        return;
      }

      // Lerp Aura (0.15) & Reticle (0.28)
      const dx = mouseX - auraX;
      const dy = mouseY - auraY;
      auraX += dx * 0.15;
      auraY += dy * 0.15;

      const rdx = mouseX - retX;
      const rdy = mouseY - retY;
      retX += rdx * 0.28;
      retY += rdy * 0.28;

      const speed = Math.hypot(rdx, rdy);
      const idleMs = now - lastMoveTime;
      const isIdle = idleMs > 600;

      // Update CSS variables on root
      if (rootRef.current) {
        rootRef.current.style.setProperty('--cx', `${auraX.toFixed(1)}px`);
        rootRef.current.style.setProperty('--cy', `${auraY.toFixed(1)}px`);
      }

      // 1. Update Aura Glow transform
      if (auraGlowRef.current) {
        auraGlowRef.current.style.transform = `translate3d(${(auraX - 150).toFixed(1)}px, ${(auraY - 150).toFixed(1)}px, 0)`;
      }

      // 7. Update Grid Reveal transform (moves a 360x360 radial-masked grid window using GPU translate3d only)
      if (gridRevealRef.current && CURSOR_AURA_LEVEL === 'full') {
        const gx = Math.round(auraX - 180);
        const gy = Math.round(auraY - 180);
        gridRevealRef.current.style.transform = `translate3d(${gx}px, ${gy}px, 0)`;
      }

      // 2. Update Reticle position + velocity stretch
      if (reticleWrapRef.current && reticleInnerRef.current && CURSOR_AURA_LEVEL === 'full') {
        const stretch = Math.min(1 + speed * 0.008, 1.24);
        const invStretch = 1 / Math.sqrt(stretch);
        const angle = Math.atan2(rdy, rdx) * (180 / Math.PI);
        const baseScale = lockedTarget ? 0.65 : isIdle ? 0.68 : 1;

        reticleWrapRef.current.style.transform = `translate3d(${(retX - 14).toFixed(1)}px, ${(retY - 14).toFixed(1)}px, 0)`;
        reticleWrapRef.current.style.opacity = inCodeZone
          ? '0.28'
          : lockedTarget
          ? '0.45'
          : isIdle
          ? '0.32'
          : '0.88';

        reticleInnerRef.current.style.transform =
          speed > 2.5
            ? `rotate(${angle.toFixed(1)}deg) scale(${(baseScale * stretch).toFixed(2)}, ${(baseScale * invStretch).toFixed(2)})`
            : `scale(${baseScale.toFixed(2)})`;
      }

      // 6. Update Data Trail points
      if (CURSOR_AURA_LEVEL === 'full') {
        let prevX = retX;
        let prevY = retY;
        for (let i = 0; i < TRAIL_COUNT; i++) {
          const pt = trailPoints[i];
          pt.x += (prevX - pt.x) * 0.36;
          pt.y += (prevY - pt.y) * 0.36;
          prevX = pt.x;
          prevY = pt.y;

          const el = trailRefs.current[i];
          if (el) {
            const showTrail = speed > 3.2 && !inCodeZone && !isIdle;
            const opacity = showTrail ? Math.max(0, (1 - i / TRAIL_COUNT) * 0.55) : 0;
            const scale = Math.max(0.35, 1 - i * 0.08);
            el.style.transform = `translate3d(${(pt.x - 2.5).toFixed(1)}px, ${(pt.y - 2.5).toFixed(1)}px, 0) scale(${scale.toFixed(2)})`;
            el.style.opacity = opacity.toFixed(2);
          }
        }
      }

      // Continue loop while moving or not yet settled into idle
      if (!isIdle || Math.hypot(dx, dy) > 0.25) {
        rafId = window.requestAnimationFrame(tick);
      } else {
        isRunning = false;
        rafId = null;
      }
    };

    const ensureLoop = () => {
      if (!isRunning && !document.hidden) {
        isRunning = true;
        rafId = window.requestAnimationFrame(tick);
      }
    };

    const resolveTargetFromElement = (rawTarget: Element | null): HTMLElement | null => {
      if (!rawTarget) return null;
      // Do not target-lock individual similarity matrix cells or Header global search input
      if (rawTarget.closest('[data-nc-matrix-grid="true"]')) return null;
      if (rawTarget.closest('[data-testid="header-search-shell"]')) return null;

      let candidate = rawTarget.closest(INTERACTIVE_SELECTOR) as HTMLElement | null;
      if (!candidate) return null;
      if (candidate.hasAttribute('data-nc-no-lock')) return null;

      // If candidate is a display:contents wrapper (e.g., AppLink with width=0, height=0), resolve to its first rendered element child
      const checkRect = candidate.getBoundingClientRect();
      if (checkRect.width === 0 && checkRect.height === 0 && candidate.firstElementChild instanceof HTMLElement) {
        candidate = candidate.firstElementChild;
      }
      return candidate;
    };

    const handlePointerMove = (e: MouseEvent) => {
      if (auraX < -200 && auraY < -200) {
        auraX = e.clientX;
        auraY = e.clientY;
        retX = e.clientX;
        retY = e.clientY;
        for (let i = 0; i < TRAIL_COUNT; i++) {
          trailPoints[i].x = e.clientX;
          trailPoints[i].y = e.clientY;
        }
      }

      mouseX = e.clientX;
      mouseY = e.clientY;
      lastMoveTime = performance.now();

      if (!isVisible) {
        isVisible = true;
        if (rootRef.current) {
          rootRef.current.style.opacity = '1';
        }
      }

      const targetEl = e.target as Element | null;
      if (targetEl) {
        // Detect surface theme (light vs dark)
        const themeHost = targetEl.closest('[data-nc-theme]') as HTMLElement | null;
        const nextTheme = (themeHost?.getAttribute('data-nc-theme') as 'light' | 'dark') || 'light';
        if (nextTheme !== currentTheme) {
          currentTheme = nextTheme;
          if (rootRef.current) {
            rootRef.current.setAttribute('data-active-theme', currentTheme);
          }
        }

        // Detect code / matrix zone
        const nextCodeZone = Boolean(
          targetEl.closest('[data-nc-zone="code"], pre, code, .monaco-editor')
        );
        if (nextCodeZone !== inCodeZone) {
          inCodeZone = nextCodeZone;
          if (rootRef.current) {
            rootRef.current.setAttribute('data-code-zone', inCodeZone ? 'true' : 'false');
          }
        }

        // Target Lock detection (only calls getBoundingClientRect when target element changes!)
        const interactiveEl = resolveTargetFromElement(targetEl);
        if (interactiveEl !== (lockedTarget?.element ?? null)) {
          if (interactiveEl) {
            const { category, isSubtleInput } = classifyTarget(interactiveEl);
            lockedTarget = {
              element: interactiveEl,
              category,
              rect: interactiveEl.getBoundingClientRect(),
              isSubtleInput,
            };
            applyTargetLockBox(lockedTarget);
          } else {
            lockedTarget = null;
            applyTargetLockBox(null);
          }
        }

        // 4. Magnetic Pull on [data-nc-magnetic] (max 3.5px, never on inputs)
        if (CURSOR_AURA_LEVEL === 'full') {
          const magCandidate = targetEl.closest('[data-nc-magnetic="true"]') as HTMLElement | null;
          if (magCandidate !== magneticEl) {
            clearMagnetic();
            if (
              magCandidate &&
              !['INPUT', 'TEXTAREA', 'SELECT'].includes(magCandidate.tagName)
            ) {
              magneticEl = magCandidate;
            }
          }
          if (magneticEl && lockedTarget?.element === magneticEl) {
            const r = lockedTarget.rect;
            const centerX = r.left + r.width / 2;
            const centerY = r.top + r.height / 2;
            const pullX = Math.max(-3.5, Math.min(3.5, ((e.clientX - centerX) / (r.width / 2 || 1)) * 3.2));
            const pullY = Math.max(-3.5, Math.min(3.5, ((e.clientY - centerY) / (r.height / 2 || 1)) * 3.2));
            magneticEl.style.transform = `translate3d(${pullX.toFixed(2)}px, ${pullY.toFixed(2)}px, 0)`;
          }
        }
      }

      ensureLoop();
    };

    const handleFocusIn = (e: FocusEvent) => {
      const targetEl = e.target as Element | null;
      const interactiveEl = resolveTargetFromElement(targetEl);
      if (interactiveEl) {
        const { category, isSubtleInput } = classifyTarget(interactiveEl);
        lockedTarget = {
          element: interactiveEl,
          category,
          rect: interactiveEl.getBoundingClientRect(),
          isSubtleInput,
        };
        if (!isVisible) {
          isVisible = true;
          if (rootRef.current) rootRef.current.style.opacity = '1';
        }
        applyTargetLockBox(lockedTarget);
      }
    };

    const handleMouseDown = (e: MouseEvent) => {
      if (CURSOR_AURA_LEVEL !== 'full') return;
      const id = Date.now() + Math.random();
      const x = e.clientX;
      const y = e.clientY;
      setRipples((prev) => [...prev.slice(-3), { id, x, y }]);
      window.setTimeout(() => {
        setRipples((prev) => prev.filter((item) => item.id !== id));
      }, 450);
    };

    const handleMouseLeaveDoc = () => {
      isVisible = false;
      lockedTarget = null;
      clearMagnetic();
      applyTargetLockBox(null);
      if (rootRef.current) {
        rootRef.current.style.opacity = '0';
      }
      if (rafId !== null) {
        window.cancelAnimationFrame(rafId);
        rafId = null;
        isRunning = false;
      }
    };

    const handleVisibilityChange = () => {
      if (document.hidden) {
        handleMouseLeaveDoc();
      }
    };

    window.addEventListener('mousemove', handlePointerMove, { passive: true });
    window.addEventListener('mousedown', handleMouseDown, { passive: true });
    window.addEventListener('focusin', handleFocusIn, { passive: true });
    window.addEventListener('scroll', refreshTargetRect, { passive: true, capture: true });
    window.addEventListener('resize', refreshTargetRect, { passive: true });
    document.documentElement.addEventListener('mouseleave', handleMouseLeaveDoc);
    document.addEventListener('visibilitychange', handleVisibilityChange);

    return () => {
      window.removeEventListener('mousemove', handlePointerMove);
      window.removeEventListener('mousedown', handleMouseDown);
      window.removeEventListener('focusin', handleFocusIn);
      window.removeEventListener('scroll', refreshTargetRect, true);
      window.removeEventListener('resize', refreshTargetRect);
      document.documentElement.removeEventListener('mouseleave', handleMouseLeaveDoc);
      document.removeEventListener('visibilitychange', handleVisibilityChange);
      clearMagnetic();
      if (rafId !== null) {
        window.cancelAnimationFrame(rafId);
      }
    };
  }, [enabled]);

  if (!enabled || CURSOR_AURA_LEVEL === 'off') {
    return null;
  }

  return (
    <div
      ref={rootRef}
      data-testid="cursor-aura-root"
      data-aura-level={CURSOR_AURA_LEVEL}
      data-active-theme="light"
      data-code-zone="false"
      aria-hidden="true"
      className="pointer-events-none fixed inset-0 z-[38] overflow-hidden opacity-0 transition-opacity duration-200 select-none"
    >
      {/* Layer 7: Grid Reveal under Cursor (360x360 radial-masked blueprint grid window moved via translate3d) */}
      {CURSOR_AURA_LEVEL === 'full' && (
        <div
          ref={gridRevealRef}
          data-testid="cursor-grid-reveal"
          className="pointer-events-none absolute top-0 left-0 w-[360px] h-[360px] rounded-full will-change-transform nc-cursor-grid-reveal"
        />
      )}

      {/* Layer 1: Soft Neural Aura Glow (~300px radial, adapts to light/dark theme & code zone) */}
      <div
        ref={auraGlowRef}
        data-testid="cursor-aura-glow"
        className="pointer-events-none absolute top-0 left-0 w-[300px] h-[300px] rounded-full will-change-transform nc-cursor-aura-glow"
      />

      {/* Layer 6: Data Trail (8 tiny cyan dots trailing fast movement) */}
      {CURSOR_AURA_LEVEL === 'full' &&
        Array.from({ length: 8 }).map((_, idx) => (
          <div
            key={idx}
            ref={(el) => {
              trailRefs.current[idx] = el;
            }}
            className="pointer-events-none absolute top-0 left-0 w-[5px] h-[5px] rounded-full bg-[#22D3EE] shadow-[0_0_6px_#22D3EE] opacity-0 will-change-transform"
          />
        ))}

      {/* Layer 2: Precision HUD Reticle (28px circle + 4 slow-rotating ticks) */}
      {CURSOR_AURA_LEVEL === 'full' && (
        <div
          ref={reticleWrapRef}
          data-testid="cursor-hud-reticle"
          className="pointer-events-none absolute top-0 left-0 w-7 h-7 will-change-transform transition-opacity duration-200"
        >
          <div
            ref={reticleInnerRef}
            className="w-full h-full flex items-center justify-center transition-transform duration-75"
          >
            <svg
              viewBox="0 0 32 32"
              className="w-7 h-7 animate-[spin_14s_linear_infinite] overflow-visible"
            >
              <circle
                cx="16"
                cy="16"
                r="11.5"
                fill="none"
                stroke="#0284C7"
                strokeOpacity="0.45"
                strokeWidth="1"
                strokeDasharray="4 3"
              />
              {/* 4 Cardinal HUD Ticks */}
              <line x1="16" y1="1.5" x2="16" y2="5" stroke="#22D3EE" strokeWidth="1.4" strokeLinecap="round" />
              <line x1="16" y1="27" x2="16" y2="30.5" stroke="#22D3EE" strokeWidth="1.4" strokeLinecap="round" />
              <line x1="1.5" y1="16" x2="5" y2="16" stroke="#22D3EE" strokeWidth="1.4" strokeLinecap="round" />
              <line x1="27" y1="16" x2="30.5" y2="16" stroke="#22D3EE" strokeWidth="1.4" strokeLinecap="round" />
            </svg>
          </div>
        </div>
      )}

      {/* Layer 3: Target Lock Brackets + Subtle Conic Border + Micro Telemetry Label */}
      <div
        ref={targetBoxRef}
        data-testid="cursor-target-lock"
        data-subtle-input="false"
        data-theme-mode="light"
        className="nc-target-lock-box pointer-events-none absolute top-0 left-0 opacity-0 rounded-lg will-change-transform transition-opacity duration-150 ease-out"
      >
        {/* 4 HUD Corner Brackets */}
        <span className="pointer-events-none absolute -top-[1px] -left-[1px] w-2.5 h-2.5 border-t-2 border-l-2 border-[#22D3EE] rounded-tl-[3px] shadow-[0_0_8px_rgba(34,211,238,0.5)]" />
        <span className="pointer-events-none absolute -top-[1px] -right-[1px] w-2.5 h-2.5 border-t-2 border-r-2 border-[#22D3EE] rounded-tr-[3px] shadow-[0_0_8px_rgba(34,211,238,0.5)]" />
        <span className="pointer-events-none absolute -bottom-[1px] -left-[1px] w-2.5 h-2.5 border-b-2 border-l-2 border-[#22D3EE] rounded-bl-[3px] shadow-[0_0_8px_rgba(34,211,238,0.5)]" />
        <span className="pointer-events-none absolute -bottom-[1px] -right-[1px] w-2.5 h-2.5 border-b-2 border-r-2 border-[#22D3EE] rounded-br-[3px] shadow-[0_0_8px_rgba(34,211,238,0.5)]" />

        {/* Micro HUD Target Label */}
        <span
          ref={targetBadgeRef}
          data-testid="cursor-target-label"
          className="nc-target-lock-badge pointer-events-none absolute -top-4 right-0 px-1.5 py-[1px] rounded bg-slate-900/90 text-[#22D3EE] border border-cyan-400/45 font-mono text-[9px] font-bold tracking-wider uppercase whitespace-nowrap shadow-[0_2px_8px_rgba(34,211,238,0.25)]"
        >
          TARGET · BUTTON
        </span>
      </div>

      {/* Layer 5: Click Scan Ripples (<= 450ms ring + 8 radial rays) */}
      {ripples.map((rp) => (
        <div
          key={rp.id}
          data-testid="cursor-click-ripple"
          style={{
            left: `${rp.x}px`,
            top: `${rp.y}px`,
          }}
          className="nc-click-ripple pointer-events-none absolute -translate-x-1/2 -translate-y-1/2 w-20 h-20"
        >
          <svg viewBox="0 0 80 80" className="w-full h-full overflow-visible">
            <circle
              cx="40"
              cy="40"
              r="28"
              fill="none"
              stroke="#22D3EE"
              strokeWidth="1.5"
              strokeOpacity="0.85"
            />
            <circle
              cx="40"
              cy="40"
              r="16"
              fill="none"
              stroke="#2563EB"
              strokeWidth="1"
              strokeDasharray="3 3"
            />
            {[0, 45, 90, 135, 180, 225, 270, 315].map((deg) => {
              const rad = (deg * Math.PI) / 180;
              const x1 = 40 + Math.cos(rad) * 18;
              const y1 = 40 + Math.sin(rad) * 18;
              const x2 = 40 + Math.cos(rad) * 34;
              const y2 = 40 + Math.sin(rad) * 34;
              return (
                <line
                  key={deg}
                  x1={x1}
                  y1={y1}
                  x2={x2}
                  y2={y2}
                  stroke="#22D3EE"
                  strokeWidth="1.3"
                  strokeLinecap="round"
                />
              );
            })}
          </svg>
        </div>
      ))}
    </div>
  );
}

