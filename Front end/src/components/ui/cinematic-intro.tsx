'use client';

import React, { useCallback, useEffect, useRef, useState } from 'react';
import { RotateCcw } from 'lucide-react';
import { NeuralLogo } from './neural-logo';
import { HudCorners } from './hud-card';

export type IntroMode = 'always' | 'once-per-session' | 'once-ever';

/**
 * Controls when the Cinematic Intro is shown:
 * - 'always': Plays on every full page load / reload (F5, new tab), but NOT on client-side route changes within the same load.
 * - 'once-per-session': Plays once per browser tab session (sessionStorage).
 * - 'once-ever': Plays once per browser profile (localStorage).
 */
export const INTRO_MODE: IntroMode = 'always';

const INTRO_STORAGE_KEY = 'codeassess_intro_seen';

// Module-level flag: resets to false on every full page reload (F5 / new tab),
// but persists across client-side navigations (e.g. /login -> /register -> /login).
let hasPlayedInThisPageLoad = false;

function checkHasSeenIntro(): boolean {
  if (hasPlayedInThisPageLoad) {
    return true;
  }
  if (INTRO_MODE === 'always') {
    return false;
  }
  try {
    if (INTRO_MODE === 'once-per-session') {
      return window.sessionStorage.getItem(INTRO_STORAGE_KEY) === '1';
    }
    if (INTRO_MODE === 'once-ever') {
      return window.localStorage.getItem(INTRO_STORAGE_KEY) === '1';
    }
  } catch {
    // Ignore storage access errors in restricted modes
  }
  return false;
}

function recordIntroSeen(): void {
  hasPlayedInThisPageLoad = true;
  try {
    if (INTRO_MODE === 'once-per-session') {
      window.sessionStorage.setItem(INTRO_STORAGE_KEY, '1');
    } else if (INTRO_MODE === 'once-ever') {
      window.localStorage.setItem(INTRO_STORAGE_KEY, '1');
    }
  } catch {
    // Ignore storage quota/privacy errors
  }
}

type IntroStatus = 'checking' | 'playing' | 'converging' | 'completed';

interface CinematicIntroProps {
  children: React.ReactNode;
}

/**
 * 7.2-second Futuristic Cinematic Intro for CodeAssess ("Neural Command Center")
 * Scenes:
 * - Scene 0 (0.0s - 0.8s): Boot sequence & horizontal laser line
 * - Scene 1 (0.8s - 2.4s): Materialize Logo + Frosted Java Code Editor + Flying .java module
 * - Scene 2 (2.4s - 4.4s): AST Syntax Tree crystallization + Live Automated Test Case Runner (✓ ✓ ✓ ✗ ✓)
 * - Scene 3 (4.4s - 6.2s): Dual AST Plagiarism Detection + Energy Arcs + Similarity Gauge (0% -> 87% HIGH SIMILARITY)
 * - Scene 4 (6.2s - 7.2s): Converge to Login + Shockwave ring + Seamless reveal of Login Card
 */
