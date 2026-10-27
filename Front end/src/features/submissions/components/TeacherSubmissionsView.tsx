'use client';

import React, { useState, useMemo } from 'react';
import { useTeacherSubmissions } from '@/features/exams/hooks/use-teacher-exams';
import { AppLink as Link } from '@/components/ui/app-link';
import { 
  Search, 
  ChevronRight, 
  ChevronDown, 
  CheckCircle2, 
  Clock, 
  Award, 
  FileCode2, 
  ExternalLink, 
  AlertCircle, 
  Loader2, 
  X, 
  Code2 
} from 'lucide-react';

export function TeacherSubmissionsView() {
  const { data: submissions, isLoading, isError, error, refetch } = useTeacherSubmissions();
  const [searchQuery, setSearchQuery] = useState(() => {
    if (typeof window !== 'undefined') {
      return new URLSearchParams(window.location.search).get('search') || '';
    }
    return '';
  });
  const [statusFilter, setStatusFilter] = useState(() => {
    if (typeof window !== 'undefined') {
      return new URLSearchParams(window.location.search).get('status') || 'all';
    }
    return 'all';
  });

  const filteredSubmissions = useMemo(() => {
    if (!submissions) return [];
    return submissions.filter(sub => {
      if (statusFilter !== 'all' && (sub.status || '').toUpperCase() !== statusFilter.toUpperCase()) {
        return false;
      }
      if (searchQuery.trim()) {
        const query = searchQuery.toLowerCase();
        const studentNameMatch = (sub.studentName || '').toLowerCase().includes(query);
        const studentEmailMatch = (sub.studentEmail || '').toLowerCase().includes(query);
        const examTitleMatch = (sub.examTitle || '').toLowerCase().includes(query);
        const courseCodeMatch = (sub.courseCode || '').toLowerCase().includes(query);
        return studentNameMatch || studentEmailMatch || examTitleMatch || courseCodeMatch;
      }
      return true;
    });
  }, [submissions, searchQuery, statusFilter]);

  const stats = useMemo(() => {
    if (!submissions || submissions.length === 0) {
      return { total: 0, graded: 0, pending: 0, avgScore: '0' };
    }
    const graded = submissions.filter(s => s.status === 'GRADED');
    const pending = submissions.filter(s => s.status !== 'GRADED');
    const totalScore = graded.reduce((acc, curr) => acc + (Number(curr.totalScore) || 0), 0);
    const avgScore = graded.length > 0 ? (totalScore / graded.length).toFixed(1) : '0';
    return {
      total: submissions.length,
      graded: graded.length,
      pending: pending.length,
      avgScore,
    };
  }, [submissions]);

  if (isLoading) {
    return (
      <div className="max-w-7xl mx-auto px-space-lg py-space-lg">
        <div className="flex flex-col h-72 items-center justify-center gap-3">
          <div className="w-8 h-8 rounded-full border-2 border-primary border-t-transparent animate-spin" />
          <p className="text-on-surface-variant font-label-md text-xs">Đang tải danh sách bài nộp...</p>
        </div>
      </div>
    );
  }

  if (isError) {
    return (
      <div className="max-w-xl mx-auto my-12 p-8 rounded-2xl bg-surface-container-lowest border border-rose-200/60 shadow-lg text-center flex flex-col items-center gap-3">
        <div className="p-3 rounded-full bg-rose-50 text-rose-600">
          <AlertCircle className="w-8 h-8" />
        </div>
        <h2 className="font-headline-md text-headline-md text-on-surface">Không thể tải danh sách bài nộp</h2>
        <p className="font-body-md text-on-surface-variant text-xs">
          {(error as Error)?.message || 'Đã xảy ra lỗi khi lấy danh sách bài nộp của học phần.'}
        </p>
        <button
          onClick={() => refetch()}
          className="mt-2 px-4 py-2 bg-primary text-on-primary rounded-lg text-xs font-medium hover:bg-primary/90 transition-all cursor-pointer shadow-xs"
        >
          Thử lại
        </button>
      </div>
    );
  }

  return (
    <div className="w-full max-w-7xl mx-auto px-4 sm:px-space-lg py-space-lg min-w-0">
      <div className="flex flex-col w-full gap-space-md min-w-0">
        {/* Breadcrumb */}
        <div className="flex items-center gap-1.5 text-xs text-on-surface-variant">
          <Link className="hover:text-primary transition-colors font-medium" href="/teacher/dashboard">
            Bảng điều khiển
          </Link>
          <ChevronRight className="w-3.5 h-3.5 text-outline" />
          <span className="text-on-surface font-semibold">Bài nộp &amp; Chấm điểm</span>
        </div>

        {/* Header */}
        <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-space-md">
          <div className="flex flex-col gap-0.5">
            <h1 className="font-display-lg text-display-lg text-on-surface tracking-tight">Bài nộp của sinh viên</h1>
            <p className="text-xs sm:text-sm text-on-surface-variant">
              Xem mã nguồn bài làm của sinh viên, đánh giá theo tiêu chí chấm điểm và kiểm tra báo cáo đạo văn AST.
            </p>
          </div>
          <Link
            href="/teacher/exams"
            className="inline-flex items-center gap-2 px-4 py-2 bg-primary text-on-primary rounded-lg text-xs font-medium hover:bg-primary/90 transition-colors self-start lg:self-auto shadow-xs"
          >
            <FileCode2 className="w-4 h-4" />
            <span>Quản lý bài thi</span>
          </Link>
        </div>

        {/* 4 Metric Cards */}
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-space-sm">
          <div className="bg-surface-container-lowest p-space-md rounded-xl border border-outline-variant/40 shadow-xs flex flex-col justify-between">
            <span className="font-label-xs uppercase tracking-wider text-outline font-semibold">Tổng số bài nộp</span>
            <span className="font-display-lg font-bold text-on-surface mt-2">{stats.total}</span>
          </div>
          <div className="bg-surface-container-lowest p-space-md rounded-xl border border-outline-variant/40 shadow-xs flex flex-col justify-between">
            <span className="font-label-xs uppercase tracking-wider text-outline font-semibold">Đã chấm điểm</span>
            <span className="font-display-lg font-bold text-emerald-600 mt-2">{stats.graded}</span>
          </div>
          <div className="bg-surface-container-lowest p-space-md rounded-xl border border-outline-variant/40 shadow-xs flex flex-col justify-between">
            <span className="font-label-xs uppercase tracking-wider text-outline font-semibold">Cần xem xét</span>
            <span className="font-display-lg font-bold text-amber-600 mt-2">{stats.pending}</span>
          </div>
          <div className="bg-surface-container-lowest p-space-md rounded-xl border border-outline-variant/40 shadow-xs flex flex-col justify-between">
            <span className="font-label-xs uppercase tracking-wider text-outline font-semibold">Điểm trung bình</span>
            <div className="mt-2 flex items-baseline gap-1">
              <span className="font-display-lg font-bold text-primary">{stats.avgScore}</span>
              <span className="text-xs text-outline font-normal">/ 10</span>
            </div>
          </div>
        </div>

        {/* Filter Bar */}
        <div className="flex flex-col sm:flex-row gap-space-sm items-stretch sm:items-center justify-between bg-surface-container-lowest p-3 rounded-xl border border-outline-variant/40 shadow-xs">
          <div className="relative flex-1 max-w-md">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-outline w-4 h-4" />
            <input
              type="text"
              placeholder="Tìm kiếm theo sinh viên, email, bài thi hoặc mã môn..."
              value={searchQuery}
              onChange={e => setSearchQuery(e.target.value)}
              className="w-full pl-9 pr-8 py-1.5 bg-surface-container-low border border-outline-variant/40 rounded-lg text-xs text-on-surface placeholder:text-outline focus:outline-none focus:border-primary transition-colors"
            />
            {searchQuery && (
              <button 
                onClick={() => setSearchQuery('')}
                className="absolute right-2.5 top-1/2 -translate-y-1/2 text-outline hover:text-on-surface cursor-pointer"
              >
                <X className="w-3.5 h-3.5" />
              </button>
            )}
          </div>
          <div className="flex items-center gap-2">
            <span className="text-on-surface-variant font-label-xs text-xs font-medium">Bộ lọc:</span>
            <div className="relative">
              <select
                value={statusFilter}
                onChange={e => setStatusFilter(e.target.value)}
                className="appearance-none pl-3 pr-8 py-1.5 bg-surface-container-low border border-outline-variant/40 rounded-lg text-xs text-on-surface focus:outline-none cursor-pointer"
              >
                <option value="all">Tất cả trạng thái</option>
                <option value="GRADED">Đã chấm điểm</option>
                <option value="PENDING">Chờ chấm điểm</option>
                <option value="EVALUATING">Đang đánh giá</option>
              </select>
              <ChevronDown className="absolute right-2.5 top-1/2 -translate-y-1/2 text-outline w-3.5 h-3.5 pointer-events-none" />
            </div>
          </div>
        </div>

        {/* Submissions Table */}
        <div className="rounded-xl bg-surface-container-lowest shadow-xs border border-outline-variant/40 overflow-hidden flex flex-col w-full min-w-0">
          <div className="overflow-x-auto w-full">
            <table className="w-full text-left border-collapse text-xs" id="teacher-submissions-table">
              <thead>
                <tr className="bg-surface-container-low text-on-surface-variant font-label-xs uppercase tracking-wider border-b border-outline-variant/30 select-none">
                  <th className="py-2.5 px-4 font-semibold">Tên sinh viên</th>
                  <th className="py-2.5 px-4 font-semibold">Email</th>
                  <th className="py-2.5 px-4 font-semibold">Môn học &amp; Bài thi</th>
                  <th className="py-2.5 px-4 font-semibold">Thời gian nộp</th>
                  <th className="py-2.5 px-4 font-semibold">Điểm số</th>
                  <th className="py-2.5 px-4 font-semibold">Trạng thái</th>
                  <th className="py-2.5 px-4 font-semibold text-right">Thao tác</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-outline-variant/20 text-on-surface">
                {filteredSubmissions.length === 0 ? (
                  <tr>
                    <td colSpan={7} className="py-12 text-center text-on-surface-variant">
                      <div className="flex flex-col items-center justify-center gap-2">
                        <Code2 className="w-8 h-8 text-outline" />
                        <p className="font-semibold text-on-surface text-sm">Không tìm thấy bài nộp nào</p>
                        <p className="text-outline text-xs">
                          {searchQuery || statusFilter !== 'all'
                            ? 'Không có bài nộp nào khớp với điều kiện lọc của bạn.'
                            : 'Chưa có sinh viên nào nộp bài cho học phần của bạn.'}
                        </p>
                      </div>
                    </td>
                  </tr>
                ) : (
                  filteredSubmissions.map(sub => {
                    const isFailed = sub.status === 'FAILED' || sub.executionStatus === 'FAILED' || sub.executionStatus === 'COMPILATION_ERROR' || sub.executionStatus === 'RUNTIME_ERROR' || sub.executionStatus === 'TIMEOUT';
                    const isProcessing = sub.status === 'PROCESSING' || sub.executionStatus === 'RUNNING';
                    const isPending = sub.status === 'PENDING' || sub.status === 'SUBMITTED' || sub.executionStatus === 'PENDING';
                    const isGraded = (sub.status === 'GRADED' || sub.status === 'COMPLETED' || sub.status === 'EVALUATED') && !isFailed;

                    return (
                      <tr
                        key={sub.id}
                        className="hover:bg-surface-container-low/60 transition-colors"
                      >
                        <td className="py-3 px-4 font-semibold text-on-surface">{sub.studentName}</td>
                        <td className="py-3 px-4 text-on-surface-variant text-[11px]">{sub.studentEmail}</td>
                        <td className="py-3 px-4">
                          <div className="flex flex-col">
                            <span className="font-medium text-on-surface">{sub.examTitle}</span>
                            {sub.courseCode && (
                              <span className="font-mono text-[10px] text-primary">{sub.courseCode}</span>
                            )}
                          </div>
                        </td>
                        <td className="py-3 px-4 text-on-surface-variant">
                          {sub.submittedAt ? new Date(sub.submittedAt).toLocaleString('vi-VN') : 'N/A'}
                        </td>
                        <td className="py-3 px-4">
                          {isProcessing || isPending ? (
                            <span className="text-outline italic text-[11px]">Đang chấm...</span>
                          ) : isGraded ? (
                            <span className="font-mono bg-surface-container px-2 py-0.5 rounded font-bold text-on-surface">
                              {sub.totalScore != null ? sub.totalScore : (sub.autoScore != null ? sub.autoScore : 0)}
                              <span className="text-outline text-[10px] ml-0.5">/10</span>
                            </span>
                          ) : isFailed ? (
                            <span className="text-rose-600 font-medium text-[11px]">0/10 (Lỗi)</span>
                          ) : (
                            <span className="text-outline italic">Chưa có điểm</span>
                          )}
                        </td>
                        <td className="py-3 px-4">
                          {isProcessing ? (
                            <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-semibold bg-blue-50 text-blue-700 border border-blue-200">
                              <Loader2 className="w-3 h-3 animate-spin" />
                              <span>Đang chấm</span>
                            </span>
                          ) : isPending ? (
                            <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-semibold bg-amber-50 text-amber-700 border border-amber-200">
                              <Clock className="w-3 h-3" />
                              <span>Chờ chấm</span>
                            </span>
                          ) : isFailed ? (
                            <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-semibold bg-rose-50 text-rose-700 border border-rose-200">
                              <AlertCircle className="w-3 h-3" />
                              <span>Lỗi thi hành</span>
                            </span>
                          ) : (
                            <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200">
                              <CheckCircle2 className="w-3 h-3" />
                              <span>Hoàn tất</span>
                            </span>
                          )}
                        </td>
                        <td className="py-3 px-4 text-right">
                          <Link
                            href={`/teacher/submissions/${sub.id}`}
                            className="inline-flex items-center gap-1 px-3 py-1 bg-surface-container hover:bg-surface-container-high text-primary rounded-lg font-medium transition-colors"
                          >
                            <span>Xem chi tiết</span>
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
          {filteredSubmissions.length > 0 && (
            <div className="px-4 py-2.5 bg-surface-container-low/40 flex items-center justify-between text-xs text-outline border-t border-outline-variant/20">
              <span>
                Hiển thị <strong className="text-on-surface">{filteredSubmissions.length}</strong> trên tổng số{' '}
                <strong className="text-on-surface">{submissions?.length || 0}</strong> bài nộp
              </span>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
