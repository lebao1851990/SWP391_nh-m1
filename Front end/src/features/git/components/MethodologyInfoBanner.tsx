'use client';

import React, { useState } from 'react';
import { Info, AlertTriangle, ChevronDown, ChevronUp, ShieldCheck } from 'lucide-react';
import { CalculationMethodology } from '../types';

interface MethodologyInfoBannerProps {
  methodology?: CalculationMethodology;
}

export const MethodologyInfoBanner: React.FC<MethodologyInfoBannerProps> = ({ methodology }) => {
  const [expanded, setExpanded] = useState(false);

  return (
    <div className="bg-blue-50 border border-blue-200 rounded-lg p-4 mb-6 text-sm text-blue-900 shadow-xs">
      <div className="flex items-start justify-between gap-3">
        <div className="flex items-start gap-2.5">
          <Info className="w-5 h-5 text-blue-600 mt-0.5 shrink-0" />
          <div>
            <h3 className="font-semibold text-blue-950 flex items-center gap-2">
              Transparent Metric Calculation & Ethical Evaluation
              <span className="text-xs bg-blue-100 text-blue-800 font-medium px-2 py-0.5 rounded-full border border-blue-300">
                {methodology?.version || 'v1.0'}
              </span>
            </h3>
            <p className="text-blue-800 text-xs mt-1">
              Contribution metrics are diagnostic indicators designed to facilitate holistic evaluation. They should never be used as the sole criterion for grading.
            </p>
          </div>
        </div>

        <button
          type="button"
          onClick={() => setExpanded(!expanded)}
          className="text-xs font-medium text-blue-700 hover:text-blue-900 flex items-center gap-1 shrink-0 px-2 py-1 rounded-md hover:bg-blue-100 transition-colors"
        >
          {expanded ? 'Hide Details' : 'View Formulas & Rules'}
          {expanded ? <ChevronUp className="w-4 h-4" /> : <ChevronDown className="w-4 h-4" />}
        </button>
      </div>

      {expanded && (
        <div className="mt-4 pt-4 border-t border-blue-200/80 space-y-3 text-xs">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
            <div className="bg-white p-3 rounded-md border border-blue-100">
              <span className="font-semibold text-slate-900 block mb-1">Net Lines of Code Formula</span>
              <code className="text-blue-700 font-mono text-[11px] block bg-slate-50 p-1.5 rounded">
                Net Lines = Lines Added - Lines Deleted (clamped at 0)
              </code>
              <p className="text-slate-600 text-[11px] mt-1.5">
                Bulk deletions (e.g. refactoring boilerplate or cleaning generated files) do not penalize cumulative contributions into negative territory.
              </p>
            </div>

            <div className="bg-white p-3 rounded-md border border-blue-100">
              <span className="font-semibold text-slate-900 block mb-1">Consistency Score (0 – 100)</span>
              <code className="text-blue-700 font-mono text-[11px] block bg-slate-50 p-1.5 rounded">
                Score = (Active Days / Total Project Span Days) * 100
              </code>
              <p className="text-slate-600 text-[11px] mt-1.5">
                Measures regular iterative development rather than sporadic single-day commit dumpings.
              </p>
            </div>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
            <div className="bg-white p-3 rounded-md border border-blue-100">
              <span className="font-semibold text-slate-900 flex items-center gap-1.5 mb-1">
                <ShieldCheck className="w-4 h-4 text-emerald-600" />
                Merge Commit Handling
              </span>
              <p className="text-slate-600 text-[11px]">
                {methodology?.mergeCommitHandling || 'Merge commits (commits with >= 2 parent hashes) are tracked separately and excluded from raw line counts to avoid double-counting code authored in feature branches.'}
              </p>
            </div>

            <div className="bg-white p-3 rounded-md border border-blue-100">
              <span className="font-semibold text-slate-900 flex items-center gap-1.5 mb-1">
                <AlertTriangle className="w-4 h-4 text-amber-600" />
                Revert Commit Handling
              </span>
              <p className="text-slate-600 text-[11px]">
                {methodology?.revertCommitHandling || 'Revert commits are flagged with isRevertCommit=true and logged transparently to ensure retracted code changes are visible during team assessments.'}
              </p>
            </div>
          </div>

          <div className="bg-amber-50 border border-amber-200 text-amber-900 p-2.5 rounded-md flex items-start gap-2">
            <AlertTriangle className="w-4 h-4 text-amber-700 shrink-0 mt-0.5" />
            <span className="text-[11px] leading-relaxed">
              <strong>Pedagogical Warning:</strong> Evaluating team members strictly by raw lines of code incentivizes verbose code and anti-patterns. Use commit frequency, pull request reviews, and structural quality alongside these metrics.
            </span>
          </div>
        </div>
      )}
    </div>
  );
};

