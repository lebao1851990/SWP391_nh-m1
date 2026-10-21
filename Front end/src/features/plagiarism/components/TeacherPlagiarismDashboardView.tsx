'use client';

import React, { useState, useMemo } from 'react';
import { usePlagiarismList } from '../hooks';
import { PlagiarismRecord } from '../types';
import { AppLink as Link } from '@/components/ui/app-link';
import { AxiosError } from 'axios';
import { 
  ShieldAlert, 
  RefreshCw, 
  Activity, 
  ExternalLink, 
  AlertCircle, 
  CheckCircle2, 
  Lock, 
  GitCompare,
  Search,
  X,
  Grid3X3,
  ListFilter,
  Info,
  ChevronLeft,
  ChevronRight,
  Filter
} from 'lucide-react';
import { toast } from 'sonner';
import { SimilarityMatrixGrid } from './SimilarityMatrixGrid';
import { useTranslation } from '@/i18n';

export function TeacherPlagiarismDashboardView() {
  const { t } = useTranslation();
  const { data, isLoading, isError, error, refetch, isRefetching } = usePlagiarismList();
  const [reanalyzing, setReanalyzing] = useState(false);
  const [searchQuery, setSearchQuery] = useState(() => {
    if (typeof window !== 'undefined') {
      return new URLSearchParams(window.location.search).get('search') || '';
    }
    return '';
  });
  const [statusFilter, setStatusFilter] = useState<'ALL' | 'FLAGGED' | 'MODERATE' | 'CLEAN'>('ALL');
  const [selectedExam, setSelectedExam] = useState<string>('ALL');
  const [selectedClass, setSelectedClass] = useState<string>('ALL');
  const [viewMode, setViewMode] = useState<'table' | 'matrix'>('table');
  const [currentPage, setCurrentPage] = useState<number>(1);
  const pageSize = 10;

  const handleRunReanalysis = async () => {
    setReanalyzing(true);
    try {
      await refetch();
      toast.success('Similarity matrix synchronized with latest database records');
    } catch {
      toast.error('Failed to sync similarity records from server');
    } finally {
      setReanalyzing(false);
    }
  };

  const totalPairs = data?.length || 0;
  const flaggedPairs = useMemo(() => data?.filter((p: PlagiarismRecord) => Boolean(p?.pair?.isFlagged)).length || 0, [data]);
  const moderatePairs = useMemo(() => data?.filter((p: PlagiarismRecord) => !p?.pair?.isFlagged && (p?.pair?.similarityRate ?? 0) >= 40).length || 0, [data]);
  const normalPairs = useMemo(() => data?.filter((p: PlagiarismRecord) => !p?.pair?.isFlagged && (p?.pair?.similarityRate ?? 0) < 40).length || 0, [data]);

  // Distinct exams and classes for filtering
  const distinctExams = useMemo(() => {
    if (!data) return [];
    const set = new Set<string>();
    data.forEach(r => {
      if (r.examName) set.add(r.examName);
    });
    return Array.from(set).sort();
  }, [data]);

  const distinctClasses = useMemo(() => {
    if (!data) return [];
    const set = new Set<string>();
    data.forEach(r => {
      if (r.className) set.add(r.className);
    });
    return Array.from(set).sort();
  }, [data]);

  const filteredPairs = useMemo(() => {
    if (!data) return [];
    return data.filter((record: PlagiarismRecord) => {
      const pair = record?.pair;
      if (!pair) return false;

      // Exam Filter
      if (selectedExam !== 'ALL' && record.examName !== selectedExam) return false;

      // Class Filter
      if (selectedClass !== 'ALL' && record.className !== selectedClass) return false;

      // Status Filter tab
      if (statusFilter === 'FLAGGED' && !pair.isFlagged) return false;
      if (statusFilter === 'MODERATE' && (pair.isFlagged || pair.similarityRate < 40)) return false;
      if (statusFilter === 'CLEAN' && (pair.isFlagged || pair.similarityRate >= 40)) return false;

      // Search query
      if (searchQuery.trim()) {
        const q = searchQuery.toLowerCase();
        const aName = (pair.studentAName || '').toLowerCase();
        const bName = (pair.studentBName || '').toLowerCase();
        const aId = (pair.studentAId || '').toLowerCase();
        const bId = (pair.studentBId || '').toLowerCase();
        const pairId = String(pair.pairId);
        return aName.includes(q) || bName.includes(q) || aId.includes(q) || bId.includes(q) || pairId.includes(q);
      }
      return true;
    });
  }, [data, statusFilter, selectedExam, selectedClass, searchQuery]);

  const totalPages = Math.ceil(filteredPairs.length / pageSize) || 1;
  const activePage = Math.min(Math.max(currentPage, 1), totalPages);
  const paginatedPairs = useMemo(() => {
    const start = (activePage - 1) * pageSize;
    return filteredPairs.slice(start, start + pageSize);
  }, [filteredPairs, activePage]);

  if (isLoading) {
    return (
      <div className="flex flex-col h-72 items-center justify-center gap-3">
        <div className="w-8 h-8 rounded-full border-2 border-primary border-t-transparent animate-spin" />
        <p className="text-on-surface-variant font-label-md text-xs">{t.common.loading}</p>
      </div>
    );
  }

  if (isError) {
    const status = (error as AxiosError)?.response?.status;
    const isMalformed = (error as Error)?.message?.toLowerCase().includes('malformed');
    return (
      <div className="max-w-xl mx-auto my-12 p-8 rounded-2xl bg-surface-container-lowest border border-outline-variant/40 shadow-lg text-center flex flex-col items-center gap-3">
        <div className="p-3 rounded-full bg-rose-50 text-rose-600 dark:bg-rose-950/40 dark:text-rose-300">
          {status === 403 ? <Lock className="w-8 h-8" /> : <AlertCircle className="w-8 h-8" />}
        </div>
        <h2 className="font-headline-md text-headline-md text-on-surface">
          {status === 403 ? t.plagiarism.accessForbidden : isMalformed ? "Integration Notice" : t.common.error}
        </h2>
        <p className="font-body-md text-on-surface-variant text-xs">
          {status === 403 
            ? t.errors.forbidden
            : (error as Error).message}
        </p>
        <button
          onClick={() => refetch()}
          className="mt-2 px-4 py-2 bg-primary text-on-primary rounded-lg text-xs font-medium hover:bg-primary/90 transition-all cursor-pointer shadow-xs"
        >
          {isMalformed ? "Retry Connection" : t.common.retry}
        </button>
      </div>
    );
  }

  if (!data) return null;

  return (
    <div className="w-full max-w-7xl mx-auto px-4 sm:px-space-lg py-space-lg flex flex-col gap-space-lg min-w-0">
      {/* Banner Card */}
      <div className="flex flex-col lg:flex-row lg:items-start lg:justify-between gap-space-md bg-surface-container-lowest p-space-lg rounded-2xl border border-outline-variant/40 shadow-xs">
        <div className="flex flex-col gap-1 max-w-3xl">
          <div className="flex items-center gap-1.5 text-xs text-on-surface-variant font-mono">
            <span className="inline-flex items-center gap-1 text-secondary font-semibold">
              <Activity className="w-3.5 h-3.5" />
              AST v4.2 Pipeline
            </span>
            <span>/</span>
            <span className="text-on-surface font-semibold">Integrity Diagnostic Engine</span>
          </div>
          <h1 className="font-headline-xl text-headline-xl text-on-surface tracking-tight mt-1">
            {t.plagiarism.plagiarismDashboardTitle}
          </h1>
          <p className="text-xs sm:text-sm text-on-surface-variant leading-relaxed">
            Automated structural similarity analysis using Abstract Syntax Tree (AST) sub-tree fingerprinting. Compares syntactic structures across all enrolled student submissions.
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-space-xs pt-space-xs lg:pt-0 self-start lg:self-center">
          <button 
            onClick={handleRunReanalysis}
            disabled={reanalyzing || isRefetching}
            className="px-4 py-2 bg-surface-container hover:bg-surface-container-high text-on-surface rounded-lg text-xs font-semibold transition-all inline-flex items-center gap-2 border border-outline-variant/40 shadow-xs cursor-pointer disabled:opacity-50"
          >
            <RefreshCw className={`w-3.5 h-3.5 text-primary ${(reanalyzing || isRefetching) ? 'animate-spin' : ''}`} />
            <span>{reanalyzing || isRefetching ? t.plagiarism.synchronizing : t.plagiarism.syncMatrixButton}</span>
          </button>
        </div>
      </div>

      {/* Prominent Academic Rigor & Neutral Evidentiary Standard Notice */}
      <div className="bg-surface-container-low rounded-2xl p-4 sm:p-5 border border-outline-variant/30 shadow-xs flex items-start gap-4">
        <div className="p-2.5 rounded-xl bg-surface-container-lowest text-primary shadow-xs shrink-0 mt-0.5 border border-outline-variant/30">
          <Info className="w-5 h-5 text-secondary" />
        </div>
        <div className="flex flex-col gap-1 min-w-0">
          <div className="flex items-center gap-2 flex-wrap">
            <span className="font-title-md text-title-md text-on-surface font-bold">
              {t.plagiarism.academicRigorNotice}
            </span>
            <span className="font-mono text-[10px] bg-surface-container-high px-2 py-0.5 rounded font-semibold text-on-surface">
              RBL-MODULE-4
            </span>
          </div>
          <p className="text-xs text-on-surface-variant leading-relaxed">
            &ldquo;<strong>{t.plagiarism.academicRigorQuote}</strong>&rdquo;
          </p>
          <p className="text-xs text-on-surface-variant/90 leading-relaxed italic border-t border-outline-variant/20 pt-1 mt-0.5">
            &ldquo;Độ tương đồng phản ánh sự trùng lặp cấu trúc hoặc cú pháp AST giữa các bài nộp. Đây là tín hiệu tham vấn học thuật dành cho giảng viên đánh giá, không phải bằng chứng khẳng định gian lận.&rdquo;
          </p>
        </div>
      </div>

      {/* 4 Telemetry Metric Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-space-md">
        {/* Card 1: Pairwise Comparisons */}
        <div className="bg-surface-container-lowest p-space-md rounded-xl border border-outline-variant/40 shadow-xs flex flex-col justify-between gap-2">
          <div className="flex items-center justify-between">
            <span className="font-label-xs uppercase tracking-wider text-outline font-semibold">
              {t.plagiarism.pairwiseComparisons}
            </span>
            <div className="p-1.5 rounded-lg bg-blue-50 text-blue-600 dark:bg-blue-950/40 dark:text-blue-300">
              <GitCompare className="w-4 h-4" />
            </div>
          </div>
          <div className="flex flex-col">
            <div className="flex items-baseline gap-1.5">
              <span className="font-display-lg text-display-lg font-bold text-on-surface">{totalPairs}</span>
              <span className="text-xs text-outline font-mono">Evaluated</span>
            </div>
            <p className="text-xs text-on-surface-variant mt-0.5">AST sub-tree pairs verified</p>
          </div>
          <div className="w-full bg-blue-100 dark:bg-blue-950/60 h-1.5 rounded-full overflow-hidden mt-1">
            <div className="bg-blue-600 h-full rounded-full" style={{ width: totalPairs ? '100%' : '0%' }}></div>
          </div>
        </div>

        {/* Card 2: Flagged Pairs */}
        <div className="bg-surface-container-lowest p-space-md rounded-xl border border-outline-variant/40 shadow-xs flex flex-col justify-between gap-2">
          <div className="flex items-center justify-between">
            <span className="font-label-xs uppercase tracking-wider text-outline font-semibold">
              {t.plagiarism.criticalFlaggedTab}
            </span>
            <div className="p-1.5 rounded-lg bg-rose-50 text-rose-600 dark:bg-rose-950/40 dark:text-rose-300">
              <ShieldAlert className="w-4 h-4" />
            </div>
          </div>
          <div className="flex flex-col">
            <div className="flex items-baseline gap-1.5">
              <span className="font-display-lg text-display-lg font-bold text-rose-600 dark:text-rose-400">{flaggedPairs}</span>
              <span className="text-xs text-rose-600 dark:text-rose-400 font-mono">Pairs</span>
            </div>
            <p className="text-xs text-on-surface-variant mt-0.5">Substantial structural congruence</p>
          </div>
          <div className="w-full bg-rose-100 dark:bg-rose-950/60 h-1.5 rounded-full overflow-hidden mt-1">
            <div className="bg-rose-600 h-full rounded-full" style={{ width: totalPairs ? `${(flaggedPairs / totalPairs) * 100}%` : '0%' }}></div>
          </div>
        </div>

        {/* Card 3: Moderate */}
        <div className="bg-surface-container-lowest p-space-md rounded-xl border border-outline-variant/40 shadow-xs flex flex-col justify-between gap-2">
          <div className="flex items-center justify-between">
            <span className="font-label-xs uppercase tracking-wider text-outline font-semibold">
              {t.plagiarism.moderateFlagged}
            </span>
            <div className="p-1.5 rounded-lg bg-amber-50 text-amber-600 dark:bg-amber-950/40 dark:text-amber-300">
              <Activity className="w-4 h-4" />
            </div>
          </div>
          <div className="flex flex-col">
            <div className="flex items-baseline gap-1.5">
              <span className="font-display-lg text-display-lg font-bold text-on-surface">{moderatePairs}</span>
              <span className="text-xs text-outline font-mono">Pairs</span>
            </div>
            <p className="text-xs text-on-surface-variant mt-0.5">Partial node congruence detected</p>
          </div>
          <div className="w-full bg-amber-100 dark:bg-amber-950/60 h-1.5 rounded-full overflow-hidden mt-1">
            <div className="bg-amber-500 h-full rounded-full" style={{ width: totalPairs ? `${(moderatePairs / totalPairs) * 100}%` : '0%' }}></div>
          </div>
        </div>

        {/* Card 4: Normal */}
        <div className="bg-surface-container-lowest p-space-md rounded-xl border border-outline-variant/40 shadow-xs flex flex-col justify-between gap-2">
          <div className="flex items-center justify-between">
            <span className="font-label-xs uppercase tracking-wider text-outline font-semibold">
              {t.plagiarism.normalSimilarity}
            </span>
            <div className="p-1.5 rounded-lg bg-emerald-50 text-emerald-600 dark:bg-emerald-950/40 dark:text-emerald-300">
              <CheckCircle2 className="w-4 h-4" />
            </div>
          </div>
          <div className="flex flex-col">
            <div className="flex items-baseline gap-1.5">
              <span className="font-display-lg text-display-lg font-bold text-on-surface">{normalPairs}</span>
              <span className="text-xs text-outline font-mono">Pairs</span>
            </div>
            <p className="text-xs text-on-surface-variant mt-0.5">Below plagiarism threshold</p>
          </div>
          <div className="w-full bg-emerald-100 dark:bg-emerald-950/60 h-1.5 rounded-full overflow-hidden mt-1">
            <div className="bg-emerald-600 h-full rounded-full" style={{ width: totalPairs ? `${(normalPairs / totalPairs) * 100}%` : '0%' }}></div>
          </div>
        </div>
      </div>

      {/* Main Exploration Section: Filters & View Switcher */}
      <div className="bg-surface-container-lowest rounded-2xl border border-outline-variant/40 shadow-xs overflow-hidden flex flex-col w-full min-w-0">
        {/* Top Filter Bar: Dropdowns & View Mode */}
        <div className="p-4 border-b border-outline-variant/30 flex flex-col lg:flex-row items-stretch lg:items-center justify-between gap-3 bg-surface-container-low/40">
          {/* Dropdown Filters */}
          <div className="flex items-center gap-2.5 flex-wrap">
            <div className="flex items-center gap-1.5">
              <Filter className="w-3.5 h-3.5 text-on-surface-variant" />
              <span className="text-xs font-semibold text-on-surface">{t.common.filter}:</span>
            </div>

            {/* Exam selector */}
            {distinctExams.length > 0 && (
              <select
                value={selectedExam}
                onChange={(e) => setSelectedExam(e.target.value)}
                className="px-2.5 py-1.5 text-xs bg-surface-container-lowest border border-outline-variant/60 rounded-lg text-on-surface font-medium focus:outline-none focus:ring-1 focus:ring-primary shadow-xs cursor-pointer"
              >
                <option value="ALL">{t.plagiarism.allExamsFilter} ({distinctExams.length})</option>
                {distinctExams.map(ex => (
                  <option key={ex} value={ex}>{ex}</option>
                ))}
              </select>
            )}

            {/* Class selector */}
            {distinctClasses.length > 0 && (
              <select
                value={selectedClass}
                onChange={(e) => setSelectedClass(e.target.value)}
                className="px-2.5 py-1.5 text-xs bg-surface-container-lowest border border-outline-variant/60 rounded-lg text-on-surface font-medium focus:outline-none focus:ring-1 focus:ring-primary shadow-xs cursor-pointer"
              >
                <option value="ALL">{t.plagiarism.allClassesFilter} ({distinctClasses.length})</option>
                {distinctClasses.map(cls => (
                  <option key={cls} value={cls}>{cls}</option>
                ))}
              </select>
            )}
          </div>

          {/* View Mode Switcher */}
          <div className="flex items-center gap-2 self-start lg:self-center">
            <span className="text-[11px] text-on-surface-variant font-medium">Display:</span>
            <div className="inline-flex rounded-lg bg-surface-container p-0.5 border border-outline-variant/40">
              <button
                onClick={() => setViewMode('table')}
                className={`px-3 py-1 rounded-md text-xs font-medium flex items-center gap-1.5 transition-all cursor-pointer ${
                  viewMode === 'table'
                    ? 'bg-surface-container-lowest text-on-surface font-semibold shadow-xs'
                    : 'text-on-surface-variant hover:text-on-surface'
                }`}
              >
                <ListFilter className="w-3.5 h-3.5" />
                <span>Table List</span>
              </button>
              <button
                onClick={() => setViewMode('matrix')}
                className={`px-3 py-1 rounded-md text-xs font-medium flex items-center gap-1.5 transition-all cursor-pointer ${
                  viewMode === 'matrix'
                    ? 'bg-surface-container-lowest text-on-surface font-semibold shadow-xs'
                    : 'text-on-surface-variant hover:text-on-surface'
                }`}
              >
                <Grid3X3 className="w-3.5 h-3.5" />
                <span>{t('plagiarism.matrixHeatmapTab')}</span>
              </button>
            </div>
          </div>
        </div>

        {/* View Mode: Matrix Heatmap vs Table */}
        {viewMode === 'matrix' ? (
          <div className="p-4 sm:p-6">
            <SimilarityMatrixGrid records={filteredPairs} />
          </div>
        ) : (
          <>
            {/* Table Filter Tabs and Search Bar */}
            <div className="p-4 border-b border-outline-variant/30 flex flex-col sm:flex-row items-stretch sm:items-center justify-between gap-3 bg-surface-container-lowest">
              <div className="flex items-center gap-1.5 flex-wrap">
                <button
                  onClick={() => setStatusFilter('ALL')}
                  className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-colors cursor-pointer ${
                    statusFilter === 'ALL'
                      ? 'bg-primary text-on-primary shadow-xs font-semibold'
                      : 'text-on-surface-variant hover:text-on-surface hover:bg-surface-container'
                  }`}
                >
                  {t.common.all} ({totalPairs})
                </button>
                <button
                  onClick={() => setStatusFilter('FLAGGED')}
                  className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-colors cursor-pointer ${
                    statusFilter === 'FLAGGED'
                      ? 'bg-rose-600 text-white shadow-xs font-semibold'
                      : 'text-rose-700 hover:bg-rose-50 dark:text-rose-300 dark:hover:bg-rose-950/40'
                  }`}
                >
                  {t.plagiarism.flaggedFilterTab} ({flaggedPairs})
                </button>
                <button
                  onClick={() => setStatusFilter('MODERATE')}
                  className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-colors cursor-pointer ${
                    statusFilter === 'MODERATE'
                      ? 'bg-amber-600 text-white shadow-xs font-semibold'
                      : 'text-amber-700 hover:bg-amber-50 dark:text-amber-300 dark:hover:bg-amber-950/40'
                  }`}
                >
                  {t.plagiarism.moderateTab} ({moderatePairs})
                </button>
                <button
                  onClick={() => setStatusFilter('CLEAN')}
                  className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-colors cursor-pointer ${
                    statusFilter === 'CLEAN'
                      ? 'bg-emerald-600 text-white shadow-xs font-semibold'
                      : 'text-emerald-700 hover:bg-emerald-50 dark:text-emerald-300 dark:hover:bg-emerald-950/40'
                  }`}
                >
                  {t.plagiarism.cleanTab} ({normalPairs})
                </button>
              </div>

              <div className="relative min-w-[240px]">
                <Search className="w-3.5 h-3.5 absolute left-3 top-1/2 -translate-y-1/2 text-outline" />
                <input
                  type="text"
                  placeholder={t.common.search}
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  className="w-full pl-8 pr-7 py-1.5 text-xs bg-surface-container-lowest border border-outline-variant/60 rounded-lg focus:outline-none focus:ring-1 focus:ring-primary text-on-surface placeholder:text-outline"
                />
                {searchQuery && (
                  <button
                    onClick={() => setSearchQuery('')}
                    className="absolute right-2 top-1/2 -translate-y-1/2 text-outline hover:text-on-surface"
                  >
                    <X className="w-3.5 h-3.5" />
                  </button>
                )}
              </div>
            </div>

            {/* Evidentiary Table */}
            <div className="overflow-x-auto w-full">
              <table className="w-full text-left border-collapse text-xs">
                <thead>
                  <tr className="bg-surface-container-low text-on-surface-variant font-label-xs uppercase tracking-wider border-b border-outline-variant/30 select-none">
                    <th className="py-2.5 px-4 font-semibold w-1/4">{t.plagiarism.studentA}</th>
                    <th className="py-2.5 px-4 font-semibold w-1/4">{t.plagiarism.studentB}</th>
                    <th className="py-2.5 px-4 font-semibold w-1/4">{t.plagiarism.structuralMatch}</th>
                    <th className="py-2.5 px-4 font-semibold text-right">{t.common.actions}</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-outline-variant/20 text-on-surface">
                  {filteredPairs.length === 0 ? (
                    <tr>
                      <td colSpan={4} className="py-12 text-center text-outline font-label-md">
                        {data.length === 0 ? t.plagiarism.noActiveExamRecords : t.plagiarism.noPairsFound}
                      </td>
                    </tr>
                  ) : (
                    paginatedPairs.map((record: PlagiarismRecord) => {
                      const pair = record.pair;
                      return (
                        <tr key={pair.pairId} className="hover:bg-surface-container-low/60 transition-colors">
                          <td className="py-3.5 px-4 align-top">
                            <div className="flex items-start gap-2.5">
                              <div className="w-8 h-8 rounded-full bg-blue-50 text-blue-700 border border-blue-200 dark:bg-blue-950/40 dark:text-blue-300 dark:border-blue-800/40 flex items-center justify-center text-xs font-bold shrink-0 mt-0.5">
                                {(pair.studentAName ?? pair.studentAId).substring(0, 2).toUpperCase()}
                              </div>
                              <div className="flex flex-col min-w-0">
                                <span className="font-semibold text-on-surface truncate">{pair.studentAName ?? 'Student A'}</span>
                                <span className="font-mono text-[11px] text-on-surface-variant">{pair.studentAId}</span>
                                {record.examName && (
                                  <span className="text-[10px] text-outline mt-0.5">{record.examName}</span>
                                )}
                              </div>
                            </div>
                          </td>

                          <td className="py-3.5 px-4 align-top">
                            <div className="flex items-start gap-2.5">
                              <div className="w-8 h-8 rounded-full bg-sky-50 text-sky-700 border border-sky-200 dark:bg-sky-950/40 dark:text-sky-300 dark:border-sky-800/40 flex items-center justify-center text-xs font-bold shrink-0 mt-0.5">
                                {(pair.studentBName ?? pair.studentBId).substring(0, 2).toUpperCase()}
                              </div>
                              <div className="flex flex-col min-w-0">
                                <span className="font-semibold text-on-surface truncate">{pair.studentBName ?? 'Student B'}</span>
                                <span className="font-mono text-[11px] text-on-surface-variant">{pair.studentBId}</span>
                                {record.className && (
                                  <span className="text-[10px] text-outline mt-0.5">{record.className}</span>
                                )}
                              </div>
                            </div>
                          </td>

                          <td className="py-3.5 px-4 align-top">
                            <div className="flex flex-col gap-1.5">
                              <div className="flex items-baseline justify-between">
                                <span className={`text-sm font-bold font-mono ${
                                  pair.isFlagged ? 'text-rose-600 dark:text-rose-400' : (pair.similarityRate >= 40 ? 'text-amber-600 dark:text-amber-400' : 'text-emerald-600 dark:text-emerald-400')
                                }`}>
                                  {pair.similarityRate}%
                                </span>
                                <span className={`text-[10px] uppercase font-semibold ${
                                  pair.isFlagged ? 'text-rose-600 dark:text-rose-400' : (pair.similarityRate >= 40 ? 'text-amber-600 dark:text-amber-400' : 'text-emerald-600 dark:text-emerald-400')
                                }`}>
                                  {pair.isFlagged ? t('plagiarism.criticalFlagBadge') : (pair.similarityRate >= 40 ? t.plagiarism.moderateTab : t.plagiarism.cleanTab)}
                                </span>
                              </div>
                              <div className="w-full bg-surface-container h-2 rounded-full overflow-hidden">
                                <div 
                                  className={`h-full rounded-full ${
                                    pair.isFlagged ? 'bg-rose-600' : (pair.similarityRate >= 40 ? 'bg-amber-500' : 'bg-emerald-600')
                                  }`} 
                                  style={{ width: `${pair.similarityRate}%` }}
                                />
                              </div>
                            </div>
                          </td>

                          <td className="py-3.5 px-4 align-top text-right">
                            <Link 
                              href={`/teacher/plagiarism/${record.recordId}`} 
                              className="px-3 py-1.5 bg-primary text-on-primary hover:bg-primary/90 rounded-lg text-xs font-medium transition-all inline-flex items-center gap-1.5 shadow-xs"
                            >
                              <span>{t.plagiarism.viewComparison}</span>
                              <ExternalLink className="w-3 h-3" />
                            </Link>
                          </td>
                        </tr>
                      );
                    })
                  )}
                </tbody>
              </table>
            </div>

            {/* Pagination Controls */}
            {filteredPairs.length > 0 && (
              <div className="p-4 border-t border-outline-variant/30 flex flex-col sm:flex-row items-center justify-between gap-3 bg-surface-container-low/30 text-xs">
                <span className="text-on-surface-variant font-medium">
                  Showing <strong className="text-on-surface">{(activePage - 1) * pageSize + 1}</strong> &ndash;{' '}
                  <strong className="text-on-surface">{Math.min(activePage * pageSize, filteredPairs.length)}</strong> of{' '}
                  <strong className="text-on-surface">{filteredPairs.length}</strong> comparisons
                </span>

                <div className="flex items-center gap-1.5">
                  <button
                    onClick={() => setCurrentPage(p => Math.max(p - 1, 1))}
                    disabled={activePage === 1}
                    className="p-1.5 rounded-lg border border-outline-variant/40 bg-surface-container-lowest text-on-surface hover:bg-surface-container disabled:opacity-40 disabled:cursor-not-allowed shadow-xs transition-colors"
                  >
                    <ChevronLeft className="w-4 h-4" />
                  </button>

                  {Array.from({ length: totalPages }).map((_, idx) => {
                    const pageNum = idx + 1;
                    if (totalPages > 6 && Math.abs(pageNum - activePage) > 2 && pageNum !== 1 && pageNum !== totalPages) {
                      if (pageNum === 2 || pageNum === totalPages - 1) {
                        return <span key={pageNum} className="px-1 text-outline">...</span>;
                      }
                      return null;
                    }
                    return (
                      <button
                        key={pageNum}
                        onClick={() => setCurrentPage(pageNum)}
                        className={`w-7 h-7 rounded-lg text-xs font-mono font-medium transition-all ${
                          activePage === pageNum
                            ? 'bg-primary text-on-primary font-bold shadow-xs'
                            : 'bg-surface-container-lowest border border-outline-variant/40 text-on-surface hover:bg-surface-container'
                        }`}
                      >
                        {pageNum}
                      </button>
                    );
                  })}

                  <button
                    onClick={() => setCurrentPage(p => Math.min(p + 1, totalPages))}
                    disabled={activePage === totalPages}
                    className="p-1.5 rounded-lg border border-outline-variant/40 bg-surface-container-lowest text-on-surface hover:bg-surface-container disabled:opacity-40 disabled:cursor-not-allowed shadow-xs transition-colors"
                  >
                    <ChevronRight className="w-4 h-4" />
                  </button>
                </div>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
}
