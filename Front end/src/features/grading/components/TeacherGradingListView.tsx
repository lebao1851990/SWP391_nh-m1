'use client';

import React, { useState } from 'react';
import { useTeacherGradingList } from '../hooks';
import { ReviewStatus, PublicationStatus } from '../types';
import { AppLink as Link } from '@/components/ui/app-link';
import {
  FileCheck2,
  Clock,
  CheckCircle2,
  AlertCircle,
  Eye,
  Filter,
  GraduationCap,
  Sparkles,
  Search,
  SendHorizontal,
} from 'lucide-react';

export function TeacherGradingListView() {
  const [selectedStatus, setSelectedStatus] = useState<string>('PENDING_REVIEW');
  const [searchTerm, setSearchTerm] = useState<string>('');

  const { data: submissions, isLoading, isError, error, refetch } = useTeacherGradingList(selectedStatus);

  const filterTabs = [
    { label: 'Chờ duyệt điểm', value: 'PENDING_REVIEW', countColor: 'bg-amber-100 text-amber-800' },
    { label: 'Đã phê duyệt', value: 'APPROVED', countColor: 'bg-blue-100 text-blue-800' },
    { label: 'Cần sửa đổi', value: 'REVISION_REQUIRED', countColor: 'bg-rose-100 text-rose-800' },
    { label: 'Tất cả bài nộp', value: 'ALL', countColor: 'bg-slate-100 text-slate-800' },
  ];

  const filteredItems = (submissions || []).filter((item) => {
    if (!searchTerm.trim()) return true;
    const term = searchTerm.toLowerCase();
    return (
      item.studentName?.toLowerCase().includes(term) ||
      item.studentEmail?.toLowerCase().includes(term) ||
      item.examTitle?.toLowerCase().includes(term) ||
      item.questionTitle?.toLowerCase().includes(term) ||
      item.submissionId?.toLowerCase().includes(term)
    );
  });

  return (
    <div className="max-w-7xl mx-auto px-space-lg py-space-lg flex flex-col w-full gap-space-lg">
      {/* Header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-space-md pb-space-md border-b border-outline-variant/30">
        <div className="flex flex-col gap-1">
          <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wider text-primary">
            <FileCheck2 className="w-4 h-4 text-primary" />
            <span>Quy trình Đánh giá &amp; Công bố Điểm</span>
          </div>
          <h1 className="font-display-lg text-display-lg text-on-surface tracking-tight">
            Thẩm định &amp; Phê duyệt Điểm bài thi
          </h1>
          <p className="text-xs sm:text-sm text-on-surface-variant max-w-2xl">
            Điểm tự động chấm từ Docker sandbox được giữ ở trạng thái <strong>tạm tính nội bộ</strong>. Giảng viên cần kiểm tra mã nguồn, xác nhận kết quả test case, phê duyệt và công bố chính thức cho sinh viên.
          </p>
        </div>
      </div>

      {/* Filter Tabs & Search */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div className="flex items-center gap-1.5 overflow-x-auto pb-1">
          {filterTabs.map((tab) => (
            <button
              key={tab.value}
              onClick={() => setSelectedStatus(tab.value)}
              className={`px-3.5 py-1.5 rounded-lg text-xs font-medium transition-all cursor-pointer whitespace-nowrap flex items-center gap-1.5 ${
                selectedStatus === tab.value
                  ? 'bg-primary text-on-primary shadow-xs font-semibold'
                  : 'bg-surface-container hover:bg-surface-container-high text-on-surface border border-outline-variant/30'
              }`}
            >
              <span>{tab.label}</span>
            </button>
          ))}
        </div>

        <div className="relative w-full sm:w-72">
          <Search className="w-4 h-4 absolute left-3 top-2.5 text-on-surface-variant" />
          <input
            type="text"
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            placeholder="Tìm theo sinh viên, bài thi, câu hỏi..."
            className="w-full pl-9 pr-3 py-1.5 bg-surface-container-lowest border border-outline-variant/40 rounded-lg text-xs text-on-surface focus:outline-none focus:ring-1 focus:ring-primary"
          />
        </div>
      </div>

      {/* Submissions Table / Cards */}
      {isLoading ? (
        <div className="flex flex-col h-64 items-center justify-center gap-3">
          <div className="w-8 h-8 rounded-full border-2 border-primary border-t-transparent animate-spin" />
          <p className="text-xs text-on-surface-variant font-medium">Đang tải danh sách bài nộp cần thẩm định...</p>
        </div>
      ) : isError ? (
        <div className="p-8 rounded-2xl bg-surface-container-lowest border border-outline-variant/40 text-center flex flex-col items-center gap-3">
          <AlertCircle className="w-8 h-8 text-rose-600" />
          <h2 className="text-base font-semibold text-on-surface">Không thể tải dữ liệu thẩm định</h2>
          <p className="text-xs text-on-surface-variant">{(error as Error)?.message || 'Vui lòng thử lại sau.'}</p>
          <button
            onClick={() => refetch()}
            className="px-4 py-2 bg-primary text-on-primary rounded-lg text-xs font-medium"
          >
            Tải lại
          </button>
        </div>
      ) : filteredItems.length === 0 ? (
        <div className="p-12 rounded-2xl bg-surface-container-lowest border border-outline-variant/40 text-center flex flex-col items-center gap-3">
          <div className="p-3 rounded-full bg-slate-50 text-slate-500">
            <CheckCircle2 className="w-8 h-8 text-emerald-600" />
          </div>
          <h3 className="text-base font-semibold text-on-surface">Không có bài nộp nào phù hợp</h3>
          <p className="text-xs text-on-surface-variant max-w-md">
            Hiện không có bài nộp nào thuộc trạng thái này cần bạn xử lý. Bạn có thể chọn tab khác để kiểm tra.
          </p>
        </div>
      ) : (
        <div className="overflow-hidden rounded-2xl border border-outline-variant/40 bg-surface-container-lowest shadow-xs">
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse text-xs">
              <thead>
                <tr className="border-b border-outline-variant/30 bg-surface-container/40 text-on-surface-variant font-semibold">
                  <th className="px-4 py-3">Bài thi / Câu hỏi</th>
                  <th className="px-4 py-3">Sinh viên</th>
                  <th className="px-4 py-3">Điểm tạm tính</th>
                  <th className="px-4 py-3">Trạng thái Duyệt</th>
                  <th className="px-4 py-3">Công bố</th>
                  <th className="px-4 py-3 text-right">Thao tác</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-outline-variant/20">
                {filteredItems.map((item) => {
                  const isApproved = item.reviewStatus === 'APPROVED';
                  const isRevision = item.reviewStatus === 'REVISION_REQUIRED';
                  const isPending = item.reviewStatus === 'PENDING_REVIEW';
                  const isLegacy = item.reviewStatus === 'LEGACY_AUTO';
                  const isPublished = item.publicationStatus === 'PUBLISHED';

                  return (
                    <tr
                      key={item.submissionId}
                      className="hover:bg-surface-container-low transition-colors"
                    >
                      <td className="px-4 py-3.5">
                        <div className="flex flex-col gap-0.5">
                          <span className="font-semibold text-on-surface text-xs">{item.examTitle}</span>
                          <span className="text-[11px] text-primary font-medium">
                            {item.questionTitle ? `Câu ${item.questionOrder + 1}: ${item.questionTitle}` : 'Bài tập đơn'}
                          </span>
                          <span className="text-[10px] text-outline font-mono">#{item.submissionId.substring(0, 8)}</span>
                        </div>
                      </td>
                      <td className="px-4 py-3.5">
                        <div className="flex flex-col gap-0.5">
                          <span className="font-semibold text-on-surface">{item.studentName || 'Sinh viên'}</span>
                          <span className="text-[11px] text-outline">{item.studentEmail || 'N/A'}</span>
                        </div>
                      </td>
                      <td className="px-4 py-3.5">
                        <div className="flex items-baseline gap-1">
                          <span className="text-base font-bold text-primary font-mono">
                            {item.approvedScore ?? item.provisionalScore ?? '—'}
                          </span>
                          <span className="text-[11px] text-outline">/ {item.maxScore} đ</span>
                        </div>
                        {item.testCasesTotal != null && (
                          <span className="text-[10px] text-emerald-700 font-medium">
                            Test: {item.testCasesPassed ?? 0}/{item.testCasesTotal}
                          </span>
                        )}
                      </td>
                      <td className="px-4 py-3.5">
                        {isPending && (
                          <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-[11px] font-semibold bg-amber-50 text-amber-800 border border-amber-200">
                            <Clock className="w-3 h-3 text-amber-600 animate-pulse" />
                            <span>Chờ duyệt</span>
                          </span>
                        )}
                        {isApproved && (
                          <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-[11px] font-semibold bg-blue-50 text-blue-800 border border-blue-200">
                            <CheckCircle2 className="w-3 h-3 text-blue-600" />
                            <span>Đã duyệt ({item.approvedScore} đ)</span>
                          </span>
                        )}
                        {isLegacy && (
                          <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-[11px] font-semibold bg-purple-50 text-purple-800 border border-purple-200">
                            <CheckCircle2 className="w-3 h-3 text-purple-600" />
                            <span>Tự động (Legacy V21)</span>
                          </span>
                        )}
                        {isRevision && (
                          <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-[11px] font-semibold bg-rose-50 text-rose-800 border border-rose-200">
                            <AlertCircle className="w-3 h-3 text-rose-600" />
                            <span>Cần sửa đổi</span>
                          </span>
                        )}
                      </td>
                      <td className="px-4 py-3.5">
                        {isPublished ? (
                          <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-[11px] font-semibold bg-emerald-50 text-emerald-800 border border-emerald-200">
                            <CheckCircle2 className="w-3 h-3 text-emerald-600" />
                            <span>Đã công bố</span>
                          </span>
                        ) : (
                          <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-[11px] font-medium bg-slate-100 text-slate-700 border border-slate-200">
                            <span>Chưa công bố</span>
                          </span>
                        )}
                      </td>
                      <td className="px-4 py-3.5 text-right">
                        <Link
                          href={`/teacher/grading/${item.submissionId}`}
                          className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-primary hover:bg-primary/90 text-on-primary text-xs font-semibold transition-colors shadow-xs"
                        >
                          <Eye className="w-3.5 h-3.5" />
                          <span>Xem &amp; Thẩm định</span>
                        </Link>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
}

