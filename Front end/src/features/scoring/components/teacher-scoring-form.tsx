'use client';

import React, { useState } from 'react';
import { useForm as useHookForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { ScoringDetail } from '../types';
import { SaveScoringPayloadSchema, SaveScoringPayloadType } from '../schemas';
import { useUpdateTeacherScoring } from '../hooks';

interface TeacherScoringFormProps {
  submissionId: string;
  initialData: ScoringDetail;
}

export function TeacherScoringForm({ submissionId, initialData }: TeacherScoringFormProps) {
  const [showOverride, setShowOverride] = useState(false);
  const updateMutation = useUpdateTeacherScoring(submissionId);

  const {
    register,
    handleSubmit,
    formState: { isDirty, errors },
  } = useHookForm<SaveScoringPayloadType>({
    resolver: zodResolver(SaveScoringPayloadSchema),
    defaultValues: {
      criteriaScores: initialData.criteria.map(c => ({
        criterionId: c.criterionId,
        score: c.score,
      })),
      instructorEvaluationComments: initialData.instructorEvaluationComments || '',
      overrideTotalScore: initialData.totalScore,
    },
  });

  const onSubmit = (data: SaveScoringPayloadType) => {
    updateMutation.mutate(data);
  };

  const isSaving = updateMutation.isPending;

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="max-w-7xl mx-auto px-space-lg py-space-lg flex flex-col w-full space-y-space-lg">
      <div className="flex flex-col gap-space-sm">
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-space-md">
          <div>
            <div className="flex items-center gap-space-sm">
              <h1 className="font-display-lg text-display-lg text-on-surface tracking-tight">
                Scoring Detail — Submission #{initialData.submissionId}
              </h1>
              <span className="font-label-sm text-label-sm px-2 py-0.5 rounded bg-tertiary-fixed text-on-tertiary-fixed font-semibold">
                {initialData.status.toUpperCase()}
              </span>
            </div>
            <p className="font-body-md text-body-md text-on-surface-variant mt-1">
              Evaluation breakdown, test case execution, and rubric scoring for <span className="text-on-surface font-semibold">{initialData.studentName}</span> (ID: <span className="font-label-sm">{initialData.studentId}</span>).
            </p>
          </div>
        </div>
      </div>

      {updateMutation.isError && (
        <div className="p-4 rounded bg-error-container text-on-error-container font-body-sm">
          Failed to save scoring: {(updateMutation.error as Error).message}
        </div>
      )}

      {updateMutation.isSuccess && (
        <div className="p-4 rounded bg-primary-container text-on-primary-container font-body-sm">
          Scoring saved successfully! The backend official score is now updated.
        </div>
      )}

      <div className="grid grid-cols-1 xl:grid-cols-12 gap-space-lg items-start">
        <div className="xl:col-span-8 flex flex-col space-y-space-md">
          {initialData.criteria.map((criterion, index) => (
            <div key={criterion.criterionId} className="rounded bg-surface-container-lowest overflow-hidden shadow-sm">
              <div className="p-space-md flex items-center justify-between bg-surface-container-lowest border-b border-surface-container">
                <div className="flex flex-col">
                  <div className="flex items-center gap-2">
                    <h3 className="font-headline-sm text-headline-sm text-on-surface font-semibold">{criterion.name}</h3>
                  </div>
                  <p className="font-body-sm text-body-sm text-on-surface-variant">{criterion.description}</p>
                </div>
                <div className="flex items-center gap-2">
                  <input 
                    type="number"
                    step="0.1"
                    {...register(`criteriaScores.${index}.score`, { valueAsNumber: true })}
                    className="w-20 px-2 py-1 rounded border border-outline focus:border-primary focus:outline-none bg-surface-container-low text-on-surface font-mono"
                    disabled={isSaving}
                  />
                  <span className="text-on-surface-variant font-label-sm">/ {criterion.maxScore}</span>
                </div>
              </div>
              {errors.criteriaScores?.[index]?.score && (
                <div className="p-2 text-error text-label-sm font-label-sm bg-error-container/20">
                  {errors.criteriaScores[index]?.score?.message}
                </div>
              )}
            </div>
          ))}

          <div className="rounded bg-surface-container-lowest p-space-md shadow-sm space-y-space-sm">
            <h4 className="font-label-md text-label-md font-semibold text-on-surface">Instructor Evaluation Comments</h4>
            <textarea 
              {...register('instructorEvaluationComments')}
              className="w-full p-2.5 rounded bg-surface-container-low font-body-sm text-body-sm text-on-surface border border-outline-variant focus:outline-none focus:border-primary transition-colors" 
              placeholder="Add specific guidance or grade rationale for the student..." 
              rows={4}
              disabled={isSaving}
            />
          </div>
        </div>

        <div className="xl:col-span-4 flex flex-col space-y-space-md">
          <div className="rounded bg-surface-container-lowest p-space-md shadow-sm space-y-space-sm">
            <h4 className="font-label-md text-label-md font-semibold text-on-surface">Student Info</h4>
            <div className="font-body-sm text-on-surface-variant">
              <div>Name: {initialData.studentName}</div>
              <div>ID: {initialData.studentId}</div>
              <div>Class: {initialData.className}</div>
            </div>
          </div>
          
          <div className="rounded bg-surface-container-lowest p-space-md shadow-sm space-y-space-sm">
            <h4 className="font-label-md text-label-md font-semibold text-on-surface">Official Score</h4>
            <div className="font-display-lg text-[64px] leading-none font-bold text-on-surface tracking-tight">
              {initialData.totalScore} <span className="text-headline-lg text-outline font-normal">/ {initialData.maxTotalScore}</span>
            </div>
            <div className="text-on-surface-variant font-body-sm">
              * Score is calculated by backend authority. Save changes to update.
            </div>
          </div>
        </div>
      </div>

      <div className="sticky bottom-4 z-30 p-space-md rounded bg-surface-container-lowest/95 backdrop-blur-md shadow-xl flex items-center justify-end gap-space-md">
        <button 
          type="button" 
          onClick={() => setShowOverride(true)}
          className="px-4 py-2 rounded bg-surface-container hover:bg-surface-container-high text-on-surface font-label-md text-label-md flex items-center gap-1.5 transition-colors"
          disabled={isSaving}
        >
          <span className="material-symbols-outlined text-[18px] text-secondary">tune</span>
          Override Official Score
        </button>
        <button 
          type="submit" 
          disabled={!isDirty || isSaving}
          className={`px-5 py-2 rounded font-label-md text-label-md font-semibold flex items-center gap-1.5 transition-colors shadow-sm ${
            !isDirty || isSaving ? 'bg-surface-container text-outline' : 'bg-primary text-on-primary hover:bg-primary-container'
          }`}
        >
          <span className="material-symbols-outlined text-[18px]">save</span>
          {isSaving ? 'Saving...' : 'Save Rubric Changes'}
        </button>
      </div>

      {/* Simplified Override Modal for Integration */}
      {showOverride && (
        <div className="fixed inset-0 bg-inverse-surface/40 backdrop-blur-sm z-50 flex items-center justify-center p-space-md">
          <div className="bg-surface-container-lowest rounded-xl max-w-lg w-full p-space-xl shadow-2xl space-y-space-md">
            <div className="flex items-center justify-between">
              <h3 className="font-headline-sm text-headline-sm text-on-surface">Manual Score Override</h3>
              <button type="button" onClick={() => setShowOverride(false)} className="text-outline hover:text-on-surface">
                <span className="material-symbols-outlined">close</span>
              </button>
            </div>
            <p className="font-body-sm text-on-surface-variant">
              Overriding requires backend confirmation. This field may be ignored if the backend contract does not support it.
            </p>
            <div>
              <label className="block font-label-sm text-outline mb-1">New Total Score</label>
              <input 
                type="number" step="0.1" 
                {...register('overrideTotalScore', { valueAsNumber: true })} 
                className="w-full px-3 py-2 rounded bg-surface-container-low font-headline-sm text-on-surface focus:outline-none" 
              />
            </div>
            <div>
              <label className="block font-label-sm text-outline mb-1">Rationale</label>
              <textarea 
                {...register('overrideRationale')} 
                className="w-full px-3 py-2 rounded bg-surface-container-low font-body-sm text-on-surface focus:outline-none" 
                rows={3} 
              />
            </div>
            <div className="flex justify-end gap-2">
              <button type="button" onClick={() => setShowOverride(false)} className="px-4 py-2 rounded bg-surface-container text-on-surface">Done</button>
            </div>
          </div>
        </div>
      )}
    </form>
  );
}
