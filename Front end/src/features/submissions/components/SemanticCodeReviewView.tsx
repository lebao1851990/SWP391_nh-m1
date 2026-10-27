'use client';

import React, { useState } from 'react';
import { AxiosError } from 'axios';
import {
  Sparkles,
  ShieldAlert,
  AlertTriangle,
  Info,
  CheckCircle2,
  RefreshCw,
  Code2,
  Terminal,
  ChevronDown,
  ChevronUp
} from 'lucide-react';
import {
  semanticReviewApi,
  SemanticReviewResponse,
  CodeReviewIssue,
  ReviewIssueSeverity,
  ReviewIssueCategory
} from '../api/semantic-review.api';

interface SemanticCodeReviewViewProps {
  submissionId: string;
  isTeacher?: boolean;
}

export function SemanticCodeReviewView({
  submissionId,
  isTeacher = false
}: SemanticCodeReviewViewProps) {
  const [data, setData] = useState<SemanticReviewResponse | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [errorCode, setErrorCode] = useState<number | null>(null);
  const [selectedCategory, setSelectedCategory] = useState<string>('ALL');

  const fetchReview = async () => {
    setIsLoading(true);
    setErrorMessage(null);
    setErrorCode(null);

    try {
      const res = isTeacher
        ? await semanticReviewApi.getTeacherSemanticReview(submissionId)
        : await semanticReviewApi.getStudentSemanticReview(submissionId);
      setData(res);
    } catch (err: unknown) {
      const axiosErr = err as AxiosError<{ message?: string }>;
      const status = axiosErr.response?.status;
      setErrorCode(status ?? null);

      if (status === 403) {
        setErrorMessage(
          'Semantic Code Review bị khóa trong thời gian diễn ra bài thi trực tiếp để đảm bảo tính công bằng.'
        );
      } else if (status === 404) {
        setErrorMessage('Không tìm thấy bài nộp hoặc bạn không có quyền xem bài nộp này.');
      } else if (axiosErr.response?.data?.message) {
        setErrorMessage(axiosErr.response.data.message);
      } else {
        setErrorMessage('Không thể kết nối tới dịch vụ AI Semantic Review. Vui lòng thử lại sau.');
      }
    } finally {
      setIsLoading(false);
    }
  };

  const getSeverityBadge = (sev: ReviewIssueSeverity) => {
    switch (sev) {
      case 'CRITICAL':
        return (
          <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[10px] font-bold bg-rose-100 text-rose-800 border border-rose-300">
            <ShieldAlert className="w-3 h-3 text-rose-600" />
            CRITICAL
          </span>
        );
      case 'WARNING':
        return (
          <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[10px] font-bold bg-amber-100 text-amber-800 border border-amber-300">
            <AlertTriangle className="w-3 h-3 text-amber-600" />
            WARNING
          </span>
        );
      case 'INFO':
        return (
          <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[10px] font-bold bg-blue-100 text-blue-800 border border-blue-300">
            <Info className="w-3 h-3 text-blue-600" />
            INFO
          </span>
        );
    }
  };

  const getCategoryLabel = (cat: ReviewIssueCategory) => {
    switch (cat) {
      case 'READABILITY_AND_NAMING':
        return 'Readability & Naming';
      case 'CODE_DUPLICATION':
        return 'Code Duplication';
      case 'COMPLEXITY_AND_MAINTAINABILITY':
        return 'Complexity & Maintainability';
      case 'CLEAN_CODE_SMELLS':
        return 'Clean Code Smells';
      case 'COMPILER_OR_RUNTIME':
        return 'Compiler & Runtime';
    }
  };

  const filteredIssues = data?.issues?.filter((issue) => {
    if (selectedCategory === 'ALL') return true;
    return issue.category === selectedCategory;
  }) || [];

  return (
    <div className="rounded-2xl bg-surface-container-lowest border border-outline-variant/60 shadow-sm overflow-hidden flex flex-col">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between p-4 sm:p-5 bg-surface-container-low border-b border-outline-variant/40 gap-3">
        <div className="flex items-center gap-3">
          <div className="p-2.5 rounded-xl bg-purple-100 text-purple-700 border border-purple-200">
            <Code2 className="w-5 h-5" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h3 className="font-headline-sm font-bold text-on-surface text-base">
                Semantic Code Review (Clean Code & Tĩnh)
              </h3>
              <span className="px-2 py-0.5 rounded-full text-[10px] font-semibold uppercase bg-purple-50 text-purple-700 border border-purple-200">
                AI Phân tích Cú pháp
              </span>
            </div>
            <p className="text-xs text-on-surface-variant mt-0.5">
              Đánh giá độc lập chất lượng mã nguồn, độ phức tạp thuật toán và chuẩn mực Clean Code
            </p>
          </div>
        </div>

        <button
          onClick={fetchReview}
          disabled={isLoading}
          className="inline-flex items-center justify-center gap-2 px-4 py-2 rounded-xl bg-primary text-on-primary text-xs font-semibold hover:bg-primary/90 disabled:opacity-60 transition-all shadow-xs shrink-0 cursor-pointer"
        >
          {isLoading ? (
            <>
              <RefreshCw className="w-3.5 h-3.5 animate-spin" />
              <span>Đang phân tích...</span>
            </>
          ) : (
            <>
              <Sparkles className="w-3.5 h-3.5" />
              <span>{data ? 'Phân tích lại' : 'Chạy Semantic Review'}</span>
            </>
          )}
        </button>
      </div>

      {/* Body Content */}
      <div className="p-4 sm:p-6 space-y-5">
        {errorMessage && (
          <div className="p-4 rounded-xl bg-rose-50 border border-rose-200 text-rose-800 text-xs flex items-start gap-2.5">
            <AlertTriangle className="w-4 h-4 text-rose-600 shrink-0 mt-0.5" />
            <div>
              <p className="font-semibold">{errorMessage}</p>
              {errorCode === 403 && (
                <p className="mt-1 text-rose-700 opacity-90">
                  Hệ thống khóa cố vấn mã nguồn tự động cho tới khi kỳ thi hoàn tất nhằm đảm bảo tính công bằng học thuật.
                </p>
              )}
            </div>
          </div>
        )}

        {!data && !isLoading && !errorMessage && (
          <div className="py-10 px-4 text-center border-2 border-dashed border-outline-variant/50 rounded-xl flex flex-col items-center justify-center gap-2 bg-surface-container-lowest">
            <div className="p-3 rounded-full bg-surface-container text-on-surface-variant">
              <Terminal className="w-6 h-6 opacity-70" />
            </div>
            <p className="text-sm font-semibold text-on-surface">Chưa có kết quả Semantic Review</p>
            <p className="text-xs text-on-surface-variant max-w-md">
              Bấm nút &quot;Chạy Semantic Review&quot; để AI quét toàn bộ cây cú pháp mã nguồn, phát hiện code smells, đặt tên biến, và đánh giá chỉ số Clean Code.
            </p>
          </div>
        )}

        {isLoading && (
          <div className="py-12 flex flex-col items-center justify-center gap-3 text-center">
            <RefreshCw className="w-8 h-8 text-primary animate-spin" />
            <p className="text-sm font-semibold text-on-surface">Đang phân tích cú pháp AST và đối soát Clean Code...</p>
            <p className="text-xs text-on-surface-variant max-w-sm">
              Hệ thống đang kiểm tra tính dư thừa, độ phức tạp lồng nhau và các vi phạm quy chuẩn lập trình.
            </p>
          </div>
        )}

        {data && (
          <div className="space-y-5 animate-in fade-in duration-200">
            {/* Overview Score Card */}
            <div className="grid grid-cols-1 md:grid-cols-4 gap-4 p-4 rounded-xl bg-surface-container-low border border-outline-variant/30">
              <div className="md:col-span-1 flex flex-col items-center justify-center p-3 rounded-lg bg-surface-container-lowest border border-outline-variant/40 text-center">
                <span className="text-[11px] font-semibold text-on-surface-variant uppercase tracking-wider">
                  Điểm Clean Code
                </span>
                <div className="mt-1 font-mono text-3xl font-extrabold text-primary">
                  {data.cleanCodeScore.toFixed(1)} <span className="text-xs font-normal text-on-surface-variant">/ 10</span>
                </div>
              </div>

              <div className="md:col-span-3 flex flex-col justify-center">
                <span className="text-xs font-bold text-on-surface uppercase tracking-wider mb-1">
                  Đánh giá tổng quan
                </span>
                <p className="text-xs text-on-surface leading-relaxed">
                  {data.summary}
                </p>
              </div>
            </div>

            {/* Filter Tabs */}
            <div className="flex flex-wrap gap-1.5 pt-1 border-b border-outline-variant/30 pb-3">
              {['ALL', 'READABILITY_AND_NAMING', 'CODE_DUPLICATION', 'COMPLEXITY_AND_MAINTAINABILITY', 'CLEAN_CODE_SMELLS', 'COMPILER_OR_RUNTIME'].map((cat) => (
                <button
                  key={cat}
                  onClick={() => setSelectedCategory(cat)}
                  className={`px-2.5 py-1 rounded-lg text-[11px] font-medium transition-colors cursor-pointer ${
                    selectedCategory === cat
                      ? 'bg-primary text-on-primary font-semibold'
                      : 'bg-surface-container hover:bg-surface-container-high text-on-surface-variant'
                  }`}
                >
                  {cat === 'ALL' ? 'Tất cả vấn đề' : getCategoryLabel(cat as ReviewIssueCategory)}
                </button>
              ))}
            </div>

            {/* Issues List */}
            <div className="space-y-3">
              <div className="flex items-center justify-between text-xs text-on-surface-variant">
                <span>Danh sách phát hiện ({filteredIssues.length} vấn đề)</span>
                {filteredIssues.length === 0 && (
                  <span className="text-emerald-700 font-semibold flex items-center gap-1">
                    <CheckCircle2 className="w-3.5 h-3.5" /> Không có vi phạm thuộc danh mục này
                  </span>
                )}
              </div>

              {filteredIssues.map((issue, idx) => (
                <div
                  key={idx}
                  className="p-4 rounded-xl bg-surface-container-lowest border border-outline-variant/40 space-y-2 hover:border-outline-variant transition-all shadow-2xs"
                >
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <div className="flex items-center gap-2">
                      {getSeverityBadge(issue.severity)}
                      <span className="text-[11px] font-semibold text-on-surface-variant">
                        [{getCategoryLabel(issue.category)}]
                      </span>
                    </div>
                    {issue.line ? (
                      <span className="font-mono text-[11px] px-2 py-0.5 rounded bg-surface-container text-on-surface-variant font-semibold">
                        Dòng: {issue.line}
                      </span>
                    ) : (
                      <span className="text-[11px] text-outline italic">
                        (Toàn bộ file / Cấu trúc chung)
                      </span>
                    )}
                  </div>

                  <p className="text-xs font-medium text-on-surface leading-relaxed">
                    {issue.message}
                  </p>

                  {issue.suggestion && (
                    <div className="p-2.5 rounded-lg bg-emerald-50/60 border border-emerald-200/60 text-xs text-emerald-900 flex items-start gap-2">
                      <CheckCircle2 className="w-3.5 h-3.5 text-emerald-600 shrink-0 mt-0.5" />
                      <div className="leading-relaxed">
                        <span className="font-semibold text-emerald-800">Gợi ý tối ưu: </span>
                        {issue.suggestion}
                      </div>
                    </div>
                  )}
                </div>
              ))}
            </div>

            {/* Advisory Footnote */}
            <div className="p-3 rounded-lg bg-surface-container-low text-[11px] text-on-surface-variant border border-outline-variant/30 flex items-center gap-2">
              <Info className="w-3.5 h-3.5 text-primary shrink-0" />
              <span>{data.advisoryNotice}</span>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
