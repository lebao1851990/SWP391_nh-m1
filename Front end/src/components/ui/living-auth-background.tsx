'use client';

import React from 'react';
import dynamic from 'next/dynamic';
import { NeuralLogo } from './neural-logo';

const ParticleNetworkCanvas = dynamic(() => import('./particle-network-canvas'), {
  ssr: false,
});

/**
 * Multi-layered "Living Logo" Background for Authentication screens.
 * Strictly pointer-events-none and aria-hidden="true".
 *
 * Layer Stack (bottom -> top):
 * 1. Void navy (#010409 -> #020617)
 * 2. Aurora mesh + Blueprint grid with radial mask & horizontal energy pulses
 * 3. 2D Canvas Particle Network (40-65 nodes, cursor parallax <= 10px)
 * 4. Orbital Rings (3 concentric counter-rotating rings, 45s-85s, with glowing satellites)
 * 5. Large Animated Neural Logo (~68vmin, opacity 0.22-0.32, breathing + chromatic aberration + laser sweep)
 * 6. Dark Edge Vignette pushing focus to the center Login Card
 */
export function LivingAuthBackground() {
  return (
    <div
      className="fixed inset-0 min-h-dvh w-full overflow-hidden pointer-events-none select-none z-0 bg-[#010409]"
      aria-hidden="true"
    >
      {/* Layer 1 & 2: Deep Void + Aurora Mesh Blobs */}
      <div
        className="nc-anim-aurora absolute -top-[18%] -left-[10%] w-[55vw] h-[55vw] max-w-[680px] max-h-[680px] rounded-full opacity-25 blur-[110px]"
        style={{
          background: 'radial-gradient(circle, #2563EB 0%, rgba(34,211,238,0.35) 55%, transparent 75%)',
          animation: 'nc-aurora-drift 26s ease-in-out infinite',
        }}
      />
      <div
        className="nc-anim-aurora absolute -bottom-[20%] -right-[10%] w-[55vw] h-[55vw] max-w-[680px] max-h-[680px] rounded-full opacity-20 blur-[120px]"
        style={{
          background: 'radial-gradient(circle, #6366F1 0%, rgba(168,85,247,0.3) 50%, transparent 75%)',
          animation: 'nc-aurora-drift 34s ease-in-out infinite reverse',
        }}
      />

      {/* Layer 2B: Blueprint Grid + Periodic Energy Pulses */}
      <div className="absolute inset-0 nc-grid-blueprint opacity-85" />
      <div className="absolute inset-x-0 top-1/3 h-[1px] overflow-hidden opacity-40">
        <div
          className="w-64 h-full bg-gradient-to-r from-transparent via-cyan-400 to-transparent"
          style={{ animation: 'nc-grid-pulse 9s cubic-bezier(0.22, 1, 0.36, 1) infinite' }}
        />
      </div>
      <div className="absolute inset-x-0 top-2/3 h-[1px] overflow-hidden opacity-35">
        <div
          className="w-72 h-full bg-gradient-to-r from-transparent via-blue-500 to-transparent"
          style={{ animation: 'nc-grid-pulse 13s cubic-bezier(0.22, 1, 0.36, 1) infinite 3s' }}
        />
      </div>

      {/* Layer 3: 2D Canvas Particle Field */}
      <ParticleNetworkCanvas />

      {/* Layer 4 & 5: Orbital Rings + Large Living Logo behind the card */}
      <div className="absolute inset-0 flex items-center justify-center z-[3]">
        {/* Outer Orbital Ring 1 (84vmin, clockwise 80s) */}
        <div
          className="nc-anim-orbit-cw absolute w-[84vmin] h-[84vmin] max-w-[820px] max-h-[820px] rounded-full border border-dashed border-cyan-400/15"
          style={{ animation: 'nc-orbit-cw 80s linear infinite' }}
        >
          <span className="absolute -top-1.5 left-1/2 -translate-x-1/2 w-3 h-3 rounded-full bg-cyan-400 shadow-[0_0_14px_#22D3EE]" />
          <span className="absolute -bottom-1 left-1/2 -translate-x-1/2 w-2 h-2 rounded-full bg-blue-500/80 shadow-[0_0_10px_#2563EB]" />
        </div>

        {/* Middle Orbital Ring 2 (68vmin, counter-clockwise 56s) */}
        <div
          className="nc-anim-orbit-ccw absolute w-[68vmin] h-[68vmin] max-w-[660px] max-h-[660px] rounded-full border border-blue-500/20"
          style={{ animation: 'nc-orbit-ccw 56s linear infinite' }}
        >
          <span className="absolute top-1/2 -right-1.5 -translate-y-1/2 w-2.5 h-2.5 rounded-full bg-indigo-400 shadow-[0_0_12px_#6366F1]" />
          <span className="absolute top-1/2 -left-1 -translate-y-1/2 w-2 h-2 rounded-full bg-cyan-300/80 shadow-[0_0_10px_#22D3EE]" />
        </div>

        {/* Inner Orbital Ring 3 (52vmin, clockwise 42s) */}
        <div
          className="nc-anim-orbit-cw absolute w-[52vmin] h-[52vmin] max-w-[500px] max-h-[500px] rounded-full border border-dashed border-indigo-400/20"
          style={{ animation: 'nc-orbit-cw 42s linear infinite' }}
        >
          <span className="absolute -top-1 left-1/2 -translate-x-1/2 w-2 h-2 rounded-full bg-cyan-300 shadow-[0_0_10px_#22D3EE]" />
        </div>

        {/* Layer 5: Animated Living Logo (64vmin, opacity ~0.24-0.34, breathing) */}
        <div
          className="nc-anim-float relative flex items-center justify-center w-[64vmin] h-[64vmin] max-w-[600px] max-h-[600px] opacity-25"
          style={{ animation: 'nc-float-breathe 12s ease-in-out infinite' }}
        >
          <NeuralLogo className="w-full h-full" showChromAberration showLaserSweep />
        </div>
      </div>

      {/* Subtle Scanlines */}
      <div className="absolute inset-0 nc-scanlines z-[4]" />

      {/* Layer 6: Dark Edge Vignette to spotlight the central Login Card */}
      <div
        className="absolute inset-0 z-[5]"
        style={{
          background:
            'radial-gradient(circle at 50% 50%, rgba(1,4,9,0.12) 0%, rgba(1,4,9,0.58) 65%, rgba(1,4,9,0.92) 100%)',
        }}
      />
    </div>
  );
}

