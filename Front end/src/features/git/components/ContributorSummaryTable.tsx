'use client';

import React from 'react';
import { UserCheck, UserX, UserPlus, TrendingUp } from 'lucide-react';
import { ContributorSummary } from '../types';

interface ContributorSummaryTableProps {
  contributors: ContributorSummary[];
  onOpenMappingModal?: (contributor: ContributorSummary) => void;
  canMap?: boolean;
}

export const ContributorSummaryTable: React.FC<ContributorSummaryTableProps> = ({
  contributors,
  onOpenMappingModal,
  canMap = false,
}) => {
  if (!contributors || contributors.length === 0) {
    return (
      <div className="bg-white rounded-xl border border-slate-200 p-8 text-center text-slate-500 text-sm">
        No contributor statistics found for the selected repository and timeframe.
      </div>
    );
  }

  return (
    <div className="bg-white rounded-xl border border-slate-200 shadow-xs overflow-hidden mb-6">
      <div className="px-5 py-4 border-b border-slate-100 flex items-center justify-between">
        <h3 className="font-bold text-slate-900 text-base flex items-center gap-2">
          Member Contribution Summary
          <span className="text-xs bg-slate-100 text-slate-700 font-medium px-2 py-0.5 rounded-full">
            {contributors.length} {contributors.length === 1 ? 'member' : 'members'}
          </span>
        </h3>
        <span className="text-xs text-slate-400">Sorted by commit count</span>
      </div>

      <div className="overflow-x-auto">
        <table className="w-full text-left border-collapse text-xs">
          <thead>
            <tr className="bg-slate-50 border-b border-slate-200 text-slate-600 font-semibold uppercase tracking-wider text-[11px]">
              <th className="py-3 px-4">Contributor</th>
              <th className="py-3 px-4">Identity Match</th>
              <th className="py-3 px-4 text-right">Commits</th>
              <th className="py-3 px-4 text-right">Lines Added (+)</th>
              <th className="py-3 px-4 text-right">Lines Deleted (-)</th>
              <th className="py-3 px-4 text-right">Net Lines</th>
              <th className="py-3 px-4 text-center">Active Days</th>
              <th className="py-3 px-4 text-center">Consistency</th>
              {canMap && <th className="py-3 px-4 text-center">Action</th>}
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {contributors.map((c, idx) => {
              const email = c.authorEmail || (c as unknown as { gitAuthorEmail?: string }).gitAuthorEmail || `contributor-${idx}`;
              const name = c.authorName || (c as unknown as { gitAuthorName?: string }).gitAuthorName || 'Unknown';
              const initial = (name || email).charAt(0).toUpperCase();
              const isMatched = Boolean(c.isMappedUser || (c as unknown as { mapped?: boolean; isMapped?: boolean }).mapped || (c as unknown as { mapped?: boolean; isMapped?: boolean }).isMapped || c.userId);
              const consistency = c.consistencyScore ?? ((c as unknown as { consistencyRatio?: number }).consistencyRatio != null ? Math.round((c as unknown as { consistencyRatio: number }).consistencyRatio * 100) : 0);

              return (
                <tr key={email} className="hover:bg-slate-50/70 transition-colors">
                  <td className="py-3.5 px-4">
                    <div className="flex items-center gap-2.5">
                      <div className="w-7 h-7 rounded-full bg-blue-100 text-blue-700 font-bold flex items-center justify-center text-xs shrink-0">
                        {initial}
                      </div>
                      <div>
                        <div className="font-semibold text-slate-900">{name}</div>
                        <div className="text-[11px] text-slate-500 font-mono">{email}</div>
                      </div>
                    </div>
                  </td>

                  <td className="py-3.5 px-4">
                    {isMatched ? (
                      <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[11px] font-medium bg-emerald-50 text-emerald-700 border border-emerald-200">
                        <UserCheck className="w-3 h-3 text-emerald-600" />
                        Matched
                      </span>
                    ) : (
                      <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[11px] font-medium bg-amber-50 text-amber-700 border border-amber-200">
                        <UserX className="w-3 h-3 text-amber-600" />
                        Unmatched
                      </span>
                    )}
                  </td>

                  <td className="py-3.5 px-4 text-right font-medium text-slate-800">
                    {(c.totalCommits || 0).toLocaleString()}
                  </td>

                  <td className="py-3.5 px-4 text-right font-medium text-emerald-600 font-mono">
                    +{(c.linesAdded || 0).toLocaleString()}
                  </td>

                  <td className="py-3.5 px-4 text-right font-medium text-rose-600 font-mono">
                    -{(c.linesDeleted || 0).toLocaleString()}
                  </td>

                  <td className="py-3.5 px-4 text-right font-bold text-slate-900 font-mono">
                    {(c.netLines || 0).toLocaleString()}
                  </td>

                  <td className="py-3.5 px-4 text-center text-slate-700 font-medium">
                    {c.activeDays || 0}d
                  </td>

                  <td className="py-3.5 px-4">
                    <div className="flex items-center gap-2 justify-center">
                      <div className="w-16 bg-slate-100 rounded-full h-2 overflow-hidden">
                        <div
                          className="bg-blue-600 h-2 rounded-full"
                          style={{ width: `${Math.min(100, Math.max(0, consistency))}%` }}
                        />
                      </div>
                      <span className="text-[11px] font-medium text-slate-600 w-8 text-right">
                        {consistency}%
                      </span>
                    </div>
                  </td>

                  {canMap && (
                    <td className="py-3.5 px-4 text-center">
                      <button
                        type="button"
                        onClick={() => onOpenMappingModal && onOpenMappingModal(c)}
                        className="inline-flex items-center gap-1 px-2.5 py-1 text-[11px] font-medium text-blue-700 bg-blue-50 border border-blue-200 rounded hover:bg-blue-100 transition-colors cursor-pointer"
                        title="Map this Git email to a registered student"
                      >
                        <UserPlus className="w-3 h-3" />
                        Map
                      </button>
                    </td>
                  )}
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
};

