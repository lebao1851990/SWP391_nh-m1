'use client';

import React from 'react';
import { useAdminExam } from '../hooks';
import { AppLink as Link } from '@/components/ui/app-link';
import { useTranslation } from '@/i18n';

export function AdminExamDetailView({ examId }: { examId: string }) {
  const { t } = useTranslation();
  const { data: exam, isLoading, isError } = useAdminExam(examId);

  return (
    <div className="flex flex-col w-full gap-space-lg px-space-lg py-space-lg max-w-4xl mx-auto">
      <div className="flex items-center gap-space-sm pb-space-xs border-b border-outline-variant/30">
        <Link href="/admin/exams" className="text-on-surface-variant hover:text-on-surface flex items-center">
          <span className="material-symbols-outlined text-[20px]">arrow_back</span>
        </Link>
        <h1 className="font-display-sm text-display-sm tracking-tight text-on-surface font-bold">Exam Details</h1>
      </div>

      {isLoading ? (
        <div className="p-10 flex justify-center text-on-surface-variant">
          <span className="material-symbols-outlined animate-spin text-[32px]">progress_activity</span>
        </div>
      ) : isError ? (
        <div className="p-10 flex flex-col items-center justify-center text-center bg-surface-container-lowest rounded-xl shadow-sm border border-error/20">
          <span className="material-symbols-outlined text-[48px] text-error mb-2">error</span>
          <span className="font-headline-sm font-semibold text-on-surface">{t.admin.integrationPending}</span>
          <span className="font-body-sm text-on-surface-variant">{t.admin.integrationPendingDesc}</span>
        </div>
      ) : exam ? (
        <div className="bg-surface-container-lowest rounded-xl shadow-sm border border-outline-variant/30 p-space-lg flex flex-col gap-space-md">
          <div className="flex items-center gap-space-md pb-space-md border-b border-outline-variant/30">
            <div className="w-16 h-16 rounded-lg bg-primary-container text-on-primary-container flex items-center justify-center font-display-sm">
              <span className="material-symbols-outlined text-[32px]">grading</span>
            </div>
            <div className="flex flex-col">
              <span className="font-headline-md font-bold text-on-surface">{exam.title}</span>
              <span className={`w-fit mt-1 px-2 py-0.5 rounded font-label-sm text-label-sm font-medium ${
                exam.status === 'active' ? 'bg-primary text-on-primary' :
                exam.status === 'upcoming' ? 'bg-secondary-container text-on-secondary-container' :
                'bg-surface-container text-on-surface-variant'
              }`}>
                {exam.status.toUpperCase()}
              </span>
            </div>
          </div>
          
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-space-md">
            <div className="flex flex-col gap-1">
              <span className="font-label-sm text-label-sm uppercase text-outline">Exam ID</span>
              <span className="font-code-body font-mono text-on-surface">{exam.id}</span>
            </div>
            <div className="flex flex-col gap-1">
              <span className="font-label-sm text-label-sm uppercase text-outline">Class Assignment</span>
              <span className="font-body-md text-on-surface font-mono">{exam.classId}</span>
            </div>
            <div className="flex flex-col gap-1">
              <span className="font-label-sm text-label-sm uppercase text-outline">{t.exams.scheduledDate}</span>
              <span className="font-body-md text-on-surface">{exam.date}</span>
            </div>
            <div className="flex flex-col gap-1">
              <span className="font-label-sm text-label-sm uppercase text-outline">{t.common.duration}</span>
              <span className="font-body-md text-on-surface">{exam.durationMinutes} Minutes</span>
            </div>
          </div>
          
          <div className="mt-space-lg flex justify-end gap-space-sm">
            <button className="px-4 py-2 rounded-lg bg-surface-container-high text-on-surface font-label-md font-semibold hover:bg-surface-container-highest transition-colors">
              Reschedule
            </button>
            <button className="px-4 py-2 rounded-lg bg-primary text-on-primary font-label-md font-semibold hover:bg-primary-container transition-colors">
              Edit Exam
            </button>
          </div>
        </div>
      ) : null}
    </div>
  );
}
