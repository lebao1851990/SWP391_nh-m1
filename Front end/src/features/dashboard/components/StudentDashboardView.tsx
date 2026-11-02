'use client';

import React from 'react';
import { useStudentDashboard } from '../hooks/use-dashboard';
import { AppLink as Link } from '@/components/ui/app-link';
import {
  FileCode2,
  CheckCircle2,
  Award,
  Scale,
  Calendar,
  ArrowRight,
  ShieldCheck,
  Terminal,
  Clock,
  ExternalLink,
  AlertCircle,
  Cpu
} from 'lucide-react';
import {
  HudCorners,
  AnimatedCounter,
  SpotlightCard,
  SystemStatusBar,
  RadarEmptyState,
} from '@/components/ui/hud-card';

export function StudentDashboardView() {
  const { data, isLoading, isError, error } = useStudentDashboard();

  if (isLoading) {
    return (
      <div className="flex flex-col h-72 items-center justify-center gap-3">
        <div className="w-8 h-8 rounded-full border-2 border-primary border-t-transparent animate-spin" />
        <p className="text-on-surface-variant font-label-md text-xs">Đang tải không gian học tập...</p>
      </div>
    );
  }

  if (isError) {
    return (
      <div className="max-w-xl mx-auto my-12 p-6 rounded-2xl bg-white/95 border border-rose-200/60 shadow-lg flex flex-col items-center text-center gap-3">
        <div className="p-3 rounded-full bg-rose-50 text-rose-600">
          <AlertCircle className="w-8 h-8" />
        </div>
        <h2 className="font-headline-md text-headline-md text-on-surface">Không thể tải Bảng điều khiển</h2>
        <p className="font-body-md text-on-surface-variant text-xs">{(error as Error)?.message || 'Đã xảy ra lỗi khi kết nối đến máy chủ.'}</p>
        <button
          onClick={() => window.location.reload()}
          className="mt-2 px-4 py-2 bg-primary text-on-primary rounded-lg text-xs font-medium hover:bg-primary/90 transition-all cursor-pointer"
        >
          Thử kết nối lại
        </button>
      </div>
    );
  }

  if (!data) return null;

  return (
    <div className="relative z-10 w-full max-w-7xl mx-auto px-4 sm:px-space-lg py-space-lg min-w-0">
      <div className="flex flex-col w-full gap-space-lg min-w-0">
        {/* Welcome Banner */}
        <SpotlightCard className="relative rounded-2xl p-4 sm:p-space-lg bg-white/95 backdrop-blur-md border border-blue-200/60 shadow-[0_4px_20px_-6px_rgba(37,99,235,0.08)] overflow-hidden w-full min-w-0">
          <HudCorners colorClass="border-cyan-500/40" />
          <div className="absolute -right-16 -top-16 w-80 h-80 rounded-full bg-primary-fixed/30 blur-3xl pointer-events-none" aria-hidden="true" />
          <div className="relative z-10 flex flex-col lg:flex-row lg:items-center lg:justify-between gap-space-md w-full min-w-0">
            <div className="flex flex-col gap-1.5 min-w-0">
              <div className="flex flex-wrap items-center gap-2 text-xs">
                <span className="text-[11px] text-[#2563EB] font-semibold uppercase tracking-wide px-2 py-0.5 rounded-md bg-blue-50 border border-blue-200/70">
                  Không gian Sinh viên
                </span>
                <span className="text-slate-400 font-mono">/</span>
                <span className="font-mono text-[11px] text-slate-500 font-semibold">OVERVIEW</span>
                <SystemStatusBar moduleLabel="STUDENT_PORTAL_V2" />
              </div>
              <div className="flex flex-wrap items-center gap-space-sm min-w-0 mt-0.5">
                <h1 className="font-headline-md sm:font-display-lg text-headline-md sm:text-display-lg text-on-surface tracking-tight break-words">
                  Xin chào, {data.fullName}
                </h1>
                <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-emerald-50/80 border border-emerald-200/80 text-emerald-800 font-label-xs text-xs font-medium max-w-full truncate">
                  <span className="w-2 h-2 rounded-full bg-[#34D399] animate-pulse shrink-0" />
                  <span className="truncate font-mono">{data.email}</span>
                </span>
              </div>
              <p className="font-body-md text-body-md text-on-surface-variant max-w-2xl text-xs sm:text-sm">
                Theo dõi các bài kiểm tra lập trình, hạn chót nộp bài thi thực hành, bài nộp và kết quả đánh giá liêm chính học thuật AST.
              </p>
            </div>

            <div className="flex items-center gap-2 self-start lg:self-center shrink-0">
              <Link
                href="/student/exams"
                className="nc-btn-shimmer px-4 py-2 bg-gradient-to-r from-[#2563EB] via-[#0284C7] to-[#6366F1] hover:brightness-110 text-white rounded-lg text-xs font-medium transition-all shadow-[0_4px_14px_rgba(37,99,235,0.3)] flex items-center gap-1.5"
              >
                <Terminal className="w-3.5 h-3.5" />
                <span>Vào phòng thi</span>
              </Link>
            </div>
          </div>
        </SpotlightCard>

        {/* 4 Metric Cards (KPI v2) */}
        <section className="nc-boot-stagger grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-space-md items-stretch">
          {/* Card 1: Available Exams */}
          <div className="nc-kpi-v2 rounded-xl p-4 bg-white/95 backdrop-blur-md border border-blue-200/75 shadow-[0_2px_10px_-2px_rgba(37,99,235,0.07)] flex flex-col justify-between h-full">
            <HudCorners colorClass="border-blue-500/45" />
            <div className="flex items-start justify-between gap-2 min-h-[2.25rem]">
              <span className="text-[11px] uppercase tracking-wide text-slate-600 font-semibold line-clamp-2 leading-snug">
                Bài thi khả dụng
              </span>
              <div className="w-8 h-8 rounded-lg bg-blue-50 border border-blue-200/70 text-[#2563EB] flex items-center justify-center shrink-0">
                <Terminal className="w-4 h-4" />
              </div>
            </div>
            <div className="my-2 flex flex-col">
              <span className="font-mono text-3xl font-bold text-on-surface leading-none tabular-nums">
                <AnimatedCounter value={data.activeExamsCount} />
              </span>
              <span className="font-body-sm text-body-sm text-on-surface-variant mt-1.5 line-clamp-2" title="Các phiên thi đang mở">
                Các phiên thi đang mở
              </span>
            </div>
            <Link
              href="/student/exams"
              className="mt-1 pt-2 border-t border-slate-100 flex items-center gap-1 font-label-xs text-xs text-[#2563EB] font-semibold hover:underline"
            >
              <span>Xem bài thi trực tiếp</span>
              <ArrowRight className="w-3.5 h-3.5" />
            </Link>
          </div>

          {/* Card 2: Submitted */}
          <div className="nc-kpi-v2 rounded-xl p-4 bg-white/95 backdrop-blur-md border border-cyan-200/80 shadow-[0_2px_10px_-2px_rgba(34,211,238,0.08)] flex flex-col justify-between h-full">
            <HudCorners colorClass="border-cyan-500/45" />
            <div className="flex items-start justify-between gap-2 min-h-[2.25rem]">
              <span className="text-[11px] uppercase tracking-wide text-slate-600 font-semibold line-clamp-2 leading-snug">
                Đã nộp bài
              </span>
              <div className="w-8 h-8 rounded-lg bg-sky-50 border border-sky-200/70 text-sky-600 flex items-center justify-center shrink-0">
                <CheckCircle2 className="w-4 h-4" />
              </div>
            </div>
            <div className="my-2 flex flex-col">
              <span className="font-mono text-3xl font-bold text-on-surface leading-none tabular-nums">
                <AnimatedCounter value={data.submittedCount} />
              </span>
              <span className="font-body-sm text-body-sm text-on-surface-variant mt-1.5 line-clamp-2" title="Bài thi đã hoàn thành nộp">
                Bài thi đã hoàn thành nộp
              </span>
            </div>
            <Link
              href="/student/submissions"
              className="mt-1 pt-2 border-t border-slate-100 flex items-center gap-1 font-label-xs text-xs text-sky-700 font-semibold hover:underline"
            >
              <span>Xem lại bài nộp</span>
              <ArrowRight className="w-3.5 h-3.5" />
            </Link>
          </div>

          {/* Card 3: Graded */}
          <div className="nc-kpi-v2 rounded-xl p-4 bg-white/95 backdrop-blur-md border border-emerald-200/80 shadow-[0_2px_10px_-2px_rgba(16,185,129,0.08)] flex flex-col justify-between h-full">
            <HudCorners colorClass="border-emerald-500/45" />
            <div className="flex items-start justify-between gap-2 min-h-[2.25rem]">
              <span className="text-[11px] uppercase tracking-wide text-slate-600 font-semibold line-clamp-2 leading-snug">
                Đã chấm điểm
              </span>
              <div className="w-8 h-8 rounded-lg bg-emerald-50 border border-emerald-200/70 text-emerald-600 flex items-center justify-center shrink-0">
                <Award className="w-4 h-4" />
              </div>
            </div>
            <div className="my-2 flex flex-col">
              <span className="font-mono text-3xl font-bold text-on-surface leading-none tabular-nums">
                <AnimatedCounter value={data.gradedCount} />
              </span>
              <span className="font-body-sm text-body-sm text-on-surface-variant mt-1.5 line-clamp-2" title="Kết quả đánh giá từ giảng viên">
                Kết quả đánh giá từ giảng viên
              </span>
            </div>
            <div className="mt-1 pt-2 border-t border-slate-100 flex items-center gap-1 font-label-xs text-xs text-emerald-700 font-medium">
              <CheckCircle2 className="w-3.5 h-3.5" />
              <span>Điểm số đã ghi nhận</span>
            </div>
          </div>

          {/* Card 4: Pending Appeals */}
          <div className="nc-kpi-v2 rounded-xl p-4 bg-white/95 backdrop-blur-md border border-indigo-200/80 shadow-[0_2px_10px_-2px_rgba(99,102,241,0.08)] flex flex-col justify-between h-full">
            <HudCorners colorClass="border-indigo-500/45" />
            <div className="flex items-start justify-between gap-2 min-h-[2.25rem]">
              <span className="text-[11px] uppercase tracking-wide text-slate-600 font-semibold line-clamp-2 leading-snug">
                Đơn khiếu nại đang chờ xử lý
              </span>
              <div className="w-8 h-8 rounded-lg bg-purple-50 border border-purple-200/70 text-purple-600 flex items-center justify-center shrink-0">
                <Scale className="w-4 h-4" />
              </div>
            </div>
            <div className="my-2 flex flex-col">
              <div className="flex items-baseline justify-between gap-2">
                <span className="font-mono text-3xl font-bold text-on-surface leading-none tabular-nums">
                  <AnimatedCounter value={data.pendingAppealsCount} />
                </span>
                <span className="text-[11px] px-2 py-0.5 rounded-full font-semibold bg-purple-50 text-purple-700 border border-purple-200/80">
                  Đang xem xét
                </span>
              </div>
              <span className="font-body-sm text-body-sm text-on-surface-variant mt-1.5 line-clamp-2" title="Đang chờ giảng viên phản hồi">
                Đang chờ giảng viên phản hồi
              </span>
            </div>
            <Link
              href="/student/appeals"
              className="mt-1 pt-2 border-t border-slate-100 flex items-center gap-1 font-label-xs text-xs text-purple-700 font-semibold hover:underline"
            >
              <span>Xem đơn phúc khảo</span>
              <ArrowRight className="w-3.5 h-3.5" />
            </Link>
          </div>
        </section>

        {/* Upcoming Deadlines */}
        <section className="flex flex-col gap-space-sm">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-space-xs">
              <Calendar className="w-4 h-4 text-primary" />
              <h2 className="font-headline-sm text-headline-sm text-on-surface tracking-tight">Hạn chót sắp tới</h2>
            </div>
            <span className="font-label-xs text-xs text-outline">Múi giờ: Asia/Ho_Chi_Minh (UTC+7)</span>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-space-md">
            {data.upcomingDeadlines && data.upcomingDeadlines.length > 0 ? (
              data.upcomingDeadlines.map((exam, idx) => (
                <div key={idx} className="nc-holo-card rounded-xl p-space-md bg-white/95 backdrop-blur-md border border-blue-200/60 shadow-2xs flex flex-col justify-between">
                  <HudCorners colorClass="border-blue-500/35" />
                  <div className="flex flex-col gap-1.5">
                    <div className="flex justify-between items-start gap-2">
                      <span className="font-label-md font-semibold text-on-surface line-clamp-2" title={exam.title}>
                        {exam.title}
                      </span>
                      <span className="px-2 py-0.5 rounded bg-slate-100 border border-slate-200/80 font-mono text-[10px] font-semibold text-slate-700 shrink-0">
                        {exam.courseCode || 'SE-PRACTICAL'}
                      </span>
                    </div>
                    <div className="flex items-center gap-1.5 text-xs text-primary font-medium font-mono">
                      <Clock className="w-3.5 h-3.5" />
                      <span>{exam.examDate ? new Date(exam.examDate).toLocaleString('vi-VN') : 'Linh hoạt'}</span>
                    </div>
                  </div>
                  <div className="mt-space-md pt-2 border-t border-slate-100 flex justify-end">
                    <Link
                      href="/student/exams"
                      className="nc-btn-shimmer inline-flex items-center gap-1 px-3 py-1 rounded-lg bg-primary text-on-primary text-xs font-medium hover:bg-primary/90 transition-colors"
                    >
                      <span>Vào thi</span>
                      <ArrowRight className="w-3.5 h-3.5" />
                    </Link>
                  </div>
                </div>
              ))
            ) : (
              <div className="col-span-full rounded-xl bg-white/95 border border-slate-200/70">
                <RadarEmptyState message="Không có hạn chót bài thi nào sắp tới. Bạn đã hoàn thành tất cả nhiệm vụ!" />
              </div>
            )}
          </div>
        </section>

        {/* Recent Submissions & Results Table */}
        <section className="flex flex-col gap-space-sm w-full min-w-0">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-space-xs">
              <FileCode2 className="w-4 h-4 text-primary" />
              <h2 className="font-headline-sm text-headline-sm text-on-surface tracking-tight">Bài nộp &amp; Kết quả gần đây</h2>
            </div>
            <Link href="/student/submissions" className="text-xs text-primary hover:underline font-medium">
              Xem tất cả bài nộp
            </Link>
          </div>

          <SpotlightCard className="rounded-xl bg-white/95 backdrop-blur-md border border-slate-200/80 shadow-[0_4px_20px_-6px_rgba(15,23,42,0.06)] overflow-hidden w-full min-w-0">
            <div className="relative z-10 overflow-x-auto w-full">
              <table className="w-full min-w-[720px] table-fixed text-left border-collapse text-xs">
                <colgroup>
                  <col className="w-[18%]" />
                  <col className="w-[22%]" />
                  <col className="w-[14%]" />
                  <col className="w-[16%]" />
                  <col className="w-[18%]" />
                  <col className="w-[12%]" />
                </colgroup>
                <thead className="sticky top-0 z-10 bg-slate-50/95 backdrop-blur-md text-slate-600 text-[11px] uppercase tracking-wider border-b border-slate-200/80">
                  <tr>
                    <th className="py-3 px-4 font-semibold text-left">Mã bài nộp</th>
                    <th className="py-3 px-4 font-semibold text-left">Thời gian nộp</th>
                    <th className="py-3 px-4 font-semibold text-left">Điểm số</th>
                    <th className="py-3 px-4 font-semibold text-left">Trạng thái</th>
                    <th className="py-3 px-4 font-semibold text-left">Kiểm định AST</th>
                    <th className="py-3 px-4 font-semibold text-right">Thao tác</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 text-on-surface">
                  {data.recentSubmissions && data.recentSubmissions.length > 0 ? (
                    data.recentSubmissions.map((sub, idx) => (
                      <tr key={idx} data-row="true" className="nc-table-row transition-colors">
                        <td className="py-3 px-4 font-mono font-semibold text-on-surface">
                          #{sub.id ? sub.id.slice(0, 8) : 'SUBMITTED'}
                        </td>
                        <td className="py-3 px-4 text-on-surface-variant font-mono text-[11px]">
                          {sub.submittedAt ? new Date(sub.submittedAt).toLocaleString('vi-VN') : 'N/A'}
                        </td>
                        <td className="py-3 px-4">
                          <span className="font-mono bg-slate-100 border border-slate-200/80 px-2 py-0.5 rounded font-bold text-on-surface">
                            {sub.totalScore ?? '--'}
                          </span>
                          <span className="text-outline font-mono text-[11px] ml-0.5">/10</span>
                        </td>
                        <td className="py-3 px-4">
                          <span className={`inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-[11px] font-semibold border ${
                            sub.status === 'GRADED'
                              ? 'bg-emerald-50 text-emerald-800 border-emerald-200'
                              : 'bg-amber-50 text-amber-800 border-amber-200'
                          }`}>
                            <span className={`w-1.5 h-1.5 rounded-full ${sub.status === 'GRADED' ? 'bg-[#34D399]' : 'bg-[#F59E0B]'}`} />
                            {sub.status === 'GRADED' ? 'Đã chấm' : 'Đã nộp'}
                          </span>
                        </td>
                        <td className="py-3 px-4">
                          <div className="flex items-center gap-1.5 text-emerald-700">
                            <ShieldCheck className="w-3.5 h-3.5" />
                            <span className="font-label-xs font-semibold">Đã xác thực AST</span>
                          </div>
                        </td>
                        <td className="py-3 px-4 text-right">
                          <Link
                            className="inline-flex items-center gap-1 px-2.5 py-1 rounded-lg bg-slate-50 hover:bg-blue-50 border border-slate-200/80 hover:border-blue-300 text-[#2563EB] font-medium transition-colors"
                            title="Xem chi tiết"
                            href={`/student/submissions/${sub.id}`}
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
                        <RadarEmptyState message="Chưa có bài nộp nào được ghi nhận. Hãy tham gia bài thi đang hoạt động để nộp bài làm của bạn." />
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </SpotlightCard>
        </section>

        {/* Technical Specification & Honor Code Section */}
        <section className="grid grid-cols-1 lg:grid-cols-3 gap-space-md">
          {/* Docker Sandbox Specs */}
          <div className="nc-holo-card rounded-xl p-space-md bg-white/95 backdrop-blur-md border border-slate-200/80 shadow-2xs flex flex-col justify-between">
            <HudCorners colorClass="border-cyan-500/35" />
            <div>
              <div className="flex items-center justify-between pb-2 border-b border-slate-200/70">
                <span className="text-[11px] uppercase tracking-wide text-slate-500 font-semibold">Môi trường Docker Sandbox</span>
                <span className="text-[11px] px-2 py-0.5 rounded-full font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200 inline-flex items-center gap-1">
                  <span className="w-1.5 h-1.5 rounded-full bg-[#34D399]" />
                  Hoạt động
                </span>
              </div>
              <h4 className="font-headline-sm text-headline-sm text-on-surface mt-2 font-bold flex items-center gap-1.5">
                <Cpu className="w-4 h-4 text-primary" />
                Vùng thực thi cô lập Docker Pod
              </h4>
              <div className="mt-space-sm space-y-1.5 text-xs">
                <div className="flex justify-between py-1 px-2.5 rounded bg-slate-50 border border-slate-100">
                  <span className="text-outline">Môi trường chạy</span>
                  <span className="text-on-surface font-semibold font-mono">OpenJDK 21 + Clang</span>
                </div>
                <div className="flex justify-between py-1 px-2.5 rounded bg-slate-50 border border-slate-100">
                  <span className="text-outline">Giới hạn bộ nhớ</span>
                  <span className="text-on-surface font-semibold font-mono">512MB cgroup v2</span>
                </div>
                <div className="flex justify-between py-1 px-2.5 rounded bg-slate-50 border border-slate-100">
                  <span className="text-outline">Giới hạn thời gian</span>
                  <span className="text-on-surface font-semibold font-mono">3.00s mỗi Test Case</span>
                </div>
              </div>
            </div>
          </div>

          {/* Honor Code & Security Standard */}
          <div className="nc-holo-card lg:col-span-2 rounded-xl p-space-md bg-white/95 backdrop-blur-md border border-slate-200/80 shadow-2xs flex flex-col justify-between">
            <HudCorners colorClass="border-blue-500/35" />
            <div className="flex flex-col gap-2">
              <div className="flex items-center justify-between pb-2 border-b border-slate-200/70">
                <span className="text-[11px] uppercase tracking-wide text-slate-500 font-semibold">
                  Bộ môn Kỹ thuật Phần mềm
                </span>
                <span className="text-[11px] px-2 py-0.5 rounded-full font-semibold bg-blue-50 text-blue-700 border border-blue-200">
                  Chuẩn mực Học thuật
                </span>
              </div>
              <h4 className="font-headline-sm text-headline-sm text-on-surface font-bold flex items-center gap-1.5">
                <ShieldCheck className="w-4 h-4 text-emerald-600" />
                Kiểm định Cú pháp AST &amp; Bytecode
              </h4>
              <p className="text-xs text-on-surface-variant leading-relaxed">
                Tất cả mã nguồn nộp lên đều được tự động phân tích cây cú pháp trừu tượng AST và token hóa. Thuật toán phát hiện đạo văn so sánh cấu trúc cú pháp của thuật toán một cách chính xác, không bị ảnh hưởng bởi việc đổi tên biến, khoảng trắng hay chú thích.
              </p>
            </div>

            <div className="pt-space-md mt-2 border-t border-slate-200/70 flex items-center justify-between text-xs text-outline">
              <span>Áp dụng Quy tắc Liêm chính Học thuật</span>
              <span className="font-mono text-[11px] text-on-surface-variant">Nền tảng AITA Intelligent v2.4</span>
            </div>
          </div>
        </section>
      </div>
    </div>
  );
}
