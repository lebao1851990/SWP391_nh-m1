'use client';

import React, { useEffect, useRef } from 'react';

interface Particle {
  x: number;
  y: number;
  vx: number;
  vy: number;
  radius: number;
  color: string;
  alpha: number;
}

/**
 * High-performance 60fps 2D Canvas Particle Field.
 * - Desktop: ~52 particles; Mobile (<768px): ~24 particles
 * - DPR capped at 2
 * - Pauses automatically on tab hidden (visibilitychange)
 * - Disabled when prefers-reduced-motion: reduce is active
 * - Subtle cursor parallax (<= 10px)
 */
export default function ParticleNetworkCanvas() {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;

    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const reducedMotionQuery = window.matchMedia('(prefers-reduced-motion: reduce)');
    if (reducedMotionQuery.matches) {
      return;
    }

    let animationFrameId = 0;
    let isPaused = document.hidden;
    let width = window.innerWidth;
    let height = window.innerHeight;
    const dpr = Math.min(window.devicePixelRatio || 1, 2);

    const mouse = { x: width / 2, y: height / 2, targetX: width / 2, targetY: height / 2 };

    const setupSize = () => {
      width = window.innerWidth;
      height = window.innerHeight;
      canvas.width = width * dpr;
      canvas.height = height * dpr;
      canvas.style.width = `${width}px`;
      canvas.style.height = `${height}px`;
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    };

    setupSize();

    const isMobile = width < 768;
    const particleCount = isMobile ? 24 : 52;
    const connectionDistance = isMobile ? 110 : 145;
    const palette = ['#22D3EE', '#2563EB', '#6366F1', '#38BDF8'];

    const particles: Particle[] = Array.from({ length: particleCount }, (_, idx) => ({
      x: Math.random() * width,
      y: Math.random() * height,
      vx: (Math.random() - 0.5) * 0.32,
      vy: (Math.random() - 0.5) * 0.32,
      radius: (idx % 3 === 0 ? 1.8 : 1.2),
      color: palette[idx % palette.length],
      alpha: 0.35 + Math.random() * 0.45,
    }));

    const handleMouseMove = (e: MouseEvent) => {
      mouse.targetX = e.clientX;
      mouse.targetY = e.clientY;
    };

    const handleVisibilityChange = () => {
      isPaused = document.hidden;
      if (!isPaused) {
        lastTime = performance.now();
        animationFrameId = requestAnimationFrame(render);
      }
    };

    window.addEventListener('resize', setupSize, { passive: true });
    window.addEventListener('mousemove', handleMouseMove, { passive: true });
    document.addEventListener('visibilitychange', handleVisibilityChange);

    let lastTime = performance.now();
    const frameInterval = 1000 / 60;

    const render = (now: number) => {
      if (isPaused) return;
      animationFrameId = requestAnimationFrame(render);

      const delta = now - lastTime;
      if (delta < frameInterval) return;
      lastTime = now - (delta % frameInterval);

      ctx.clearRect(0, 0, width, height);

      // Smooth mouse interpolation for <= 10px parallax
      mouse.x += (mouse.targetX - mouse.x) * 0.05;
      mouse.y += (mouse.targetY - mouse.y) * 0.05;
      const offsetX = ((mouse.x - width / 2) / Math.max(width, 1)) * 10;
      const offsetY = ((mouse.y - height / 2) / Math.max(height, 1)) * 10;

      for (let i = 0; i < particles.length; i++) {
        const p = particles[i];
        p.x += p.vx;
        p.y += p.vy;

        if (p.x < 0) p.x = width;
        else if (p.x > width) p.x = 0;
        if (p.y < 0) p.y = height;
        else if (p.y > height) p.y = 0;

        const px = p.x + offsetX;
        const py = p.y + offsetY;

        // Draw particle node
        ctx.beginPath();
        ctx.arc(px, py, p.radius, 0, Math.PI * 2);
        ctx.fillStyle = p.color;
        ctx.globalAlpha = p.alpha;
        ctx.fill();

        // Connect nearby nodes
        for (let j = i + 1; j < particles.length; j++) {
          const p2 = particles[j];
          const dx = px - (p2.x + offsetX);
          const dy = py - (p2.y + offsetY);
          const distSq = dx * dx + dy * dy;
          const maxDistSq = connectionDistance * connectionDistance;

          if (distSq < maxDistSq) {
            const ratio = 1 - distSq / maxDistSq;
            ctx.beginPath();
            ctx.moveTo(px, py);
            ctx.lineTo(p2.x + offsetX, p2.y + offsetY);
            ctx.strokeStyle = '#22D3EE';
            ctx.globalAlpha = ratio * 0.16;
            ctx.lineWidth = 0.75;
            ctx.stroke();
          }
        }
      }
      ctx.globalAlpha = 1;
    };

    animationFrameId = requestAnimationFrame(render);

    return () => {
      cancelAnimationFrame(animationFrameId);
      window.removeEventListener('resize', setupSize);
      window.removeEventListener('mousemove', handleMouseMove);
      document.removeEventListener('visibilitychange', handleVisibilityChange);
    };
  }, []);

  return (
    <canvas
      ref={canvasRef}
      className="fixed inset-0 pointer-events-none z-[2]"
      aria-hidden="true"
    />
  );
}

