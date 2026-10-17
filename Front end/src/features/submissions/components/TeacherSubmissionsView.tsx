'use client';

import React, { useState, useMemo, useEffect } from 'react';
import { useTeacherSubmissions } from '@/features/exams/hooks/use-teacher-exams';
import { AppLink as Link } from '@/components/ui/app-link';
import { 
  Search, 
  ChevronRight, 
  ChevronDown, 
  CheckCircle2, 
  Clock, 
  Award, 
  FileCode2, 
  ExternalLink,
  AlertCircle,
  Loader2,
  X,
  Code2
} from 'lucide-react';

export function TeacherSubmissionsView() {
  const { data: submissions, isLoading, isError, error, refetch } = useTeacherSubmissions();
  const [searchQuery, setSearchQuery] = useState(() => {
    if (typeof window !== 'undefined') {
      return new URLSearchParams(window.location.search).get('search') || '';
    }
    return '';
  });
  const [statusFilter, setStatusFilter] = useState(() => {
    if (typeof window !== 'undefined') {
      return new URLSearchParams(window.location.search).get('status') || 'all';
    }
    return 'all';
  });

  const filteredSubmissions = useMemo(() => {
    if (!submissions) return [];
    return submissions.filter(sub => {
      if (statusFilter !== 'all' && (sub.status || '').toUpperCase() !== statusFilter.toUpperCase()) {
        return false;
      }
      if (searchQuery.trim()) {
        const query = searchQuery.toLowerCase();
        const studentNameMatch = (sub.studentName || '').toLowerCase().includes(query);
        const studentEmailMatch = (sub.studentEmail || '').toLowerCase().includes(query);
        const examTitleMatch = (sub.examTitle || '').toLowerCase().includes(query);
        const courseCodeMatch = (sub.courseCode || '').toLowerCase().includes(query);
        return studentNameMatch || studentEmailMatch || examTitleMatch || courseCodeMatch;
      }
      return true;
    });
  }, [submissions, searchQuery, statusFilter]);

  const stats = useMemo(() => {
    if (!submissions || submissions.length === 0) {
      return { total: 0, graded: 0, pending: 0, avgScore: '0' };
    }
    const graded = submissions.filter(s => s.status === 'GRADED');
    const pending = submissions.filter(s => s.status !== 'GRADED');
    const totalScore = graded.reduce((acc, curr) => acc + (Number(curr.totalScore) || 0), 0);
    const avgScore = graded.length > 0 ? (totalScore / graded.length).toFixed(1) : '0';
    return {
      total: submissions.length,
      graded: graded.length,
      pending: pending.length,
      avgScore,
    };
  }, [submissions]);

  if (isLoading) {
    return (
      <div className="max-w-7xl mx-auto px-space-lg py-space-lg">
        <div className="flex flex-col h-72 items-center justify-center gap-3">
          <div className="w-8 h-8 rounded-full border-2 border-primary border-t-transparent animate-spin" />
          <p className="text-on-surface-variant font-label-md text-xs">Loading course submissions...</p>
        </div>
      </div>
    );
  }

  if (isError) {
    return (
      <div className="max-w-xl mx-auto my-12 p-8 rounded-2xl bg-surface-container-lowest border border-rose-200/60 shadow-lg text-center flex flex-col items-center gap-3">
        <div className="p-3 rounded-full bg-rose-50 text-rose-600">
          <AlertCircle className="w-8 h-8" />
        </div>
        <h2 className="font-headline-md text-headline-md text-on-surface">Unable to Load Submissions</h2>
        <p className="font-body-md text-on-surface-variant text-xs">
          {(error as Error)?.message || 'An error occurred while fetching course submissions.'}
        </p>
        <button
          onClick={() => refetch()}
          className="mt-2 px-4 py-2 bg-primary text-on-primary rounded-lg text-xs font-medium hover:bg-primary/90 transition-all cursor-pointer shadow-xs"
        >
          Retry
        </button>
      </div>
    );
  }

  return (
    <div className="w-full max-w-7xl mx-auto px-4 sm:px-space-lg py-space-lg min-w-0">
      <div className="flex flex-col w-full gap-space-md min-w-0">
        {/* Breadcrumb */}
        <div className="flex items-center gap-1.5 text-xs text-on-surface-variant">
          <Link className="hover:text-primary transition-colors font-medium" href="/teacher/dashboard">
            Dashboard
          </Link>
          <ChevronRight className="w-3.5 h-3.5 text-outline" />
          <span className="text-on-surface font-semibold">Submissions &amp; Grading</span>
        </div>

        {/* Header */}
        <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-space-md">
          <div className="flex flex-col gap-0.5">
            <h1 className="font-display-lg text-display-lg text-on-surface tracking-tight">Student Submissions</h1>
            <p className="text-xs sm:text-sm text-on-surface-variant">
              Review student code artifacts, evaluate criteria rubrics, and inspect AST plagiarism reports.
            </p>
          </div>
          <Link
            href="/teacher/exams"
            className="inline-flex items-center gap-2 px-4 py-2 bg-primary text-on-primary rounded-lg text-xs font-medium hover:bg-primary/90 transition-colors self-start lg:self-auto shadow-xs"
          >
            <FileCode2 className="w-4 h-4" />
            <span>Manage Exams</span>
          </Link>
        </div>

        {/* 4 Metric Cards */}
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-space-sm">
          <div className="bg-surface-container-lowest p-space-md rounded-xl border border-outline-variant/40 shadow-xs flex flex-col justify-between">
            <span className="font-label-xs uppercase tracking-wider text-outline font-semibold">Total Submissions</span>
            <span className="font-display-lg font-bold text-on-surface mt-2">{stats.total}</span>
          </div>
          <div className="bg-surface-container-lowest p-space-md rounded-xl border border-outline-variant/40 shadow-xs flex flex-col justify-between">
            <span className="font-label-xs uppercase tracking-wider text-outline font-semibold">Graded</span>
            <span className="font-display-lg font-bold text-emerald-600 mt-2">{stats.graded}</span>
          </div>
          <div className="bg-surface-container-lowest p-space-md rounded-xl border border-outline-variant/40 shadow-xs flex flex-col justify-between">
            <span className="font-label-xs uppercase tracking-wider text-outline font-semibold">Needs Review</span>
            <span className="font-display-lg font-bold text-amber-600 mt-2">{stats.pending}</span>
          </div>
          <div className="bg-surface-container-lowest p-space-md rounded-xl border border-outline-variant/40 shadow-xs flex flex-col justify-between">
            <span className="font-label-xs uppercase tracking-wider text-outline font-semibold">Cohort Avg</span>
            <div className="mt-2 flex items-baseline gap-1">
              <span className="font-display-lg font-bold text-primary">{stats.avgScore}</span>
              <span className="text-xs text-outline font-normal">/ 100</span>
            </div>
          </div>
        </div>

        {/* Filter Bar */}
        <div className="flex flex-col sm:flex-row gap-space-sm items-stretch sm:items-center justify-between bg-surface-container-lowest p-3 rounded-xl border border-outline-variant/40 shadow-xs">
          <div className="relative flex-1 max-w-md">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-outline w-4 h-4" />
            <input
              type="text"
              placeholder="Search by student, email, exam or course..."
              value={searchQuery}
              onChange={e => setSearchQuery(e.target.value)}
              className="w-full pl-9 pr-8 py-1.5 bg-surface-container-low border border-outline-variant/40 rounded-lg text-xs text-on-surface placeholder:text-outline focus:outline-none focus:border-primary transition-colors"
            />
            {searchQuery && (
              <button 
                onClick={() => setSearchQuery('')}
                className="absolute right-2.5 top-1/2 -translate-y-1/2 text-outline hover:text-on-surface cursor-pointer"
              >
                <X className="w-3.5 h-3.5" />
              </button>
            )}
          </div>
          <div className="flex items-center gap-2">
            <span className="text-on-surface-variant font-label-xs text-xs font-medium">Filter:</span>
            <div className="relative">
              <select
                value={statusFilter}
                onChange={e => setStatusFilter(e.target.value)}
                className="appearance-none pl-3 pr-8 py-1.5 bg-surface-container-low border border-outline-variant/40 rounded-lg text-xs text-on-surface focus:outline-none cursor-pointer"
              >
                <option value="all">All Statuses</option>
                <option value="GRADED">Graded</option>
                <option value="PENDING">Pending</option>
                <option value="EVALUATING">Evaluating</option>
              </select>
              <ChevronDown className="absolute right-2.5 top-1/2 -translate-y-1/2 text-outline w-3.5 h-3.5 pointer-events-none" />
            </div>
          </div>
        </div>

        {/* Submissions Table */}
        <div className="rounded-xl bg-surface-container-lowest shadow-xs border border-outline-variant/40 overflow-hidden flex flex-col w-full min-w-0">
          <div className="overflow-x-auto w-full">
            <table className="w-full text-left border-collapse text-xs" id="teacher-submissions-table">
              <thead>
                <tr className="bg-surface-container-low text-on-surface-variant font-label-xs uppercase tracking-wider border-b border-outline-variant/30 select-none">
                  <th className="py-2.5 px-4 font-semibold">Student Name</th>
                  <th className="py-2.5 px-4 font-semibold">Email</th>
                  <th className="py-2.5 px-4 font-semibold">Course &amp; Exam</th>
                  <th className="py-2.5 px-4 font-semibold">Submission Time</th>
                  <th className="py-2.5 px-4 font-semibold">Score</th>
                  <th className="py-2.5 px-4 font-semibold">Status</th>
                  <th className="py-2.5 px-4 font-semibold text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-outline-variant/20 text-on-surface">
                {filteredSubmissions.length === 0 ? (
                  <tr>
                    <td colSpan={7} className="py-12 text-center text-on-surface-variant">
                      <div className="flex flex-col items-center justify-center gap-2">
                        <Code2 className="w-8 h-8 text-outline" />
                        <p className="font-semibold text-on-surface text-sm">No submissions found</p>
                        <p className="text-outline text-xs">
                          {searchQuery || statusFilter !== 'all'
                            ? 'No student submissions match your filter criteria.'
                            : 'No students have submitted code for your assigned courses yet.'}
                        </p>
                      </div>
                    </td>
                  </tr>
                ) : (
                  filteredSubmissions.map(sub => {
                    const isGraded = sub.status === 'GRADED';
                    return (
                      <tr
                        key={sub.id}
                        className="hover:bg-surface-container-low/60 transition-colors"
                      >
                        <td className="py-3 px-4 font-semibold text-on-surface">{sub.studentName}</td>
                        <td className="py-3 px-4 text-on-surface-variant text-[11px]">{sub.studentEmail}</td>
                        <td className="py-3 px-4">
                          <div className="flex flex-col">
                            <span className="font-medium text-on-surface">{sub.examTitle}</span>
                            {sub.courseCode && (
                              <span className="font-mono text-[10px] text-primary">{sub.courseCode}</span>
                            )}
                          </div>
                        </td>
                        <td className="py-3 px-4 text-on-surface-variant">
                          {sub.submittedAt ? new Date(sub.submittedAt).toLocaleString() : 'N/A'}
                        </td>
                        <td className="py-3 px-4">
                          {isGraded ? (
                            <span className="font-mono bg-surface-container px-2 py-0.5 rounded font-bold text-on-surface">
                              {sub.totalScore != null ? sub.totalScore : 0}
                              <span className="text-outline text-[10px] ml-0.5">/100</span>
                            </span>
                          ) : (
                            <span className="text-outline italic">Pending</span>
                          )}
                        </td>
                        <td className="py-3 px-4">
                          <span
                            className={`inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-semibold uppercase ${
                              isGraded
                                ? 'bg-emerald-50 text-emerald-700 border border-emerald-200'
                                : 'bg-amber-50 text-amber-700 border border-amber-200'
                            }`}
                          >
                            {sub.status || 'PENDING'}
                          </span>
                        </td>
                        <td className="py-3 px-4 text-right">
                          <Link
                            href={`/teacher/submissions/${sub.id}`}
                            className="inline-flex items-center gap-1 px-3 py-1 bg-surface-container hover:bg-surface-container-high text-primary rounded-lg font-medium transition-colors"
                          >
                            <span>Grade Code</span>
                            <ExternalLink className="w-3 h-3" />
                          </Link>
                        </td>
                      </tr>
                    );
                  })
                )}
              </tbody>
            </table>
          </div>
          {filteredSubmissions.length > 0 && (
            <div className="px-4 py-2.5 bg-surface-container-low/40 flex items-center justify-between text-xs text-outline border-t border-outline-variant/20">
              <span>
                Showing <strong className="text-on-surface">{filteredSubmissions.length}</strong> of{' '}
                <strong className="text-on-surface">{submissions?.length || 0}</strong> submissions
              </span>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
