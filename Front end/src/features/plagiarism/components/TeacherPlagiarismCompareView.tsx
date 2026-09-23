'use client';
import React, { useState } from 'react';
import { usePlagiarismComparison, usePlagiarismDetail } from '../hooks';
import { AxiosError } from 'axios';
import { AppLink as Link } from '@/components/ui/app-link';
import { AiAnalysisModal } from './AiAnalysisModal';
import { plagiarismAiApi, AiAnalysisResponse } from '../api/plagiarism-ai.api';

export function TeacherPlagiarismCompareView({ recordId }: { recordId: string }) {
  const { data, isLoading, isError, error } = usePlagiarismComparison(recordId);
  const { data: detailData } = usePlagiarismDetail(recordId);

  // AI Modal State
  const [aiModalOpen, setAiModalOpen] = useState(false);
  const [aiStatus, setAiStatus] = useState<'idle' | 'loading' | 'success' | 'error'>('idle');
  const [aiData, setAiData] = useState<AiAnalysisResponse | null>(null);
  const [aiError, setAiError] = useState<string | undefined>();
  const [selectedEvidenceId, setSelectedEvidenceId] = useState<string>('');

  const handleRunAiCodeReview = async (evidenceId: string) => {
    setAiModalOpen(true);
    setAiStatus('loading');
    setAiError(undefined);
    try {
      const response = await plagiarismAiApi.getCodeReview(recordId, evidenceId);
      setAiData(response);
      setAiStatus('success');
    } catch (err: unknown) {
      setAiStatus('error');
      setAiError((err instanceof Error ? err.message : 'Unknown error') || 'Failed to fetch AI code review');
    }
  };

  if (isLoading) {
    return (
      <div className="flex h-64 items-center justify-center">
        <div className="text-on-surface-variant font-label-md">Loading plagiarism data...</div>
      </div>
    );
  }

  if (isError) {
    const status = (error as AxiosError)?.response?.status;
    if (status === 403) {
      return (
        <div className="flex flex-col h-64 items-center justify-center p-space-lg text-center gap-space-sm">
          <span className="material-symbols-outlined text-error text-[48px]">lock</span>
          <h2 className="font-headline-md text-headline-md text-on-surface">Forbidden</h2>
          <p className="font-body-md text-on-surface-variant">You do not have permission to access this resource.</p>
        </div>
      );
    }
    if (status === 404) {
      return (
        <div className="flex flex-col h-64 items-center justify-center p-space-lg text-center gap-space-sm">
          <span className="material-symbols-outlined text-outline text-[48px]">search_off</span>
          <h2 className="font-headline-md text-headline-md text-on-surface">Not Found</h2>
          <p className="font-body-md text-on-surface-variant">The requested resource could not be found.</p>
        </div>
      );
    }
    return (
      <div className="flex flex-col h-64 items-center justify-center p-space-lg text-center gap-space-sm">
        <span className="material-symbols-outlined text-error text-[48px]">error</span>
        <h2 className="font-headline-md text-headline-md text-on-surface">Integration Error</h2>
        <p className="font-body-md text-on-surface-variant">Backend is currently offline or the contract is unconfirmed.</p>
        <p className="font-code-body text-code-body text-outline">{(error as Error).message}</p>
      </div>
    );
  }

  if (!data) return null;

  return (
    <>
      <div className="flex-1 flex flex-col min-h-0 overflow-y-auto bg-surface">
        <div className="max-w-7xl mx-auto px-space-lg py-space-lg w-full">
          <div className="flex flex-col w-full space-y-space-xl">
            <div className="flex flex-col md:flex-row md:items-center justify-between gap-space-sm pb-space-sm border-b border-outline-variant/30">
              <div className="flex flex-col space-y-1">
                <nav className="flex items-center gap-2 text-on-surface-variant font-body-sm text-body-sm">
                  <Link className="hover:text-primary transition-colors" href="/teacher/plagiarism">Plagiarism Dashboard</Link>
                  <span className="text-outline">/</span>
                  <span className="text-on-surface font-semibold">Code Comparison</span>
                </nav>
                <h1 className="font-headline-lg text-headline-lg text-on-surface tracking-tight">Source Code Diff Analysis</h1>
                <p className="font-body-md text-body-md text-on-surface-variant">Comparing {detailData?.examName || 'Exam'} - Record #{data.pairId}</p>
              </div>
              <div className="flex items-center gap-space-sm self-start md:self-auto">
                <span className="font-label-sm text-label-sm px-2.5 py-1 rounded bg-surface-container-high text-on-surface font-semibold flex items-center gap-1.5">
                  <span className="h-2 w-2 rounded-full bg-primary animate-pulse"></span> AST Engine v4.8.2
                </span>
              </div>
            </div>

            <div className="flex flex-col space-y-space-sm">
              <h2 className="font-headline-sm text-headline-sm text-on-surface">Matched AST Structures</h2>
              <div className="space-y-space-xs">
                {data.matches && data.matches.length > 0 ? (
                  data.matches.map((match, idx) => (
                    <div key={idx} className="rounded-xl bg-surface-container-lowest p-space-md shadow-sm border border-outline-variant/30 flex flex-col hover:shadow-md transition-shadow">
                      <div className="flex items-center justify-between pb-space-xs mb-space-xs border-b border-outline-variant/20">
                        <span className="font-label-md text-label-md font-semibold text-primary font-mono">{match.type || 'AST Node Match'}</span>
                      </div>
                      <div className="flex justify-between items-center text-label-sm text-on-surface-variant font-mono">
                        <span>Student A: Lines {match.startLineA} - {match.endLineA}</span>
                        <span>Student B: Lines {match.startLineB} - {match.endLineB}</span>
                      </div>
                    </div>
                  ))
                ) : (
                  <p className="font-body-sm text-on-surface-variant">No structural matches found.</p>
                )}
              </div>
            </div>

            <div className="flex flex-col space-y-space-sm">
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
                <div>
                  <h2 className="font-headline-md text-headline-md text-on-surface">Source Code Comparison</h2>
                  <p className="font-body-sm text-body-sm text-on-surface-variant">Side-by-side diff of Student A and Student B submissions</p>
                </div>
              </div>
              <div className="grid grid-cols-1 lg:grid-cols-2 gap-space-md w-full">
                <div className="flex flex-col border border-outline-variant/30 rounded-xl overflow-hidden shadow-sm h-[600px] flex-shrink-0">
                  <div className="bg-surface-container-low px-space-md py-2 flex items-center justify-between border-b border-outline-variant/30">
                    <span className="font-code-body text-code-body font-semibold text-on-surface">{data.fileA || 'Student A Code'}</span>
                  </div>
                  <div className="bg-[#0b1120] text-[#e2e8f0] p-space-md overflow-x-auto overflow-y-auto select-text font-mono text-[13px] leading-6 flex-1">
                    {data.sourceCodeA ? data.sourceCodeA.split('\n').map((line, idx) => {
                      const isMatch = data.matches?.some(m => (idx + 1) >= m.startLineA && (idx + 1) <= m.endLineA);
                      return (
                        <div key={idx} className={isMatch ? "bg-[#ba1a1a]/20 -mx-space-md px-space-md border-l-4 border-error text-[#f8fafc]" : ""}>
                          <span className="text-[#64748b] mr-4 inline-block w-6 text-right">{idx + 1}</span>
                          {line}
                        </div>
                      );
                    }) : <div className="text-on-surface-variant italic">No source code provided.</div>}
                  </div>
                </div>
                
                <div className="flex flex-col border border-outline-variant/30 rounded-xl overflow-hidden shadow-sm h-[600px] flex-shrink-0">
                  <div className="bg-surface-container-low px-space-md py-2 flex items-center justify-between border-b border-outline-variant/30">
                    <span className="font-code-body text-code-body font-semibold text-on-surface">{data.fileB || 'Student B Code'}</span>
                  </div>
                  <div className="bg-[#0b1120] text-[#e2e8f0] p-space-md overflow-x-auto overflow-y-auto select-text font-mono text-[13px] leading-6 flex-1">
                    {data.sourceCodeB ? data.sourceCodeB.split('\n').map((line, idx) => {
                      const isMatch = data.matches?.some(m => (idx + 1) >= m.startLineB && (idx + 1) <= m.endLineB);
                      return (
                        <div key={idx} className={isMatch ? "bg-[#ba1a1a]/20 -mx-space-md px-space-md border-l-4 border-error text-[#f8fafc]" : ""}>
                          <span className="text-[#64748b] mr-4 inline-block w-6 text-right">{idx + 1}</span>
                          {line}
                        </div>
                      );
                    }) : <div className="text-on-surface-variant italic">No source code provided.</div>}
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
      <AiAnalysisModal 
        isOpen={aiModalOpen}
        onClose={() => setAiModalOpen(false)}
        title="AI Code Review Assistant"
        status={aiStatus}
        data={aiData}
        errorMessage={aiError}
      />
    </>
  );
}
