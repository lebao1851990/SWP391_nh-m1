'use client';

import React, { useState, useMemo } from 'react';
import { useTeacherExamDetail, useTeacherExamSubmissions, useUpdateExam, useTeacherClasses } from '../hooks/use-teacher-exams';
import { AxiosError } from 'axios';
import { AppLink as Link } from '@/components/ui/app-link';
import { 
  ArrowLeft, 
  Search, 
  Edit3, 
  X, 
  ChevronDown, 
  CheckCircle2, 
  Loader2, 
  AlertCircle, 
  Calendar, 
  Clock, 
  BookOpen, 
  ExternalLink,
  Code2,
  Users
} from 'lucide-react';
import { toast } from 'sonner';

interface TeacherExamDetailViewProps {
  examId?: string;
}

export function TeacherExamDetailView({ examId }: TeacherExamDetailViewProps) {
  const { data: exam, isLoading: isLoadingExam, isError: isErrorExam, error: examError } = useTeacherExamDetail(examId || '');
  const { data: submissions, isLoading: isLoadingSubmissions } = useTeacherExamSubmissions(examId || '');
  const { data: classes } = useTeacherClasses();
  const updateExam = useUpdateExam();

  const [searchQuery, setSearchQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState('all');

  // Edit Modal State
  const [isEditModalOpen, setIsEditModalOpen] = useState(false);
  const [editTitle, setEditTitle] = useState('');
  const [editClassIds, setEditClassIds] = useState<string[]>([]);
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

  const handleToggleClass = (classId: string) => {
    setEditClassIds(prev => 
      prev.includes(classId) 
        ? prev.filter(id => id !== classId)
        : [...prev, classId]
    );
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
        <p className="text-on-surface-variant font-label-md text-xs">Loading assessment roster...</p>
      </div>
    );
  }

  if (isErrorExam) {
    const status = (examError as AxiosError)?.response?.status;
    return (
      <div className="max-w-xl mx-auto my-12 p-8 rounded-2xl bg-surface-container-lowest border border-outline-variant/40 shadow-lg text-center flex flex-col items-center gap-3">
        <div className="p-3 rounded-full bg-rose-50 text-rose-600">
          <AlertCircle className="w-8 h-8" />
        </div>
        <h2 className="font-headline-md text-headline-md text-on-surface">
          {status === 404 ? 'Exam Not Found' : status === 403 ? 'Access Restricted' : 'Error'}
        </h2>
        <p className="font-body-md text-on-surface-variant text-xs">{(examError as Error).message}</p>
        <Link href="/teacher/exams" className="mt-2 px-4 py-2 bg-primary text-on-primary rounded-lg text-xs font-medium">
          Back to Exams
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
                <span>Exams</span>
              </Link>
              <span className="text-outline-variant">/</span>
              <span className="text-primary font-semibold truncate max-w-[220px]">{exam.title}</span>
            </nav>
            <div className="flex flex-wrap items-center gap-3">
              <h1 className="font-headline-lg text-headline-lg text-on-surface tracking-tight">{exam.title}</h1>
              <div className="flex items-center gap-1.5 px-2.5 py-0.5 rounded-full bg-emerald-50 text-emerald-700 border border-emerald-200 text-xs font-semibold uppercase">
                {exam.status || 'Active'}
              </div>
            </div>
            <div className="flex flex-wrap items-center gap-2 pt-1 text-xs text-on-surface-variant">
              <div className="flex flex-wrap items-center gap-1.5" data-testid="assigned-classes-list">
                <span className="text-outline font-medium">Assigned Classes:</span>
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
                <span>Date: {exam.examDate ? new Date(exam.examDate).toLocaleString() : 'Open Schedule'}</span>
              </span>
              {exam.enrolledCount !== undefined && (
                <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded bg-surface-container text-on-surface border border-outline-variant/30">
                  <Users className="w-3.5 h-3.5 text-secondary" />
                  <span>{exam.enrolledCount} Students Enrolled</span>
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
              <span>Edit Exam Settings</span>
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
              All Submissions ({stats.all})
            </button>
            <button 
              className={`px-3 py-1.5 rounded-lg font-semibold transition-all cursor-pointer ${
                statusFilter === 'submitted' 
                  ? 'bg-primary text-on-primary shadow-xs' 
                  : 'text-on-surface-variant hover:bg-surface-container hover:text-on-surface'
              }`}
              onClick={() => setStatusFilter('submitted')}
            >
              Pending Review ({stats.submitted})
            </button>
            <button 
              className={`px-3 py-1.5 rounded-lg font-semibold transition-all cursor-pointer ${
                statusFilter === 'graded' 
                  ? 'bg-primary text-on-primary shadow-xs' 
                  : 'text-on-surface-variant hover:bg-surface-container hover:text-on-surface'
              }`}
              onClick={() => setStatusFilter('graded')}
            >
              Graded ({stats.graded})
            </button>
          </div>

          <div className="flex items-center gap-2 flex-1 lg:max-w-md justify-end">
            <div className="relative w-full">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-outline w-4 h-4" />
              <input 
                className="w-full pl-9 pr-8 py-1.5 bg-surface-container-low rounded-lg text-on-surface text-xs border border-outline-variant/40 focus:outline-none focus:border-primary transition-all" 
                placeholder="Search student name or email..." 
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
                  <th className="py-2.5 px-4 font-semibold">Student Name</th>
                  <th className="py-2.5 px-4 font-semibold">Email</th>
                  <th className="py-2.5 px-4 font-semibold">Submission Time</th>
                  <th className="py-2.5 px-4 font-semibold">Score</th>
                  <th className="py-2.5 px-4 font-semibold">Status</th>
                  <th className="py-2.5 px-4 font-semibold text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-outline-variant/20 text-on-surface">
                {filteredSubmissions.length === 0 ? (
                  <tr>
                    <td colSpan={6} className="py-12 text-center text-outline">
                      No student submissions match query.
                    </td>
                  </tr>
                ) : (
                  filteredSubmissions.map(sub => (
                    <tr key={sub.id} className="hover:bg-surface-container-low/60 transition-colors">
                      <td className="py-3 px-4 font-semibold text-on-surface">{sub.studentName}</td>
                      <td className="py-3 px-4 text-on-surface-variant text-[11px]">{sub.studentEmail}</td>
                      <td className="py-3 px-4 text-on-surface-variant">
                        {sub.submittedAt ? new Date(sub.submittedAt).toLocaleString() : 'N/A'}
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
                            ? 'bg-emerald-50 text-emerald-700 border border-emerald-200' 
                            : 'bg-amber-50 text-amber-700 border border-amber-200'
                        }`}>
                          {sub.status || 'PENDING'}
                        </span>
                      </td>
                      <td className="py-3 px-4 text-right">
                        <Link 
                          href={`/teacher/submissions/${sub.id}`} 
                          className="inline-flex items-center gap-1 px-3 py-1 bg-surface-container hover:bg-surface-container-high text-primary rounded-lg font-medium transition-colors"
                        >
                          <span>Review &amp; Grade</span>
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
            <span>Showing <strong className="text-on-surface">{filteredSubmissions.length}</strong> of <strong className="text-on-surface">{stats.all}</strong> submissions</span>
          </div>
        </div>

        {/* Edit Exam Modal */}
        {isEditModalOpen && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-xs p-4 animate-in fade-in duration-150">
            <div className="bg-surface-container-lowest rounded-2xl max-w-lg w-full p-6 shadow-2xl border border-outline-variant/40 flex flex-col gap-4 animate-in zoom-in-95 duration-150">
              <div className="flex items-center justify-between border-b border-outline-variant/30 pb-3">
                <div className="flex items-center gap-2">
                  <Edit3 className="w-5 h-5 text-primary" />
                  <h2 className="font-headline-sm text-headline-sm text-on-surface font-semibold">Edit Exam Settings</h2>
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
                  <div className="p-3 rounded-lg bg-rose-50 border border-rose-200 text-rose-700 flex items-center gap-2 text-xs">
                    <AlertCircle className="w-4 h-4 shrink-0" />
                    <span>{editError}</span>
                  </div>
                )}

                <div className="flex flex-col gap-1">
                  <label className="text-xs font-semibold text-on-surface">Exam Title *</label>
                  <input
                    type="text"
                    required
                    value={editTitle}
                    onChange={(e) => setEditTitle(e.target.value)}
                    className="w-full px-3 py-2 bg-surface-container-low border border-outline-variant/40 rounded-lg text-xs text-on-surface focus:outline-none focus:border-primary transition-colors"
                  />
                </div>

                <div className="flex flex-col gap-1.5">
                  <div className="flex items-center justify-between">
                    <label className="text-xs font-semibold text-on-surface">
                      Assigned Course Classes ({editClassIds.length} selected) *
                    </label>
                    {classes && classes.length > 1 && (
                      <div className="flex items-center gap-2 text-[11px]">
                        <button
                          type="button"
                          onClick={() => setEditClassIds(classes.map(c => c.id))}
                          className="text-primary hover:underline font-medium cursor-pointer"
                        >
                          Select All
                        </button>
                        <span className="text-outline">|</span>
                        <button
                          type="button"
                          onClick={() => setEditClassIds([])}
                          className="text-outline hover:underline cursor-pointer"
                        >
                          Clear
                        </button>
                      </div>
                    )}
                  </div>
                  <div className="max-h-40 overflow-y-auto p-2 bg-surface-container-low border border-outline-variant/40 rounded-lg flex flex-col gap-1.5">
                    {classes && classes.length > 0 ? (
                      classes.map((cls) => {
                        const isChecked = editClassIds.includes(cls.id);
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
                              <span className="truncate max-w-[200px]">{cls.name}</span>
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
                      value={editDate}
                      onChange={(e) => setEditDate(e.target.value)}
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
                      value={editDuration}
                      onChange={(e) => setEditDuration(Number(e.target.value))}
                      className="w-full px-3 py-2 bg-surface-container-low border border-outline-variant/40 rounded-lg text-xs text-on-surface focus:outline-none focus:border-primary transition-colors"
                    />
                  </div>
                </div>

                <div className="flex flex-col gap-1">
                  <label className="text-xs font-semibold text-on-surface">Exam Status *</label>
                  <select
                    value={editStatus}
                    onChange={(e) => setEditStatus(e.target.value)}
                    className="w-full px-3 py-2 bg-surface-container-low border border-outline-variant/40 rounded-lg text-xs text-on-surface focus:outline-none focus:border-primary transition-colors cursor-pointer"
                  >
                    <option value="active">Active (Open for student submissions)</option>
                    <option value="upcoming">Upcoming (Scheduled)</option>
                    <option value="completed">Completed (Evaluation concluded)</option>
                  </select>
                </div>

                <div className="flex justify-end gap-3 mt-2 pt-3 border-t border-outline-variant/30">
                  <button
                    type="button"
                    onClick={() => setIsEditModalOpen(false)}
                    disabled={updateExam.isPending}
                    className="px-4 py-2 text-xs rounded-lg border border-outline-variant/40 hover:bg-surface-container transition-colors cursor-pointer"
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    disabled={updateExam.isPending}
                    className="inline-flex items-center gap-2 px-5 py-2 bg-primary text-on-primary rounded-lg text-xs font-medium hover:bg-primary/90 transition-colors disabled:opacity-50 cursor-pointer shadow-sm"
                  >
                    {updateExam.isPending ? (
                      <>
                        <Loader2 className="w-3.5 h-3.5 animate-spin" />
                        <span>Saving...</span>
                      </>
                    ) : (
                      <>
                        <CheckCircle2 className="w-3.5 h-3.5" />
                        <span>Save Changes</span>
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
