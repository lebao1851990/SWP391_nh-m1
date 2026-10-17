'use client';

import React, { useState, useMemo, useEffect } from 'react';
import { useTeacherExams, useTeacherClasses, useCreateExam } from '../hooks/use-teacher-exams';
import { AppLink as Link } from '@/components/ui/app-link';
import { 
  Search, 
  PlusCircle, 
  CheckCircle2, 
  X, 
  ChevronDown, 
  FileCode2, 
  Calendar, 
  Clock, 
  ExternalLink,
  AlertCircle,
  Loader2,
  Users
} from 'lucide-react';
import { toast } from 'sonner';

export function TeacherExamsView() {
  const { data: exams, isLoading, isError, error } = useTeacherExams();
  const { data: classes } = useTeacherClasses();
  const createExam = useCreateExam();

  const [searchQuery, setSearchQuery] = useState(() => {
    if (typeof window !== 'undefined') {
      return new URLSearchParams(window.location.search).get('search') || '';
    }
    return '';
  });
  const [classFilter, setClassFilter] = useState('all');

  // Modal State
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(() => {
    if (typeof window !== 'undefined') {
      return new URLSearchParams(window.location.search).get('action') === 'create';
    }
    return false;
  });
  const [examTitle, setExamTitle] = useState('');
  const [selectedClassIds, setSelectedClassIds] = useState<string[]>([]);
  const [examDate, setExamDate] = useState('');
  const [durationMinutes, setDurationMinutes] = useState(90);
  const [formError, setFormError] = useState('');
  const [successMessage, setSuccessMessage] = useState('');

  const filteredExams = useMemo(() => {
    if (!exams) return [];
    return exams.filter(exam => {
      if (classFilter !== 'all') {
        const hasClass = (exam.classCodes && exam.classCodes.includes(classFilter)) || exam.courseCode === classFilter;
        if (!hasClass) return false;
      }
      if (searchQuery) {
        const query = searchQuery.toLowerCase();
        const matchesClassCodes = exam.classCodes?.some(c => c.toLowerCase().includes(query));
        return (
          exam.title?.toLowerCase().includes(query) ||
          exam.courseCode?.toLowerCase().includes(query) ||
          matchesClassCodes ||
          exam.id?.toLowerCase().includes(query)
        );
      }
      return true;
    });
  }, [exams, searchQuery, classFilter]);

  const uniqueClasses = useMemo(() => {
    if (!exams) return [];
    const set = new Set<string>();
    exams.forEach(e => {
      if (e.classCodes && e.classCodes.length > 0) {
        e.classCodes.forEach(code => set.add(code));
      } else if (e.courseCode) {
        set.add(e.courseCode);
      }
    });
    return Array.from(set).filter(Boolean);
  }, [exams]);

  const stats = useMemo(() => {
    if (!exams) return { published: 0, drafts: 0, total: 0 };
    return {
      published: exams.filter(e => e.status !== 'draft').length,
      drafts: exams.filter(e => e.status === 'draft').length,
      total: exams.length,
    };
  }, [exams]);

  const handleOpenCreateModal = () => {
    setExamTitle('');
    setSelectedClassIds(classes && classes.length > 0 ? [classes[0].id] : []);
    const now = new Date();
    now.setMinutes(now.getMinutes() - now.getTimezoneOffset());
    setExamDate(now.toISOString().slice(0, 16));
    setDurationMinutes(90);
    setFormError('');
    setIsCreateModalOpen(true);
  };

  const handleToggleClass = (classId: string) => {
    setSelectedClassIds(prev => 
      prev.includes(classId) 
        ? prev.filter(id => id !== classId)
        : [...prev, classId]
    );
  };

  const handleCreateSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!examTitle.trim()) {
      setFormError('Exam title is required.');
      return;
    }
    if (selectedClassIds.length === 0) {
      setFormError('Please select at least one course class for the exam.');
      return;
    }
    if (!examDate) {
      setFormError('Exam date and start time are required.');
      return;
    }
    if (durationMinutes <= 0) {
      setFormError('Duration must be greater than 0 minutes.');
      return;
    }

    setFormError('');
    createExam.mutate(
      {
        title: examTitle.trim(),
        classId: selectedClassIds[0],
        classIds: selectedClassIds,
        date: new Date(examDate).toISOString(),
        durationMinutes: Number(durationMinutes),
      },
      {
        onSuccess: (newExam) => {
          setIsCreateModalOpen(false);
          const msg = `Exam "${newExam.title}" created successfully!`;
          setSuccessMessage(msg);
          toast.success(msg);
          setTimeout(() => setSuccessMessage(''), 5000);
        },
        onError: (err: unknown) => {
          const msg = (err as { response?: { data?: { message?: string } }; message?: string })?.response?.data?.message || (err as { message?: string })?.message || 'Failed to create exam';
          setFormError(msg);
          toast.error(msg);
        },
      }
    );
  };

  if (isLoading) {
    return (
      <div className="flex flex-col h-72 items-center justify-center gap-3">
        <div className="w-8 h-8 rounded-full border-2 border-primary border-t-transparent animate-spin" />
        <p className="text-on-surface-variant font-label-md text-xs">Loading course exams...</p>
      </div>
    );
  }

  if (isError) {
    return (
      <div className="max-w-xl mx-auto my-12 p-6 rounded-2xl bg-surface-container-lowest border border-rose-200/60 shadow-lg flex flex-col items-center text-center gap-3">
        <div className="p-3 rounded-full bg-rose-50 text-rose-600">
          <AlertCircle className="w-8 h-8" />
        </div>
        <h2 className="font-headline-md text-headline-md text-on-surface">Error Loading Exams</h2>
        <p className="font-body-md text-on-surface-variant text-xs">{(error as Error).message}</p>
        <button 
          onClick={() => window.location.reload()}
          className="mt-2 px-4 py-2 bg-primary text-on-primary rounded-lg text-xs font-medium hover:bg-primary/90 transition-all cursor-pointer"
        >
          Retry Connection
        </button>
      </div>
    );
  }

  return (
    <div className="w-full max-w-7xl mx-auto px-4 sm:px-space-lg py-space-lg min-w-0">
      <div className="flex flex-col w-full min-w-0">
        {/* Success Alert */}
        {successMessage && (
          <div className="mb-4 p-3.5 rounded-xl bg-emerald-50 border border-emerald-200 text-emerald-800 flex items-center justify-between text-xs animate-in fade-in">
            <div className="flex items-center gap-2">
              <CheckCircle2 className="w-4 h-4 text-emerald-600 shrink-0" />
              <span className="font-semibold">{successMessage}</span>
            </div>
            <button onClick={() => setSuccessMessage('')} className="text-emerald-700 hover:text-emerald-900 cursor-pointer">
              <X className="w-3.5 h-3.5" />
            </button>
          </div>
        )}

        {/* Page Header */}
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-space-md mb-space-lg">
          <div className="flex flex-col">
            <div className="flex items-center gap-space-sm">
              <h1 className="font-headline-lg text-headline-lg text-on-surface tracking-tight">Exam Management</h1>
              <span className="font-label-xs text-xs px-2 py-0.5 rounded bg-surface-container border border-outline-variant/30 text-on-surface-variant font-medium">Fall 2026 Term</span>
            </div>
            <p className="font-body-md text-body-md text-on-surface-variant mt-0.5 text-xs sm:text-sm">
              Create, configure, and monitor your course programming assessments and AST detection runs.
            </p>
          </div>
          <button
            onClick={handleOpenCreateModal}
            className="inline-flex items-center gap-2 px-4 py-2 bg-primary text-on-primary rounded-lg font-label-md text-xs hover:bg-primary/90 transition-colors shadow-sm self-start md:self-auto cursor-pointer font-medium"
            id="btn-create-exam"
          >
            <PlusCircle className="w-4 h-4" />
            <span>+ Create Exam</span>
          </button>
        </div>

        {/* Filter & Search Bar */}
        <div className="bg-surface-container-lowest rounded-xl p-3 mb-space-md border border-outline-variant/40 shadow-xs flex flex-col xl:flex-row items-stretch xl:items-center justify-between gap-3">
          <div className="flex flex-wrap items-center gap-2 flex-1 min-w-0">
            <div className="relative min-w-[240px] flex-1 max-w-sm">
              <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 text-outline w-4 h-4" />
              <input 
                className="w-full pl-8 pr-3 py-1.5 bg-surface-container-low rounded-lg text-on-surface font-body-sm text-xs border border-outline-variant/40 focus:outline-none focus:border-primary transition-all" 
                placeholder="Search exams by title, course, or ID..." 
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

            <div className="relative">
              <select 
                className="appearance-none bg-surface-container-low text-on-surface font-label-md text-xs pl-3 pr-8 py-1.5 rounded-lg border border-outline-variant/40 focus:outline-none cursor-pointer"
                value={classFilter}
                onChange={(e) => setClassFilter(e.target.value)}
              >
                <option value="all">All Cohorts / Classes</option>
                {uniqueClasses.map(c => <option key={c} value={c}>{c}</option>)}
              </select>
              <ChevronDown className="absolute right-2 top-1/2 -translate-y-1/2 text-outline w-3.5 h-3.5 pointer-events-none" />
            </div>
          </div>
        </div>

        {/* Main Table Section */}
        <div className="bg-surface-container-lowest rounded-xl border border-outline-variant/40 shadow-xs overflow-hidden flex flex-col mb-space-lg w-full min-w-0" id="tableContainer">
          <div className="overflow-x-auto w-full">
            <table className="w-full text-left border-collapse min-w-[900px] text-xs" id="teacher-exams-table">
              <thead>
                <tr className="bg-surface-container-low text-on-surface-variant font-label-xs uppercase tracking-wider border-b border-outline-variant/30">
                  <th className="py-2.5 px-4 font-semibold">Exam Title</th>
                  <th className="py-2.5 px-4 font-semibold">Cohort</th>
                  <th className="py-2.5 px-4 font-semibold">Scheduled Date</th>
                  <th className="py-2.5 px-4 font-semibold">Duration</th>
                  <th className="py-2.5 px-4 font-semibold">Status</th>
                  <th className="py-2.5 px-4 text-right font-semibold">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-outline-variant/20 text-on-surface">
                {filteredExams.length === 0 ? (
                  <tr>
                    <td colSpan={6} className="py-12 text-center text-outline font-label-md">
                      <div className="flex flex-col items-center justify-center gap-2">
                        <FileCode2 className="w-8 h-8 text-outline" />
                        <p className="font-headline-sm text-on-surface text-sm">No exams found</p>
                        <p className="text-outline text-xs">
                          {searchQuery || classFilter !== 'all'
                            ? 'No exams match your search or cohort filter criteria.'
                            : 'You have not created any exams for your courses yet.'}
                        </p>
                      </div>
                    </td>
                  </tr>
                ) : (
                  filteredExams.map(exam => (
                    <tr key={exam.id} className="hover:bg-surface-container-low/60 transition-colors">
                      <td className="py-3 px-4">
                        <Link href={`/teacher/exams/${exam.id}`} className="font-semibold text-on-surface hover:text-primary transition-colors">
                          {exam.title}
                        </Link>
                      </td>
                      <td className="py-3 px-4">
                        <div className="flex flex-wrap gap-1">
                          {exam.classCodes && exam.classCodes.length > 0 ? (
                            exam.classCodes.map(code => (
                              <span key={code} className="font-mono px-1.5 py-0.5 rounded bg-surface-container text-[11px] font-semibold text-on-surface-variant">
                                {code}
                              </span>
                            ))
                          ) : (
                            <span className="font-mono px-2 py-0.5 rounded bg-surface-container text-[11px] font-semibold text-on-surface-variant">
                              {exam.courseCode || 'SE-GENERAL'}
                            </span>
                          )}
                        </div>
                      </td>
                      <td className="py-3 px-4 text-on-surface-variant">
                        {exam.examDate ? new Date(exam.examDate).toLocaleString() : 'Open Schedule'}
                      </td>
                      <td className="py-3 px-4 text-on-surface-variant">
                        {exam.duration || 90} mins
                      </td>
                      <td className="py-3 px-4">
                        <span className="inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200">
                          Active
                        </span>
                      </td>
                      <td className="py-3 px-4 text-right">
                        <Link 
                          href={`/teacher/exams/${exam.id}`} 
                          className="inline-flex items-center gap-1 px-3 py-1 bg-surface-container hover:bg-surface-container-high text-primary rounded-lg font-medium transition-colors"
                        >
                          <span>Manage</span>
                          <ExternalLink className="w-3 h-3" />
                        </Link>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
          <div className="px-4 py-2.5 bg-surface-container-low/40 border-t border-outline-variant/30 flex flex-col sm:flex-row items-center justify-between gap-3 text-xs text-outline">
            <span>
              Showing <strong className="text-on-surface">{filteredExams.length}</strong> of <strong className="text-on-surface">{stats.total}</strong> registered exams
            </span>
          </div>
        </div>

        {/* Create Exam Modal */}
        {isCreateModalOpen && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-xs p-4 animate-in fade-in duration-150">
            <div className="bg-surface-container-lowest rounded-2xl max-w-lg w-full p-6 shadow-2xl border border-outline-variant/40 flex flex-col gap-4 animate-in zoom-in-95 duration-150">
              <div className="flex items-center justify-between border-b border-outline-variant/30 pb-3">
                <div className="flex items-center gap-2">
                  <PlusCircle className="w-5 h-5 text-primary" />
                  <h2 className="font-headline-sm text-headline-sm text-on-surface font-semibold">Create New Exam</h2>
                </div>
                <button
                  type="button"
                  onClick={() => setIsCreateModalOpen(false)}
                  className="text-outline hover:text-on-surface transition-colors p-1 rounded-lg cursor-pointer"
                >
                  <X className="w-4 h-4" />
                </button>
              </div>

              <form onSubmit={handleCreateSubmit} className="flex flex-col gap-4">
                {formError && (
                  <div className="p-3 rounded-lg bg-rose-50 border border-rose-200 text-rose-700 flex items-center gap-2 text-xs">
                    <AlertCircle className="w-4 h-4 shrink-0" />
                    <span>{formError}</span>
                  </div>
                )}

                <div className="flex flex-col gap-1">
                  <label className="text-xs font-semibold text-on-surface">Exam Title *</label>
                  <input
                    type="text"
                    required
                    placeholder="e.g. CS201 Data Structures Midterm Practical"
                    value={examTitle}
                    onChange={(e) => setExamTitle(e.target.value)}
                    className="w-full px-3 py-2 bg-surface-container-low border border-outline-variant/40 rounded-lg text-xs text-on-surface focus:outline-none focus:border-primary transition-colors"
                  />
                </div>

                <div className="flex flex-col gap-1.5">
                  <div className="flex items-center justify-between">
                    <label className="text-xs font-semibold text-on-surface">
                      Assigned Course Classes ({selectedClassIds.length} selected) *
                    </label>
                    {classes && classes.length > 1 && (
                      <div className="flex items-center gap-2 text-[11px]">
                        <button
                          type="button"
                          onClick={() => setSelectedClassIds(classes.map(c => c.id))}
                          className="text-primary hover:underline font-medium cursor-pointer"
                        >
                          Select All
                        </button>
                        <span className="text-outline">|</span>
                        <button
                          type="button"
                          onClick={() => setSelectedClassIds([])}
                          className="text-outline hover:underline cursor-pointer"
                        >
                          Clear
                        </button>
                      </div>
                    )}
                  </div>
                  <div className="max-h-44 overflow-y-auto p-2 bg-surface-container-low border border-outline-variant/40 rounded-lg flex flex-col gap-1.5">
                    {classes && classes.length > 0 ? (
                      classes.map((cls) => {
                        const isChecked = selectedClassIds.includes(cls.id);
                        return (
                          <label
                            key={cls.id}
                            className={`flex items-center justify-between p-2 rounded-md border text-xs cursor-pointer transition-colors ${
                              isChecked 
                                ? 'bg-primary/5 border-primary/40 text-on-surface font-medium' 
                                : 'bg-surface-container-lowest border-outline-variant/20 hover:bg-surface-container-high/40 text-on-surface-variant'
                            }`}
                          >
                            <div className="flex items-center gap-2.5">
                              <input
                                type="checkbox"
                                checked={isChecked}
                                onChange={() => handleToggleClass(cls.id)}
                                className="w-3.5 h-3.5 rounded text-primary focus:ring-primary cursor-pointer"
                              />
                              <span className="font-mono font-semibold">{cls.code}</span>
                              <span className="text-outline">—</span>
                              <span className="truncate max-w-[220px]">{cls.name}</span>
                            </div>
                            {cls.studentCount !== undefined && (
                              <span className="text-[11px] text-outline shrink-0">
                                {cls.studentCount} students
                              </span>
                            )}
                          </label>
                        );
                      })
                    ) : (
                      <p className="text-xs text-outline py-2 text-center">No authorized classes found.</p>
                    )}
                  </div>
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                  <div className="flex flex-col gap-1">
                    <label className="text-xs font-semibold text-on-surface">Start Date &amp; Time *</label>
                    <input
                      type="datetime-local"
                      required
                      value={examDate}
                      onChange={(e) => setExamDate(e.target.value)}
                      className="w-full px-3 py-2 bg-surface-container-low border border-outline-variant/40 rounded-lg text-xs text-on-surface focus:outline-none focus:border-primary transition-colors"
                    />
                  </div>

                  <div className="flex flex-col gap-1">
                    <label className="text-xs font-semibold text-on-surface">Duration (Minutes) *</label>
                    <input
                      type="number"
                      required
                      min={1}
                      max={480}
                      value={durationMinutes}
                      onChange={(e) => setDurationMinutes(Number(e.target.value))}
                      className="w-full px-3 py-2 bg-surface-container-low border border-outline-variant/40 rounded-lg text-xs text-on-surface focus:outline-none focus:border-primary transition-colors"
                    />
                  </div>
                </div>

                <div className="flex justify-end gap-3 mt-2 pt-3 border-t border-outline-variant/30">
                  <button
                    type="button"
                    onClick={() => setIsCreateModalOpen(false)}
                    disabled={createExam.isPending}
                    className="px-4 py-2 text-xs rounded-lg border border-outline-variant/40 hover:bg-surface-container transition-colors cursor-pointer"
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    disabled={createExam.isPending}
                    className="inline-flex items-center gap-2 px-5 py-2 bg-primary text-on-primary rounded-lg text-xs font-medium hover:bg-primary/90 transition-colors disabled:opacity-50 cursor-pointer shadow-sm"
                  >
                    {createExam.isPending ? (
                      <>
                        <Loader2 className="w-3.5 h-3.5 animate-spin" />
                        <span>Creating Exam...</span>
                      </>
                    ) : (
                      <>
                        <CheckCircle2 className="w-3.5 h-3.5" />
                        <span>Publish &amp; Save</span>
                      </>
                    )}
                  </button>
                </div>
              </form>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
