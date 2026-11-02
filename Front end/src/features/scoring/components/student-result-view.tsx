'use client';

import React from 'react';
import { useStudentResult } from '../hooks';
import { AxiosError } from 'axios';
import { AppLink as Link } from '@/components/ui/app-link';
import { StudentAiAssistantCard, SemanticCodeReviewView } from '@/features/submissions/components';
import { 
  ArrowLeft, 
  Award, 
  Scale, 
  CheckCircle2, 
  AlertCircle, 
  Calendar, 
  Lock, 
  FileCode2,
  ExternalLink
} from 'lucide-react';

interface StudentResultViewProps {
  submissionId: string;
}

export function StudentResultView({ submissionId }: StudentResultViewProps) {
  const { data, isLoading, isError, error } = useStudentResult(submissionId);

  if (isLoading) {
    return (
      <div className="flex flex-col h-72 items-center justify-center gap-3">
        <div className="w-8 h-8 rounded-full border-2 border-primary border-t-transparent animate-spin" />
        <p className="text-on-surface-variant font-label-md text-xs">Đang tải kết quả đánh giá chính thức...</p>
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
          {status === 403 ? 'Chưa công bố kết quả' : status === 404 ? 'Không tìm thấy bài nộp' : 'Lỗi hệ thống'}
        </h2>
        <p className="font-body-md text-on-surface-variant text-xs">
          {status === 403 
            ? 'Kết quả bài thi của bạn chưa được công bố hoặc bạn không có quyền xem.'
            : (error as Error).message}
        </p>
        <Link href="/student/submissions" className="mt-2 px-4 py-2 bg-primary text-on-primary rounded-lg text-xs font-medium">
          Quay lại danh sách bài nộp
        </Link>
      </div>
    );
  }

  if (!data) return null;

  return (
    <div className="max-w-7xl mx-auto px-space-lg py-space-lg flex flex-col w-full gap-space-lg">
      {/* Header & Actions */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-space-md pb-space-md border-b border-outline-variant/30">
        <div className="flex flex-col gap-1">
          <nav className="flex items-center gap-2 text-on-surface-variant text-xs mb-1">
            <Link className="hover:text-primary transition-colors flex items-center gap-1 font-medium" href="/student/submissions">
              <ArrowLeft className="w-3.5 h-3.5" />
              <span>Bài nộp của tôi</span>
            </Link>
            <span className="text-outline-variant">/</span>
            <span className="text-primary font-semibold">{data.examName}</span>
          </nav>
          <div className="flex items-center gap-space-sm flex-wrap">
            <h1 className="font-display-lg text-display-lg text-on-surface tracking-tight">
              {data.examName} — Kết quả Chính thức
            </h1>
            <span className="px-2.5 py-0.5 rounded-full bg-emerald-50 text-emerald-700 border border-emerald-200 text-xs font-semibold inline-flex items-center gap-1">
              <CheckCircle2 className="w-3.5 h-3.5" />
              <span>{data.status.toUpperCase()}</span>
            </span>
          </div>
        </div>

        <div className="flex items-center gap-2 self-start md:self-auto">
          <Link 
            href={`/student/appeals/new?submissionId=${submissionId}&examTitle=${encodeURIComponent(data.examName || '')}&score=${data.effectiveScore ?? (data.overrideTotalScore != null ? data.overrideTotalScore : data.totalScore)}`} 
            className="px-4 py-2 bg-purple-50 hover:bg-purple-100 text-purple-800 border border-purple-200 rounded-lg text-xs font-semibold transition-all flex items-center gap-1.5 shadow-xs"
          >
            <Scale className="w-3.5 h-3.5 text-purple-600" />
            <span>Gửi đơn khiếu nại điểm</span>
          </Link>
        </div>
      </div>

      {/* Main Score Banner Card */}
      {(() => {
        const effectiveScore = data.effectiveScore ?? (data.overrideTotalScore != null ? data.overrideTotalScore : data.totalScore);
        const maxScore = data.maxTotalScore ?? (data.criteria?.length ? data.criteria.reduce((sum, c) => sum + (c.maxScore || 0), 0) : 10);
        const isOverridden = data.overrideTotalScore != null;

        return (
          <div className="rounded-2xl bg-surface-container-lowest border border-outline-variant/40 p-space-lg shadow-xs flex flex-col md:flex-row md:items-center justify-between gap-space-md">
            <div className="flex flex-col gap-2">
              <div className="flex items-center gap-2 flex-wrap">
                <Award className="w-5 h-5 text-primary" />
                <span className="font-label-xs uppercase tracking-wider text-outline font-semibold">
                  Tổng điểm đánh giá hợp nhất
                </span>
                {isOverridden && (
                  <span className="text-xs px-2.5 py-0.5 rounded-full font-semibold bg-amber-50 text-amber-700 border border-amber-200">
                    Giảng viên điều chỉnh (Rubric: {data.totalScore} đ)
                  </span>
                )}
              </div>
              <div className="flex items-baseline gap-2">
                <span className="font-display-lg text-[64px] leading-none font-bold text-primary tracking-tight">
                  {effectiveScore}
                </span>
                <span className="font-headline-lg text-outline font-normal text-2xl">
                  / {maxScore} điểm
                </span>
              </div>
            </div>

            <div className="flex flex-col gap-1 text-xs text-on-surface-variant md:text-right">
              <div className="flex md:justify-end items-center gap-1.5">
                <Calendar className="w-3.5 h-3.5 text-outline" />
                <span>Thời gian chấm: {data.evaluatedAt ? new Date(data.evaluatedAt).toLocaleString('vi-VN') : 'Gần đây'}</span>
              </div>
              <span className="text-[11px] text-outline">Xác nhận bởi Hội đồng Khảo thí</span>
            </div>
          </div>
        );
      })()}

      {/* Automated Test Case Execution Results */}
      {data.testResults && data.testResults.length > 0 && (
        <div className="flex flex-col gap-space-sm">
          <div className="flex items-center justify-between flex-wrap gap-2">
            <h2 className="font-headline-sm text-headline-sm text-on-surface tracking-tight flex items-center gap-2">
              <FileCode2 className="w-5 h-5 text-primary" />
              <span>Kết quả Chấm điểm Tự động (Automated Test Cases)</span>
            </h2>
            <div className="flex items-center gap-2 text-xs">
              <span className="px-3 py-1 rounded-full font-semibold bg-primary/10 text-primary border border-primary/20">
                Đạt: {data.testCasesPassed ?? 0} / {data.testCasesTotal ?? data.testResults.length} test
              </span>
              <span className="px-3 py-1 rounded-full font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200">
                Điểm tự động: {data.autoScore ?? 0} / 10 đ
              </span>
            </div>
          </div>

          <div className="space-y-3">
            {data.testResults.map((tr, idx) => {
              const isPassed = tr.status === 'PASSED';
              const isTimeout = tr.status === 'TIMEOUT';
              const isCompileErr = tr.status === 'COMPILE_ERROR';

              const badgeColor = isPassed
                ? 'bg-emerald-50 text-emerald-700 border-emerald-200'
                : isTimeout
                ? 'bg-amber-50 text-amber-700 border-amber-200'
                : isCompileErr
                ? 'bg-rose-50 text-rose-700 border-rose-200'
                : 'bg-red-50 text-red-700 border-red-200';

              return (
                <div 
                  key={tr.id || idx}
                  className="bg-surface-container-lowest rounded-xl border border-outline-variant/40 p-4 shadow-xs flex flex-col gap-3"
                >
                  <div className="flex items-center justify-between flex-wrap gap-2">
                    <div className="flex items-center gap-2">
                      <span className="font-semibold text-sm text-on-surface">
                        Test Case #{idx + 1}: {tr.testCaseName || `Test ${idx + 1}`}
                      </span>
                      {tr.isHidden && (
                        <span className="text-[11px] px-2 py-0.5 rounded bg-surface-container-high text-on-surface-variant flex items-center gap-1 border border-outline-variant/30">
                          <Lock className="w-3 h-3 text-outline" />
                          <span>Test ẩn</span>
                        </span>
                      )}
                    </div>
                    <div className="flex items-center gap-3">
                      {tr.executionTimeMs !== undefined && tr.executionTimeMs !== null && (
                        <span className="text-xs text-outline flex items-center gap-1">
                          <Calendar className="w-3.5 h-3.5" />
                          <span>{tr.executionTimeMs} ms</span>
                        </span>
                      )}
                      <span className={`text-xs px-2.5 py-0.5 rounded-full font-semibold border flex items-center gap-1 ${badgeColor}`}>
                        {isPassed ? <CheckCircle2 className="w-3.5 h-3.5" /> : <AlertCircle className="w-3.5 h-3.5" />}
                        <span>{tr.status}</span>
                      </span>
                      <span className="font-mono text-xs font-bold text-primary bg-primary/5 px-2.5 py-1 rounded-md border border-primary/20">
                        {tr.pointsAwarded ?? 0} {tr.maxPoints != null ? `/ ${tr.maxPoints}` : ''} đ
                      </span>
                    </div>
                  </div>

                  {/* Test Details */}
                  {!tr.isHidden ? (
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-3 pt-2 border-t border-outline-variant/20 text-xs">
                      {tr.expectedOutput && (
                        <div className="bg-surface-container-low rounded-lg p-2.5 font-mono">
                          <span className="text-outline font-semibold block mb-1">Expected Output:</span>
                          <pre className="text-on-surface whitespace-pre-wrap max-h-24 overflow-y-auto">{tr.expectedOutput}</pre>
                        </div>
                      )}
                      {tr.actualOutput && (
                        <div className="bg-surface-container-low rounded-lg p-2.5 font-mono">
                          <span className="text-outline font-semibold block mb-1">Actual Output:</span>
                          <pre className="text-on-surface whitespace-pre-wrap max-h-24 overflow-y-auto">{tr.actualOutput}</pre>
                        </div>
                      )}
                      {tr.errorMessage && (
                        <div className="md:col-span-2 bg-rose-50 text-rose-800 rounded-lg p-2.5 font-mono border border-rose-200">
                          <span className="font-semibold block mb-1">Error Message / Stderr:</span>
                          <pre className="whitespace-pre-wrap max-h-24 overflow-y-auto">{tr.errorMessage}</pre>
                        </div>
                      )}
                    </div>
                  ) : (
                    <div className="pt-2 border-t border-outline-variant/20 text-xs text-outline italic flex items-center gap-1.5">
                      <Lock className="w-3.5 h-3.5" />
                      <span>Dữ liệu đầu vào và đầu ra của test case này được ẩn để đảm bảo tính bảo mật bài thi.</span>
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        </div>
      )}

      {/* Criterion Breakdown */}
      <div className="flex flex-col gap-space-sm">
        <h2 className="font-headline-sm text-headline-sm text-on-surface tracking-tight">
          Chi tiết Tiêu chí Chấm điểm (Rubric)
        </h2>

        <div className="space-y-2">
          {data.criteria.map((criterion) => (
            <div 
              key={criterion.criterionId} 
              className="bg-surface-container-lowest rounded-xl border border-outline-variant/40 p-space-md shadow-xs flex items-center justify-between gap-space-md hover:border-primary/30 transition-all"
            >
              <div className="flex flex-col">
                <span className="font-label-md font-semibold text-on-surface text-sm">{criterion.name}</span>
                <span className="text-xs text-on-surface-variant mt-0.5">{criterion.description}</span>
              </div>
              <div className="font-mono text-sm font-bold text-emerald-700 bg-emerald-50 px-3 py-1 rounded-lg border border-emerald-200 shrink-0">
                {criterion.score} / {criterion.maxScore} đ
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* Semantic Code Review (Independent Static Analysis) */}
      <SemanticCodeReviewView submissionId={submissionId} />

      {/* Student AI Learning Assistant */}
      <StudentAiAssistantCard submissionId={submissionId} />
    </div>
  );
}
