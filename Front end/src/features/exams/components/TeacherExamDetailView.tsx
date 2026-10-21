'use client';

import React, { useState, useMemo } from 'react';
import { useTeacherExamDetail, useTeacherExamSubmissions, useUpdateExam, useTeacherClasses } from '../hooks/use-teacher-exams';
import { ClassMultiSelectDropdown } from './ClassMultiSelectDropdown';
import { AxiosError } from 'axios';
import { AppLink as Link } from '@/components/ui/app-link';
import { 
  ArrowLeft, 
  Search, 
  Edit3, 
  X, 
  CheckCircle2, 
  Loader2, 
  AlertCircle, 
  Clock, 
  BookOpen, 
  ExternalLink,
  Users
} from 'lucide-react';
import { toast } from 'sonner';
import { useTranslation } from '@/i18n';

interface TeacherExamDetailViewProps {
  examId?: string;
}

export function TeacherExamDetailView({ examId }: TeacherExamDetailViewProps) {
  const { t, formatDate } = useTranslation();
  const { data: exam, isLoading: isLoadingExam, isError: isErrorExam, error: examError } = useTeacherExamDetail(examId || '');
  const { data: submissions, isLoading: isLoadingSubmissions } = useTeacherExamSubmissions(examId || '');
  const { 
    data: classes, 
    isLoading: isLoadingClasses, 
    isError: isErrorClasses, 
    error: classesError,
    refetch: refetchClasses 
  } = useTeacherClasses();
  const updateExam = useUpdateExam();

  const [searchQuery, setSearchQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState('all');

  // Edit Modal State
  const [isEditModalOpen, setIsEditModalOpen] = useState(false);
  const [editTitle, setEditTitle] = useState('');
  const [editClassIds, setEditClassIds] = useState<string[]>([]);
  const [isEditClassesDropdownOpen, setIsEditClassesDropdownOpen] = useState(false);
  const [editDate, setEditDate] = useState('');
  const [editDuration, setEditDuration] = useState(90);
  const [editStatus, setEditStatus] = useState('active');
  const [editError, setEditError] = useState('');

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

  const handleOpenEditModal = () => {
    if (!exam) return;
    setEditTitle(exam.title || '');
    setEditClassIds(exam.classIds && exam.classIds.length > 0 ? exam.classIds : (exam.classId ? [exam.classId] : []));
    if (exam.examDate) {
      const d = new Date(exam.examDate);
      d.setMinutes(d.getMinutes() - d.getTimezoneOffset());
      setEditDate(d.toISOString().slice(0, 16));
    } else {
      setEditDate('');
    }
    setEditDuration(exam.duration || 90);
    setEditStatus((exam.status || 'active').toLowerCase());
    setEditError('');
    setIsEditModalOpen(true);
  };

  const handleEditSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!exam) return;
    if (!editTitle.trim()) {
      setEditError('Exam title is required.');
      return;
    }
    if (editClassIds.length === 0) {
      setEditError('Please select at least one course class for the exam.');
      return;
    }

    setEditError('');
    updateExam.mutate(
      {
        id: exam.id,
        payload: {
          title: editTitle.trim(),
          classId: editClassIds[0],
          classIds: editClassIds,
          date: editDate ? new Date(editDate).toISOString() : undefined,
          durationMinutes: Number(editDuration),
          status: editStatus,
        },
      },
      {
        onSuccess: () => {
          setIsEditModalOpen(false);
          toast.success('Exam settings updated successfully');
        },
        onError: (err: unknown) => {
          const msg = (err as { response?: { data?: { message?: string } }; message?: string })?.response?.data?.message || (err as { message?: string })?.message || 'Failed to update exam';
          setEditError(msg);
          toast.error(msg);
        },
      }
    );
  };

  if (!examId) return null;

  if (isLoadingExam || isLoadingSubmissions) {
    return (
      <div className="flex flex-col h-72 items-center justify-center gap-3">
        <div className="w-8 h-8 rounded-full border-2 border-primary border-t-transparent animate-spin" />
        <p className="text-on-surface-variant font-label-md text-xs">{t.exams.loadingRoster}</p>
      </div>
    );
  }

  if (isErrorExam) {
    const status = (examError as AxiosError)?.response?.status;
    return (
      <div className="max-w-xl mx-auto my-12 p-8 rounded-2xl bg-surface-container-lowest border border-outline-variant/40 shadow-lg text-center flex flex-col items-center gap-3">
        <div className="p-3 rounded-full bg-rose-50 dark:bg-rose-950/60 text-rose-600 dark:text-rose-400">
          <AlertCircle className="w-8 h-8" />
        </div>
        <h2 className="font-headline-md text-headline-md text-on-surface">
          {status === 404 ? t.exams.examNotFound : status === 403 ? t.exams.accessRestricted : t.common.error}
        </h2>
        <p className="font-body-md text-on-surface-variant text-xs">{(examError as Error).message}</p>
        <Link href="/teacher/exams" className="mt-2 px-4 py-2 bg-primary text-on-primary rounded-lg text-xs font-medium">
          {t.exams.backToAssessments}
        </Link>
      </div>
    );
  }

  if (!exam) return null;

  return (
    <div className="max-w-7xl mx-auto px-space-lg py-space-lg">
      <div className="flex flex-col w-full space-y-space-lg">
        {/* Top Navigation & Action Bar */}
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-space-md">
          <div className="space-y-1.5">
            <nav className="flex items-center gap-2 text-on-surface-variant text-xs">
              <Link className="hover:text-primary transition-colors flex items-center gap-1 font-medium" href="/teacher/exams">
                <ArrowLeft className="w-3.5 h-3.5" />
                <span>{t.nav.exams}</span>
              </Link>
              <span className="text-outline-variant">/</span>
              <span className="text-primary font-semibold truncate max-w-[220px]">{exam.title}</span>
            </nav>
            <div className="flex flex-wrap items-center gap-3">
              <h1 className="font-headline-lg text-headline-lg text-on-surface tracking-tight">{exam.title}</h1>
              <div className="flex items-center gap-1.5 px-2.5 py-0.5 rounded-full bg-emerald-50 dark:bg-emerald-950/60 text-emerald-700 dark:text-emerald-300 border border-emerald-200 dark:border-emerald-800 text-xs font-semibold uppercase">
                {exam.status || t.exams.statusActive}
              </div>
            </div>
            <div className="flex flex-wrap items-center gap-2 pt-1 text-xs text-on-surface-variant">
              <div className="flex flex-wrap items-center gap-1.5" data-testid="assigned-classes-list">
                <span className="text-outline font-medium">{t.exams.assignedClasses}:</span>
                {exam.assignedClasses && exam.assignedClasses.length > 0 ? (
                  exam.assignedClasses.map((cls) => (
                    <span key={cls.id} className="inline-flex items-center gap-1 px-2.5 py-1 rounded bg-surface-container font-mono font-medium text-on-surface border border-outline-variant/30">
                      <BookOpen className="w-3.5 h-3.5 text-primary" />
                      <span>{cls.code} — {cls.name}</span>
                    </span>
                  ))
                ) : (
                  <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded bg-surface-container font-mono font-medium text-on-surface border border-outline-variant/30">
                    <BookOpen className="w-3.5 h-3.5 text-primary" />
                    <span>{exam.courseCode || 'SE-GENERAL'}</span>
                  </span>
                )}
              </div>
              <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded bg-surface-container-high text-on-surface border border-outline-variant/30">
                <Clock className="w-3.5 h-3.5 text-outline" />
                <span>{t.exams.scheduledDate}: {exam.examDate ? formatDate(exam.examDate) : t.exams.openSchedule}</span>
              </span>
              {exam.enrolledCount !== undefined && (
                <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded bg-surface-container text-on-surface border border-outline-variant/30">
                  <Users className="w-3.5 h-3.5 text-secondary" />
                  <span>{exam.enrolledCount} {t.exams.studentsEnrolled}</span>
                </span>
              )}
            </div>
          </div>
          <div className="flex flex-wrap items-center gap-2 self-start md:self-center">
            <button
              onClick={handleOpenEditModal}
              className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-lg bg-surface-container hover:bg-surface-container-high text-on-surface text-xs font-medium transition-all border border-outline-variant/40 shadow-xs cursor-pointer"
              type="button"
              id="btn-edit-exam"
            >
              <Edit3 className="w-3.5 h-3.5 text-on-surface-variant" />
              <span>{t.exams.editExamSettings}</span>
            </button>
          </div>
        </div>

        {/* Filter & Search Toolbar */}
        <div className="p-3.5 rounded-xl bg-surface-container-lowest border border-outline-variant/40 shadow-xs flex flex-col lg:flex-row items-stretch lg:items-center justify-between gap-3">
          <div className="flex flex-wrap items-center gap-1.5 overflow-x-auto pb-1 lg:pb-0 text-xs">
            <button 
              className={`px-3 py-1.5 rounded-lg font-semibold transition-all cursor-pointer ${
                statusFilter === 'all' 
                  ? 'bg-primary text-on-primary shadow-xs' 
                  : 'text-on-surface-variant hover:bg-surface-container hover:text-on-surface'
              }`}
              onClick={() => setStatusFilter('all')}
            >
              {t.exams.allSubmissionsCount} ({stats.all})
            </button>
            <button 
              className={`px-3 py-1.5 rounded-lg font-semibold transition-all cursor-pointer ${
                statusFilter === 'submitted' 
                  ? 'bg-primary text-on-primary shadow-xs' 
                  : 'text-on-surface-variant hover:bg-surface-container hover:text-on-surface'
              }`}
              onClick={() => setStatusFilter('submitted')}
            >
              {t.exams.pendingReviewCount} ({stats.submitted})
            </button>
            <button 
              className={`px-3 py-1.5 rounded-lg font-semibold transition-all cursor-pointer ${
                statusFilter === 'graded' 
                  ? 'bg-primary text-on-primary shadow-xs' 
                  : 'text-on-surface-variant hover:bg-surface-container hover:text-on-surface'
              }`}
              onClick={() => setStatusFilter('graded')}
            >
              {t.exams.gradedCount} ({stats.graded})
            </button>
          </div>

          <div className="flex items-center gap-2 flex-1 lg:max-w-md justify-end">
            <div className="relative w-full">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-outline w-4 h-4" />
              <input 
                className="w-full pl-9 pr-8 py-1.5 bg-surface-container-low rounded-lg text-on-surface text-xs border border-outline-variant/40 focus:outline-none focus:border-primary transition-all" 
                placeholder={t.exams.searchSubmissionsPlaceholder} 
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
          </div>
        </div>

        {/* Detailed Submissions Table */}
        <div className="rounded-xl bg-surface-container-lowest border border-outline-variant/40 shadow-xs overflow-hidden flex flex-col">
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse text-xs" id="submissions-table">
              <thead>
                <tr className="bg-surface-container-low text-on-surface-variant font-label-xs uppercase tracking-wider border-b border-outline-variant/30">
                  <th className="py-2.5 px-4 font-semibold">{t.submissions.studentName}</th>
                  <th className="py-2.5 px-4 font-semibold">{t.submissions.studentEmail}</th>
                  <th className="py-2.5 px-4 font-semibold">{t.submissions.submittedAt}</th>
                  <th className="py-2.5 px-4 font-semibold">{t.submissions.score}</th>
                  <th className="py-2.5 px-4 font-semibold">{t.common.status}</th>
                  <th className="py-2.5 px-4 font-semibold text-right">{t.exams.actions}</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-outline-variant/20 text-on-surface">
                {filteredSubmissions.length === 0 ? (
                  <tr>
                    <td colSpan={6} className="py-12 text-center text-outline">
                      {t.exams.noSubmissionsMatch}
                    </td>
                  </tr>
                ) : (
                  filteredSubmissions.map(sub => (
                    <tr key={sub.id} className="hover:bg-surface-container-low/60 transition-colors">
                      <td className="py-3 px-4 font-semibold text-on-surface">{sub.studentName}</td>
                      <td className="py-3 px-4 text-on-surface-variant text-[11px]">{sub.studentEmail}</td>
                      <td className="py-3 px-4 text-on-surface-variant">
                        {sub.submittedAt ? formatDate(sub.submittedAt) : 'N/A'}
                      </td>
                      <td className="py-3 px-4">
                        <span className="font-mono bg-surface-container px-2 py-0.5 rounded font-bold text-on-surface">
                          {sub.totalScore ?? 0}
                        </span>
                        <span className="text-outline text-[10px] ml-0.5">/100</span>
                      </td>
                      <td className="py-3 px-4">
                        <span className={`px-2 py-0.5 rounded-full text-[10px] font-semibold ${
                          sub.status === 'GRADED' 
                            ? 'bg-emerald-50 dark:bg-emerald-950/60 text-emerald-700 dark:text-emerald-300 border border-emerald-200 dark:border-emerald-800' 
                            : 'bg-amber-50 dark:bg-amber-950/60 text-amber-700 dark:text-amber-300 border border-amber-200 dark:border-amber-800'
                        }`}>
                          {sub.status || t.scoring.notEvaluated}
                        </span>
                      </td>
                      <td className="py-3 px-4 text-right">
                        <Link 
                          href={`/teacher/submissions/${sub.id}`} 
                          className="inline-flex items-center gap-1 px-3 py-1 bg-surface-container hover:bg-surface-container-high text-primary rounded-lg font-medium transition-colors"
                        >
                          <span>{t.exams.reviewAndGrade}</span>
                          <ExternalLink className="w-3 h-3" />
                        </Link>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
          <div className="px-4 py-2.5 bg-surface-container-low/40 border-t border-outline-variant/30 flex items-center justify-between text-xs text-outline">
            <span>{t.exams.showingSubmissions.replace('{count}', String(filteredSubmissions.length)).replace('{total}', String(stats.all))}</span>
          </div>
        </div>

        {/* Edit Exam Modal */}
        {isEditModalOpen && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-xs p-4 animate-in fade-in duration-150">
            <div className="bg-surface-container-lowest rounded-2xl max-w-lg w-full p-6 shadow-2xl border border-outline-variant/40 flex flex-col gap-4 animate-in zoom-in-95 duration-150">
              <div className="flex items-center justify-between border-b border-outline-variant/30 pb-3">
                <div className="flex items-center gap-2">
                  <Edit3 className="w-5 h-5 text-primary" />
                  <h2 className="font-headline-sm text-headline-sm text-on-surface font-semibold">{t.exams.editExamSettings}</h2>
                </div>
                <button
                  type="button"
                  onClick={() => setIsEditModalOpen(false)}
                  className="text-outline hover:text-on-surface transition-colors p-1 rounded-lg cursor-pointer"
                >
                  <X className="w-4 h-4" />
                </button>
              </div>

              <form onSubmit={handleEditSubmit} className="flex flex-col gap-4">
                {editError && (
                  <div className="p-3 rounded-lg bg-rose-50 dark:bg-rose-950/60 border border-rose-200 dark:border-rose-800 text-rose-700 dark:text-rose-300 flex items-center gap-2 text-xs">
                    <AlertCircle className="w-4 h-4 shrink-0" />
                    <span>{editError}</span>
                  </div>
                )}

                <div className="flex flex-col gap-1">
                  <label className="text-xs font-semibold text-on-surface">{t.exams.examTitle} *</label>
                  <input
                    type="text"
                    required
                    value={editTitle}
                    onChange={(e) => setEditTitle(e.target.value)}
                    className="w-full px-3 py-2 bg-surface-container-low border border-outline-variant/40 rounded-lg text-xs text-on-surface focus:outline-none focus:border-primary transition-colors"
                  />
                </div>

                <div className="flex flex-col gap-1.5">
                  <label 
                    htmlFor="edit-assigned-classes-trigger"
                    className="text-xs font-semibold text-on-surface flex items-center justify-between"
                  >
                    <span>{t('exams.targetClasses')} ({t('exams.classesSelectedCount', { count: editClassIds.length })}) *</span>
                  </label>
                  <ClassMultiSelectDropdown
                    id="edit-assigned-classes-trigger"
                    classes={classes || []}
                    selectedClassIds={editClassIds}
                    onChange={(ids) => {
                      setEditClassIds(ids);
                      if (editError && ids.length > 0) setEditError('');
                    }}
                    isLoading={isLoadingClasses}
                    isError={isErrorClasses}
                    errorMessage={classesError ? (classesError as Error).message : undefined}
                    onRetry={() => refetchClasses()}
                    hasError={!!editError && editClassIds.length === 0}
                    required
                    isOpen={isEditClassesDropdownOpen}
                    onOpenChange={setIsEditClassesDropdownOpen}
                  />
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                  <div className="flex flex-col gap-1">
                    <label className="text-xs font-semibold text-on-surface">{t.exams.startTime} *</label>
                    <input
                      type="datetime-local"
                      required
                      value={editDate}
                      onChange={(e) => setEditDate(e.target.value)}
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
                      value={editDuration}
                      onChange={(e) => setEditDuration(Number(e.target.value))}
                      className="w-full px-3 py-2 bg-surface-container-low border border-outline-variant/40 rounded-lg text-xs text-on-surface focus:outline-none focus:border-primary transition-colors"
                    />
                  </div>
                </div>

                <div className="flex flex-col gap-1">
                  <label className="text-xs font-semibold text-on-surface">{t.common.status} *</label>
                  <select
                    value={editStatus}
                    onChange={(e) => setEditStatus(e.target.value)}
                    className="w-full px-3 py-2 bg-surface-container-low border border-outline-variant/40 rounded-lg text-xs text-on-surface focus:outline-none focus:border-primary transition-colors cursor-pointer"
                  >
                    <option value="active">{t.exams.activeStatusDesc}</option>
                    <option value="upcoming">{t.exams.upcomingStatusDesc}</option>
                    <option value="completed">{t.exams.completedStatusDesc}</option>
                  </select>
                </div>

                <div className="flex justify-end gap-3 mt-2 pt-3 border-t border-outline-variant/30">
                  <button
                    type="button"
                    onClick={() => setIsEditModalOpen(false)}
                    disabled={updateExam.isPending}
                    className="px-4 py-2 text-xs rounded-lg border border-outline-variant/40 hover:bg-surface-container transition-colors cursor-pointer"
                  >
                    {t.common.cancel}
                  </button>
                  <button
                    type="submit"
                    disabled={updateExam.isPending}
                    className="inline-flex items-center gap-2 px-5 py-2 bg-primary text-on-primary rounded-lg text-xs font-medium hover:bg-primary/90 transition-colors disabled:opacity-50 cursor-pointer shadow-sm"
                  >
                    {updateExam.isPending ? (
                      <>
                        <Loader2 className="w-3.5 h-3.5 animate-spin" />
                        <span>{t.common.loading}</span>
                      </>
                    ) : (
                      <>
                        <CheckCircle2 className="w-3.5 h-3.5" />
                        <span>{t.exams.saveChanges}</span>
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
