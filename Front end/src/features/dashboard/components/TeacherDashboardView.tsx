'use client';

import React, { useMemo } from 'react';
import { AppLink as Link } from '@/components/ui/app-link';
import { useTeacherExams, useTeacherSubmissions } from '@/features/exams/hooks/use-teacher-exams';
import { usePlagiarismList } from '@/features/plagiarism/hooks';
import { useTeacherAppeals } from '@/features/appeals/hooks';
import {
  FileCode2,
  Code2,
  Clock,
  ShieldAlert,
  Scale,
  PlusCircle,
  Download,
  ArrowRight,
  CheckCircle2,
  ExternalLink,
  Calendar,
  BookOpen
} from 'lucide-react';
import { toast } from 'sonner';
import {
  AnimatedCounter,
  SystemStatusBar,
  AstIntegrityGauge,
  RadarEmptyState,
} from '@/components/ui/hud-card';

export function TeacherDashboardView() {
  const { data: exams, isLoading: examsLoading, isError: examsError } = useTeacherExams();
  const { data: submissions, isLoading: submissionsLoading } = useTeacherSubmissions();
  const { data: plagiarismList, isLoading: plagiarismLoading } = usePlagiarismList();
  const { data: appeals, isLoading: appealsLoading } = useTeacherAppeals();

  const totalExams = exams?.length || 0;
  const totalSubmissions = submissions?.length || 0;
  const pendingGradingCount = useMemo(() => {
    if (!submissions) return 0;
    return submissions.filter(s => s.status !== 'GRADED').length;
  }, [submissions]);

  const flaggedCount = useMemo(() => {
    if (!plagiarismList) return 0;
    return plagiarismList.filter(p => Boolean(p.pair.isFlagged)).length;
  }, [plagiarismList]);

  const pendingAppealsCount = useMemo(() => {
    if (!appeals) return 0;
    return appeals.filter(a => a.status === 'PENDING').length;
  }, [appeals]);

  // Export summary report as CSV with UTF-8 BOM
  const handleExportReports = () => {
    if (examsLoading) {
      toast.info('Đang tải dữ liệu bài thi, vui lòng đợi...');
      return;
    }
    if (examsError) {
      toast.error('Đã xảy ra lỗi khi tải danh sách bài thi. Vui lòng thử lại.');
      return;
    }
    if (!exams || exams.length === 0) {
      toast.error('Không có dữ liệu bài thi để xuất.');
      return;
    }

    const headers = ['Mã bài thi', 'Tiêu đề bài thi', 'Mã lớp / Môn học', 'Ngày thi', 'Thời lượng (Phút)', 'Trạng thái'];
    const rows = exams.map(e => [
      `"${e.id}"`,
      `"${(e.title || '').replace(/"/g, '""')}"`,
      `"${e.classCodes && e.classCodes.length > 0 ? e.classCodes.join('; ') : (e.courseCode || '')}"`,
      `"${e.examDate ? new Date(e.examDate).toLocaleString('vi-VN') : ''}"`,
      e.duration || 90,
      `"${e.status === 'active' || e.status === 'ACTIVE' ? 'Đang diễn ra' : (e.status === 'draft' ? 'Bản nháp' : 'Hoàn thành')}"`
    ]);

    const csvString = '\uFEFF' + [headers.join(','), ...rows.map(r => r.join(','))].join('\r\n');
    const blob = new Blob([csvString], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `Bao_Cao_Bai_Thi_AITA_${new Date().toISOString().slice(0, 10)}.csv`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);

    toast.success('Xuất báo cáo danh sách bài thi thành công!');
  };

  return (
    <div className="relative z-10 w-full max-w-7xl mx-auto px-4 sm:px-6 py-6 min-w-0">
      <div className="flex flex-col w-full gap-6 min-w-0">
        {/* Modern SaaS Header Title, System Status Pill & Actions */}
        <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4">
          <div className="flex flex-col gap-1.5">
            <div className="flex flex-wrap items-center gap-2.5 text-xs">
              <div className="inline-flex items-center gap-1.5">
                <span className="text-[11px] text-blue-600 dark:text-blue-400 font-medium px-2 py-0.5 rounded-md bg-blue-50/80 dark:bg-blue-950/40 border border-blue-200/60 dark:border-blue-900/40">
                  Không gian Giảng viên
                </span>
                <span className="text-slate-300 dark:text-slate-700 font-mono">/</span>
                <span className="font-mono text-[11px] text-slate-500 font-medium">OVERVIEW</span>
              </div>
              <SystemStatusBar moduleLabel="TEACHER_CMD_V2" />
            </div>
            <h1 className="text-2xl sm:text-3xl font-semibold text-slate-900 dark:text-slate-100 tracking-tight">
              Bảng điều khiển Giảng viên
            </h1>
            <p className="text-sm text-slate-500 dark:text-slate-400 max-w-2xl leading-relaxed">
              Theo dõi trực tiếp các lớp học, bài nộp, phát hiện đạo văn cây cú pháp AST và đơn khiếu nại của sinh viên.
            </p>
          </div>

          <div className="flex flex-wrap items-center gap-2.5 shrink-0">
            <div className="flex items-center gap-1.5 px-3 py-1.5 bg-white/80 dark:bg-slate-900/60 rounded-lg border border-slate-200/80 dark:border-slate-800 text-slate-600 dark:text-slate-300 text-xs font-medium shadow-2xs">
              <Calendar className="w-3.5 h-3.5 text-blue-600 dark:text-blue-400" />
              <span>Học kỳ Fall 2026</span>
            </div>

            <button
              onClick={handleExportReports}
              id="export-csv-btn"
              data-nc-magnetic="true"
              className="inline-flex items-center gap-1.5 px-3.5 py-1.5 bg-white hover:bg-slate-50 dark:bg-slate-900 dark:hover:bg-slate-800 text-slate-700 dark:text-slate-200 rounded-lg text-xs font-medium transition-colors border border-slate-200/80 dark:border-slate-800 cursor-pointer shadow-2xs focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
            >
              <Download className="w-3.5 h-3.5 text-blue-600 dark:text-blue-400" />
              <span>Xuất CSV</span>
            </button>

            <Link
              href="/teacher/exams?action=create"
              id="create-exam-btn"
              data-nc-magnetic="true"
              className="inline-flex items-center gap-1.5 px-4 py-1.5 bg-slate-900 hover:bg-slate-800 text-white dark:bg-white dark:hover:bg-slate-100 dark:text-slate-900 rounded-lg text-xs font-medium transition-colors shadow-2xs cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
            >
              <PlusCircle className="w-3.5 h-3.5" />
              <span>+ Tạo bài thi mới</span>
            </Link>
          </div>
        </div>

        {/* 5 Stat Cards (Linear / Vercel Minimalist Style) */}
        <div
          data-testid="teacher-kpi-grid"
          className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-3.5 items-stretch"
        >
          {/* Card 1: Total Exams (Blue) */}
          <div
            data-testid="kpi-card-item"
            data-nc-target="KPI"
            className="relative flex flex-col justify-between p-4 rounded-xl border border-slate-200/80 bg-white/95 dark:border-slate-800 dark:bg-slate-900/90 shadow-2xs hover:border-slate-300 dark:hover:border-slate-700 hover:shadow-xs transition-all duration-150 h-full"
          >
            <div className="flex items-start justify-between gap-2 min-h-[2rem]">
              <span
                className="text-xs font-medium text-slate-500 dark:text-slate-400 line-clamp-1"
                title="Tổng số bài thi"
              >
                Tổng số bài thi
              </span>
              <div className="p-1.5 rounded-lg bg-blue-50 text-blue-600 dark:bg-blue-950/40 dark:text-blue-400 border border-blue-100/80 dark:border-blue-900/40 shrink-0">
                <FileCode2 className="w-4 h-4" />
              </div>
            </div>

            <div className="my-2.5 flex items-baseline justify-between gap-2 min-h-[2.25rem]">
              <span
                data-testid="kpi-card-value"
                className="font-mono text-3xl font-semibold tracking-tight text-slate-900 dark:text-slate-100 tabular-nums leading-none"
              >
                {examsLoading ? '...' : <AnimatedCounter value={totalExams} />}
              </span>
              <span className="text-[11px] font-medium text-emerald-700 dark:text-emerald-400 inline-flex items-center gap-1.5 px-2 py-0.5 rounded-full bg-emerald-50/80 dark:bg-emerald-950/40 border border-emerald-200/70 dark:border-emerald-800/40 shrink-0">
                <span className="w-1.5 h-1.5 rounded-full bg-emerald-500" />
                Trực tiếp
              </span>
            </div>

            <div className="pt-2 border-t border-slate-100 dark:border-slate-800/80 flex items-center justify-between">
              <span
                className="text-xs text-slate-500 dark:text-slate-400 truncate"
                title="Bài thi trong các học phần"
              >
                Bài thi trong các học phần
              </span>
            </div>
          </div>

          {/* Card 2: Total Submissions (Teal/Cyan) */}
          <div
            data-testid="kpi-card-item"
            data-nc-target="KPI"
            className="relative flex flex-col justify-between p-4 rounded-xl border border-slate-200/80 bg-white/95 dark:border-slate-800 dark:bg-slate-900/90 shadow-2xs hover:border-slate-300 dark:hover:border-slate-700 hover:shadow-xs transition-all duration-150 h-full"
          >
            <div className="flex items-start justify-between gap-2 min-h-[2rem]">
              <span
                className="text-xs font-medium text-slate-500 dark:text-slate-400 line-clamp-1"
                title="Bài nộp sinh viên"
              >
                Bài nộp sinh viên
              </span>
              <div className="p-1.5 rounded-lg bg-teal-50 text-teal-600 dark:bg-teal-950/40 dark:text-teal-400 border border-teal-100/80 dark:border-teal-900/40 shrink-0">
                <Code2 className="w-4 h-4" />
              </div>
            </div>

            <div className="my-2.5 flex items-baseline justify-between gap-2 min-h-[2.25rem]">
              <span
                data-testid="kpi-card-value"
                className="font-mono text-3xl font-semibold tracking-tight text-slate-900 dark:text-slate-100 tabular-nums leading-none"
              >
                {submissionsLoading ? '...' : <AnimatedCounter value={totalSubmissions} />}
              </span>
              <span className="text-[11px] font-medium text-slate-600 dark:text-slate-400 inline-flex items-center gap-1.5 px-2 py-0.5 rounded-full bg-slate-100/80 dark:bg-slate-800/60 border border-slate-200/70 dark:border-slate-700/60 shrink-0">
                <span className="w-1.5 h-1.5 rounded-full bg-teal-500" />
                Đã ghi nhận
              </span>
            </div>

            <div className="pt-2 border-t border-slate-100 dark:border-slate-800/80 flex items-center justify-between">
              <span
                className="text-xs text-slate-500 dark:text-slate-400 truncate"
                title="Bài nộp mã nguồn của sinh viên"
              >
                Bài nộp mã nguồn của sinh viên
              </span>
            </div>
          </div>

          {/* Card 3: Pending Grading (Amber) */}
          <div
            data-testid="kpi-card-item"
            data-nc-target="KPI"
            className="relative flex flex-col justify-between p-4 rounded-xl border border-slate-200/80 bg-white/95 dark:border-slate-800 dark:bg-slate-900/90 shadow-2xs hover:border-slate-300 dark:hover:border-slate-700 hover:shadow-xs transition-all duration-150 h-full"
          >
            <div className="flex items-start justify-between gap-2 min-h-[2rem]">
              <span
                className="text-xs font-medium text-slate-500 dark:text-slate-400 line-clamp-1"
                title="Chờ chấm điểm"
              >
                Chờ chấm điểm
              </span>
              <div className="p-1.5 rounded-lg bg-amber-50 text-amber-600 dark:bg-amber-950/40 dark:text-amber-400 border border-amber-100/80 dark:border-amber-900/40 shrink-0">
                <Clock className="w-4 h-4" />
              </div>
            </div>

            <div className="my-2.5 flex items-baseline justify-between gap-2 min-h-[2.25rem]">
              <span
                data-testid="kpi-card-value"
                className="font-mono text-3xl font-semibold tracking-tight text-slate-900 dark:text-slate-100 tabular-nums leading-none"
              >
                {submissionsLoading ? '...' : <AnimatedCounter value={pendingGradingCount} />}
              </span>
              <span className="text-[11px] font-medium text-amber-700 dark:text-amber-400 px-2 py-0.5 rounded-full bg-amber-50/80 dark:bg-amber-950/40 border border-amber-200/70 dark:border-amber-800/40 inline-flex items-center gap-1.5 shrink-0">
                <span className="w-1.5 h-1.5 rounded-full bg-amber-500" />
                Cần xử lý
              </span>
            </div>

            <div className="pt-2 border-t border-slate-100 dark:border-slate-800/80 flex items-center justify-between">
              <span
                className="text-xs text-slate-500 dark:text-slate-400 truncate"
                title="Đang chờ hoàn tất điểm"
              >
                Đang chờ hoàn tất điểm
              </span>
            </div>
          </div>

          {/* Card 4: Flagged AST Plagiarism (Rose/Red) */}
          <div
            data-testid="kpi-card-item"
            data-nc-target="KPI"
            className="relative flex flex-col justify-between p-4 rounded-xl border border-slate-200/80 bg-white/95 dark:border-slate-800 dark:bg-slate-900/90 shadow-2xs hover:border-slate-300 dark:hover:border-slate-700 hover:shadow-xs transition-all duration-150 h-full"
          >
            <div className="flex items-start justify-between gap-2 min-h-[2rem]">
              <span
                className="text-xs font-medium text-slate-500 dark:text-slate-400 line-clamp-1"
                title="Cảnh báo liêm chính học thuật"
              >
                Cảnh báo liêm chính học thuật
              </span>
              <div className="p-1.5 rounded-lg bg-rose-50 text-rose-600 dark:bg-rose-950/40 dark:text-rose-400 border border-rose-100/80 dark:border-rose-900/40 shrink-0">
                <ShieldAlert className="w-4 h-4" />
              </div>
            </div>

            <div className="my-2.5 flex items-baseline justify-between gap-2 min-h-[2.25rem]">
              <span
                data-testid="kpi-card-value"
                className={`font-mono text-3xl font-semibold tracking-tight tabular-nums leading-none ${
                  flaggedCount > 0
                    ? 'text-rose-600 dark:text-rose-400 font-bold'
                    : 'text-slate-900 dark:text-slate-100'
                }`}
              >
                {plagiarismLoading ? '...' : <AnimatedCounter value={flaggedCount} />}
              </span>
              <span className="text-[11px] font-medium text-rose-700 dark:text-rose-400 px-2 py-0.5 rounded-full bg-rose-50/80 dark:bg-rose-950/40 border border-rose-200/70 dark:border-rose-800/40 inline-flex items-center gap-1.5 shrink-0">
                <span
                  className={`w-1.5 h-1.5 rounded-full bg-rose-500 ${
                    flaggedCount > 0 ? 'animate-pulse' : ''
                  }`}
                />
                Cảnh báo AST
              </span>
            </div>

            <div className="pt-2 border-t border-slate-100 dark:border-slate-800/80 flex items-center justify-between">
              <span
                className="text-xs text-slate-500 dark:text-slate-400 truncate"
                title="Ngưỡng tương đồng ≥ 30%"
              >
                Ngưỡng tương đồng &ge; 30%
              </span>
            </div>
          </div>

          {/* Card 5: Pending Appeals (Violet/Indigo) */}
          <div
            data-testid="kpi-card-item"
            data-nc-target="KPI"
            className="relative flex flex-col justify-between p-4 rounded-xl border border-slate-200/80 bg-white/95 dark:border-slate-800 dark:bg-slate-900/90 shadow-2xs hover:border-slate-300 dark:hover:border-slate-700 hover:shadow-xs transition-all duration-150 h-full"
          >
            <div className="flex items-start justify-between gap-2 min-h-[2rem]">
              <span
                className="text-xs font-medium text-slate-500 dark:text-slate-400 line-clamp-1"
                title="Đơn khiếu nại đang chờ xử lý"
              >
                Đơn khiếu nại đang chờ xử lý
              </span>
              <div className="p-1.5 rounded-lg bg-violet-50 text-violet-600 dark:bg-violet-950/40 dark:text-violet-400 border border-violet-100/80 dark:border-violet-900/40 shrink-0">
                <Scale className="w-4 h-4" />
              </div>
            </div>

            <div className="my-2.5 flex items-baseline justify-between gap-2 min-h-[2.25rem]">
              <span
                data-testid="kpi-card-value"
                className="font-mono text-3xl font-semibold tracking-tight text-slate-900 dark:text-slate-100 tabular-nums leading-none"
              >
                {appealsLoading ? '...' : <AnimatedCounter value={pendingAppealsCount} />}
              </span>
              <span className="text-[11px] font-medium text-violet-700 dark:text-violet-400 px-2 py-0.5 rounded-full bg-violet-50/80 dark:bg-violet-950/40 border border-violet-200/70 dark:border-violet-800/40 inline-flex items-center gap-1.5 shrink-0">
                <span className="w-1.5 h-1.5 rounded-full bg-violet-500" />
                Đang xem xét
              </span>
            </div>

            <div className="pt-2 border-t border-slate-100 dark:border-slate-800/80 flex items-center justify-between">
              <span
                className="text-xs text-slate-500 dark:text-slate-400 truncate"
                title="Yêu cầu phúc khảo từ sinh viên"
              >
                Yêu cầu phúc khảo từ sinh viên
              </span>
            </div>
          </div>
        </div>

        {/* Quick Actions Toolbar (Linear Style) */}
        <div className="flex flex-wrap items-center justify-between p-2.5 bg-white/80 dark:bg-slate-900/70 backdrop-blur-sm rounded-xl border border-slate-200/80 dark:border-slate-800 gap-2.5 shadow-2xs">
          <div className="flex items-center gap-2 px-1">
            <span className="text-xs font-medium text-slate-500 dark:text-slate-400">
              Lối tắt nhanh:
            </span>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <Link
              data-nc-magnetic="true"
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-slate-200/80 bg-white hover:bg-slate-50 hover:border-slate-300 text-slate-700 dark:border-slate-800 dark:bg-slate-900 dark:hover:bg-slate-800 dark:text-slate-300 text-xs font-medium transition-colors shadow-2xs focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
              href="/teacher/exams"
            >
              <BookOpen className="w-3.5 h-3.5 text-blue-600 dark:text-blue-400" />
              <span>Quản lý bài thi</span>
            </Link>
            <Link
              data-nc-magnetic="true"
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-slate-200/80 bg-white hover:bg-slate-50 hover:border-slate-300 text-slate-700 dark:border-slate-800 dark:bg-slate-900 dark:hover:bg-slate-800 dark:text-slate-300 text-xs font-medium transition-colors shadow-2xs focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
              href="/teacher/submissions"
            >
              <Code2 className="w-3.5 h-3.5 text-teal-600 dark:text-teal-400" />
              <span>Bài nộp &amp; Chấm điểm</span>
            </Link>
            <Link
              data-nc-magnetic="true"
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-slate-200/80 bg-white hover:bg-slate-50 hover:border-slate-300 text-slate-700 dark:border-slate-800 dark:bg-slate-900 dark:hover:bg-slate-800 dark:text-slate-300 text-xs font-medium transition-colors shadow-2xs focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
              href="/teacher/plagiarism"
            >
              <ShieldAlert className="w-3.5 h-3.5 text-rose-600 dark:text-rose-400" />
              <span>Kiểm tra đạo văn AST</span>
            </Link>
            <Link
              data-nc-magnetic="true"
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-slate-200/80 bg-white hover:bg-slate-50 hover:border-slate-300 text-slate-700 dark:border-slate-800 dark:bg-slate-900 dark:hover:bg-slate-800 dark:text-slate-300 text-xs font-medium transition-colors shadow-2xs focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
              href="/teacher/appeals"
            >
              <Scale className="w-3.5 h-3.5 text-violet-600 dark:text-violet-400" />
              <span>Duyệt đơn phúc khảo</span>
            </Link>
          </div>
        </div>

        {/* Recent Exams Table (Minimalist Clean 6-Column Alignment) */}
        <div className="flex flex-col bg-white/95 dark:bg-slate-900/90 rounded-xl border border-slate-200/80 dark:border-slate-800 shadow-2xs overflow-hidden w-full min-w-0">
          <div className="p-4 sm:p-5 flex flex-col sm:flex-row sm:items-center justify-between gap-3 border-b border-slate-200/70 dark:border-slate-800">
            <div className="flex items-center gap-3">
              <div className="p-2 rounded-lg bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-300">
                <FileCode2 className="w-4 h-4" />
              </div>
              <div>
                <h2 className="text-sm font-semibold text-slate-900 dark:text-slate-100">
                  Bài thi gần đây
                </h2>
                <span className="text-xs text-slate-500 dark:text-slate-400">
                  Danh sách các bài thi trong học phần của bạn
                </span>
              </div>
            </div>
            <Link
              href="/teacher/exams"
              className="text-xs font-medium text-blue-600 hover:text-blue-700 dark:text-blue-400 dark:hover:text-blue-300 inline-flex items-center gap-1 self-start sm:self-center transition-colors"
            >
              <span>Xem tất cả {totalExams} bài thi</span>
              <ArrowRight className="w-3.5 h-3.5" />
            </Link>
          </div>

          <div className="relative w-full overflow-x-auto">
            <table
              data-testid="recent-exams-table"
              className="w-full min-w-[780px] table-fixed text-left font-body-md text-body-md border-collapse"
            >
              <colgroup>
                <col className="w-[30%]" />
                <col className="w-[16%]" />
                <col className="w-[18%]" />
                <col className="w-[11%]" />
                <col className="w-[14%]" />
                <col className="w-[11%]" />
              </colgroup>
              <thead className="bg-slate-50/80 dark:bg-slate-800/50 text-slate-500 dark:text-slate-400 text-[11px] font-medium uppercase tracking-wider border-b border-slate-200/80 dark:border-slate-800">
                <tr>
                  <th className="py-2.5 px-4 font-medium text-left">Tiêu đề bài thi</th>
                  <th className="py-2.5 px-4 font-medium text-left">Mã lớp / Môn</th>
                  <th className="py-2.5 px-4 font-medium text-left">Thời gian thi</th>
                  <th className="py-2.5 px-4 font-medium text-left">Thời lượng</th>
                  <th className="py-2.5 px-4 font-medium text-left">Trạng thái</th>
                  <th className="py-2.5 px-4 font-medium text-right">Thao tác</th>
                </tr>
              </thead>
              <tbody className="text-slate-700 dark:text-slate-300 divide-y divide-slate-100 dark:divide-slate-800/70 text-xs">
                {examsLoading ? (
                  <>
                    {[1, 2, 3].map((row) => (
                      <tr key={row}>
                        <td colSpan={6} className="p-3">
                          <div className="nc-skeleton h-8 w-full rounded-lg" />
                        </td>
                      </tr>
                    ))}
                  </>
                ) : exams && exams.length > 0 ? (
                  exams.slice(0, 5).map((exam) => (
                    <tr key={exam.id} data-row="true" className="nc-table-row hover:bg-slate-50/60 dark:hover:bg-slate-800/30 transition-colors">
                      <td className="py-3 px-4 font-medium text-slate-900 dark:text-slate-100 align-middle">
                        <Link
                          href={`/teacher/exams/${exam.id}`}
                          className={`hover:text-blue-600 dark:hover:text-blue-400 transition-colors line-clamp-2 ${
                            exam.title ? '' : 'italic text-slate-400 font-normal'
                          }`}
                          title={exam.title || '(Chưa đặt tên)'}
                        >
                          {exam.title || '(Chưa đặt tên)'}
                        </Link>
                      </td>
                      <td className="py-3 px-4 align-middle">
                        <span className="inline-block px-2 py-0.5 rounded bg-slate-100 dark:bg-slate-800 border border-slate-200/70 dark:border-slate-700/70 font-mono text-xs text-slate-600 dark:text-slate-300 truncate max-w-full">
                          {exam.classCodes && exam.classCodes.length > 0 ? exam.classCodes.join(', ') : (exam.courseCode || 'SE-CORE')}
                        </span>
                      </td>
                      <td className="py-3 px-4 text-slate-500 dark:text-slate-400 font-mono text-[11px] align-middle">
                        {exam.examDate ? new Date(exam.examDate).toLocaleString('vi-VN') : 'Linh hoạt'}
                      </td>
                      <td className="py-3 px-4 text-slate-500 dark:text-slate-400 font-mono text-[11px] align-middle">
                        {exam.duration || 90} phút
                      </td>
                      <td className="py-3 px-4 align-middle">
                        <span className={`inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-medium border ${
                          exam.status === 'draft'
                            ? 'bg-slate-100 text-slate-600 border-slate-200 dark:bg-slate-800 dark:text-slate-400 dark:border-slate-700'
                            : 'bg-emerald-50 text-emerald-700 border-emerald-200/80 dark:bg-emerald-950/40 dark:text-emerald-400 dark:border-emerald-800/40'
                        }`}>
                          <span className={`w-1.5 h-1.5 rounded-full ${exam.status === 'draft' ? 'bg-slate-400' : 'bg-emerald-500'}`} />
                          {exam.status === 'draft' ? 'Bản nháp' : 'Đang hoạt động'}
                        </span>
                      </td>
                      <td className="py-3 px-4 text-right align-middle">
                        <Link
                          href={`/teacher/exams/${exam.id}`}
                          className="inline-flex items-center gap-1 px-2.5 py-1 rounded-md text-xs font-medium text-slate-600 hover:text-slate-900 hover:bg-slate-100 dark:text-slate-400 dark:hover:text-slate-100 dark:hover:bg-slate-800 transition-colors border border-transparent hover:border-slate-200 dark:hover:border-slate-700"
                        >
                          <span>Chi tiết</span>
                          <ExternalLink className="w-3 h-3" />
                        </Link>
                      </td>
                    </tr>
                  ))
                ) : (
                  <tr>
                    <td colSpan={6}>
                      <RadarEmptyState message='Chưa có bài thi nào được tạo. Nhấn "+ Tạo bài thi mới" để bắt đầu.' />
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </div>

        {/* 2 Bottom Cards: Recent Submissions & AST Integrity Forensics */}
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
          {/* Recent Submissions Feed */}
          <div className="lg:col-span-7 flex flex-col bg-white/95 dark:bg-slate-900/90 rounded-xl border border-slate-200/80 dark:border-slate-800 p-4 sm:p-5 shadow-2xs">
            <div className="flex items-center justify-between mb-4 pb-3 border-b border-slate-200/70 dark:border-slate-800">
              <div className="flex items-center gap-2.5">
                <div className="p-1.5 rounded-lg bg-teal-50 dark:bg-teal-950/40 text-teal-600 dark:text-teal-400">
                  <Code2 className="w-4 h-4" />
                </div>
                <h3 className="text-sm font-semibold text-slate-900 dark:text-slate-100">
                  Bài nộp gần đây
                </h3>
              </div>
              <Link href="/teacher/submissions" className="text-xs text-blue-600 hover:text-blue-700 dark:text-blue-400 font-medium">
                Xem tất cả
              </Link>
            </div>

            <div className="flex flex-col gap-2">
              {submissionsLoading ? (
                <div className="space-y-2 py-2">
                  {[1, 2, 3].map((idx) => (
                    <div key={idx} className="nc-skeleton h-12 w-full rounded-lg" />
                  ))}
                </div>
              ) : submissions && submissions.length > 0 ? (
                submissions.slice(0, 4).map((sub) => (
                  <div
                    key={sub.id}
                    data-row="true"
                    className="flex items-center justify-between p-2.5 px-3 rounded-lg bg-slate-50/60 dark:bg-slate-800/30 border border-slate-200/60 dark:border-slate-800 text-xs hover:border-slate-300 dark:hover:border-slate-700 transition-colors"
                  >
                    <div className="flex flex-col min-w-0">
                      <span
                        className="font-medium text-slate-900 dark:text-slate-100 truncate max-w-[240px]"
                        title={`Bài nộp #${sub.id}`}
                      >
                        Bài nộp <span className="font-mono">#{sub.id.slice(0, 8)}</span>
                      </span>
                      <span className="text-[11px] font-mono text-slate-400">
                        {sub.submittedAt ? new Date(sub.submittedAt).toLocaleString('vi-VN') : 'Vừa xong'}
                      </span>
                    </div>

                    <div className="flex items-center gap-2 shrink-0">
                      <span className={`inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-[11px] font-medium border ${
                        sub.status === 'GRADED'
                          ? 'bg-emerald-50 text-emerald-700 border-emerald-200 dark:bg-emerald-950/40 dark:text-emerald-400 dark:border-emerald-800/40'
                          : 'bg-amber-50 text-amber-700 border-amber-200 dark:bg-amber-950/40 dark:text-amber-400 dark:border-amber-800/40'
                      }`}>
                        <span className={`w-1.5 h-1.5 rounded-full ${sub.status === 'GRADED' ? 'bg-emerald-500' : 'bg-amber-500'}`} />
                        {sub.status === 'GRADED' ? 'Đã chấm' : 'Chờ chấm'}
                      </span>
                      <Link
                        href={`/teacher/submissions/${sub.id}`}
                        className="px-2.5 py-1 rounded-md bg-white hover:bg-slate-50 dark:bg-slate-800 dark:hover:bg-slate-700 border border-slate-200 dark:border-slate-700 text-slate-700 dark:text-slate-200 font-medium transition-colors"
                      >
                        Chấm điểm
                      </Link>
                    </div>
                  </div>
                ))
              ) : (
                <RadarEmptyState message="Chưa có bài nộp nào từ sinh viên." />
              )}
            </div>
          </div>

          {/* AST Integrity Forensics Box */}
          <div
            className={`lg:col-span-5 flex flex-col bg-white/95 dark:bg-slate-900/90 rounded-xl border p-4 sm:p-5 justify-between shadow-2xs ${
              flaggedCount > 0
                ? 'border-rose-300 dark:border-rose-900/60'
                : 'border-slate-200/80 dark:border-slate-800'
            }`}
          >
            <div className="flex flex-col gap-3">
              <div className="flex items-center justify-between pb-3 border-b border-slate-200/70 dark:border-slate-800">
                <div className="flex items-center gap-2.5">
                  <div
                    className={`p-1.5 rounded-lg border ${
                      flaggedCount > 0
                        ? 'bg-rose-50 border-rose-200/70 text-rose-600 dark:bg-rose-950/40 dark:text-rose-400 dark:border-rose-900/40'
                        : 'bg-slate-100 border-slate-200/70 text-slate-600 dark:bg-slate-800 dark:text-slate-300 dark:border-slate-700'
                    }`}
                  >
                    <ShieldAlert className="w-4 h-4" />
                  </div>
                  <h3
                    data-testid="ast-forensics-title"
                    className="text-sm font-semibold text-slate-900 dark:text-slate-100"
                  >
                    Giám sát Liêm chính AST
                  </h3>
                </div>
                <span
                  className={`inline-flex items-center gap-1.5 text-[11px] px-2.5 py-0.5 rounded-full font-medium border ${
                    flaggedCount > 0
                      ? 'bg-rose-50 text-rose-700 border-rose-200 dark:bg-rose-950/40 dark:text-rose-400 dark:border-rose-900/40'
                      : 'bg-emerald-50 text-emerald-700 border-emerald-200 dark:bg-emerald-950/40 dark:text-emerald-400 dark:border-emerald-800/40'
                  }`}
                >
                  <span
                    className={`w-1.5 h-1.5 rounded-full ${
                      flaggedCount > 0 ? 'bg-rose-500 animate-pulse' : 'bg-emerald-500'
                    }`}
                  />
                  Phân tích tự động
                </span>
              </div>

              {/* Semi-circular AST Integrity Gauge */}
              <AstIntegrityGauge flaggedCount={flaggedCount} />

              <div className="p-3 rounded-lg bg-slate-50/70 dark:bg-slate-800/30 border border-slate-200/70 dark:border-slate-800 flex flex-col gap-2 text-xs">
                <div className="flex justify-between items-center gap-2">
                  <span className="text-slate-500 dark:text-slate-400 font-medium">Cặp bài bị cảnh báo:</span>
                  <span
                    className={`font-mono font-semibold ${
                      flaggedCount > 0 ? 'text-rose-600 dark:text-rose-400' : 'text-emerald-700 dark:text-emerald-400'
                    }`}
                  >
                    {flaggedCount} cặp
                  </span>
                </div>
                <div className="flex justify-between items-center gap-2">
                  <span className="text-slate-500 dark:text-slate-400 font-medium shrink-0">Thuật toán AST:</span>
                  <span
                    className="text-slate-700 dark:text-slate-300 text-[11px] font-medium text-right line-clamp-1"
                    title="Khoảng cách chỉnh sửa cây + Tokenizer"
                  >
                    Khoảng cách chỉnh sửa cây + Tokenizer
                  </span>
                </div>
                <div className="flex justify-between items-center gap-2">
                  <span className="text-slate-500 dark:text-slate-400 font-medium">Trạng thái:</span>
                  <span className="text-emerald-700 dark:text-emerald-400 font-medium flex items-center gap-1">
                    <CheckCircle2 className="w-3.5 h-3.5" /> Đã đồng bộ
                  </span>
                </div>
              </div>
            </div>

            <div className="pt-3 flex items-center justify-between gap-2 border-t border-slate-200/70 dark:border-slate-800 mt-4">
              <span className="text-[11px] text-slate-500 dark:text-slate-400 font-medium">So sánh mã nguồn thời gian thực</span>
              <Link
                data-nc-magnetic="true"
                className={`inline-flex items-center gap-1.5 px-3.5 py-1.5 rounded-lg text-xs font-medium transition-colors shadow-2xs ${
                  flaggedCount > 0
                    ? 'bg-rose-600 hover:bg-rose-500 text-white'
                    : 'bg-slate-900 hover:bg-slate-800 text-white dark:bg-white dark:hover:bg-slate-100 dark:text-slate-900'
                }`}
                href="/teacher/plagiarism"
              >
                <span>Mở công cụ AST</span>
                <ArrowRight className="w-3.5 h-3.5" />
              </Link>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
