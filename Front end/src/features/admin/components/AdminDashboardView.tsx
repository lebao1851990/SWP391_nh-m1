'use client';

import React from 'react';
import { useAdminDashboardMetrics } from '../hooks';
import { AppLink as Link } from '@/components/ui/app-link';

export function AdminDashboardView() {
  const { data, isLoading, isError, error } = useAdminDashboardMetrics();

  return (
    <div className="flex flex-col w-full gap-space-lg px-space-lg py-space-lg">
      {/* Header Section */}
      <div className="flex flex-col xl:flex-row xl:items-center justify-between gap-space-md pb-space-xs">
        <div className="flex flex-col gap-1">
          <div className="flex items-center gap-space-sm">
            <h1 className="font-display-lg text-display-lg tracking-tight text-on-surface font-bold">Admin Dashboard</h1>
            <span className="px-2.5 py-0.5 rounded-full bg-surface-container-high text-primary font-label-sm text-label-sm uppercase tracking-wider font-semibold">AY 2025–2026</span>
          </div>
          <p className="font-body-md text-body-md text-on-surface-variant">Master Data & System Operations Overview.</p>
        </div>
        
        {/* Actions Toolbar */}
        <div className="flex flex-wrap items-center gap-space-xs">
          <Link href="/admin/users" className="flex items-center gap-1.5 px-3 py-2 rounded-lg bg-primary text-on-primary hover:bg-primary-container transition-colors font-label-md text-label-md font-semibold shadow-sm">
            <span className="material-symbols-outlined text-[18px]">manage_accounts</span>
            <span>Manage Users</span>
          </Link>
          <Link href="/admin/classes" className="flex items-center gap-1.5 px-3 py-2 rounded-lg bg-surface-container text-primary hover:bg-surface-container-high transition-colors font-label-md text-label-md font-semibold">
            <span className="material-symbols-outlined text-[18px]">class</span>
            <span>Manage Classes</span>
          </Link>
          <Link href="/admin/exams" className="flex items-center gap-1.5 px-3 py-2 rounded-lg bg-surface-container text-primary hover:bg-surface-container-high transition-colors font-label-md text-label-md font-semibold">
            <span className="material-symbols-outlined text-[18px]">grading</span>
            <span>Manage Exams</span>
          </Link>
        </div>
      </div>

      {isLoading ? (
        <div className="flex flex-col items-center justify-center py-20 text-on-surface-variant">
          <span className="material-symbols-outlined text-[32px] animate-spin mb-4">progress_activity</span>
          <p className="font-body-md text-body-md">Loading metrics...</p>
        </div>
      ) : isError ? (
        <div className="flex flex-col items-center justify-center py-20 bg-surface-container-lowest rounded-xl shadow-sm border border-error/20 text-center">
          <span className="material-symbols-outlined text-[48px] text-error mb-4">cloud_off</span>
          <h2 className="font-headline-sm text-headline-sm font-semibold text-on-surface">Integration Pending</h2>
          <p className="font-body-md text-body-md text-on-surface-variant mt-2 max-w-md">
            Backend API integration for Admin Dashboard metrics is currently unavailable.
          </p>
        </div>
      ) : data ? (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-space-md">
          {/* Real data cards would go here based on Stitch UI, using `data` */}
          <div className="flex flex-col justify-between p-space-md rounded-xl bg-surface-container-lowest shadow-sm">
            <div className="flex items-start justify-between">
              <div className="flex flex-col">
                <span className="font-label-sm text-label-sm uppercase tracking-wider text-on-surface-variant font-semibold">Total Users</span>
                <span className="font-display-lg text-display-lg tracking-tight text-on-surface font-bold mt-1">{data.totalUsers}</span>
              </div>
              <div className="w-10 h-10 rounded-lg bg-surface-container flex items-center justify-center text-primary">
                <span className="material-symbols-outlined text-[22px]">group</span>
              </div>
            </div>
          </div>
          {/* Other metric cards ... */}
        </div>
      ) : (
        <div className="flex flex-col items-center justify-center py-20 bg-surface-container-lowest rounded-xl shadow-sm text-center">
          <span className="material-symbols-outlined text-[48px] text-on-surface-variant mb-4">inbox</span>
          <h2 className="font-headline-sm text-headline-sm font-semibold text-on-surface">No Data Available</h2>
        </div>
      )}

      {/* Quick Navigation Banner: Master Data Modules */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-space-md mt-space-lg">
        <Link href="/admin/users" className="group flex items-center justify-between p-space-md rounded-xl bg-surface-container-lowest hover:bg-surface-container-low shadow-sm transition-all duration-200">
          <div className="flex items-center gap-space-sm">
            <div className="w-10 h-10 rounded-lg bg-surface-container group-hover:bg-primary group-hover:text-on-primary text-primary flex items-center justify-center transition-colors">
              <span className="material-symbols-outlined text-[20px]">manage_accounts</span>
            </div>
            <div className="flex flex-col">
              <span className="font-headline-sm text-body-md font-semibold text-on-surface group-hover:text-primary transition-colors">Manage Users</span>
            </div>
          </div>
          <span className="material-symbols-outlined text-[18px] text-on-surface-variant group-hover:translate-x-1 group-hover:text-primary transition-all">arrow_forward</span>
        </Link>
        <Link href="/admin/classes" className="group flex items-center justify-between p-space-md rounded-xl bg-surface-container-lowest hover:bg-surface-container-low shadow-sm transition-all duration-200">
          <div className="flex items-center gap-space-sm">
            <div className="w-10 h-10 rounded-lg bg-surface-container group-hover:bg-primary group-hover:text-on-primary text-primary flex items-center justify-center transition-colors">
              <span className="material-symbols-outlined text-[20px]">class</span>
            </div>
            <div className="flex flex-col">
              <span className="font-headline-sm text-body-md font-semibold text-on-surface group-hover:text-primary transition-colors">Manage Classes</span>
            </div>
          </div>
          <span className="material-symbols-outlined text-[18px] text-on-surface-variant group-hover:translate-x-1 group-hover:text-primary transition-all">arrow_forward</span>
        </Link>
        <Link href="/admin/exams" className="group flex items-center justify-between p-space-md rounded-xl bg-surface-container-lowest hover:bg-surface-container-low shadow-sm transition-all duration-200">
          <div className="flex items-center gap-space-sm">
            <div className="w-10 h-10 rounded-lg bg-surface-container group-hover:bg-primary group-hover:text-on-primary text-primary flex items-center justify-center transition-colors">
              <span className="material-symbols-outlined text-[20px]">grading</span>
            </div>
            <div className="flex flex-col">
              <span className="font-headline-sm text-body-md font-semibold text-on-surface group-hover:text-primary transition-colors">Manage Exams</span>
            </div>
          </div>
          <span className="material-symbols-outlined text-[18px] text-on-surface-variant group-hover:translate-x-1 group-hover:text-primary transition-all">arrow_forward</span>
        </Link>
      </div>

    </div>
  );
}
