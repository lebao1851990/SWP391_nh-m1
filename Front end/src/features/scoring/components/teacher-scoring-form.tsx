'use client';

import React, { useState, useMemo } from 'react';
import { useForm as useHookForm, useWatch } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { ScoringDetail } from '../types';
import { SaveScoringPayloadSchema, SaveScoringPayloadType } from '../schemas';
import { useUpdateTeacherScoring } from '../hooks';
import { 
  Save, 
  Sliders, 
  X, 
  Award, 
  User, 
  CheckCircle2, 
  AlertCircle, 
  Loader2
} from 'lucide-react';
import { toast } from 'sonner';
import { GradeAuditHistory } from './grade-audit-history';
import { useTranslation } from '@/i18n';

interface TeacherScoringFormProps {
  submissionId: string;
  initialData: ScoringDetail;
}

export function TeacherScoringForm({ submissionId, initialData }: TeacherScoringFormProps) {
  const { t } = useTranslation();
  const [showOverride, setShowOverride] = useState(false);
  const updateMutation = useUpdateTeacherScoring(submissionId);
  const maxTotalScore = initialData.maxTotalScore ?? (initialData.criteria?.length ? initialData.criteria.reduce((s, c) => s + (c.maxScore || 0), 0) : 100);

  const {
    register,
    handleSubmit,
    control,
    setValue,
    formState: { errors },
  } = useHookForm<SaveScoringPayloadType>({
    resolver: zodResolver(SaveScoringPayloadSchema),
    defaultValues: {
      criteriaScores: initialData.criteria.map(c => ({
        criterionId: c.criterionId,
        score: c.score,
      })),
      instructorEvaluationComments: initialData.instructorEvaluationComments || '',
      overrideTotalScore: initialData.overrideTotalScore != null ? initialData.overrideTotalScore : undefined,
    },
  });

  // Watch criteriaScores to calculate live running total
  const watchedCriteria = useWatch({
    control,
    name: 'criteriaScores',
  });

  const watchedOverride = useWatch({
    control,
    name: 'overrideTotalScore',
  });

  const hasActiveOverride = watchedOverride !== undefined && watchedOverride !== null && !isNaN(Number(watchedOverride));

  const liveCalculatedTotal = useMemo(() => {
    if (!watchedCriteria || !Array.isArray(watchedCriteria)) return initialData.totalScore;
    return watchedCriteria.reduce((sum, curr) => {
      const val = Number(curr?.score);
      return sum + (isNaN(val) ? 0 : val);
    }, 0);
  }, [watchedCriteria, initialData.totalScore]);

  const onSubmit = (data: SaveScoringPayloadType) => {
    updateMutation.mutate(data, {
      onSuccess: () => {
        toast.success(t.scoring.evaluationScoreSaved);
      },
      onError: (err: unknown) => {
        toast.error((err as Error).message || t.common.error);
      }
    });
  };

  const isSaving = updateMutation.isPending;

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="max-w-7xl mx-auto px-space-lg py-space-lg flex flex-col w-full space-y-space-lg">
      {/* Header Info */}
      <div className="flex flex-col gap-space-sm border-b border-outline-variant/30 pb-space-md">
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-space-md">
          <div>
            <div className="flex items-center gap-space-sm flex-wrap">
              <h1 className="font-display-lg text-display-lg text-on-surface tracking-tight">
                {t.scoring.scoringRubricTitle || 'Evaluation Rubric'} — #{initialData.submissionId.slice(0, 8)}
              </h1>
              <span className="text-xs px-2.5 py-0.5 rounded-full font-semibold bg-emerald-50 dark:bg-emerald-950/60 text-emerald-700 dark:text-emerald-300 border border-emerald-200 dark:border-emerald-800">
                {initialData.status.toUpperCase()}
              </span>
            </div>
            <p className="font-body-md text-body-md text-on-surface-variant mt-1 text-xs">
              {t.scoring.scoringTitle} ({t.submissions.studentName}:{' '}
              <strong className="text-on-surface">{initialData.studentName}</strong>, {t.submissions.studentId}:{' '}
              <span className="font-mono">{initialData.studentId}</span>).
            </p>
          </div>
        </div>
      </div>

      {updateMutation.isError && (
        <div className="p-3.5 rounded-xl bg-rose-50 dark:bg-rose-950/60 border border-rose-200 dark:border-rose-800 text-rose-700 dark:text-rose-300 text-xs flex items-center gap-2">
          <AlertCircle className="w-4 h-4 shrink-0" />
          <span>{(updateMutation.error as Error).message}</span>
        </div>
      )}

      {updateMutation.isSuccess && (
        <div className="p-3.5 rounded-xl bg-emerald-50 dark:bg-emerald-950/60 border border-emerald-200 dark:border-emerald-800 text-emerald-800 dark:text-emerald-300 text-xs flex items-center gap-2">
          <CheckCircle2 className="w-4 h-4 shrink-0 text-emerald-600 dark:text-emerald-400" />
          <span>{t.scoring.evaluationScoreSaved}</span>
        </div>
      )}

      <div className="grid grid-cols-1 xl:grid-cols-12 gap-space-lg items-start">
        {/* Left: Criteria List & Instructor Comments */}
        <div className="xl:col-span-8 flex flex-col space-y-space-md">
          <div className="flex items-center justify-between">
            <h2 className="font-headline-sm text-headline-sm text-on-surface">{t.scoring.rubricCriteria}</h2>
            <span className="text-xs text-outline font-medium">{t.scoring.scoreExplanation}</span>
          </div>

          {initialData.criteria.map((criterion, index) => (
            <div key={criterion.criterionId} className="rounded-xl bg-surface-container-lowest border border-outline-variant/40 shadow-xs overflow-hidden">
              <div className="p-space-md flex flex-col sm:flex-row sm:items-center justify-between gap-3 bg-surface-container-lowest border-b border-outline-variant/20">
                <div className="flex flex-col">
                  <h3 className="font-label-md font-semibold text-on-surface text-sm">{criterion.name}</h3>
                  <p className="text-xs text-on-surface-variant mt-0.5">{criterion.description}</p>
                </div>
                <div className="flex items-center gap-2 shrink-0">
                  <input 
                    type="number"
                    step="0.1"
                    min={0}
                    max={criterion.maxScore}
                    {...register(`criteriaScores.${index}.score`, { valueAsNumber: true })}
                    className="w-20 px-2.5 py-1.5 rounded-lg border border-outline-variant/60 focus:border-primary focus:outline-none bg-surface-container-low text-on-surface font-mono text-xs font-bold text-center"
                    disabled={isSaving}
                  />
                  <span className="text-on-surface-variant text-xs font-medium">/ {criterion.maxScore} {t.scoring.points}</span>
                </div>
              </div>
              {errors.criteriaScores?.[index]?.score && (
                <div className="p-2 text-rose-600 dark:text-rose-400 text-xs font-medium bg-rose-50 dark:bg-rose-950/60 border-t border-rose-100 dark:border-rose-900">
                  {errors.criteriaScores[index]?.score?.message}
                </div>
              )}
            </div>
          ))}

          {/* Instructor Evaluation Feedback */}
          <div className="rounded-xl bg-surface-container-lowest border border-outline-variant/40 p-space-md shadow-xs space-y-2">
            <h4 className="font-label-md font-semibold text-on-surface text-xs uppercase tracking-wider">
              {t.scoring.instructorFeedbackLabel}
            </h4>
            <textarea 
              {...register('instructorEvaluationComments')}
              className="w-full p-3 rounded-lg bg-surface-container-low font-body-sm text-xs text-on-surface border border-outline-variant/40 focus:outline-none focus:border-primary transition-colors" 
              placeholder={t.scoring.instructorFeedbackPlaceholder} 
              rows={4}
              disabled={isSaving}
            />
          </div>
        </div>

        {/* Right Sidebar: Student Info & Official Score Display */}
        <div className="xl:col-span-4 flex flex-col space-y-space-md">
          {/* Student Info Card */}
          <div className="rounded-xl bg-surface-container-lowest border border-outline-variant/40 p-space-md shadow-xs space-y-2 text-xs">
            <div className="flex items-center gap-1.5 pb-2 border-b border-outline-variant/20">
              <User className="w-4 h-4 text-primary" />
              <h4 className="font-semibold text-on-surface">{t.scoring.studentProfile}</h4>
            </div>
            <div className="space-y-1.5 text-on-surface-variant">
              <div className="flex justify-between">
                <span className="text-outline">{t.submissions.studentName}:</span>
                <span className="font-semibold text-on-surface">{initialData.studentName}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-outline">{t.submissions.studentId}:</span>
                <span className="font-mono text-on-surface">{initialData.studentId}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-outline">{t.exams.cohort}:</span>
                <span className="font-mono text-on-surface">{initialData.className}</span>
              </div>
            </div>
          </div>
          
          {/* Dynamic Score Card */}
          <div className="rounded-xl bg-surface-container-lowest border border-outline-variant/40 p-space-md shadow-xs space-y-2">
            <div className="flex items-center gap-1.5 pb-2 border-b border-outline-variant/20">
              <Award className="w-4 h-4 text-emerald-600 dark:text-emerald-400" />
              <h4 className="font-semibold text-on-surface text-xs">
                {hasActiveOverride ? t.scoring.effectiveScoreOverride : t.scoring.calculatedScore}
              </h4>
            </div>
            <div className="font-display-lg text-[52px] leading-none font-bold text-primary tracking-tight">
              {hasActiveOverride 
                ? Number(watchedOverride).toFixed(1).replace(/\.0$/, '') 
                : Number(liveCalculatedTotal).toFixed(1).replace(/\.0$/, '')}
              <span className="text-lg text-outline font-normal ml-1">/ {maxTotalScore}</span>
            </div>
            {hasActiveOverride ? (
              <div className="text-[11px] text-amber-800 dark:text-amber-300 bg-amber-50 dark:bg-amber-950/60 border border-amber-200 dark:border-amber-800 p-2 rounded-lg leading-relaxed">
                * {t.scoring.scoreOverrideTitle}: <strong>{watchedOverride} {t.scoring.points}</strong>
              </div>
            ) : (
              <p className="text-[11px] text-on-surface-variant leading-relaxed">
                * {t.scoring.liveTotalNotice}
              </p>
            )}
          </div>
        </div>
      </div>

      {/* Sticky Bottom Actions Bar */}
      <div className="sticky bottom-4 z-30 p-3.5 rounded-xl bg-surface-container-lowest/95 backdrop-blur-md border border-outline-variant/50 shadow-xl flex items-center justify-end gap-space-md">
        <button 
          type="button" 
          onClick={() => setShowOverride(true)}
          className={`px-4 py-2 rounded-lg text-xs font-medium flex items-center gap-1.5 transition-colors cursor-pointer border ${
            hasActiveOverride 
              ? 'bg-amber-50 dark:bg-amber-950/60 hover:bg-amber-100 dark:hover:bg-amber-900/60 text-amber-800 dark:text-amber-300 border-amber-300 dark:border-amber-800' 
              : 'bg-surface-container hover:bg-surface-container-high text-on-surface border-outline-variant/30'
          }`}
          disabled={isSaving}
        >
          <Sliders className="w-3.5 h-3.5 text-secondary" />
          <span>{t.scoring.manualScoreOverrideButton} {hasActiveOverride ? `(${watchedOverride} ${t.scoring.points})` : ''}</span>
        </button>

        <button 
          type="submit" 
          disabled={isSaving}
          className="px-5 py-2 rounded-lg text-xs font-semibold flex items-center gap-1.5 transition-all shadow-sm bg-primary text-on-primary hover:bg-primary/90 disabled:opacity-50 cursor-pointer"
        >
          {isSaving ? (
            <>
              <Loader2 className="w-3.5 h-3.5 animate-spin" />
              <span>{t.scoring.savingScore}</span>
            </>
          ) : (
            <>
              <Save className="w-3.5 h-3.5" />
              <span>{t.scoring.saveScoreButton}</span>
            </>
          )}
        </button>
      </div>

      {/* Grade Audit History */}
      <GradeAuditHistory submissionId={submissionId} className="mt-space-md" />

      {/* Manual Override Modal */}
      {showOverride && (
        <div className="fixed inset-0 bg-black/50 backdrop-blur-xs z-50 flex items-center justify-center p-4 animate-in fade-in duration-150">
          <div className="bg-surface-container-lowest rounded-2xl max-w-md w-full p-6 shadow-2xl border border-outline-variant/40 space-y-4 animate-in zoom-in-95 duration-150">
            <div className="flex items-center justify-between border-b border-outline-variant/30 pb-3">
              <div className="flex items-center gap-2">
                <Sliders className="w-5 h-5 text-secondary" />
                <h3 className="font-headline-sm text-headline-sm text-on-surface font-semibold">{t.scoring.scoreOverrideTitle}</h3>
              </div>
              <button 
                type="button" 
                onClick={() => setShowOverride(false)} 
                className="text-outline hover:text-on-surface p-1 rounded-lg cursor-pointer"
              >
                <X className="w-4 h-4" />
              </button>
            </div>
            <p className="text-xs text-on-surface-variant leading-relaxed">
              {t.scoring.scoreExplanation}
            </p>
            <div className="space-y-1">
              <label className="text-xs font-semibold text-on-surface">{t.scoring.finalScore}</label>
              <input 
                type="number" 
                step="0.1" 
                min={0}
                max={maxTotalScore}
                {...register('overrideTotalScore', { 
                  setValueAs: (v) => v === '' || v === null || isNaN(Number(v)) ? undefined : Number(v) 
                })} 
                className="w-full px-3 py-2 rounded-lg bg-surface-container-low text-xs text-on-surface font-bold border border-outline-variant/40 focus:outline-none focus:border-primary" 
              />
              {errors.overrideTotalScore && (
                <p className="text-xs text-rose-600 dark:text-rose-400 mt-1">{errors.overrideTotalScore.message}</p>
              )}
            </div>
            <div className="space-y-1">
              <label className="text-xs font-semibold text-on-surface">{t.scoring.overrideReasonLabel}</label>
              <textarea 
                {...register('overrideRationale')} 
                className="w-full p-3 rounded-lg bg-surface-container-low text-xs text-on-surface border border-outline-variant/40 focus:outline-none focus:border-primary" 
                rows={3} 
                placeholder={t.scoring.overrideReasonPlaceholder} 
              />
            </div>
            <div className="flex justify-between items-center gap-2 pt-2 border-t border-outline-variant/30">
              {hasActiveOverride ? (
                <button
                  type="button"
                  onClick={() => {
                    setValue('overrideTotalScore', undefined, { shouldDirty: true });
                    setShowOverride(false);
                    toast.info('Override cleared. Form will use calculated rubric total.');
                  }}
                  className="px-3 py-1.5 rounded-lg text-xs font-medium text-rose-600 dark:text-rose-400 hover:bg-rose-50 dark:hover:bg-rose-950/60 border border-rose-200 dark:border-rose-800 cursor-pointer"
                >
                  {t.scoring.clearOverride}
                </button>
              ) : <div />}
              <button 
                type="button" 
                onClick={() => setShowOverride(false)} 
                className="px-4 py-2 rounded-lg bg-primary text-on-primary text-xs font-medium cursor-pointer"
              >
                {t.scoring.applyToForm}
              </button>
            </div>
          </div>
        </div>
      )}
    </form>
  );
}
