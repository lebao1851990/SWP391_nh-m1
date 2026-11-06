'use client';

import React from 'react';
import { Calendar, BarChart2 } from 'lucide-react';
import { ContributionTimelinePoint } from '../types';

interface ContributionTimelineChartProps {
  timeline: ContributionTimelinePoint[];
}

export const ContributionTimelineChart: React.FC<ContributionTimelineChartProps> = ({ timeline }) => {
  if (!timeline || timeline.length === 0) {
    return (
      <div className="bg-white rounded-xl border border-slate-200 p-6 text-center text-slate-500 text-xs">
        No commit activity timeline available.
      </div>
    );
  }

  // Calculate maximum commit count for bar scaling
  const maxCommits = Math.max(
    ...timeline.map((t) => t.commitCount ?? (t as unknown as { totalCommits?: number }).totalCommits ?? 1),
    1
  );

  return (
    <div className="bg-white rounded-xl border border-slate-200 shadow-xs p-5 mb-6">
      <div className="flex items-center justify-between pb-4 border-b border-slate-100 mb-4">
        <h3 className="font-bold text-slate-900 text-base flex items-center gap-2">
          <BarChart2 className="w-4 h-4 text-blue-600" />
          Contribution Timeline
        </h3>
        <span className="text-xs text-slate-500">
          {timeline.length} {timeline.length === 1 ? 'active day' : 'active days'}
        </span>
      </div>

      <div className="space-y-3">
        {timeline.map((point, idx) => {
          const dateLabel = point.period || (point as unknown as { date?: string }).date || `Period ${idx + 1}`;
          const commits = point.commitCount ?? (point as unknown as { totalCommits?: number }).totalCommits ?? 0;
          const percentage = Math.round((commits / maxCommits) * 100);

          return (
            <div key={dateLabel} className="text-xs">
              <div className="flex items-center justify-between text-slate-600 mb-1">
                <span className="font-mono font-medium text-slate-800 flex items-center gap-1.5">
                  <Calendar className="w-3.5 h-3.5 text-slate-400" />
                  {dateLabel}
                </span>
                <div className="flex items-center gap-3">
                  <span className="font-semibold text-slate-900">
                    {commits} {commits === 1 ? 'commit' : 'commits'}
                  </span>
                  <span className="text-emerald-600 font-mono">+{point.linesAdded}</span>
                  <span className="text-rose-600 font-mono">-{point.linesDeleted}</span>
                </div>
              </div>

              {/* Progress Bar representing relative volume */}
              <div className="w-full bg-slate-100 h-2.5 rounded-full overflow-hidden flex">
                <div
                  className="bg-blue-600 h-full rounded-full transition-all duration-300"
                  style={{ width: `${Math.max(5, Math.min(100, percentage))}%` }}
                />
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
};

