'use client';

import React, { useState } from 'react';
import { usePlagiarismDetail } from '../hooks';
import { AxiosError } from 'axios';
import { AppLink as Link } from '@/components/ui/app-link';
import { AiAnalysisModal } from './AiAnalysisModal';
import { plagiarismAiApi, AiAnalysisResponse } from '../api/plagiarism-ai.api';

export function TeacherPlagiarismDetailView({ recordId }: { recordId: string }) {
  const { data, isLoading, isError, error } = usePlagiarismDetail(recordId);

  // AI Modal State
  const [aiModalOpen, setAiModalOpen] = useState(false);
  const [aiStatus, setAiStatus] = useState<'idle' | 'loading' | 'success' | 'error'>('idle');
  const [aiData, setAiData] = useState<AiAnalysisResponse | null>(null);
  const [aiError, setAiError] = useState<string | undefined>();

  const handleRunAiAnalysis = async () => {
    setAiModalOpen(true);
    setAiStatus('loading');
    setAiError(undefined);
    try {
      const response = await plagiarismAiApi.getSimilarityExplanation(recordId);
      setAiData(response);
      setAiStatus('success');
    } catch (err: unknown) {
      setAiStatus('error');
      setAiError((err instanceof Error ? err.message : 'Unknown error') || 'Failed to fetch AI explanation');
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

  const { pair, examName, className, evidence } = data;

  return (
    <div className="flex-1 flex flex-col min-h-0 overflow-y-auto bg-surface">
      <div className="max-w-7xl mx-auto px-space-lg py-space-lg w-full">
        <div className="flex flex-col w-full space-y-space-lg">
          
          <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-space-md">
            <div className="space-y-1">
              <div className="flex items-center gap-space-xs">
                <span className="font-label-sm text-label-sm px-2 py-0.5 rounded bg-primary-fixed text-on-primary-fixed font-semibold tracking-wide">CASE #{recordId.substring(0, 8).toUpperCase()}</span>
                <span className="h-1.5 w-1.5 rounded-full bg-tertiary-fixed-dim"></span>
                <span className="font-label-sm text-label-sm text-outline">{examName} • {className}</span>
              </div>
              <h1 className="font-headline-lg text-headline-lg text-on-surface tracking-tight">Similarity Analysis</h1>
              <p className="font-body-md text-body-md text-on-surface-variant">Reviewing detected plagiarism evidence between {pair.studentAName} and {pair.studentBName}.</p>
            </div>
            
            <div className="flex items-center gap-space-sm self-start md:self-auto">
              <Link href="/teacher/plagiarism" className="flex items-center gap-1.5 px-3 py-2 rounded bg-surface-container-low text-on-surface-variant hover:bg-surface-container hover:text-on-surface transition-colors">
                <span className="material-symbols-outlined text-[18px]">arrow_back</span>
                <span className="font-label-md text-label-md font-medium">Back to Dashboard</span>
              </Link>
            </div>
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-12 gap-space-md">
            <div className="lg:col-span-8 p-space-md rounded-xl bg-surface-container-lowest shadow-sm flex flex-col justify-between">
              <div>
                <div className="flex items-center justify-between mb-space-sm">
                  <div className="flex items-center gap-space-xs">
                    <span className="material-symbols-outlined text-primary text-[20px]">scatter_plot</span>
                    <h3 className="font-headline-sm text-headline-sm text-on-surface">AST Fingerprint Matches</h3>
                  </div>
                  <div className="flex items-center gap-space-sm">
                    <button 
                      onClick={handleRunAiAnalysis}
                      className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-secondary-container/30 text-secondary hover:bg-secondary-container/50 transition-colors border border-secondary/20"
                    >
                      <span className="material-symbols-outlined text-[18px]">smart_toy</span>
                      <span className="font-label-sm text-label-sm font-semibold">AI Similarity Explanation</span>
                    </button>
                    {pair.isFlagged && <span className="font-label-sm text-label-sm px-2 py-0.5 rounded bg-error-container text-on-error-container font-semibold">High Similarity Flagged</span>}
                  </div>
                </div>
                <p className="font-body-sm text-body-sm text-on-surface-variant">
                  Similarity match detected between <strong className="text-on-surface font-medium">{pair.studentAId}</strong> and <strong className="text-on-surface font-medium">{pair.studentBId}</strong> in <span className="font-mono bg-surface-container px-1 py-0.5 rounded">{className}</span>.
                </p>
                
                <div className="mt-space-md h-48 bg-surface-container-low rounded-lg relative overflow-hidden flex items-center justify-center p-4">
                  <svg className="w-full h-full" fill="none" viewBox="0 0 600 160" xmlns="http://www.w3.org/2000/svg">
                    <line stroke={pair.isFlagged ? "#ba1a1a" : "#006398"} strokeDasharray="4 2" strokeWidth="2.5" x1="200" x2="400" y1="80" y2="80"></line>
                    <rect fill={pair.isFlagged ? "#ffdad6" : "#cce5ff"} height="20" rx="4" width="40" x="280" y="68"></rect>
                    <text fill={pair.isFlagged ? "#93000a" : "#001d31"} fontFamily="JetBrains Mono" fontSize="11" fontWeight="700" textAnchor="middle" x="300" y="82">{pair.similarityRate}%</text>
                    
                    <g className="cursor-pointer">
                      <circle cx="200" cy="80" fill="#1d4ed8" r="24"></circle>
                      <text fill="#ffffff" fontFamily="Inter" fontSize="10" fontWeight="700" textAnchor="middle" x="200" y="84">A</text>
                      <text fill="#131b2e" fontFamily="JetBrains Mono" fontSize="11" fontWeight="600" textAnchor="middle" x="200" y="120">{pair.studentAName}</text>
                    </g>
                    
                    <g className="cursor-pointer">
                      <circle cx="400" cy="80" fill="#1d4ed8" r="24"></circle>
                      <text fill="#ffffff" fontFamily="Inter" fontSize="10" fontWeight="700" textAnchor="middle" x="400" y="84">B</text>
                      <text fill="#131b2e" fontFamily="JetBrains Mono" fontSize="11" fontWeight="600" textAnchor="middle" x="400" y="120">{pair.studentBName}</text>
                    </g>
                  </svg>
                </div>
              </div>
            </div>
            
            <div className="lg:col-span-4 p-space-md rounded-xl bg-surface-container-lowest shadow-sm flex flex-col justify-between space-y-space-md">
              <div>
                <div className="flex items-center justify-between mb-space-xs">
                  <div className="flex items-center gap-1.5">
                    <span className="material-symbols-outlined text-primary text-[18px]">neurology</span>
                    <h3 className="font-headline-sm text-headline-sm text-on-surface">AST Evidence Metrics</h3>
                  </div>
                  <span className="font-label-sm text-label-sm px-1.5 py-0.5 rounded bg-tertiary-fixed text-on-tertiary-fixed font-semibold">SYNCHRONIZED</span>
                </div>
                <p className="font-body-sm text-body-sm text-on-surface-variant">Similarity metrics based on AST Winnowing and Jaccard comparison.</p>
                <div className="mt-space-md space-y-space-sm">
                  <div className="p-space-xs rounded bg-surface-container-low flex items-center justify-between">
                    <span className="font-body-sm text-body-sm text-on-surface-variant">Similarity Match</span>
                    <span className={`font-label-sm text-label-sm font-semibold font-mono ${pair.isFlagged ? 'text-error' : 'text-on-surface'}`}>{pair.similarityRate}%</span>
                  </div>
                  <div className="p-space-xs rounded bg-surface-container-low flex items-center justify-between">
                    <span className="font-body-sm text-body-sm text-on-surface-variant">Status</span>
                    <span className={`font-label-sm text-label-sm font-semibold font-mono ${pair.isFlagged ? 'text-error' : 'text-on-surface'}`}>{pair.isFlagged ? 'FLAGGED' : 'MODERATE'}</span>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div className="bg-surface-container-lowest p-space-lg rounded-xl shadow-sm mt-space-md">
            <h3 className="font-headline-sm text-headline-sm text-on-surface mb-space-md">Detected Evidence</h3>
            {evidence && evidence.length > 0 ? (
              <div className="space-y-space-sm">
                {evidence.map((ev) => (
                  <div key={ev.evidenceId} className="bg-surface-container-low p-space-md rounded-xl hover:bg-surface-container transition-colors">
                    <div className="flex items-start justify-between mb-2">
                      <div className="flex items-center gap-2">
                        <span className="material-symbols-outlined text-primary text-[20px]">alt_route</span>
                        <span className="font-label-md text-label-md font-semibold text-on-surface">{ev.severity} Severity</span>
                      </div>
                    </div>
                    <p className="font-body-sm text-body-sm text-on-surface-variant mb-space-sm">
                      {ev.description}
                    </p>
                  </div>
                ))}
              </div>
            ) : (
              <div className="text-outline font-body-sm text-center py-4">No detailed evidence available for this pair.</div>
            )}
          </div>
        </div>
      </div>
      
      <AiAnalysisModal 
        isOpen={aiModalOpen}
        onClose={() => setAiModalOpen(false)}
        title="AI Similarity Explanation"
        status={aiStatus}
        data={aiData}
        errorMessage={aiError}
      />
    </div>
  );
}
