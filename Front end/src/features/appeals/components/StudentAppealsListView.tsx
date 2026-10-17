'use client';

import React, { useState } from 'react';
import { AppLink as Link } from '@/components/ui/app-link';
import { useStudentAppeals } from '../hooks';
import { 
  Search, 
  X, 
  ChevronDown, 
  Download, 
  CheckCircle2, 
  Clock, 
  ArrowRight, 
  Scale, 
  AlertCircle,
  RotateCcw,
  ExternalLink
} from 'lucide-react';
import { toast } from 'sonner';

export function StudentAppealsListView() {
  const { data: appeals, isLoading, isError, error } = useStudentAppeals();
  const [search, setSearch] = useState(() => {
    if (typeof window !== 'undefined') {
      return new URLSearchParams(window.location.search).get('search') || '';
    }
    return '';
  });
  const [statusFilter, setStatusFilter] = useState<string>('ALL');

  const handleExportStatement = () => {
    if (!appeals || appeals.length === 0) {
      toast.error('No appeals records available to export.');
      return;
    }

    const headers = ['Case ID', 'Exam Name', 'Submission ID', 'Submitted Date', 'Status', 'Resolved Date'];
    const rows = appeals.map(a => [
      `"${a.id}"`,
      `"${a.examName.replace(/"/g, '""')}"`,
      `"${a.submissionId}"`,
      `"${new Date(a.createdAt).toISOString()}"`,
      `"${a.status}"`,
      `"${a.resolvedAt ? new Date(a.resolvedAt).toISOString() : ''}"`
    ]);

    const csvContent = 'data:text/csv;charset=utf-8,' + [headers.join(','), ...rows.map(r => r.join(','))].join('\n');
    const encodedUri = encodeURI(csvContent);
    const link = document.createElement('a');
    link.setAttribute('href', encodedUri);
    link.setAttribute('download', `CodeAssess_Appeals_Statement_${new Date().toISOString().slice(0, 10)}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);

    toast.success('Appeals statement downloaded successfully');
  };

  if (isLoading) {
    return (
      <div className="flex flex-col h-72 items-center justify-center gap-3">
        <div className="w-8 h-8 rounded-full border-2 border-primary border-t-transparent animate-spin" />
        <p className="text-on-surface-variant font-label-md text-xs">Loading academic appeals history...</p>
      </div>
    );
  }

  if (isError) {
    return (
      <div className="max-w-xl mx-auto my-12 p-8 rounded-2xl bg-surface-container-lowest border border-rose-200/60 shadow-lg text-center flex flex-col items-center gap-3">
        <div className="p-3 rounded-full bg-rose-50 text-rose-600">
          <AlertCircle className="w-8 h-8" />
        </div>
        <h3 className="font-headline-sm text-headline-sm text-on-surface">Unable to Retrieve Appeals</h3>
        <p className="text-xs text-on-surface-variant">{error?.message || 'Server connection error.'}</p>
        <button onClick={() => window.location.reload()} className="mt-2 px-4 py-2 bg-primary text-on-primary rounded-lg text-xs font-medium">
          Retry
        </button>
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
    <div className="w-full max-w-7xl mx-auto px-4 sm:px-space-lg py-space-lg flex flex-col gap-space-lg min-w-0">
      {/* Header & Export Action */}
      <div className="flex flex-col lg:flex-row lg:items-end justify-between gap-space-md">
        <div className="flex flex-col gap-1">
          <div className="flex items-center gap-1.5 text-xs text-on-surface-variant">
            <span className="text-primary uppercase tracking-wider font-semibold">Student Rectification Portal</span>
            <span className="text-outline">•</span>
            <span>Academic Year 2026</span>
          </div>
          <h1 className="font-display-lg text-display-lg text-on-surface tracking-tight">My Appeals</h1>
          <p className="text-xs sm:text-sm text-on-surface-variant">
            Real-time status and faculty resolution for your grade disputes and AST similarity clarifications.
          </p>
        </div>

        <div className="flex items-center gap-2 self-start lg:self-auto">
          <button 
            onClick={handleExportStatement}
            className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-lg bg-surface-container hover:bg-surface-container-high text-on-surface text-xs font-medium border border-outline-variant/40 shadow-xs transition-colors cursor-pointer"
          >
            <Download className="w-3.5 h-3.5 text-secondary" />
            <span>Export Statement</span>
          </button>
        </div>
      </div>

      {/* Filter & Search Bar */}
      <div className="bg-surface-container-lowest p-3 rounded-xl border border-outline-variant/40 shadow-xs flex flex-col md:flex-row items-center gap-3">
        <div className="relative flex-1 w-full">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-outline w-4 h-4" />
          <input 
            className="w-full pl-9 pr-4 py-1.5 text-xs text-on-surface bg-surface-container-low border border-outline-variant/40 rounded-lg focus:outline-none focus:border-primary transition-colors" 
            placeholder="Search by case ID, exam title, or course..." 
            type="text"
            value={search}
            onChange={e => setSearch(e.target.value)}
          />
          {search && (
            <button onClick={() => setSearch('')} className="absolute right-2.5 top-1/2 -translate-y-1/2 text-outline hover:text-on-surface cursor-pointer">
              <X className="w-3.5 h-3.5" />
            </button>
          )}
        </div>

        <div className="flex items-center gap-2 w-full md:w-auto">
          <div className="relative flex-1 md:w-56">
            <select 
              className="w-full appearance-none pl-3 pr-8 py-1.5 bg-surface-container-low border border-outline-variant/40 text-on-surface text-xs rounded-lg focus:outline-none cursor-pointer"
              value={statusFilter}
              onChange={e => setStatusFilter(e.target.value)}
            >
              <option value="ALL">All Statuses</option>
              <option value="PENDING">Pending Review</option>
              <option value="APPROVED">Accepted</option>
              <option value="REJECTED">Rejected</option>
            </select>
            <ChevronDown className="absolute right-2.5 top-1/2 -translate-y-1/2 text-outline w-3.5 h-3.5 pointer-events-none" />
          </div>

          <button 
            className="p-2 text-on-surface-variant hover:text-on-surface hover:bg-surface-container rounded-lg transition-colors cursor-pointer" 
            title="Reset Filters"
            onClick={() => { setSearch(''); setStatusFilter('ALL'); }}
          >
            <RotateCcw className="w-3.5 h-3.5" />
          </button>
        </div>
      </div>

      {/* Appeals Table */}
      <div className="bg-surface-container-lowest rounded-2xl border border-outline-variant/40 shadow-xs overflow-hidden w-full min-w-0">
        <div className="overflow-x-auto w-full">
          <table className="w-full text-left border-collapse text-xs">
            <thead>
              <tr className="bg-surface-container-low text-on-surface-variant font-label-xs uppercase tracking-wider border-b border-outline-variant/30 select-none">
                <th className="py-2.5 px-4 font-semibold">Case ID &amp; Exam</th>
                <th className="py-2.5 px-4 font-semibold">Submission ID</th>
                <th className="py-2.5 px-4 font-semibold">Submitted Date</th>
                <th className="py-2.5 px-4 font-semibold">Status</th>
                <th className="py-2.5 px-4 font-semibold">Resolved Date</th>
                <th className="py-2.5 px-4 font-semibold text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-outline-variant/20 text-on-surface">
              {filteredAppeals.length === 0 ? (
                <tr>
                  <td colSpan={6} className="py-12 text-center text-outline">
                    <div className="flex flex-col items-center justify-center gap-2">
                      <Scale className="w-8 h-8 text-outline" />
                      <p className="font-semibold text-on-surface text-sm">No appeals found</p>
                      <p className="text-outline text-xs">
                        {search || statusFilter !== 'ALL'
                          ? 'No appeal records match your search criteria.'
                          : 'You have not submitted any appeals for your submissions.'}
                      </p>
                    </div>
                  </td>
                </tr>
              ) : (
                filteredAppeals.map(appeal => (
                  <tr key={appeal.id} className="hover:bg-surface-container-low/60 transition-colors">
                    <td className="py-3 px-4">
                      <div className="flex flex-col">
                        <span className="font-mono text-[11px] font-bold text-primary">#{appeal.id.substring(0, 8)}</span>
                        <span className="font-semibold text-on-surface text-xs mt-0.5">{appeal.examName}</span>
                      </div>
                    </td>
                    <td className="py-3 px-4 font-mono text-on-surface-variant text-[11px]">
                      #{appeal.submissionId.substring(0, 8)}
                    </td>
                    <td className="py-3 px-4 text-on-surface-variant">
                      {new Date(appeal.createdAt).toLocaleDateString()}
                    </td>
                    <td className="py-3 px-4">
                      {appeal.status === 'PENDING' && (
                        <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full bg-amber-50 text-amber-700 border border-amber-200 text-[10px] font-semibold">
                          <span className="w-1.5 h-1.5 rounded-full bg-amber-500 animate-pulse"></span>
                          <span>In Review</span>
                        </span>
                      )}
                      {appeal.status === 'APPROVED' && (
                        <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full bg-emerald-50 text-emerald-700 border border-emerald-200 text-[10px] font-semibold">
                          <CheckCircle2 className="w-3 h-3 text-emerald-600" />
                          <span>Approved</span>
                        </span>
                      )}
                      {appeal.status === 'REJECTED' && (
                        <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full bg-surface-container text-on-surface-variant border border-outline-variant/40 text-[10px] font-semibold">
                          <span>Rejected</span>
                        </span>
                      )}
                    </td>
                    <td className="py-3 px-4 text-on-surface-variant">
                      {appeal.resolvedAt ? new Date(appeal.resolvedAt).toLocaleDateString() : '—'}
                    </td>
                    <td className="py-3 px-4 text-right">
                      <Link 
                        href={`/student/appeals/${appeal.id}`}
                        className="inline-flex items-center gap-1 px-3 py-1 bg-surface-container hover:bg-surface-container-high text-primary rounded-lg font-medium transition-colors"
                      >
                        <span>View Dossier</span>
                        <ExternalLink className="w-3 h-3" />
                      </Link>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
