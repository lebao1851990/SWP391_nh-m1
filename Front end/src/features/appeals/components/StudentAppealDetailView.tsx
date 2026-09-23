'use client';
import React from 'react';
import { AppLink as Link } from '@/components/ui/app-link';
import { useStudentAppeal } from '../hooks';

export function StudentAppealDetailView({ appealId }: { appealId: string }) {
  const { data: appeal, isLoading, isError } = useStudentAppeal(appealId);

  if (isLoading) {
    return (
      <div className="flex flex-col w-full h-screen items-center justify-center">
        <span className="material-symbols-outlined text-[32px] text-primary animate-spin">refresh</span>
        <span className="mt-4 font-body-md text-on-surface-variant">Loading appeal details...</span>
      </div>
    );
  }

  if (isError || !appeal) {
    return (
      <div className="p-space-lg max-w-7xl mx-auto mt-16 text-center text-error">
        <span className="material-symbols-outlined text-[48px] mb-2">error</span>
        <h2 className="font-headline-md">Appeal Not Found</h2>
        <p className="mt-2 font-body-md">We could not retrieve this appeal. It may not exist or the backend is offline.</p>
        <Link href="/student/appeals">
          <button className="mt-6 px-4 py-2 bg-surface-container text-on-surface rounded font-label-md">Back to Appeals</button>
        </Link>
      </div>
    );
  }

  const isPending = appeal.status === 'PENDING';
  const isApproved = appeal.status === 'APPROVED';

  return (
    <>
      <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-space-md pb-space-lg mb-space-lg border-b border-outline-variant/30">
        <div className="space-y-1">
          <div className="flex items-center gap-space-sm">
            <h1 className="font-headline-lg text-headline-lg text-on-surface tracking-tight">Grade &amp; Similarity Appeal Details</h1>
            {isPending && <span className="font-label-sm text-label-sm px-2 py-0.5 rounded bg-[#fffbeb] text-[#92400e] font-semibold">Under Review</span>}
            {isApproved && <span className="font-label-sm text-label-sm px-2 py-0.5 rounded bg-[#ecfdf5] text-[#065f46] font-semibold">Appeal Accepted</span>}
            {!isPending && !isApproved && <span className="font-label-sm text-label-sm px-2 py-0.5 rounded bg-surface-container-low text-outline font-semibold">Appeal Rejected</span>}
          </div>
          <p className="font-body-md text-body-md text-on-surface-variant">Submitted on {new Date(appeal.createdAt).toLocaleDateString()}</p>
        </div>
        <div className="flex flex-col gap-1 items-end">
          <div className="flex items-center gap-space-sm">
            <span className="font-label-sm text-label-sm text-outline uppercase tracking-wider">Case ID</span>
            <span className="font-label-sm text-label-sm px-2.5 py-1 rounded bg-surface-container text-on-surface font-semibold">{appeal.id}</span>
          </div>
          <Link href="/student/appeals">
            <span className="font-label-sm text-primary hover:underline cursor-pointer">Back to all appeals</span>
          </Link>
        </div>
      </div>
      
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-space-xl items-start">
        <aside className="lg:col-span-4 space-y-space-md">
          <div className="bg-surface-container-lowest rounded-xl p-space-md shadow-sm border border-outline-variant/20 relative overflow-hidden">
            <div className="absolute top-0 left-0 right-0 h-1 bg-outline"></div>
            <div className="flex items-center justify-between pb-space-sm border-b border-outline-variant/30 mb-space-md">
              <span className="font-label-sm text-label-sm text-outline font-semibold uppercase tracking-wider">Evaluation Context</span>
              <span className="font-label-sm text-label-sm text-outline">ID #{appeal.submissionId}</span>
            </div>
            <div className="space-y-space-md">
              <div>
                <div className="font-label-sm text-label-sm text-outline uppercase">Target Assessment</div>
                <div className="font-headline-sm text-headline-sm text-on-surface font-semibold mt-0.5">{appeal.examName}</div>
              </div>
            </div>
          </div>
          
          <div className="bg-surface-container-high/60 rounded-xl p-space-md border border-outline-variant/30 text-on-surface">
            <div className="flex items-start gap-space-sm">
              <span className="material-symbols-outlined text-primary text-[20px] shrink-0 mt-0.5">verified_user</span>
              <div className="space-y-1">
                <span className="font-headline-sm text-headline-sm block text-on-surface leading-tight">Institutional Policy Notice</span>
                <p className="font-body-sm text-body-sm text-on-surface-variant leading-relaxed">
                  Decisions rendered by the Faculty Examination Board are final. No further appeals are permitted for this specific submission once a decision is logged.
                </p>
              </div>
            </div>
          </div>
        </aside>

        <main className="lg:col-span-8">
          <div className="bg-surface-container-lowest rounded-xl shadow-sm border border-outline-variant/20 p-space-lg space-y-space-xl">
            <div className="space-y-space-sm">
              <label className="font-headline-sm text-headline-sm text-on-surface block">
                Appeal Classification
              </label>
              <div className="bg-surface-container p-space-md rounded-lg">
                <span className="font-body-md text-on-surface font-semibold capitalize">
                  {appeal.basis.replace(/_/g, ' ')}
                </span>
              </div>
            </div>

            <div className="space-y-space-sm pt-space-md border-t border-outline-variant/20">
              <label className="font-headline-sm text-headline-sm text-on-surface block">
                Explanation Provided
              </label>
              <div className="bg-surface-container-low p-space-md rounded-lg whitespace-pre-wrap font-body-md text-on-surface">
                {appeal.explanation}
              </div>
            </div>

            <div className="space-y-space-sm pt-space-md border-t border-outline-variant/20">
              <label className="font-headline-sm text-headline-sm text-on-surface block">
                Resolution Status
              </label>
              <div className="bg-surface-container-low p-space-md rounded-lg">
                {appeal.resolvedAt ? (
                  <span className="font-body-md text-on-surface">Resolved on {new Date(appeal.resolvedAt).toLocaleDateString()}</span>
                ) : (
                  <span className="font-body-md text-on-surface-variant">The board has not yet concluded its review.</span>
                )}
              </div>
            </div>
          </div>
        </main>
      </div>
    </>
  );
}