export function CinematicIntro({ children }: CinematicIntroProps) {
  const [status, setStatus] = useState<IntroStatus>('checking');
  const [scene, setScene] = useState<number>(0);
  const [similarityPct, setSimilarityPct] = useState<number>(0);
  const [scoreValue, setScoreValue] = useState<number>(0);
  const [reducedMotion, setReducedMotion] = useState<boolean>(false);
  const timersRef = useRef<number[]>([]);

  const clearAllTimers = useCallback(() => {
    timersRef.current.forEach((id) => window.clearTimeout(id));
    timersRef.current = [];
  }, []);

  const scheduleSceneTimeline = useCallback(
    (isReduced: boolean) => {
      clearAllTimers();
      hasPlayedInThisPageLoad = true;

      if (isReduced) {
        const t = window.setTimeout(() => {
          recordIntroSeen();
          setStatus('completed');
        }, 850);
        timersRef.current.push(t);
        return;
      }

      timersRef.current.push(window.setTimeout(() => setScene(1), 800));
      timersRef.current.push(window.setTimeout(() => setScene(2), 2400));
      timersRef.current.push(window.setTimeout(() => setScene(3), 4400));
      timersRef.current.push(
        window.setTimeout(() => {
          setScene(4);
          setStatus('converging');
        }, 6200)
      );
      timersRef.current.push(
        window.setTimeout(() => {
          recordIntroSeen();
          setStatus('completed');
        }, 7200)
      );
    },
    [clearAllTimers]
  );

  const finishIntro = useCallback(
    (fast = false) => {
      clearAllTimers();
      recordIntroSeen();

      if (fast) {
        setStatus('converging');
        setScene(4);
        const t = window.setTimeout(() => {
          setStatus('completed');
        }, 250);
        timersRef.current.push(t);
      } else {
        setStatus('completed');
      }
    },
    [clearAllTimers]
  );

  const handleReplayIntro = useCallback(() => {
    clearAllTimers();
    const isReduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    setReducedMotion(isReduced);
    setSimilarityPct(0);
    setScoreValue(0);
    setScene(0);
    setStatus('playing');
    scheduleSceneTimeline(isReduced);
  }, [clearAllTimers, scheduleSceneTimeline]);

  // Client mount check for INTRO_MODE & prefers-reduced-motion
  useEffect(() => {
    const seen = checkHasSeenIntro();
    const isReduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

    const initTimer = window.setTimeout(() => {
      setReducedMotion(isReduced);
      if (seen) {
        setStatus('completed');
        return;
      }
      setStatus('playing');
      setScene(0);
      scheduleSceneTimeline(isReduced);
    }, 0);
    timersRef.current.push(initTimer);

    return () => clearAllTimers();
  }, [clearAllTimers, scheduleSceneTimeline]);

  // Animate score counter in Scene 2 (0 -> 80 pts)
  useEffect(() => {
    if (status !== 'playing' || scene !== 2 || reducedMotion) return;
    let raf = 0;
    const start = performance.now();
    const duration = 1400;
    const step = (now: number) => {
      const p = Math.min((now - start) / duration, 1);
      setScoreValue(Math.round(p * 80));
      if (p < 1) raf = requestAnimationFrame(step);
    };
    raf = requestAnimationFrame(step);
    return () => cancelAnimationFrame(raf);
  }, [status, scene, reducedMotion]);

  // Animate similarity gauge in Scene 3 (0% -> 87%)
  useEffect(() => {
    if (status !== 'playing' || scene !== 3 || reducedMotion) return;
    let raf = 0;
    const start = performance.now();
    const duration = 1350;
    const target = 87;
    const step = (now: number) => {
      const p = Math.min((now - start) / duration, 1);
      const eased = 1 - Math.pow(1 - p, 3);
      setSimilarityPct(Math.round(eased * target));
      if (p < 1) raf = requestAnimationFrame(step);
    };
    raf = requestAnimationFrame(step);
    return () => cancelAnimationFrame(raf);
  }, [status, scene, reducedMotion]);

  // Keyboard listener: Esc or Enter skips intro (active whenever intro is not completed)
  useEffect(() => {
    if (status === 'completed') return;

    const handleKeyDown = (e: KeyboardEvent) => {
      const tag = (e.target as HTMLElement | null)?.tagName;
      if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') return;

      if (e.key === 'Escape' || e.key === 'Enter') {
        e.preventDefault();
        finishIntro(true);
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [status, finishIntro]);

  // Determine gauge color in Scene 3 (Cyan -> Amber -> Rose Red)
  const gaugeColor =
    similarityPct >= 70 ? '#F43F5E' : similarityPct >= 40 ? '#F59E0B' : '#22D3EE';
  const gaugeCircumference = 2 * Math.PI * 52;
  const gaugeDashoffset = gaugeCircumference - (similarityPct / 100) * gaugeCircumference;

  const isIntroVisible = status === 'checking' || status === 'playing' || status === 'converging';

  return (
    <div className="relative w-full flex flex-col items-center justify-center">
      {/* Cinematic Intro Overlay (renders neutral Void navy frame during 'checking' to prevent Login flash & hydration mismatch) */}
      {isIntroVisible && (
        <div
          data-testid="cinematic-intro-overlay"
          role="region"
          aria-label="Giới thiệu hệ thống CodeAssess"
          className={`fixed inset-0 z-50 flex flex-col items-center justify-center overflow-hidden bg-[#010409] px-4 transition-opacity duration-500 ${status === 'converging' ? 'opacity-0 pointer-events-none' : 'opacity-100'
            }`}
        >
          {/* Ambient Blueprint Grid + Aurora inside Intro */}
          <div className="absolute inset-0 nc-grid-blueprint opacity-80 pointer-events-none" aria-hidden="true" />
          <div
            className="absolute w-[62vmin] h-[62vmin] rounded-full blur-[110px] opacity-25 pointer-events-none"
            style={{
              background: 'radial-gradient(circle, #2563EB 0%, #22D3EE 45%, transparent 75%)',
            }}
            aria-hidden="true"
          />

          {/* Reduced Motion Fast View */}
          {reducedMotion && status === 'playing' && (
            <div className="relative z-10 flex flex-col items-center text-center gap-4 max-w-md">
              <NeuralLogo className="w-28 h-28" showChromAberration={false} showLaserSweep={false} />
              <div className="font-mono text-xs uppercase tracking-[0.25em] text-cyan-400">
                CODEASSESS NEURAL CORE v4.2
              </div>
              <p className="text-sm text-slate-300">
                AST Plagiarism Detection &amp; Automated Grading Platform
              </p>
            </div>
          )}

          {/* Full 5-Scene Storyboard (when not in checking/reducedMotion) */}
          {!reducedMotion && status !== 'checking' && (
            <div className="relative z-10 w-full max-w-4xl flex flex-col items-center justify-center min-h-[420px]">
              {/* Top HUD Telemetry Bar */}
              <div
                className="w-full max-w-2xl mb-6 flex items-center justify-between px-4 py-2 rounded-lg border border-slate-800/80 bg-slate-950/70 backdrop-blur-md text-[11px] font-mono text-slate-400"
                aria-hidden="true"
              >
                <div className="flex items-center gap-2">
                  <span className="w-2 h-2 rounded-full bg-cyan-400 animate-ping" />
                  <span className="text-cyan-300 font-semibold">NEURAL_CMD_CENTER // AST_V4.2</span>
                </div>
                <div className="flex items-center gap-3">
                  <span>PHASE: 0{Math.min(scene + 1, 4)}/04</span>
                  <span className="text-emerald-400">SANDBOX: ONLINE</span>
                </div>
              </div>

              {/* SCENE 0 — BOOT (0 - 0.8s) */}
              {scene === 0 && (
                <div className="flex flex-col items-center justify-center gap-6 w-full py-16 animate-in fade-in duration-300">
                  <div className="w-full max-w-lg h-[2px] bg-slate-900 overflow-hidden relative rounded-full">
                    <div className="h-full w-full bg-gradient-to-r from-transparent via-cyan-400 to-blue-500 animate-pulse" />
                  </div>
                  <div className="font-mono text-sm sm:text-base text-cyan-300 tracking-wider flex items-center gap-2">
                    <span className="text-blue-400">&gt;</span>
                    <span>initializing CodeAssess core...</span>
                    <span className="w-2 h-4 bg-cyan-400 animate-pulse inline-block" />
                  </div>
                </div>
              )}

              {/* SCENE 1 — SUBMIT YOUR CODE (0.8 - 2.4s) */}
              {scene === 1 && (
                <div className="w-full flex flex-col items-center gap-6 animate-in fade-in zoom-in-95 duration-500">
                  <div className="relative flex flex-col md:flex-row items-center justify-center gap-6 w-full max-w-2xl">
                    {/* Materializing Logo Core */}
                    <div className="relative flex flex-col items-center">
                      <NeuralLogo className="w-36 h-36 sm:w-44 sm:h-44" />
                      {/* Flying .java file badge entering the core */}
                      <div className="mt-2 inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-cyan-500/15 border border-cyan-400/40 text-cyan-300 font-mono text-xs shadow-[0_0_20px_rgba(34,211,238,0.3)]">
                        <span className="w-2 h-2 rounded-full bg-cyan-400" />
                        <span>Solution.java &rarr; AST_INGEST</span>
                      </div>
                    </div>

                    {/* Frosted Glass Code Editor Window */}
                    <div className="relative w-full max-w-md rounded-xl bg-slate-900/75 border border-slate-700/60 backdrop-blur-xl p-4 shadow-[0_8px_40px_-8px_rgba(37,99,235,0.45)] font-mono text-xs text-slate-200">
                      <HudCorners colorClass="border-cyan-400/60" />
                      <div className="flex items-center justify-between pb-2.5 mb-3 border-b border-slate-800 text-[11px] text-slate-400">
                        <div className="flex items-center gap-1.5">
                          <span className="w-2.5 h-2.5 rounded-full bg-rose-500/80" />
                          <span className="w-2.5 h-2.5 rounded-full bg-amber-400/80" />
                          <span className="w-2.5 h-2.5 rounded-full bg-emerald-400/80" />
                        </div>
                        <span className="text-cyan-300">QuickSortSubmission.java</span>
                      </div>
                      <pre className="space-y-1.5 text-[11px] sm:text-xs leading-relaxed overflow-hidden">
                        <div>
                          <span className="text-indigo-400">public class</span>{' '}
                          <span className="text-cyan-300">Solution</span> {'{'}
                        </div>
                        <div className="pl-4">
                          <span className="text-indigo-400">public static int</span>{' '}
                          <span className="text-blue-300">solve</span>(int[] arr) {'{'}
                        </div>
                        <div className="pl-8 text-emerald-300">
                          return AstEngine.verifyAndExecute(arr);
                        </div>
                        <div className="pl-4">{'}'}</div>
                        <div>{'}'}</div>
                      </pre>
                    </div>
                  </div>

                  {/* Scene 1 Caption */}
                  <div className="text-center mt-2">
                    <div className="font-mono text-[11px] uppercase tracking-[0.25em] text-cyan-400">
                      STAGE 01 // INGESTION
                    </div>
                    <h2 className="text-xl sm:text-2xl font-semibold text-white tracking-tight mt-1">
                      Submit Your Code
                    </h2>
                  </div>
                </div>
              )}

              {/* SCENE 2 — AST ANALYSIS & AUTOMATED GRADING (2.4 - 4.4s) */}
              {scene === 2 && (
                <div className="w-full flex flex-col items-center gap-6 animate-in fade-in duration-500">
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-5 w-full max-w-2xl">
                    {/* Left: AST Tree Visualization */}
                    <div className="relative rounded-xl bg-slate-900/75 border border-slate-700/60 backdrop-blur-xl p-4 flex flex-col items-center justify-center overflow-hidden">
                      <HudCorners colorClass="border-cyan-400/60" />
                      <div className="w-full flex items-center justify-between text-[11px] font-mono text-slate-400 mb-2">
                        <span>AST_SYNTAX_TREE</span>
                        <span className="text-cyan-400">24 TOKENS</span>
                      </div>
                      <svg viewBox="0 0 260 150" className="w-full h-36">
                        <g stroke="#22D3EE" strokeWidth="1.5" strokeOpacity="0.65">
                          <line x1="130" y1="24" x2="70" y2="74" />
                          <line x1="130" y1="24" x2="190" y2="74" />
                          <line x1="70" y1="74" x2="40" y2="126" />
                          <line x1="70" y1="74" x2="100" y2="126" />
                          <line x1="190" y1="74" x2="160" y2="126" />
                          <line x1="190" y1="74" x2="220" y2="126" />
                        </g>
                        {/* Glowing AST Nodes */}
                        <circle cx="130" cy="24" r="9" fill="#2563EB" stroke="#22D3EE" strokeWidth="2" />
                        <circle cx="70" cy="74" r="7" fill="#0F172A" stroke="#22D3EE" strokeWidth="2" />
                        <circle cx="190" cy="74" r="7" fill="#0F172A" stroke="#6366F1" strokeWidth="2" />
                        <circle cx="40" cy="126" r="6" fill="#34D399" />
                        <circle cx="100" cy="126" r="6" fill="#34D399" />
                        <circle cx="160" cy="126" r="6" fill="#22D3EE" />
                        <circle cx="220" cy="126" r="6" fill="#34D399" />
                      </svg>
                    </div>

                    {/* Right: Automated Test Case Execution Board */}
                    <div className="relative rounded-xl bg-slate-900/75 border border-slate-700/60 backdrop-blur-xl p-4 flex flex-col justify-between">
                      <HudCorners colorClass="border-blue-400/60" />
                      <div className="flex items-center justify-between border-b border-slate-800 pb-2 mb-2 font-mono text-xs">
                        <span className="text-slate-300">SANDBOX RUNNER</span>
                        <span className="text-emerald-400 font-bold tabular-nums">{scoreValue} / 100 PTS</span>
                      </div>
                      <div className="space-y-1.5 font-mono text-xs">
                        {[
                          { id: 'TC-01', label: 'Base Array Sort', pass: true, time: '12ms' },
                          { id: 'TC-02', label: 'Large N=10^5Input', pass: true, time: '38ms' },
                          { id: 'TC-03', label: 'Duplicate Elements', pass: true, time: '15ms' },
                          { id: 'TC-04', label: 'Negative Overflow', pass: false, time: '44ms' },
                          { id: 'TC-05', label: 'Reverse Sorted Edge', pass: true, time: '19ms' },
                        ].map((tc) => (
                          <div
                            key={tc.id}
                            className="flex items-center justify-between px-2.5 py-1 rounded bg-slate-950/70 border border-slate-800/80"
                          >
                            <div className="flex items-center gap-2">
                              <span
                                className={
                                  tc.pass ? 'text-emerald-400 font-bold' : 'text-rose-400 font-bold'
                                }
                              >
                                {tc.pass ? '✓' : '✗'}
                              </span>
                              <span className="text-slate-300">{tc.id}</span>
                              <span className="text-slate-500 text-[11px] hidden sm:inline">
                                {tc.label}
                              </span>
                            </div>
                            <span className="text-slate-400 text-[11px]">{tc.time}</span>
                          </div>
                        ))}
                      </div>
                    </div>
                  </div>

                  {/* Scene 2 Caption */}
                  <div className="text-center">
                    <div className="font-mono text-[11px] uppercase tracking-[0.25em] text-cyan-400">
                      STAGE 02 // EXECUTION &amp; PARSING
                    </div>
                    <h2 className="text-xl sm:text-2xl font-semibold text-white tracking-tight mt-1">
                      AST Analysis &amp; Automated Grading
                    </h2>
                  </div>
                </div>
              )}

              {/* SCENE 3 — PLAGIARISM DETECTION (4.4 - 6.2s) */}
              {scene === 3 && (
                <div className="w-full flex flex-col items-center gap-6 animate-in fade-in duration-500">
                  <div className="relative w-full max-w-2xl rounded-xl bg-slate-900/75 border border-slate-700/60 backdrop-blur-xl p-5 flex flex-col md:flex-row items-center justify-between gap-6">
                    <HudCorners colorClass="border-rose-400/60" />

                    {/* Dual AST Comparison with Connecting Energy Curves */}
                    <div className="flex-1 w-full">
                      <div className="flex items-center justify-between text-[11px] font-mono text-slate-400 mb-2">
                        <span>SUBMISSION_A (SE1801)</span>
                        <span>SUBMISSION_B (SE1842)</span>
                      </div>
                      <svg viewBox="0 0 320 140" className="w-full h-32">
                        {/* Left AST */}
                        <g stroke="#22D3EE" strokeWidth="1.5">
                          <line x1="65" y1="22" x2="35" y2="70" />
                          <line x1="65" y1="22" x2="95" y2="70" />
                          <line x1="35" y1="70" x2="55" y2="115" />
                        </g>
                        {/* Right AST */}
                        <g stroke="#F43F5E" strokeWidth="1.5">
                          <line x1="255" y1="22" x2="225" y2="70" />
                          <line x1="255" y1="22" x2="285" y2="70" />
                          <line x1="225" y1="70" x2="245" y2="115" />
                        </g>
                        {/* Energy Arcs connecting isomorphic AST nodes */}
                        <path
                          d="M 65 22 Q 160 -8 255 22"
                          stroke="#F43F5E"
                          strokeWidth="1.75"
                          strokeDasharray="5 4"
                          fill="none"
                        />
                        <path
                          d="M 95 70 Q 160 45 285 70"
                          stroke="#F59E0B"
                          strokeWidth="1.75"
                          strokeDasharray="5 4"
                          fill="none"
                        />
                        <path
                          d="M 55 115 Q 150 95 245 115"
                          stroke="#F43F5E"
                          strokeWidth="1.75"
                          strokeDasharray="5 4"
                          fill="none"
                        />
                        {/* Nodes */}
                        <circle cx="65" cy="22" r="6" fill="#22D3EE" />
                        <circle cx="35" cy="70" r="5" fill="#22D3EE" />
                        <circle cx="95" cy="70" r="6" fill="#F59E0B" />
                        <circle cx="55" cy="115" r="6" fill="#F43F5E" />

                        <circle cx="255" cy="22" r="6" fill="#F43F5E" />
                        <circle cx="225" cy="70" r="5" fill="#22D3EE" />
                        <circle cx="285" cy="70" r="6" fill="#F59E0B" />
                        <circle cx="245" cy="115" r="6" fill="#F43F5E" />
                      </svg>
                    </div>

                    {/* Circular Similarity Gauge (0 -> 87%) */}
                    <div className="flex flex-col items-center justify-center shrink-0">
                      <div className="relative w-32 h-32 flex items-center justify-center">
                        <svg viewBox="0 0 120 120" className="w-full h-full -rotate-90">
                          <circle
                            cx="60"
                            cy="60"
                            r="52"
                            stroke="rgba(148,163,184,0.18)"
                            strokeWidth="8"
                            fill="none"
                          />
                          <circle
                            cx="60"
                            cy="60"
                            r="52"
                            stroke={gaugeColor}
                            strokeWidth="8"
                            strokeLinecap="round"
                            strokeDasharray={gaugeCircumference}
                            strokeDashoffset={gaugeDashoffset}
                            fill="none"
                          />
                        </svg>
                        <div className="absolute inset-0 flex flex-col items-center justify-center">
                          <span
                            className="font-mono text-2xl font-bold tabular-nums"
                            style={{ color: gaugeColor }}
                          >
                            {similarityPct}%
                          </span>
                          <span className="font-mono text-[9px] uppercase tracking-wider text-slate-400">
                            SIMILARITY
                          </span>
                        </div>
                      </div>
                      <span className="mt-2 px-2.5 py-0.5 rounded-full text-[10px] font-mono font-bold tracking-wider bg-rose-500/20 text-rose-300 border border-rose-500/50 shadow-[0_0_16px_rgba(244,63,94,0.35)]">
                        HIGH SIMILARITY
                      </span>
                    </div>
                  </div>

                  {/* Scene 3 Caption */}
                  <div className="text-center">
                    <div className="font-mono text-[11px] uppercase tracking-[0.25em] text-rose-400">
                      STAGE 03 // FORENSICS ENGINE
                    </div>
                    <h2 className="text-xl sm:text-2xl font-semibold text-white tracking-tight mt-1">
                      Academic Integrity &amp; Plagiarism Detection
                    </h2>
                  </div>
                </div>
              )}

              {/* SCENE 4 — CONVERGE TO LOGIN (6.2 - 7.2s) */}
              {scene === 4 && (
                <div className="relative flex items-center justify-center py-20">
                  <div
                    className="absolute w-56 h-56 rounded-full border-2 border-cyan-400/60"
                    style={{ animation: 'nc-shockwave 900ms cubic-bezier(0.22, 1, 0.36, 1) forwards' }}
                  />
                  <div className="scale-125 opacity-40 transition-all duration-500">
                    <NeuralLogo className="w-56 h-56" />
                  </div>
                </div>
              )}
            </div>
          )}

          {/* Skip Button (Bottom-Right, Glassmorphic, Keyboard Focusable) */}
          <button
            type="button"
            onClick={() => finishIntro(true)}
            data-testid="skip-intro-btn"
            className="fixed bottom-6 right-6 z-50 inline-flex items-center gap-2 px-3.5 py-2 rounded-xl bg-slate-900/75 hover:bg-slate-800/90 text-slate-200 hover:text-white border border-slate-700/70 hover:border-cyan-400/50 backdrop-blur-xl text-xs font-medium shadow-lg transition-all cursor-pointer focus:outline-none focus:ring-2 focus:ring-cyan-400"
          >
            <span>Bỏ qua / Skip</span>
            <kbd className="px-1.5 py-0.5 rounded bg-slate-800 border border-slate-600/70 font-mono text-[10px] text-cyan-300">
              Esc
            </kbd>
          </button>
        </div>
      )}

      {/* Replay Intro Button (Bottom-Left on Login screen when intro is completed) */}
      {status === 'completed' && (
        <button
          type="button"
          onClick={handleReplayIntro}
          data-testid="replay-intro-btn"
          title="Xem lại hoạt cảnh giới thiệu hệ thống"
          className="fixed bottom-5 left-5 z-30 inline-flex items-center gap-2 px-3 py-1.5 rounded-xl bg-slate-900/65 hover:bg-slate-800/85 text-slate-300 hover:text-cyan-300 border border-slate-700/60 hover:border-cyan-400/45 backdrop-blur-xl text-xs font-medium shadow-md transition-all cursor-pointer focus:outline-none focus:ring-2 focus:ring-cyan-400"
        >
          <RotateCcw className="w-3.5 h-3.5 text-cyan-400" />
          <span>Xem lại Intro</span>
        </button>
      )}

      {/* Underlying Login Content — Always mounted in DOM for zero hydration mismatch */}
      <div
        className={`w-full flex flex-col items-center justify-center transition-all duration-700 ${status === 'completed'
            ? 'opacity-100 translate-y-0 scale-100'
            : 'opacity-0 translate-y-4 scale-[0.98] pointer-events-none'
          }`}
      >
        {children}
      </div>
    </div>
  );
}
