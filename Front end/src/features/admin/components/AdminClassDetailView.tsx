'use client';

import React, { useState } from 'react';
import { useAdminClass } from '../hooks';
import { AppLink as Link } from '@/components/ui/app-link';
import { EditClassModal } from './EditClassModal';
import { ManageRosterModal } from './ManageRosterModal';
import { useTranslation } from '@/i18n';

export function AdminClassDetailView({ classId }: { classId: string }) {
  const { t } = useTranslation();
  const { data: cls, isLoading, isError, refetch } = useAdminClass(classId);
  const [isEditModalOpen, setIsEditModalOpen] = useState(false);
  const [isRosterModalOpen, setIsRosterModalOpen] = useState(false);

  return (
    <div className="flex flex-col w-full gap-space-lg px-space-lg py-space-lg max-w-4xl mx-auto">
      <div className="flex items-center gap-space-sm pb-space-xs border-b border-outline-variant/30">
        <Link href="/admin/classes" className="text-on-surface-variant hover:text-on-surface flex items-center">
          <span className="material-symbols-outlined text-[20px]">arrow_back</span>
        </Link>
        <h1 className="font-display-sm text-display-sm tracking-tight text-on-surface font-bold">Class Details</h1>
      </div>

      {isLoading ? (
        <div className="p-10 flex justify-center text-on-surface-variant">
          <span className="material-symbols-outlined animate-spin text-[32px]">progress_activity</span>
        </div>
      ) : isError ? (
        <div className="p-10 flex flex-col items-center justify-center text-center bg-surface-container-lowest rounded-xl shadow-sm border border-error/20">
          <span className="material-symbols-outlined text-[48px] text-error mb-2">error</span>
          <span className="font-headline-sm font-semibold text-on-surface">Failed to load class</span>
          <span className="font-body-sm text-on-surface-variant mb-4">Class record not found or network error.</span>
          <button
            onClick={() => refetch()}
            className="px-4 py-2 rounded-lg bg-surface-container-high text-xs font-semibold hover:bg-surface-container-highest transition-colors cursor-pointer"
          >
            {t.common.retry}
          </button>
        </div>
      ) : cls ? (
        <div className="bg-surface-container-lowest rounded-xl shadow-sm border border-outline-variant/30 p-space-lg flex flex-col gap-space-md">
          <div className="flex items-center gap-space-md pb-space-md border-b border-outline-variant/30">
            <div className="w-16 h-16 rounded-lg bg-surface-container flex items-center justify-center text-on-surface font-display-sm">
              <span className="material-symbols-outlined text-[32px]">class</span>
            </div>
            <div className="flex flex-col">
              <span className="font-headline-md font-bold text-on-surface">{cls.name}</span>
              <span className="font-body-md text-on-surface-variant font-mono">{cls.code}</span>
            </div>
          </div>
          
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-space-md">
            <div className="flex flex-col gap-1">
              <span className="font-label-sm text-label-sm uppercase text-outline">Class ID</span>
              <span className="font-code-body font-mono text-on-surface">{cls.id}</span>
            </div>
            <div className="flex flex-col gap-1">
              <span className="font-label-sm text-label-sm uppercase text-outline">{t.admin.semester}</span>
              <span className="font-body-md text-on-surface">{cls.semester}</span>
            </div>
            <div className="flex flex-col gap-1">
              <span className="font-label-sm text-label-sm uppercase text-outline">Teacher Assignment</span>
              <span className="font-body-md text-on-surface font-mono">{cls.teacherId}</span>
            </div>
            <div className="flex flex-col gap-1">
              <span className="font-label-sm text-label-sm uppercase text-outline">{t.admin.studentCount}</span>
              <span className="font-body-md text-on-surface font-semibold text-primary">
                {cls.studentCount ?? 0} Students
              </span>
            </div>
          </div>
          
          <div className="mt-space-lg flex justify-end gap-space-sm">
            <button
              onClick={() => setIsRosterModalOpen(true)}
              className="px-4 py-2 rounded-lg bg-surface-container-high text-on-surface font-label-md font-semibold hover:bg-surface-container-highest transition-colors cursor-pointer"
            >
              {t.admin.manageRosterButton}
            </button>
            <button
              onClick={() => setIsEditModalOpen(true)}
              className="px-4 py-2 rounded-lg bg-primary text-on-primary font-label-md font-semibold hover:bg-primary-container transition-colors cursor-pointer"
            >
              {t.admin.editClassButton}
            </button>
          </div>

          {isEditModalOpen && (
            <EditClassModal
              key={`${cls.id}-${cls.name}-${cls.code}`}
              isOpen={isEditModalOpen}
              onClose={() => setIsEditModalOpen(false)}
              cls={cls}
            />
          )}

          <ManageRosterModal
            isOpen={isRosterModalOpen}
            onClose={() => setIsRosterModalOpen(false)}
            classId={cls.id}
            className={cls.name}
            classCode={cls.code}
          />
        </div>
      ) : null}
    </div>
  );
}
