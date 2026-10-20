'use client';

import React, { useState, useMemo } from 'react';
import { useStudentExams } from '../hooks/use-student-exams';
import { useStudentSubmissions } from '../../submissions/hooks';
import { AppLink as Link } from '@/components/ui/app-link';
import { 
  Search, 
  X, 
  ChevronDown, 
  Terminal, 
  FileCode2, 
  Calendar, 
  CheckCircle2, 
  Clock, 
  ExternalLink,
  AlertCircle
} from 'lucide-react';

export function StudentExamsView() {
  const { data: exams, isLoading: isLoadingExams, isError: isErrorExams } = useStudentExams();
  const { data: submissions, isLoading: isLoadingSubmissions } = useStudentSubmissions();

  const [activeTab, setActiveTab] = useState('all');
  const [searchQuery, setSearchQuery] = useState(() => {
    if (typeof window !== 'undefined') {
      return new URLSearchParams(window.location.search).get('search') || '';
    }
    return '';
  });
  const [statusFilter, setStatusFilter] = useState('all');
  const [now] = useState(() => Date.now());
  
  const correlatedExams = useMemo(() => {
    if (!exams) return [];
    return exams.map(exam => {
      const submission = submissions?.find(s => s.examId === exam.id);
      
      const startTimeMs = exam.startTime ? new Date(exam.startTime).getTime() : now;
      const durationMs = (exam.durationMinutes || exam.duration || 60) * 60 * 1000;
      const endTimeMs = exam.endTime ? new Date(exam.endTime).getTime() : (startTimeMs + durationMs);

      let derivedStatus = 'UPCOMING';
      if (submission) {
        derivedStatus = submission.status === 'GRADED' ? 'COMPLETED' : 'SUBMITTED';
      } else if (now > endTimeMs) {
        derivedStatus = 'MISSED';
      } else if (now >= startTimeMs && now <= endTimeMs) {
        derivedStatus = 'ACTIVE';
      } else {
        derivedStatus = 'UPCOMING';
      }
      return { ...exam, submission, derivedStatus };
    });
  }, [exams, submissions, now]);

  const filteredExams = useMemo(() => {
    return correlatedExams.filter(exam => {
      // Tab filter
      if (activeTab === 'upcoming' && exam.derivedStatus !== 'UPCOMING' && exam.derivedStatus !== 'ACTIVE') return false;
      if (activeTab === 'submitted' && exam.derivedStatus !== 'SUBMITTED') return false;
      if (activeTab === 'completed' && exam.derivedStatus !== 'COMPLETED') return false;
      
      // Status filter
      if (statusFilter !== 'all') {
        const sf = statusFilter.toLowerCase();
        if (sf === 'upcoming') {
          if (exam.derivedStatus !== 'UPCOMING' && exam.derivedStatus !== 'ACTIVE') return false;
        } else if (sf === 'active') {
          if (exam.derivedStatus !== 'ACTIVE') return false;
        } else if (exam.derivedStatus.toLowerCase() !== sf) {
          return false;
        }
      }
      
      // Search filter
      if (searchQuery) {
        const query = searchQuery.toLowerCase();
        return exam.title.toLowerCase().includes(query) || (exam.courseClassCode && exam.courseClassCode.toLowerCase().includes(query));
      }
      
      return true;
    });
  }, [correlatedExams, activeTab, statusFilter, searchQuery]);

  const stats = useMemo(() => {
    const upcoming = correlatedExams.filter(e => e.derivedStatus === 'UPCOMING' || e.derivedStatus === 'ACTIVE').length;
    const submitted = correlatedExams.filter(e => e.derivedStatus === 'SUBMITTED').length;
    const completed = correlatedExams.filter(e => e.derivedStatus === 'COMPLETED').length;
    return { all: correlatedExams.length, upcoming, submitted, completed };
  }, [correlatedExams]);

  if (isLoadingExams || isLoadingSubmissions) {
    return (
      <div className="flex flex-col h-72 items-center justify-center gap-3">
        <div className="w-8 h-8 rounded-full border-2 border-primary border-t-transparent animate-spin" />
        <p className="text-on-surface-variant font-label-md text-xs">Đang tải danh sách bài thi...</p>
      </div>
    );
  }

  if (isErrorExams) {
    return (
      <div className="max-w-xl mx-auto my-12 p-6 rounded-2xl bg-surface-container-lowest border border-rose-200/60 shadow-lg flex flex-col items-center text-center gap-3">
        <div className="p-3 rounded-full bg-rose-50 text-rose-600">
          <AlertCircle className="w-8 h-8" />
        </div>
        <h2 className="font-headline-md text-headline-md text-on-surface">Không thể tải danh sách bài thi</h2>
        <p className="font-body-md text-on-surface-variant text-xs">Vui lòng kiểm tra trạng thái lớp học hoặc thử lại.</p>
        <button 
          onClick={() => window.location.reload()}
          className="mt-2 px-4 py-2 bg-primary text-on-primary rounded-lg text-xs font-medium hover:bg-primary/90 transition-all cursor-pointer"
        >
          Thử lại
        </button>
      </div>
    );
  }

  return (
    <div className="w-full max-w-7xl mx-auto px-space-lg py-space-lg">
      <div className="flex flex-col w-full">
        {/* Page Header */}
        <div className="flex flex-col lg:flex-row lg:items-end justify-between gap-space-md mb-space-lg">
          <div className="space-y-1">
            <h1 className="font-display-lg text-display-lg text-on-surface tracking-tight">Bài thi học phần</h1>
            <p className="font-body-md text-body-md text-on-surface-variant max-w-2xl text-xs sm:text-sm">
              Theo dõi các bài kiểm tra lập trình sắp tới, nộp bài làm mã nguồn và xem kết quả đánh giá.
            </p>
          </div>
        </div>

        {/* Card Container */}
        <div className="bg-surface-container-lowest rounded-2xl border border-outline-variant/40 shadow-xs overflow-hidden flex flex-col">
          {/* Tabs */}
          <div className="px-space-md pt-space-md bg-surface-container-low/40 border-b border-outline-variant/30 flex flex-col lg:flex-row lg:items-center justify-between gap-space-md">
            <div className="flex items-center gap-1 overflow-x-auto pb-space-xs lg:pb-0 text-xs">
              <button 
                className={`px-4 py-2.5 rounded-t-lg font-medium transition-all flex items-center gap-2 cursor-pointer ${
                  activeTab === 'all' 
                    ? 'bg-surface-container-lowest text-primary font-semibold border-b-2 border-primary shadow-xs' 
                    : 'text-on-surface-variant hover:text-on-surface hover:bg-surface-container'
                }`}
                onClick={() => setActiveTab('all')}
              >
                <span>Tất cả bài thi</span>
                <span className={`px-1.5 py-0.2 rounded-full font-mono text-[11px] ${
                  activeTab === 'all' ? 'bg-primary-fixed text-on-primary-fixed font-bold' : 'bg-surface-container text-on-surface-variant'
                }`}>
                  {stats.all}
                </span>
              </button>

              <button 
                className={`px-4 py-2.5 rounded-t-lg font-medium transition-all flex items-center gap-2 cursor-pointer ${
                  activeTab === 'upcoming' 
                    ? 'bg-surface-container-lowest text-primary font-semibold border-b-2 border-primary shadow-xs' 
                    : 'text-on-surface-variant hover:text-on-surface hover:bg-surface-container'
                }`}
                onClick={() => setActiveTab('upcoming')}
              >
                <span>Sắp &amp; Đang diễn ra</span>
                <span className={`px-1.5 py-0.2 rounded-full font-mono text-[11px] ${
                  activeTab === 'upcoming' ? 'bg-primary-fixed text-on-primary-fixed font-bold' : 'bg-surface-container text-on-surface-variant'
                }`}>
                  {stats.upcoming}
                </span>
              </button>

              <button 
                className={`px-4 py-2.5 rounded-t-lg font-medium transition-all flex items-center gap-2 cursor-pointer ${
                  activeTab === 'submitted' 
                    ? 'bg-surface-container-lowest text-primary font-semibold border-b-2 border-primary shadow-xs' 
                    : 'text-on-surface-variant hover:text-on-surface hover:bg-surface-container'
                }`}
                onClick={() => setActiveTab('submitted')}
              >
                <span>Đã nộp bài</span>
                <span className={`px-1.5 py-0.2 rounded-full font-mono text-[11px] ${
                  activeTab === 'submitted' ? 'bg-primary-fixed text-on-primary-fixed font-bold' : 'bg-surface-container text-on-surface-variant'
                }`}>
                  {stats.submitted}
                </span>
              </button>

              <button 
                className={`px-4 py-2.5 rounded-t-lg font-medium transition-all flex items-center gap-2 cursor-pointer ${
                  activeTab === 'completed' 
                    ? 'bg-surface-container-lowest text-primary font-semibold border-b-2 border-primary shadow-xs' 
                    : 'text-on-surface-variant hover:text-on-surface hover:bg-surface-container'
                }`}
                onClick={() => setActiveTab('completed')}
              >
                <span>Đã hoàn thành</span>
                <span className={`px-1.5 py-0.2 rounded-full font-mono text-[11px] ${
                  activeTab === 'completed' ? 'bg-primary-fixed text-on-primary-fixed font-bold' : 'bg-surface-container text-on-surface-variant'
                }`}>
                  {stats.completed}
                </span>
              </button>
            </div>
          </div>

          {/* Filter Bar */}
          <div className="p-3 bg-surface-container-low/20 border-b border-outline-variant/30 flex flex-col md:flex-row gap-3 items-center justify-between">
            <div className="relative w-full md:w-80 lg:w-96">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-outline w-4 h-4" />
              <input 
                className="w-full pl-9 pr-8 py-1.5 bg-surface-container-lowest rounded-lg text-xs text-on-surface placeholder:text-outline border border-outline-variant/40 focus:outline-none focus:border-primary transition-all" 
                placeholder="Tìm kiếm bài thi theo tiêu đề hoặc mã môn..." 
                type="text"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
              />
              {searchQuery && (
                <button className="absolute right-2.5 top-1/2 -translate-y-1/2 text-outline hover:text-on-surface cursor-pointer" onClick={() => setSearchQuery('')}>
                  <X className="w-3.5 h-3.5" />
                </button>
              )}
            </div>
            
            <div className="flex items-center flex-wrap gap-2.5 w-full md:w-auto justify-end">
              <div className="relative">
                <select 
                  className="appearance-none bg-surface-container-lowest pl-3 pr-8 py-1.5 rounded-lg text-xs text-on-surface border border-outline-variant/40 focus:outline-none cursor-pointer"
                  value={statusFilter}
                  onChange={(e) => setStatusFilter(e.target.value)}
                >
                  <option value="all">Tất cả trạng thái</option>
                  <option value="upcoming">Sắp &amp; Đang diễn ra</option>
                  <option value="active">Đang diễn ra</option>
                  <option value="submitted">Đã nộp bài</option>
                  <option value="completed">Đã hoàn thành</option>
                  <option value="missed">Quá hạn</option>
                </select>
                <ChevronDown className="absolute right-2 top-1/2 -translate-y-1/2 text-outline w-3.5 h-3.5 pointer-events-none" />
              </div>
            </div>
          </div>

          {/* Assessments Table */}
          <div className="overflow-x-auto w-full">
            <table className="w-full text-left border-collapse text-xs">
              <thead className="bg-surface-container-low text-on-surface-variant font-label-xs uppercase tracking-wider border-b border-outline-variant/30">
                <tr>
                  <th className="py-2.5 px-4 font-semibold">Tên bài thi &amp; Lớp học</th>
                  <th className="py-2.5 px-4 font-semibold">Thời gian thi</th>
                  <th className="py-2.5 px-4 font-semibold">Trạng thái</th>
                  <th className="py-2.5 px-4 font-semibold">Điểm số</th>
                  <th className="py-2.5 px-4 font-semibold text-right">Thao tác</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-outline-variant/20 text-on-surface">
                {filteredExams.length === 0 ? (
                  <tr>
                    <td colSpan={5} className="py-12 text-center text-outline">
                      Chưa có bài thi nào phù hợp với điều kiện tìm kiếm.
                    </td>
                  </tr>
                ) : (
                  filteredExams.map(exam => (
                    <tr key={exam.id} className="hover:bg-surface-container-low/60 transition-colors">
                      <td className="py-3 px-4">
                        <Link href={`/student/exams/${exam.id}`} className="font-semibold text-on-surface hover:text-primary transition-colors">
                          {exam.title}
                        </Link>
                        <div className="text-[11px] font-mono text-on-surface-variant mt-0.5">{exam.courseClassCode || 'SE-PRACTICAL'}</div>
                      </td>
                      <td className="py-3 px-4 text-on-surface-variant">
                        {exam.startTime ? new Date(exam.startTime).toLocaleString('vi-VN') : 'Linh hoạt'}
                      </td>
                      <td className="py-3 px-4">
                        <span className={`px-2 py-0.5 rounded-full text-[10px] font-semibold ${
                          exam.derivedStatus === 'ACTIVE'
                            ? 'bg-emerald-50 text-emerald-700 border border-emerald-200'
                            : exam.derivedStatus === 'UPCOMING' 
                            ? 'bg-blue-50 text-blue-700 border border-blue-200' 
                            : exam.derivedStatus === 'COMPLETED' 
                            ? 'bg-teal-50 text-teal-700 border border-teal-200' 
                            : exam.derivedStatus === 'SUBMITTED' 
                            ? 'bg-amber-50 text-amber-700 border border-amber-200' 
                            : 'bg-rose-50 text-rose-700 border border-rose-200'
                        }`}>
                          {exam.derivedStatus === 'ACTIVE'
                            ? 'Đang diễn ra'
                            : exam.derivedStatus === 'UPCOMING' 
                            ? 'Sắp diễn ra' 
                            : exam.derivedStatus === 'COMPLETED' 
                            ? 'Đã hoàn thành' 
                            : exam.derivedStatus === 'SUBMITTED' 
                            ? 'Đã nộp bài' 
                            : 'Quá hạn'}
                        </span>
                      </td>
                      <td className="py-3 px-4">
                        {exam.submission ? (
                          <span className="font-mono bg-surface-container px-2 py-0.5 rounded font-bold text-xs text-on-surface">
                            {exam.submission.totalScore ?? 0} <span className="text-outline text-[10px]">/ 100</span>
                          </span>
                        ) : (
                          <span className="text-outline text-xs">--</span>
                        )}
                      </td>
                      <td className="py-3 px-4 text-right">
                        <div className="flex items-center justify-end gap-2">
                          {exam.submission && (
                            <Link 
                              href={`/student/submissions/${exam.submission.id}`} 
                              className="px-2.5 py-1 bg-emerald-50 hover:bg-emerald-100 text-emerald-800 rounded-lg text-xs font-medium transition-colors border border-emerald-200"
                            >
                              Kết quả
                            </Link>
                          )}
                          <Link 
                            href={`/student/exams/${exam.id}`} 
                            className={`inline-flex items-center gap-1 px-3 py-1 rounded-lg text-xs font-medium transition-colors shadow-xs ${
                              exam.derivedStatus === 'MISSED'
                                ? 'bg-surface-container text-on-surface-variant hover:bg-surface-container-high'
                                : 'bg-primary text-on-primary hover:bg-primary/90'
                            }`}
                          >
                            <span>{exam.derivedStatus === 'MISSED' ? 'Chi tiết' : (exam.derivedStatus === 'ACTIVE' ? 'Vào thi ngay' : 'Vào thi')}</span>
                            <ExternalLink className="w-3 h-3" />
                          </Link>
                        </div>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>

          <div className="px-4 py-2.5 bg-surface-container-low/40 border-t border-outline-variant/30 flex items-center justify-between text-xs text-outline">
            <span>
              Hiển thị <strong className="text-on-surface">{filteredExams.length}</strong> trên tổng số <strong className="text-on-surface">{correlatedExams.length}</strong> bài thi
            </span>
          </div>
        </div>
      </div>
    </div>
  );
}
