'use client';

import React, { useState, useCallback } from 'react';
import { usePlagiarismAstTree, usePlagiarismComparison } from '../hooks';
import { AstTreeNode, AstMatchStatus } from '../types';
import { AppLink as Link } from '@/components/ui/app-link';
import { 
  GitCommit, 
  ExternalLink, 
  Layers, 
  ChevronRight, 
  ChevronDown, 
  AlertTriangle,
  Info,
  Maximize2,
  FolderTree,
  Minimize2,
  Code2
} from 'lucide-react';

interface AstStructuralComparisonProps {
  recordId: string;
  studentAName?: string;
  studentBName?: string;
  submissionAId?: string;
  submissionBId?: string;
}

function extractSnippet(sourceCode: string | undefined, startLine: number, endLine: number, maxLines = 10): { lines: string[]; startLineNumber: number; totalCount: number } {
  if (!sourceCode) return { lines: [], startLineNumber: startLine, totalCount: 0 };
  const allLines = sourceCode.split('\n');
  const start = Math.max(0, startLine - 1);
  const end = Math.min(allLines.length, endLine);
  const slice = allLines.slice(start, end);
  return {
    lines: slice.slice(0, maxLines),
    startLineNumber: start + 1,
    totalCount: slice.length,
  };
}

function getStatusBadge(status: AstMatchStatus) {
  switch (status) {
    case 'MATCHED_REGION':
      return (
        <span className="text-[10px] px-1.5 py-0.2 rounded font-semibold font-mono bg-amber-100 text-amber-800 dark:bg-amber-950/70 dark:text-amber-200 border border-amber-300 dark:border-amber-700">
          MATCHED REGION
        </span>
      );
    case 'CONTEXT':
      return (
        <span className="text-[10px] px-1.5 py-0.2 rounded font-semibold font-mono bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-300 border border-slate-300 dark:border-slate-700">
          CONTEXT
        </span>
      );
    default:
      return (
        <span className="text-[10px] px-1.5 py-0.2 rounded font-mono text-outline">
          UNMATCHED
        </span>
      );
  }
}

interface TreeNodeItemProps {
  node: AstTreeNode;
  side: 'A' | 'B';
  selectedNode: { node: AstTreeNode; side: 'A' | 'B' } | null;
  selectedEvidenceId: string | null;
  collapsedNodes: Set<string>;
  onToggleCollapse: (nodeId: string) => void;
  onSelectNode: (node: AstTreeNode, side: 'A' | 'B') => void;
  depth?: number;
}

