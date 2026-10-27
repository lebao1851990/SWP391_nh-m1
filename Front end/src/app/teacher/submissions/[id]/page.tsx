'use client';

import React, { use } from 'react';
import { useTeacherSubmissionDetail } from '@/features/exams/hooks/use-teacher-exams';
import { AppLink as Link } from '@/components/ui/app-link';
import { GradeAuditHistory } from '@/features/scoring/components';

export default function TeacherSubmissionDetail({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  const { data: submission, isLoading, isError, error } = useTeacherSubmissionDetail(id);

  if (isLoading) {
    return (
      <div className="flex flex-col h-64 items-center justify-center text-on-surface-variant gap-4">
        <span className="material-symbols-outlined animate-spin text-[48px]">progress_activity</span>
        <span className="font-label-md text-label-md">Loading submission details...</span>
      </div>
    );
  }

  if (isError || !submission) {
    return (
      <div className="flex flex-col h-64 items-center justify-center p-space-lg text-center gap-space-sm">
        <span className="material-symbols-outlined text-error text-[48px]">error</span>
        <h2 className="font-headline-md text-headline-md text-on-surface">Failed to load submission</h2>
        <p className="font-body-md text-on-surface-variant">
          {error instanceof Error ? error.message : 'Submission not found or you are not authorized.'}
        </p>
      </div>
    );
  }

  return (
    <div className="max-w-7xl mx-auto px-space-lg py-space-lg">
      <div className="flex flex-col w-full">
        <nav aria-label="Breadcrumb" className="flex items-center gap-space-xs text-on-surface-variant font-body-sm text-body-sm mb-space-md">
          <Link className="hover:text-primary transition-colors" href="/teacher/exams">Exams</Link>
          <span className="material-symbols-outlined text-[14px] text-outline">chevron_right</span>
          <span className="font-medium text-on-surface">Submission #{submission.id}</span>
        </nav>

        <section className="bg-surface-container-lowest p-space-lg rounded-xl shadow-sm mb-space-lg">
          <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-space-md">
            <div className="flex flex-col gap-space-xs">
              <div className="flex flex-wrap items-center gap-space-sm">
                <h1 className="font-headline-lg text-headline-lg text-on-surface tracking-tight">Submission #{submission.id}</h1>
                <span className={`font-label-sm text-label-sm px-2 py-0.5 rounded font-semibold uppercase tracking-wide flex items-center gap-1 ${
                  submission.status === 'GRADED' ? 'bg-tertiary-fixed text-on-tertiary-fixed' : 'bg-surface-container-high text-on-surface-variant'
                }`}>
                  {submission.status === 'GRADED' && <span className="material-symbols-outlined text-[13px]">check_circle</span>}
                  {submission.status || 'PENDING'}
                </span>
              </div>
              <div className="flex flex-wrap items-center gap-y-1 gap-x-space-md font-body-sm text-body-sm text-on-surface-variant mt-1">
                <div className="flex items-center gap-1.5 font-medium text-on-surface">
                  <span className="material-symbols-outlined text-[16px] text-primary">person</span>
                  <span>Student ID: <span className="font-label-sm text-label-sm font-mono text-outline">{submission.studentId}</span></span>
                </div>
                <span className="h-1 w-1 rounded-full bg-outline-variant"></span>
                <div className="flex items-center gap-1.5">
                  <span className="material-symbols-outlined text-[16px] text-outline">assignment</span>
                  <span>Exam ID: <span className="font-mono">{submission.examId}</span></span>
                </div>
                <span className="h-1 w-1 rounded-full bg-outline-variant"></span>
                <div className="flex items-center gap-1.5 text-outline">
                  <span className="material-symbols-outlined text-[16px]">schedule</span>
                  <span>Created: {new Date(submission.createdAt).toLocaleString()}</span>
                </div>
              </div>
            </div>
            
            <div className="flex flex-wrap items-center gap-space-xs shrink-0">
              <Link href={`/teacher/scoring/${submission.id}`} className="bg-primary hover:bg-primary/90 text-on-primary font-label-md text-label-md px-3 py-2 rounded flex items-center gap-1.5 transition-colors cursor-pointer">
                <span className="material-symbols-outlined text-[16px]">edit_document</span>
                <span>Grade Submission</span>
              </Link>
            </div>
          </div>
        </section>

        <div className="grid grid-cols-1 lg:grid-cols-12 gap-space-lg">
          <div className="lg:col-span-8 flex flex-col gap-space-lg">
            <div className="bg-surface-container-lowest rounded-xl p-space-lg shadow-sm flex flex-col gap-space-md">
              <div className="flex items-center justify-between pb-space-xs border-b border-outline-variant/30">
                <div className="flex items-center gap-space-sm">
                  <span className="p-1.5 rounded-lg bg-surface-container text-primary">
                    <span className="material-symbols-outlined text-[20px]">data_object</span>
                  </span>
                  <div>
                    <h2 className="font-headline-sm text-headline-sm text-on-surface leading-tight">Submitted Source Code</h2>
                    <span className="font-label-sm text-label-sm text-outline">Raw code submitted by the student</span>
                  </div>
                </div>
              </div>
              
              <div className="mt-space-sm bg-surface-container-lowest rounded-lg border border-outline-variant/30 overflow-hidden">
                <div className="flex items-center px-space-md py-2 bg-surface-container-low border-b border-outline-variant/30 font-label-sm text-label-sm text-on-surface-variant font-mono">
                  main.java
                </div>
                <pre className="p-space-md font-code-body text-code-body text-on-surface overflow-x-auto whitespace-pre-wrap">
                  {submission.sourceCode || 'No source code provided.'}
                </pre>
              </div>
            </div>

            <GradeAuditHistory submissionId={submission.id} />
          </div>

          <div className="lg:col-span-4 flex flex-col gap-space-lg">
            <div className="bg-surface-container-lowest rounded-xl p-space-lg shadow-sm flex flex-col gap-space-md">
              <div className="flex items-center justify-between pb-space-xs">
                <div className="flex flex-col">
                  <span className="font-label-sm text-label-sm text-outline uppercase tracking-wider font-semibold">Consolidated Assessment Score</span>
                  <span className="font-body-sm text-body-sm text-on-surface-variant">Auto-graded</span>
                </div>
              </div>
              
              <div className="flex items-center justify-between p-space-md bg-surface-container-low rounded-xl">
                <div className="flex items-baseline gap-space-xs">
                  <span className="font-display-lg text-display-lg text-primary font-bold tracking-tight">
                    {submission.totalScore != null ? submission.totalScore : 'N/A'}
                  </span>
                  <span className="font-headline-lg text-headline-lg text-outline">/ 10</span>
                </div>
              </div>
              
              {submission.overrideScore != null && (
                <div className="flex items-center justify-between p-space-sm bg-surface-container-low rounded-lg mt-2">
                  <span className="font-label-sm text-label-sm font-semibold text-outline">Manual Override Score:</span>
                  <span className="font-label-md text-label-md font-bold text-secondary">{submission.overrideScore} / 10</span>
                </div>
              )}
              
              {submission.evaluatedAt && (
                <div className="flex items-center gap-2 mt-2 font-label-sm text-label-sm text-outline">
                  <span className="material-symbols-outlined text-[16px]">schedule</span>
                  <span>Evaluated at: {new Date(submission.evaluatedAt).toLocaleString()}</span>
                </div>
              )}
            </div>

            <div className="bg-surface-container-lowest rounded-xl p-space-lg shadow-sm flex flex-col gap-space-md opacity-75">
              <div className="flex items-center justify-between pb-space-xs border-b border-outline-variant/30">
                <div className="flex items-center gap-space-sm">
                  <span className="p-1.5 rounded-lg bg-surface-container text-outline">
                    <span className="material-symbols-outlined text-[20px]">policy</span>
                  </span>
                  <div>
                    <h2 className="font-headline-sm text-headline-sm text-on-surface leading-tight">Integrity Forensics</h2>
                  </div>
                </div>
              </div>
              <div className="flex flex-col items-center justify-center py-6 text-outline text-center gap-2">
                <span className="material-symbols-outlined text-[32px]">visibility_off</span>
                <span className="font-label-md text-label-md font-semibold">Missing Backend Contract</span>
                <span className="font-body-sm text-body-sm">Plagiarism AST metrics and demographic data are currently unavailable via the API.</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
