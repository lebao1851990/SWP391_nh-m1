'use client';

import { useState, useMemo } from 'react';
import { useTeacherExams } from '../hooks/use-teacher-exams';
import { AppLink as Link } from '@/components/ui/app-link';

export function TeacherExamsView() {
  const { data: exams, isLoading, isError, error } = useTeacherExams();
  const [searchQuery, setSearchQuery] = useState('');
  const [classFilter, setClassFilter] = useState('all');

  const filteredExams = useMemo(() => {
    if (!exams) return [];
    return exams.filter(exam => {
      if (classFilter !== 'all' && exam.courseCode !== classFilter) return false;
      if (searchQuery) {
        const query = searchQuery.toLowerCase();
        return exam.title?.toLowerCase().includes(query) || exam.courseCode?.toLowerCase().includes(query) || exam.id?.toLowerCase().includes(query);
      }
      return true;
    });
  }, [exams, searchQuery, classFilter]);

  const uniqueClasses = useMemo(() => {
    if (!exams) return [];
    return Array.from(new Set(exams.map(e => e.courseCode).filter(Boolean)));
  }, [exams]);

  const stats = useMemo(() => {
    if (!exams) return { published: 0, drafts: 0, total: 0 };
    return {
      published: exams.filter(e => e.status !== 'draft').length,
      drafts: exams.filter(e => e.status === 'draft').length,
      total: exams.length,
    };
  }, [exams]);

  if (isLoading) {
    return <div className="p-8 text-center text-on-surface-variant font-label-md">Loading exams...</div>;
  }

  if (isError) {
    return (
      <div className="flex flex-col h-64 items-center justify-center p-space-lg text-center gap-space-sm">
        <span className="material-symbols-outlined text-error text-[48px]">error</span>
        <h2 className="font-headline-md text-headline-md text-on-surface">Error loading exams</h2>
        <p className="font-body-md text-on-surface-variant">{(error as Error).message}</p>
      </div>
    );
  }

  return (
    <div className="max-w-7xl mx-auto px-space-lg py-space-lg">
      <div className="flex flex-col w-full">
        {/* Page Header */}
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-space-md mb-space-lg">
          <div className="flex flex-col">
            <div className="flex items-center gap-space-sm">
              <h1 className="font-headline-lg text-headline-lg text-on-surface tracking-tight">Exams</h1>
              <span className="font-label-sm text-label-sm px-2 py-0.5 rounded bg-surface-container text-on-surface-variant font-medium">Fall 2026</span>
            </div>
            <p className="font-body-md text-body-md text-on-surface-variant mt-0.5">Manage your programming exams, live AST runs, and batch assessments.</p>
          </div>
        </div>

        {/* Filter & Utility Bar */}
        <div className="bg-surface-container-lowest rounded-xl p-3 mb-space-md shadow-sm flex flex-col xl:flex-row items-stretch xl:items-center justify-between gap-3">
          <div className="flex flex-wrap items-center gap-2 flex-1 min-w-0">
            <div className="relative min-w-[240px] flex-1 max-w-sm">
              <span className="material-symbols-outlined absolute left-2.5 top-1/2 -translate-y-1/2 text-outline text-[18px]">search</span>
              <input 
                className="w-full pl-8 pr-3 py-1.5 bg-surface-container-low rounded text-on-surface font-body-sm text-body-sm focus:outline-none focus:bg-surface-container-lowest transition-all" 
                placeholder="Search exams by title, course, or ID..." 
                type="text"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
              />
            </div>
            <div className="relative">
              <select 
                className="appearance-none bg-surface-container-low text-on-surface font-label-md text-label-md pl-3 pr-8 py-1.5 rounded focus:outline-none cursor-pointer"
                value={classFilter}
                onChange={(e) => setClassFilter(e.target.value)}
              >
                <option value="all">All Classes</option>
                {uniqueClasses.map(c => <option key={c} value={c}>{c}</option>)}
              </select>
              <span className="material-symbols-outlined absolute right-2 top-1/2 -translate-y-1/2 text-outline text-[16px] pointer-events-none">expand_more</span>
            </div>
          </div>
        </div>

        {/* Main Table Section */}
        <div className="bg-surface-container-lowest rounded-xl shadow-sm overflow-hidden flex flex-col mb-space-lg" id="tableContainer">
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse min-w-[1020px]">
              <thead>
                <tr className="bg-surface-container-low/70 text-on-surface-variant font-label-sm text-label-sm select-none">
                  <th className="py-3 px-4 font-semibold">Exam Title</th>
                  <th className="py-3 px-3 font-semibold">Class / Cohort</th>
                  <th className="py-3 px-3 font-semibold">Date & Time</th>
                  <th className="py-3 px-3 font-semibold">Submissions</th>
                  <th className="py-3 px-3 font-semibold">Status</th>
                  <th className="py-3 px-4 text-right font-semibold">Actions</th>
                </tr>
              </thead>
              <tbody className="text-on-surface">
                {filteredExams.length === 0 ? (
                  <tr><td colSpan={6} className="py-8 text-center text-outline font-label-md">No exams found</td></tr>
                ) : (
                  filteredExams.map(exam => (
                    <tr key={exam.id} className="border-b border-outline-variant/20 hover:bg-surface-container-low transition-colors">
                      <td className="py-3 px-4">
                        <div className="font-semibold">{exam.title}</div>
                      </td>
                      <td className="py-3 px-3">
                        <span className="font-label-sm px-2 py-0.5 rounded bg-surface-container-high font-medium text-on-surface">{exam.courseCode}</span>
                        <div className="text-label-sm text-on-surface-variant mt-0.5">{exam.courseName}</div>
                      </td>
                      <td className="py-3 px-3">
                        {exam.examDate ? new Date(exam.examDate).toLocaleString() : 'TBD'}
                      </td>
                      <td className="py-3 px-3">
                        <span className="font-mono text-primary font-bold">{exam.submittedCount || 0}</span>
                        <span className="text-outline"> / {exam.enrolledCount || 0}</span>
                      </td>
                      <td className="py-3 px-3">
                        <span className={`px-2 py-0.5 rounded font-label-sm text-label-sm font-semibold ${exam.status === 'PUBLISHED' ? 'bg-tertiary-fixed text-on-tertiary-fixed' : 'bg-surface-container text-on-surface-variant'}`}>
                          {exam.status || 'DRAFT'}
                        </span>
                      </td>
                      <td className="py-3 px-4 text-right">
                        <Link href={`/teacher/exams/${exam.id}`} className="px-3 py-1.5 bg-primary-container text-on-primary rounded font-label-sm hover:bg-primary transition-colors">
                          View Roster
                        </Link>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
          <div className="px-4 py-3 bg-surface-container-low/40 flex flex-col sm:flex-row items-center justify-between gap-3">
            <span className="font-label-sm text-label-sm text-outline">
              Showing <strong className="text-on-surface">{filteredExams.length}</strong> of <strong className="text-on-surface">{stats.total}</strong> registered exams
            </span>
          </div>
        </div>
      </div>
    </div>
  );
}
