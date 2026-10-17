'use client';

import React, { useState } from 'react';
import { usePlagiarismComparison, usePlagiarismDetail, useAdjudicatePlagiarismPair } from '../hooks';
import { AxiosError } from 'axios';
import { AppLink as Link } from '@/components/ui/app-link';
import { AiAnalysisModal } from './AiAnalysisModal';
import { plagiarismAiApi, AiAnalysisResponse } from '../api/plagiarism-ai.api';
import { 
  ArrowLeft, 
  Sparkles, 
  AlertCircle, 
  Lock, 
  CheckCircle2, 
  Cpu, 
  Info, 
  Scale, 
  Bookmark 
} from 'lucide-react';
import { toast } from 'sonner';

export function TeacherPlagiarismCompareView({ recordId }: { recordId: string }) {
  const { data, isLoading, isError, error } = usePlagiarismComparison(recordId);
  const { data: detailData } = usePlagiarismDetail(recordId);
  const adjudicateMutation = useAdjudicatePlagiarismPair(recordId);

  // AI Modal State
  const [aiModalOpen, setAiModalOpen] = useState(false);
  const [aiStatus, setAiStatus] = useState<'idle' | 'loading' | 'success' | 'error'>('idle');
  const [aiData, setAiData] = useState<AiAnalysisResponse | null>(null);
  const [aiError, setAiError] = useState<string | undefined>();

  // Faculty Adjudication State - user overrides take precedence over server-loaded data
  const [selectedStatus, setSelectedStatus] = useState<'PENDING' | 'FLAG_CONFIRMED' | 'FALSE_POSITIVE' | 'HEARING_SCHEDULED' | null>(null);
  const [customNotes, setCustomNotes] = useState<string | null>(null);
  const [isSaved, setIsSaved] = useState(false);

  const serverStatus = data?.adjudicationStatus || detailData?.pair?.adjudicationStatus;
  const adjudicationStatus = selectedStatus ?? (serverStatus && serverStatus !== 'PENDING' ? serverStatus : 'PENDING');

  const serverNotes = data?.adjudicationNotes ?? detailData?.pair?.adjudicationNotes;
  const facultyNotes = customNotes ?? (serverNotes || '');

  const isAdjudicationRecorded = isSaved || Boolean(data?.adjudicatedAt || detailData?.pair?.adjudicatedAt);

  const handleSaveAdjudication = async () => {
    try {
      await adjudicateMutation.mutateAsync({
        status: adjudicationStatus,
        notes: facultyNotes,
      });
      setIsSaved(true);
      toast.success('Faculty adjudication decision successfully persisted to dossier.');
    } catch (err: unknown) {
      const msg = (err instanceof Error ? err.message : 'Unknown error') || 'Failed to persist adjudication';
      toast.error(msg);
    }
  };

  const handleRunAiCodeReview = async (evidenceId: string) => {
    setAiModalOpen(true);
    setAiStatus('loading');
    setAiError(undefined);
    try {
      const response = await plagiarismAiApi.getCodeReview(recordId, evidenceId);
      setAiData(response);
      setAiStatus('success');
      toast.success('AI Forensic analysis synthesized successfully');
    } catch (err: unknown) {
      setAiStatus('error');
      const msg = (err instanceof Error ? err.message : 'Unknown error') || 'Failed to fetch AI code review';
      setAiError(msg);
      toast.error(msg);
    }
  };

  if (isLoading) {
    return (
      <div className="flex flex-col h-72 items-center justify-center gap-3">
        <div className="w-8 h-8 rounded-full border-2 border-primary border-t-transparent animate-spin" />
        <p className="text-on-surface-variant font-label-md text-xs">Loading AST syntax diff engine...</p>
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
          {status === 403 ? 'Access Forbidden' : 'Diff Unavailable'}
        </h2>
        <p className="font-body-md text-on-surface-variant text-xs">
          {status === 403 
            ? 'You do not have permission to view this AST comparison pair.'
            : (error as Error).message}
        </p>
        <Link href="/teacher/plagiarism" className="mt-2 px-4 py-2 bg-primary text-on-primary rounded-lg text-xs font-medium">
          Back to Plagiarism Hub
        </Link>
      </div>
    );
  }

  if (!data) return null;

  return (
    <>
      <div className="flex-1 flex flex-col min-h-0 overflow-y-auto bg-surface">
        <div className="max-w-7xl mx-auto px-4 sm:px-space-lg py-space-lg w-full">
          <div className="flex flex-col w-full space-y-space-lg">
            {/* Header & Breadcrumb */}
            <div className="flex flex-col md:flex-row md:items-center justify-between gap-space-sm pb-space-sm border-b border-outline-variant/30">
              <div className="flex flex-col space-y-1">
                <nav className="flex items-center gap-2 text-on-surface-variant text-xs">
                  <Link className="hover:text-primary transition-colors flex items-center gap-1 font-medium" href="/teacher/plagiarism">
                    <ArrowLeft className="w-3.5 h-3.5" />
                    <span>Plagiarism Matrix</span>
                  </Link>
                  <span className="text-outline">/</span>
                  <span className="text-primary font-semibold">Pair Comparison</span>
                </nav>
                <h1 className="font-headline-lg text-headline-lg text-on-surface tracking-tight">
                  AST Structural Code Comparison
                </h1>
                <p className="text-xs text-on-surface-variant">
                  Assessment: <strong className="text-on-surface">{detailData?.examName || 'Exam'}</strong> &mdash; Comparison Record #{data.pairId}
                </p>
              </div>

              <div className="flex items-center gap-2 self-start md:self-auto">
                <span className="text-xs px-2.5 py-1 rounded-full bg-surface-container border border-outline-variant/40 text-on-surface font-semibold flex items-center gap-1.5">
                  <Cpu className="w-3.5 h-3.5 text-primary" />
                  <span>AST Engine v4.8</span>
                </span>
                <button
                  onClick={() => handleRunAiCodeReview(data.matches?.[0]?.type || 'CLUSTER_MATCH')}
                  className="px-3.5 py-1.5 rounded-lg bg-purple-50 hover:bg-purple-100 text-purple-800 border border-purple-200 text-xs font-semibold flex items-center gap-1.5 transition-all shadow-xs cursor-pointer"
                >
                  <Sparkles className="w-3.5 h-3.5 text-purple-600" />
                  <span>AI Structural Audit</span>
                </button>
              </div>
            </div>

            {/* Prominent Academic Rigor & Neutral Evidentiary Notice */}
            <div className="bg-surface-container-low rounded-2xl p-4 sm:p-5 border border-outline-variant/30 shadow-xs flex items-start gap-4">
              <div className="p-2.5 rounded-xl bg-surface-container-lowest text-primary shadow-xs shrink-0 mt-0.5 border border-outline-variant/30">
                <Info className="w-5 h-5 text-secondary" />
              </div>
              <div className="flex flex-col gap-1 min-w-0">
                <div className="flex items-center gap-2 flex-wrap">
                  <span className="font-title-md text-title-md text-on-surface font-bold">
                    Evidentiary Standard Notice (Thông báo Tham vấn Học thuật)
                  </span>
                  <span className="font-mono text-[10px] bg-surface-container-high px-2 py-0.5 rounded font-semibold text-on-surface">
                    ISO-6820 AST Spec
                  </span>
                </div>
                <p className="text-xs text-on-surface-variant leading-relaxed">
                  &ldquo;<strong>Similarity indicates structural or textual overlap between submissions. It is a review signal, not proof of plagiarism.</strong>&rdquo;
                </p>
                <p className="text-xs text-on-surface-variant/90 leading-relaxed italic border-t border-outline-variant/20 pt-1 mt-0.5">
                  &ldquo;Độ tương đồng phản ánh sự trùng lặp cấu trúc hoặc cú pháp AST giữa các bài nộp. Đây là tín hiệu tham vấn học thuật dành cho giảng viên đánh giá, không phải bằng chứng khẳng định gian lận.&rdquo;
                </p>
              </div>
            </div>

            {/* Matching Sub-tree Evidence List */}
            <div className="flex flex-col space-y-space-xs">
              <div className="flex items-center justify-between">
                <h2 className="font-headline-sm text-headline-sm text-on-surface">Matching Sub-tree Evidence</h2>
                <span className="text-xs text-outline font-mono">{data.matches?.length || 0} isomorphic nodes</span>
              </div>

              <div className="space-y-2">
                {data.matches && data.matches.length > 0 ? (
                  data.matches.map((match, idx) => (
                    <div 
                      key={idx} 
                      className="rounded-xl bg-surface-container-lowest p-space-md border border-outline-variant/40 shadow-xs flex flex-col sm:flex-row sm:items-center justify-between gap-3 hover:border-primary/40 transition-all"
                    >
                      <div className="flex flex-col">
                        <span className="font-mono text-xs font-bold text-primary">{match.type || 'AST Subtree Equivalence'}</span>
                        <div className="flex items-center gap-4 text-xs text-on-surface-variant font-mono mt-1">
                          <span>Student A: Lines {match.startLineA}&ndash;{match.endLineA}</span>
                          <span>&bull;</span>
                          <span>Student B: Lines {match.startLineB}&ndash;{match.endLineB}</span>
                        </div>
                      </div>

                      <button
                        onClick={() => handleRunAiCodeReview(`evidence-${idx}`)}
                        className="px-2.5 py-1 rounded bg-surface-container hover:bg-surface-container-high text-xs text-primary font-medium flex items-center gap-1 self-start sm:self-center transition-colors cursor-pointer"
                      >
                        <Sparkles className="w-3 h-3 text-purple-600" />
                        <span>Inspect Match AI</span>
                      </button>
                    </div>
                  ))
                ) : (
                  <p className="text-xs text-on-surface-variant p-4 rounded-xl bg-surface-container-lowest border border-outline-variant/30 text-center">
                    No structural match fragments detected in this comparison.
                  </p>
                )}
              </div>
            </div>

            {/* Side by side code viewer */}
            <div className="flex flex-col space-y-space-xs">
              <div>
                <h2 className="font-headline-md text-headline-md text-on-surface">Dual Code Viewer</h2>
                <p className="text-xs text-on-surface-variant">Side-by-side view with AST sub-tree duplication highlighting</p>
              </div>

              <div className="grid grid-cols-1 lg:grid-cols-2 gap-space-md w-full">
                {/* Code Window A */}
                <div className="flex flex-col border border-outline-variant/40 rounded-xl overflow-hidden shadow-xs h-[560px]">
                  <div className="bg-slate-900 px-4 py-2 flex items-center justify-between border-b border-slate-800 text-xs font-mono text-slate-300">
                    <div className="flex items-center gap-2">
                      <span className="w-2.5 h-2.5 rounded-full bg-blue-500"></span>
                      <span className="font-semibold">{data.fileA || 'Student A Code (Author A)'}</span>
                    </div>
                    <span className="text-[11px] text-slate-500">Java</span>
                  </div>

                  <div className="bg-slate-950 text-slate-100 p-4 overflow-x-auto overflow-y-auto select-text font-mono text-xs leading-relaxed flex-1">
                    {data.sourceCodeA ? data.sourceCodeA.split('\n').map((line, idx) => {
                      const isMatch = data.matches?.some(m => (idx + 1) >= m.startLineA && (idx + 1) <= m.endLineA);
                      return (
                        <div 
                          key={idx} 
                          className={isMatch ? "bg-rose-950/60 -mx-4 px-4 border-l-2 border-rose-500 text-rose-100" : ""}
                        >
                          <span className="text-slate-600 mr-3 inline-block w-6 text-right select-none">{idx + 1}</span>
                          {line}
                        </div>
                      );
                    }) : <div className="text-slate-500 italic">No source code provided.</div>}
                  </div>
                </div>
                
                {/* Code Window B */}
                <div className="flex flex-col border border-outline-variant/40 rounded-xl overflow-hidden shadow-xs h-[560px]">
                  <div className="bg-slate-900 px-4 py-2 flex items-center justify-between border-b border-slate-800 text-xs font-mono text-slate-300">
                    <div className="flex items-center gap-2">
                      <span className="w-2.5 h-2.5 rounded-full bg-sky-500"></span>
                      <span className="font-semibold">{data.fileB || 'Student B Code (Author B)'}</span>
                    </div>
                    <span className="text-[11px] text-slate-500">Java</span>
                  </div>

                  <div className="bg-slate-950 text-slate-100 p-4 overflow-x-auto overflow-y-auto select-text font-mono text-xs leading-relaxed flex-1">
                    {data.sourceCodeB ? data.sourceCodeB.split('\n').map((line, idx) => {
                      const isMatch = data.matches?.some(m => (idx + 1) >= m.startLineB && (idx + 1) <= m.endLineB);
                      return (
                        <div 
                          key={idx} 
                          className={isMatch ? "bg-rose-950/60 -mx-4 px-4 border-l-2 border-rose-500 text-rose-100" : ""}
                        >
                          <span className="text-slate-600 mr-3 inline-block w-6 text-right select-none">{idx + 1}</span>
                          {line}
                        </div>
                      );
                    }) : <div className="text-slate-500 italic">No source code provided.</div>}
                  </div>
                </div>
              </div>
            </div>

            {/* Faculty Adjudication Action Box */}
            <div className="bg-surface-container-lowest rounded-2xl border border-outline-variant/40 p-5 shadow-xs flex flex-col gap-4">
              <div className="flex items-center justify-between border-b border-outline-variant/20 pb-3">
                <div className="flex items-center gap-2">
                  <Scale className="w-4 h-4 text-primary" />
                  <h3 className="font-headline-sm text-headline-sm text-on-surface font-semibold">
                    Faculty Adjudication Protocol
                  </h3>
                </div>
                {isAdjudicationRecorded && (
                  <span className="text-[10px] px-2.5 py-0.5 rounded-full bg-emerald-50 text-emerald-700 border border-emerald-200 font-bold flex items-center gap-1">
                    <CheckCircle2 className="w-3 h-3" />
                    <span>Adjudication Recorded</span>
                    {(data?.adjudicatedBy || detailData?.pair?.adjudicatedBy) && (
                      <span className="text-emerald-600 font-normal ml-1">
                        ({data?.adjudicatedBy || detailData?.pair?.adjudicatedBy})
                      </span>
                    )}
                  </span>
                )}
              </div>

              <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                <div className="flex flex-col gap-1.5">
                  <label className="text-xs font-semibold text-on-surface">Preliminary Decision:</label>
                  <select
                    value={adjudicationStatus}
                    onChange={(e) => setSelectedStatus(e.target.value as 'PENDING' | 'FLAG_CONFIRMED' | 'FALSE_POSITIVE' | 'HEARING_SCHEDULED')}
                    className="w-full px-3 py-2 text-xs bg-surface-container-low border border-outline-variant/40 rounded-lg text-on-surface font-medium focus:outline-none focus:ring-1 focus:ring-primary shadow-xs cursor-pointer"
                  >
                    <option value="PENDING">Under Faculty Review (Pending)</option>
                    <option value="FALSE_POSITIVE">False Positive (Instructor Scaffold / Common Boilerplate)</option>
                    <option value="HEARING_SCHEDULED">Academic Inquiry (Schedule Student Hearing)</option>
                    <option value="FLAG_CONFIRMED">Flag Confirmed (Substantial Unattributed Congruence)</option>
                  </select>
                </div>

                <div className="md:col-span-2 flex flex-col gap-1.5">
                  <label className="text-xs font-semibold text-on-surface">Faculty Review Notes &amp; Rationales:</label>
                  <div className="flex gap-2">
                    <input
                      type="text"
                      placeholder="e.g. Scrutinized loop structures and helper functions; shared code matches lab skeleton..."
                      value={facultyNotes}
                      onChange={(e) => setCustomNotes(e.target.value)}
                      className="flex-1 px-3 py-2 text-xs bg-surface-container-low border border-outline-variant/40 rounded-lg text-on-surface placeholder:text-outline focus:outline-none focus:ring-1 focus:ring-primary shadow-xs"
                    />
                    <button
                      onClick={handleSaveAdjudication}
                      disabled={adjudicateMutation.isPending}
                      className="px-4 py-2 bg-primary text-on-primary hover:bg-primary/90 disabled:opacity-50 disabled:cursor-not-allowed rounded-lg text-xs font-semibold shrink-0 shadow-xs transition-all cursor-pointer flex items-center gap-1.5"
                    >
                      {adjudicateMutation.isPending ? (
                        <>
                          <div className="w-3.5 h-3.5 border-2 border-on-primary border-t-transparent rounded-full animate-spin" />
                          <span>Saving...</span>
                        </>
                      ) : (
                        <>
                          <Bookmark className="w-3.5 h-3.5" />
                          <span>Record Review</span>
                        </>
                      )}
                    </button>
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
        title="AI Forensic Code Review Assistant"
        status={aiStatus}
        data={aiData}
        errorMessage={aiError}
      />
    </>
  );
}
