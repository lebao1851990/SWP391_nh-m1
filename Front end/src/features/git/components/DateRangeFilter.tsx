'use client';

import React, { useState } from 'react';
import { Calendar, Filter, X } from 'lucide-react';

interface DateRangeFilterProps {
  initialSince?: string;
  initialUntil?: string;
  onFilterChange: (since?: string, until?: string) => void;
}

export const DateRangeFilter: React.FC<DateRangeFilterProps> = ({
  initialSince = '',
  initialUntil = '',
  onFilterChange,
}) => {
  const [since, setSince] = useState(initialSince ? initialSince.substring(0, 10) : '');
  const [until, setUntil] = useState(initialUntil ? initialUntil.substring(0, 10) : '');

  const applyPreset = (days: number) => {
    const end = new Date();
    const start = new Date();
    start.setDate(end.getDate() - days);

    const sStr = start.toISOString().substring(0, 10);
    const uStr = end.toISOString().substring(0, 10);

    setSince(sStr);
    setUntil(uStr);
    onFilterChange(new Date(sStr).toISOString(), new Date(uStr + 'T23:59:59.999Z').toISOString());
  };

  const handleApply = () => {
    const sIso = since ? new Date(since + 'T00:00:00.000Z').toISOString() : undefined;
    const uIso = until ? new Date(until + 'T23:59:59.999Z').toISOString() : undefined;
    onFilterChange(sIso, uIso);
  };

  const handleReset = () => {
    setSince('');
    setUntil('');
    onFilterChange(undefined, undefined);
  };

  return (
    <div className="bg-white rounded-lg border border-slate-200 p-3.5 mb-6 flex flex-col sm:flex-row items-stretch sm:items-center justify-between gap-3 text-xs shadow-xs">
      <div className="flex items-center gap-2 text-slate-700 font-medium">
        <Filter className="w-4 h-4 text-slate-500" />
        <span>Date Range Filter:</span>
      </div>

      <div className="flex flex-wrap items-center gap-2">
        <div className="flex items-center gap-1 bg-slate-50 border border-slate-200 px-2 py-1 rounded">
          <Calendar className="w-3.5 h-3.5 text-slate-400" />
          <input
            type="date"
            value={since}
            onChange={(e) => setSince(e.target.value)}
            className="bg-transparent text-slate-800 outline-none text-xs"
            placeholder="Start date"
          />
        </div>
        <span className="text-slate-400">to</span>
        <div className="flex items-center gap-1 bg-slate-50 border border-slate-200 px-2 py-1 rounded">
          <Calendar className="w-3.5 h-3.5 text-slate-400" />
          <input
            type="date"
            value={until}
            onChange={(e) => setUntil(e.target.value)}
            className="bg-transparent text-slate-800 outline-none text-xs"
            placeholder="End date"
          />
        </div>

        <button
          type="button"
          onClick={handleApply}
          className="px-3 py-1 bg-blue-600 text-white rounded font-medium hover:bg-blue-700 transition-colors cursor-pointer"
        >
          Apply
        </button>

        {(since || until) && (
          <button
            type="button"
            onClick={handleReset}
            className="p-1 text-slate-400 hover:text-slate-600 rounded transition-colors cursor-pointer"
            title="Clear filters"
          >
            <X className="w-4 h-4" />
          </button>
        )}
      </div>

      <div className="flex items-center gap-1.5 flex-wrap">
        <span className="text-slate-400 text-[11px]">Presets:</span>
        <button
          type="button"
          onClick={() => applyPreset(7)}
          className="px-2 py-0.5 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded text-[11px] transition-colors"
        >
          7D
        </button>
        <button
          type="button"
          onClick={() => applyPreset(14)}
          className="px-2 py-0.5 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded text-[11px] transition-colors"
        >
          14D
        </button>
        <button
          type="button"
          onClick={() => applyPreset(30)}
          className="px-2 py-0.5 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded text-[11px] transition-colors"
        >
          30D
        </button>
        <button
          type="button"
          onClick={handleReset}
          className="px-2 py-0.5 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded text-[11px] transition-colors"
        >
          All Time
        </button>
      </div>
    </div>
  );
};

