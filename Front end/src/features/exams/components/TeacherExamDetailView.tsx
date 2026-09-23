'use client';

import { useState, useMemo } from 'react';
import { useTeacherExamDetail, useTeacherExamSubmissions } from '../hooks/use-teacher-exams';
import { AxiosError } from 'axios';
import { AppLink as Link } from '@/components/ui/app-link';

interface TeacherExamDetailViewProps {
  examId?: string;
}

export function TeacherExamDetailView({ examId }: TeacherExamDetailViewProps) {
  const { data: exam, isLoading: isLoadingExam, isError: isErrorExam, error: examError } = useTeacherExamDetail(examId || '');
  const { data: submissions, isLoading: isLoadingSubmissions } = useTeacherExamSubmissions(examId || '');

  const [searchQuery, setSearchQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState('all');

  const filteredSubmissions = useMemo(() => {
    if (!submissions) return [];
    return submissions.filter(sub => {
      if (statusFilter !== 'all' && sub.status?.toLowerCase() !== statusFilter.toLowerCase()) return false;
      if (searchQuery) {
        const query = searchQuery.toLowerCase();
        return sub.studentName?.toLowerCase().includes(query) || sub.studentEmail?.toLowerCase().includes(query);
      }
      return true;
    });
  }, [submissions, searchQuery, statusFilter]);

  const stats = useMemo(() => {
    if (!submissions) return { all: 0, submitted: 0, graded: 0 };
    return {
      all: submissions.length,
      submitted: submissions.filter(s => s.status === 'SUBMITTED').length,
      graded: submissions.filter(s => s.status === 'GRADED').length,
    };
  }, [submissions]);

  if (!examId) return null;

  if (isLoadingExam || isLoadingSubmissions) {
    return (
      <div className="flex h-64 items-center justify-center">
        <div className="text-on-surface-variant font-label-md">Loading exam details...</div>
      </div>
    );
  }

  if (isErrorExam) {
    const status = (examError as AxiosError)?.response?.status;
    if (status === 404 || status === 403) {
      return (
        <div className="flex flex-col h-64 items-center justify-center p-space-lg text-center gap-space-sm">
          <span className="material-symbols-outlined text-outline text-[48px]">search_off</span>
          <h2 className="font-headline-md text-headline-md text-on-surface">Exam Not Found</h2>
          <p className="font-body-md text-on-surface-variant">The requested exam does not exist or you do not have permission.</p>
        </div>
      );
    }
    return (
      <div className="flex flex-col h-64 items-center justify-center p-space-lg text-center gap-space-sm">
        <span className="material-symbols-outlined text-error text-[48px]">error</span>
        <h2 className="font-headline-md text-headline-md text-on-surface">Error</h2>
        <p className="font-body-md text-on-surface-variant">{(examError as Error).message}</p>
      </div>
    );
  }

  if (!exam) return null;

  return (
    <div className="max-w-7xl mx-auto px-space-lg py-space-lg">
      <div className="flex flex-col w-full space-y-space-lg">
        {/* Top Navigation / Breadcrumbs & Action Bar */}
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-space-md">
          <div className="space-y-1.5">
            <nav className="flex items-center gap-2 text-on-surface-variant font-label-sm text-label-sm">
              <Link className="hover:text-primary transition-colors flex items-center gap-1" href="/teacher/exams">
                <span className="material-symbols-outlined text-[14px]">home</span> Exams
              </Link>
              <span className="text-outline-variant">/</span>
              <span className="hover:text-on-surface transition-colors cursor-pointer">{exam.title} ({exam.courseCode})</span>
            </nav>
            <div className="flex flex-wrap items-center gap-3">
              <h1 className="font-headline-lg text-headline-lg text-on-surface tracking-tight">{exam.title}</h1>
              <div className="flex items-center gap-1.5 px-2.5 py-0.5 rounded-full bg-tertiary-fixed text-on-tertiary-fixed font-label-sm text-label-sm font-semibold">
                {exam.status || 'Draft'}
              </div>
            </div>
            <div className="flex flex-wrap items-center gap-2 pt-1 font-label-sm text-label-sm text-on-surface-variant">
              <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded bg-surface-container font-medium text-on-surface">
                <span className="material-symbols-outlined text-[15px] text-primary">menu_book</span> {exam.courseCode}
              </span>
              <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded bg-surface-container-high text-on-surface">
                <span className="material-symbols-outlined text-[15px] text-outline">schedule</span>
                Date: {exam.examDate ? new Date(exam.examDate).toLocaleString() : 'TBD'}
              </span>
            </div>
          </div>
          <div className="flex flex-wrap items-center gap-2 self-start md:self-center">
            <button className="inline-flex items-center gap-1.5 px-3 py-2 rounded bg-surface-container-lowest text-on-surface font-label-md text-label-md hover:bg-surface-container transition-all shadow-sm" type="button">
              <span className="material-symbols-outlined text-[16px] text-on-surface-variant">edit</span> Edit Exam
            </button>
          </div>
        </div>

        {/* Filter & Search Toolbar Card */}
        <div className="p-3.5 rounded-xl bg-surface-container-lowest shadow-sm flex flex-col lg:flex-row items-stretch lg:items-center justify-between gap-3">
          <div className="flex flex-wrap items-center gap-1.5 overflow-x-auto pb-1 lg:pb-0">
            <button 
              className={`px-3 py-1.5 rounded font-label-md text-label-md transition-all font-semibold ${statusFilter === 'all' ? 'bg-primary-container text-on-primary shadow-xs' : 'text-on-surface-variant hover:bg-surface-container hover:text-on-surface'}`}
              onClick={() => setStatusFilter('all')}
            >
              All <span className="ml-1 opacity-80">({stats.all})</span>
            </button>
            <button 
              className={`px-3 py-1.5 rounded font-label-md text-label-md transition-all font-semibold ${statusFilter === 'submitted' ? 'bg-primary-container text-on-primary shadow-xs' : 'text-on-surface-variant hover:bg-surface-container hover:text-on-surface'}`}
              onClick={() => setStatusFilter('submitted')}
            >
              Submitted <span className="ml-1 opacity-80">({stats.submitted})</span>
            </button>
            <button 
              className={`px-3 py-1.5 rounded font-label-md text-label-md transition-all font-semibold ${statusFilter === 'graded' ? 'bg-primary-container text-on-primary shadow-xs' : 'text-on-surface-variant hover:bg-surface-container hover:text-on-surface'}`}
              onClick={() => setStatusFilter('graded')}
            >
              Graded <span className="ml-1 opacity-80">({stats.graded})</span>
            </button>
          </div>
          <div className="flex items-center gap-2 flex-1 lg:max-w-md justify-end">
            <div className="relative w-full">
              <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-outline text-[18px]">search</span>
              <input 
                className="w-full pl-9 pr-8 py-1.5 bg-surface-container-low rounded text-on-surface font-body-sm text-body-sm focus:outline-none focus:bg-surface-container" 
                placeholder="Search student name or email..." 
                type="text"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
              />
              {searchQuery && (
                <button className="absolute right-2.5 top-1/2 -translate-y-1/2 text-outline hover:text-on-surface" onClick={() => setSearchQuery('')}>
                  <span className="material-symbols-outlined text-[16px]">close</span>
                </button>
              )}
            </div>
          </div>
        </div>

        {/* Detailed Submissions Table Card */}
        <div className="rounded-xl bg-surface-container-lowest shadow-sm overflow-hidden flex flex-col">
          <div className="overflow-x-auto">
            <table className="w-full text-left font-body-md text-body-md border-collapse" id="submissions-table">
              <thead>
                <tr className="bg-surface-container-lowest text-on-surface-variant font-label-sm text-label-sm uppercase tracking-wider border-b border-outline-variant/30">
                  <th className="py-3 px-4 font-semibold">Student Name</th>
                  <th className="py-3 px-4 font-semibold">Email</th>
                  <th className="py-3 px-4 font-semibold">Submission Time</th>
                  <th className="py-3 px-4 font-semibold">Score</th>
                  <th className="py-3 px-4 font-semibold">Status</th>
                  <th className="py-3 px-4 font-semibold text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="text-on-surface">
                {filteredSubmissions.length === 0 ? (
                  <tr>
                    <td colSpan={6} className="py-8 text-center text-outline font-label-md">
                      No student submissions match query
                    </td>
                  </tr>
                ) : (
                  filteredSubmissions.map(sub => (
                    <tr key={sub.id} className="border-b border-outline-variant/20 hover:bg-surface-container-low transition-colors">
                      <td className="py-3 px-4 font-semibold">{sub.studentName}</td>
                      <td className="py-3 px-4 text-on-surface-variant text-sm">{sub.studentEmail}</td>
                      <td className="py-3 px-4">{sub.submittedAt ? new Date(sub.submittedAt).toLocaleString() : 'N/A'}</td>
                      <td className="py-3 px-4 font-mono">{sub.totalScore || 0}</td>
                      <td className="py-3 px-4">
                        <span className={`px-2 py-0.5 rounded font-label-sm text-label-sm font-semibold ${sub.status === 'GRADED' ? 'bg-tertiary-fixed text-on-tertiary-fixed' : 'bg-surface-container-high text-on-surface-variant'}`}>
                          {sub.status || 'PENDING'}
                        </span>
                      </td>
                      <td className="py-3 px-4 text-right">
                        <Link href={`/teacher/submissions/${sub.id}`} className="px-3 py-1.5 bg-primary-container text-on-primary rounded font-label-sm hover:bg-primary transition-colors">
                          View Code
                        </Link>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
          <div className="px-4 py-3 bg-surface-container-low/60 flex items-center justify-between text-on-surface-variant font-label-sm text-label-sm">
            <span>Showing <strong className="text-on-surface">{filteredSubmissions.length}</strong> of <strong className="text-on-surface">{stats.all}</strong> submissions</span>
          </div>
        </div>
      </div>
    </div>
  );
}
