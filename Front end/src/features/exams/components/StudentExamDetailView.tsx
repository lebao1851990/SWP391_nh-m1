'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { useStudentExam, useSubmitExam } from '../hooks/use-student-exams';
import { AxiosError } from 'axios';
import { AppLink as Link } from '@/components/ui/app-link';

interface StudentExamDetailViewProps {
  examId: string;
}

export function StudentExamDetailView({ examId }: StudentExamDetailViewProps) {
  const router = useRouter();
  const { data: exam, isLoading, isError, error } = useStudentExam(examId);
  const submitExam = useSubmitExam();
  
  const [sourceCode, setSourceCode] = useState('');
  const [localError, setLocalError] = useState('');

  if (isLoading) {
    return (
      <div className="max-w-7xl mx-auto px-space-lg py-space-lg flex justify-center items-center h-64">
        <div className="text-on-surface-variant font-label-md">Loading exam details...</div>
      </div>
    );
  }

  if (isError) {
    const status = (error as AxiosError)?.response?.status;
    if (status === 403) {
      return (
        <div className="max-w-7xl mx-auto px-space-lg py-space-lg text-center">
          <span className="material-symbols-outlined text-error text-[48px]">lock</span>
          <h2 className="font-headline-md text-headline-md text-on-surface mt-4">Forbidden</h2>
          <p className="font-body-md text-on-surface-variant">You do not have permission to access this exam.</p>
        </div>
      );
    }
    if (status === 404) {
      return (
        <div className="max-w-7xl mx-auto px-space-lg py-space-lg text-center">
          <span className="material-symbols-outlined text-outline text-[48px]">search_off</span>
          <h2 className="font-headline-md text-headline-md text-on-surface mt-4">Exam Not Found</h2>
          <p className="font-body-md text-on-surface-variant">This exam does not exist or you are not enrolled.</p>
        </div>
      );
    }
    return (
      <div className="max-w-7xl mx-auto px-space-lg py-space-lg text-center">
        <span className="material-symbols-outlined text-error text-[48px]">error</span>
        <h2 className="font-headline-md text-headline-md text-on-surface mt-4">Error Loading Exam</h2>
        <p className="font-body-md text-on-surface-variant">{(error as Error).message}</p>
      </div>
    );
  }

  if (!exam) return null;

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!sourceCode.trim()) {
      setLocalError('Source code cannot be empty.');
      return;
    }
    setLocalError('');
    
    submitExam.mutate(
      { examId, sourceCode },
      {
        onSuccess: () => {
          // Navigate back to the exams list or dashboard
          router.push('/student/exams');
        },
      }
    );
  };

  const isClosed = exam.status === 'completed' || exam.status === 'missed';

  return (
    <div className="max-w-7xl mx-auto px-space-lg py-space-lg">
      <div className="flex flex-col w-full space-y-space-lg">
        {/* Top Navigation & Action Bar */}
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-space-md">
          <div className="space-y-1.5">
            <nav className="flex items-center gap-2 text-on-surface-variant font-label-sm text-label-sm">
              <Link className="hover:text-primary transition-colors flex items-center gap-1" href="/student/exams">
                <span className="material-symbols-outlined text-[14px]">home</span>
                Exams
              </Link>
              <span className="text-outline-variant">/</span>
              <span className="text-primary font-semibold">{exam.title}</span>
            </nav>
            
            <div className="flex flex-wrap items-center gap-3">
              <h1 className="font-headline-lg text-headline-lg text-on-surface tracking-tight">{exam.title}</h1>
              <div className={`flex items-center gap-1.5 px-2.5 py-0.5 rounded-full font-label-sm text-label-sm font-semibold ${
                exam.status === 'active' 
                  ? 'bg-tertiary-fixed text-on-tertiary-fixed' 
                  : 'bg-surface-container-high text-on-surface-variant'
              }`}>
                {exam.status === 'active' && (
                  <span className="relative flex h-2 w-2">
                    <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-tertiary opacity-75"></span>
                    <span className="relative inline-flex rounded-full h-2 w-2 bg-tertiary"></span>
                  </span>
                )}
                {exam.status.toUpperCase()}
              </div>
            </div>

            <div className="flex flex-wrap items-center gap-2 pt-1 font-label-sm text-label-sm text-on-surface-variant">
              <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded bg-surface-container font-medium text-on-surface">
                <span className="material-symbols-outlined text-[15px] text-primary">menu_book</span>
                Course Class: {exam.courseClassCode}
              </span>
              <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded bg-surface-container-high text-on-surface">
                <span className="material-symbols-outlined text-[15px] text-outline">schedule</span>
                Deadline: {new Date(exam.endTime).toLocaleString()}
                {isClosed && <span className="text-error font-medium ml-1">(Closed)</span>}
              </span>
            </div>
          </div>
        </div>

        {/* Submission Form */}
        <div className="bg-surface-container-lowest p-space-md rounded-xl shadow-sm border border-outline-variant">
          <form onSubmit={handleSubmit} className="flex flex-col gap-4">
            <div className="flex items-center gap-2">
              <span className="material-symbols-outlined text-primary text-[20px]">code</span>
              <h2 className="font-headline-sm text-headline-sm text-on-surface">Source Code Submission</h2>
            </div>
            
            <p className="font-body-sm text-body-sm text-on-surface-variant">
              {exam.description}
            </p>

            <div className="relative">
              <textarea 
                className="w-full h-96 p-4 bg-surface-container font-code-body text-code-body text-on-surface rounded-lg border border-outline focus:border-primary focus:ring-1 focus:ring-primary outline-none transition-all resize-y"
                placeholder="Paste your source code here..."
                value={sourceCode}
                onChange={(e) => setSourceCode(e.target.value)}
                disabled={submitExam.isPending || isClosed}
                spellCheck="false"
              />
            </div>

            {localError && (
              <div className="flex items-center gap-2 text-error font-label-sm text-label-sm">
                <span className="material-symbols-outlined text-[16px]">warning</span>
                {localError}
              </div>
            )}

            {submitExam.isError && (
              <div className="flex items-center gap-2 text-error font-label-sm text-label-sm p-3 bg-error-container rounded">
                <span className="material-symbols-outlined text-[16px]">error</span>
                {(submitExam.error as AxiosError<{message?: string}>)?.response?.data?.message || submitExam.error.message}
              </div>
            )}

            <div className="flex justify-end gap-3 mt-4">
              <button 
                type="button"
                className="px-4 py-2 font-label-md text-label-md rounded border border-outline hover:bg-surface-container transition-colors"
                onClick={() => router.push('/student/exams')}
                disabled={submitExam.isPending}
              >
                Cancel
              </button>
              
              <button 
                type="submit"
                className="flex items-center gap-2 px-6 py-2 bg-primary text-on-primary rounded font-label-md text-label-md hover:bg-primary-container transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                disabled={submitExam.isPending || isClosed || !sourceCode.trim()}
              >
                {submitExam.isPending ? (
                  <>
                    <span className="material-symbols-outlined text-[18px] animate-spin">refresh</span>
                    Submitting...
                  </>
                ) : (
                  <>
                    <span className="material-symbols-outlined text-[18px]">send</span>
                    Submit Code
                  </>
                )}
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>
  );
}
