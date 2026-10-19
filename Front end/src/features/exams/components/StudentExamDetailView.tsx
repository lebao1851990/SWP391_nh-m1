'use client';

import React, { useState } from 'react';
import { useRouter } from 'next/navigation';
import { useStudentExam, useSubmitExam } from '../hooks/use-student-exams';
import { AxiosError } from 'axios';
import { AppLink as Link } from '@/components/ui/app-link';
import { 
  ArrowLeft, 
  Code2, 
  Send, 
  AlertCircle, 
  Clock, 
  BookOpen, 
  Lock, 
  FileCheck, 
  CheckCircle2, 
  Loader2, 
  FileCode2,
  HelpCircle,
  ShieldAlert
} from 'lucide-react';
import { toast } from 'sonner';

interface StudentExamDetailViewProps {
  examId: string;
}

export function StudentExamDetailView({ examId }: StudentExamDetailViewProps) {
  const router = useRouter();
  const { data: exam, isLoading, isError, error } = useStudentExam(examId);
  const submitExam = useSubmitExam();
  
  const [sourceCode, setSourceCode] = useState('');
  const [localError, setLocalError] = useState('');
  const [showConfirmModal, setShowConfirmModal] = useState(false);

  if (isLoading) {
    return (
      <div className="flex flex-col h-72 items-center justify-center gap-3">
        <div className="w-8 h-8 rounded-full border-2 border-primary border-t-transparent animate-spin" />
        <p className="text-on-surface-variant font-label-md text-xs">Loading exam specifications...</p>
      </div>
    );
  }

  if (isError) {
    const status = (error as AxiosError)?.response?.status;
    if (status === 403) {
      return (
        <div className="max-w-xl mx-auto my-12 p-8 rounded-2xl bg-surface-container-lowest border border-outline-variant/40 shadow-lg text-center flex flex-col items-center gap-3">
          <div className="p-3 rounded-full bg-rose-50 text-rose-600">
            <Lock className="w-8 h-8" />
          </div>
          <h2 className="font-headline-md text-headline-md text-on-surface">Access Restricted</h2>
          <p className="font-body-md text-on-surface-variant text-xs">You are not enrolled in the class assigned to this assessment.</p>
          <Link href="/student/exams" className="mt-2 px-4 py-2 bg-primary text-on-primary rounded-lg text-xs font-medium">
            Back to Assessments
          </Link>
        </div>
      );
    }
    if (status === 404) {
      return (
        <div className="max-w-xl mx-auto my-12 p-8 rounded-2xl bg-surface-container-lowest border border-outline-variant/40 shadow-lg text-center flex flex-col items-center gap-3">
          <div className="p-3 rounded-full bg-amber-50 text-amber-600">
            <AlertCircle className="w-8 h-8" />
          </div>
          <h2 className="font-headline-md text-headline-md text-on-surface">Assessment Not Found</h2>
          <p className="font-body-md text-on-surface-variant text-xs">This assessment does not exist or has been archived.</p>
          <Link href="/student/exams" className="mt-2 px-4 py-2 bg-primary text-on-primary rounded-lg text-xs font-medium">
            Back to Assessments
          </Link>
        </div>
      );
    }
    return (
      <div className="max-w-xl mx-auto my-12 p-8 rounded-2xl bg-surface-container-lowest border border-rose-200/60 shadow-lg text-center flex flex-col items-center gap-3">
        <div className="p-3 rounded-full bg-rose-50 text-rose-600">
          <AlertCircle className="w-8 h-8" />
        </div>
        <h2 className="font-headline-md text-headline-md text-on-surface">Error Loading Assessment</h2>
        <p className="font-body-md text-on-surface-variant text-xs">{(error as Error).message}</p>
        <button onClick={() => window.location.reload()} className="mt-2 px-4 py-2 bg-primary text-on-primary rounded-lg text-xs font-medium">
          Retry
        </button>
      </div>
    );
  }

  if (!exam) return null;

  const nowMs = Date.now();
  const startMs = exam.startTime ? new Date(exam.startTime).getTime() : 0;
  const endMs = exam.endTime ? new Date(exam.endTime).getTime() : 0;
  const isUpcoming = Boolean(startMs && nowMs < startMs);
  const isDeadlinePassed = Boolean(endMs && nowMs > endMs);
  const isClosed = exam.status === 'completed' || exam.status === 'missed' || isDeadlinePassed || isUpcoming;
  const lineCount = sourceCode ? sourceCode.split('\n').length : 0;
  const charCount = sourceCode.length;

  const handlePreSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (isUpcoming) {
      setLocalError(`Assessment has not started yet. Window opens at ${new Date(exam.startTime).toLocaleString()}.`);
      return;
    }
    if (isDeadlinePassed || exam.status === 'completed' || exam.status === 'missed') {
      setLocalError(`Assessment deadline has passed (${new Date(exam.endTime).toLocaleString()}). Submissions are closed.`);
      return;
    }
    if (!sourceCode.trim()) {
      setLocalError('Source code cannot be empty.');
      return;
    }
    setLocalError('');
    setShowConfirmModal(true);
  };

  const handleFinalSubmit = () => {
    setShowConfirmModal(false);
    submitExam.mutate(
      { examId, sourceCode },
      {
        onSuccess: (data: { id?: string }) => {
          toast.success('Source code submitted successfully! Running sandbox tests...');
          if (data && data.id) {
            router.push(`/student/submissions/${data.id}`);
          } else {
            router.push('/student/submissions');
          }
        },
        onError: (err: unknown) => {
          const msg = (err as AxiosError<{message?: string}>)?.response?.data?.message || (err as Error)?.message || 'Submission failed';
          setLocalError(msg);
          toast.error(msg);
        }
      }
    );
  };

  return (
    <div className="max-w-7xl mx-auto px-space-lg py-space-lg">
      <div className="flex flex-col w-full space-y-space-lg">
        {/* Header Breadcrumbs & Status Bar */}
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-space-md">
          <div className="space-y-1.5">
            <nav className="flex items-center gap-2 text-on-surface-variant text-xs">
              <Link className="hover:text-primary transition-colors flex items-center gap-1 font-medium" href="/student/exams">
                <ArrowLeft className="w-3.5 h-3.5" />
                <span>Assessments</span>
              </Link>
              <span className="text-outline-variant">/</span>
              <span className="text-primary font-semibold truncate max-w-[200px]">{exam.title}</span>
            </nav>
            
            <div className="flex flex-wrap items-center gap-3">
              <h1 className="font-headline-lg text-headline-lg text-on-surface tracking-tight">{exam.title}</h1>
              <div className={`flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-semibold ${
                !isClosed && exam.status === 'active'
                  ? 'bg-emerald-50 text-emerald-700 border border-emerald-200' 
                  : isUpcoming
                  ? 'bg-blue-50 text-blue-700 border border-blue-200'
                  : 'bg-surface-container text-on-surface-variant'
              }`}>
                {!isClosed && exam.status === 'active' && (
                  <span className="relative flex h-2 w-2">
                    <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75"></span>
                    <span className="relative inline-flex rounded-full h-2 w-2 bg-emerald-500"></span>
                  </span>
                )}
                <span>{isUpcoming ? 'UPCOMING' : isDeadlinePassed ? 'CLOSED' : exam.status.toUpperCase()}</span>
              </div>
            </div>

            <div className="flex flex-wrap items-center gap-2 pt-1 text-xs text-on-surface-variant">
              <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded bg-surface-container font-mono font-medium text-on-surface border border-outline-variant/30">
                <BookOpen className="w-3.5 h-3.5 text-primary" />
                <span>Class: {exam.courseClassCode || 'SE-PRACTICAL'}</span>
              </span>
              <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded bg-surface-container-high text-on-surface border border-outline-variant/30">
                <Clock className="w-3.5 h-3.5 text-outline" />
                <span>Deadline: {new Date(exam.endTime).toLocaleString()}</span>
                {isDeadlinePassed && <span className="text-rose-600 font-semibold ml-1">(Concluded)</span>}
                {isUpcoming && <span className="text-blue-600 font-semibold ml-1">(Not Started)</span>}
              </span>
            </div>
          </div>
        </div>

        {/* Upcoming Exam Alert Banner */}
        {isUpcoming && (
          <div className="p-4 rounded-xl bg-blue-50 border border-blue-200 text-blue-800 text-xs flex items-center gap-3">
            <Clock className="w-5 h-5 text-blue-600 shrink-0" />
            <div>
              <p className="font-semibold">Assessment Not Yet Started</p>
              <p className="text-blue-700 mt-0.5">This assessment will open at {new Date(exam.startTime).toLocaleString()}. You cannot submit solutions until the window starts.</p>
            </div>
          </div>
        )}

        {/* Closed Exam Alert Banner */}
        {isDeadlinePassed && (
          <div className="p-4 rounded-xl bg-amber-50 border border-amber-200 text-amber-800 text-xs flex items-center gap-3">
            <AlertCircle className="w-5 h-5 text-amber-600 shrink-0" />
            <div>
              <p className="font-semibold">Assessment Window Concluded</p>
              <p className="text-amber-700 mt-0.5">The deadline for this assessment has passed ({new Date(exam.endTime).toLocaleString()}). Submissions are closed.</p>
            </div>
          </div>
        )}

        {/* Completed/Missed Banner */}
        {(!isDeadlinePassed && !isUpcoming && (exam.status === 'completed' || exam.status === 'missed')) && (
          <div className="p-4 rounded-xl bg-surface-container border border-outline-variant/40 text-on-surface-variant text-xs flex items-center gap-3">
            <CheckCircle2 className="w-5 h-5 text-primary shrink-0" />
            <div>
              <p className="font-semibold">Assessment Status: {exam.status.toUpperCase()}</p>
              <p className="text-on-surface-variant/80 mt-0.5">This assessment is no longer accepting new submissions.</p>
            </div>
          </div>
        )}

        {/* Exam Description Card */}
        {exam.description && (
          <div className="p-space-md rounded-xl bg-surface-container-lowest border border-outline-variant/40 shadow-xs">
            <h3 className="font-label-sm font-semibold text-outline uppercase tracking-wider mb-1">Problem Statement &amp; Requirements</h3>
            <p className="text-xs text-on-surface leading-relaxed whitespace-pre-wrap">{exam.description}</p>
          </div>
        )}

        {/* Submission IDE Card */}
        <div className="bg-surface-container-lowest p-space-md rounded-2xl border border-outline-variant/40 shadow-xs">
          <form onSubmit={handlePreSubmit} className="flex flex-col gap-4">
            <div className="flex items-center justify-between border-b border-outline-variant/30 pb-3">
              <div className="flex items-center gap-2">
                <Code2 className="w-5 h-5 text-primary" />
                <h2 className="font-headline-sm text-headline-sm text-on-surface">Source Code Workspace</h2>
              </div>
              <div className="flex items-center gap-3 text-xs text-outline font-mono">
                <span>{lineCount} lines</span>
                <span>•</span>
                <span>{charCount} chars</span>
                <span>•</span>
                <span className="px-2 py-0.5 rounded bg-surface-container text-on-surface-variant font-semibold">Java 17</span>
              </div>
            </div>

            {/* Code Textarea with IDE Styling */}
            <div className="relative rounded-xl border border-outline-variant/60 bg-slate-950 overflow-hidden shadow-inner focus-within:ring-2 focus-within:ring-primary/40 focus-within:border-primary transition-all">
              <div className="px-4 py-2 bg-slate-900 border-b border-slate-800 flex items-center justify-between text-xs text-slate-400 font-mono">
                <div className="flex items-center gap-2">
                  <span className="w-2.5 h-2.5 rounded-full bg-rose-500/80"></span>
                  <span className="w-2.5 h-2.5 rounded-full bg-amber-500/80"></span>
                  <span className="w-2.5 h-2.5 rounded-full bg-emerald-500/80"></span>
                  <span className="ml-2 text-slate-300">Solution.java</span>
                </div>
                <span>UTF-8</span>
              </div>

              <textarea 
                className="w-full h-96 p-4 bg-transparent font-mono text-xs text-slate-100 placeholder:text-slate-600 outline-none resize-y leading-relaxed"
                placeholder="// Enter your Java solution here...&#10;public class Solution {&#10;    public static void main(String[] args) {&#10;        // Code goes here&#10;    }&#10;}"
                value={sourceCode}
                onChange={(e) => setSourceCode(e.target.value)}
                disabled={submitExam.isPending || isClosed}
                spellCheck="false"
              />
            </div>

            {localError && (
              <div className="p-3 rounded-lg bg-rose-50 border border-rose-200 text-rose-700 flex items-center gap-2 text-xs">
                <AlertCircle className="w-4 h-4 shrink-0" />
                <span>{localError}</span>
              </div>
            )}

            <div className="flex items-center justify-between pt-2">
              <div className="text-xs text-outline flex items-center gap-1.5">
                <ShieldAlert className="w-3.5 h-3.5 text-amber-600" />
                <span>Submissions are audited for AST syntax duplication and run in Docker sandbox.</span>
              </div>

              <div className="flex items-center gap-3">
                <button 
                  type="button"
                  className="px-4 py-2 text-xs rounded-lg border border-outline-variant/40 hover:bg-surface-container transition-colors cursor-pointer"
                  onClick={() => router.push('/student/exams')}
                  disabled={submitExam.isPending}
                >
                  Cancel
                </button>
                
                <button 
                  type="submit"
                  className="flex items-center gap-2 px-6 py-2 bg-primary text-on-primary rounded-lg text-xs font-medium hover:bg-primary/90 transition-colors disabled:opacity-50 disabled:cursor-not-allowed cursor-pointer shadow-sm"
                  disabled={submitExam.isPending || isClosed || !sourceCode.trim()}
                >
                  <Send className="w-3.5 h-3.5" />
                  <span>{isUpcoming ? 'Opens at ' + (exam.startTime ? new Date(exam.startTime).toLocaleTimeString() : '') : isDeadlinePassed ? 'Deadline Passed' : 'Submit Code Artifact'}</span>
                </button>
              </div>
            </div>
          </form>
        </div>

        {/* Confirmation Modal */}
        {showConfirmModal && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-xs p-4 animate-in fade-in duration-150">
            <div className="bg-surface-container-lowest rounded-2xl max-w-md w-full p-6 shadow-2xl border border-outline-variant/40 flex flex-col gap-4 animate-in zoom-in-95 duration-150">
              <div className="flex items-center gap-3 text-on-surface">
                <div className="p-2.5 rounded-full bg-blue-50 text-blue-600">
                  <FileCheck className="w-6 h-6" />
                </div>
                <div>
                  <h3 className="font-headline-sm text-headline-sm font-semibold">Confirm Final Submission</h3>
                  <p className="text-xs text-on-surface-variant">Are you ready to submit your code for grading?</p>
                </div>
              </div>

              <div className="p-3 rounded-lg bg-surface-container-low border border-outline-variant/30 text-xs text-on-surface-variant space-y-1">
                <div className="flex justify-between">
                  <span>Lines of code:</span>
                  <span className="font-mono font-semibold text-on-surface">{lineCount}</span>
                </div>
                <div className="flex justify-between">
                  <span>Character count:</span>
                  <span className="font-mono font-semibold text-on-surface">{charCount}</span>
                </div>
                <div className="flex justify-between">
                  <span>Language target:</span>
                  <span className="font-mono font-semibold text-on-surface">Java 17 (OpenJDK)</span>
                </div>
              </div>

              <p className="text-xs text-outline leading-relaxed">
                Once submitted, your code will be immediately queued for Docker sandbox execution and AST plagiarism tokenization.
              </p>

              <div className="flex justify-end gap-3 pt-2 border-t border-outline-variant/30">
                <button
                  type="button"
                  onClick={() => setShowConfirmModal(false)}
                  className="px-4 py-2 text-xs rounded-lg border border-outline-variant/40 hover:bg-surface-container transition-colors cursor-pointer"
                >
                  Revise Code
                </button>
                <button
                  type="button"
                  onClick={handleFinalSubmit}
                  disabled={submitExam.isPending}
                  className="px-5 py-2 bg-primary text-on-primary rounded-lg text-xs font-medium hover:bg-primary/90 transition-colors cursor-pointer shadow-sm flex items-center gap-2"
                >
                  {submitExam.isPending ? (
                    <>
                      <Loader2 className="w-3.5 h-3.5 animate-spin" />
                      <span>Submitting...</span>
                    </>
                  ) : (
                    <>
                      <CheckCircle2 className="w-3.5 h-3.5" />
                      <span>Confirm &amp; Grade</span>
                    </>
                  )}
                </button>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
