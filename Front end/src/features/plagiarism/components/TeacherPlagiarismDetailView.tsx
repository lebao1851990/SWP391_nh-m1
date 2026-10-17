'use client';

import React, { useState } from 'react';
import { usePlagiarismDetail } from '../hooks';
import { AxiosError } from 'axios';
import { AppLink as Link } from '@/components/ui/app-link';
import { AiAnalysisModal } from './AiAnalysisModal';
import { plagiarismAiApi, AiAnalysisResponse } from '../api/plagiarism-ai.api';
import { 
  ArrowLeft, 
  Sparkles, 
  ShieldAlert, 
  CheckCircle2, 
  GitCompare, 
  Lock, 
  AlertCircle, 
  ExternalLink,
  Loader2,
  FileCode2,
  Activity,
  Info
} from 'lucide-react';
import { toast } from 'sonner';

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
      toast.success('AI Similarity explanation generated successfully');
    } catch (err: unknown) {
      setAiStatus('error');
      const msg = (err instanceof Error ? err.message : 'Unknown error') || 'Failed to fetch AI explanation';
      setAiError(msg);
      toast.error(msg);
    }
  };

  if (isLoading) {
    return (
      <div className="flex flex-col h-72 items-center justify-center gap-3">
        <div className="w-8 h-8 rounded-full border-2 border-primary border-t-transparent animate-spin" />
        <p className="text-on-surface-variant font-label-md text-xs">Loading case dossier...</p>
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
          {status === 403 ? 'Access Restricted' : 'Record Not Found'}
        </h2>
        <p className="font-body-md text-on-surface-variant text-xs">
          {status === 403 
            ? 'You do not have permission to access this plagiarism record.'
            : (error as Error).message}
        </p>
        <Link href="/teacher/plagiarism" className="mt-2 px-4 py-2 bg-primary text-on-primary rounded-lg text-xs font-medium">
          Back to Plagiarism Hub
        </Link>
      </div>
    );
  }

  if (!data) return null;

  const { pair, examName, className, evidence } = data;

  return (
    <div className="flex-1 flex flex-col min-h-0 overflow-y-auto bg-surface">
      <div className="max-w-7xl mx-auto px-space-lg py-space-lg w-full">
        <div className="flex flex-col w-full space-y-space-lg">
          {/* Header & Navigation */}
          <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-space-md border-b border-outline-variant/30 pb-space-md">
            <div className="space-y-1">
              <nav className="flex items-center gap-2 text-on-surface-variant text-xs mb-1">
                <Link className="hover:text-primary transition-colors flex items-center gap-1 font-medium" href="/teacher/plagiarism">
                  <ArrowLeft className="w-3.5 h-3.5" />
                  <span>Plagiarism Matrix</span>
                </Link>
                <span className="text-outline">/</span>
                <span className="text-primary font-semibold">Case #{recordId.substring(0, 8).toUpperCase()}</span>
              </nav>
              <div className="flex items-center gap-space-xs flex-wrap">
                <span className="font-mono text-xs px-2.5 py-0.5 rounded-full bg-blue-50 text-blue-700 border border-blue-200 font-bold">
                  CASE #{recordId.substring(0, 8).toUpperCase()}
                </span>
                <span className="text-xs text-on-surface-variant">• {examName} • {className}</span>
              </div>
              <h1 className="font-headline-lg text-headline-lg text-on-surface tracking-tight mt-1">
                Similarity Dossier Analysis
              </h1>
              <p className="text-xs text-on-surface-variant">
                Syntactic tree comparison between <strong className="text-on-surface">{pair.studentAName || pair.studentAId}</strong> and{' '}
                <strong className="text-on-surface">{pair.studentBName || pair.studentBId}</strong>.
              </p>
            </div>
            
            <div className="flex items-center gap-2 self-start md:self-auto">
              <button 
                onClick={handleRunAiAnalysis}
                className="px-3.5 py-2 rounded-lg bg-purple-50 hover:bg-purple-100 text-purple-800 border border-purple-200 text-xs font-semibold flex items-center gap-1.5 transition-all shadow-xs cursor-pointer"
              >
                <Sparkles className="w-3.5 h-3.5 text-purple-600" />
                <span>AI Similarity Explanation</span>
              </button>

              <Link 
                href={`/teacher/plagiarism/${recordId}/compare`}
                className="px-4 py-2 rounded-lg bg-primary hover:bg-primary/90 text-on-primary text-xs font-medium flex items-center gap-1.5 shadow-sm transition-all"
              >
                <GitCompare className="w-3.5 h-3.5" />
                <span>Open Dual Code Comparison</span>
              </Link>
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
                  Academic Rigor &amp; Evidentiary Standard Notice
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

          {/* Pair Metric Grid */}
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-space-md">
            {/* Visual AST Diagram Card */}
            <div className="lg:col-span-8 p-space-md rounded-2xl bg-surface-container-lowest border border-outline-variant/40 shadow-xs flex flex-col justify-between">
              <div>
                <div className="flex items-center justify-between mb-space-sm pb-2 border-b border-outline-variant/20">
                  <div className="flex items-center gap-1.5">
                    <Activity className="w-4 h-4 text-primary" />
                    <h3 className="font-headline-sm text-headline-sm text-on-surface font-semibold">AST Fingerprint Interlink</h3>
                  </div>
                  {pair.isFlagged && (
                    <span className="text-[10px] px-2 py-0.5 rounded-full bg-rose-50 text-rose-700 border border-rose-200 font-bold">
                      Critical Threshold Exceeded
                    </span>
                  )}
                </div>

                <p className="text-xs text-on-surface-variant">
                  Comparison between <strong className="text-on-surface">{pair.studentAId}</strong> and <strong className="text-on-surface">{pair.studentBId}</strong> in cohort <span className="font-mono bg-surface-container px-1 py-0.5 rounded text-[11px]">{className}</span>.
                </p>
                
                {/* SVG Visual Diagram */}
                <div className="mt-space-md h-44 bg-surface-container-low rounded-xl relative overflow-hidden flex items-center justify-center p-4 border border-outline-variant/20">
                  <svg className="w-full h-full" fill="none" viewBox="0 0 600 160" xmlns="http://www.w3.org/2000/svg">
                    <line stroke={pair.isFlagged ? "#dc2626" : "#2563eb"} strokeDasharray="5 3" strokeWidth="2" x1="180" x2="420" y1="80" y2="80"></line>
                    <rect fill={pair.isFlagged ? "#fee2e2" : "#dbeafe"} height="24" rx="6" width="60" x="270" y="68"></rect>
                    <text fill={pair.isFlagged ? "#991b1b" : "#1e40af"} fontFamily="JetBrains Mono" fontSize="12" fontWeight="700" textAnchor="middle" x="300" y="84">{pair.similarityRate}% Match</text>
                    
                    <g>
                      <circle cx="180" cy="80" fill="#2563eb" r="26"></circle>
                      <text fill="#ffffff" fontFamily="Inter" fontSize="11" fontWeight="700" textAnchor="middle" x="180" y="84">A</text>
                      <text fill="#0f172a" fontFamily="JetBrains Mono" fontSize="11" fontWeight="600" textAnchor="middle" x="180" y="125">{pair.studentAName || 'Student A'}</text>
                    </g>
                    
                    <g>
                      <circle cx="420" cy="80" fill="#0284c7" r="26"></circle>
                      <text fill="#ffffff" fontFamily="Inter" fontSize="11" fontWeight="700" textAnchor="middle" x="420" y="84">B</text>
                      <text fill="#0f172a" fontFamily="JetBrains Mono" fontSize="11" fontWeight="600" textAnchor="middle" x="420" y="125">{pair.studentBName || 'Student B'}</text>
                    </g>
                  </svg>
                </div>
              </div>
            </div>
            
            {/* Statistics Card */}
            <div className="lg:col-span-4 p-space-md rounded-2xl bg-surface-container-lowest border border-outline-variant/40 shadow-xs flex flex-col justify-between space-y-3">
              <div>
                <div className="flex items-center justify-between pb-2 border-b border-outline-variant/20 mb-2">
                  <span className="font-label-xs uppercase tracking-wider text-outline font-semibold">Evidence Metrics</span>
                  <span className="text-[10px] px-1.5 py-0.5 rounded font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200">
                    VERIFIED
                  </span>
                </div>
                <p className="text-xs text-on-surface-variant">Metrics derived from tokenized AST Winnowing &amp; Jaccard index.</p>
                <div className="mt-3 space-y-2 text-xs">
                  <div className="p-2.5 rounded-lg bg-surface-container-low flex items-center justify-between border border-outline-variant/20">
                    <span className="text-on-surface-variant">Structural Equivalence</span>
                    <span className={`font-bold font-mono ${pair.isFlagged ? 'text-rose-600' : 'text-on-surface'}`}>{pair.similarityRate}%</span>
                  </div>
                  <div className="p-2.5 rounded-lg bg-surface-container-low flex items-center justify-between border border-outline-variant/20">
                    <span className="text-on-surface-variant">Classification</span>
                    <span className={`font-semibold font-mono ${pair.isFlagged ? 'text-rose-600' : 'text-on-surface'}`}>{pair.isFlagged ? 'FLAGGED' : 'MODERATE'}</span>
                  </div>
                  <div className="p-2.5 rounded-lg bg-surface-container-low flex items-center justify-between border border-outline-variant/20">
                    <span className="text-on-surface-variant">Evidence Items</span>
                    <span className="font-mono font-bold text-on-surface">{evidence?.length || 0}</span>
                  </div>
                </div>
              </div>

              <Link
                href={`/teacher/plagiarism/${recordId}/compare`}
                className="w-full py-2 bg-surface-container hover:bg-surface-container-high text-primary rounded-lg text-xs font-semibold flex items-center justify-center gap-1.5 transition-colors border border-outline-variant/30"
              >
                <span>Examine Side-by-Side Diff</span>
                <ExternalLink className="w-3.5 h-3.5" />
              </Link>
            </div>
          </div>

          {/* Evidence Details Cards */}
          <div className="bg-surface-container-lowest p-space-lg rounded-2xl border border-outline-variant/40 shadow-xs">
            <h3 className="font-headline-sm text-headline-sm text-on-surface mb-space-sm font-semibold">
              Detected Evidence Fragments ({evidence?.length || 0})
            </h3>
            {evidence && evidence.length > 0 ? (
              <div className="space-y-2">
                {evidence.map((ev) => (
                  <div key={ev.evidenceId} className="bg-surface-container-low p-space-md rounded-xl border border-outline-variant/30 hover:border-primary/30 transition-all text-xs">
                    <div className="flex items-start justify-between mb-1.5">
                      <span className={`font-semibold px-2 py-0.5 rounded-full text-[10px] uppercase ${
                        ev.severity === 'high' 
                          ? 'bg-rose-50 text-rose-700 border border-rose-200' 
                          : 'bg-amber-50 text-amber-700 border border-amber-200'
                      }`}>
                        {ev.severity} Severity
                      </span>
                    </div>
                    <p className="text-on-surface-variant leading-relaxed">
                      {ev.description}
                    </p>
                  </div>
                ))}
              </div>
            ) : (
              <div className="text-outline text-xs text-center py-6">No detailed evidence fragments logged for this pair.</div>
            )}
          </div>
        </div>
      </div>
      
      <AiAnalysisModal 
        isOpen={aiModalOpen}
        onClose={() => setAiModalOpen(false)}
        title="AI Similarity Forensic Explanation"
        status={aiStatus}
        data={aiData}
        errorMessage={aiError}
      />
    </div>
  );
}
