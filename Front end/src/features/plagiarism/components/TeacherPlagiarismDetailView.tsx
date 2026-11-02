'use client';

import React, { useState } from 'react';
import { usePlagiarismDetail } from '../hooks';
import { AxiosError } from 'axios';
import { AppLink as Link } from '@/components/ui/app-link';
import { AiAnalysisModal } from './AiAnalysisModal';
import { AstStructuralComparison } from './AstStructuralComparison';
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
  Info,
  ArrowDown,
  Layers
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
      toast.success('Đã tạo giải thích tương đồng AI thành công');
    } catch (err: unknown) {
      setAiStatus('error');
      const msg = (err instanceof Error ? err.message : 'Unknown error') || 'Không thể lấy giải thích từ AI';
      setAiError(msg);
      toast.error(msg);
    }
  };

  if (isLoading) {
    return (
      <div className="flex flex-col h-72 items-center justify-center gap-3">
        <div className="w-8 h-8 rounded-full border-2 border-primary border-t-transparent animate-spin" />
        <p className="text-on-surface-variant font-label-md text-xs">Đang tải hồ sơ vụ việc...</p>
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
          {status === 403 ? 'Quyền truy cập bị hạn chế' : 'Không tìm thấy bản ghi'}
        </h2>
        <p className="font-body-md text-on-surface-variant text-xs">
          {status === 403 
            ? 'Bạn không có quyền truy cập bản ghi tương đồng này.'
            : (error as Error).message}
        </p>
        <Link href="/teacher/plagiarism" className="mt-2 px-4 py-2 bg-primary text-on-primary rounded-lg text-xs font-medium">
          Quay lại Trung tâm Kiểm tra Tương đồng
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
                  <span>Ma trận Tương đồng</span>
                </Link>
                <span className="text-outline">/</span>
                <span className="text-primary font-semibold">Hồ sơ #{recordId.substring(0, 8).toUpperCase()}</span>
              </nav>
              <div className="flex items-center gap-space-xs flex-wrap">
                <span className="font-mono text-xs px-2.5 py-0.5 rounded-full bg-blue-50 text-blue-700 border border-blue-200 font-bold">
                  HỒ SƠ #{recordId.substring(0, 8).toUpperCase()}
                </span>
                <span className="text-xs text-on-surface-variant">• {examName} • {className}</span>
              </div>
              <h1 className="font-headline-lg text-headline-lg text-on-surface tracking-tight mt-1">
                Phân tích Hồ sơ Tương đồng
              </h1>
              <p className="text-xs text-on-surface-variant">
                So sánh đối chiếu giữa <strong className="text-on-surface">{pair.studentAName || 'Sinh viên A'}</strong> và{' '}
                <strong className="text-on-surface">{pair.studentBName || 'Sinh viên B'}</strong>.
              </p>
              <div className="flex items-center gap-2 text-[11px] text-on-surface-variant font-mono mt-0.5">
                <span title={`Mã sinh viên A: ${pair.studentAId} | Mã bài nộp A: ${pair.submissionAId}`}>
                  Bài A: #{pair.submissionAId ? pair.submissionAId.substring(0, 8) : 'A'}
                </span>
                <span>•</span>
                <span title={`Mã sinh viên B: ${pair.studentBId} | Mã bài nộp B: ${pair.submissionBId}`}>
                  Bài B: #{pair.submissionBId ? pair.submissionBId.substring(0, 8) : 'B'}
                </span>
              </div>
            </div>
            
            <div className="flex items-center gap-2 self-start md:self-auto">
              <button 
                onClick={handleRunAiAnalysis}
                className="px-3.5 py-2 rounded-lg bg-purple-50 hover:bg-purple-100 text-purple-800 border border-purple-200 text-xs font-semibold flex items-center gap-1.5 transition-all shadow-xs cursor-pointer"
              >
                <Sparkles className="w-3.5 h-3.5 text-purple-600" />
                <span>Giải thích Tương đồng từ AI</span>
              </button>

              <Link 
                href={`/teacher/plagiarism/${recordId}/compare`}
                className="px-4 py-2 rounded-lg bg-primary hover:bg-primary/90 text-on-primary text-xs font-medium flex items-center gap-1.5 shadow-sm transition-all"
              >
                <GitCompare className="w-3.5 h-3.5" />
                <span>Mở so sánh song song mã nguồn</span>
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
                  Lưu ý về Tính nghiêm cẩn học thuật &amp; Đánh giá chứng cứ
                </span>
                <span className="font-mono text-[10px] bg-surface-container-high px-2 py-0.5 rounded font-semibold text-on-surface">
                  TIÊU CHUẨN AST
                </span>
              </div>
              <p className="text-xs text-on-surface-variant leading-relaxed">
                &ldquo;<strong>Độ tương đồng phản ánh sự trùng lặp cấu trúc hoặc cú pháp AST giữa các bài nộp. Đây là tín hiệu tham vấn học thuật dành cho giảng viên đánh giá, không phải bằng chứng khẳng định gian lận.</strong>&rdquo;
              </p>
            </div>
          </div>

          {/* Pair Metric Grid */}
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-space-md">
            {/* Visual Relationship Diagram Card */}
            <div className="lg:col-span-8 p-space-md rounded-2xl bg-surface-container-lowest border border-outline-variant/40 shadow-xs flex flex-col justify-between">
              <div>
                <div className="flex items-center justify-between mb-space-sm pb-2 border-b border-outline-variant/20">
                  <div className="flex items-center gap-1.5">
                    <Activity className="w-4 h-4 text-primary" />
                    <h3 className="font-headline-sm text-headline-sm text-on-surface font-semibold">
                      Sơ đồ Quan hệ Tương đồng Giữa hai Bài nộp
                    </h3>
                  </div>
                  {pair.isFlagged ? (
                    <span className="inline-flex items-center gap-1.5 text-[11px] px-2.5 py-1 rounded-full bg-rose-50 dark:bg-rose-950/50 text-rose-700 dark:text-rose-300 border border-rose-200 dark:border-rose-800 font-bold shadow-xs">
                      <ShieldAlert className="w-3.5 h-3.5 text-rose-600 dark:text-rose-400" />
                      Vượt ngưỡng cảnh báo ({pair.similarityRate}%)
                    </span>
                  ) : (
                    <span className="inline-flex items-center gap-1.5 text-[11px] px-2.5 py-1 rounded-full bg-emerald-50 dark:bg-emerald-950/50 text-emerald-700 dark:text-emerald-300 border border-emerald-200 dark:border-emerald-800 font-bold shadow-xs">
                      <CheckCircle2 className="w-3.5 h-3.5 text-emerald-600 dark:text-emerald-400" />
                      Dưới ngưỡng cảnh báo ({pair.similarityRate}%)
                    </span>
                  )}
                </div>

                <p className="text-xs text-on-surface-variant leading-relaxed">
                  Mô hình hóa mức độ tương đồng giữa bài nộp của <strong className="text-on-surface">{pair.studentAName || 'Sinh viên A'}</strong> và <strong className="text-on-surface">{pair.studentBName || 'Sinh viên B'}</strong> thuộc lớp <span className="font-mono bg-surface-container px-1.5 py-0.5 rounded text-[11px] text-on-surface font-medium">{className}</span> dựa trên đối chiếu tập dấu vân tay AST (Winnowing).
                </p>
                
                {/* SVG Visual Relationship Diagram */}
                <div 
                  className="mt-space-md h-48 bg-surface-container-low rounded-xl relative overflow-hidden flex items-center justify-center p-3 border border-outline-variant/20"
                  role="region"
                  aria-label="Khu vực sơ đồ trực quan quan hệ tương đồng giữa hai bài nộp"
                >
                  <svg 
                    className="w-full h-auto max-h-44 select-none" 
                    fill="none" 
                    viewBox="0 0 680 180" 
                    xmlns="http://www.w3.org/2000/svg"
                    role="img"
                    aria-label={`Sơ đồ tương quan giữa ${pair.studentAName || 'Sinh viên A'} và ${pair.studentBName || 'Sinh viên B'}, mức độ tương đồng ${pair.similarityRate}%, ${pair.isFlagged ? 'vượt ngưỡng cảnh báo' : 'dưới ngưỡng cảnh báo'}.`}
                  >
                    <title>
                      {`So sánh ${pair.studentAName || 'Sinh viên A'} vs ${pair.studentBName || 'Sinh viên B'} - Tương đồng: ${pair.similarityRate}%`}
                    </title>
                    
                    <defs>
                      <filter id="node-shadow" x="-20%" y="-20%" width="140%" height="140%">
                        <feDropShadow dx="0" dy="2" stdDeviation="3" floodOpacity="0.15" />
                      </filter>
                      <filter id="badge-shadow" x="-10%" y="-15%" width="120%" height="130%">
                        <feDropShadow dx="0" dy="2" stdDeviation="2.5" floodOpacity="0.1" />
                      </filter>
                    </defs>

                    {/* Connecting line - segmented so it never crosses the center badge */}
                    {/* Left segment: from Node A halo (x=172) to Badge left edge (x=265) */}
                    <line 
                      x1="172" 
                      y1="65" 
                      x2="265" 
                      y2="65" 
                      stroke={pair.isFlagged ? "#e11d48" : "#3b82f6"} 
                      strokeWidth="2" 
                      strokeDasharray="6 4" 
                    />
                    
                    {/* Right segment: from Badge right edge (x=415) to Node B halo (x=508) */}
                    <line 
                      x1="415" 
                      y1="65" 
                      x2="508" 
                      y2="65" 
                      stroke={pair.isFlagged ? "#e11d48" : "#3b82f6"} 
                      strokeWidth="2" 
                      strokeDasharray="6 4" 
                    />
                    
                    {/* Centered Similarity Rate Badge (width 150, centered at x=340) */}
                    <g filter="url(#badge-shadow)">
                      <rect 
                        x="265" 
                        y="43" 
                        width="150" 
                        height="44" 
                        rx="22" 
                        fill={pair.isFlagged ? "#fff1f2" : "#eff6ff"} 
                        stroke={pair.isFlagged ? "#f43f5e" : "#60a5fa"} 
                        strokeWidth="1.5" 
                      />
                      <text 
                        x="340" 
                        y="62" 
                        textAnchor="middle" 
                        fill={pair.isFlagged ? "#be123c" : "#1d4ed8"} 
                        fontFamily="JetBrains Mono, monospace" 
                        fontSize="14" 
                        fontWeight="800"
                      >
                        {pair.similarityRate}%
                      </text>
                      <text 
                        x="340" 
                        y="77" 
                        textAnchor="middle" 
                        fill={pair.isFlagged ? "#9f1239" : "#1e40af"} 
                        fontFamily="Inter, sans-serif" 
                        fontSize="9" 
                        fontWeight="700" 
                        letterSpacing="0.05em"
                      >
                        {pair.isFlagged ? 'TƯƠNG ĐỒNG CAO' : 'MỨC BÌNH THƯỜNG'}
                      </text>
                    </g>
                    
                    {/* Node A (Left) */}
                    <g className="cursor-default">
                      <circle cx="140" cy="65" r="32" fill="#dbeafe" fillOpacity="0.5" stroke="#93c5fd" strokeWidth="1" strokeDasharray="3 3" />
                      <circle cx="140" cy="65" r="26" fill="#2563eb" filter="url(#node-shadow)" />
                      <text x="140" y="70" textAnchor="middle" fill="#ffffff" fontFamily="Inter, sans-serif" fontSize="13" fontWeight="800">A</text>
                      
                      {/* Node A Student Name label with background pill for contrast */}
                      <text 
                        x="140" 
                        y="118" 
                        textAnchor="middle" 
                        fill="#0f172a" 
                        fontFamily="Inter, sans-serif" 
                        fontSize="12" 
                        fontWeight="700"
                      >
                        {(pair.studentAName && pair.studentAName.length > 22) 
                          ? `${pair.studentAName.substring(0, 20)}…` 
                          : (pair.studentAName || 'Sinh viên A')}
                      </text>
                      
                      {/* Node A Submission Tag */}
                      <text 
                        x="140" 
                        y="136" 
                        textAnchor="middle" 
                        fill="#64748b" 
                        fontFamily="JetBrains Mono, monospace" 
                        fontSize="10" 
                        fontWeight="500"
                      >
                        Bài #{pair.submissionAId ? pair.submissionAId.substring(0, 8) : 'A'}
                      </text>
                    </g>
                    
                    {/* Node B (Right) */}
                    <g className="cursor-default">
                      <circle cx="540" cy="65" r="32" fill="#e0f2fe" fillOpacity="0.5" stroke="#7dd3fc" strokeWidth="1" strokeDasharray="3 3" />
                      <circle cx="540" cy="65" r="26" fill="#0284c7" filter="url(#node-shadow)" />
                      <text x="540" y="70" textAnchor="middle" fill="#ffffff" fontFamily="Inter, sans-serif" fontSize="13" fontWeight="800">B</text>
                      
                      {/* Node B Student Name label with background pill for contrast */}
                      <text 
                        x="540" 
                        y="118" 
                        textAnchor="middle" 
                        fill="#0f172a" 
                        fontFamily="Inter, sans-serif" 
                        fontSize="12" 
                        fontWeight="700"
                      >
                        {(pair.studentBName && pair.studentBName.length > 22) 
                          ? `${pair.studentBName.substring(0, 20)}…` 
                          : (pair.studentBName || 'Sinh viên B')}
                      </text>
                      
                      {/* Node B Submission Tag */}
                      <text 
                        x="540" 
                        y="136" 
                        textAnchor="middle" 
                        fill="#64748b" 
                        fontFamily="JetBrains Mono, monospace" 
                        fontSize="10" 
                        fontWeight="500"
                      >
                        Bài #{pair.submissionBId ? pair.submissionBId.substring(0, 8) : 'B'}
                      </text>
                    </g>
                  </svg>
                </div>

                {/* Direct Action Navigation Paths */}
                <div className="flex flex-wrap items-center justify-between gap-3 pt-3 mt-3 border-t border-outline-variant/20">
                  <div className="flex items-center gap-1.5 text-xs text-on-surface-variant">
                    <FileCode2 className="w-4 h-4 text-primary shrink-0" />
                    <span>
                      Ghi nhận <strong>{evidence?.length || 0}</strong> đoạn cấu trúc AST trùng khớp.
                    </span>
                  </div>
                  <div className="flex items-center gap-2">
                    <a
                      href="#ast-structural-comparison"
                      className="px-3 py-1.5 rounded-lg bg-surface-container hover:bg-surface-container-high text-on-surface text-xs font-medium flex items-center gap-1.5 transition-colors border border-outline-variant/30"
                    >
                      <Layers className="w-3.5 h-3.5 text-primary" />
                      <span>Đối chiếu nút AST</span>
                    </a>
                    {evidence && evidence.length > 0 && (
                      <a
                        href="#evidence-list"
                        className="px-3 py-1.5 rounded-lg bg-surface-container hover:bg-surface-container-high text-on-surface text-xs font-medium flex items-center gap-1.5 transition-colors border border-outline-variant/30"
                      >
                        <span>Xem chứng cứ ({evidence.length})</span>
                        <ArrowDown className="w-3.5 h-3.5 text-outline" />
                      </a>
                    )}
                    <Link
                      href={`/teacher/plagiarism/${recordId}/compare`}
                      className="px-3.5 py-1.5 rounded-lg bg-primary hover:bg-primary/90 text-on-primary text-xs font-medium flex items-center gap-1.5 transition-colors shadow-xs"
                    >
                      <GitCompare className="w-3.5 h-3.5" />
                      <span>So sánh song song</span>
                    </Link>
                  </div>
                </div>
              </div>
            </div>
            
            {/* Statistics Card */}
            <div className="lg:col-span-4 p-space-md rounded-2xl bg-surface-container-lowest border border-outline-variant/40 shadow-xs flex flex-col justify-between space-y-3">
              <div>
                <div className="flex items-center justify-between pb-2 border-b border-outline-variant/20 mb-2">
                  <span className="font-label-xs uppercase tracking-wider text-outline font-semibold">Chỉ số Chứng cứ</span>
                  <span className="text-[10px] px-1.5 py-0.5 rounded font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200">
                    ĐÃ XÁC MINH
                  </span>
                </div>
                <p className="text-xs text-on-surface-variant">Chỉ số được tính toán dựa trên thuật toán Winnowing phân tích từ vựng AST &amp; hệ số Jaccard.</p>
                <div className="mt-3 space-y-2 text-xs">
                  <div className="p-2.5 rounded-lg bg-surface-container-low flex items-center justify-between border border-outline-variant/20">
                    <span className="text-on-surface-variant">Độ tương đồng cấu trúc</span>
                    <span className={`font-bold font-mono ${pair.isFlagged ? 'text-rose-600' : 'text-on-surface'}`}>{pair.similarityRate}%</span>
                  </div>
                  <div className="p-2.5 rounded-lg bg-surface-container-low flex items-center justify-between border border-outline-variant/20">
                    <span className="text-on-surface-variant">Phân loại</span>
                    <span className={`font-semibold font-mono ${pair.isFlagged ? 'text-rose-600' : 'text-on-surface'}`}>{pair.isFlagged ? 'CẢNH BÁO CAO' : 'TRUNG BÌNH'}</span>
                  </div>
                  <div className="p-2.5 rounded-lg bg-surface-container-low flex items-center justify-between border border-outline-variant/20">
                    <span className="text-on-surface-variant">Số lượng chứng cứ</span>
                    <span className="font-mono font-bold text-on-surface">{evidence?.length || 0}</span>
                  </div>
                </div>
              </div>

              <Link
                href={`/teacher/plagiarism/${recordId}/compare`}
                className="w-full py-2 bg-surface-container hover:bg-surface-container-high text-primary rounded-lg text-xs font-semibold flex items-center justify-center gap-1.5 transition-colors border border-outline-variant/30"
              >
                <span>Kiểm tra chi tiết so sánh song song</span>
                <ExternalLink className="w-3.5 h-3.5" />
              </Link>
            </div>
          </div>

          {/* AST Structural Comparison & Matched Node Flow */}
          <div id="ast-structural-comparison" className="scroll-mt-6">
            <AstStructuralComparison
              recordId={recordId}
              studentAName={pair.studentAName}
              studentBName={pair.studentBName}
              submissionAId={pair.submissionAId}
              submissionBId={pair.submissionBId}
            />
          </div>

          {/* Evidence Details Cards */}
          <div id="evidence-list" className="bg-surface-container-lowest p-space-lg rounded-2xl border border-outline-variant/40 shadow-xs scroll-mt-6">
            <h3 className="font-headline-sm text-headline-sm text-on-surface mb-space-sm font-semibold">
              Các đoạn chứng cứ phát hiện được ({evidence?.length || 0})
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
                        Mức độ {ev.severity === 'high' ? 'Cao' : 'Trung bình'}
                      </span>
                    </div>
                    <p className="text-on-surface-variant leading-relaxed">
                      {ev.description}
                    </p>
                  </div>
                ))}
              </div>
            ) : (
              <div className="text-outline text-xs text-center py-6">Không có đoạn chứng cứ chi tiết nào được ghi nhận cho cặp bài này.</div>
            )}
          </div>
        </div>
      </div>
      
      <AiAnalysisModal 
        isOpen={aiModalOpen}
        onClose={() => setAiModalOpen(false)}
        title="Giải thích Giám định Tương đồng bằng AI"
        status={aiStatus}
        data={aiData}
        errorMessage={aiError}
      />
    </div>
  );
}
