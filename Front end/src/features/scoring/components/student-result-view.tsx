'use client';

import React from 'react';
import { useStudentResult } from '../hooks';
import { AxiosError } from 'axios';

interface StudentResultViewProps {
  submissionId: string;
}

import Link from 'next/link';

export function StudentResultView({ submissionId }: StudentResultViewProps) {
  const { data, isLoading, isError, error } = useStudentResult(submissionId);

  if (isLoading) {
    return (
      <div className="flex h-64 items-center justify-center">
        <div className="text-on-surface-variant font-label-md">Loading your authorized result...</div>
      </div>
    );
  }

  if (isError) {
    const status = (error as AxiosError)?.response?.status;
    
    if (status === 403) {
      return (
        <div className="flex flex-col h-64 items-center justify-center p-space-lg text-center gap-space-sm">
          <span className="material-symbols-outlined text-error text-[48px]">lock</span>
          <h2 className="font-headline-md text-headline-md text-on-surface">Result Not Available</h2>
          <p className="font-body-md text-on-surface-variant">Your result has not been released yet, or you do not have permission to view it.</p>
        </div>
      );
    }

    if (status === 404) {
      return (
        <div className="flex flex-col h-64 items-center justify-center p-space-lg text-center gap-space-sm">
          <span className="material-symbols-outlined text-outline text-[48px]">search_off</span>
          <h2 className="font-headline-md text-headline-md text-on-surface">Submission Not Found</h2>
          <p className="font-body-md text-on-surface-variant">We could not locate this submission in your records.</p>
        </div>
      );
    }

    return (
      <div className="flex flex-col h-64 items-center justify-center p-space-lg text-center gap-space-sm">
        <span className="material-symbols-outlined text-error text-[48px]">error</span>
        <h2 className="font-headline-md text-headline-md text-on-surface">System Error</h2>
        <p className="font-body-md text-on-surface-variant">
          Unable to fetch your result at this time.
        </p>
      </div>
    );
  }

  if (!data) return null;

  return (
    <div className="max-w-7xl mx-auto px-space-lg py-space-lg flex flex-col w-full gap-space-lg">
      <div className="flex flex-col lg:flex-row lg:items-end justify-between gap-space-md pb-space-lg border-b border-surface-container">
        <div className="flex flex-col gap-1">
          <div className="flex items-center gap-space-sm flex-wrap">
            <h1 className="font-display-lg text-display-lg text-on-surface tracking-tight">{data.examName} — Result</h1>
            <span className="px-2.5 py-0.5 rounded bg-tertiary-fixed text-on-tertiary-fixed font-label-sm font-semibold inline-flex items-center gap-1">
              {data.status.toUpperCase()}
            </span>
          </div>
        </div>
        <div className="flex items-center gap-2">
          <Link href={`/student/appeals/new?submissionId=${submissionId}`} className="px-4 py-2 bg-error text-on-error font-label-md rounded hover:bg-error/90 transition-colors flex items-center gap-1.5">
            <span className="material-symbols-outlined text-[18px]">gavel</span>
            Submit Appeal
          </Link>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-space-md mb-space-xl">
        <div className="lg:col-span-12 bg-surface-container-lowest rounded-xl p-space-lg shadow-sm flex flex-col justify-between">
          <div className="flex flex-col gap-space-md">
            <div className="flex items-center justify-between">
              <span className="font-label-md uppercase tracking-wider text-on-surface-variant font-semibold">Consolidated Assessment Total</span>
              <span className="font-label-sm px-2 py-0.5 rounded bg-surface-container text-on-surface-variant">Evaluated: {data.evaluatedAt}</span>
            </div>
            <div className="flex flex-col md:flex-row md:items-baseline gap-space-md mt-2">
              <div className="flex items-baseline gap-2">
                <span className="font-display-lg text-[64px] leading-none font-bold text-primary tracking-tight">{data.totalScore}</span>
                <span className="font-headline-lg text-outline font-normal">/ {data.maxTotalScore}</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div className="flex flex-col gap-space-md">
        <h2 className="font-headline-lg text-headline-lg text-on-surface">Criterion-Based Scoring Breakdown</h2>
        {data.criteria.map((criterion) => (
          <div key={criterion.criterionId} className="bg-surface-container-lowest rounded-xl shadow-sm p-space-md">
            <div className="flex items-center justify-between">
              <div className="flex flex-col">
                <span className="font-headline-sm text-on-surface">{criterion.name}</span>
                <span className="font-label-sm text-outline">{criterion.description}</span>
              </div>
              <div className="font-headline-sm text-tertiary font-semibold">
                {criterion.score} / {criterion.maxScore} pts
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
