'use client';

import React from 'react';
import { AppLink as Link } from '@/components/ui/app-link';
import { useStudentAppeal } from '../hooks';
import { 
  ArrowLeft, 
  CheckCircle2, 
  XCircle, 
  AlertCircle,
  ShieldCheck
} from 'lucide-react';
import { useTranslation } from '@/i18n';

export function StudentAppealDetailView({ appealId }: { appealId: string }) {
  const { t, formatDate } = useTranslation();
  const { data: appeal, isLoading, isError } = useStudentAppeal(appealId);

  if (isLoading) {
    return (
      <div className="flex flex-col h-72 items-center justify-center gap-3">
        <div className="w-8 h-8 rounded-full border-2 border-primary border-t-transparent animate-spin" />
        <p className="text-on-surface-variant font-label-md text-xs">{t.common.loading}</p>
      </div>
    );
  }

  if (isError || !appeal) {
    return (
      <div className="max-w-xl mx-auto my-12 p-8 rounded-2xl bg-surface-container-lowest border border-outline-variant/40 shadow-lg text-center flex flex-col items-center gap-3">
        <div className="p-3 rounded-full bg-rose-50 text-rose-600 dark:bg-rose-950/40 dark:text-rose-300">
          <AlertCircle className="w-8 h-8" />
        </div>
        <h2 className="font-headline-md text-headline-md text-on-surface">{t.appeals.appealNotFound}</h2>
        <p className="font-body-md text-on-surface-variant text-xs">{t.appeals.appealNotFoundDesc}</p>
        <Link href="/student/appeals" className="mt-2 px-4 py-2 bg-primary text-on-primary rounded-lg text-xs font-medium hover:bg-primary/90 transition-colors">
          {t.appeals.backToAppeals}
        </Link>
      </div>
    );
  }

  const isPending = appeal.status === 'PENDING';
  const isApproved = appeal.status === 'APPROVED';

  return (
    <div className="max-w-7xl mx-auto px-space-lg py-space-lg">
      <div className="flex flex-col w-full space-y-space-lg">
        {/* Header & Status */}
        <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-space-md pb-space-md border-b border-outline-variant/30">
          <div className="space-y-1">
            <nav className="flex items-center gap-2 text-on-surface-variant text-xs mb-1">
              <Link className="hover:text-primary transition-colors flex items-center gap-1 font-medium" href="/student/appeals">
                <ArrowLeft className="w-3.5 h-3.5" />
                <span>{t.appeals.appealsListTitle}</span>
              </Link>
              <span className="text-outline">/</span>
              <span className="text-primary font-semibold">Case #{appeal.id.substring(0, 8)}</span>
            </nav>
            <div className="flex items-center gap-space-sm flex-wrap">
              <h1 className="font-headline-lg text-headline-lg text-on-surface tracking-tight">
                {t.appeals.appealDetailTitle} &mdash; #{appeal.id.substring(0, 8)}
              </h1>
              {isPending && (
                <span className="text-xs px-2.5 py-0.5 rounded-full bg-amber-50 text-amber-700 border border-amber-200 dark:bg-amber-950/40 dark:text-amber-300 dark:border-amber-800/40 font-semibold flex items-center gap-1">
                  <span className="w-1.5 h-1.5 rounded-full bg-amber-500 animate-pulse"></span>
                  <span>{t.appeals.appealStatusPending}</span>
                </span>
              )}
              {isApproved && (
                <span className="text-xs px-2.5 py-0.5 rounded-full bg-emerald-50 text-emerald-700 border border-emerald-200 dark:bg-emerald-950/40 dark:text-emerald-300 dark:border-emerald-800/40 font-semibold flex items-center gap-1">
                  <CheckCircle2 className="w-3.5 h-3.5 text-emerald-600 dark:text-emerald-400" />
                  <span>{t.appeals.appealStatusApproved}</span>
                </span>
              )}
              {!isPending && !isApproved && (
                <span className="text-xs px-2.5 py-0.5 rounded-full bg-rose-50 text-rose-700 border border-rose-200 dark:bg-rose-950/40 dark:text-rose-300 dark:border-rose-800/40 font-semibold flex items-center gap-1">
                  <XCircle className="w-3.5 h-3.5 text-rose-600 dark:text-rose-400" />
                  <span>{t.appeals.appealStatusRejected}</span>
                </span>
              )}
            </div>
            <p className="text-xs text-on-surface-variant">
              Filing Date: {formatDate(appeal.createdAt)}
            </p>
          </div>

          <div className="flex items-center gap-2 self-start lg:self-auto">
            <Link 
              href="/student/appeals"
              className="px-4 py-2 rounded-lg bg-surface-container hover:bg-surface-container-high text-on-surface text-xs font-medium border border-outline-variant/40 transition-colors"
            >
              {t.appeals.backToAppeals}
            </Link>
          </div>
        </div>
        
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-space-lg items-start">
          {/* Sidebar */}
          <aside className="lg:col-span-4 space-y-space-md">
            <div className="bg-surface-container-lowest rounded-2xl p-space-md border border-outline-variant/40 shadow-xs space-y-3 text-xs">
              <div className="font-label-xs uppercase tracking-wider text-outline font-semibold pb-2 border-b border-outline-variant/20">
                Evaluation Context
              </div>
              <div>
                <span className="text-outline uppercase text-[10px] block">Target Assessment</span>
                <span className="font-semibold text-on-surface text-sm">{appeal.examName}</span>
              </div>
              <div>
                <span className="text-outline uppercase text-[10px] block">Submission Reference</span>
                <span className="font-mono text-on-surface text-xs">#{appeal.submissionId.substring(0, 8)}</span>
              </div>
            </div>
            
            <div className="bg-surface-container-lowest rounded-2xl p-space-md border border-outline-variant/40 shadow-xs space-y-2 text-xs">
              <div className="flex items-center gap-1.5 text-primary font-semibold">
                <ShieldCheck className="w-4 h-4" />
                <span>Governance Protocol</span>
              </div>
              <p className="text-on-surface-variant leading-relaxed">
                Decisions rendered by the Faculty Examination Board are formally documented. Additional filings for this submission are restricted.
              </p>
            </div>
          </aside>

          {/* Main Content */}
          <main className="lg:col-span-8 space-y-space-md">
            <div className="bg-surface-container-lowest rounded-2xl border border-outline-variant/40 shadow-xs p-space-lg space-y-4">
              <div className="space-y-1">
                <span className="text-xs font-semibold text-outline uppercase tracking-wider block">
                  {t.appeals.appealBasisLabel}
                </span>
                <div className="inline-block px-3 py-1.5 rounded-lg bg-surface-container text-xs font-semibold text-on-surface capitalize">
                  {appeal.basis.replace(/_/g, ' ')}
                </div>
              </div>

              <div className="space-y-1 pt-space-md border-t border-outline-variant/20">
                <span className="text-xs font-semibold text-outline uppercase tracking-wider block">
                  {t.appeals.studentExplanation}
                </span>
                <div className="bg-surface-container-low p-space-md rounded-xl whitespace-pre-wrap text-xs text-on-surface leading-relaxed font-mono border border-outline-variant/20">
                  {appeal.explanation}
                </div>
              </div>

              <div className="space-y-2 pt-space-md border-t border-outline-variant/20">
                <span className="text-xs font-semibold text-outline uppercase tracking-wider block">
                  {t.appeals.resolutionRemarks}
                </span>
                <div className="bg-surface-container-low p-space-md rounded-xl space-y-2 border border-outline-variant/20">
                  <div className="text-xs">
                    {appeal.resolvedAt ? (
                      <span className="font-semibold text-on-surface">
                        Decision: {appeal.status} (Concluded on {formatDate(appeal.resolvedAt)})
                      </span>
                    ) : (
                      <span className="text-on-surface-variant italic">
                        The board has not yet recorded a final determination.
                      </span>
                    )}
                  </div>
                  {appeal.reviewerNotes && (
                    <div className="pt-2 border-t border-outline-variant/20">
                      <span className="font-label-xs uppercase tracking-wider text-outline block mb-1">
                        {t.appeals.reviewerNotes}:
                      </span>
                      <div className="text-xs text-on-surface whitespace-pre-wrap">{appeal.reviewerNotes}</div>
                    </div>
                  )}
                </div>
              </div>
            </div>
          </main>
        </div>
      </div>
    </div>
  );
}
