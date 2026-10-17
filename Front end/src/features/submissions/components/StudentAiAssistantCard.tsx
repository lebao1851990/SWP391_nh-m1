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
          'AI Learning Assistant is locked during active examinations. Guidance will become accessible after the assessment has concluded.'
        );
      } else if (status === 429) {
        setErrorMessage(
          'Request limit reached (maximum 10 requests/minute). Please wait a few moments before requesting more guidance.'
        );
      } else if (status === 404) {
        setErrorMessage('Submission not found or unauthorized to view this submission.');
      } else if (axiosErr.response?.data?.message) {
        setErrorMessage(axiosErr.response.data.message);
      } else {
        setErrorMessage('Unable to connect to the AI learning assistant. Please try again shortly.');
      }
    } finally {
      setIsLoading(false);
    }
  };

  const getModeTitle = (m: StudentAiMode) => {
    switch (m) {
      case 'COMPILER_ERROR':
        return 'Explain Compiler Error';
      case 'CODE_EXPLANATION':
        return 'Explain My Code';
      case 'DEBUG_HINTS':
        return 'Incremental Debug Hints';
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
                AI Learning Assistant
              </h3>
              <span className="px-2 py-0.5 rounded-full text-[10px] font-semibold tracking-wider uppercase bg-primary/10 text-primary border border-primary/20">
                Advisory Only
              </span>
            </div>
            <p className="text-xs text-on-surface-variant mt-0.5">
              Pedagogical feedback on compilation errors, code structure, and debugging hints.
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
            <span>Compiler Error</span>
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
            <span>Explain Code</span>
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
            <span>Debug Hints</span>
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
              ? 'Hide custom error / compiler output'
              : 'Add or refine compiler output / test logs (optional)'}
          </span>
        </button>

        {showInputOverride && (
          <div className="flex flex-col gap-1.5 p-3 rounded-xl bg-surface-container-high/30 border border-outline-variant/30 text-xs">
            <label htmlFor="compiler-output-textarea" className="font-medium text-on-surface">
              Raw Compiler / Test Suite Output:
            </label>
            <textarea
              id="compiler-output-textarea"
              rows={4}
              value={compilerOutput}
              onChange={(e) => setCompilerOutput(e.target.value)}
              placeholder="Paste custom compilation errors or failing test logs here..."
              className="w-full p-2.5 font-mono text-xs rounded-lg bg-surface border border-outline-variant/50 text-on-surface focus:outline-none focus:ring-1 focus:ring-primary resize-y"
            />
            <span className="text-[11px] text-on-surface-variant">
              If left blank, the assistant will automatically inspect the sandbox build for errors.
            </span>
          </div>
        )}
      </div>

      {/* Action Trigger Area */}
      <div className="flex items-center justify-between gap-3 flex-wrap">
        <p className="text-xs text-on-surface-variant">
          Mode selected: <strong className="text-on-surface">{getModeTitle(mode)}</strong>
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
              <span>Analyzing with Assistant...</span>
            </>
          ) : (
            <>
              <Sparkles className="w-3.5 h-3.5" />
              <span>Get AI Guidance</span>
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
                ? 'Examination In Progress'
                : errorCode === 429
                ? 'Rate Limit Exceeded'
                : 'Assistant Notice'}
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
                <span>Assistant Guidance — {getModeTitle(response.mode)}</span>
              </div>
              <button
                type="button"
                onClick={() => handleRequestAi()}
                className="text-on-surface-variant hover:text-primary transition-colors flex items-center gap-1 text-[11px]"
                title="Refresh response"
              >
                <RotateCcw className="w-3 h-3" />
                <span>Regenerate</span>
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
              <strong>Advisory Notice:</strong> AI-generated learning guidance is educational only and may be incomplete
              or inaccurate. It does not replace teacher assessment, alter test suite outcomes, or modify assignment scores.
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
          <h4 className="font-semibold text-xs text-on-surface">Ready to guide your learning</h4>
          <p className="text-[11px] text-on-surface-variant max-w-md">
            Click <strong>Get AI Guidance</strong> above to receive pedagogical insights tailored to your code submission.
            The assistant provides hints and concept explanations without solving problems for you.
          </p>
        </div>
      )}
    </div>
  );
}
