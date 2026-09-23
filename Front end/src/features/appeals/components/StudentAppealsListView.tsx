'use client';
import React, { useState } from 'react';
import { AppLink as Link } from '@/components/ui/app-link';
import { useStudentAppeals } from '../hooks';

export function StudentAppealsListView() {
  const { data: appeals, isLoading, isError, error } = useStudentAppeals();
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState<string>('ALL');

  if (isLoading) {
    return (
      <div className="flex flex-col w-full h-64 items-center justify-center">
        <span className="material-symbols-outlined text-[32px] text-primary animate-spin">refresh</span>
        <span className="mt-4 font-body-md text-on-surface-variant">Loading Appeals...</span>
      </div>
    );
  }

  if (isError) {
    return (
      <div className="flex flex-col w-full h-64 items-center justify-center text-error">
        <span className="material-symbols-outlined text-[48px] mb-2">error</span>
        <h3 className="font-headline-sm text-headline-sm">Integration Error</h3>
        <p className="font-body-md mt-2">Could not retrieve appeals. The backend may be offline.</p>
        <p className="font-body-sm mt-1 opacity-80">{error?.message}</p>
      </div>
    );
  }

  const safeAppeals = appeals || [];
  
  const filteredAppeals = safeAppeals.filter(a => {
    const searchMatch = !search || a.id.toLowerCase().includes(search.toLowerCase()) || a.examName.toLowerCase().includes(search.toLowerCase());
    const statusMatch = statusFilter === 'ALL' || a.status === statusFilter;
    return searchMatch && statusMatch;
  });

  return (
    <>
      <div className="flex flex-col lg:flex-row lg:items-end justify-between gap-space-md mb-space-lg">
        <div className="flex flex-col gap-space-xs">
          <div className="flex items-center gap-space-xs">
            <span className="font-label-sm text-label-sm text-primary uppercase tracking-widest font-semibold">Student Audit &amp; Rectification Portal</span>
            <span className="font-label-sm text-label-sm text-outline">•</span>
            <span className="font-label-sm text-label-sm text-on-surface-variant font-medium">AY 2025–2026</span>
          </div>
          <h1 className="font-headline-lg text-headline-lg text-on-surface tracking-tight">My Appeals</h1>
          <p className="font-body-md text-body-md text-on-surface-variant">History and real-time status of your grade disputes and similarity clarifications.</p>
        </div>
        <div className="flex items-center gap-space-sm">
          <button className="inline-flex items-center gap-space-xs px-3.5 py-2 rounded bg-surface-container-low text-on-surface font-label-md text-label-md shadow-sm hover:bg-surface-container transition-colors">
            <span className="material-symbols-outlined text-[18px] text-secondary">history_edu</span>
            <span>Export Statement</span>
          </button>
        </div>
      </div>

      <div className="bg-surface-container-lowest p-space-sm rounded-xl shadow-sm mb-space-md">
        <div className="flex flex-col md:flex-row items-center gap-space-sm">
          <div className="relative flex-1 w-full">
            <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-outline text-[18px]">search</span>
            <input 
              className="w-full pl-9 pr-4 py-2 text-body-md font-body-md text-on-surface bg-surface-container-low rounded-lg focus:outline-none focus:bg-surface-container-lowest transition-colors" 
              placeholder="Search by case ID, exam title, or course..." 
              type="text"
              value={search}
              onChange={e => setSearch(e.target.value)}
            />
          </div>
          <div className="flex items-center gap-space-xs w-full md:w-auto">
            <div className="relative flex-1 md:w-56">
              <select 
                className="w-full appearance-none pl-3 pr-8 py-2 bg-surface-container-low text-on-surface font-body-md text-body-md rounded-lg focus:outline-none cursor-pointer"
                value={statusFilter}
                onChange={e => setStatusFilter(e.target.value)}
              >
                <option value="ALL">All Statuses</option>
                <option value="PENDING">Pending Review</option>
                <option value="APPROVED">Accepted</option>
                <option value="REJECTED">Rejected / Defended</option>
              </select>
              <span className="material-symbols-outlined absolute right-2.5 top-1/2 -translate-y-1/2 text-outline text-[18px] pointer-events-none">unfold_more</span>
            </div>
            <button 
              className="p-2 text-on-surface-variant hover:text-on-surface hover:bg-surface-container-low rounded-lg transition-colors" 
              title="Reset Filters"
              onClick={() => { setSearch(''); setStatusFilter('ALL'); }}
            >
              <span className="material-symbols-outlined text-[20px]">filter_alt_off</span>
            </button>
          </div>
        </div>
      </div>

      <div className="bg-surface-container-lowest rounded-xl shadow-sm overflow-hidden mb-space-xl">
        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse">
            <thead>
              <tr className="bg-surface-container-low text-on-surface-variant font-label-md text-label-md select-none">
                <th className="py-3 px-space-md font-semibold tracking-wider uppercase text-[11px]">Case ID &amp; Exam</th>
                <th className="py-3 px-space-md font-semibold tracking-wider uppercase text-[11px]">Submission</th>
                <th className="py-3 px-space-md font-semibold tracking-wider uppercase text-[11px]">Submitted At</th>
                <th className="py-3 px-space-md font-semibold tracking-wider uppercase text-[11px]">Status</th>
                <th className="py-3 px-space-md font-semibold tracking-wider uppercase text-[11px]">Resolved At</th>
                <th className="py-3 px-space-md font-semibold tracking-wider uppercase text-[11px] text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="font-body-md text-body-md divide-y-0">
              {filteredAppeals.length === 0 ? (
                <tr>
                  <td colSpan={6} className="py-space-xl text-center text-on-surface-variant">
                    No appeals found.
                  </td>
                </tr>
              ) : (
                filteredAppeals.map(appeal => (
                  <tr key={appeal.id} className="hover:bg-surface-container-low/60 transition-colors">
                    <td className="py-space-md px-space-md">
                      <div className="flex items-start gap-space-xs">
                        <span className={
                          'w-1.5 h-1.5 rounded-full mt-1.5 shrink-0 ' +
                          (appeal.status === 'PENDING' ? 'bg-[#92400e]' :
                          appeal.status === 'APPROVED' ? 'bg-[#065f46]' : 'bg-outline')
                        }></span>
                        <div className="flex flex-col">
                          <div className="flex items-center gap-1.5">
                            <span className="font-label-sm text-label-sm font-semibold text-primary">{appeal.id}</span>
                          </div>
                          <span className="font-headline-sm text-headline-sm text-on-surface font-semibold tracking-tight mt-0.5">{appeal.examName}</span>
                        </div>
                      </div>
                    </td>
                    <td className="py-space-md px-space-md align-middle">
                      <span className="font-label-sm text-label-sm text-on-surface font-semibold">{appeal.submissionId}</span>
                    </td>
                    <td className="py-space-md px-space-md align-middle">
                      <span className="font-body-md text-body-md text-on-surface">{new Date(appeal.createdAt).toLocaleDateString()}</span>
                    </td>
                    <td className="py-space-md px-space-md align-middle">
                      {appeal.status === 'PENDING' && (
                        <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded bg-[#fffbeb] text-[#92400e] font-label-sm text-label-sm font-semibold">
                          <span className="w-1.5 h-1.5 rounded-full bg-[#92400e] animate-pulse"></span>
                          Pending Review
                        </span>
                      )}
                      {appeal.status === 'APPROVED' && (
                        <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded bg-[#ecfdf5] text-[#065f46] font-label-sm text-label-sm font-semibold">
                          <span className="material-symbols-outlined text-[14px]">check_circle</span>
                          Accepted
                        </span>
                      )}
                      {appeal.status === 'REJECTED' && (
                        <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded bg-surface-container-low text-outline font-label-sm text-label-sm font-semibold">
                          <span className="material-symbols-outlined text-[14px]">block</span>
                          Rejected
                        </span>
                      )}
                    </td>
                    <td className="py-space-md px-space-md align-middle">
                      <span className="font-body-md text-body-md text-on-surface">{appeal.resolvedAt ? new Date(appeal.resolvedAt).toLocaleDateString() : '-'}</span>
                    </td>
                    <td className="py-space-md px-space-md align-middle text-right">
                      <Link href={`/student/appeals/${appeal.id}`}>
                        <button className="inline-flex items-center gap-1 px-3 py-1.5 rounded bg-primary text-on-primary font-label-md text-label-md font-medium shadow-sm hover:bg-primary-container transition-colors">
                          <span>View Detail</span>
                          <span className="material-symbols-outlined text-[16px]">arrow_forward</span>
                        </button>
                      </Link>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>
    </>
  );
}