function TreeNodeItem({
  node,
  side,
  selectedNode,
  selectedEvidenceId,
  collapsedNodes,
  onToggleCollapse,
  onSelectNode,
  depth = 0,
}: TreeNodeItemProps) {
  const isCollapsed = collapsedNodes.has(node.nodeId);
  const hasChildren = node.children && node.children.length > 0;
  const isSelected = selectedNode?.node.nodeId === node.nodeId && selectedNode.side === side;
  const isSameEvidence = Boolean(selectedEvidenceId && node.evidenceId === selectedEvidenceId);

  let statusClass = 'hover:bg-surface-container-high border-transparent text-on-surface-variant';
  if (node.matchStatus === 'MATCHED_REGION') {
    statusClass = 'bg-amber-50/80 dark:bg-amber-950/40 border-amber-300/80 dark:border-amber-700 text-amber-950 dark:text-amber-100';
  } else if (node.matchStatus === 'CONTEXT') {
    statusClass = 'bg-surface-container-low border-outline-variant/30 text-on-surface';
  }

  if (isSelected) {
    statusClass += ' ring-2 ring-primary shadow-xs';
  } else if (isSameEvidence) {
    statusClass += ' ring-1 ring-amber-400 bg-amber-100/50 dark:bg-amber-900/30';
  }

  return (
    <div className="flex flex-col select-none">
      <div 
        className={`flex items-center gap-1.5 px-2 py-1.5 rounded-lg border text-xs transition-all cursor-pointer ${statusClass}`}
        style={{ paddingLeft: `${Math.min(depth * 14 + 8, 120)}px` }}
        onClick={() => onSelectNode(node, side)}
      >
        {/* Expand / Collapse Toggle button */}
        {hasChildren ? (
          <button
            type="button"
            onClick={(e) => {
              e.stopPropagation();
              onToggleCollapse(node.nodeId);
            }}
            className="p-0.5 rounded hover:bg-black/10 dark:hover:bg-white/10 text-outline hover:text-on-surface transition-colors shrink-0"
            aria-label={isCollapsed ? 'Mở rộng nhánh' : 'Thu gọn nhánh'}
          >
            {isCollapsed ? <ChevronRight className="w-3.5 h-3.5" /> : <ChevronDown className="w-3.5 h-3.5" />}
          </button>
        ) : (
          <span className="w-3.5 h-3.5 shrink-0" />
        )}

        {/* Node Type and Line Range */}
        <div className="flex items-center gap-1.5 min-w-0 flex-1">
          <span className="font-mono font-bold text-[11px] truncate">
            {node.nodeType}
          </span>
          <span className="font-mono text-[10px] text-outline shrink-0">
            L{node.startLine}&ndash;{node.endLine}
          </span>
          <span className="text-[11px] text-on-surface-variant truncate hidden sm:inline" title={node.label}>
            {node.label}
          </span>
        </div>

        {/* Node Status Badge */}
        <div className="shrink-0 flex items-center gap-1">
          {node.hasHiddenChildren && (
            <span className="text-[9px] px-1 py-0.2 rounded bg-amber-200 dark:bg-amber-900 text-amber-900 dark:text-amber-100 font-mono" title="Có nút con bị ẩn do giới hạn an toàn">
              +ẩn
            </span>
          )}
          {getStatusBadge(node.matchStatus)}
        </div>
      </div>

      {/* Render Children Recursively if not collapsed */}
      {hasChildren && !isCollapsed && (
        <div className="flex flex-col mt-0.5 space-y-0.5 border-l border-outline-variant/20 ml-3">
          {node.children.map((child) => (
            <TreeNodeItem
              key={child.nodeId}
              node={child}
              side={side}
              selectedNode={selectedNode}
              selectedEvidenceId={selectedEvidenceId}
              collapsedNodes={collapsedNodes}
              onToggleCollapse={onToggleCollapse}
              onSelectNode={onSelectNode}
              depth={depth + 1}
            />
          ))}
        </div>
      )}
    </div>
  );
}

