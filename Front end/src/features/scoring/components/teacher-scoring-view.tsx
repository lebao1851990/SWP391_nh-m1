'use client';

import React from 'react';
import { useTeacherScoring } from '../hooks';
import { TeacherScoringForm } from './teacher-scoring-form';
import { AxiosError } from 'axios';

interface TeacherScoringViewProps {
  submissionId: string;
}

export function TeacherScoringView({ submissionId }: TeacherScoringViewProps) {
  const { data, isLoading, isError, error } = useTeacherScoring(submissionId);

  if (isLoading) {
    return (
      <div className="flex h-64 items-center justify-center">
        <div className="text-on-surface-variant font-label-md">Loading scoring data from backend authority...</div>
      </div>
    );
  }

  if (isError) {
    // Determine the type of error. If it's a 401, the interceptor already redirected.
    // We handle 403 and 404 explicitly as required.
    const status = (error as AxiosError)?.response?.status;
    
    if (status === 403) {
      return (
        <div className="flex flex-col h-64 items-center justify-center p-space-lg text-center gap-space-sm">
          <span className="material-symbols-outlined text-error text-[48px]">block</span>
          <h2 className="font-headline-md text-headline-md text-on-surface">Access Forbidden</h2>
          <p className="font-body-md text-on-surface-variant">You do not have authorization to score this submission.</p>
        </div>
      );
    }

    if (status === 404) {
      return (
        <div className="flex flex-col h-64 items-center justify-center p-space-lg text-center gap-space-sm">
          <span className="material-symbols-outlined text-outline text-[48px]">search_off</span>
          <h2 className="font-headline-md text-headline-md text-on-surface">Not Found</h2>
          <p className="font-body-md text-on-surface-variant">The scoring record or submission could not be found.</p>
        </div>
      );
    }

    return (
      <div className="flex flex-col h-64 items-center justify-center p-space-lg text-center gap-space-sm">
        <span className="material-symbols-outlined text-error text-[48px]">error</span>
        <h2 className="font-headline-md text-headline-md text-on-surface">Backend Offline / Integration Error</h2>
        <p className="font-body-md text-on-surface-variant">
          The backend endpoints for scoring are currently unavailable or unconfirmed.
          <br />
          <span className="text-outline font-mono text-label-sm mt-2 block">{error?.message}</span>
        </p>
      </div>
    );
  }

  if (!data) {
    return (
      <div className="flex h-64 items-center justify-center">
        <div className="text-on-surface-variant font-label-md">No scoring data available.</div>
      </div>
    );
  }

  return (
    <TeacherScoringForm submissionId={submissionId} initialData={data} />
  );
}
