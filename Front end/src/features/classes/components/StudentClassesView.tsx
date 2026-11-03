'use client';

import React, { useState, useMemo } from 'react';
import { useStudentClasses } from '../hooks/use-student-classes';
import { AppLink as Link } from '@/components/ui/app-link';
import { 
  GraduationCap, 
  Search,
  Users, 
  Calendar, 
  ArrowRight, 
  Loader2, 
  AlertCircle, 
  CheckCircle2, 
  FileCode2 
} from 'lucide-react';

export function StudentClassesView() {
  const { data: classes, isLoading, isError, error } = useStudentClasses();
  const [searchQuery, setSearchQuery] = useState('');

  const filteredClasses = useMemo(() => {
    if (!classes) return [];
    if (!searchQuery.trim()) return classes;
    const q = searchQuery.toLowerCase().trim();
    return classes.filter(
      (c) =>
        c.name.toLowerCase().includes(q) ||
        c.code.toLowerCase().includes(q) ||
        (c.semester && c.semester.toLowerCase().includes(q))
    );
  }, [classes, searchQuery]);

  if (isLoading) {
    return (
      <div className="flex flex-col h-72 items-center justify-center gap-3">
        <Loader2 className="w-8 h-8 animate-spin text-primary" />
        <p className="text-on-surface-variant font-label-md text-xs">Đang tải danh sách lớp học của bạn...</p>
      </div>
    );
  }

  if (isError) {
    return (
      <div className="max-w-xl mx-auto my-12 p-8 rounded-2xl bg-surface-container-lowest border border-rose-200 shadow-lg text-center flex flex-col items-center gap-3">
        <div className="p-3 rounded-full bg-rose-50 text-rose-600">
          <AlertCircle className="w-8 h-8" />
        </div>
        <h2 className="font-headline-md text-headline-md text-on-surface">Không thể tải danh sách lớp học</h2>
        <p className="font-body-md text-on-surface-variant text-xs">{(error as Error)?.message || 'Lỗi kết nối máy chủ.'}</p>
        <button
          onClick={() => window.location.reload()}
          className="mt-2 px-4 py-2 bg-primary text-on-primary rounded-lg text-xs font-medium cursor-pointer"
        >
          Thử lại
        </button>
      </div>
    );
  }

  return (
    <div className="max-w-7xl mx-auto px-space-lg py-space-lg flex flex-col gap-space-lg">
      {/* Top Header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-space-md pb-space-sm border-b border-outline-variant/30">
        <div>
          <div className="flex items-center gap-2 text-xs text-on-surface-variant mb-1">
            <span className="font-semibold text-primary">Sinh viên</span>
            <span className="text-outline">/</span>
            <span>Lớp học</span>
          </div>
          <div className="flex items-center gap-3">
            <h1 className="font-headline-lg text-headline-lg text-on-surface tracking-tight">
              Lớp học của tôi
            </h1>
            <span className="px-2.5 py-0.5 rounded-full bg-primary/10 text-primary border border-primary/20 text-xs font-semibold">
              {classes?.length ?? 0} lớp đã tham gia
            </span>
          </div>
        </div>

        {/* Search */}
        <div className="relative w-full md:w-72">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-outline w-4 h-4" />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Tìm theo mã lớp, tên môn..."
            className="w-full pl-9 pr-4 py-1.5 bg-surface-container-low rounded-lg text-xs text-on-surface border border-outline-variant/40 focus:outline-none focus:border-primary transition-all"
          />
        </div>
      </div>

      {/* Classes Grid */}
      {filteredClasses.length === 0 ? (
        <div className="p-12 rounded-2xl bg-surface-container-lowest border border-outline-variant/30 text-center flex flex-col items-center gap-3">
          <div className="p-3.5 rounded-full bg-surface-container text-outline">
            <GraduationCap className="w-8 h-8" />
          </div>
          <h3 className="font-headline-sm text-headline-sm font-semibold text-on-surface">
            {searchQuery ? 'Không tìm thấy lớp học phù hợp' : 'Bạn chưa tham gia lớp học nào'}
          </h3>
          <p className="text-xs text-on-surface-variant max-w-md">
            {searchQuery
              ? 'Vui lòng kiểm tra lại từ khóa tìm kiếm hoặc thử mã lớp khác.'
              : 'Hãy liên hệ với Giảng viên hoặc Quản trị viên để được ghi danh vào lớp học của bạn.'}
          </p>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
          {filteredClasses.map((cls) => (
            <div
              key={cls.id}
              className="p-5 rounded-2xl bg-surface-container-lowest border border-outline-variant/30 shadow-xs hover:border-primary/40 hover:shadow-md transition-all flex flex-col justify-between gap-4"
            >
              <div className="flex flex-col gap-2">
                <div className="flex items-center justify-between gap-2">
                  <span className="px-2.5 py-1 rounded-lg bg-primary/10 text-primary font-mono text-xs font-bold border border-primary/20">
                    {cls.code}
                  </span>
                  {cls.semester && (
                    <span className="text-[11px] font-medium text-outline flex items-center gap-1">
                      <Calendar className="w-3.5 h-3.5" />
                      <span>{cls.semester}</span>
                    </span>
                  )}
                </div>

                <div>
                  <h3 className="font-headline-sm text-headline-sm font-bold text-on-surface group-hover:text-primary transition-colors">
                    {cls.name}
                  </h3>
                </div>

                <div className="flex items-center gap-3 text-xs text-on-surface-variant pt-2 border-t border-outline-variant/20">
                  <span className="flex items-center gap-1.5">
                    <Users className="w-3.5 h-3.5 text-secondary" />
                    <span>{cls.studentCount != null ? `${cls.studentCount} sinh viên` : 'Đang hoạt động'}</span>
                  </span>
                  <span className="text-outline">•</span>
                  <span className="flex items-center gap-1 text-emerald-600 font-medium">
                    <CheckCircle2 className="w-3.5 h-3.5" />
                    <span>Đã ghi danh</span>
                  </span>
                </div>
              </div>

              <div className="pt-2 border-t border-outline-variant/20 flex items-center justify-between">
                <Link
                  href={`/student/exams`}
                  className="inline-flex items-center gap-1 text-xs font-semibold text-primary hover:text-primary/80 transition-colors"
                >
                  <FileCode2 className="w-3.5 h-3.5" />
                  <span>Xem danh sách bài thi</span>
                  <ArrowRight className="w-3 h-3" />
                </Link>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

