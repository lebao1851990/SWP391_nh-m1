'use client';

import React from 'react';
import { AiAnalysisResponse } from '../api/plagiarism-ai.api';
import { Bot, X, Loader2, AlertCircle, Info, Sparkles } from 'lucide-react';

interface AiAnalysisModalProps {
  isOpen: boolean;
  onClose: () => void;
  title: string;
  status: 'idle' | 'loading' | 'success' | 'error';
  data: AiAnalysisResponse | null;
  errorMessage?: string;
}

export function AiAnalysisModal({
  isOpen,
  onClose,
  title,
  status,
  data,
  errorMessage,
}: AiAnalysisModalProps) {
  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-scrim/50 backdrop-blur-sm px-space-md animate-in fade-in duration-150">
      <div className="bg-surface-container-lowest w-full max-w-2xl rounded-2xl shadow-2xl border border-outline-variant/40 flex flex-col overflow-hidden animate-in zoom-in-95 duration-200">
        
        {/* Header */}
        <div className="flex items-center justify-between px-space-lg py-space-md border-b border-outline-variant/30 bg-surface-container-low">
          <div className="flex items-center gap-space-sm">
            <div className="p-1.5 rounded-lg bg-purple-50 text-purple-700 border border-purple-200/60">
              <Sparkles className="w-4 h-4" />
            </div>
            <div>
              <h2 className="font-headline-sm text-headline-sm font-semibold text-on-surface">{title}</h2>
              <p className="text-[11px] text-on-surface-variant">Tổng hợp Tham vấn AI — Phân tích Cấu trúc Mã nguồn Neural</p>
            </div>
          </div>
          <button 
            onClick={onClose}
            className="p-1.5 rounded-lg text-on-surface-variant hover:text-on-surface hover:bg-surface-container transition-colors cursor-pointer"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* Content Body */}
        <div className="p-space-lg max-h-[60vh] overflow-y-auto">
          {status === 'loading' && (
            <div className="flex flex-col items-center justify-center py-12 gap-3 text-center">
              <Loader2 className="w-8 h-8 text-primary animate-spin" />
              <div className="space-y-1">
                <span className="font-headline-sm text-sm font-semibold text-on-surface">Đang tổng hợp báo cáo giám định AI...</span>
                <p className="font-body-sm text-xs text-on-surface-variant">
                  Đang phân tích vector nhúng cây con và tổng hợp báo cáo đối chiếu học thuật khách quan.
                </p>
              </div>
            </div>
          )}

          {status === 'error' && (
            <div className="flex flex-col items-center justify-center py-8 px-6 gap-3 bg-surface-container-low rounded-xl border border-outline-variant/30 text-center">
              <div className="p-3 rounded-full bg-amber-50 text-amber-600 border border-amber-200">
                <Bot className="w-7 h-7" />
              </div>
              <div className="space-y-1.5 max-w-md">
                <h3 className="font-headline-sm text-sm font-bold text-on-surface">Chưa cấu hình Trợ lý AI</h3>
                <p className="font-body-sm text-xs text-on-surface-variant leading-relaxed">
                  {errorMessage || 'Tính năng phân tích mã nguồn bằng AI hiện chưa khả dụng trong môi trường này. Backend StubAiClient cần cấu hình thông tin xác thực nhà cung cấp AI (khóa API Gemini / OpenAI).'}
                </p>
              </div>
              <div className="mt-2 p-2.5 rounded-lg bg-surface-container-lowest border border-outline-variant/30 text-[11px] text-outline font-mono">
                Trạng thái: TẠM KHÓA — StubAiClient đang kích hoạt
              </div>
            </div>
          )}

          {status === 'success' && data && (
            <div className="flex flex-col gap-space-md">
              <div className="flex items-start gap-2.5 p-3 bg-blue-50/70 text-blue-900 rounded-xl border border-blue-200/60 text-xs">
                <Info className="w-4 h-4 text-blue-600 shrink-0 mt-0.5" />
                <span className="leading-relaxed">
                  Phân tích này chỉ mang tính chất tham vấn tổng hợp từ các token cú pháp AST. Công cụ kiểm tra gian lận xác định AST vẫn là nguồn căn cứ chính thức cho hội đồng học thuật.
                </span>
              </div>
              <div className="p-4 rounded-xl bg-surface-container-low text-on-surface text-xs font-mono whitespace-pre-wrap leading-relaxed border border-outline-variant/30">
                {data.analysis}
              </div>
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="flex items-center justify-end px-space-lg py-space-sm border-t border-outline-variant/30 bg-surface-container-low">
          <button 
            onClick={onClose}
            className="px-4 py-2 rounded-lg bg-primary text-on-primary font-label-md text-xs font-semibold hover:bg-primary/90 transition-colors cursor-pointer shadow-xs"
          >
            Đóng hồ sơ
          </button>
        </div>

      </div>
    </div>
  );
}