export function AstStructuralComparison({
  recordId,
  studentAName = 'Sinh viên A',
  studentBName = 'Sinh viên B',
  submissionAId,
  submissionBId,
}: AstStructuralComparisonProps) {
  const { data: treeData, isLoading: isTreeLoading, isError: isTreeError, error: treeError } = usePlagiarismAstTree(recordId);
  const { data: compareData, isLoading: isCompareLoading } = usePlagiarismComparison(recordId);

  // Selected node state
  const [selectedNode, setSelectedNode] = useState<{ node: AstTreeNode; side: 'A' | 'B' } | null>(null);
  const [collapsedNodes, setCollapsedNodes] = useState<Set<string>>(new Set());

  const handleToggleCollapse = useCallback((nodeId: string) => {
    setCollapsedNodes((prev) => {
      const next = new Set(prev);
      if (next.has(nodeId)) {
        next.delete(nodeId);
      } else {
        next.add(nodeId);
      }
      return next;
    });
  }, []);

  const handleExpandAll = useCallback(() => {
    setCollapsedNodes(new Set());
  }, []);

  const handleCollapseAll = useCallback(() => {
    const allParentIds = new Set<string>();
    const collectIds = (node: AstTreeNode | null | undefined) => {
      if (!node) return;
      if (node.children && node.children.length > 0) {
        allParentIds.add(node.nodeId);
        for (const child of node.children) {
          collectIds(child);
        }
      }
    };
    collectIds(treeData?.treeA);
    collectIds(treeData?.treeB);
    setCollapsedNodes(allParentIds);
  }, [treeData?.treeA, treeData?.treeB]);

  // Handle node selection
  const handleSelectNode = useCallback((node: AstTreeNode, side: 'A' | 'B') => {
    setSelectedNode({ node, side });
  }, []);

  // Determine active evidence ID from selected node
  const activeEvidenceId = selectedNode?.node.evidenceId || null;

  // Evidence resolution for the selected node with strict academic rigor
  let correspondingMatch: {
    startLineA: number;
    endLineA: number;
    startLineB: number;
    endLineB: number;
    type: string;
  } | null = null;
  let matchNotice: string | null = null;

  if (selectedNode) {
    const node = selectedNode.node;
    if (node.matchStatus === 'UNMATCHED') {
      matchNotice = 'Nút cú pháp này không nằm trong vùng bằng chứng tương đồng (UNMATCHED). Không có đoạn mã đối ứng.';
    } else if (node.matchStatus === 'CONTEXT') {
      matchNotice = 'Nút tổ tiên ngữ cảnh (CONTEXT) bao quanh khối mã; không cấu thành một cặp trùng khớp tương đồng 1:1.';
    } else if (node.matchStatus === 'MATCHED_REGION' && compareData?.matches) {
      // Find candidate evidence matches that intersect with this node's line range
      const candidates = compareData.matches.filter(m => {
        if (selectedNode.side === 'A') {
          return m.startLineA <= node.endLine && node.startLine <= m.endLineA;
        } else {
          return m.startLineB <= node.endLine && node.startLine <= m.endLineB;
        }
      });

      if (candidates.length === 1) {
        correspondingMatch = candidates[0];
      } else if (candidates.length > 1) {
        // Disambiguate overlapping evidence spans by narrowest span (tightest local match)
        const sorted = [...candidates].sort((a, b) => {
          const spanA = selectedNode.side === 'A' ? (a.endLineA - a.startLineA) : (a.endLineB - a.startLineB);
          const spanB = selectedNode.side === 'A' ? (b.endLineA - b.startLineA) : (b.endLineB - b.startLineB);
          return spanA - spanB;
        });
        correspondingMatch = sorted[0];
        matchNotice = `Khu vực giao thoa giữa ${candidates.length} dải bằng chứng tương đồng; hiển thị dải khớp hẹp nhất.`;
      } else {
        matchNotice = 'Không tìm thấy dải bằng chứng tương ứng trong dữ liệu so sánh.';
      }
    }
  }

  // Extract snippet for Side A
  let snippetA = null;
  if (compareData?.sourceCodeA) {
    if (selectedNode?.side === 'A') {
      snippetA = extractSnippet(compareData.sourceCodeA, selectedNode.node.startLine, selectedNode.node.endLine);
    } else if (correspondingMatch) {
      snippetA = extractSnippet(compareData.sourceCodeA, correspondingMatch.startLineA, correspondingMatch.endLineA);
    }
  }

  // Extract snippet for Side B
  let snippetB = null;
  if (compareData?.sourceCodeB) {
    if (selectedNode?.side === 'B') {
      snippetB = extractSnippet(compareData.sourceCodeB, selectedNode.node.startLine, selectedNode.node.endLine);
    } else if (correspondingMatch) {
      snippetB = extractSnippet(compareData.sourceCodeB, correspondingMatch.startLineB, correspondingMatch.endLineB);
    }
  }

  // Loading State
  if (isTreeLoading || isCompareLoading) {
    return (
      <div className="bg-surface-container-lowest p-space-lg rounded-2xl border border-outline-variant/40 shadow-xs">
        <div className="flex items-center gap-3 mb-4">
          <div className="w-5 h-5 rounded-full border-2 border-primary border-t-transparent animate-spin" />
          <h3 className="font-headline-sm text-headline-sm text-on-surface font-semibold">
            Đang phân tích cây cú pháp AST phân cấp song song...
          </h3>
        </div>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div className="h-64 rounded-xl bg-surface-container-low animate-pulse" />
          <div className="h-64 rounded-xl bg-surface-container-low animate-pulse" />
        </div>
      </div>
    );
  }

  // Error State
  if (isTreeError) {
    return (
      <div className="bg-surface-container-lowest p-space-lg rounded-2xl border border-outline-variant/40 shadow-xs">
        <div className="flex items-start gap-3 text-rose-600">
          <AlertTriangle className="w-5 h-5 shrink-0 mt-0.5" />
          <div>
            <h3 className="font-title-md text-on-surface font-bold">
              Không thể tải cây cú pháp AST phân cấp
            </h3>
            <p className="text-xs text-on-surface-variant mt-1">
              {(treeError as Error)?.message || 'Vui lòng thử lại sau.'}
            </p>
          </div>
        </div>
      </div>
    );
  }

  // Parse Problem / Special States from backend
  if (treeData && treeData.parseStatus !== 'SUCCESS') {
    return (
      <div className="bg-surface-container-lowest p-space-lg rounded-2xl border border-outline-variant/40 shadow-xs space-y-3">
        <div className="flex items-center gap-2">
          <Layers className="w-5 h-5 text-primary" />
          <h3 className="font-headline-sm text-headline-sm text-on-surface font-bold">
            Đối chiếu Cây Cú pháp AST Phân cấp Song song
          </h3>
        </div>
        <div className="p-4 rounded-xl bg-amber-50 dark:bg-amber-950/40 border border-amber-300 dark:border-amber-700 text-xs flex items-start gap-3">
          <AlertTriangle className="w-5 h-5 text-amber-600 shrink-0 mt-0.5" />
          <div className="space-y-1">
            <span className="font-bold text-amber-900 dark:text-amber-100">
              {treeData.parseStatus === 'EMPTY_SOURCE' && 'Không có mã nguồn để dựng cây cú pháp AST'}
              {treeData.parseStatus === 'FILE_TOO_LARGE' && 'Kích thước tập tin vượt quá giới hạn an toàn để phân tích AST'}
              {treeData.parseStatus.startsWith('SYNTAX_ERROR') && 'Mã nguồn chứa lỗi cú pháp Java không thể phân tích cây AST'}
            </span>
            <p className="text-amber-800 dark:text-amber-200">
              {treeData.parseErrorMessage || 'Vui lòng kiểm tra lại mã nguồn gốc của bài thi.'}
            </p>
          </div>
        </div>
        <div className="flex justify-end">
          <Link
            href={`/teacher/plagiarism/${recordId}/compare`}
            className="px-3.5 py-1.5 rounded-lg bg-primary text-on-primary text-xs font-semibold flex items-center gap-1.5 shadow-xs"
          >
            <span>Xem mã nguồn thô trên trình Code Diff</span>
            <ExternalLink className="w-3.5 h-3.5" />
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className="bg-surface-container-lowest p-space-lg rounded-2xl border border-outline-variant/40 shadow-xs space-y-space-md">
      {/* Header and Controls */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-3 pb-3 border-b border-outline-variant/20">
        <div>
          <div className="flex items-center gap-2">
            <span className="p-1.5 rounded-lg bg-primary/10 text-primary">
              <FolderTree className="w-4 h-4" />
            </span>
            <h3 className="font-headline-sm text-headline-sm text-on-surface font-bold">
              Đối chiếu Cây Cú pháp AST Phân cấp Song song
            </h3>
          </div>
          <p className="text-xs text-on-surface-variant mt-1">
            Duyệt cây cú pháp phân cấp thực tế từ JavaParser. Các vùng chứa dải bằng chứng tương đồng được tô sáng đồng bộ.
          </p>
        </div>

        <div className="flex items-center gap-2 shrink-0 flex-wrap">
          <button
            type="button"
            onClick={handleExpandAll}
            className="px-2.5 py-1.5 rounded-lg bg-surface-container hover:bg-surface-container-high text-on-surface text-xs font-medium flex items-center gap-1 border border-outline-variant/30 cursor-pointer"
          >
            <Maximize2 className="w-3 h-3 text-outline" />
            <span>Mở rộng tất cả</span>
          </button>
          <button
            type="button"
            onClick={handleCollapseAll}
            className="px-2.5 py-1.5 rounded-lg bg-surface-container hover:bg-surface-container-high text-on-surface text-xs font-medium flex items-center gap-1 border border-outline-variant/30 cursor-pointer"
          >
            <Minimize2 className="w-3 h-3 text-outline" />
            <span>Thu gọn tất cả</span>
          </button>
          <Link
            href={`/teacher/plagiarism/${recordId}/compare`}
            className="px-3 py-1.5 rounded-lg bg-primary hover:bg-primary/90 text-on-primary text-xs font-semibold flex items-center gap-1.5 shadow-xs"
          >
            <span>Toàn bộ Code Diff</span>
            <ExternalLink className="w-3.5 h-3.5" />
          </Link>
        </div>
      </div>

      {/* Truncation Safety Warning Notice */}
      {treeData?.isTruncated && (
        <div className="p-3 rounded-xl bg-amber-50 dark:bg-amber-950/40 border border-amber-300 dark:border-amber-700 text-xs flex items-start gap-2.5 text-amber-900 dark:text-amber-100">
          <Info className="w-4 h-4 text-amber-600 shrink-0 mt-0.5" />
          <div>
            <span className="font-semibold">Cây cú pháp đã được giới hạn an toàn: </span>
            <span>{treeData.truncationNotice || 'Một số nút con sâu đã được ẩn để duy trì hiệu năng mượt mà.'}</span>
          </div>
        </div>
      )}

      {/* Metrics Row */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 text-xs">
        <div className="p-3 rounded-xl bg-surface-container-low border border-outline-variant/20">
          <span className="text-on-surface-variant block font-medium">Nút hiển thị / Thực tế (A)</span>
          <span className="text-sm font-bold font-mono text-primary mt-0.5 block">
            {treeData?.displayedNodesA || 0} / {treeData?.totalCountedNodesA || 0}
          </span>
        </div>
        <div className="p-3 rounded-xl bg-surface-container-low border border-outline-variant/20">
          <span className="text-on-surface-variant block font-medium">Nút hiển thị / Thực tế (B)</span>
          <span className="text-sm font-bold font-mono text-primary mt-0.5 block">
            {treeData?.displayedNodesB || 0} / {treeData?.totalCountedNodesB || 0}
          </span>
        </div>
        <div className="p-3 rounded-xl bg-surface-container-low border border-outline-variant/20">
          <span className="text-on-surface-variant block font-medium">Vùng trùng khớp (k-gram)</span>
          <span className="text-sm font-bold font-mono text-amber-700 dark:text-amber-400 mt-0.5 block">
            {treeData?.matchedRegionNodesCount || 0} nút
          </span>
        </div>
        <div className="p-3 rounded-xl bg-surface-container-low border border-outline-variant/20">
          <span className="text-on-surface-variant block font-medium">Quy chuẩn học thuật</span>
          <span className="text-xs font-semibold text-emerald-700 dark:text-emerald-400 mt-0.5 block">
            Chứng cứ tham vấn
          </span>
        </div>
      </div>

      {/* Side-by-Side Dual Tree View */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        {/* Left: Tree A */}
        <div className="flex flex-col border border-outline-variant/30 rounded-xl overflow-hidden bg-surface-container-low">
          <div className="bg-surface-container-high px-3.5 py-2 flex items-center justify-between border-b border-outline-variant/30 text-xs">
            <div className="flex items-center gap-2">
              <span className="w-2.5 h-2.5 rounded-full bg-blue-600" />
              <span className="font-bold text-on-surface truncate max-w-[200px]" title={studentAName}>
                Cây AST: {studentAName}
              </span>
            </div>
            <span className="font-mono text-[11px] text-outline">
              #{submissionAId ? submissionAId.substring(0, 8) : 'A'}
            </span>
          </div>

          <div data-testid="ast-tree-panel-a" className="p-2.5 overflow-y-auto max-h-[460px] space-y-1">
            {treeData?.treeA ? (
              <TreeNodeItem
                node={treeData.treeA}
                side="A"
                selectedNode={selectedNode}
                selectedEvidenceId={activeEvidenceId}
                collapsedNodes={collapsedNodes}
                onToggleCollapse={handleToggleCollapse}
                onSelectNode={handleSelectNode}
              />
            ) : (
              <p className="text-xs text-outline p-4 text-center">Không có cây cú pháp cho bài nộp A.</p>
            )}
          </div>
        </div>

        {/* Right: Tree B */}
        <div className="flex flex-col border border-outline-variant/30 rounded-xl overflow-hidden bg-surface-container-low">
          <div className="bg-surface-container-high px-3.5 py-2 flex items-center justify-between border-b border-outline-variant/30 text-xs">
            <div className="flex items-center gap-2">
              <span className="w-2.5 h-2.5 rounded-full bg-sky-600" />
              <span className="font-bold text-on-surface truncate max-w-[200px]" title={studentBName}>
                Cây AST: {studentBName}
              </span>
            </div>
            <span className="font-mono text-[11px] text-outline">
              #{submissionBId ? submissionBId.substring(0, 8) : 'B'}
            </span>
          </div>

          <div data-testid="ast-tree-panel-b" className="p-2.5 overflow-y-auto max-h-[460px] space-y-1">
            {treeData?.treeB ? (
              <TreeNodeItem
                node={treeData.treeB}
                side="B"
                selectedNode={selectedNode}
                selectedEvidenceId={activeEvidenceId}
                collapsedNodes={collapsedNodes}
                onToggleCollapse={handleToggleCollapse}
                onSelectNode={handleSelectNode}
              />
            ) : (
              <p className="text-xs text-outline p-4 text-center">Không có cây cú pháp cho bài nộp B.</p>
            )}
          </div>
        </div>
      </div>

      {/* Selected Node Details & Dual Code Snippet Inspector */}
      <div className="p-4 rounded-xl bg-surface-container-low border border-outline-variant/30 shadow-xs space-y-3">
        <div className="flex flex-wrap items-center justify-between gap-2 pb-2 border-b border-outline-variant/20">
          <div className="flex items-center gap-2">
            <GitCommit className="w-4 h-4 text-primary" />
            <span className="text-xs font-bold text-on-surface">
              {selectedNode ? (
                <>
                  Nút đang chọn: <strong className="font-mono text-primary">{selectedNode.node.nodeType}</strong>{' '}
                  <span className="text-outline font-normal">
                    (Bài {selectedNode.side}, Dòng {selectedNode.node.startLine}&ndash;{selectedNode.node.endLine})
                  </span>
                </>
              ) : (
                'Chọn một nút AST trên cây để đối chiếu trích đoạn mã nguồn thực tế'
              )}
            </span>
          </div>

          {selectedNode && (
            <div className="flex items-center gap-2">
              {getStatusBadge(selectedNode.node.matchStatus)}
              {selectedNode.node.evidenceId && (
                <span className="text-[10px] px-2 py-0.5 rounded bg-surface-container-high font-mono text-outline">
                  Evidence #{selectedNode.node.evidenceId.substring(0, 8)}
                </span>
              )}
            </div>
          )}
        </div>

        {matchNotice && selectedNode && selectedNode.node.matchStatus === 'MATCHED_REGION' && (
          <div className="text-[11px] px-3 py-1.5 rounded-lg bg-amber-500/10 text-amber-700 dark:text-amber-400 border border-amber-500/20 flex items-center gap-1.5">
            <Info className="w-3.5 h-3.5 shrink-0" />
            <span>{matchNotice}</span>
          </div>
        )}

        {/* Dual Code Snippet Display */}
        {selectedNode ? (
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs">
            {/* Snippet A */}
            <div className="flex flex-col border border-outline-variant/30 rounded-lg overflow-hidden bg-slate-950 text-slate-100">
              <div className="bg-slate-900 px-3 py-1.5 flex items-center justify-between text-[11px] font-mono border-b border-slate-800 text-slate-300">
                <span className="font-semibold text-blue-400 truncate max-w-[160px]">{studentAName}</span>
                <span className="text-slate-400">
                  {snippetA ? `Dòng ${snippetA.startLineNumber}–${snippetA.startLineNumber + snippetA.lines.length - 1}` : 'N/A'}
                </span>
              </div>
              <div className="p-3 font-mono text-[11px] overflow-x-auto leading-relaxed max-h-56 select-text">
                {snippetA && snippetA.lines.length > 0 ? (
                  snippetA.lines.map((line, lIdx) => (
                    <div key={lIdx} className="flex">
                      <span className="text-slate-600 mr-2.5 inline-block w-5 text-right select-none shrink-0">
                        {snippetA.startLineNumber + lIdx}
                      </span>
                      <span className="text-slate-200 whitespace-pre">{line || ' '}</span>
                    </div>
                  ))
                ) : (
                  <span className="text-slate-400 italic text-[11px] block p-2">
                    {matchNotice || 'Không có mã nguồn tương ứng.'}
                  </span>
                )}
                {snippetA && snippetA.totalCount > snippetA.lines.length && (
                  <div className="text-[10px] text-slate-500 italic mt-1 pt-1 border-t border-slate-800">
                    + {snippetA.totalCount - snippetA.lines.length} dòng khác...
                  </div>
                )}
              </div>
            </div>

            {/* Snippet B */}
            <div className="flex flex-col border border-outline-variant/30 rounded-lg overflow-hidden bg-slate-950 text-slate-100">
              <div className="bg-slate-900 px-3 py-1.5 flex items-center justify-between text-[11px] font-mono border-b border-slate-800 text-slate-300">
                <span className="font-semibold text-sky-400 truncate max-w-[160px]">{studentBName}</span>
                <span className="text-slate-400">
                  {snippetB ? `Dòng ${snippetB.startLineNumber}–${snippetB.startLineNumber + snippetB.lines.length - 1}` : 'N/A'}
                </span>
              </div>
              <div className="p-3 font-mono text-[11px] overflow-x-auto leading-relaxed max-h-56 select-text">
                {snippetB && snippetB.lines.length > 0 ? (
                  snippetB.lines.map((line, lIdx) => (
                    <div key={lIdx} className="flex">
                      <span className="text-slate-600 mr-2.5 inline-block w-5 text-right select-none shrink-0">
                        {snippetB.startLineNumber + lIdx}
                      </span>
                      <span className="text-slate-200 whitespace-pre">{line || ' '}</span>
                    </div>
                  ))
                ) : (
                  <span className="text-slate-400 italic text-[11px] block p-2">
                    {matchNotice || 'Không có mã nguồn tương ứng.'}
                  </span>
                )}
                {snippetB && snippetB.totalCount > snippetB.lines.length && (
                  <div className="text-[10px] text-slate-500 italic mt-1 pt-1 border-t border-slate-800">
                    + {snippetB.totalCount - snippetB.lines.length} dòng khác...
                  </div>
                )}
              </div>
            </div>
          </div>
        ) : (
          <div className="p-6 text-center rounded-lg bg-surface-container border border-outline-variant/20 text-xs text-on-surface-variant flex flex-col items-center gap-1.5">
            <Code2 className="w-5 h-5 text-outline" />
            <p>Nhấp vào bất kỳ nút nào trên Cây AST (bài A hoặc bài B) để xem trích đoạn mã nguồn và đối chiếu dải dòng.</p>
          </div>
        )}
      </div>
    </div>
  );
}
