'use client';

import React from 'react';
import { useStudentResult } from '../hooks';
import { AxiosError } from 'axios';
import { AppLink as Link } from '@/components/ui/app-link';
import { StudentAiAssistantCard } from '@/features/submissions/components';
import { 
  ArrowLeft, 
  Award, 
  Scale, 
  CheckCircle2, 
  AlertCircle, 
  Calendar, 
  Lock, 
  FileCode2,
  ExternalLink
} from 'lucide-react';

interface StudentResultViewProps {
  submissionId: string;
}

export function StudentResultView({ submissionId }: StudentResultViewProps) {
  const { data, isLoading, isError, error } = useStudentResult(submissionId);

  if (isLoading) {
    return (
      <div className="flex flex-col h-72 items-center justify-center gap-3">
        <div className="w-8 h-8 rounded-full border-2 border-primary border-t-transparent animate-spin" />
        <p className="text-on-surface-variant font-label-md text-xs">Loading authorized assessment result...</p>
      </div>
    );
  }

  if (isError) {
    const status = (error as AxiosError)?.response?.status;
    return (
      <div className="max-w-xl mx-auto my-12 p-8 rounded-2xl bg-surface-container-lowest border border-outline-variant/40 shadow-lg text-center flex flex-col items-center gap-3">
        <div className="p-3 rounded-full bg-rose-50 text-rose-600">
          {status === 403 ? <Lock className="w-8 h-8" /> : <AlertCircle className="w-8 h-8" />}
        </div>
        <h2 className="font-headline-md text-headline-md text-on-surface">
          {status === 403 ? 'Result Not Available' : status === 404 ? 'Submission Not Found' : 'System Error'}
        </h2>
        <p className="font-body-md text-on-surface-variant text-xs">
          {status === 403 
            ? 'Your result has not been released yet, or you do not have permission to view it.'
            : (error as Error).message}
        </p>
        <Link href="/student/submissions" className="mt-2 px-4 py-2 bg-primary text-on-primary rounded-lg text-xs font-medium">
          Back to Submissions
        </Link>
      </div>
    );
  }

  if (!data) return null;

  return (
    <div className="max-w-7xl mx-auto px-space-lg py-space-lg flex flex-col w-full gap-space-lg">
      {/* Header & Actions */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-space-md pb-space-md border-b border-outline-variant/30">
        <div className="flex flex-col gap-1">
          <nav className="flex items-center gap-2 text-on-surface-variant text-xs mb-1">
            <Link className="hover:text-primary transition-colors flex items-center gap-1 font-medium" href="/student/submissions">
              <ArrowLeft className="w-3.5 h-3.5" />
              <span>Submissions</span>
            </Link>
            <span className="text-outline-variant">/</span>
            <span className="text-primary font-semibold">{data.examName}</span>
          </nav>
          <div className="flex items-center gap-space-sm flex-wrap">
            <h1 className="font-display-lg text-display-lg text-on-surface tracking-tight">
              {data.examName} — Official Result
            </h1>
            <span className="px-2.5 py-0.5 rounded-full bg-emerald-50 text-emerald-700 border border-emerald-200 text-xs font-semibold inline-flex items-center gap-1">
              <CheckCircle2 className="w-3.5 h-3.5" />
              <span>{data.status.toUpperCase()}</span>
            </span>
          </div>
        </div>

        <div className="flex items-center gap-2 self-start md:self-auto">
          <Link 
            href={`/student/appeals/new?submissionId=${submissionId}`} 
            className="px-4 py-2 bg-purple-50 hover:bg-purple-100 text-purple-800 border border-purple-200 rounded-lg text-xs font-semibold transition-all flex items-center gap-1.5 shadow-xs"
          >
            <Scale className="w-3.5 h-3.5 text-purple-600" />
            <span>Submit Appeal Ticket</span>
          </Link>
        </div>
      </div>

      {/* Main Score Banner Card */}
      {(() => {
        const effectiveScore = data.effectiveScore ?? (data.overrideTotalScore != null ? data.overrideTotalScore : data.totalScore);
        const maxScore = data.maxTotalScore ?? (data.criteria?.length ? data.criteria.reduce((sum, c) => sum + (c.maxScore || 0), 0) : 100);
        const isOverridden = data.overrideTotalScore != null;

        return (
          <div className="rounded-2xl bg-surface-container-lowest border border-outline-variant/40 p-space-lg shadow-xs flex flex-col md:flex-row md:items-center justify-between gap-space-md">
            <div className="flex flex-col gap-2">
              <div className="flex items-center gap-2 flex-wrap">
                <Award className="w-5 h-5 text-primary" />
                <span className="font-label-xs uppercase tracking-wider text-outline font-semibold">
                  Consolidated Assessment Total
                </span>
                {isOverridden && (
                  <span className="text-xs px-2.5 py-0.5 rounded-full font-semibold bg-amber-50 text-amber-700 border border-amber-200">
                    Adjusted by Instructor (Rubric: {data.totalScore} pts)
                  </span>
                )}
              </div>
              <div className="flex items-baseline gap-2">
                <span className="font-display-lg text-[64px] leading-none font-bold text-primary tracking-tight">
                  {effectiveScore}
                </span>
                <span className="font-headline-lg text-outline font-normal text-2xl">
                  / {maxScore} pts
                </span>
              </div>
            </div>

            <div className="flex flex-col gap-1 text-xs text-on-surface-variant md:text-right">
              <div className="flex md:justify-end items-center gap-1.5">
                <Calendar className="w-3.5 h-3.5 text-outline" />
                <span>Evaluated: {data.evaluatedAt ? new Date(data.evaluatedAt).toLocaleString() : 'Recent'}</span>
              </div>
              <span className="text-[11px] text-outline">Certified by Faculty Evaluation Board</span>
            </div>
          </div>
        );
      })()}

      {/* Criterion Breakdown */}
      <div className="flex flex-col gap-space-sm">
        <h2 className="font-headline-sm text-headline-sm text-on-surface tracking-tight">
          Criterion-Based Rubric Breakdown
        </h2>

        <div className="space-y-2">
          {data.criteria.map((criterion) => (
            <div 
              key={criterion.criterionId} 
              className="bg-surface-container-lowest rounded-xl border border-outline-variant/40 p-space-md shadow-xs flex items-center justify-between gap-space-md hover:border-primary/30 transition-all"
            >
              <div className="flex flex-col">
                <span className="font-label-md font-semibold text-on-surface text-sm">{criterion.name}</span>
                <span className="text-xs text-on-surface-variant mt-0.5">{criterion.description}</span>
              </div>
              <div className="font-mono text-sm font-bold text-emerald-700 bg-emerald-50 px-3 py-1 rounded-lg border border-emerald-200 shrink-0">
                {criterion.score} / {criterion.maxScore} pts
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* Student AI Learning Assistant */}
      <StudentAiAssistantCard submissionId={submissionId} />
    </div>
  );
}
