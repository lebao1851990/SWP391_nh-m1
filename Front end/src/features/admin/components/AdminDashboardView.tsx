'use client';

import React from 'react';
import { useAdminDashboardMetrics } from '../hooks';
import { AppLink as Link } from '@/components/ui/app-link';
import {
  HudCorners,
  AnimatedCounter,
  SpotlightCard,
  SystemStatusBar,
  RadarEmptyState,
} from '@/components/ui/hud-card';

export function AdminDashboardView() {
  const { data, isLoading, isError } = useAdminDashboardMetrics();

  return (
    <div className="relative z-10 flex flex-col w-full max-w-7xl mx-auto gap-space-lg px-4 sm:px-space-lg py-space-lg">
      {/* Header Section */}
      <div className="flex flex-col xl:flex-row xl:items-center justify-between gap-space-md pb-space-xs">
        <div className="flex flex-col gap-1.5">
          <div className="flex flex-wrap items-center gap-2 text-xs">
            <span className="text-[11px] text-[#2563EB] font-semibold uppercase tracking-wide px-2 py-0.5 rounded-md bg-blue-50 border border-blue-200/70">
              Không gian Quản trị
            </span>
            <span className="text-slate-400 font-mono">/</span>
            <span className="font-mono text-[11px] text-slate-500 font-semibold">MASTER_CONTROL</span>
            <SystemStatusBar moduleLabel="ADMIN_CORE_V2" />
          </div>
          <div className="flex items-center gap-space-sm">
            <h1 className="font-display-lg text-display-lg tracking-tight text-on-surface font-bold">Admin Dashboard</h1>
            <span className="px-2.5 py-0.5 rounded-full bg-blue-50 border border-blue-200/70 text-[#2563EB] font-mono text-[11px] uppercase tracking-wider font-semibold">
              AY 2025–2026
            </span>
          </div>
          <p className="font-body-md text-body-md text-on-surface-variant">Master Data &amp; System Operations Overview.</p>
        </div>

        {/* Actions Toolbar */}
        <div className="flex flex-wrap items-center gap-space-xs">
          <Link href="/admin/users" className="nc-btn-shimmer flex items-center gap-1.5 px-3.5 py-2 rounded-lg bg-gradient-to-r from-[#2563EB] via-[#0284C7] to-[#6366F1] text-white hover:brightness-110 transition-all font-label-md text-label-md font-semibold shadow-[0_4px_14px_rgba(37,99,235,0.28)]">
            <span className="material-symbols-outlined text-[18px]">manage_accounts</span>
            <span>Manage Users</span>
          </Link>
          <Link href="/admin/classes" className="nc-quick-pill flex items-center gap-1.5 px-3 py-2 rounded-lg bg-white/95 border border-slate-200/90 text-[#2563EB] hover:bg-blue-50/70 transition-all font-label-md text-label-md font-semibold">
            <span className="material-symbols-outlined text-[18px]">class</span>
            <span>Manage Classes</span>
          </Link>
          <Link href="/admin/exams" className="nc-quick-pill flex items-center gap-1.5 px-3 py-2 rounded-lg bg-white/95 border border-slate-200/90 text-[#2563EB] hover:bg-blue-50/70 transition-all font-label-md text-label-md font-semibold">
            <span className="material-symbols-outlined text-[18px]">grading</span>
            <span>Manage Exams</span>
          </Link>
        </div>
      </div>

      {isLoading ? (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-space-md">
          {[1, 2, 3, 4].map((idx) => (
            <div key={idx} className="nc-skeleton h-28 rounded-xl" />
          ))}
        </div>
      ) : isError ? (
        <SpotlightCard className="flex flex-col items-center justify-center py-16 bg-white/95 backdrop-blur-md rounded-xl shadow-xs border border-rose-200/70 text-center">
          <HudCorners colorClass="border-rose-400/45" />
          <span className="material-symbols-outlined text-[48px] text-error mb-4">cloud_off</span>
          <h2 className="font-headline-sm text-headline-sm font-semibold text-on-surface">Integration Pending</h2>
          <p className="font-body-md text-body-md text-on-surface-variant mt-2 max-w-md">
            Backend API integration for Admin Dashboard metrics is currently unavailable.
          </p>
        </SpotlightCard>
      ) : data ? (
        <div className="nc-boot-stagger grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-space-md">
          <div className="nc-kpi-v2 flex flex-col justify-between p-4 rounded-xl bg-white/95 backdrop-blur-md border border-blue-200/75 shadow-[0_2px_10px_-2px_rgba(37,99,235,0.07)]">
            <HudCorners colorClass="border-blue-500/45" />
            <div className="flex items-start justify-between">
              <div className="flex flex-col">
                <span className="text-[11px] uppercase tracking-wide text-slate-600 font-semibold">Total Users</span>
                <span className="font-mono text-3xl tracking-tight text-on-surface font-bold mt-2">
                  <AnimatedCounter value={data.totalUsers} />
                </span>
              </div>
              <div className="w-10 h-10 rounded-lg bg-blue-50 border border-blue-200/70 flex items-center justify-center text-[#2563EB]">
                <span className="material-symbols-outlined text-[22px]">group</span>
              </div>
            </div>
          </div>
        </div>
      ) : (
        <div className="bg-white/95 rounded-xl border border-slate-200/80 shadow-xs">
          <RadarEmptyState message="No Data Available" />
        </div>
      )}

      {/* Quick Navigation Banner: Master Data Modules */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-space-md mt-space-sm">
        <Link href="/admin/users" className="nc-holo-card group flex items-center justify-between p-space-md rounded-xl bg-white/95 backdrop-blur-md border border-blue-200/60 shadow-2xs transition-all duration-200">
          <HudCorners colorClass="border-blue-500/35" />
          <div className="flex items-center gap-space-sm">
            <div className="w-10 h-10 rounded-lg bg-blue-50 border border-blue-200/60 group-hover:bg-[#2563EB] group-hover:text-white text-[#2563EB] flex items-center justify-center transition-colors">
              <span className="material-symbols-outlined text-[20px]">manage_accounts</span>
            </div>
            <div className="flex flex-col">
              <span className="font-headline-sm text-body-md font-semibold text-on-surface group-hover:text-[#2563EB] transition-colors">Manage Users</span>
            </div>
          </div>
          <span className="material-symbols-outlined text-[18px] text-on-surface-variant group-hover:translate-x-1 group-hover:text-[#2563EB] transition-all">arrow_forward</span>
        </Link>
        <Link href="/admin/classes" className="nc-holo-card group flex items-center justify-between p-space-md rounded-xl bg-white/95 backdrop-blur-md border border-cyan-200/60 shadow-2xs transition-all duration-200">
          <HudCorners colorClass="border-cyan-500/35" />
          <div className="flex items-center gap-space-sm">
            <div className="w-10 h-10 rounded-lg bg-cyan-50 border border-cyan-200/60 group-hover:bg-[#0284C7] group-hover:text-white text-[#0284C7] flex items-center justify-center transition-colors">
              <span className="material-symbols-outlined text-[20px]">class</span>
            </div>
            <div className="flex flex-col">
              <span className="font-headline-sm text-body-md font-semibold text-on-surface group-hover:text-[#0284C7] transition-colors">Manage Classes</span>
            </div>
          </div>
          <span className="material-symbols-outlined text-[18px] text-on-surface-variant group-hover:translate-x-1 group-hover:text-[#0284C7] transition-all">arrow_forward</span>
        </Link>
        <Link href="/admin/exams" className="nc-holo-card group flex items-center justify-between p-space-md rounded-xl bg-white/95 backdrop-blur-md border border-indigo-200/60 shadow-2xs transition-all duration-200">
          <HudCorners colorClass="border-indigo-500/35" />
          <div className="flex items-center gap-space-sm">
            <div className="w-10 h-10 rounded-lg bg-indigo-50 border border-indigo-200/60 group-hover:bg-[#6366F1] group-hover:text-white text-[#6366F1] flex items-center justify-center transition-colors">
              <span className="material-symbols-outlined text-[20px]">grading</span>
            </div>
            <div className="flex flex-col">
              <span className="font-headline-sm text-body-md font-semibold text-on-surface group-hover:text-[#6366F1] transition-colors">Manage Exams</span>
            </div>
          </div>
          <span className="material-symbols-outlined text-[18px] text-on-surface-variant group-hover:translate-x-1 group-hover:text-[#6366F1] transition-all">arrow_forward</span>
        </Link>
      </div>
    </div>
  );
}
