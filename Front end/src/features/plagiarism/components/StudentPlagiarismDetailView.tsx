'use client';

import React from 'react';
import { useStudentPlagiarismDetail } from '../hooks';
import { AxiosError } from 'axios';
import { AppLink as Link } from '@/components/ui/app-link';
import { 
  ArrowLeft, 
  ShieldCheck, 
  ShieldAlert, 
  Lock, 
  AlertCircle, 
  Scale, 
  FileCode2,
  Calendar
} from 'lucide-react';

export function StudentPlagiarismDetailView({ recordId }: { recordId: string }) {
  const { data, isLoading, isError, error } = useStudentPlagiarismDetail(recordId);

  if (isLoading) {
    return (
      <div className="flex flex-col h-72 items-center justify-center gap-3">
        <div className="w-8 h-8 rounded-full border-2 border-primary border-t-transparent animate-spin" />
        <p className="text-on-surface-variant font-label-md text-xs">Loading academic integrity audit...</p>
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
          {status === 404 ? 'Report Not Found' : status === 403 ? 'Access Restricted' : 'Error'}
        </h2>
        <p className="font-body-md text-on-surface-variant text-xs">{(error as Error).message}</p>
        <Link href="/student/submissions" className="mt-2 px-4 py-2 bg-primary text-on-primary rounded-lg text-xs font-medium">
          Back to Submissions
        </Link>
      </div>
    );
  }

  if (!data) return null;

  return (
    <div className="flex-1 flex flex-col min-h-0 overflow-y-auto bg-surface">
      <div className="max-w-7xl mx-auto px-space-lg py-space-lg w-full">
        <div className="flex flex-col w-full space-y-space-lg">
          {/* Header & Breadcrumbs */}
          <div className="flex flex-col md:flex-row md:items-center justify-between gap-space-md border-b border-outline-variant/30 pb-space-md">
            <div className="flex flex-col gap-1">
              <nav className="flex items-center gap-2 text-on-surface-variant text-xs mb-1">
                <Link className="hover:text-primary transition-colors flex items-center gap-1 font-medium" href="/student/submissions">
                  <ArrowLeft className="w-3.5 h-3.5" />
                  <span>Submissions</span>
                </Link>
                <span className="text-outline">/</span>
                <span className="text-primary font-semibold">Integrity Report #{data.recordId.substring(0, 8)}</span>
              </nav>
              <div className="flex items-center gap-3 flex-wrap">
                <h1 className="font-headline-lg text-headline-lg text-on-surface tracking-tight">Academic Integrity Audit</h1>
                <span className={`px-2.5 py-0.5 rounded-full text-xs font-semibold flex items-center gap-1.5 ${
                  data.isFlagged 
                    ? 'bg-rose-50 text-rose-700 border border-rose-200' 
                    : 'bg-emerald-50 text-emerald-700 border border-emerald-200'
                }`}>
                  {data.isFlagged ? <ShieldAlert className="w-3.5 h-3.5 text-rose-600" /> : <ShieldCheck className="w-3.5 h-3.5 text-emerald-600" />}
                  <span>{data.isFlagged ? 'High Similarity Flagged' : 'Standard Check Passed'}</span>
                </span>
              </div>
              <p className="text-xs text-on-surface-variant mt-0.5">
                Evaluation for Exam: <strong className="text-on-surface font-semibold">{data.examName}</strong>
              </p>
            </div>

            {data.isFlagged && (
              <div className="flex items-center gap-2 self-start md:self-auto">
                <Link 
                  href={`/student/appeals/new?submissionId=${data.recordId}`}
                  className="px-4 py-2 bg-purple-50 hover:bg-purple-100 text-purple-800 border border-purple-200 rounded-lg text-xs font-semibold flex items-center gap-1.5 shadow-xs transition-all"
                >
                  <Scale className="w-3.5 h-3.5 text-purple-600" />
                  <span>Dispute with Appeal Ticket</span>
                </Link>
              </div>
            )}
          </div>

          {/* Academic Rigor & Neutral Evidentiary Notice */}
          <div className="bg-surface-container-low rounded-2xl p-4 border border-outline-variant/30 shadow-xs flex items-start gap-3">
            <div className="p-2 rounded-lg bg-surface-container-lowest text-primary shadow-xs shrink-0 mt-0.5 border border-outline-variant/30">
              <Scale className="w-4 h-4 text-secondary" />
            </div>
            <div className="flex flex-col gap-0.5 min-w-0">
              <span className="font-semibold text-xs text-on-surface">
                Academic Rigor Notice (Thông báo Tham vấn Học thuật):
              </span>
              <p className="text-xs text-on-surface-variant leading-relaxed">
                &ldquo;<strong>Similarity indicates structural or textual overlap between submissions. It is a review signal, not proof of plagiarism.</strong>&rdquo;
              </p>
              <p className="text-xs text-on-surface-variant/90 leading-relaxed italic border-t border-outline-variant/20 pt-1 mt-0.5">
                &ldquo;Độ tương đồng phản ánh sự trùng lặp cấu trúc hoặc cú pháp AST giữa các bài nộp. Đây là tín hiệu tham vấn học thuật dành cho giảng viên đánh giá, không phải bằng chứng khẳng định gian lận.&rdquo;
              </p>
            </div>
          </div>

          {/* Report Card */}
          <div className="bg-surface-container-lowest rounded-2xl p-space-lg border border-outline-variant/40 shadow-xs flex flex-col gap-space-md">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-space-sm border-b border-outline-variant/20">
              <div>
                <span className="font-label-xs uppercase tracking-wider text-outline font-semibold">
                  AST Syntactic Similarity Score
                </span>
                <div className="flex items-baseline gap-2 mt-1">
                  <span className={`font-display-lg text-[48px] leading-none font-bold font-mono ${
                    data.isFlagged ? 'text-rose-600' : 'text-emerald-600'
                  }`}>
                    {data.similarityRate}%
                  </span>
                  <span className="text-xs text-outline font-normal">/ 100% threshold</span>
                </div>
              </div>

              <div className="text-xs text-on-surface-variant sm:text-right">
                <span className="block font-semibold text-on-surface">Sub-tree Hash Diagnostics</span>
                <span className="text-[11px] text-outline">
                  {data.evidence ? data.evidence.length : 0} structural matches recorded
                </span>
              </div>
            </div>

            <p className="text-xs text-on-surface-variant leading-relaxed">
              {data.isFlagged
                ? 'The AST comparison engine detected significant isomorphic patterns between this submission and another enrolled candidate. If you believe this is false congruence (e.g. standard boilerplate or course-provided skeleton code), you are entitled to file a formal appeal.'
                : 'Your submission passed all automated AST bytecode checks. Token frequency and node sequences divergence meet institutional criteria for unique authorship.'}
            </p>
          </div>
          
          {/* Institutional Compliance Footer */}
          <div className="flex items-center gap-2 text-xs text-outline bg-surface-container-low p-3 rounded-xl border border-outline-variant/20">
            <ShieldCheck className="w-4 h-4 text-emerald-600 shrink-0" />
            <span>Generated cryptographically by CodeAssess AST Evaluation Engine. Dean&apos;s Honor Code applies.</span>
          </div>
        </div>
      </div>
    </div>
  );
}
