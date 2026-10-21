'use client';

import React, { useState } from 'react';
import { AppLink as Link } from '@/components/ui/app-link';
import { useSearchParams } from 'next/navigation';
import { useAppRouter as useRouter } from '@/hooks/use-app-router';
import { useCreateAppeal, useStudentAppeals } from '../hooks';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { createAppealSchema, AppealFormData } from '../schemas';
import { 
  ArrowLeft, 
  Send, 
  CheckCircle2, 
  AlertCircle, 
  Scale, 
  FileText, 
  Check, 
  Loader2,
  ShieldCheck
} from 'lucide-react';
import { toast } from 'sonner';
import { useTranslation } from '@/i18n';

export function StudentAppealCreateView() {
  const { t } = useTranslation();
  const router = useRouter();
  const searchParams = useSearchParams();
  const submissionId = searchParams?.get('submissionId') || '';

  const { data: appeals } = useStudentAppeals();
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
        toast.success(t.appeals.appealCreatedSuccess);
        router.push('/student/appeals');
      },
      onError: (err: unknown) => {
        setShowConfirm(false);
        const msg = (err as { response?: { data?: { message?: string } }; message?: string })?.response?.data?.message || (err as { message?: string })?.message || 'Failed to submit appeal';
        toast.error(msg);
      }
    });
  };

  if (!submissionId) {
    return (
      <div className="p-space-lg max-w-xl mx-auto my-16 text-center bg-surface-container-lowest rounded-2xl border border-outline-variant/40 shadow-lg flex flex-col items-center gap-3">
        <div className="p-3 rounded-full bg-amber-50 text-amber-600 dark:bg-amber-950/40 dark:text-amber-300">
          <AlertCircle className="w-8 h-8" />
        </div>
        <h2 className="font-headline-md text-headline-md text-on-surface">{t.appeals.missingSubmissionContext}</h2>
        <p className="text-xs text-on-surface-variant">Cannot create an appeal without an associated submission record.</p>
        <Link href="/student/dashboard" className="mt-2 px-4 py-2 bg-primary text-on-primary rounded-lg text-xs font-medium hover:bg-primary/90 transition-colors">
          Return to Dashboard
        </Link>
      </div>
    );
  }

  return (
    <div className="max-w-7xl mx-auto px-space-lg py-space-lg">
      <div className="flex flex-col w-full space-y-space-lg">
        {/* Top Header */}
        <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-space-md pb-space-md border-b border-outline-variant/30">
          <div className="space-y-1">
            <nav className="flex items-center gap-2 text-on-surface-variant text-xs mb-1">
              <Link className="hover:text-primary transition-colors flex items-center gap-1 font-medium" href="/student/appeals">
                <ArrowLeft className="w-3.5 h-3.5" />
                <span>{t.appeals.appealsListTitle}</span>
              </Link>
              <span className="text-outline">/</span>
              <span className="text-primary font-semibold">{t.appeals.createAppealButton}</span>
            </nav>
            <div className="flex items-center gap-space-sm flex-wrap">
              <h1 className="font-headline-lg text-headline-lg text-on-surface tracking-tight">
                {t.appeals.createAppealButton}
              </h1>
              <span className="text-[10px] px-2.5 py-0.5 rounded-full bg-purple-50 text-purple-700 border border-purple-200 dark:bg-purple-950/40 dark:text-purple-300 dark:border-purple-800/40 font-bold uppercase">
                Formal Proceeding
              </span>
            </div>
            <p className="text-xs sm:text-sm text-on-surface-variant">
              Provide formal academic clarification, architectural rationale, or approved lecture citations for your submission.
            </p>
          </div>
          <div className="flex items-center gap-2 self-start lg:self-auto">
            <span className="text-xs text-outline font-medium">Target Context:</span>
            <span className="font-mono text-xs px-2.5 py-1 rounded-lg bg-surface-container text-on-surface font-semibold border border-outline-variant/30">
              #{submissionId.substring(0, 8)}
            </span>
          </div>
        </div>

        {/* Existing Pending Warning */}
        {existingPendingAppeal && (
          <div className="bg-amber-50 dark:bg-amber-950/40 p-space-md rounded-2xl border border-amber-200 dark:border-amber-800/40 text-amber-800 dark:text-amber-200 flex items-start gap-3">
            <AlertCircle className="w-5 h-5 text-amber-600 dark:text-amber-400 shrink-0 mt-0.5" />
            <div className="text-xs space-y-1">
              <p className="font-semibold text-amber-900 dark:text-amber-100">Active Appeal Already Pending</p>
              <p>You already have a pending appeal for this submission (#{existingPendingAppeal.id.substring(0, 8)}). Per institutional policy, only one active appeal may be processed at a time.</p>
              <Link href="/student/appeals" className="inline-block mt-1 font-semibold underline text-amber-900 dark:text-amber-100">
                View existing appeal ticket &rarr;
              </Link>
            </div>
          </div>
        )}

        <div className="grid grid-cols-1 lg:grid-cols-12 gap-space-lg items-start">
          {/* Sidebar Guidelines */}
          <aside className="lg:col-span-4 space-y-space-md">
            <div className="bg-surface-container-lowest rounded-2xl p-space-md shadow-xs border border-outline-variant/40 space-y-3 text-xs">
              <div className="flex items-center gap-1.5 pb-2 border-b border-outline-variant/20">
                <Scale className="w-4 h-4 text-primary" />
                <h3 className="font-semibold text-on-surface">Institutional Policy Notice</h3>
              </div>
              <p className="text-on-surface-variant leading-relaxed">
                Appeals are reviewed by the Department Examination Board. Falsification of reference citations or misrepresentation of code origin is subject to the Code of Academic Integrity.
              </p>
              <div className="p-2.5 rounded-lg bg-surface-container-low border border-outline-variant/20 space-y-1.5">
                <div className="flex items-center gap-1.5 text-primary font-semibold">
                  <ShieldCheck className="w-3.5 h-3.5" />
                  <span>SLA Guarantee</span>
                </div>
                <p className="text-[11px] text-on-surface-variant">
                  Formal decisions are rendered within 48 academic hours of ticket receipt.
                </p>
              </div>
            </div>
          </aside>

          {/* Form */}
          <main className="lg:col-span-8">
            <form 
              className="bg-surface-container-lowest rounded-2xl border border-outline-variant/40 shadow-xs p-space-lg space-y-space-lg" 
              onSubmit={handleSubmit(onValid)}
            >
              <div className="space-y-space-sm">
                <label className="text-xs font-semibold text-on-surface block uppercase tracking-wider">
                  1. {t.appeals.appealBasisLabel} *
                </label>
                <div className="grid grid-cols-1 md:grid-cols-3 gap-2">
                  <label className="flex flex-col p-3 rounded-xl bg-surface-container-low border border-outline-variant/40 hover:border-primary cursor-pointer transition-all">
                    <input {...register('basis')} className="sr-only peer" type="radio" value="ast_clarification"/>
                    <div className="flex items-center justify-between mb-1">
                      <Scale className="w-4 h-4 text-primary" />
                      <span className="w-3.5 h-3.5 rounded-full border border-outline peer-checked:border-primary peer-checked:bg-primary transition-all"></span>
                    </div>
                    <span className="text-xs font-semibold text-on-surface">{t.appeals.basisAstClarification}</span>
                    <span className="text-[10px] text-on-surface-variant mt-0.5">False-positive isomorphism</span>
                  </label>

                  <label className="flex flex-col p-3 rounded-xl bg-surface-container-low border border-outline-variant/40 hover:border-primary cursor-pointer transition-all">
                    <input {...register('basis')} className="sr-only peer" type="radio" value="grading_dispute"/>
                    <div className="flex items-center justify-between mb-1">
                      <FileText className="w-4 h-4 text-secondary" />
                      <span className="w-3.5 h-3.5 rounded-full border border-outline peer-checked:border-primary peer-checked:bg-primary transition-all"></span>
                    </div>
                    <span className="text-xs font-semibold text-on-surface">{t.appeals.basisTechnicalIssue}</span>
                    <span className="text-[10px] text-on-surface-variant mt-0.5">Test runner timeout or error</span>
                  </label>

                  <label className="flex flex-col p-3 rounded-xl bg-surface-container-low border border-outline-variant/40 hover:border-primary cursor-pointer transition-all">
                    <input {...register('basis')} className="sr-only peer" type="radio" value="rubric_clarification"/>
                    <div className="flex items-center justify-between mb-1">
                      <CheckCircle2 className="w-4 h-4 text-emerald-600" />
                      <span className="w-3.5 h-3.5 rounded-full border border-outline peer-checked:border-primary peer-checked:bg-primary transition-all"></span>
                    </div>
                    <span className="text-xs font-semibold text-on-surface">{t.appeals.basisGradingDiscrepancy}</span>
                    <span className="text-[10px] text-on-surface-variant mt-0.5">Criteria score miscalculation</span>
                  </label>
                </div>
                {errors.basis && <span className="text-rose-600 text-xs font-medium">{errors.basis.message}</span>}
              </div>

              <div className="space-y-2 pt-space-md border-t border-outline-variant/30">
                <div className="flex items-center justify-between">
                  <label className="text-xs font-semibold text-on-surface block uppercase tracking-wider">
                    2. {t.appeals.appealReasonLabel} *
                  </label>
                  <span className={`text-[11px] font-mono ${explanationLength < 20 ? 'text-rose-600' : 'text-outline'}`}>
                    {explanationLength} / 1000 chars (min 20)
                  </span>
                </div>
                <p className="text-xs text-on-surface-variant">
                  Detail your algorithmic logic, design decisions, and specify lecture slides or course-provided skeletons.
                </p>
                <textarea 
                  {...register('explanation')}
                  className="w-full bg-surface-container-low rounded-xl p-3 text-xs text-on-surface border border-outline-variant/40 focus:border-primary focus:outline-none transition-all resize-y leading-relaxed placeholder:text-outline" 
                  placeholder={t.appeals.appealReasonPlaceholder} 
                  rows={6}
                />
                {errors.explanation && <span className="text-rose-600 text-xs font-medium">{errors.explanation.message}</span>}
              </div>

              <div className="pt-space-md border-t border-outline-variant/30 flex items-center justify-end gap-3">
                <Link 
                  href="/student/appeals"
                  className="px-4 py-2 rounded-lg text-xs font-medium text-on-surface-variant hover:bg-surface-container transition-colors"
                >
                  {t.common.cancel}
                </Link>
                <button 
                  disabled={createAppeal.isPending || !!existingPendingAppeal} 
                  className="px-6 py-2 rounded-lg bg-primary text-on-primary text-xs font-semibold hover:bg-primary/90 transition-all flex items-center gap-1.5 shadow-sm disabled:opacity-50 cursor-pointer" 
                  type="submit"
                >
                  <Send className="w-3.5 h-3.5" />
                  <span>{t.appeals.submitAppealButton}</span>
                </button>
              </div>
            </form>
          </main>
        </div>
      </div>

      {/* Confirmation Modal */}
      {showConfirm && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 backdrop-blur-xs animate-in fade-in duration-150">
          <div className="bg-surface-container-lowest rounded-2xl max-w-md w-full shadow-2xl border border-outline-variant/40 p-6 flex flex-col gap-4 animate-in zoom-in-95 duration-150">
            <div className="flex items-center gap-3">
              <div className="p-2.5 rounded-full bg-blue-50 text-blue-600 dark:bg-blue-950/40 dark:text-blue-300">
                <Scale className="w-6 h-6" />
              </div>
              <div>
                <h3 className="font-headline-sm text-headline-sm font-semibold text-on-surface">{t.appeals.confirmSubmitAppealTitle}</h3>
                <span className="text-xs text-outline">Academic Governance Proceeding</span>
              </div>
            </div>

            <p className="text-xs text-on-surface-variant leading-relaxed">
              {t.appeals.confirmSubmitAppealDesc}
            </p>

            <div className="flex justify-end gap-2 pt-2 border-t border-outline-variant/30">
              <button 
                type="button" 
                onClick={() => setShowConfirm(false)}
                className="px-4 py-2 rounded-lg text-xs font-medium border border-outline-variant/40 hover:bg-surface-container transition-colors cursor-pointer"
              >
                {t.common.cancel}
              </button>
              <button 
                type="button" 
                onClick={confirmSubmit}
                disabled={createAppeal.isPending}
                className="px-5 py-2 rounded-lg bg-primary text-on-primary text-xs font-semibold hover:bg-primary/90 transition-colors flex items-center gap-2 cursor-pointer shadow-sm"
              >
                {createAppeal.isPending ? (
                  <>
                    <Loader2 className="w-3.5 h-3.5 animate-spin" />
                    <span>{t.appeals.submittingAppeal}</span>
                  </>
                ) : (
                  <>
                    <Check className="w-3.5 h-3.5" />
                    <span>{t.common.confirm}</span>
                  </>
                )}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
