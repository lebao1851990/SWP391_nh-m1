'use client';

import React, { useState, useMemo, useEffect } from 'react';
import { useStudentExams } from '../hooks/use-student-exams';
import { useStudentSubmissions } from '../../submissions/hooks';
import { AppLink as Link } from '@/components/ui/app-link';
import { 
  Search, 
  X, 
  ChevronDown, 
  Terminal, 
  FileCode2, 
  Calendar, 
  CheckCircle2, 
  Clock, 
  ExternalLink,
  AlertCircle
} from 'lucide-react';

export function StudentExamsView() {
  const { data: exams, isLoading: isLoadingExams, isError: isErrorExams } = useStudentExams();
  const { data: submissions, isLoading: isLoadingSubmissions } = useStudentSubmissions();

  const [activeTab, setActiveTab] = useState('all');
  const [searchQuery, setSearchQuery] = useState(() => {
    if (typeof window !== 'undefined') {
      return new URLSearchParams(window.location.search).get('search') || '';
    }
    return '';
  });
  const [statusFilter, setStatusFilter] = useState('all');
  const [now] = useState(() => Date.now());
  
  const correlatedExams = useMemo(() => {
    if (!exams) return [];
    return exams.map(exam => {
      const submission = submissions?.find(s => s.examId === exam.id);
      let derivedStatus = 'UPCOMING';
      if (submission) {
        derivedStatus = submission.status === 'GRADED' ? 'COMPLETED' : 'SUBMITTED';
      } else if (exam.startTime && new Date(exam.startTime).getTime() < now) {
        derivedStatus = 'MISSED';
      }
      return { ...exam, submission, derivedStatus };
    });
  }, [exams, submissions, now]);

  const filteredExams = useMemo(() => {
    return correlatedExams.filter(exam => {
      // Tab filter
      if (activeTab === 'upcoming' && exam.derivedStatus !== 'UPCOMING') return false;
      if (activeTab === 'submitted' && exam.derivedStatus !== 'SUBMITTED') return false;
      if (activeTab === 'completed' && exam.derivedStatus !== 'COMPLETED') return false;
      
      // Status filter
      if (statusFilter !== 'all' && exam.derivedStatus.toLowerCase() !== statusFilter.toLowerCase()) return false;
      
      // Search filter
      if (searchQuery) {
        const query = searchQuery.toLowerCase();
        return exam.title.toLowerCase().includes(query) || (exam.courseClassCode && exam.courseClassCode.toLowerCase().includes(query));
      }
      
      return true;
    });
  }, [correlatedExams, activeTab, statusFilter, searchQuery]);

  const stats = useMemo(() => {
    const upcoming = correlatedExams.filter(e => e.derivedStatus === 'UPCOMING').length;
    const submitted = correlatedExams.filter(e => e.derivedStatus === 'SUBMITTED').length;
    const completed = correlatedExams.filter(e => e.derivedStatus === 'COMPLETED').length;
    return { all: correlatedExams.length, upcoming, submitted, completed };
  }, [correlatedExams]);

  if (isLoadingExams || isLoadingSubmissions) {
    return (
      <div className="flex flex-col h-72 items-center justify-center gap-3">
        <div className="w-8 h-8 rounded-full border-2 border-primary border-t-transparent animate-spin" />
        <p className="text-on-surface-variant font-label-md text-xs">Loading course assessments...</p>
      </div>
    );
  }

  if (isErrorExams) {
    return (
      <div className="max-w-xl mx-auto my-12 p-6 rounded-2xl bg-surface-container-lowest border border-rose-200/60 shadow-lg flex flex-col items-center text-center gap-3">
        <div className="p-3 rounded-full bg-rose-50 text-rose-600">
          <AlertCircle className="w-8 h-8" />
        </div>
        <h2 className="font-headline-md text-headline-md text-on-surface">Failed to Load Assessments</h2>
        <p className="font-body-md text-on-surface-variant text-xs">Please verify your enrollment status or try again.</p>
        <button 
          onClick={() => window.location.reload()}
          className="mt-2 px-4 py-2 bg-primary text-on-primary rounded-lg text-xs font-medium hover:bg-primary/90 transition-all cursor-pointer"
        >
          Retry
        </button>
      </div>
    );
  }

  return (
    <div className="w-full max-w-7xl mx-auto px-space-lg py-space-lg">
      <div className="flex flex-col w-full">
        {/* Page Header */}
        <div className="flex flex-col lg:flex-row lg:items-end justify-between gap-space-md mb-space-lg">
          <div className="space-y-1">
            <h1 className="font-display-lg text-display-lg text-on-surface tracking-tight">Enrolled Assessments</h1>
            <p className="font-body-md text-body-md text-on-surface-variant max-w-2xl text-xs sm:text-sm">
              View your upcoming programming exams, submit source code artifacts, and inspect evaluation feedback.
            </p>
          </div>
        </div>

        {/* Card Container */}
        <div className="bg-surface-container-lowest rounded-2xl border border-outline-variant/40 shadow-xs overflow-hidden flex flex-col">
          {/* Tabs */}
          <div className="px-space-md pt-space-md bg-surface-container-low/40 border-b border-outline-variant/30 flex flex-col lg:flex-row lg:items-center justify-between gap-space-md">
            <div className="flex items-center gap-1 overflow-x-auto pb-space-xs lg:pb-0 text-xs">
              <button 
                className={`px-4 py-2.5 rounded-t-lg font-medium transition-all flex items-center gap-2 cursor-pointer ${
                  activeTab === 'all' 
                    ? 'bg-surface-container-lowest text-primary font-semibold border-b-2 border-primary shadow-xs' 
                    : 'text-on-surface-variant hover:text-on-surface hover:bg-surface-container'
                }`}
                onClick={() => setActiveTab('all')}
              >
                <span>All Assessments</span>
                <span className={`px-1.5 py-0.2 rounded-full font-mono text-[11px] ${
                  activeTab === 'all' ? 'bg-primary-fixed text-on-primary-fixed font-bold' : 'bg-surface-container text-on-surface-variant'
                }`}>
                  {stats.all}
                </span>
              </button>

              <button 
                className={`px-4 py-2.5 rounded-t-lg font-medium transition-all flex items-center gap-2 cursor-pointer ${
                  activeTab === 'upcoming' 
                    ? 'bg-surface-container-lowest text-primary font-semibold border-b-2 border-primary shadow-xs' 
                    : 'text-on-surface-variant hover:text-on-surface hover:bg-surface-container'
                }`}
                onClick={() => setActiveTab('upcoming')}
              >
                <span>Upcoming</span>
                <span className={`px-1.5 py-0.2 rounded-full font-mono text-[11px] ${
                  activeTab === 'upcoming' ? 'bg-primary-fixed text-on-primary-fixed font-bold' : 'bg-surface-container text-on-surface-variant'
                }`}>
                  {stats.upcoming}
                </span>
              </button>

              <button 
                className={`px-4 py-2.5 rounded-t-lg font-medium transition-all flex items-center gap-2 cursor-pointer ${
                  activeTab === 'submitted' 
                    ? 'bg-surface-container-lowest text-primary font-semibold border-b-2 border-primary shadow-xs' 
                    : 'text-on-surface-variant hover:text-on-surface hover:bg-surface-container'
                }`}
                onClick={() => setActiveTab('submitted')}
              >
                <span>Submitted</span>
                <span className={`px-1.5 py-0.2 rounded-full font-mono text-[11px] ${
                  activeTab === 'submitted' ? 'bg-primary-fixed text-on-primary-fixed font-bold' : 'bg-surface-container text-on-surface-variant'
                }`}>
                  {stats.submitted}
                </span>
              </button>

              <button 
                className={`px-4 py-2.5 rounded-t-lg font-medium transition-all flex items-center gap-2 cursor-pointer ${
                  activeTab === 'completed' 
                    ? 'bg-surface-container-lowest text-primary font-semibold border-b-2 border-primary shadow-xs' 
                    : 'text-on-surface-variant hover:text-on-surface hover:bg-surface-container'
                }`}
                onClick={() => setActiveTab('completed')}
              >
                <span>Completed</span>
                <span className={`px-1.5 py-0.2 rounded-full font-mono text-[11px] ${
                  activeTab === 'completed' ? 'bg-primary-fixed text-on-primary-fixed font-bold' : 'bg-surface-container text-on-surface-variant'
                }`}>
                  {stats.completed}
                </span>
              </button>
            </div>
          </div>

          {/* Filter Bar */}
          <div className="p-3 bg-surface-container-low/20 border-b border-outline-variant/30 flex flex-col md:flex-row gap-3 items-center justify-between">
            <div className="relative w-full md:w-80 lg:w-96">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-outline w-4 h-4" />
              <input 
                className="w-full pl-9 pr-8 py-1.5 bg-surface-container-lowest rounded-lg text-xs text-on-surface placeholder:text-outline border border-outline-variant/40 focus:outline-none focus:border-primary transition-all" 
                placeholder="Search exams by title or course..." 
                type="text"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
              />
              {searchQuery && (
                <button className="absolute right-2.5 top-1/2 -translate-y-1/2 text-outline hover:text-on-surface cursor-pointer" onClick={() => setSearchQuery('')}>
                  <X className="w-3.5 h-3.5" />
                </button>
              )}
            </div>
            
            <div className="flex items-center flex-wrap gap-2.5 w-full md:w-auto justify-end">
              <div className="relative">
                <select 
                  className="appearance-none bg-surface-container-lowest pl-3 pr-8 py-1.5 rounded-lg text-xs text-on-surface border border-outline-variant/40 focus:outline-none cursor-pointer"
                  value={statusFilter}
                  onChange={(e) => setStatusFilter(e.target.value)}
                >
                  <option value="all">All Statuses</option>
                  <option value="upcoming">Upcoming</option>
                  <option value="submitted">Submitted</option>
                  <option value="completed">Completed</option>
                  <option value="missed">Missed</option>
                </select>
                <ChevronDown className="absolute right-2 top-1/2 -translate-y-1/2 text-outline w-3.5 h-3.5 pointer-events-none" />
              </div>
            </div>
          </div>

          {/* Assessments Table */}
          <div className="overflow-x-auto w-full">
            <table className="w-full text-left border-collapse text-xs">
              <thead className="bg-surface-container-low text-on-surface-variant font-label-xs uppercase tracking-wider border-b border-outline-variant/30">
                <tr>
                  <th className="py-2.5 px-4 font-semibold">Exam Name &amp; Course</th>
                  <th className="py-2.5 px-4 font-semibold">Scheduled Date</th>
                  <th className="py-2.5 px-4 font-semibold">Status</th>
                  <th className="py-2.5 px-4 font-semibold">Score</th>
                  <th className="py-2.5 px-4 font-semibold text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-outline-variant/20 text-on-surface">
                {filteredExams.length === 0 ? (
                  <tr>
                    <td colSpan={5} className="py-12 text-center text-outline">
                      No assessments found matching the criteria.
                    </td>
                  </tr>
                ) : (
                  filteredExams.map(exam => (
                    <tr key={exam.id} className="hover:bg-surface-container-low/60 transition-colors">
                      <td className="py-3 px-4">
                        <Link href={`/student/exams/${exam.id}`} className="font-semibold text-on-surface hover:text-primary transition-colors">
                          {exam.title}
                        </Link>
                        <div className="text-[11px] font-mono text-on-surface-variant mt-0.5">{exam.courseClassCode || 'SE-PRACTICAL'}</div>
                      </td>
                      <td className="py-3 px-4 text-on-surface-variant">
                        {exam.startTime ? new Date(exam.startTime).toLocaleString() : 'Open Window'}
                      </td>
                      <td className="py-3 px-4">
                        <span className={`px-2 py-0.5 rounded-full text-[10px] font-semibold ${
                          exam.derivedStatus === 'UPCOMING' 
                            ? 'bg-blue-50 text-blue-700 border border-blue-200' 
                            : exam.derivedStatus === 'COMPLETED' 
                            ? 'bg-emerald-50 text-emerald-700 border border-emerald-200' 
                            : 'bg-amber-50 text-amber-700 border border-amber-200'
                        }`}>
                          {exam.derivedStatus}
                        </span>
                      </td>
                      <td className="py-3 px-4">
                        {exam.submission ? (
                          <span className="font-mono bg-surface-container px-2 py-0.5 rounded font-bold text-xs text-on-surface">
                            {exam.submission.totalScore ?? 0} <span className="text-outline text-[10px]">/ 100</span>
                          </span>
                        ) : (
                          <span className="text-outline text-xs">--</span>
                        )}
                      </td>
                      <td className="py-3 px-4 text-right">
                        <div className="flex items-center justify-end gap-2">
                          {exam.submission && (
                            <Link 
                              href={`/student/submissions/${exam.submission.id}`} 
                              className="px-2.5 py-1 bg-emerald-50 hover:bg-emerald-100 text-emerald-800 rounded-lg text-xs font-medium transition-colors border border-emerald-200"
                            >
                              Result
                            </Link>
                          )}
                          <Link 
                            href={`/student/exams/${exam.id}`} 
                            className="inline-flex items-center gap-1 px-3 py-1 bg-primary text-on-primary rounded-lg text-xs font-medium hover:bg-primary/90 transition-colors shadow-xs"
                          >
                            <span>Open</span>
                            <ExternalLink className="w-3 h-3" />
                          </Link>
                        </div>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>

          <div className="px-4 py-2.5 bg-surface-container-low/40 border-t border-outline-variant/30 flex items-center justify-between text-xs text-outline">
            <span>
              Showing <strong className="text-on-surface">{filteredExams.length}</strong> of <strong className="text-on-surface">{correlatedExams.length}</strong> assessments
            </span>
          </div>
        </div>
      </div>
    </div>
  );
}
