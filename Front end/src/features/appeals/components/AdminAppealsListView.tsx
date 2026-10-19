'use client';

import React, { useState } from 'react';
import { AppLink as Link } from '@/components/ui/app-link';
import { useAdminAppeals } from '../hooks';
import { 
  Search, 
  Clock, 
  CheckCircle2, 
  XCircle, 
  ExternalLink, 
  Scale, 
  AlertCircle,
  FileText,
  X,
  RotateCcw,
  ShieldCheck,
  Award
} from 'lucide-react';

export function AdminAppealsListView() {
  const [filter, setFilter] = useState<'ALL' | 'PENDING' | 'APPROVED' | 'REJECTED'>('ALL');
  const { data: appeals, isLoading, isError, refetch } = useAdminAppeals(filter);
  const [search, setSearch] = useState('');

  if (isLoading) {
    return (
      <div className="flex flex-col h-72 items-center justify-center gap-3">
        <div className="w-8 h-8 rounded-full border-2 border-primary border-t-transparent animate-spin" />
        <p className="text-on-surface-variant font-label-md text-xs">Đang tải danh sách khiếu nại hệ thống...</p>
      </div>
    );
  }

  if (isError) {
    return (
      <div className="max-w-xl mx-auto my-12 p-8 rounded-2xl bg-surface-container-lowest border border-rose-200/60 shadow-lg text-center flex flex-col items-center gap-3">
        <div className="p-3 rounded-full bg-rose-50 text-rose-600">
          <AlertCircle className="w-8 h-8" />
        </div>
        <h2 className="font-headline-md text-headline-md text-on-surface">Không thể tải danh sách khiếu nại</h2>
        <p className="font-body-md text-on-surface-variant text-xs">Lỗi khi lấy dữ liệu khiếu nại từ máy chủ Admin.</p>
        <button 
          onClick={() => refetch()} 
          className="mt-2 px-4 py-2 bg-primary text-on-primary rounded-lg text-xs font-medium cursor-pointer flex items-center gap-1.5"
        >
          <RotateCcw className="w-3.5 h-3.5" />
          <span>Thử lại</span>
        </button>
      </div>
    );
  }

  const allAppeals = appeals || [];
  const filtered = allAppeals.filter(a => {
    if (filter !== 'ALL' && a.status !== filter) return false;
    if (search.trim()) {
      const q = search.toLowerCase();
      const matchId = a.id?.toLowerCase().includes(q);
      const matchSubmission = a.submissionId?.toLowerCase().includes(q);
      const matchExam = a.examName?.toLowerCase().includes(q);
      const matchStudent = a.studentName?.toLowerCase().includes(q) || a.studentEmail?.toLowerCase().includes(q);
      return matchId || matchSubmission || matchExam || matchStudent;
    }
    return true;
  });

  const pendingCount = allAppeals.filter(a => a.status === 'PENDING').length;
  const approvedCount = allAppeals.filter(a => a.status === 'APPROVED').length;
  const rejectedCount = allAppeals.filter(a => a.status === 'REJECTED').length;

  return (
    <div className="max-w-7xl mx-auto px-space-lg py-space-lg flex flex-col w-full space-y-space-lg">
      {/* Page Title */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-space-md">
        <div>
          <div className="flex items-center gap-2 mb-1">
            <span className="px-2 py-0.5 rounded-full text-[10px] font-semibold bg-indigo-50 text-indigo-700 border border-indigo-200 uppercase tracking-wider font-mono">
              Hội đồng Khảo thí &amp; Quản trị
            </span>
          </div>
          <h1 className="font-display-lg text-display-lg text-on-surface tracking-tight">
            Quản lý Khiếu nại Điểm số
          </h1>
          <p className="text-xs sm:text-sm text-on-surface-variant mt-0.5">
            Cổng thẩm quyền Admin: Xem xét, đối chiếu bài làm, chấp thuận điều chỉnh điểm hoặc từ chối khiếu nại toàn trường.
          </p>
        </div>

        <button
          onClick={() => refetch()}
          className="self-start md:self-auto px-3.5 py-2 rounded-lg bg-surface-container hover:bg-surface-container-high text-on-surface text-xs font-medium border border-outline-variant/40 transition-colors flex items-center gap-1.5 cursor-pointer"
        >
          <RotateCcw className="w-3.5 h-3.5 text-outline" />
          <span>Làm mới dữ liệu</span>
        </button>
      </div>

      {/* 4 Metric Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-space-md">
        <div className="bg-surface-container-lowest p-space-md rounded-2xl border border-outline-variant/40 shadow-xs flex items-center justify-between">
          <div>
            <span className="font-label-xs uppercase tracking-wider text-outline font-semibold">Tổng số đơn</span>
            <div className="font-display-lg font-bold text-on-surface mt-1">{allAppeals.length}</div>
          </div>
          <div className="p-2.5 rounded-xl bg-slate-100 text-slate-700">
            <Scale className="w-5 h-5" />
          </div>
        </div>

        <div className="bg-surface-container-lowest p-space-md rounded-2xl border border-outline-variant/40 shadow-xs flex items-center justify-between">
          <div>
            <span className="font-label-xs uppercase tracking-wider text-outline font-semibold">Chờ Admin xử lý</span>
            <div className="font-display-lg font-bold text-amber-600 mt-1">{pendingCount}</div>
          </div>
          <div className="p-2.5 rounded-xl bg-amber-50 text-amber-600">
            <Clock className="w-5 h-5" />
          </div>
        </div>

        <div className="bg-surface-container-lowest p-space-md rounded-2xl border border-outline-variant/40 shadow-xs flex items-center justify-between">
          <div>
            <span className="font-label-xs uppercase tracking-wider text-outline font-semibold">Đã chấp thuận</span>
            <div className="font-display-lg font-bold text-emerald-600 mt-1">{approvedCount}</div>
          </div>
          <div className="p-2.5 rounded-xl bg-emerald-50 text-emerald-600">
            <CheckCircle2 className="w-5 h-5" />
          </div>
        </div>

        <div className="bg-surface-container-lowest p-space-md rounded-2xl border border-outline-variant/40 shadow-xs flex items-center justify-between">
          <div>
            <span className="font-label-xs uppercase tracking-wider text-outline font-semibold">Đã từ chối</span>
            <div className="font-display-lg font-bold text-rose-600 mt-1">{rejectedCount}</div>
          </div>
          <div className="p-2.5 rounded-xl bg-rose-50 text-rose-600">
            <XCircle className="w-5 h-5" />
          </div>
        </div>
      </div>

      {/* Filter Tabs & Search */}
      <div className="flex flex-col sm:flex-row items-center justify-between gap-space-md">
        <div className="flex items-center gap-1.5 overflow-x-auto text-xs w-full sm:w-auto">
          {(['ALL', 'PENDING', 'APPROVED', 'REJECTED'] as const).map(tab => (
            <button
              key={tab}
              onClick={() => setFilter(tab)}
              className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition-all cursor-pointer ${
                filter === tab 
                  ? 'bg-primary text-on-primary shadow-xs' 
                  : 'bg-surface-container text-on-surface-variant hover:bg-surface-container-high'
              }`}
            >
              {tab === 'ALL' ? 'Tất cả đơn' : tab === 'PENDING' ? 'Chờ xem xét' : tab === 'APPROVED' ? 'Đã chấp thuận' : 'Đã từ chối'}
            </button>
          ))}
        </div>

        <div className="w-full sm:w-80 relative">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-outline w-4 h-4" />
          <input
            type="text"
            placeholder="Tìm theo sinh viên, email, bài thi, mã..."
            value={search}
            onChange={e => setSearch(e.target.value)}
            className="w-full pl-9 pr-8 py-1.5 rounded-lg border border-outline-variant/40 bg-surface-container-lowest text-on-surface text-xs focus:outline-none focus:border-primary transition-colors"
          />
          {search && (
            <button onClick={() => setSearch('')} className="absolute right-2.5 top-1/2 -translate-y-1/2 text-outline hover:text-on-surface cursor-pointer">
              <X className="w-3.5 h-3.5" />
            </button>
          )}
        </div>
      </div>

      {/* Table */}
      <div className="bg-surface-container-lowest rounded-2xl shadow-xs border border-outline-variant/40 overflow-hidden">
        {filtered.length === 0 ? (
          <div className="p-12 text-center text-outline text-xs">
            Không tìm thấy đơn khiếu nại nào phù hợp với bộ lọc hiện tại.
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse text-xs">
              <thead>
                <tr className="bg-surface-container-low text-on-surface-variant font-label-xs uppercase tracking-wider border-b border-outline-variant/30">
                  <th className="py-2.5 px-4 font-semibold">Mã khiếu nại</th>
                  <th className="py-2.5 px-4 font-semibold">Sinh viên</th>
                  <th className="py-2.5 px-4 font-semibold">Bài thi &amp; Bài nộp</th>
                  <th className="py-2.5 px-4 font-semibold">Điểm số</th>
                  <th className="py-2.5 px-4 font-semibold">Lý do</th>
                  <th className="py-2.5 px-4 font-semibold">Trạng thái</th>
                  <th className="py-2.5 px-4 font-semibold">Ngày gửi</th>
                  <th className="py-2.5 px-4 font-semibold">Ngày xử lý</th>
                  <th className="py-2.5 px-4 font-semibold text-right">Thao tác</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-outline-variant/20 text-on-surface">
                {filtered.map(appeal => (
                  <tr key={appeal.id} className="hover:bg-surface-container-low/60 transition-colors">
                    <td className="py-3 px-4 font-mono font-medium text-primary">#{appeal.id.substring(0, 8)}</td>
                    <td className="py-3 px-4">
                      <div className="font-semibold text-on-surface">{appeal.studentName || 'Sinh viên'}</div>
                      <div className="text-on-surface-variant text-[11px]">{appeal.studentEmail || 'N/A'}</div>
                    </td>
                    <td className="py-3 px-4">
                      <div className="font-medium">{appeal.examName}</div>
                      <div className="text-outline text-[11px] font-mono">#{appeal.submissionId?.substring(0, 8)}</div>
                    </td>
                    <td className="py-3 px-4">
                      {appeal.updatedScore != null ? (
                        <div className="flex items-center gap-1 font-mono">
                          <span className="text-outline line-through text-[11px]">{appeal.originalScore ?? '—'}</span>
                          <span className="text-emerald-700 font-bold text-xs">{appeal.updatedScore}</span>
                        </div>
                      ) : (
                        <span className="font-mono font-bold text-on-surface text-xs">
                          {appeal.currentScore != null ? appeal.currentScore : (appeal.originalScore ?? '—')}
                        </span>
                      )}
                    </td>
                    <td className="py-3 px-4 capitalize text-on-surface-variant">{appeal.basis?.replace(/_/g, ' ')}</td>
                    <td className="py-3 px-4">
                      {appeal.status === 'PENDING' && (
                        <span className="px-2 py-0.5 rounded-full bg-amber-50 text-amber-700 border border-amber-200 font-semibold text-[10px] inline-flex items-center gap-1">
                          <span className="w-1.5 h-1.5 rounded-full bg-amber-500 animate-pulse" />
                          <span>Chờ Admin</span>
                        </span>
                      )}
                      {appeal.status === 'APPROVED' && (
                        <span className="px-2 py-0.5 rounded-full bg-emerald-50 text-emerald-700 border border-emerald-200 font-semibold text-[10px] inline-flex items-center gap-1">
                          <CheckCircle2 className="w-3 h-3 text-emerald-600" />
                          <span>Đã chấp thuận</span>
                        </span>
                      )}
                      {appeal.status === 'REJECTED' && (
                        <span className="px-2 py-0.5 rounded-full bg-rose-50 text-rose-700 border border-rose-200 font-semibold text-[10px] inline-flex items-center gap-1">
                          <XCircle className="w-3 h-3 text-rose-600" />
                          <span>Đã từ chối</span>
                        </span>
                      )}
                    </td>
                    <td className="py-3 px-4 text-on-surface-variant">
                      {new Date(appeal.createdAt).toLocaleDateString()}
                    </td>
                    <td className="py-3 px-4 text-on-surface-variant">
                      {appeal.resolvedAt ? new Date(appeal.resolvedAt).toLocaleDateString() : '—'}
                    </td>
                    <td className="py-3 px-4 text-right">
                      <Link
                        href={`/admin/appeals/${appeal.id}`}
                        className="px-3 py-1 bg-surface-container hover:bg-surface-container-high text-primary rounded-lg text-xs font-medium transition-colors inline-flex items-center gap-1 cursor-pointer"
                      >
                        <span>{appeal.status === 'PENDING' ? 'Xử lý' : 'Chi tiết'}</span>
                        <ExternalLink className="w-3 h-3" />
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
