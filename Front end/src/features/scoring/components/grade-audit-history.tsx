'use client';

import React from 'react';
import { useTeacherGradeAuditLogs } from '../hooks';
import { GradeAuditLog } from '../types';
import { 
  History, 
  RotateCw, 
  User, 
  Clock, 
  ArrowRight, 
  MessageSquareText, 
  CheckCircle2, 
  Sliders, 
  FileEdit, 
  RotateCcw, 
  FileText,
  AlertCircle,
  Loader2,
  ShieldCheck
} from 'lucide-react';
import { useTranslation } from '@/i18n';

interface GradeAuditHistoryProps {
  submissionId: string;
  className?: string;
}

export function GradeAuditHistory({ submissionId, className = '' }: GradeAuditHistoryProps) {
  const { t, formatDate } = useTranslation();
  const { data: logs, isLoading, isError, error, refetch, isFetching } = useTeacherGradeAuditLogs(submissionId);

  const formatRubricScore = (val: number | null | undefined): React.ReactNode => {
    if (val === null || val === undefined) {
      return <span className="text-outline font-medium">{t.scoring.notEvaluated}</span>;
    }
    return <span className="font-mono font-semibold">{Number(val).toFixed(2)} {t.scoring.points}</span>;
  };

  const formatOverrideScore = (val: number | null | undefined): React.ReactNode => {
    if (val === null || val === undefined) {
      return (
        <span className="inline-flex items-center px-1.5 py-0.5 rounded text-[11px] font-medium bg-surface-container text-outline">
          {t.scoring.noOverride}
        </span>
      );
    }
    return <span className="font-mono font-semibold text-amber-600 dark:text-amber-400">{Number(val).toFixed(2)} {t.scoring.points}</span>;
  };

  const formatEffectiveScore = (val: number | null | undefined, isPost = false): React.ReactNode => {
    if (val === null || val === undefined) {
      return <span className="text-outline font-medium">—</span>;
    }
    return (
      <span className={`font-mono font-bold ${isPost ? 'text-primary' : 'text-on-surface'}`}>
        {Number(val).toFixed(2)} {t.scoring.points}
      </span>
    );
  };

  const getActionBadge = (actionType: string) => {
    switch (actionType) {
      case 'INITIAL_GRADING':
        return (
          <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md text-xs font-semibold bg-emerald-500/10 text-emerald-700 dark:text-emerald-400 border border-emerald-500/20">
            <CheckCircle2 className="w-3.5 h-3.5" />
            <span>{t.scoring.initialGrading}</span>
          </span>
        );
      case 'OVERRIDE_SET':
        return (
          <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md text-xs font-semibold bg-amber-500/10 text-amber-700 dark:text-amber-400 border border-amber-500/20">
            <Sliders className="w-3.5 h-3.5" />
            <span>{t.scoring.overrideSet}</span>
          </span>
        );
      case 'OVERRIDE_CLEARED':
        return (
          <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md text-xs font-semibold bg-zinc-500/10 text-zinc-700 dark:text-zinc-400 border border-zinc-500/20">
            <RotateCcw className="w-3.5 h-3.5" />
            <span>{t.scoring.overrideCleared}</span>
          </span>
        );
      case 'OVERRIDE_UPDATE':
        return (
          <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md text-xs font-semibold bg-sky-500/10 text-sky-700 dark:text-sky-400 border border-sky-500/20">
            <FileEdit className="w-3.5 h-3.5" />
            <span>{t.scoring.overrideUpdated}</span>
          </span>
        );
      case 'RUBRIC_UPDATE':
        return (
          <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md text-xs font-semibold bg-indigo-500/10 text-indigo-700 dark:text-indigo-400 border border-indigo-500/20">
            <FileText className="w-3.5 h-3.5" />
            <span>{t.scoring.rubricUpdated}</span>
          </span>
        );
      default:
        return (
          <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md text-xs font-semibold bg-surface-container text-on-surface-variant border border-outline-variant/30">
            <History className="w-3.5 h-3.5" />
            <span>{actionType}</span>
          </span>
        );
    }
  };

  const sortedLogs = React.useMemo(() => {
    if (!logs || !Array.isArray(logs)) return [];
    return [...logs].sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());
  }, [logs]);

  return (
    <section 
      aria-label="Grade Audit History"
      className={`bg-surface-container-lowest rounded-xl p-space-lg shadow-sm border border-outline-variant/20 flex flex-col gap-space-md ${className}`}
    >
      {/* Section Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between pb-space-sm border-b border-outline-variant/30 gap-space-sm">
        <div className="flex items-center gap-space-sm">
          <span className="p-2 rounded-lg bg-primary/10 text-primary">
            <History className="w-5 h-5" />
          </span>
          <div>
            <div className="flex items-center gap-2">
              <h2 className="font-headline-sm text-headline-sm text-on-surface font-semibold tracking-tight">
                {t.scoring.gradeAuditTrailTitle}
              </h2>
              {sortedLogs.length > 0 && (
                <span className="text-xs px-2 py-0.5 rounded-full font-medium bg-surface-container text-on-surface-variant">
                  {sortedLogs.length} {sortedLogs.length === 1 ? 'event' : 'events'}
                </span>
              )}
            </div>
            <p className="font-body-sm text-body-sm text-on-surface-variant mt-0.5">
              {t.scoring.gradeAuditTrailDesc}
            </p>
          </div>
        </div>

        <button
          type="button"
          onClick={() => refetch()}
          disabled={isFetching}
          className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-label-sm font-medium text-on-surface-variant hover:text-on-surface hover:bg-surface-container transition-colors disabled:opacity-50 self-start sm:self-center cursor-pointer"
          title="Refresh audit history"
        >
          <RotateCw className={`w-4 h-4 ${isFetching ? 'animate-spin text-primary' : ''}`} />
          <span>{t.common.refresh}</span>
        </button>
      </div>

      {/* Loading State */}
      {isLoading && (
        <div className="flex flex-col items-center justify-center py-12 text-on-surface-variant gap-3">
          <Loader2 className="w-6 h-6 animate-spin text-primary" />
          <span className="font-label-md text-label-md">Loading authoritative grade audit trail...</span>
        </div>
      )}

      {/* Error State */}
      {isError && (
        <div className="flex flex-col items-center justify-center py-8 px-4 rounded-xl bg-error-container/20 border border-error/20 text-center gap-2">
          <AlertCircle className="w-8 h-8 text-error shrink-0" />
          <h3 className="font-headline-sm text-headline-sm text-on-surface font-medium">Failed to load grade history</h3>
          <p className="font-body-sm text-body-sm text-on-surface-variant max-w-md">
            {error instanceof Error ? error.message : 'An error occurred while communicating with the audit endpoint.'}
          </p>
          <button
            type="button"
            onClick={() => refetch()}
            className="mt-2 px-3 py-1.5 rounded-lg text-xs font-semibold bg-error text-on-error hover:bg-error/90 transition-colors"
          >
            Retry Request
          </button>
        </div>
      )}

      {/* Empty State */}
      {!isLoading && !isError && sortedLogs.length === 0 && (
        <div className="flex flex-col items-center justify-center py-10 px-4 text-center rounded-xl bg-surface-container-low/50 border border-dashed border-outline-variant/40 gap-2">
          <ShieldCheck className="w-9 h-9 text-outline" />
          <h3 className="font-headline-sm text-headline-sm text-on-surface font-medium mt-1">No grade history recorded yet</h3>
          <p className="font-body-sm text-body-sm text-on-surface-variant max-w-md">
            This submission has not undergone any official evaluations or score overrides yet. Future changes will be recorded here permanently.
          </p>
        </div>
      )}

      {/* Populated Audit Log Timeline */}
      {!isLoading && !isError && sortedLogs.length > 0 && (
        <div className="flex flex-col gap-space-md" data-testid="grade-audit-logs-list">
          {sortedLogs.map((log: GradeAuditLog) => (
            <article 
              key={log.id}
              data-testid={`grade-audit-log-${log.id}`}
              className="bg-surface-container-low/40 hover:bg-surface-container-low transition-colors rounded-xl p-space-md border border-outline-variant/30 flex flex-col gap-space-sm"
            >
              {/* Header row: Action Badge + Actor + Timestamp */}
              <div className="flex flex-wrap items-center justify-between gap-y-2 gap-x-space-md">
                <div className="flex flex-wrap items-center gap-space-sm">
                  {getActionBadge(log.actionType)}

                  <div className="flex items-center gap-1.5 text-xs text-on-surface-variant">
                    <User className="w-3.5 h-3.5 text-primary" />
                    <span className="font-medium text-on-surface">{log.actorName || 'Authorized Teacher'}</span>
                    {log.actorEmail && (
                      <span className="text-outline hidden sm:inline">({log.actorEmail})</span>
                    )}
                  </div>
                </div>

                <div className="flex items-center gap-1.5 text-xs text-outline font-mono">
                  <Clock className="w-3.5 h-3.5" />
                  <time dateTime={log.createdAt}>{formatDate(log.createdAt, { dateStyle: 'medium', timeStyle: 'short' })}</time>
                </div>
              </div>

              {/* 3-Column Score Diff Grid */}
              <div className="grid grid-cols-1 md:grid-cols-3 gap-space-sm mt-1">
                {/* Rubric Score */}
                <div className="bg-surface-container-lowest rounded-lg p-2.5 border border-outline-variant/20 flex flex-col gap-1">
                  <span className="text-[11px] font-semibold text-outline uppercase tracking-wider">
                    Rubric Score
                  </span>
                  <div className="flex items-center gap-2 text-xs">
                    <span className="text-on-surface-variant">{formatRubricScore(log.preRubricScore)}</span>
                    <ArrowRight className="w-3 h-3 text-outline shrink-0" />
                    <span className="text-on-surface">{formatRubricScore(log.postRubricScore)}</span>
                  </div>
                </div>

                {/* Override Score */}
                <div className="bg-surface-container-lowest rounded-lg p-2.5 border border-outline-variant/20 flex flex-col gap-1">
                  <span className="text-[11px] font-semibold text-outline uppercase tracking-wider">
                    Manual Override
                  </span>
                  <div className="flex items-center gap-2 text-xs">
                    <span className="text-on-surface-variant">{formatOverrideScore(log.preOverrideScore)}</span>
                    <ArrowRight className="w-3 h-3 text-outline shrink-0" />
                    <span className="text-on-surface">{formatOverrideScore(log.postOverrideScore)}</span>
                  </div>
                </div>

                {/* Effective Score */}
                <div className="bg-surface-container-lowest rounded-lg p-2.5 border border-primary/20 bg-primary/[0.02] flex flex-col gap-1">
                  <span className="text-[11px] font-semibold text-primary uppercase tracking-wider">
                    Effective Score
                  </span>
                  <div className="flex items-center gap-2 text-xs">
                    <span>{formatEffectiveScore(log.preEffectiveScore, false)}</span>
                    <ArrowRight className="w-3 h-3 text-primary shrink-0" />
                    <span>{formatEffectiveScore(log.postEffectiveScore, true)}</span>
                  </div>
                </div>
              </div>

              {/* Consolidated Rationale / Remarks */}
              {log.rationale && log.rationale.trim().length > 0 && (
                <div className="mt-1 p-space-sm rounded-lg bg-surface-container-lowest border border-outline-variant/20 flex gap-2.5 text-xs">
                  <MessageSquareText className="w-4 h-4 text-on-surface-variant shrink-0 mt-0.5" />
                  <div className="flex flex-col gap-0.5">
                    <span className="font-semibold text-on-surface-variant text-[11px] uppercase tracking-wide">
                      Instructor Justification & Comments
                    </span>
                    <p className="font-body-sm text-on-surface whitespace-pre-line leading-relaxed">
                      {log.rationale}
                    </p>
                  </div>
                </div>
              )}
            </article>
          ))}
        </div>
      )}
    </section>
  );
}
