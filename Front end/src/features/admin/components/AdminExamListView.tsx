'use client';

import React, { useState, useMemo } from 'react';
import { useAdminExams, useDeleteAdminExam } from '../hooks';
import { AdminExam } from '../api/admin.api';
import { AppLink as Link } from '@/components/ui/app-link';
import { useTranslation } from '@/i18n';
import { Trash2, AlertCircle, Loader2 } from 'lucide-react';
import { toast } from 'sonner';

export function AdminExamListView() {
  const { t } = useTranslation();
  const { data: exams, isLoading, isError } = useAdminExams();
  const deleteExamMutation = useDeleteAdminExam();
  const [searchQuery, setSearchQuery] = useState('');
  const [examToDelete, setExamToDelete] = useState<AdminExam | null>(null);
  const [deleteError, setDeleteError] = useState('');

  const handleConfirmDelete = async () => {
    if (!examToDelete) return;
    setDeleteError('');
    try {
      await deleteExamMutation.mutateAsync(examToDelete.id);
      toast.success(t('teacherExams.deleteSuccess'));
      setExamToDelete(null);
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } }; message?: string })?.response?.data?.message || (err as { message?: string })?.message || t('teacherExams.deleteError');
      setDeleteError(msg);
      toast.error(msg);
    }
  };

  const filteredExams = useMemo(() => {
    if (!exams) return [];
    if (!searchQuery) return exams;
    const lowerQuery = searchQuery.toLowerCase();
    return exams.filter(
      (e) =>
        e.title?.toLowerCase().includes(lowerQuery) ||
        e.classId?.toLowerCase().includes(lowerQuery)
    );
  }, [exams, searchQuery]);

  return (
    <div className="flex flex-col w-full gap-space-lg px-space-lg py-space-lg">
      <div className="flex flex-col xl:flex-row xl:items-center justify-between gap-space-md pb-space-xs">
        <div className="flex flex-col gap-1">
          <div className="flex items-center gap-space-sm">
            <h1 className="font-display-lg text-display-lg tracking-tight text-on-surface font-bold">Manage Exams</h1>
          </div>
          <p className="font-body-md text-body-md text-on-surface-variant">Schedule and monitor institutional assessments.</p>
        </div>
      </div>

      <div className="flex flex-col rounded-xl bg-surface-container-lowest shadow-sm overflow-hidden border border-outline-variant/30">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between p-space-md bg-surface-container-low gap-space-sm border-b border-outline-variant/30">
          <div className="flex flex-col">
            <div className="flex items-center gap-space-xs">
              <span className="material-symbols-outlined text-[20px] text-primary">grading</span>
              <h2 className="font-headline-sm text-headline-sm text-on-surface font-semibold">Exam Schedule</h2>
            </div>
          </div>
          <div className="flex items-center gap-space-xs">
            <div className="flex items-center bg-surface-container-lowest border border-outline-variant/50 px-2.5 py-1 rounded-lg">
              <span className="material-symbols-outlined text-[16px] text-on-surface-variant mr-1.5">search</span>
              <input 
                className="bg-transparent font-body-sm text-body-sm text-on-surface outline-none w-32 focus:w-44 transition-all" 
                placeholder="Search exams..." 
                type="text"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
              />
            </div>
          </div>
        </div>

        {isLoading ? (
          <div className="p-10 flex justify-center text-on-surface-variant">
            <span className="material-symbols-outlined animate-spin text-[32px]">progress_activity</span>
          </div>
        ) : isError ? (
          <div className="p-10 flex flex-col items-center justify-center text-center">
            <span className="material-symbols-outlined text-[48px] text-error mb-2">error</span>
            <span className="font-headline-sm font-semibold text-on-surface">Integration Pending</span>
            <span className="font-body-sm text-on-surface-variant">Backend API is currently unavailable.</span>
          </div>
        ) : filteredExams?.length === 0 ? (
          <div className="p-10 flex flex-col items-center justify-center text-center">
            <span className="material-symbols-outlined text-[48px] text-on-surface-variant mb-2">event_busy</span>
            <span className="font-headline-sm font-semibold text-on-surface">No Exams Scheduled</span>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left">
              <thead>
                <tr className="bg-surface-container-lowest font-label-sm text-label-sm uppercase tracking-wider text-on-surface-variant font-semibold border-b border-outline-variant/30">
                  <th className="py-2.5 px-space-md">Exam Title</th>
                  <th className="py-2.5 px-space-md">Class ID</th>
                  <th className="py-2.5 px-space-md">Date</th>
                  <th className="py-2.5 px-space-md">Status</th>
                  <th className="py-2.5 px-space-md text-right">Action</th>
                </tr>
              </thead>
              <tbody className="font-body-md text-body-md divide-y divide-surface-container-high/40">
                {filteredExams?.map(exam => (
                  <tr key={exam.id} className="hover:bg-surface-container-low/60 transition-colors group">
                    <td className="py-3 px-space-md font-semibold text-on-surface">{exam.title}</td>
                    <td className="py-3 px-space-md text-on-surface-variant font-mono text-label-sm">{exam.classId}</td>
                    <td className="py-3 px-space-md text-on-surface-variant">{exam.date}</td>
                    <td className="py-3 px-space-md">
                      <span className={`px-2 py-0.5 rounded font-label-sm text-label-sm font-medium ${
                        exam.status === 'active' ? 'bg-primary-container text-on-primary-container' :
                        exam.status === 'upcoming' ? 'bg-secondary-container text-on-secondary-container' :
                        'bg-surface-container text-on-surface-variant'
                      }`}>
                        {exam.status.toUpperCase()}
                      </span>
                    </td>
                    <td className="py-3 px-space-md text-right">
                      <div className="flex items-center justify-end gap-1.5">
                        <Link href={`/admin/exams/${exam.id}`} className="px-2.5 py-1 rounded bg-surface-container-low group-hover:bg-primary group-hover:text-on-primary text-on-surface-variant font-label-sm text-label-sm font-medium transition-colors">
                          View Details
                        </Link>
                        <button
                          type="button"
                          onClick={() => {
                            setExamToDelete(exam);
                            setDeleteError('');
                          }}
                          className="p-1 rounded text-rose-600 hover:text-rose-700 hover:bg-rose-50 transition-colors cursor-pointer"
                          title={t('teacherExams.deleteButton')}
                        >
                          <Trash2 className="w-3.5 h-3.5" />
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {/* Delete Exam Confirmation Modal */}
        {examToDelete && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-xs p-4 animate-in fade-in duration-150">
            <div className="bg-surface-container-lowest rounded-2xl max-w-md w-full p-6 shadow-2xl border border-rose-200/60 flex flex-col gap-4 animate-in zoom-in-95 duration-150">
              <div className="flex items-center gap-3">
                <div className="p-2.5 rounded-full bg-rose-50 text-rose-600 shrink-0">
                  <Trash2 className="w-5 h-5" />
                </div>
                <div>
                  <h3 className="font-headline-sm text-headline-sm font-semibold text-on-surface">
                    {t('teacherExams.deleteModalTitle')}
                  </h3>
                  <p className="text-xs text-on-surface-variant font-medium truncate max-w-[280px]">
                    {examToDelete.title}
                  </p>
                </div>
              </div>

              <div className="p-3 rounded-lg bg-surface-container-low border border-outline-variant/30 text-xs text-on-surface-variant space-y-2">
                <p>
                  {t('teacherExams.deleteModalWarning').replace('{title}', examToDelete.title)}
                </p>
                <p className="text-[11px] text-outline italic">
                  {t('teacherExams.deleteModalActiveGradingWarning')}
                </p>
              </div>

              {deleteError && (
                <div className="p-3 rounded-lg bg-rose-50 border border-rose-200 text-rose-700 flex items-center gap-2 text-xs">
                  <AlertCircle className="w-4 h-4 shrink-0" />
                  <span>{deleteError}</span>
                </div>
              )}

              <div className="flex justify-end gap-3 pt-2 border-t border-outline-variant/30">
                <button
                  type="button"
                  onClick={() => setExamToDelete(null)}
                  disabled={deleteExamMutation.isPending}
                  className="px-4 py-2 text-xs rounded-lg border border-outline-variant/40 hover:bg-surface-container transition-colors cursor-pointer"
                >
                  {t('common.cancel')}
                </button>
                <button
                  type="button"
                  onClick={handleConfirmDelete}
                  disabled={deleteExamMutation.isPending}
                  className="inline-flex items-center gap-2 px-5 py-2 bg-rose-600 text-white rounded-lg text-xs font-medium hover:bg-rose-700 transition-colors disabled:opacity-50 cursor-pointer shadow-sm"
                >
                  {deleteExamMutation.isPending ? (
                    <>
                      <Loader2 className="w-3.5 h-3.5 animate-spin" />
                      <span>{t('teacherExams.deleting')}</span>
                    </>
                  ) : (
                    <>
                      <Trash2 className="w-3.5 h-3.5" />
                      <span>{t('common.confirmDelete')}</span>
                    </>
                  )}
                </button>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
