'use client';

import React, { useState } from 'react';
import { AxiosError } from 'axios';
import {
  Sparkles,
  Terminal,
  BookOpen,
  Lightbulb,
  RotateCcw,
  ShieldCheck,
  ChevronDown,
  ChevronUp,
  Loader2,
  AlertCircle
} from 'lucide-react';
import { studentAiApi, StudentAiMode, StudentAiResponse } from '../api/student-ai.api';

interface StudentAiAssistantCardProps {
  submissionId: string;
  defaultCompilerOutput?: string;
  initialMode?: StudentAiMode;
}

export function StudentAiAssistantCard({
  submissionId,
  defaultCompilerOutput = '',
  initialMode = 'COMPILER_ERROR',
}: StudentAiAssistantCardProps) {
  const [mode, setMode] = useState<StudentAiMode>(initialMode);
  const [compilerOutput, setCompilerOutput] = useState<string>(defaultCompilerOutput);
  const [showInputOverride, setShowInputOverride] = useState<boolean>(false);
  const [isLoading, setIsLoading] = useState<boolean>(false);
  const [response, setResponse] = useState<StudentAiResponse | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [errorCode, setErrorCode] = useState<number | null>(null);

  const handleRequestAi = async (overrideMode?: StudentAiMode) => {
    const targetMode = overrideMode || mode;
    setIsLoading(true);
    setErrorMessage(null);
    setErrorCode(null);

    try {
      const res = await studentAiApi.getSubmissionAiFeedback(submissionId, {
        mode: targetMode,
        compilerOutput: compilerOutput.trim() ? compilerOutput.trim() : undefined,
      });
      setResponse(res);
    } catch (err: unknown) {
      const axiosErr = err as AxiosError<{ message?: string }>;
      const status = axiosErr.response?.status;
      setErrorCode(status ?? null);

      if (status === 403) {
        setErrorMessage(
          'Trợ lý học tập AI bị khóa trong thời gian diễn ra bài thi trực tiếp. Hướng dẫn sẽ tự động mở sau khi bài thi kết thúc.'
        );
      } else if (status === 429) {
        setErrorMessage(
          'Đã đạt giới hạn yêu cầu (tối đa 10 yêu cầu/phút). Vui lòng chờ ít phút trước khi yêu cầu trợ giúp tiếp.'
        );
      } else if (status === 404) {
        setErrorMessage('Không tìm thấy bài nộp hoặc bạn không có quyền xem bài nộp này.');
      } else if (axiosErr.response?.data?.message) {
        setErrorMessage(axiosErr.response.data.message);
      } else {
        setErrorMessage('Không thể kết nối tới trợ lý học tập AI. Vui lòng thử lại sau giây lát.');
      }
    } finally {
      setIsLoading(false);
    }
  };

  const getModeTitle = (m: StudentAiMode) => {
    switch (m) {
      case 'COMPILER_ERROR':
        return 'Giải thích lỗi biên dịch';
      case 'CODE_EXPLANATION':
        return 'Giải thích mã nguồn';
      case 'DEBUG_HINTS':
        return 'Gợi ý gỡ lỗi từng bước';
    }
  };

  return (
    <div className="rounded-2xl bg-gradient-to-br from-surface-container-lowest to-surface-container-low/60 border border-outline-variant/50 p-space-lg shadow-sm flex flex-col gap-5">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-3 border-b border-outline-variant/30">
        <div className="flex items-center gap-2.5">
          <div className="p-2 rounded-xl bg-primary/10 text-primary">
            <Sparkles className="w-5 h-5 animate-pulse" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h3 className="font-headline-sm text-base font-bold text-on-surface tracking-tight">
                Trợ lý Học tập AI
              </h3>
              <span className="px-2 py-0.5 rounded-full text-[10px] font-semibold tracking-wider uppercase bg-primary/10 text-primary border border-primary/20">
                Chỉ mang tính tham vấn
              </span>
            </div>
            <p className="text-xs text-on-surface-variant mt-0.5">
              Phản hồi sư phạm hỗ trợ phân tích lỗi biên dịch, cấu trúc mã nguồn và định hướng gỡ lỗi.
            </p>
          </div>
        </div>

        {/* Mode Selector Tabs */}
        <div className="flex items-center p-1 bg-surface-container-high/40 rounded-xl border border-outline-variant/30 text-xs">
          <button
            type="button"
            id="tab-compiler-error"
            onClick={() => {
              setMode('COMPILER_ERROR');
              if (response && response.mode !== 'COMPILER_ERROR') setResponse(null);
            }}
            className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg font-medium transition-all ${
              mode === 'COMPILER_ERROR'
                ? 'bg-surface shadow-xs text-primary font-semibold'
                : 'text-on-surface-variant hover:text-on-surface'
            }`}
          >
            <Terminal className="w-3.5 h-3.5" />
            <span>Lỗi biên dịch</span>
          </button>

          <button
            type="button"
            id="tab-code-explanation"
            onClick={() => {
              setMode('CODE_EXPLANATION');
              if (response && response.mode !== 'CODE_EXPLANATION') setResponse(null);
            }}
            className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg font-medium transition-all ${
              mode === 'CODE_EXPLANATION'
                ? 'bg-surface shadow-xs text-primary font-semibold'
                : 'text-on-surface-variant hover:text-on-surface'
            }`}
          >
            <BookOpen className="w-3.5 h-3.5" />
            <span>Giải thích mã</span>
          </button>

          <button
            type="button"
            id="tab-debug-hints"
            onClick={() => {
              setMode('DEBUG_HINTS');
              if (response && response.mode !== 'DEBUG_HINTS') setResponse(null);
            }}
            className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg font-medium transition-all ${
              mode === 'DEBUG_HINTS'
                ? 'bg-surface shadow-xs text-primary font-semibold'
                : 'text-on-surface-variant hover:text-on-surface'
            }`}
          >
            <Lightbulb className="w-3.5 h-3.5" />
            <span>Gợi ý gỡ lỗi</span>
          </button>
        </div>
      </div>

      {/* Optional Compiler / Error Context Accordion */}
      <div className="flex flex-col gap-2">
        <button
          type="button"
          onClick={() => setShowInputOverride(!showInputOverride)}
          className="flex items-center gap-1.5 text-xs font-medium text-on-surface-variant hover:text-primary transition-colors self-start"
        >
          {showInputOverride ? <ChevronUp className="w-3.5 h-3.5" /> : <ChevronDown className="w-3.5 h-3.5" />}
          <span>
            {showInputOverride
              ? 'Ẩn kết quả biên dịch tùy chỉnh'
              : 'Thêm nhật ký biên dịch / lỗi kiểm thử (tùy chọn)'}
          </span>
        </button>

        {showInputOverride && (
          <div className="flex flex-col gap-1.5 p-3 rounded-xl bg-surface-container-high/30 border border-outline-variant/30 text-xs">
            <label htmlFor="compiler-output-textarea" className="font-medium text-on-surface">
              Nội dung thông báo lỗi từ trình biên dịch / bộ kiểm thử:
            </label>
            <textarea
              id="compiler-output-textarea"
              rows={4}
              value={compilerOutput}
              onChange={(e) => setCompilerOutput(e.target.value)}
              placeholder="Dán mã lỗi biên dịch hoặc nhật ký kiểm thử thất bại vào đây..."
              className="w-full p-2.5 font-mono text-xs rounded-lg bg-surface border border-outline-variant/50 text-on-surface focus:outline-none focus:ring-1 focus:ring-primary resize-y"
            />
            <span className="text-[11px] text-on-surface-variant">
              Nếu để trống, trợ lý sẽ tự động trích xuất thông tin lỗi từ sandbox chấm điểm.
            </span>
          </div>
        )}
      </div>

      {/* Action Trigger Area */}
      <div className="flex items-center justify-between gap-3 flex-wrap">
        <p className="text-xs text-on-surface-variant">
          Chế độ đã chọn: <strong className="text-on-surface">{getModeTitle(mode)}</strong>
        </p>

        <button
          type="button"
          id="btn-request-ai"
          onClick={() => handleRequestAi()}
          disabled={isLoading}
          className="px-4 py-2 bg-primary hover:bg-primary/90 disabled:opacity-50 text-on-primary rounded-xl text-xs font-semibold shadow-xs flex items-center gap-2 transition-all cursor-pointer disabled:cursor-not-allowed"
        >
          {isLoading ? (
            <>
              <Loader2 className="w-3.5 h-3.5 animate-spin" />
              <span>Đang phân tích cùng trợ lý...</span>
            </>
          ) : (
            <>
              <Sparkles className="w-3.5 h-3.5" />
              <span>Nhận hướng dẫn AI</span>
            </>
          )}
        </button>
      </div>

      {/* Error Message Box */}
      {errorMessage && (
        <div
          id="ai-error-banner"
          className="rounded-xl p-4 bg-rose-50 border border-rose-200 text-rose-800 text-xs flex items-start gap-3"
        >
          <AlertCircle className="w-4 h-4 text-rose-600 shrink-0 mt-0.5" />
          <div className="flex flex-col gap-1">
            <span className="font-semibold">
              {errorCode === 403
                ? 'Bài thi đang diễn ra'
                : errorCode === 429
                ? 'Vượt quá giới hạn tần suất'
                : 'Thông báo Trợ lý'}
            </span>
            <p className="leading-relaxed">{errorMessage}</p>
          </div>
        </div>
      )}

      {/* AI Response Display Card */}
      {response && (
        <div id="ai-response-container" className="flex flex-col gap-4 mt-1">
          <div className="rounded-xl border border-primary/20 bg-primary/5 p-space-md flex flex-col gap-3">
            <div className="flex items-center justify-between border-b border-primary/10 pb-2">
              <div className="flex items-center gap-2 text-primary font-semibold text-xs">
                <Sparkles className="w-3.5 h-3.5" />
                <span>Hướng dẫn của Trợ lý — {getModeTitle(response.mode)}</span>
              </div>
              <button
                type="button"
                onClick={() => handleRequestAi()}
                className="text-on-surface-variant hover:text-primary transition-colors flex items-center gap-1 text-[11px]"
                title="Tạo lại phản hồi"
              >
                <RotateCcw className="w-3 h-3" />
                <span>Tạo lại</span>
              </button>
            </div>

            {/* Formatted Content */}
            <div className="text-xs text-on-surface leading-relaxed whitespace-pre-line font-normal space-y-2">
              {response.explanation}
            </div>
          </div>

          {/* Legal / Policy Disclaimer Banner */}
          <div className="rounded-lg p-3 bg-amber-50/70 border border-amber-200/60 text-amber-900 text-[11px] flex items-start gap-2">
            <ShieldCheck className="w-3.5 h-3.5 text-amber-700 shrink-0 mt-0.5" />
            <p className="leading-relaxed">
              <strong>Lưu ý tham vấn:</strong> Hướng dẫn học tập do AI cung cấp chỉ phục vụ mục đích giáo dục, có thể chưa hoàn chỉnh hoặc có độ trễ. Phản hồi AI không thay thế đánh giá chính thức của giảng viên, không thay đổi kết quả kiểm thử và không can thiệp vào điểm số.
            </p>
          </div>
        </div>
      )}

      {/* Empty State / Prompt Cards */}
      {!response && !isLoading && !errorMessage && (
        <div className="rounded-xl border border-dashed border-outline-variant/60 p-6 text-center flex flex-col items-center gap-2">
          <div className="p-2.5 rounded-full bg-surface-container-high/50 text-on-surface-variant">
            <BookOpen className="w-5 h-5" />
          </div>
          <h4 className="font-semibold text-xs text-on-surface">Sẵn sàng đồng hành cùng bạn</h4>
          <p className="text-[11px] text-on-surface-variant max-w-md">
            Nhấn <strong>Nhận hướng dẫn AI</strong> phía trên để nhận những phân tích sư phạm phù hợp với bài nộp của bạn. Trợ lý cung cấp gợi ý và giải thích khái niệm mà không giải thay bài tập.
          </p>
        </div>
      )}
    </div>
  );
}
