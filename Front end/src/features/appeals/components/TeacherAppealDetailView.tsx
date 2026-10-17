'use client';

import React, { useState } from 'react';
import { AppLink as Link } from '@/components/ui/app-link';
import { useTeacherAppeal, useResolveAppeal } from '../hooks';
import { 
  ArrowLeft, 
  CheckCircle2, 
  XCircle, 
  AlertCircle, 
  Scale, 
  FileText, 
  Check, 
  X, 
  Loader2,
  Calendar,
  Lock,
  MessageSquare
} from 'lucide-react';
import { toast } from 'sonner';

export function TeacherAppealDetailView({ appealId }: { appealId: string }) {
  const { data: appeal, isLoading, isError, refetch } = useTeacherAppeal(appealId);
  const resolveMutation = useResolveAppeal(appealId);
  const [reviewerNotes, setReviewerNotes] = useState('');
  const [errorMessage, setErrorMessage] = useState('');
  const [successMessage, setSuccessMessage] = useState('');

  if (isLoading) {
    return (
      <div className="flex flex-col h-72 items-center justify-center gap-3">
        <div className="w-8 h-8 rounded-full border-2 border-primary border-t-transparent animate-spin" />
        <p className="text-on-surface-variant font-label-md text-xs">Loading appeal docket...</p>
      </div>
    );
  }

  if (isError || !appeal) {
    return (
      <div className="max-w-xl mx-auto my-12 p-8 rounded-2xl bg-surface-container-lowest border border-outline-variant/40 shadow-lg text-center flex flex-col items-center gap-3">
        <div className="p-3 rounded-full bg-rose-50 text-rose-600">
          <AlertCircle className="w-8 h-8" />
        </div>
        <h2 className="font-headline-md text-headline-md text-on-surface">Appeal Not Found</h2>
        <p className="font-body-md text-on-surface-variant text-xs">
          Could not locate this appeal or you are not authorized to view appeals for this course cohort.
        </p>
        <Link href="/teacher/appeals" className="mt-2 px-4 py-2 bg-primary text-on-primary rounded-lg text-xs font-medium">
          Back to Appeals Hub
        </Link>
      </div>
    );
  }

  const isPending = appeal.status === 'PENDING';
  const isApproved = appeal.status === 'APPROVED';
  const isRejected = appeal.status === 'REJECTED';

  const handleResolve = (decision: 'APPROVED' | 'REJECTED') => {
    setErrorMessage('');
    setSuccessMessage('');

    resolveMutation.mutate(
      { decision, reviewerNotes },
      {
        onSuccess: (data) => {
          const statusText = data.status === 'APPROVED' ? 'approved' : 'rejected';
          toast.success(`Appeal #${appeal.id.substring(0, 8)} successfully ${statusText}!`);
          setSuccessMessage(`Decision recorded: ${data.status}`);
          refetch();
        },
        onError: (err: unknown) => {
          const msg = (err as { response?: { data?: { message?: string } }; message?: string })?.response?.data?.message || (err as { message?: string })?.message || 'Failed to record decision.';
          setErrorMessage(msg);
          toast.error(msg);
        },
      }
    );
  };

  return (
    <div className="max-w-7xl mx-auto px-space-lg py-space-lg flex flex-col w-full space-y-space-lg">
      {/* Top Bar */}
      <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-space-md pb-space-md border-b border-outline-variant/30">
        <div className="space-y-1">
          <nav className="flex items-center gap-2 text-on-surface-variant text-xs mb-1">
            <Link className="hover:text-primary transition-colors flex items-center gap-1 font-medium" href="/teacher/appeals">
              <ArrowLeft className="w-3.5 h-3.5" />
              <span>Appeals Hub</span>
            </Link>
            <span className="text-outline">/</span>
            <span className="text-primary font-semibold">Case #{appeal.id.substring(0, 8)}</span>
          </nav>
          <div className="flex items-center gap-space-sm flex-wrap">
            <h1 className="font-headline-lg text-headline-lg text-on-surface tracking-tight">
              Appeal Review — Case #{appeal.id.substring(0, 8)}
            </h1>
            {isPending && (
              <span className="text-xs px-2.5 py-0.5 rounded-full bg-amber-50 text-amber-700 border border-amber-200 font-semibold flex items-center gap-1">
                <span className="w-1.5 h-1.5 rounded-full bg-amber-500 animate-pulse"></span>
                <span>Pending Review</span>
              </span>
            )}
            {isApproved && (
              <span className="text-xs px-2.5 py-0.5 rounded-full bg-emerald-50 text-emerald-700 border border-emerald-200 font-semibold flex items-center gap-1">
                <CheckCircle2 className="w-3.5 h-3.5 text-emerald-600" />
                <span>Approved</span>
              </span>
            )}
            {isRejected && (
              <span className="text-xs px-2.5 py-0.5 rounded-full bg-rose-50 text-rose-700 border border-rose-200 font-semibold flex items-center gap-1">
                <XCircle className="w-3.5 h-3.5 text-rose-600" />
                <span>Rejected</span>
              </span>
            )}
          </div>
          <p className="text-xs text-on-surface-variant">
            Submitted by <strong className="text-on-surface">{appeal.studentName || 'Student'}</strong> ({appeal.studentEmail || 'N/A'}) on {new Date(appeal.createdAt).toLocaleDateString()}
          </p>
        </div>

        <div className="flex items-center gap-2 self-start lg:self-auto">
          <Link
            href="/teacher/appeals"
            className="px-4 py-2 rounded-lg bg-surface-container hover:bg-surface-container-high text-on-surface text-xs font-medium border border-outline-variant/40 transition-colors"
          >
            Back to Appeals
          </Link>
        </div>
      </div>

      {successMessage && (
        <div className="p-3.5 rounded-xl bg-emerald-50 text-emerald-800 text-xs flex items-center gap-2 border border-emerald-200">
          <CheckCircle2 className="w-4 h-4 text-emerald-600 shrink-0" />
          <span>{successMessage}</span>
        </div>
      )}

      {errorMessage && (
        <div className="p-3.5 rounded-xl bg-rose-50 text-rose-700 text-xs flex items-center gap-2 border border-rose-200">
          <AlertCircle className="w-4 h-4 text-rose-600 shrink-0" />
          <span>{errorMessage}</span>
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-space-lg items-start">
        {/* Left Column: Context Card */}
        <aside className="lg:col-span-4 space-y-space-md">
          <div className="bg-surface-container-lowest rounded-2xl p-space-md border border-outline-variant/40 shadow-xs space-y-3 text-xs">
            <div className="font-label-xs uppercase tracking-wider text-outline font-semibold pb-2 border-b border-outline-variant/20">
              Assessment Information
            </div>
            <div>
              <span className="text-outline uppercase text-[10px] block">Exam</span>
              <span className="font-semibold text-on-surface text-sm">{appeal.examName}</span>
            </div>
            <div>
              <span className="text-outline uppercase text-[10px] block">Submission Reference</span>
              <span className="font-mono text-on-surface text-xs">#{appeal.submissionId.substring(0, 8)}</span>
            </div>
            <div>
              <span className="text-outline uppercase text-[10px] block">Basis for Appeal</span>
              <span className="font-semibold text-on-surface capitalize">{appeal.basis.replace(/_/g, ' ')}</span>
            </div>
            <div>
              <span className="text-outline uppercase text-[10px] block">Student Details</span>
              <span className="font-semibold text-on-surface block">{appeal.studentName || 'Student'}</span>
              <span className="text-on-surface-variant text-[11px]">{appeal.studentEmail}</span>
            </div>
          </div>
        </aside>

        {/* Right Column: Explanation and Decision Panel */}
        <main className="lg:col-span-8 space-y-space-lg">
          {/* Student Explanation */}
          <div className="bg-surface-container-lowest rounded-2xl border border-outline-variant/40 shadow-xs p-space-lg space-y-2">
            <div className="flex items-center gap-2 pb-2 border-b border-outline-variant/20">
              <FileText className="w-4 h-4 text-primary" />
              <h2 className="font-headline-sm text-headline-sm text-on-surface font-semibold">Student Defense Statement</h2>
            </div>
            <div className="bg-surface-container-low p-space-md rounded-xl whitespace-pre-wrap text-xs text-on-surface leading-relaxed font-mono border border-outline-variant/20">
              {appeal.explanation}
            </div>
          </div>

          {/* Resolution Box */}
          <div className="bg-surface-container-lowest rounded-2xl border border-outline-variant/40 shadow-xs p-space-lg space-y-space-md">
            <div className="flex items-center gap-2 pb-2 border-b border-outline-variant/20">
              <Scale className="w-4 h-4 text-primary" />
              <h2 className="font-headline-sm text-headline-sm text-on-surface font-semibold">Faculty Committee Determination</h2>
            </div>

            {!isPending ? (
              <div className="space-y-space-md text-xs">
                <div className={`p-4 rounded-xl flex items-center gap-2.5 ${
                  isApproved 
                    ? 'bg-emerald-50 text-emerald-800 border border-emerald-200' 
                    : 'bg-rose-50 text-rose-800 border border-rose-200'
                }`}>
                  {isApproved ? <CheckCircle2 className="w-5 h-5 text-emerald-600 shrink-0" /> : <XCircle className="w-5 h-5 text-rose-600 shrink-0" />}
                  <span className="font-semibold">
                    Decision Concluded: {appeal.status} (Resolved on {new Date(appeal.resolvedAt || '').toLocaleString()})
                  </span>
                </div>

                <div className="space-y-1">
                  <span className="font-label-xs uppercase tracking-wider text-outline">Reviewer Feedback Logged:</span>
                  <div className="bg-surface-container-low p-space-md rounded-xl text-xs text-on-surface border border-outline-variant/20">
                    {appeal.reviewerNotes || 'No additional reviewer notes provided.'}
                  </div>
                </div>

                <div className="flex items-center gap-1.5 text-outline text-[11px] italic">
                  <Lock className="w-3.5 h-3.5" />
                  <span>This case is locked. Completed decisions cannot be modified without Dean approval.</span>
                </div>
              </div>
            ) : (
              <div className="space-y-space-md">
                <div className="space-y-1">
                  <label className="text-xs font-semibold text-on-surface block">
                    Reviewer Notes / Instructions for Candidate:
                  </label>
                  <textarea
                    rows={4}
                    placeholder="Enter formal justification, revised grade rationale, or rejection explanation for the candidate..."
                    value={reviewerNotes}
                    onChange={(e) => setReviewerNotes(e.target.value)}
                    className="w-full p-3 rounded-xl border border-outline-variant/40 bg-surface-container-low text-on-surface text-xs focus:outline-none focus:border-primary transition-colors leading-relaxed"
                    disabled={resolveMutation.isPending}
                  />
                </div>

                <div className="flex items-center justify-end gap-3 pt-space-sm border-t border-outline-variant/20">
                  <button
                    type="button"
                    onClick={() => handleResolve('REJECTED')}
                    disabled={resolveMutation.isPending}
                    className="px-5 py-2 rounded-lg bg-rose-50 hover:bg-rose-100 text-rose-700 border border-rose-200 text-xs font-semibold transition-all inline-flex items-center gap-1.5 shadow-xs disabled:opacity-50 cursor-pointer"
                  >
                    <X className="w-3.5 h-3.5 text-rose-600" />
                    <span>Reject Appeal</span>
                  </button>

                  <button
                    type="button"
                    onClick={() => handleResolve('APPROVED')}
                    disabled={resolveMutation.isPending}
                    className="px-5 py-2 rounded-lg bg-emerald-600 hover:bg-emerald-700 text-white text-xs font-semibold transition-all inline-flex items-center gap-1.5 shadow-xs disabled:opacity-50 cursor-pointer"
                  >
                    {resolveMutation.isPending ? (
                      <>
                        <Loader2 className="w-3.5 h-3.5 animate-spin" />
                        <span>Recording...</span>
                      </>
                    ) : (
                      <>
                        <Check className="w-3.5 h-3.5" />
                        <span>Approve Appeal</span>
                      </>
                    )}
                  </button>
                </div>
              </div>
            )}
          </div>
        </main>
      </div>
    </div>
  );
}
