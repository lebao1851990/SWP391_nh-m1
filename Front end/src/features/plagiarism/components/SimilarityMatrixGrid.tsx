'use client';

import React, { useMemo, useState } from 'react';
import { PlagiarismRecord } from '../types';
import { AppLink as Link } from '@/components/ui/app-link';
import { ExternalLink, Info } from 'lucide-react';

interface SimilarityMatrixGridProps {
  records: PlagiarismRecord[];
}

interface StudentNode {
  id: string;
  name: string;
}

export function SimilarityMatrixGrid({ records }: SimilarityMatrixGridProps) {
  const [hoveredCell, setHoveredCell] = useState<{
    rowStudent: StudentNode;
    colStudent: StudentNode;
    pairId?: string;
    rate?: number;
    isFlagged?: boolean;
    x: number;
    y: number;
  } | null>(null);

  const [cellSize, setCellSize] = useState<'sm' | 'md' | 'lg'>('md');

  // Extract distinct students from records
  const students = useMemo<StudentNode[]>(() => {
    const studentMap = new Map<string, string>();
    records.forEach((r) => {
      const p = r.pair;
      if (p.studentAId) {
        studentMap.set(p.studentAId, p.studentAName || p.studentAId);
      }
      if (p.studentBId) {
        studentMap.set(p.studentBId, p.studentBName || p.studentBId);
      }
    });

    return Array.from(studentMap.entries())
      .map(([id, name]) => ({ id, name }))
      .sort((a, b) => a.name.localeCompare(b.name));
  }, [records]);

  // Build pairwise lookup map: key = `${studentIdA}__${studentIdB}` (symmetrical)
  const pairLookup = useMemo(() => {
    const map = new Map<string, { pairId: string; rate: number; isFlagged: boolean }>();
    records.forEach((r) => {
      const p = r.pair;
      const key1 = `${p.studentAId}__${p.studentBId}`;
      const key2 = `${p.studentBId}__${p.studentAId}`;
      const data = { pairId: p.pairId, rate: p.similarityRate, isFlagged: p.isFlagged };
      map.set(key1, data);
      map.set(key2, data);
    });
    return map;
  }, [records]);

  if (students.length === 0) {
    return (
      <div className="p-12 text-center text-outline text-xs bg-surface-container-lowest rounded-2xl border border-outline-variant/40">
        No student submissions available to construct the AST similarity matrix.
      </div>
    );
  }

  const sizeClasses = {
    sm: 'w-10 h-10 text-[10px]',
    md: 'w-14 h-14 text-xs',
    lg: 'w-20 h-20 text-sm font-semibold',
  };

  return (
    <div className="flex flex-col gap-4 bg-surface-container-lowest p-5 rounded-2xl border border-outline-variant/40 shadow-xs">
      {/* Header controls & Legend */}
      <div className="flex flex-col md:flex-row items-start md:items-center justify-between gap-3 border-b border-outline-variant/30 pb-4">
        <div>
          <h3 className="font-headline-sm text-headline-sm text-on-surface font-semibold">
            All-Pairs AST Similarity Matrix ({students.length} &times; {students.length})
          </h3>
          <p className="text-xs text-on-surface-variant mt-0.5">
            Cross-tabulation of AST sub-tree isomorphism scores across cohort submissions. Select any cell to review side-by-side evidence.
          </p>
        </div>

        <div className="flex items-center gap-3 self-end md:self-center">
          <div className="flex items-center gap-1 bg-surface-container-low p-1 rounded-lg border border-outline-variant/30 text-xs">
            <span className="text-[11px] text-on-surface-variant px-1.5 font-medium">Cell Size:</span>
            <button
              onClick={() => setCellSize('sm')}
              className={`px-2 py-0.5 rounded text-xs font-mono transition-all ${
                cellSize === 'sm' ? 'bg-primary text-on-primary font-bold shadow-xs' : 'text-on-surface hover:bg-surface-container'
              }`}
            >
              S
            </button>
            <button
              onClick={() => setCellSize('md')}
              className={`px-2 py-0.5 rounded text-xs font-mono transition-all ${
                cellSize === 'md' ? 'bg-primary text-on-primary font-bold shadow-xs' : 'text-on-surface hover:bg-surface-container'
              }`}
            >
              M
            </button>
            <button
              onClick={() => setCellSize('lg')}
              className={`px-2 py-0.5 rounded text-xs font-mono transition-all ${
                cellSize === 'lg' ? 'bg-primary text-on-primary font-bold shadow-xs' : 'text-on-surface hover:bg-surface-container'
              }`}
            >
              L
            </button>
          </div>
        </div>
      </div>

      {/* Heatmap Legend */}
      <div className="flex flex-wrap items-center gap-4 text-xs bg-surface-container-low/50 px-3.5 py-2 rounded-xl border border-outline-variant/20">
        <span className="font-semibold text-on-surface text-[11px] uppercase tracking-wider">Similarity Heatmap:</span>
        <div className="flex items-center gap-1.5">
          <span className="w-3.5 h-3.5 rounded bg-rose-100 border border-rose-300 inline-block"></span>
          <span className="text-rose-700 font-semibold">&ge; 70% Critical (Review Flagged)</span>
        </div>
        <div className="flex items-center gap-1.5">
          <span className="w-3.5 h-3.5 rounded bg-amber-100 border border-amber-300 inline-block"></span>
          <span className="text-amber-700 font-medium">40% &ndash; 69% Moderate (Advisory)</span>
        </div>
        <div className="flex items-center gap-1.5">
          <span className="w-3.5 h-3.5 rounded bg-emerald-50 border border-emerald-200 inline-block"></span>
          <span className="text-emerald-700 font-medium">&lt; 40% Baseline Distinct</span>
        </div>
        <div className="flex items-center gap-1.5">
          <span className="w-3.5 h-3.5 rounded bg-slate-100 border border-slate-300 inline-block"></span>
          <span className="text-slate-500 font-mono">Self (100%)</span>
        </div>
      </div>

      {/* Matrix Table with Sticky Row/Column Headers */}
      <div className="overflow-x-auto overflow-y-auto max-h-[640px] border border-outline-variant/30 rounded-xl bg-surface-container-lowest select-none relative">
        <table className="border-collapse text-center">
          <thead>
            <tr>
              <th className="sticky top-0 left-0 z-30 bg-surface-container p-2 border-b border-r border-outline-variant/40 text-[11px] font-bold text-on-surface min-w-[120px] shadow-xs">
                Author &darr; / Peer &rarr;
              </th>
              {students.map((colStudent, cIdx) => (
                <th
                  key={colStudent.id}
                  className="sticky top-0 z-20 bg-surface-container p-2 border-b border-r border-outline-variant/30 text-[11px] font-semibold text-on-surface font-mono min-w-[50px] shadow-xs"
                  title={`${colStudent.name} (${colStudent.id})`}
                >
                  <div className="w-full truncate px-1" style={{ maxWidth: cellSize === 'sm' ? '40px' : cellSize === 'md' ? '60px' : '90px' }}>
                    {colStudent.name.split(' ').slice(-1)[0] || `S${cIdx + 1}`}
                  </div>
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {students.map((rowStudent) => (
              <tr key={rowStudent.id}>
                {/* Row Header */}
                <th
                  className="sticky left-0 z-10 bg-surface-container p-2 border-b border-r border-outline-variant/40 text-[11px] font-semibold text-on-surface text-left max-w-[140px] truncate shadow-xs"
                  title={`${rowStudent.name} (${rowStudent.id})`}
                >
                  <div className="truncate font-medium">{rowStudent.name}</div>
                  <div className="font-mono text-[10px] text-on-surface-variant truncate">{rowStudent.id}</div>
                </th>

                {/* Matrix Cells */}
                {students.map((colStudent) => {
                  const isSelf = rowStudent.id === colStudent.id;
                  const pairInfo = pairLookup.get(`${rowStudent.id}__${colStudent.id}`);

                  if (isSelf) {
                    return (
                      <td
                        key={colStudent.id}
                        className={`border-b border-r border-outline-variant/20 bg-slate-100 text-slate-400 font-mono text-[10px] font-semibold cursor-default ${sizeClasses[cellSize]}`}
                        title={`${rowStudent.name}: Self Identity Match`}
                      >
                        &mdash;
                      </td>
                    );
                  }

                  if (!pairInfo) {
                    return (
                      <td
                        key={colStudent.id}
                        className={`border-b border-r border-outline-variant/20 bg-surface-container-lowest text-outline text-[10px] ${sizeClasses[cellSize]}`}
                      >
                        N/A
                      </td>
                    );
                  }

                  const isCritical = Boolean(pairInfo.isFlagged || pairInfo.rate >= 70);
                  const isModerate = !isCritical && pairInfo.rate >= 40;

                  const colorClass = isCritical
                    ? 'bg-rose-50 hover:bg-rose-100 text-rose-700 font-bold border-rose-200'
                    : isModerate
                    ? 'bg-amber-50 hover:bg-amber-100 text-amber-700 font-semibold border-amber-200'
                    : 'bg-emerald-50/40 hover:bg-emerald-100/60 text-emerald-700 border-emerald-100';

                  return (
                    <td
                      key={colStudent.id}
                      className={`border-b border-r border-outline-variant/20 transition-all p-0 cursor-pointer ${colorClass} ${sizeClasses[cellSize]}`}
                      onMouseEnter={(e) => {
                        const rect = e.currentTarget.getBoundingClientRect();
                        setHoveredCell({
                          rowStudent,
                          colStudent,
                          pairId: pairInfo.pairId,
                          rate: pairInfo.rate,
                          isFlagged: pairInfo.isFlagged,
                          x: rect.x + rect.width / 2,
                          y: rect.y,
                        });
                      }}
                      onMouseLeave={() => setHoveredCell(null)}
                    >
                      <Link
                        href={`/teacher/plagiarism/${pairInfo.pairId}`}
                        className="w-full h-full flex items-center justify-center font-mono transition-transform hover:scale-105"
                      >
                        {pairInfo.rate}%
                      </Link>
                    </td>
                  );
                })}
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Floating Hover Tooltip if active */}
      {hoveredCell && (
        <div className="flex items-center justify-between p-3 rounded-xl bg-surface-container-high border border-outline-variant/40 text-xs shadow-xs animate-in fade-in duration-150">
          <div className="flex items-center gap-3">
            <span className="font-semibold text-on-surface">Inspection Pair:</span>
            <span className="font-mono text-on-surface">
              {hoveredCell.rowStudent.name} &times; {hoveredCell.colStudent.name}
            </span>
            <span className="text-outline">&bull;</span>
            <span className="font-mono font-bold text-sm">
              Similarity: {hoveredCell.rate}% {hoveredCell.isFlagged ? '(Critical Flagged)' : ''}
            </span>
          </div>

          {hoveredCell.pairId && (
            <Link
              href={`/teacher/plagiarism/${hoveredCell.pairId}`}
              className="px-3 py-1 bg-primary text-on-primary rounded-md text-xs font-semibold inline-flex items-center gap-1 shadow-xs"
            >
              <span>Inspect Full Diff</span>
              <ExternalLink className="w-3 h-3" />
            </Link>
          )}
        </div>
      )}

      {/* Neutral Scientific Interpretation Notice */}
      <div className="flex items-start gap-3 p-3.5 rounded-xl bg-blue-50/70 border border-blue-200/60 text-xs text-blue-900 leading-relaxed">
        <Info className="w-4 h-4 text-blue-600 shrink-0 mt-0.5" />
        <div className="flex flex-col gap-0.5">
          <span className="font-semibold">Neutral Evidentiary Notice (Thông báo Tham vấn Học thuật):</span>
          <span>
            Similarity indicates structural or textual overlap between submissions. It is a review signal, not proof of plagiarism.
          </span>
          <span className="text-[11px] text-blue-800 italic">
            Độ tương đồng phản ánh sự trùng lặp cấu trúc hoặc cú pháp AST giữa các bài nộp. Đây là tín hiệu tham vấn học thuật dành cho giảng viên đánh giá, không phải bằng chứng khẳng định gian lận.
          </span>
        </div>
      </div>
    </div>
  );
}
