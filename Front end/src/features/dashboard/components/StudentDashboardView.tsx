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
      <div className="max-w-xl mx-auto my-12 p-6 rounded-2xl bg-surface-container-lowest border border-rose-200/60 shadow-lg flex flex-col items-center text-center gap-3">
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
    <div className="w-full max-w-7xl mx-auto px-4 sm:px-space-lg py-space-lg min-w-0">
      <div className="flex flex-col w-full gap-space-lg min-w-0">
        {/* Welcome Banner */}
        <section className="relative rounded-2xl p-4 sm:p-space-lg bg-surface-container-lowest border border-outline-variant/40 shadow-xs overflow-hidden w-full min-w-0">
          <div className="absolute -right-16 -top-16 w-80 h-80 rounded-full bg-primary-fixed/30 blur-3xl pointer-events-none"></div>
          <div className="relative z-10 flex flex-col lg:flex-row lg:items-center lg:justify-between gap-space-md w-full min-w-0">
            <div className="flex flex-col gap-space-xs min-w-0">
              <div className="flex flex-wrap items-center gap-space-sm min-w-0">
                <h1 className="font-headline-md sm:font-display-lg text-headline-md sm:text-display-lg text-on-surface tracking-tight break-words">
                  Xin chào, {data.fullName}
                </h1>
                <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-surface-container border border-outline-variant/40 text-on-surface-variant font-label-xs text-xs font-medium max-w-full truncate">
                  <span className="w-2 h-2 rounded-full bg-emerald-500 shrink-0"></span>
                  <span className="truncate">{data.email}</span>
                </span>
              </div>
              <p className="font-body-md text-body-md text-on-surface-variant max-w-2xl text-xs sm:text-sm">
                Theo dõi các bài kiểm tra lập trình, hạn chót nộp bài thi thực hành, bài nộp và kết quả đánh giá liêm chính học thuật AST.
              </p>
            </div>

            <div className="flex items-center gap-2 self-start lg:self-center">
              <Link 
                href="/student/exams" 
                className="px-4 py-2 bg-primary text-on-primary rounded-lg text-xs font-medium hover:bg-primary/90 transition-all shadow-sm flex items-center gap-1.5"
              >
                <Terminal className="w-3.5 h-3.5" />
                <span>Vào phòng thi</span>
              </Link>
            </div>
          </div>
        </section>

        {/* 4 Metric Cards */}
        <section className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-space-md">
          {/* Card 1: Available Exams */}
          <div className="rounded-xl p-space-md bg-surface-container-lowest border border-outline-variant/40 shadow-xs flex flex-col justify-between hover:border-primary/40 transition-all">
            <div className="flex items-center justify-between">
              <span className="font-label-xs uppercase tracking-wider text-outline font-semibold">Bài thi khả dụng</span>
              <div className="w-8 h-8 rounded-lg bg-blue-50 text-blue-600 flex items-center justify-center">
                <Terminal className="w-4 h-4" />
              </div>
            </div>
            <div className="mt-space-md flex flex-col">
              <span className="font-display-lg text-display-lg font-bold text-on-surface leading-none">
                {data.activeExamsCount}
              </span>
              <span className="font-body-sm text-body-sm text-on-surface-variant mt-1">
                Các phiên thi đang mở
              </span>
            </div>
            <Link 
              href="/student/exams" 
              className="mt-space-sm pt-space-xs flex items-center gap-1 font-label-xs text-xs text-primary font-semibold hover:underline"
            >
              <span>Xem bài thi trực tiếp</span>
              <ArrowRight className="w-3.5 h-3.5" />
            </Link>
          </div>

          {/* Card 2: Submitted */}
          <div className="rounded-xl p-space-md bg-surface-container-lowest border border-outline-variant/40 shadow-xs flex flex-col justify-between hover:border-sky-400/40 transition-all">
            <div className="flex items-center justify-between">
              <span className="font-label-xs uppercase tracking-wider text-outline font-semibold">Đã nộp bài</span>
              <div className="w-8 h-8 rounded-lg bg-sky-50 text-sky-600 flex items-center justify-center">
                <CheckCircle2 className="w-4 h-4" />
              </div>
            </div>
            <div className="mt-space-md flex flex-col">
              <span className="font-display-lg text-display-lg font-bold text-on-surface leading-none">
                {data.submittedCount}
              </span>
              <span className="font-body-sm text-body-sm text-on-surface-variant mt-1">
                Bài thi đã hoàn thành nộp
              </span>
            </div>
            <Link 
              href="/student/submissions" 
              className="mt-space-sm pt-space-xs flex items-center gap-1 font-label-xs text-xs text-sky-700 font-semibold hover:underline"
            >
              <span>Xem lại bài nộp</span>
              <ArrowRight className="w-3.5 h-3.5" />
            </Link>
          </div>

          {/* Card 3: Graded */}
          <div className="rounded-xl p-space-md bg-surface-container-lowest border border-outline-variant/40 shadow-xs flex flex-col justify-between hover:border-emerald-400/40 transition-all">
            <div className="flex items-center justify-between">
              <span className="font-label-xs uppercase tracking-wider text-outline font-semibold">Đã chấm điểm</span>
              <div className="w-8 h-8 rounded-lg bg-emerald-50 text-emerald-600 flex items-center justify-center">
                <Award className="w-4 h-4" />
              </div>
            </div>
            <div className="mt-space-md flex flex-col">
              <span className="font-display-lg text-display-lg font-bold text-on-surface leading-none">
                {data.gradedCount}
              </span>
              <span className="font-body-sm text-body-sm text-on-surface-variant mt-1">
                Kết quả đánh giá từ giảng viên
              </span>
            </div>
            <div className="mt-space-sm pt-space-xs flex items-center gap-1 font-label-xs text-xs text-emerald-700 font-medium">
              <CheckCircle2 className="w-3.5 h-3.5" />
              <span>Điểm số đã ghi nhận</span>
            </div>
          </div>

          {/* Card 4: Pending Appeals */}
          <div className="rounded-xl p-space-md bg-surface-container-lowest border border-outline-variant/40 shadow-xs flex flex-col justify-between hover:border-purple-400/40 transition-all">
            <div className="flex items-center justify-between">
              <span className="font-label-xs uppercase tracking-wider text-outline font-semibold">Đơn khiếu nại đang chờ xử lý</span>
              <div className="w-8 h-8 rounded-lg bg-purple-50 text-purple-600 flex items-center justify-center">
                <Scale className="w-4 h-4" />
              </div>
            </div>
            <div className="mt-space-md flex flex-col">
              <div className="flex items-center gap-2">
                <span className="font-display-lg text-display-lg font-bold text-on-surface leading-none">
                  {data.pendingAppealsCount}
                </span>
                <span className="text-[10px] px-1.5 py-0.5 rounded font-semibold bg-purple-50 text-purple-700">
                  Đang xem xét
                </span>
              </div>
              <span className="font-body-sm text-body-sm text-on-surface-variant mt-1">
                Đang chờ giảng viên phản hồi
              </span>
            </div>
            <Link 
              href="/student/appeals" 
              className="mt-space-sm pt-space-xs flex items-center gap-1 font-label-xs text-xs text-purple-700 font-semibold hover:underline"
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
                <div key={idx} className="rounded-xl p-space-md bg-surface-container-lowest border border-outline-variant/40 shadow-xs flex flex-col justify-between hover:border-primary/40 transition-all">
                  <div className="flex flex-col gap-1.5">
                    <div className="flex justify-between items-start gap-2">
                      <span className="font-label-md font-semibold text-on-surface line-clamp-1">{exam.title}</span>
                      <span className="px-2 py-0.5 rounded bg-surface-container font-mono text-[10px] font-semibold text-on-surface-variant shrink-0">
                        {exam.courseCode || 'SE-PRACTICAL'}
                      </span>
                    </div>
                    <div className="flex items-center gap-1.5 text-xs text-primary font-medium">
                      <Clock className="w-3.5 h-3.5" />
                      <span>{exam.examDate ? new Date(exam.examDate).toLocaleString('vi-VN') : 'Linh hoạt'}</span>
                    </div>
                  </div>
                  <div className="mt-space-md pt-2 border-t border-outline-variant/30 flex justify-end">
                    <Link 
                      href="/student/exams"
                      className="inline-flex items-center gap-1 px-3 py-1 rounded-lg bg-primary text-on-primary text-xs font-medium hover:bg-primary/90 transition-colors"
                    >
                      <span>Vào thi</span>
                      <ArrowRight className="w-3.5 h-3.5" />
                    </Link>
                  </div>
                </div>
              ))
            ) : (
              <div className="col-span-full rounded-xl p-space-lg bg-surface-container-lowest border border-outline-variant/30 text-outline text-center text-xs">
                Không có hạn chót bài thi nào sắp tới. Bạn đã hoàn thành tất cả nhiệm vụ!
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

          <div className="rounded-xl bg-surface-container-lowest border border-outline-variant/40 shadow-xs overflow-hidden w-full min-w-0">
            <div className="overflow-x-auto w-full">
              <table className="w-full text-left border-collapse text-xs">
                <thead>
                  <tr className="bg-surface-container-low text-on-surface-variant font-label-xs uppercase tracking-wider border-b border-outline-variant/30">
                    <th className="py-2.5 px-4 font-semibold">Mã bài nộp</th>
                    <th className="py-2.5 px-4 font-semibold">Thời gian nộp</th>
                    <th className="py-2.5 px-4 font-semibold">Điểm số</th>
                    <th className="py-2.5 px-4 font-semibold">Trạng thái</th>
                    <th className="py-2.5 px-4 font-semibold">Kiểm định AST</th>
                    <th className="py-2.5 px-4 font-semibold text-right">Thao tác</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-outline-variant/20 text-on-surface">
                  {data.recentSubmissions && data.recentSubmissions.length > 0 ? (
                    data.recentSubmissions.map((sub, idx) => (
                      <tr key={idx} className="hover:bg-surface-container-low/60 transition-colors">
                        <td className="py-3 px-4 font-mono font-medium text-on-surface">
                          #{sub.id ? sub.id.slice(0, 8) : 'SUBMITTED'}
                        </td>
                        <td className="py-3 px-4 text-on-surface-variant">
                          {sub.submittedAt ? new Date(sub.submittedAt).toLocaleString('vi-VN') : 'N/A'}
                        </td>
                        <td className="py-3 px-4">
                          <span className="font-mono bg-surface-container px-2 py-0.5 rounded font-bold text-on-surface">
                            {sub.totalScore ?? '--'}
                          </span>
                          <span className="text-outline text-[11px] ml-0.5">/10</span>
                        </td>
                        <td className="py-3 px-4">
                          <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-semibold ${
                            sub.status === 'GRADED' 
                              ? 'bg-emerald-50 text-emerald-700 border border-emerald-200' 
                              : 'bg-amber-50 text-amber-700 border border-amber-200'
                          }`}>
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
                            className="inline-flex items-center gap-1 px-2.5 py-1 rounded bg-surface-container hover:bg-surface-container-high text-primary font-medium transition-colors" 
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
                      <td colSpan={6} className="py-8 text-center text-outline font-label-md">
                        Chưa có bài nộp nào được ghi nhận. Hãy tham gia bài thi đang hoạt động để nộp bài làm của bạn.
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </section>

        {/* Technical Specification & Honor Code Section */}
        <section className="grid grid-cols-1 lg:grid-cols-3 gap-space-md">
          {/* Docker Sandbox Specs */}
          <div className="rounded-xl p-space-md bg-surface-container-lowest border border-outline-variant/40 shadow-xs flex flex-col justify-between">
            <div>
              <div className="flex items-center justify-between pb-2 border-b border-outline-variant/20">
                <span className="font-label-xs uppercase tracking-wider text-outline font-semibold">Môi trường Docker Sandbox</span>
                <span className="text-[10px] px-1.5 py-0.5 rounded font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200">
                  Hoạt động
                </span>
              </div>
              <h4 className="font-headline-sm text-headline-sm text-on-surface mt-2 font-bold flex items-center gap-1.5">
                <Cpu className="w-4 h-4 text-primary" />
                Vùng thực thi cô lập Docker Pod
              </h4>
              <div className="mt-space-sm space-y-1.5 text-xs">
                <div className="flex justify-between py-1 px-2.5 rounded bg-surface-container-low">
                  <span className="text-outline">Môi trường chạy</span>
                  <span className="text-on-surface font-semibold font-mono">OpenJDK 21 + Clang</span>
                </div>
                <div className="flex justify-between py-1 px-2.5 rounded bg-surface-container-low">
                  <span className="text-outline">Giới hạn bộ nhớ</span>
                  <span className="text-on-surface font-semibold font-mono">512MB cgroup v2</span>
                </div>
                <div className="flex justify-between py-1 px-2.5 rounded bg-surface-container-low">
                  <span className="text-outline">Giới hạn thời gian</span>
                  <span className="text-on-surface font-semibold font-mono">3.00s mỗi Test Case</span>
                </div>
              </div>
            </div>
          </div>

          {/* Honor Code & Security Standard */}
          <div className="lg:col-span-2 rounded-xl p-space-md bg-surface-container-lowest border border-outline-variant/40 shadow-xs flex flex-col justify-between">
            <div className="flex flex-col gap-2">
              <div className="flex items-center justify-between pb-2 border-b border-outline-variant/20">
                <span className="font-label-xs uppercase tracking-wider text-outline font-semibold">
                  Bộ môn Kỹ thuật Phần mềm
                </span>
                <span className="text-[10px] px-1.5 py-0.5 rounded font-semibold bg-blue-50 text-blue-700 border border-blue-200">
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

            <div className="pt-space-md mt-2 border-t border-outline-variant/20 flex items-center justify-between text-xs text-outline">
              <span>Áp dụng Quy tắc Liêm chính Học thuật</span>
              <span className="font-mono text-[11px] text-on-surface-variant">Nền tảng AITA Intelligent v2.4</span>
            </div>
          </div>
        </section>
      </div>
    </div>
  );
}
