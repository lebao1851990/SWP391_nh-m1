'use client';
import React, { useState } from 'react';
import { AppLink as Link } from '@/components/ui/app-link';
import { useSearchParams } from 'next/navigation';
import { useAppRouter as useRouter } from '@/hooks/use-app-router';
import { AxiosError } from 'axios';
import { useCreateAppeal, useStudentAppeals } from '../hooks';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { createAppealSchema, AppealFormData } from '../schemas';

export function StudentAppealCreateView() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const submissionId = searchParams?.get('submissionId') || '';

  const { data: appeals, isLoading: isLoadingAppeals } = useStudentAppeals();
  const createAppeal = useCreateAppeal();

  const existingPendingAppeal = appeals?.find(a => a.submissionId === submissionId && a.status === 'PENDING');

  const [showConfirm, setShowConfirm] = useState(false);
  const [formData, setFormData] = useState<AppealFormData | null>(null);

  const { register, handleSubmit, formState: { errors }, watch } = useForm<AppealFormData>({
    resolver: zodResolver(createAppealSchema),
    defaultValues: {
      submissionId: submissionId,
      basis: 'ast_clarification',
      explanation: '',
    }
  });

  const explanationLength = watch('explanation')?.length || 0;

  const onValid = (data: AppealFormData) => {
    setFormData(data);
    setShowConfirm(true);
  };

  const confirmSubmit = () => {
    if (!formData) return;
    createAppeal.mutate(formData, {
      onSuccess: () => {
        router.push('/student/appeals');
      },
      onError: () => {
        setShowConfirm(false);
      }
    });
  };

  if (!submissionId) {
    return (
      <div className="p-space-lg max-w-7xl mx-auto mt-16 text-center">
        <span className="material-symbols-outlined text-[48px] text-error mb-2">warning</span>
        <h2 className="font-headline-md">Missing Submission Reference</h2>
        <p className="mt-2 text-on-surface-variant font-body-md">Cannot create an appeal without a specific submission context.</p>
        <Link href="/student/dashboard">
          <button className="mt-4 px-4 py-2 bg-primary text-on-primary rounded font-label-md">Return to Dashboard</button>
        </Link>
      </div>
    );
  }



  return (
    <>
      <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-space-md pb-space-lg mb-space-lg border-b border-outline-variant/30">
        <div className="space-y-1">
          <div className="flex items-center gap-space-sm">
            <h1 className="font-headline-lg text-headline-lg text-on-surface tracking-tight">Submit Grade &amp; Similarity Appeal</h1>
            <span className="font-label-sm text-label-sm px-2 py-0.5 rounded bg-error-container text-on-error-container font-semibold">Integrity Review Active</span>
          </div>
          <p className="font-body-md text-body-md text-on-surface-variant">Provide formal academic clarification, architectural notes, or lecture references for your submission evaluation.</p>
        </div>
        <div className="flex items-center gap-space-sm self-start lg:self-auto">
          <span className="font-label-sm text-label-sm text-outline uppercase tracking-wider">Board Case Reference</span>
          <span className="font-label-sm text-label-sm px-2.5 py-1 rounded bg-surface-container text-on-surface font-semibold">APL-{submissionId}</span>
        </div>
      </div>
      
      {createAppeal.isError && createAppeal.error instanceof AxiosError && createAppeal.error.response?.status === 409 && (
        <div className="mb-space-lg bg-error-container/40 p-space-md rounded-xl border border-error/20 flex flex-col items-center justify-center">
           <span className="font-headline-sm text-error">Duplicate Pending Appeal</span>
           <span className="font-body-md text-on-surface-variant mt-1">An appeal is already pending for this submission. The backend rejected this submission.</span>
        </div>
      )}

      {createAppeal.isError && createAppeal.error instanceof AxiosError && createAppeal.error.response?.status !== 409 && (
        <div className="mb-space-lg bg-error-container/40 p-space-md rounded-xl border border-error/20 flex flex-col items-center justify-center">
           <span className="font-headline-sm text-error">Submission Failed</span>
           <span className="font-body-md text-on-surface-variant mt-1">An unexpected error occurred. Please try again.</span>
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-space-xl items-start">
        <aside className="lg:col-span-4 space-y-space-md">
          <div className="bg-surface-container-lowest rounded-xl p-space-md shadow-sm border border-outline-variant/20 relative overflow-hidden">
            <div className="absolute top-0 left-0 right-0 h-1 bg-error"></div>
            <div className="flex items-center justify-between pb-space-sm border-b border-outline-variant/30 mb-space-md">
              <span className="font-label-sm text-label-sm text-outline font-semibold uppercase tracking-wider">Evaluation Context</span>
              <span className="font-label-sm text-label-sm text-outline">ID #{submissionId}</span>
            </div>
            <div className="space-y-space-md">
              <div>
                <div className="font-label-sm text-label-sm text-outline uppercase">Target Assessment</div>
                <div className="font-headline-sm text-headline-sm text-on-surface font-semibold mt-0.5">Submission</div>
              </div>
              <div className="bg-error-container/40 p-space-sm rounded border border-error/20">
                <span className="font-label-sm text-label-sm text-on-error-container font-semibold block">AST Similarity</span>
                <span className="font-label-sm text-label-sm text-on-error-container">Flagged for Review</span>
              </div>
            </div>
          </div>
          
          <div className="bg-surface-container-high/60 rounded-xl p-space-md border border-outline-variant/30 text-on-surface">
            <div className="flex items-start gap-space-sm">
              <span className="material-symbols-outlined text-primary text-[20px] shrink-0 mt-0.5">verified_user</span>
              <div className="space-y-1">
                <span className="font-headline-sm text-headline-sm block text-on-surface leading-tight">Institutional Policy Notice</span>
                <p className="font-body-sm text-body-sm text-on-surface-variant leading-relaxed">
                  Submitting an appeal forwards your statement directly to the Faculty Examination Board. You may submit only one appeal per submission while active. Falsification of reference citations is subject to Code of Academic Integrity §4.2.
                </p>
              </div>
            </div>
          </div>
        </aside>

        <main className="lg:col-span-8">
          {existingPendingAppeal ? (
            <div className="bg-surface-container-lowest rounded-xl shadow-sm border border-outline-variant/20 p-space-lg flex flex-col items-center justify-center text-center">
              <span className="material-symbols-outlined text-[48px] text-primary mb-4">pending_actions</span>
              <h2 className="font-headline-md text-headline-md text-on-surface">Appeal Currently Under Review</h2>
              <p className="mt-2 text-on-surface-variant font-body-md max-w-md">
                You already have a pending appeal for this submission. The Faculty Examination Board is currently reviewing your case. You will be notified once a decision has been made.
              </p>
              <Link href="/student/appeals">
                <button className="mt-6 px-6 py-2 bg-primary-container text-on-primary font-label-md rounded shadow-sm hover:bg-primary transition-colors">
                  View My Appeals
                </button>
              </Link>
            </div>
          ) : (
          <form className="bg-surface-container-lowest rounded-xl shadow-sm border border-outline-variant/20 p-space-lg space-y-space-xl" onSubmit={handleSubmit(onValid)}>
            <div className="space-y-space-sm">
              <div className="flex items-center justify-between">
                <label className="font-headline-sm text-headline-sm text-on-surface block">
                  1. Appeal Classification <span className="text-error">*</span>
                </label>
              </div>
              <div className="grid grid-cols-1 md:grid-cols-3 gap-space-sm pt-1">
                <label className="relative flex flex-col p-space-sm rounded-lg bg-surface-container border border-outline-variant/50 hover:border-primary cursor-pointer transition-all">
                  <input {...register('basis')} className="sr-only peer" type="radio" value="ast_clarification"/>
                  <div className="flex items-center justify-between mb-1">
                    <span className="material-symbols-outlined text-primary text-[20px]">account_tree</span>
                    <span className="h-4 w-4 rounded-full border border-outline peer-checked:border-primary peer-checked:bg-primary"></span>
                  </div>
                  <span className="font-body-md text-body-md text-on-surface font-semibold">Code Similarity Clarification</span>
                </label>
                <label className="relative flex flex-col p-space-sm rounded-lg bg-surface-container border border-outline-variant/50 hover:border-primary cursor-pointer transition-all">
                  <input {...register('basis')} className="sr-only peer" type="radio" value="grading_dispute"/>
                  <div className="flex items-center justify-between mb-1">
                    <span className="material-symbols-outlined text-outline text-[20px]">terminal</span>
                    <span className="h-4 w-4 rounded-full border border-outline peer-checked:border-primary peer-checked:bg-primary"></span>
                  </div>
                  <span className="font-body-md text-body-md text-on-surface font-semibold">Automated Grading</span>
                </label>
                <label className="relative flex flex-col p-space-sm rounded-lg bg-surface-container border border-outline-variant/50 hover:border-primary cursor-pointer transition-all">
                  <input {...register('basis')} className="sr-only peer" type="radio" value="rubric_clarification"/>
                  <div className="flex items-center justify-between mb-1">
                    <span className="material-symbols-outlined text-outline text-[20px]">fact_check</span>
                    <span className="h-4 w-4 rounded-full border border-outline peer-checked:border-primary peer-checked:bg-primary"></span>
                  </div>
                  <span className="font-body-md text-body-md text-on-surface font-semibold">Rubric Clarification</span>
                </label>
              </div>
              {errors.basis && <span className="text-error text-body-sm">{errors.basis.message}</span>}
            </div>

            <div className="space-y-space-sm pt-space-md border-t border-outline-variant/20">
              <div className="flex items-center justify-between">
                <label className="font-headline-sm text-headline-sm text-on-surface block">
                  2. Explanation of Appeal <span className="text-error">*</span>
                </label>
              </div>
              <p className="font-body-sm text-body-sm text-on-surface-variant">Specify file names, lines of code, and specific lecture references to substantiate legitimate derivation.</p>
              <div className="relative mt-2">
                <textarea 
                  {...register('explanation')}
                  className="w-full bg-surface-container-low rounded-lg p-space-sm font-code-body text-code-body text-on-surface border border-outline-variant focus:border-primary focus:ring-1 focus:ring-primary transition-all resize-y leading-relaxed" 
                  placeholder="Detail your algorithmic structure and cite approved course resources..." 
                  rows={6}
                ></textarea>
              </div>
              <div className="flex items-center justify-between pt-1">
                <span className="font-label-sm text-label-sm text-outline font-semibold">{explanationLength} characters</span>
                {errors.explanation && <span className="text-error text-body-sm">{errors.explanation.message}</span>}
              </div>
            </div>

            <div className="pt-space-md border-t border-outline-variant/30 flex items-center justify-end gap-space-md">
              <Link href="/student/appeals">
                <button type="button" className="px-space-md py-2.5 rounded text-on-surface-variant hover:text-on-surface font-body-md text-body-md font-medium hover:bg-surface-container transition-colors">
                  Cancel
                </button>
              </Link>
              <button disabled={createAppeal.isPending} className="px-space-lg py-2.5 rounded bg-primary-container text-on-primary font-body-md text-body-md font-semibold hover:bg-primary transition-colors flex items-center gap-2 shadow-sm focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-2 disabled:opacity-50" type="submit">
                <span className="material-symbols-outlined text-[18px]">send</span>
                {createAppeal.isPending ? 'Submitting...' : 'Submit Appeal'}
              </button>
            </div>
          </form>
          )}
        </main>
      </div>

      {showConfirm && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-space-md bg-inverse-surface/60 backdrop-blur-sm">
          <div className="bg-surface-container-lowest rounded-xl max-w-lg w-full shadow-2xl border border-outline-variant/30 overflow-hidden transform transition-all">
            <div className="p-space-md pb-space-sm border-b border-outline-variant/20 flex items-start gap-space-sm bg-surface-container-low">
              <div className="h-10 w-10 rounded-full bg-primary-fixed text-primary flex items-center justify-center shrink-0">
                <span className="material-symbols-outlined text-[22px]">gavel</span>
              </div>
              <div className="flex-1 min-w-0">
                <h2 className="font-headline-md text-headline-md text-on-surface tracking-tight">Submit this appeal?</h2>
                <span className="font-label-sm text-label-sm text-outline">Formal Academic Governance Proceeding</span>
              </div>
              <button onClick={() => setShowConfirm(false)} className="text-outline hover:text-on-surface p-1 rounded hover:bg-surface-container transition-colors" type="button">
                <span className="material-symbols-outlined text-[20px]">close</span>
              </button>
            </div>
            
            <div className="p-space-lg space-y-space-md">
              <p className="font-body-md text-body-md text-on-surface-variant leading-relaxed">
                You will not be able to submit another appeal for this submission while this appeal is under review by the Faculty Examination Board.
              </p>
            </div>
            
            <div className="p-space-md border-t border-outline-variant/20 bg-surface-container-low/50 flex items-center justify-end gap-space-sm">
              <button disabled={createAppeal.isPending} onClick={() => setShowConfirm(false)} className="px-space-md py-2 rounded text-on-surface-variant hover:text-on-surface font-body-md text-body-md font-medium hover:bg-surface-container transition-colors" type="button">
                Cancel
              </button>
              <button disabled={createAppeal.isPending} onClick={confirmSubmit} className="px-space-lg py-2 rounded bg-primary-container text-on-primary font-body-md text-body-md font-semibold hover:bg-primary transition-colors flex items-center gap-1.5 shadow-sm" type="button">
                <span className="material-symbols-outlined text-[18px]">check</span>
                {createAppeal.isPending ? 'Submitting...' : 'Confirm & Submit'}
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
