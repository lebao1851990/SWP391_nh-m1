'use client';

import React from 'react';

interface NeuralLogoProps {
  className?: string;
  showChromAberration?: boolean;
  showLaserSweep?: boolean;
}

/**
 * Futuristic CodeAssess "CA" Neural Command Center Emblem.
 * Pure SVG with AST node geometry, hexagonal core, chromatic aberration, and laser sweep.
 */
export function NeuralLogo({
  className = 'w-64 h-64',
  showChromAberration = true,
  showLaserSweep = true,
}: NeuralLogoProps) {
  const renderMark = (strokeColor: string, idSuffix: string) => (
    <svg
      viewBox="0 0 320 320"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      className="w-full h-full"
      aria-hidden="true"
    >
      <defs>
        <linearGradient id={`nc-grad-${idSuffix}`} x1="30" y1="30" x2="290" y2="290" gradientUnits="userSpaceOnUse">
          <stop offset="0%" stopColor="#2563EB" />
          <stop offset="50%" stopColor="#22D3EE" />
          <stop offset="100%" stopColor="#6366F1" />
        </linearGradient>
        <radialGradient id={`nc-core-${idSuffix}`} cx="50%" cy="50%" r="50%">
          <stop offset="0%" stopColor="#22D3EE" stopOpacity="0.32" />
          <stop offset="60%" stopColor="#2563EB" stopOpacity="0.08" />
          <stop offset="100%" stopColor="#010409" stopOpacity="0" />
        </radialGradient>
      </defs>

      {/* Ambient Core Radial Glow */}
      <circle cx="160" cy="160" r="140" fill={`url(#nc-core-${idSuffix})`} />

      {/* Outer Hexagonal AST Shield */}
      <polygon
        points="160,22 280,91 280,229 160,298 40,229 40,91"
        stroke={strokeColor || `url(#nc-grad-${idSuffix})`}
        strokeWidth="2"
        strokeOpacity="0.65"
        fill="rgba(11, 18, 32, 0.28)"
      />

      {/* Inner Precision Hexagon */}
      <polygon
        points="160,44 260,102 260,218 160,276 60,218 60,102"
        stroke={strokeColor || `url(#nc-grad-${idSuffix})`}
        strokeWidth="1"
        strokeDasharray="8 6"
        strokeOpacity="0.45"
      />

      {/* AST Circuit Traces */}
      <g stroke={strokeColor || '#22D3EE'} strokeWidth="1.25" strokeOpacity="0.45">
        <line x1="160" y1="22" x2="160" y2="58" />
        <line x1="160" y1="262" x2="160" y2="298" />
        <line x1="40" y1="91" x2="72" y2="110" />
        <line x1="280" y1="91" x2="248" y2="110" />
        <line x1="40" y1="229" x2="72" y2="210" />
        <line x1="280" y1="229" x2="248" y2="210" />
      </g>

      {/* Monogram "C" — Futuristic Geometric Arc */}
      <path
        d="M 152 106 L 106 106 L 84 128 L 84 192 L 106 214 L 152 214"
        stroke={strokeColor || `url(#nc-grad-${idSuffix})`}
        strokeWidth="12"
        strokeLinecap="square"
        strokeLinejoin="miter"
      />

      {/* Monogram "A" — Futuristic Angular Apex with AST Crossbar Node */}
      <path
        d="M 168 214 L 200 106 L 236 214"
        stroke={strokeColor || `url(#nc-grad-${idSuffix})`}
        strokeWidth="12"
        strokeLinecap="square"
        strokeLinejoin="bevel"
      />
      <line
        x1="180"
        y1="176"
        x2="224"
        y2="176"
        stroke={strokeColor || '#22D3EE'}
        strokeWidth="9"
        strokeLinecap="round"
      />

      {/* AST Syntax Nodes on the Emblem */}
      <circle cx="152" cy="106" r="5" fill="#22D3EE" />
      <circle cx="152" cy="214" r="5" fill="#22D3EE" />
      <circle cx="200" cy="106" r="5.5" fill="#38BDF8" />
      <circle cx="202" cy="176" r="4.5" fill="#A855F7" />
      <circle cx="160" cy="22" r="4" fill="#22D3EE" />
      <circle cx="160" cy="298" r="4" fill="#6366F1" />
    </svg>
  );

  return (
    <div className={`relative select-none pointer-events-none ${className}`} aria-hidden="true">
      {/* Chromatic Aberration Cyan Offset (-1.5px, -1px) */}
      {showChromAberration && (
        <div
          className="absolute inset-0 opacity-35 mix-blend-screen"
          style={{ transform: 'translate3d(-1.5px, -1px, 0)' }}
        >
          {renderMark('#22D3EE', 'cyan')}
        </div>
      )}

      {/* Chromatic Aberration Magenta Offset (+1.5px, +1px) */}
      {showChromAberration && (
        <div
          className="absolute inset-0 opacity-30 mix-blend-screen"
          style={{ transform: 'translate3d(1.5px, 1px, 0)' }}
        >
          {renderMark('#A855F7', 'magenta')}
        </div>
      )}

      {/* Primary Crisp Vector Layer */}
      <div className="relative inset-0 w-full h-full drop-shadow-[0_0_32px_rgba(34,211,238,0.35)]">
        {renderMark('', 'main')}
      </div>

      {/* Periodic Laser Scan Beam (every ~8s) */}
      {showLaserSweep && (
        <div className="absolute inset-0 overflow-hidden rounded-full pointer-events-none">
          <div
            className="nc-anim-sweep w-[140%] h-8 -left-[20%] bg-gradient-to-b from-transparent via-cyan-300/25 to-transparent blur-[2px]"
            style={{ animation: 'nc-laser-sweep 8s cubic-bezier(0.22, 1, 0.36, 1) infinite' }}
          />
        </div>
      )}
    </div>
  );
}

