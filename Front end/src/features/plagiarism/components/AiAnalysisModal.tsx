'use client';

import React from 'react';
import { AiAnalysisResponse } from '../api/plagiarism-ai.api';

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
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-scrim/40 backdrop-blur-sm px-space-md">
      <div className="bg-surface-container-lowest w-full max-w-2xl rounded-2xl shadow-2xl flex flex-col overflow-hidden animate-in fade-in zoom-in duration-200">
        
        {/* Header */}
        <div className="flex items-center justify-between px-space-lg py-space-md border-b border-outline-variant/30 bg-surface-container-low">
          <div className="flex items-center gap-space-sm">
            <span className="material-symbols-outlined text-primary text-[24px]">smart_toy</span>
            <h2 className="font-headline-sm text-headline-sm font-semibold text-on-surface">{title}</h2>
          </div>
          <button 
            onClick={onClose}
            className="p-1 rounded text-on-surface-variant hover:text-on-surface hover:bg-surface-container transition-colors"
          >
            <span className="material-symbols-outlined text-[20px]">close</span>
          </button>
        </div>

        {/* Content Body */}
        <div className="p-space-lg max-h-[60vh] overflow-y-auto">
          {status === 'loading' && (
            <div className="flex flex-col items-center justify-center py-10 gap-space-sm">
              <span className="material-symbols-outlined animate-spin text-[40px] text-primary">progress_activity</span>
              <span className="font-body-md text-on-surface-variant">Generating AI advisory analysis...</span>
            </div>
          )}

          {status === 'error' && (
            <div className="flex flex-col items-center justify-center py-10 gap-space-sm bg-surface-container-low rounded-xl border border-outline-variant/30">
              <span className="material-symbols-outlined text-[40px] text-outline">smart_toy</span>
              <span className="font-headline-sm text-on-surface font-semibold text-center">AI Assistant Not Configured</span>
              <span className="font-body-sm text-on-surface-variant text-center max-w-md mt-2">
                AI-assisted analysis is currently unavailable. This feature requires an active AI provider configuration which has not been set up in this environment.
              </span>
            </div>
          )}

          {status === 'success' && data && (
            <div className="flex flex-col gap-space-md">
              <div className="flex items-center gap-2 p-3 bg-secondary-container/20 rounded-lg border border-secondary/20">
                <span className="material-symbols-outlined text-[18px] text-secondary">info</span>
                <span className="font-body-sm text-on-surface-variant text-sm">
                  This analysis is advisory and based on existing AST evidence. The backend Plagiarism Engine remains authoritative.
                </span>
              </div>
              <div className="font-body-md text-on-surface whitespace-pre-wrap leading-relaxed">
                {data.analysis}
              </div>
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="flex items-center justify-end px-space-lg py-space-sm border-t border-outline-variant/30 bg-surface-container-low">
          <button 
            onClick={onClose}
            className="px-4 py-2 rounded-lg bg-surface-container-high text-on-surface font-label-md font-semibold hover:bg-surface-container-highest transition-colors"
          >
            Close
          </button>
        </div>

      </div>
    </div>
  );
}
