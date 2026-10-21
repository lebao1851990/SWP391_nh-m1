'use client';

import React, { useState } from 'react';
import { X, Loader2, Users, UserPlus, Trash2, AlertCircle, Search, Check, RefreshCw, FileSpreadsheet } from 'lucide-react';
import { toast } from 'sonner';
import { useAdminClassStudents, useEnrollStudent, useUnenrollStudent } from '../hooks';
import { AdminClassStudent } from '../api/admin.api';
import { ExcelImportModal } from './ExcelImportModal';
import { useTranslation } from '@/i18n';

interface ManageRosterModalProps {
  isOpen: boolean;
  onClose: () => void;
  classId: string;
  className: string;
  classCode: string;
}

export function ManageRosterModal({
  isOpen,
  onClose,
  classId,
  className,
  classCode,
}: ManageRosterModalProps) {
  const { t } = useTranslation();
  const [identifierInput, setIdentifierInput] = useState('');
  const [studentPendingDelete, setStudentPendingDelete] = useState<AdminClassStudent | null>(null);
  const [filterQuery, setFilterQuery] = useState('');
  const [isExcelImportOpen, setIsExcelImportOpen] = useState(false);

  const { data: students, isLoading, isError, refetch, isFetching } = useAdminClassStudents(classId);
  const enrollMutation = useEnrollStudent(classId);
  const unenrollMutation = useUnenrollStudent(classId);

  const filteredStudents = React.useMemo(() => {
    if (!students) return [];
    if (!filterQuery.trim()) return students;
    const q = filterQuery.toLowerCase();
    return students.filter(
      (s) =>
        s.fullName?.toLowerCase().includes(q) ||
        s.email?.toLowerCase().includes(q) ||
        s.studentId?.toLowerCase().includes(q)
    );
  }, [students, filterQuery]);

  if (!isOpen) return null;

  const handleEnroll = (e: React.FormEvent) => {
    e.preventDefault();
    const raw = identifierInput.trim();
    if (!raw) {
      toast.error('Please enter a student email or student ID.');
      return;
    }

    const isEmail = raw.includes('@');
    const payload = isEmail ? { email: raw } : { studentId: raw };

    enrollMutation.mutate(payload, {
      onSuccess: (data) => {
        toast.success(`Enrolled ${data.fullName || raw} into ${classCode}!`);
        setIdentifierInput('');
      },
      onError: (err: unknown) => {
        const res = (err as { response?: { status?: number; data?: { message?: string } } })?.response;
        const status = res?.status;
        let msg = res?.data?.message || (err as { message?: string })?.message || 'Failed to enroll student.';

        if (status === 409) {
          msg = 'Student is already enrolled in this class.';
        } else if (status === 400) {
          msg = res?.data?.message || 'User is not a student or input is invalid.';
        } else if (status === 404) {
          msg = 'Student account not found with the provided ID or email.';
        } else if (status === 403) {
          msg = 'Permission denied. Admin role required.';
        }

        toast.error(msg);
      },
    });
  };

  const handleConfirmDelete = () => {
    if (!studentPendingDelete) return;

    const target = studentPendingDelete;
    unenrollMutation.mutate(target.studentId, {
      onSuccess: () => {
        toast.success(`Removed ${target.fullName || target.email} from class roster.`);
        setStudentPendingDelete(null);
      },
      onError: (err: unknown) => {
        const res = (err as { response?: { status?: number; data?: { message?: string } } })?.response;
        const status = res?.status;
        let msg = res?.data?.message || (err as { message?: string })?.message || 'Failed to remove student.';

        if (status === 404) {
          msg = 'Student enrollment record was not found in this class.';
        } else if (status === 403) {
          msg = 'Permission denied. Admin role required.';
        }

        toast.error(msg);
      },
    });
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-xs p-4 animate-in fade-in duration-150">
      <div 
        className="bg-surface-container-lowest w-full max-w-3xl rounded-2xl shadow-2xl border border-outline-variant/30 flex flex-col overflow-hidden max-h-[90vh] animate-in zoom-in-95 duration-200"
        role="dialog"
        aria-modal="true"
        aria-label="Manage Class Roster"
      >
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-outline-variant/30 bg-surface-container-low shrink-0">
          <div className="flex items-center gap-2.5">
            <div className="p-2 rounded-lg bg-primary/10 text-primary">
              <Users className="w-5 h-5" />
            </div>
            <div>
              <h2 id="manage-roster-modal-title" className="font-headline-sm text-headline-sm font-semibold text-on-surface">
                {t.admin.manageRosterButton}
              </h2>
              <p className="text-xs text-on-surface-variant font-mono">
                {className} <span className="text-primary font-bold">({classCode})</span> • {students?.length ?? 0} enrolled
              </p>
            </div>
          </div>
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={() => setIsExcelImportOpen(true)}
              title="Import students from Excel spreadsheet"
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold text-emerald-700 dark:text-emerald-400 bg-emerald-500/10 hover:bg-emerald-500/20 border border-emerald-500/20 transition-colors cursor-pointer"
            >
              <FileSpreadsheet className="w-3.5 h-3.5" />
              <span>{t.admin.importRosterButton}</span>
            </button>
            <button
              onClick={() => refetch()}
              disabled={isFetching}
              title="Refresh roster"
              className="p-1.5 rounded-lg text-on-surface-variant hover:text-on-surface hover:bg-surface-container transition-colors disabled:opacity-50 cursor-pointer"
            >
              <RefreshCw className={`w-4 h-4 ${isFetching ? 'animate-spin' : ''}`} />
            </button>
            <button
              onClick={onClose}
              className="p-1.5 rounded-lg text-on-surface-variant hover:text-on-surface hover:bg-surface-container transition-colors cursor-pointer"
              aria-label="Close dialog"
            >
              <X className="w-5 h-5" />
            </button>
          </div>
        </div>

        {/* Enrollment Form Section */}
        <div className="p-5 border-b border-outline-variant/30 bg-surface-container-low/40 shrink-0">
          <form onSubmit={handleEnroll} className="flex flex-col sm:flex-row gap-2.5 items-end">
            <div className="flex-1 w-full flex flex-col gap-1">
              <label htmlFor="enroll-input" className="text-xs font-semibold text-on-surface-variant">
                Enroll Student <span className="font-normal text-outline">(by Student ID or Email)</span>
              </label>
              <input
                id="enroll-input"
                type="text"
                disabled={enrollMutation.isPending}
                value={identifierInput}
                onChange={(e) => setIdentifierInput(e.target.value)}
                placeholder="e.g. student@school.edu or student UUID"
                className="w-full px-3.5 py-2 rounded-lg bg-surface-container-lowest border border-outline-variant/60 focus:border-primary focus:ring-1 focus:ring-primary text-sm text-on-surface outline-none transition-all disabled:opacity-50"
              />
            </div>
            <div className="flex items-center gap-2 w-full sm:w-auto">
              <button
                type="submit"
                disabled={enrollMutation.isPending || !identifierInput.trim()}
                className="flex-1 sm:flex-initial flex items-center justify-center gap-2 px-4 py-2 rounded-lg bg-primary text-on-primary text-sm font-semibold hover:bg-primary-container transition-colors shadow-sm disabled:opacity-50 disabled:cursor-not-allowed cursor-pointer shrink-0"
              >
                {enrollMutation.isPending ? (
                  <>
                    <Loader2 className="w-4 h-4 animate-spin" />
                    <span>Enrolling...</span>
                  </>
                ) : (
                  <>
                    <UserPlus className="w-4 h-4" />
                    <span>Enroll</span>
                  </>
                )}
              </button>
              <button
                type="button"
                onClick={() => setIsExcelImportOpen(true)}
                title="Bulk enroll students by uploading an Excel spreadsheet"
                className="flex-1 sm:flex-initial flex items-center justify-center gap-1.5 px-3.5 py-2 rounded-lg bg-surface-container border border-outline-variant/60 text-on-surface text-sm font-semibold hover:bg-surface-container-high transition-colors shadow-xs cursor-pointer shrink-0"
              >
                <FileSpreadsheet className="w-4 h-4 text-emerald-600 dark:text-emerald-400" />
                <span>Batch Spreadsheet</span>
              </button>
            </div>
          </form>
        </div>

        {/* Search & Filter Toolbar */}
        <div className="px-6 py-2.5 bg-surface-container-lowest border-b border-outline-variant/20 flex items-center justify-between gap-4 shrink-0">
          <div className="flex items-center gap-2 text-xs text-on-surface-variant">
            <span>Enrolled Roster:</span>
            <span className="px-2 py-0.5 rounded-full bg-surface-container font-mono font-semibold text-on-surface">
              {filteredStudents.length} of {students?.length ?? 0}
            </span>
          </div>

          <div className="flex items-center bg-surface-container-low border border-outline-variant/40 px-2.5 py-1 rounded-lg">
            <Search className="w-3.5 h-3.5 text-on-surface-variant mr-1.5" />
            <input
              type="text"
              value={filterQuery}
              onChange={(e) => setFilterQuery(e.target.value)}
              placeholder={t.common.search}
              className="bg-transparent text-xs text-on-surface outline-none w-32 focus:w-48 transition-all placeholder:text-outline"
            />
            {filterQuery && (
              <button onClick={() => setFilterQuery('')} className="text-on-surface-variant hover:text-on-surface">
                <X className="w-3 h-3" />
              </button>
            )}
          </div>
        </div>

        {/* Student List Body */}
        <div className="flex-1 overflow-y-auto p-6">
          {isLoading ? (
            <div className="py-16 flex flex-col items-center justify-center text-on-surface-variant gap-3">
              <Loader2 className="w-7 h-7 animate-spin text-primary" />
              <span className="text-sm">{t.common.loading}</span>
            </div>
          ) : isError ? (
            <div className="py-12 flex flex-col items-center justify-center text-center p-6 bg-error/5 rounded-xl border border-error/20">
              <AlertCircle className="w-10 h-10 text-error mb-2" />
              <h3 className="text-sm font-semibold text-on-surface">{t.errors.somethingWentWrong}</h3>
              <p className="text-xs text-on-surface-variant mt-1">{t.errors.networkError}</p>
              <button
                onClick={() => refetch()}
                className="mt-4 px-3.5 py-1.5 rounded-lg bg-surface-container-high text-xs font-semibold hover:bg-surface-container-highest transition-colors cursor-pointer"
              >
                {t.common.retry}
              </button>
            </div>
          ) : filteredStudents.length === 0 ? (
            <div className="py-14 flex flex-col items-center justify-center text-center p-6 bg-surface-container-low/40 rounded-xl border border-outline-variant/20">
              <Users className="w-10 h-10 text-outline mb-2 stroke-1" />
              <h3 className="text-sm font-semibold text-on-surface">
                {filterQuery ? 'No matching students found' : 'No students enrolled yet'}
              </h3>
              <p className="text-xs text-on-surface-variant mt-1 max-w-sm">
                {filterQuery
                  ? 'Try modifying your search query above.'
                  : 'Use the enrollment bar above to enroll students into this class by their email address or student ID.'}
              </p>
            </div>
          ) : (
            <div className="overflow-x-auto rounded-xl border border-outline-variant/30">
              <table className="w-full text-left text-sm">
                <thead>
                  <tr className="bg-surface-container-low text-xs font-semibold uppercase tracking-wider text-on-surface-variant border-b border-outline-variant/30">
                    <th className="py-2.5 px-4">Student Name</th>
                    <th className="py-2.5 px-4">Email</th>
                    <th className="py-2.5 px-4">Student ID</th>
                    <th className="py-2.5 px-4 text-right">{t.common.actions}</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-outline-variant/20">
                  {filteredStudents.map((student) => (
                    <tr key={student.studentId} className="hover:bg-surface-container-low/50 transition-colors">
                      <td className="py-3 px-4 font-medium text-on-surface">
                        {student.fullName || 'Unnamed Student'}
                      </td>
                      <td className="py-3 px-4 text-on-surface-variant text-xs">
                        {student.email}
                      </td>
                      <td className="py-3 px-4 font-mono text-xs text-outline">
                        {student.studentId}
                      </td>
                      <td className="py-3 px-4 text-right">
                        <button
                          onClick={() => setStudentPendingDelete(student)}
                          disabled={unenrollMutation.isPending}
                          className="inline-flex items-center gap-1 px-2.5 py-1 rounded text-xs font-semibold text-error hover:bg-error/10 transition-colors disabled:opacity-50 cursor-pointer"
                          title="Remove student from class"
                        >
                          <Trash2 className="w-3.5 h-3.5" />
                          <span>Remove</span>
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="px-6 py-3 border-t border-outline-variant/30 bg-surface-container-low flex justify-end shrink-0">
          <button
            onClick={onClose}
            aria-label="Done"
            className="px-4 py-2 rounded-lg bg-surface-container-high text-on-surface text-xs font-semibold hover:bg-surface-container-highest transition-colors cursor-pointer"
          >
            Done
          </button>
        </div>
      </div>

      {/* Delete Confirmation Modal */}
      {studentPendingDelete && (
        <div className="fixed inset-0 z-60 flex items-center justify-center bg-black/70 backdrop-blur-xs p-4 animate-in fade-in duration-100">
          <div className="bg-surface-container-lowest w-full max-w-md rounded-2xl shadow-2xl border border-error/30 p-6 flex flex-col gap-4 animate-in zoom-in-95 duration-150">
            <div className="flex items-center gap-3">
              <div className="p-2.5 rounded-full bg-error/10 text-error">
                <Trash2 className="w-5 h-5" />
              </div>
              <div>
                <h3 className="text-base font-semibold text-on-surface">Confirm Removal</h3>
                <p className="text-xs text-on-surface-variant">Remove student from course roster</p>
              </div>
            </div>

            <p className="text-xs text-on-surface-variant leading-relaxed">
              Are you sure you want to remove <strong className="text-on-surface">{studentPendingDelete.fullName}</strong> ({studentPendingDelete.email}) from <strong className="text-on-surface">{classCode}</strong>?
            </p>
            <div className="p-3 bg-surface-container-low rounded-lg text-xs text-outline border border-outline-variant/30">
              Note: This action only revokes their class membership and exam access. The student account is preserved.
            </div>

            <div className="flex items-center justify-end gap-3 pt-2">
              <button
                onClick={() => setStudentPendingDelete(null)}
                disabled={unenrollMutation.isPending}
                className="px-4 py-2 rounded-lg text-xs font-semibold text-on-surface-variant hover:bg-surface-container transition-colors disabled:opacity-50 cursor-pointer"
              >
                {t.common.cancel}
              </button>
              <button
                onClick={handleConfirmDelete}
                disabled={unenrollMutation.isPending}
                aria-label="Confirm Remove"
                className="flex items-center gap-2 px-4 py-2 rounded-lg bg-error text-white text-xs font-semibold hover:bg-error/90 transition-colors shadow-sm disabled:opacity-50 cursor-pointer"
              >
                {unenrollMutation.isPending ? (
                  <>
                    <Loader2 className="w-3.5 h-3.5 animate-spin" />
                    <span>Removing...</span>
                  </>
                ) : (
                  <>
                    <Check className="w-3.5 h-3.5" />
                    <span>Confirm Remove</span>
                  </>
                )}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Excel Bulk Import Modal */}
      <ExcelImportModal
        isOpen={isExcelImportOpen}
        onClose={() => setIsExcelImportOpen(false)}
        classId={classId}
        className={className}
        classCode={classCode}
        onImportSuccess={() => refetch()}
      />
    </div>
  );
}
