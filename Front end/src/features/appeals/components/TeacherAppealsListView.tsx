'use client';

import React, { useState } from 'react';
import { AppLink as Link } from '@/components/ui/app-link';
import { useTeacherAppeals } from '../hooks';
import { 
  Search, 
  Clock, 
  CheckCircle2, 
  XCircle, 
  ExternalLink, 
  Scale, 
  AlertCircle,
  FileText,
  X
} from 'lucide-react';

export function TeacherAppealsListView() {
  const { data: appeals, isLoading, isError, refetch } = useTeacherAppeals();
  const [filter, setFilter] = useState<'ALL' | 'PENDING' | 'APPROVED' | 'REJECTED'>('ALL');
  const [search, setSearch] = useState(() => {
    if (typeof window !== 'undefined') {
      return new URLSearchParams(window.location.search).get('search') || '';
    }
    return '';
  });

  if (isLoading) {
    return (
      <div className="flex flex-col h-72 items-center justify-center gap-3">
        <div className="w-8 h-8 rounded-full border-2 border-primary border-t-transparent animate-spin" />
        <p className="text-on-surface-variant font-label-md text-xs">Loading course appeals roster...</p>
      </div>
    );
  }

  if (isError) {
    return (
      <div className="max-w-xl mx-auto my-12 p-8 rounded-2xl bg-surface-container-lowest border border-rose-200/60 shadow-lg text-center flex flex-col items-center gap-3">
        <div className="p-3 rounded-full bg-rose-50 text-rose-600">
          <AlertCircle className="w-8 h-8" />
        </div>
        <h2 className="font-headline-md text-headline-md text-on-surface">Unable to Load Appeals</h2>
        <p className="font-body-md text-on-surface-variant text-xs">Error retrieving appeals from server.</p>
        <button onClick={() => refetch()} className="mt-2 px-4 py-2 bg-primary text-on-primary rounded-lg text-xs font-medium cursor-pointer">
          Retry
        </button>
      </div>
    );
  }

  const allAppeals = appeals || [];
  const filtered = allAppeals.filter(a => {
    if (filter !== 'ALL' && a.status !== filter) return false;
    if (search.trim()) {
      const q = search.toLowerCase();
      const matchId = a.id?.toLowerCase().includes(q);
      const matchExam = a.examName?.toLowerCase().includes(q);
      const matchStudent = a.studentName?.toLowerCase().includes(q) || a.studentEmail?.toLowerCase().includes(q);
      return matchId || matchExam || matchStudent;
    }
    return true;
  });

  const pendingCount = allAppeals.filter(a => a.status === 'PENDING').length;
  const approvedCount = allAppeals.filter(a => a.status === 'APPROVED').length;
  const rejectedCount = allAppeals.filter(a => a.status === 'REJECTED').length;

  return (
    <div className="max-w-7xl mx-auto px-space-lg py-space-lg flex flex-col w-full space-y-space-lg">
      {/* Page Title */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-space-md">
        <div>
          <h1 className="font-display-lg text-display-lg text-on-surface tracking-tight">Grade &amp; Similarity Appeals</h1>
          <p className="text-xs sm:text-sm text-on-surface-variant mt-0.5">
            Review and resolve student dispute tickets regarding automated grading and AST similarity flags.
          </p>
        </div>
      </div>

      {/* 3 Metric Cards */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-space-md">
        <div className="bg-surface-container-lowest p-space-md rounded-2xl border border-outline-variant/40 shadow-xs flex items-center justify-between">
          <div>
            <span className="font-label-xs uppercase tracking-wider text-outline font-semibold">Pending Review</span>
            <div className="font-display-lg font-bold text-amber-600 mt-1">{pendingCount}</div>
          </div>
          <div className="p-2 rounded-xl bg-amber-50 text-amber-600">
            <Clock className="w-5 h-5" />
          </div>
        </div>

        <div className="bg-surface-container-lowest p-space-md rounded-2xl border border-outline-variant/40 shadow-xs flex items-center justify-between">
          <div>
            <span className="font-label-xs uppercase tracking-wider text-outline font-semibold">Approved</span>
            <div className="font-display-lg font-bold text-emerald-600 mt-1">{approvedCount}</div>
          </div>
          <div className="p-2 rounded-xl bg-emerald-50 text-emerald-600">
            <CheckCircle2 className="w-5 h-5" />
          </div>
        </div>

        <div className="bg-surface-container-lowest p-space-md rounded-2xl border border-outline-variant/40 shadow-xs flex items-center justify-between">
          <div>
            <span className="font-label-xs uppercase tracking-wider text-outline font-semibold">Rejected</span>
            <div className="font-display-lg font-bold text-rose-600 mt-1">{rejectedCount}</div>
          </div>
          <div className="p-2 rounded-xl bg-rose-50 text-rose-600">
            <XCircle className="w-5 h-5" />
          </div>
        </div>
      </div>

      {/* Filter Tabs & Search */}
      <div className="flex flex-col sm:flex-row items-center justify-between gap-space-md">
        <div className="flex items-center gap-1.5 overflow-x-auto text-xs w-full sm:w-auto">
          {(['ALL', 'PENDING', 'APPROVED', 'REJECTED'] as const).map(tab => (
            <button
              key={tab}
              onClick={() => setFilter(tab)}
              className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition-all cursor-pointer ${
                filter === tab 
                  ? 'bg-primary text-on-primary shadow-xs' 
                  : 'bg-surface-container text-on-surface-variant hover:bg-surface-container-high'
              }`}
            >
              {tab === 'ALL' ? 'All Appeals' : tab === 'PENDING' ? 'Pending' : tab === 'APPROVED' ? 'Approved' : 'Rejected'}
            </button>
          ))}
        </div>

        <div className="w-full sm:w-72 relative">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-outline w-4 h-4" />
          <input
            type="text"
            placeholder="Search student, exam, case ID..."
            value={search}
            onChange={e => setSearch(e.target.value)}
            className="w-full pl-9 pr-8 py-1.5 rounded-lg border border-outline-variant/40 bg-surface-container-lowest text-on-surface text-xs focus:outline-none focus:border-primary transition-colors"
          />
          {search && (
            <button onClick={() => setSearch('')} className="absolute right-2.5 top-1/2 -translate-y-1/2 text-outline hover:text-on-surface cursor-pointer">
              <X className="w-3.5 h-3.5" />
            </button>
          )}
        </div>
      </div>

      {/* Table */}
      <div className="bg-surface-container-lowest rounded-2xl shadow-xs border border-outline-variant/40 overflow-hidden">
        {filtered.length === 0 ? (
          <div className="p-12 text-center text-outline text-xs">
            No appeals found matching your current filter.
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse text-xs">
              <thead>
                <tr className="bg-surface-container-low text-on-surface-variant font-label-xs uppercase tracking-wider border-b border-outline-variant/30">
                  <th className="py-2.5 px-4 font-semibold">Case ID</th>
                  <th className="py-2.5 px-4 font-semibold">Student</th>
                  <th className="py-2.5 px-4 font-semibold">Exam</th>
                  <th className="py-2.5 px-4 font-semibold">Basis</th>
                  <th className="py-2.5 px-4 font-semibold">Status</th>
                  <th className="py-2.5 px-4 font-semibold">Submitted</th>
                  <th className="py-2.5 px-4 font-semibold text-right">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-outline-variant/20 text-on-surface">
                {filtered.map(appeal => (
                  <tr key={appeal.id} className="hover:bg-surface-container-low/60 transition-colors">
                    <td className="py-3 px-4 font-mono font-medium text-primary">#{appeal.id.substring(0, 8)}</td>
                    <td className="py-3 px-4">
                      <div className="font-semibold text-on-surface">{appeal.studentName || 'Student'}</div>
                      <div className="text-on-surface-variant text-[11px]">{appeal.studentEmail || 'N/A'}</div>
                    </td>
                    <td className="py-3 px-4 font-medium">{appeal.examName}</td>
                    <td className="py-3 px-4 capitalize text-on-surface-variant">{appeal.basis.replace(/_/g, ' ')}</td>
                    <td className="py-3 px-4">
                      {appeal.status === 'PENDING' && (
                        <span className="px-2 py-0.5 rounded-full bg-amber-50 text-amber-700 border border-amber-200 font-semibold text-[10px]">
                          Pending Review
                        </span>
                      )}
                      {appeal.status === 'APPROVED' && (
                        <span className="px-2 py-0.5 rounded-full bg-emerald-50 text-emerald-700 border border-emerald-200 font-semibold text-[10px]">
                          Approved
                        </span>
                      )}
                      {appeal.status === 'REJECTED' && (
                        <span className="px-2 py-0.5 rounded-full bg-rose-50 text-rose-700 border border-rose-200 font-semibold text-[10px]">
                          Rejected
                        </span>
                      )}
                    </td>
                    <td className="py-3 px-4 text-on-surface-variant">
                      {new Date(appeal.createdAt).toLocaleDateString()}
                    </td>
                    <td className="py-3 px-4 text-right">
                      <Link
                        href={`/teacher/appeals/${appeal.id}`}
                        className="px-3 py-1 bg-surface-container hover:bg-surface-container-high text-primary rounded-lg text-xs font-medium transition-colors inline-flex items-center gap-1"
                      >
                        <span>Review</span>
                        <ExternalLink className="w-3 h-3" />
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
