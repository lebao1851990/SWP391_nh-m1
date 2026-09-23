'use client';

import React from 'react';
import { useAdminUser } from '../hooks';
import { AppLink as Link } from '@/components/ui/app-link';

export function AdminUserDetailView({ userId }: { userId: string }) {
  const { data: user, isLoading, isError } = useAdminUser(userId);

  return (
    <div className="flex flex-col w-full gap-space-lg px-space-lg py-space-lg max-w-4xl mx-auto">
      <div className="flex items-center gap-space-sm pb-space-xs border-b border-outline-variant/30">
        <Link href="/admin/users" className="text-on-surface-variant hover:text-on-surface flex items-center">
          <span className="material-symbols-outlined text-[20px]">arrow_back</span>
        </Link>
        <h1 className="font-display-sm text-display-sm tracking-tight text-on-surface font-bold">User Details</h1>
      </div>

      {isLoading ? (
        <div className="p-10 flex justify-center text-on-surface-variant">
          <span className="material-symbols-outlined animate-spin text-[32px]">progress_activity</span>
        </div>
      ) : isError ? (
        <div className="p-10 flex flex-col items-center justify-center text-center bg-surface-container-lowest rounded-xl shadow-sm border border-error/20">
          <span className="material-symbols-outlined text-[48px] text-error mb-2">error</span>
          <span className="font-headline-sm font-semibold text-on-surface">Integration Pending</span>
          <span className="font-body-sm text-on-surface-variant">Backend API is currently unavailable.</span>
        </div>
      ) : user ? (
        <div className="bg-surface-container-lowest rounded-xl shadow-sm border border-outline-variant/30 p-space-lg flex flex-col gap-space-md">
          <div className="flex items-center gap-space-md pb-space-md border-b border-outline-variant/30">
            <div className="w-16 h-16 rounded-full bg-primary flex items-center justify-center text-on-primary font-display-sm">
              {user.name.charAt(0)}
            </div>
            <div className="flex flex-col">
              <span className="font-headline-md font-bold text-on-surface">{user.name}</span>
              <span className="font-body-md text-on-surface-variant">{user.email}</span>
            </div>
          </div>
          
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-space-md">
            <div className="flex flex-col gap-1">
              <span className="font-label-sm text-label-sm uppercase text-outline">User ID</span>
              <span className="font-code-body font-mono text-on-surface">{user.id}</span>
            </div>
            <div className="flex flex-col gap-1">
              <span className="font-label-sm text-label-sm uppercase text-outline">System Role</span>
              <div>
                <span className="px-2 py-0.5 rounded bg-primary-container text-on-primary-container font-label-sm text-label-sm font-medium font-mono">
                  {user.role}
                </span>
              </div>
            </div>
            <div className="flex flex-col gap-1">
              <span className="font-label-sm text-label-sm uppercase text-outline">Department</span>
              <span className="font-body-md text-on-surface">{user.department || 'Unassigned'}</span>
            </div>
            <div className="flex flex-col gap-1">
              <span className="font-label-sm text-label-sm uppercase text-outline">Joined</span>
              <span className="font-body-md text-on-surface">{new Date(user.createdAt).toLocaleDateString()}</span>
            </div>
          </div>
          
          <div className="mt-space-lg flex justify-end gap-space-sm">
            <button className="px-4 py-2 rounded-lg bg-surface-container-high text-on-surface font-label-md font-semibold hover:bg-surface-container-highest transition-colors">
              Reset Password
            </button>
            <button className="px-4 py-2 rounded-lg bg-primary text-on-primary font-label-md font-semibold hover:bg-primary-container transition-colors">
              Edit User
            </button>
          </div>
        </div>
      ) : null}
    </div>
  );
}
