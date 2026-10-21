'use client';

import React from 'react';
import { useAdminExams } from '../hooks';
import { AppLink as Link } from '@/components/ui/app-link';
import { useTranslation } from '@/i18n';

export function AdminExamListView() {
  const { t } = useTranslation();
  const { data: exams, isLoading, isError } = useAdminExams();
  const [searchQuery, setSearchQuery] = React.useState('');

  const filteredExams = React.useMemo(() => {
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
            <h1 className="font-display-lg text-display-lg tracking-tight text-on-surface font-bold">{t.admin.examManagementTitle}</h1>
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
                className="bg-transparent font-body-sm text-body-sm text-on-surface outline-none w-32 focus:w-44 transition-all placeholder:text-outline" 
                placeholder={t.common.search} 
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
            <span className="font-headline-sm font-semibold text-on-surface">{t.admin.integrationPending}</span>
            <span className="font-body-sm text-on-surface-variant">{t.admin.integrationPendingDesc}</span>
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
                  <th className="py-2.5 px-space-md">{t.dashboard.examColumn}</th>
                  <th className="py-2.5 px-space-md">{t.admin.classCode}</th>
                  <th className="py-2.5 px-space-md">{t.exams.scheduledDate}</th>
                  <th className="py-2.5 px-space-md">{t.common.status}</th>
                  <th className="py-2.5 px-space-md text-right">{t.common.actions}</th>
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
                      <Link href={`/admin/exams/${exam.id}`} className="px-2.5 py-1 rounded bg-surface-container-low group-hover:bg-primary group-hover:text-on-primary text-on-surface-variant font-label-sm text-label-sm font-medium transition-colors">
                        {t.common.view}
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
