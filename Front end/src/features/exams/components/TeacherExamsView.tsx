'use client';

import React, { useState, useMemo } from 'react';
import { useTeacherExams, useTeacherClasses, useCreateExam } from '../hooks/use-teacher-exams';
import { ClassMultiSelectDropdown } from './ClassMultiSelectDropdown';
import { AppLink as Link } from '@/components/ui/app-link';
import { 
  Search, 
  PlusCircle, 
  CheckCircle2, 
  X, 
  ChevronDown, 
  FileCode2, 
  ExternalLink,
  AlertCircle,
  Loader2
} from 'lucide-react';
import { toast } from 'sonner';
import { useTranslation } from '@/i18n';

export function TeacherExamsView() {
  const { t, formatDate } = useTranslation();
  const { data: exams, isLoading, isError, error } = useTeacherExams();
  const { 
    data: classes, 
    isLoading: isLoadingClasses, 
    isError: isErrorClasses, 
    error: classesError,
    refetch: refetchClasses 
  } = useTeacherClasses();
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
  const [isClassesDropdownOpen, setIsClassesDropdownOpen] = useState(false);
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
    setIsClassesDropdownOpen(false);
    const now = new Date();
    now.setMinutes(now.getMinutes() - now.getTimezoneOffset());
    setExamDate(now.toISOString().slice(0, 16));
    setDurationMinutes(90);
    setFormError('');
    setIsCreateModalOpen(true);
  };

  const handleCreateSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!examTitle.trim()) {
      setFormError(t.exams.emptyCodeError || 'Exam title is required.');
      return;
    }
    if (selectedClassIds.length === 0) {
      setFormError(t.exams.selectClassRequiredError || 'Please select at least one course class for the exam.');
      setIsClassesDropdownOpen(true);
      return;
    }
    if (!examDate) {
      setFormError(t.exams.examDateLabel || 'Exam date and start time are required.');
      return;
    }
    if (durationMinutes <= 0) {
      setFormError(t.exams.durationMinutesLabel || 'Duration must be greater than 0 minutes.');
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
        <p className="text-on-surface-variant font-label-md text-xs">{t.exams.loadingCourseExams}</p>
      </div>
    );
  }

  if (isError) {
    return (
      <div className="max-w-xl mx-auto my-12 p-6 rounded-2xl bg-surface-container-lowest border border-rose-200/60 dark:border-rose-900/60 shadow-lg flex flex-col items-center text-center gap-3">
        <div className="p-3 rounded-full bg-rose-50 dark:bg-rose-950/60 text-rose-600 dark:text-rose-400">
          <AlertCircle className="w-8 h-8" />
        </div>
        <h2 className="font-headline-md text-headline-md text-on-surface">{t.exams.errorLoadingExams}</h2>
        <p className="font-body-md text-on-surface-variant text-xs">{(error as Error).message}</p>
        <button 
          onClick={() => window.location.reload()}
          className="mt-2 px-4 py-2 bg-primary text-on-primary rounded-lg text-xs font-medium hover:bg-primary/90 transition-all cursor-pointer"
        >
          {t.dashboard.retryConnection}
        </button>
      </div>
    );
  }

  return (
    <div className="w-full max-w-7xl mx-auto px-4 sm:px-space-lg py-space-lg min-w-0">
      <div className="flex flex-col w-full min-w-0">
        {/* Success Alert */}
        {successMessage && (
          <div className="mb-4 p-3.5 rounded-xl bg-emerald-50 dark:bg-emerald-950/60 border border-emerald-200 dark:border-emerald-800 text-emerald-800 dark:text-emerald-300 flex items-center justify-between text-xs animate-in fade-in">
            <div className="flex items-center gap-2">
              <CheckCircle2 className="w-4 h-4 text-emerald-600 shrink-0" />
              <span className="font-semibold">{successMessage}</span>
            </div>
            <button onClick={() => setSuccessMessage('')} className="text-emerald-700 dark:text-emerald-300 hover:text-emerald-900 cursor-pointer">
              <X className="w-3.5 h-3.5" />
            </button>
          </div>
        )}

        {/* Page Header */}
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-space-md mb-space-lg">
          <div className="flex flex-col">
            <div className="flex items-center gap-space-sm">
              <h1 className="font-headline-lg text-headline-lg text-on-surface tracking-tight">{t.exams.examManagement}</h1>
              <span className="font-label-xs text-xs px-2 py-0.5 rounded bg-surface-container border border-outline-variant/30 text-on-surface-variant font-medium">{t.header.termBadge}</span>
            </div>
            <p className="font-body-md text-body-md text-on-surface-variant mt-0.5 text-xs sm:text-sm">
              {t.exams.examManagementDesc}
            </p>
          </div>
          <button
            onClick={handleOpenCreateModal}
            className="inline-flex items-center gap-2 px-4 py-2 bg-primary text-on-primary rounded-lg font-label-md text-xs hover:bg-primary/90 transition-colors shadow-sm self-start md:self-auto cursor-pointer font-medium"
            id="btn-create-exam"
          >
            <PlusCircle className="w-4 h-4" />
            <span>{t.exams.createExamButton}</span>
          </button>
        </div>

        {/* Filter & Search Bar */}
        <div className="bg-surface-container-lowest rounded-xl p-3 mb-space-md border border-outline-variant/40 shadow-xs flex flex-col xl:flex-row items-stretch xl:items-center justify-between gap-3">
          <div className="flex flex-wrap items-center gap-2 flex-1 min-w-0">
            <div className="relative min-w-[240px] flex-1 max-w-sm">
              <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 text-outline w-4 h-4" />
              <input 
                className="w-full pl-8 pr-3 py-1.5 bg-surface-container-low rounded-lg text-on-surface font-body-sm text-xs border border-outline-variant/40 focus:outline-none focus:border-primary transition-all" 
                placeholder={t.exams.searchPlaceholder} 
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
                <option value="all">{t.exams.allClasses}</option>
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
                  <th className="py-2.5 px-4 font-semibold">{t.exams.examTitle}</th>
                  <th className="py-2.5 px-4 font-semibold">{t.exams.cohort}</th>
                  <th className="py-2.5 px-4 font-semibold">{t.exams.scheduledDate}</th>
                  <th className="py-2.5 px-4 font-semibold">{t.exams.durationLabel}</th>
                  <th className="py-2.5 px-4 font-semibold">{t.common.status}</th>
                  <th className="py-2.5 px-4 text-right font-semibold">{t.exams.actions}</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-outline-variant/20 text-on-surface">
                {filteredExams.length === 0 ? (
                  <tr>
                    <td colSpan={6} className="py-12 text-center text-outline font-label-md">
                      <div className="flex flex-col items-center justify-center gap-2">
                        <FileCode2 className="w-8 h-8 text-outline" />
                        <p className="font-headline-sm text-on-surface text-sm">{t.exams.noExamsFound}</p>
                        <p className="text-outline text-xs">
                          {searchQuery || classFilter !== 'all'
                            ? t.exams.noExamsMatch
                            : t.exams.noExamsCreated}
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
                        {exam.examDate ? formatDate(exam.examDate) : t.exams.openSchedule}
                      </td>
                      <td className="py-3 px-4 text-on-surface-variant">
                        {exam.duration || 90} {t.exams.durationMinutesLabel.toLowerCase()}
                      </td>
                      <td className="py-3 px-4">
                        <span className="inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-semibold bg-emerald-50 dark:bg-emerald-950/60 text-emerald-700 dark:text-emerald-300 border border-emerald-200 dark:border-emerald-800">
                          {t.exams.statusActive}
                        </span>
                      </td>
                      <td className="py-3 px-4 text-right">
                        <Link 
                          href={`/teacher/exams/${exam.id}`} 
                          className="inline-flex items-center gap-1 px-3 py-1 bg-surface-container hover:bg-surface-container-high text-primary rounded-lg font-medium transition-colors"
                        >
                          <span>{t.exams.manage}</span>
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
              {t.exams.showingExams.replace('{count}', String(filteredExams.length)).replace('{total}', String(stats.total))}
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
                  <h2 className="font-headline-sm text-headline-sm text-on-surface font-semibold">{t.exams.createNewExamModal}</h2>
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
                  <div className="p-3 rounded-lg bg-rose-50 dark:bg-rose-950/60 border border-rose-200 dark:border-rose-850 text-rose-700 dark:text-rose-300 flex items-center gap-2 text-xs">
                    <AlertCircle className="w-4 h-4 shrink-0" />
                    <span>{formError}</span>
                  </div>
                )}

                <div className="flex flex-col gap-1">
                  <label className="text-xs font-semibold text-on-surface">{t.exams.examTitle} *</label>
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
                  <label 
                    htmlFor="assigned-classes-trigger"
                    className="text-xs font-semibold text-on-surface flex items-center justify-between"
                  >
                    <span>{t('exams.targetClasses')} ({t('exams.classesSelectedCount', { count: selectedClassIds.length })}) *</span>
                  </label>
                  <ClassMultiSelectDropdown
                    id="assigned-classes-trigger"
                    classes={classes || []}
                    selectedClassIds={selectedClassIds}
                    onChange={(ids) => {
                      setSelectedClassIds(ids);
                      if (formError && ids.length > 0) setFormError('');
                    }}
                    isLoading={isLoadingClasses}
                    isError={isErrorClasses}
                    errorMessage={classesError ? (classesError as Error).message : undefined}
                    onRetry={() => refetchClasses()}
                    hasError={!!formError && selectedClassIds.length === 0}
                    required
                    isOpen={isClassesDropdownOpen}
                    onOpenChange={setIsClassesDropdownOpen}
                  />
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                  <div className="flex flex-col gap-1">
                    <label className="text-xs font-semibold text-on-surface">{t.exams.startTime} *</label>
                    <input
                      type="datetime-local"
                      required
                      value={examDate}
                      onChange={(e) => setExamDate(e.target.value)}
                      className="w-full px-3 py-2 bg-surface-container-low border border-outline-variant/40 rounded-lg text-xs text-on-surface focus:outline-none focus:border-primary transition-colors"
                    />
                  </div>

                  <div className="flex flex-col gap-1">
                    <label className="text-xs font-semibold text-on-surface">{t.exams.durationMinutesLabel} *</label>
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
                    {t.common.cancel}
                  </button>
                  <button
                    type="submit"
                    disabled={createExam.isPending}
                    className="inline-flex items-center gap-2 px-5 py-2 bg-primary text-on-primary rounded-lg text-xs font-medium hover:bg-primary/90 transition-colors disabled:opacity-50 cursor-pointer shadow-sm"
                  >
                    {createExam.isPending ? (
                      <>
                        <Loader2 className="w-3.5 h-3.5 animate-spin" />
                        <span>{t.exams.creatingExam}</span>
                      </>
                    ) : (
                      <>
                        <CheckCircle2 className="w-3.5 h-3.5" />
                        <span>{t.exams.publishAndSave}</span>
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
