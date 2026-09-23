'use client';

import React from 'react';
import { usePlagiarismList } from '../hooks';
import { AxiosError } from 'axios';
import { AppLink as Link } from '@/components/ui/app-link';

export function TeacherPlagiarismDashboardView() {
  const { data, isLoading, isError, error } = usePlagiarismList();

  if (isLoading) {
    return (
      <div className="flex h-64 items-center justify-center">
        <div className="text-on-surface-variant font-label-md">Loading plagiarism data...</div>
      </div>
    );
  }

  if (isError) {
    const status = (error as AxiosError)?.response?.status;
    if (status === 403) {
      return (
        <div className="flex flex-col h-64 items-center justify-center p-space-lg text-center gap-space-sm">
          <span className="material-symbols-outlined text-error text-[48px]">lock</span>
          <h2 className="font-headline-md text-headline-md text-on-surface">Forbidden</h2>
          <p className="font-body-md text-on-surface-variant">You do not have permission to access this resource.</p>
        </div>
      );
    }
    if (status === 404) {
      return (
        <div className="flex flex-col h-64 items-center justify-center p-space-lg text-center gap-space-sm">
          <span className="material-symbols-outlined text-outline text-[48px]">search_off</span>
          <h2 className="font-headline-md text-headline-md text-on-surface">Not Found</h2>
          <p className="font-body-md text-on-surface-variant">The requested resource could not be found.</p>
        </div>
      );
    }
    return (
      <div className="flex flex-col h-64 items-center justify-center p-space-lg text-center gap-space-sm">
        <span className="material-symbols-outlined text-error text-[48px]">error</span>
        <h2 className="font-headline-md text-headline-md text-on-surface">Integration Error</h2>
        <p className="font-body-md text-on-surface-variant">Backend is currently offline or the contract is unconfirmed.</p>
        <p className="font-code-body text-code-body text-outline">{(error as Error).message}</p>
      </div>
    );
  }

  if (!data) return null;

  const totalPairs = data.length;
  const flaggedPairs = data.filter(p => p.pair.isFlagged).length;
  const moderatePairs = data.filter(p => !p.pair.isFlagged && p.pair.similarityRate >= 40 && p.pair.similarityRate < 70).length;
  const normalPairs = data.filter(p => !p.pair.isFlagged && p.pair.similarityRate < 40).length;

  return (
    <div className="px-space-lg py-space-lg flex flex-col gap-space-lg">
      <div className="flex flex-col lg:flex-row lg:items-start lg:justify-between gap-space-md bg-surface-container-lowest p-space-lg rounded-xl shadow-sm">
        <div className="flex flex-col gap-space-xs max-w-3xl">
          <div className="flex items-center gap-space-xs text-on-surface-variant font-code-sm text-code-sm">
            <span className="inline-flex items-center gap-1 text-secondary font-medium">
              <span className="material-symbols-outlined text-label-md">account_tree</span>
              AST v4.2 Pipeline
            </span>
            <span>/</span>
            <span className="text-on-surface font-semibold">Integrity Diagnostic Engine</span>
          </div>
          <h1 className="font-headline-xl text-headline-xl text-on-surface tracking-tight">
            AST Code Plagiarism Detection & Integrity Matrix
          </h1>
          <p className="font-body-lg text-body-lg text-on-surface-variant">
            Automated structural similarity analysis using Abstract Syntax Tree (AST) fingerprinting. Comparing submissions across exams.
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-space-xs pt-space-xs lg:pt-0">
          <button className="h-9 px-space-sm bg-surface-container-low hover:bg-surface-container text-on-surface rounded font-title-md text-label-md transition-all inline-flex items-center gap-1.5 shadow-sm">
            <span className="material-symbols-outlined text-label-md">refresh</span>
            <span>Run Cluster Re-Analysis</span>
          </button>
        </div>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-space-md">
        <div className="bg-surface-container-lowest p-space-md rounded-xl shadow-sm flex flex-col justify-between gap-space-sm relative overflow-hidden">
          <div className="flex items-center justify-between">
            <span className="font-label-xs text-label-xs uppercase tracking-wider text-on-surface-variant font-semibold">Pairwise Comparisons</span>
            <span className="p-1 rounded bg-surface-container-low text-on-surface-variant">
              <span className="material-symbols-outlined text-title-md">hub</span>
            </span>
          </div>
          <div className="flex flex-col">
            <div className="flex items-baseline gap-space-xs">
              <span className="font-headline-xl text-headline-xl font-bold text-on-surface tabular-nums">{totalPairs}</span>
              <span className="font-code-sm text-code-sm text-on-surface-variant font-medium">pairs</span>
            </div>
            <p className="font-body-md text-body-md text-on-surface-variant mt-0.5">All-pairs AST sub-tree hashing</p>
          </div>
          <div className="w-full bg-surface-container-low h-1.5 rounded-full overflow-hidden mt-1">
            <div className="bg-secondary h-full rounded-full w-full"></div>
          </div>
        </div>

        <div className="bg-surface-container-lowest p-space-md rounded-xl shadow-sm flex flex-col justify-between gap-space-sm relative overflow-hidden">
          <div className="flex items-center justify-between">
            <span className="font-label-xs text-label-xs uppercase tracking-wider text-error font-semibold">Flagged Submissions (≥70%)</span>
            <span className="px-2 py-0.5 rounded-full bg-error-container text-on-error-container font-label-xs text-label-xs font-bold inline-flex items-center gap-1">
              <span className="w-1.5 h-1.5 rounded-full bg-error animate-pulse"></span>
              FLAGGED
            </span>
          </div>
          <div className="flex flex-col">
            <div className="flex items-baseline gap-space-xs">
              <span className="font-headline-xl text-headline-xl font-bold text-error tabular-nums">{flaggedPairs} Pairs</span>
            </div>
            <p className="font-body-md text-body-md text-on-surface-variant mt-0.5">Critical isomorphism threshold exceeded</p>
          </div>
          <div className="w-full bg-error-container h-1.5 rounded-full overflow-hidden mt-1">
            <div className="bg-error h-full rounded-full" style={{ width: totalPairs ? `${(flaggedPairs / totalPairs) * 100}%` : '0%' }}></div>
          </div>
        </div>

        <div className="bg-surface-container-lowest p-space-md rounded-xl shadow-sm flex flex-col justify-between gap-space-sm relative overflow-hidden">
          <div className="flex items-center justify-between">
            <span className="font-label-xs text-label-xs uppercase tracking-wider text-on-surface-variant font-semibold">Moderate Similarity (40–69%)</span>
            <span className="px-2 py-0.5 rounded-full bg-surface-container-high text-on-surface font-label-xs text-label-xs font-semibold inline-flex items-center gap-1">
              <span className="material-symbols-outlined text-label-md text-secondary">visibility</span>
              Under Watch
            </span>
          </div>
          <div className="flex flex-col">
            <div className="flex items-baseline gap-space-xs">
              <span className="font-headline-xl text-headline-xl font-bold text-on-surface tabular-nums">{moderatePairs} Pairs</span>
            </div>
            <p className="font-body-md text-body-md text-on-surface-variant mt-0.5">Partial node congruence detected</p>
          </div>
          <div className="w-full bg-surface-container-low h-1.5 rounded-full overflow-hidden mt-1">
            <div className="bg-secondary-container h-full rounded-full" style={{ width: totalPairs ? `${(moderatePairs / totalPairs) * 100}%` : '0%' }}></div>
          </div>
        </div>

        <div className="bg-surface-container-lowest p-space-md rounded-xl shadow-sm flex flex-col justify-between gap-space-sm relative overflow-hidden">
          <div className="flex items-center justify-between">
            <span className="font-label-xs text-label-xs uppercase tracking-wider text-on-surface-variant font-semibold">Normal Comparisons (&lt;40%)</span>
          </div>
          <div className="flex flex-col">
            <div className="flex items-baseline gap-space-xs">
              <span className="font-headline-xl text-headline-xl font-bold text-on-surface tabular-nums">{normalPairs}</span>
            </div>
            <p className="font-body-md text-body-md text-on-surface-variant mt-0.5">Below threshold</p>
          </div>
          <div className="w-full bg-surface-container-low h-1.5 rounded-full overflow-hidden mt-1">
            <div className="bg-tertiary h-full rounded-full" style={{ width: totalPairs ? `${(normalPairs / totalPairs) * 100}%` : '0%' }}></div>
          </div>
        </div>
      </div>

      <div className="bg-surface-container-lowest rounded-xl shadow-sm overflow-hidden flex flex-col">
        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse">
            <thead>
              <tr className="bg-surface-container-low text-on-surface-variant font-label-xs text-label-xs uppercase tracking-wider">
                <th className="py-3 px-space-md font-semibold w-1/4">Submission A (Author)</th>
                <th className="py-3 px-space-md font-semibold w-1/4">Submission B (Author)</th>
                <th className="py-3 px-space-md font-semibold w-1/4">Structural Match</th>
                <th className="py-3 px-space-md font-semibold text-right">Investigation Action</th>
              </tr>
            </thead>
            <tbody className="divide-y-0 font-body-md text-body-md text-on-surface">
              {data.length === 0 ? (
                <tr>
                  <td colSpan={4} className="py-8 text-center text-outline font-label-md">
                    No plagiarism records found for your exams.
                  </td>
                </tr>
              ) : (
                data.map((record) => {
                  const pair = record.pair;
                  return (
                  <tr key={pair.pairId} className="hover:bg-surface-container-low/60 transition-colors group">
                    <td className="py-space-md px-space-md align-top">
                      <div className="flex items-start gap-space-xs">
                        <div className="w-8 h-8 rounded-full bg-primary-fixed text-on-primary-fixed flex items-center justify-center font-title-md text-label-md flex-shrink-0 font-bold shadow-sm mt-0.5">
                          {(pair.studentAName ?? pair.studentAId).substring(0, 2).toUpperCase()}
                        </div>
                        <div className="flex flex-col min-w-0">
                          <span className="font-title-md text-body-md text-on-surface font-semibold truncate">{pair.studentAName ?? 'Unknown'}</span>
                          <span className="font-code-sm text-code-sm text-on-surface-variant">{pair.studentAId}</span>
                        </div>
                      </div>
                    </td>
                    <td className="py-space-md px-space-md align-top">
                      <div className="flex items-start gap-space-xs">
                        <div className="w-8 h-8 rounded-full bg-secondary-fixed text-on-secondary-fixed flex items-center justify-center font-title-md text-label-md flex-shrink-0 font-bold shadow-sm mt-0.5">
                          {(pair.studentBName ?? pair.studentBId).substring(0, 2).toUpperCase()}
                        </div>
                        <div className="flex flex-col min-w-0">
                          <span className="font-title-md text-body-md text-on-surface font-semibold truncate">{pair.studentBName ?? 'Unknown'}</span>
                          <span className="font-code-sm text-code-sm text-on-surface-variant">{pair.studentBId}</span>
                        </div>
                      </div>
                    </td>
                    <td className="py-space-md px-space-md align-top">
                      <div className="flex flex-col gap-1.5">
                        <div className="flex items-baseline justify-between">
                          <span className={`font-title-md text-headline-sm font-bold tabular-nums ${pair.isFlagged ? 'text-error' : (pair.similarityRate >= 40 ? 'text-secondary' : 'text-on-surface-variant')}`}>
                            {pair.similarityRate}%
                          </span>
                          <span className={`font-code-sm text-code-sm uppercase font-semibold ${pair.isFlagged ? 'text-error' : (pair.similarityRate >= 40 ? 'text-secondary' : 'text-on-surface-variant')}`}>
                            {pair.isFlagged ? 'FLAGGED' : 'MODERATE'}
                          </span>
                        </div>
                        <div className="w-full bg-surface-container h-2 rounded-full overflow-hidden">
                          <div className={`h-full rounded-full ${pair.isFlagged ? 'bg-error' : (pair.similarityRate >= 40 ? 'bg-secondary' : 'bg-tertiary')}`} style={{ width: `${pair.similarityRate}%` }}></div>
                        </div>
                      </div>
                    </td>
                    <td className="py-space-md px-space-md align-top text-right">
                      <Link href={`/teacher/plagiarism/${record.recordId}`} className="h-8 px-space-sm bg-primary text-on-primary hover:bg-inverse-surface rounded font-title-md text-label-md transition-all inline-flex items-center gap-1 shadow-sm ml-auto">
                        <span className="material-symbols-outlined text-label-md">compare_arrows</span>
                        <span>Review Code</span>
                      </Link>
                    </td>
                  </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
