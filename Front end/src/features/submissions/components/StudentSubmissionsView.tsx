'use client';

import React from 'react';
import { useStudentSubmission } from '../hooks';
import { AxiosError } from 'axios';
import { AppLink as Link } from '@/components/ui/app-link';

export function StudentSubmissionsView({ submissionId }: { submissionId: string }) {
  const { data: submission, isLoading, isError, error } = useStudentSubmission(submissionId);

  if (isLoading) {
    return (
      <div className="flex h-64 items-center justify-center">
        <div className="text-on-surface-variant font-label-md">Loading submission details...</div>
      </div>
    );
  }

  if (isError) {
    const status = (error as AxiosError)?.response?.status;
    if (status === 404 || status === 403) {
      return (
        <div className="flex flex-col h-64 items-center justify-center p-space-lg text-center gap-space-sm">
          <span className="material-symbols-outlined text-outline text-[48px]">search_off</span>
          <h2 className="font-headline-md text-headline-md text-on-surface">Not Found</h2>
          <p className="font-body-md text-on-surface-variant">The submission does not exist or you do not have permission.</p>
        </div>
      );
    }
    return (
      <div className="flex flex-col h-64 items-center justify-center p-space-lg text-center gap-space-sm">
        <span className="material-symbols-outlined text-error text-[48px]">error</span>
        <h2 className="font-headline-md text-headline-md text-on-surface">Error</h2>
        <p className="font-body-md text-on-surface-variant">{(error as Error).message}</p>
      </div>
    );
  }

  if (!submission) return null;
  return (
    <div className="max-w-7xl mx-auto px-space-lg py-space-lg">
      <div className="flex flex-col w-full">
        <div className="flex items-center gap-space-xs text-on-surface-variant mb-space-sm">
          <Link className="font-body-sm text-body-sm hover:text-primary transition-colors" href="/student/dashboard">Dashboard</Link>
          <span className="material-symbols-outlined text-[14px] text-outline">chevron_right</span>
          <span className="font-body-sm text-body-sm text-on-surface font-medium">Submission #{submission.id.substring(0, 8)}</span>
        </div>
        <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-space-md mb-space-lg">
          <div className="flex flex-col gap-1">
            <div className="flex items-center gap-space-sm flex-wrap">
              <h1 className="font-display-lg text-display-lg text-on-surface tracking-tight">Submission #{submission.id.substring(0, 8)}</h1>
              <span className="font-label-sm text-label-sm px-2.5 py-0.5 rounded-full bg-tertiary-fixed text-on-tertiary-fixed font-semibold tracking-wide uppercase">{submission.status}</span>
            </div>
            <p className="font-body-sm text-body-sm text-on-surface-variant">
              Submitted on <span className="text-on-surface font-medium">{new Date(submission.createdAt).toLocaleString()}</span>
            </p>
          </div>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-space-md mb-space-md">
          <div className="bg-surface-container-lowest rounded-xl p-space-md shadow-sm border border-outline-variant/30 flex flex-col justify-between hover:shadow-md transition-shadow">
            <div className="flex items-center justify-between pb-space-xs mb-space-xs border-b border-outline-variant/20">
              <span className="font-label-md text-label-md font-semibold text-primary font-mono">Exam</span>
            </div>
            <p className="font-body-md text-on-surface">{submission.examId}</p>
          </div>
          
          <div className="bg-surface-container-lowest rounded-xl p-space-md shadow-sm border border-outline-variant/30 flex flex-col justify-between hover:shadow-md transition-shadow">
            <div className="flex items-center justify-between pb-space-xs mb-space-xs border-b border-outline-variant/20">
              <span className="font-label-md text-label-md font-semibold text-primary font-mono">Total Score</span>
            </div>
            <p className="font-body-md text-on-surface">{submission.totalScore != null ? `${submission.totalScore}/100` : 'Not yet graded'}</p>
          </div>
        </div>

        <div className="bg-surface-container-lowest rounded-xl p-space-md shadow-sm border border-outline-variant/30 flex flex-col hover:shadow-md transition-shadow">
          <div className="flex items-center justify-between pb-space-xs mb-space-xs border-b border-outline-variant/20">
            <span className="font-label-md text-label-md font-semibold text-primary font-mono">Source Code</span>
          </div>
          <pre className="font-mono text-sm bg-surface-container text-on-surface p-4 rounded overflow-x-auto whitespace-pre-wrap">
            {submission.sourceCode}
          </pre>
        </div>

      </div>
    </div>
  );
}
