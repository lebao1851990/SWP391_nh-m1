'use client';

import React, { useState, useMemo, useEffect } from 'react';
import { useTeacherExams, useTeacherClasses, useCreateExam, useCreateTeacherClass, useDeleteExam } from '../hooks/use-teacher-exams';
import { ExamResponse } from '../api/teacher-exams.api';
import { AppLink as Link } from '@/components/ui/app-link';
import { useTranslation } from '@/i18n';
import { 
  Search, 
  PlusCircle, 
  CheckCircle2, 
  X, 
  ChevronDown, 
  FileCode2, 
  Calendar, 
  Clock, 
  ExternalLink,
  AlertCircle,
  Loader2,
  Users,
  FolderPlus,
  Trash2,
  Sparkles,
  Send
} from 'lucide-react';
import { toast } from 'sonner';
import { AiExamGeneratorModal } from './AiExamGeneratorModal';
import { usePublishExam } from '../hooks/use-teacher-exams';

export function TeacherExamsView() {
  const { t } = useTranslation();
  const { data: exams, isLoading, isError, error } = useTeacherExams();
  const { data: classes, refetch: refetchClasses } = useTeacherClasses();
  const createExam = useCreateExam();
  const createTeacherClass = useCreateTeacherClass();
  const deleteExamMutation = useDeleteExam();
  const publishMutation = usePublishExam();

  const [examToDelete, setExamToDelete] = useState<ExamResponse | null>(null);
  const [deleteError, setDeleteError] = useState('');

  const [searchQuery, setSearchQuery] = useState(() => {
    if (typeof window !== 'undefined') {
      return new URLSearchParams(window.location.search).get('search') || '';
    }
    return '';
  });
  const [classFilter, setClassFilter] = useState('all');

  // Modal State
  const [isAiModalOpen, setIsAiModalOpen] = useState(false);
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(() => {
    if (typeof window !== 'undefined') {
      return new URLSearchParams(window.location.search).get('action') === 'create';
    }
    return false;
  });
  const [examTitle, setExamTitle] = useState('');
  const [selectedClassIds, setSelectedClassIds] = useState<string[]>([]);
  const [examDate, setExamDate] = useState('');
  const [durationMinutes, setDurationMinutes] = useState(90);
  const [formError, setFormError] = useState('');
  const [successMessage, setSuccessMessage] = useState('');

  // Quick class creation state
  const [showQuickCreateClass, setShowQuickCreateClass] = useState(false);
  const [newClassCode, setNewClassCode] = useState('');
  const [newClassName, setNewClassName] = useState('');
  const [isCreatingClass, setIsCreatingClass] = useState(false);

  const filteredExams = useMemo(() => {
    if (!exams) return [];
    return exams.filter(exam => {
      if (classFilter !== 'all') {
        const hasClass = (exam.classCodes && exam.classCodes.includes(classFilter)) || exam.courseCode === classFilter;
        if (!hasClass) return false;
      }
      if (searchQuery) {
        const query = searchQuery.toLowerCase();
        const matchesClassCodes = exam.classCodes?.some(c => c.toLowerCase().includes(query));
        return (
          exam.title?.toLowerCase().includes(query) ||
          exam.courseCode?.toLowerCase().includes(query) ||
          matchesClassCodes ||
          exam.id?.toLowerCase().includes(query)
        );
      }
      return true;
    });
  }, [exams, searchQuery, classFilter]);

  const uniqueClasses = useMemo(() => {
    if (!exams) return [];
    const set = new Set<string>();
    exams.forEach(e => {
      if (e.classCodes && e.classCodes.length > 0) {
        e.classCodes.forEach(code => set.add(code));
      } else if (e.courseCode) {
        set.add(e.courseCode);
      }
    });
    return Array.from(set).filter(Boolean);
  }, [exams]);

  const stats = useMemo(() => {
    if (!exams) return { published: 0, drafts: 0, total: 0 };
    return {
      published: exams.filter(e => e.status !== 'draft').length,
      drafts: exams.filter(e => e.status === 'draft').length,
      total: exams.length,
    };
  }, [exams]);

  const handleOpenCreateModal = () => {
    setExamTitle('');
    setSelectedClassIds(classes && classes.length > 0 ? [classes[0].id] : []);
    const now = new Date();
    now.setMinutes(now.getMinutes() - now.getTimezoneOffset());
    setExamDate(now.toISOString().slice(0, 16));
    setDurationMinutes(90);
    setFormError('');
    setNewClassCode('');
    setNewClassName('');
    setShowQuickCreateClass(!classes || classes.length === 0);
    setIsCreateModalOpen(true);
  };

  const handleToggleClass = (classId: string) => {
    setSelectedClassIds(prev => 
      prev.includes(classId) 
        ? prev.filter(id => id !== classId)
        : [...prev, classId]
    );
  };

  const handleQuickAddClass = async () => {
    if (!newClassCode.trim()) {
      setFormError('Vui lòng nhập mã lớp học (ví dụ: PRJ301).');
      return;
    }
    const name = newClassName.trim() || newClassCode.trim();
    setIsCreatingClass(true);
    setFormError('');
    try {
      const created = await createTeacherClass.mutateAsync({
        code: newClassCode.trim(),
        name,
        semester: 'Fall 2026',
      });
      toast.success(`Đã tạo lớp học ${created.code} thành công!`);
      setSelectedClassIds(prev => [...prev, created.id]);
      setNewClassCode('');
      setNewClassName('');
      setShowQuickCreateClass(false);
      refetchClasses();
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } }; message?: string })?.response?.data?.message || (err as { message?: string })?.message || 'Không thể tạo lớp học';
      setFormError(msg);
      toast.error(msg);
    } finally {
      setIsCreatingClass(false);
    }
  };

  const handleCreateSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!examTitle.trim()) {
      setFormError('Tiêu đề bài thi không được để trống.');
      return;
    }
    if (selectedClassIds.length === 0 && !newClassCode.trim()) {
      setFormError('Vui lòng chọn hoặc nhập ít nhất một lớp học cho bài thi.');
      return;
    }
    if (!examDate) {
      setFormError('Vui lòng chọn ngày và giờ bắt đầu thi.');
      return;
    }
    if (durationMinutes <= 0) {
      setFormError('Thời lượng thi phải lớn hơn 0 phút.');
      return;
    }

    setFormError('');
    createExam.mutate(
      {
        title: examTitle.trim(),
        classId: selectedClassIds[0],
        classIds: selectedClassIds.length > 0 ? selectedClassIds : undefined,
        newClassCode: newClassCode.trim() ? newClassCode.trim() : undefined,
        newClassName: newClassName.trim() ? newClassName.trim() : undefined,
        semester: 'Fall 2026',
        date: new Date(examDate).toISOString(),
        durationMinutes: Number(durationMinutes),
      },
      {
        onSuccess: (newExam) => {
          setIsCreateModalOpen(false);
          const msg = `Tạo bài thi "${newExam.title}" thành công!`;
          setSuccessMessage(msg);
          toast.success(msg);
          setTimeout(() => setSuccessMessage(''), 5000);
          refetchClasses();
        },
        onError: (err: unknown) => {
          const msg = (err as { response?: { data?: { message?: string } }; message?: string })?.response?.data?.message || (err as { message?: string })?.message || 'Đã xảy ra lỗi khi tạo bài thi. Vui lòng thử lại.';
          setFormError(msg);
          toast.error(msg);
        },
      }
    );
  };

  const handlePublish = (id: string, title: string) => {
    publishMutation.mutate(id, {
      onSuccess: () => {
        toast.success(`Đã phát hành bài thi "${title}" thành công! Sinh viên thuộc lớp đã có thể xem và làm bài.`);
      },
      onError: (err: unknown) => {
        const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message || 'Không thể phát hành bài thi.';
        toast.error(msg);
      },
    });
  };

  const handleOpenDeleteModal = (exam: ExamResponse) => {
    setExamToDelete(exam);
    setDeleteError('');
  };

  const handleConfirmDelete = async () => {
    if (!examToDelete) return;
    setDeleteError('');
    try {
      await deleteExamMutation.mutateAsync(examToDelete.id);
      toast.success(t('teacherExams.deleteSuccess'));
      setExamToDelete(null);
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } }; message?: string })?.response?.data?.message || (err as { message?: string })?.message || t('teacherExams.deleteError');
      setDeleteError(msg);
      toast.error(msg);
    }
  };

  if (isLoading) {
    return (
      <div className="flex flex-col h-72 items-center justify-center gap-3">
        <div className="w-8 h-8 rounded-full border-2 border-primary border-t-transparent animate-spin" />
        <p className="text-on-surface-variant font-label-md text-xs">Đang tải danh sách bài thi...</p>
      </div>
    );
  }

  if (isError) {
    return (
      <div className="max-w-xl mx-auto my-12 p-6 rounded-2xl bg-surface-container-lowest border border-rose-200/60 shadow-lg flex flex-col items-center text-center gap-3">
        <div className="p-3 rounded-full bg-rose-50 text-rose-600">
          <AlertCircle className="w-8 h-8" />
        </div>
        <h2 className="font-headline-md text-headline-md text-on-surface">Lỗi tải dữ liệu bài thi</h2>
        <p className="font-body-md text-on-surface-variant text-xs">{(error as Error).message || 'Đã xảy ra lỗi. Vui lòng thử lại.'}</p>
        <button 
          onClick={() => window.location.reload()}
          className="mt-2 px-4 py-2 bg-primary text-on-primary rounded-lg text-xs font-medium hover:bg-primary/90 transition-all cursor-pointer"
        >
          Thử kết nối lại
        </button>
      </div>
    );
  }

  return (
    <div className="w-full max-w-7xl mx-auto px-4 sm:px-space-lg py-space-lg min-w-0">
      <div className="flex flex-col w-full min-w-0">
        {/* Success Alert */}
        {successMessage && (
          <div className="mb-4 p-3.5 rounded-xl bg-emerald-50 border border-emerald-200 text-emerald-800 flex items-center justify-between text-xs animate-in fade-in">
            <div className="flex items-center gap-2">
              <CheckCircle2 className="w-4 h-4 text-emerald-600 shrink-0" />
              <span className="font-semibold">{successMessage}</span>
            </div>
            <button onClick={() => setSuccessMessage('')} className="text-emerald-700 hover:text-emerald-900 cursor-pointer">
              <X className="w-3.5 h-3.5" />
            </button>
          </div>
        )}

        {/* Page Header */}
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-space-md mb-space-lg">
          <div className="flex flex-col">
            <div className="flex items-center gap-space-sm">
              <h1 className="font-headline-lg text-headline-lg text-on-surface tracking-tight">Quản lý bài thi</h1>
              <span className="font-label-xs text-xs px-2 py-0.5 rounded bg-surface-container border border-outline-variant/30 text-on-surface-variant font-medium">Học kỳ Fall 2026</span>
            </div>
            <p className="font-body-md text-body-md text-on-surface-variant mt-0.5 text-xs sm:text-sm">
              Tạo, cấu hình và giám sát các bài thi lập trình và phiên quét đạo văn cây AST của bạn.
            </p>
          </div>
          <div className="flex items-center gap-2 self-start md:self-auto">
            <button
              onClick={() => setIsAiModalOpen(true)}
              className="inline-flex items-center gap-2 px-3.5 py-2 bg-purple-600 text-white rounded-lg font-label-md text-xs hover:bg-purple-700 transition-colors shadow-sm cursor-pointer font-medium"
              id="btn-ai-generate-exam"
            >
              <Sparkles className="w-4 h-4 animate-pulse" />
              <span>Sinh đề bằng AI</span>
            </button>
            <button
              onClick={handleOpenCreateModal}
              className="inline-flex items-center gap-2 px-4 py-2 bg-primary text-on-primary rounded-lg font-label-md text-xs hover:bg-primary/90 transition-colors shadow-sm cursor-pointer font-medium"
              id="btn-create-exam"
            >
              <PlusCircle className="w-4 h-4" />
              <span>+ Tạo thủ công</span>
            </button>
          </div>
        </div>

        {/* Filter & Search Bar */}
        <div className="bg-surface-container-lowest rounded-xl p-3 mb-space-md border border-outline-variant/40 shadow-xs flex flex-col xl:flex-row items-stretch xl:items-center justify-between gap-3">
          <div className="flex flex-wrap items-center gap-2 flex-1 min-w-0">
            <div className="relative min-w-[240px] flex-1 max-w-sm">
              <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 text-outline w-4 h-4" />
              <input 
                className="w-full pl-8 pr-3 py-1.5 bg-surface-container-low rounded-lg text-on-surface font-body-sm text-xs border border-outline-variant/40 focus:outline-none focus:border-primary transition-all" 
                placeholder="Tìm kiếm bài thi theo tiêu đề, mã lớp hoặc ID..." 
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

            <div className="relative">
              <select 
                className="appearance-none bg-surface-container-low text-on-surface font-label-md text-xs pl-3 pr-8 py-1.5 rounded-lg border border-outline-variant/40 focus:outline-none cursor-pointer"
                value={classFilter}
                onChange={(e) => setClassFilter(e.target.value)}
              >
                <option value="all">Tất cả các lớp học</option>
                {uniqueClasses.map(c => <option key={c} value={c}>{c}</option>)}
              </select>
              <ChevronDown className="absolute right-2 top-1/2 -translate-y-1/2 text-outline w-3.5 h-3.5 pointer-events-none" />
            </div>
          </div>
        </div>

        {/* Main Table Section */}
        <div className="bg-surface-container-lowest rounded-xl border border-outline-variant/40 shadow-xs overflow-hidden flex flex-col mb-space-lg w-full min-w-0" id="tableContainer">
          <div className="overflow-x-auto w-full">
            <table className="w-full text-left border-collapse min-w-[900px] text-xs" id="teacher-exams-table">
              <thead>
                <tr className="bg-surface-container-low text-on-surface-variant font-label-xs uppercase tracking-wider border-b border-outline-variant/30">
                  <th className="py-2.5 px-4 font-semibold">Tiêu đề bài thi</th>
                  <th className="py-2.5 px-4 font-semibold">Lớp học</th>
                  <th className="py-2.5 px-4 font-semibold">Thời gian thi</th>
                  <th className="py-2.5 px-4 font-semibold">Thời lượng</th>
                  <th className="py-2.5 px-4 font-semibold">Trạng thái</th>
                  <th className="py-2.5 px-4 text-right font-semibold">Thao tác</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-outline-variant/20 text-on-surface">
                {filteredExams.length === 0 ? (
                  <tr>
                    <td colSpan={6} className="py-12 text-center text-outline font-label-md">
                      <div className="flex flex-col items-center justify-center gap-2">
                        <FileCode2 className="w-8 h-8 text-outline" />
                        <p className="font-headline-sm text-on-surface text-sm">Không tìm thấy bài thi nào</p>
                        <p className="text-outline text-xs">
                          {searchQuery || classFilter !== 'all'
                            ? 'Không có bài thi nào khớp với từ khóa tìm kiếm hoặc bộ lọc lớp học.'
                            : 'Bạn chưa tạo bài thi nào. Nhấn "+ Tạo bài thi mới" để bắt đầu.'}
                        </p>
                      </div>
                    </td>
                  </tr>
                ) : (
                  filteredExams.map(exam => (
                    <tr key={exam.id} className="hover:bg-surface-container-low/60 transition-colors">
                      <td className="py-3 px-4">
                        <Link href={`/teacher/exams/${exam.id}`} className="font-semibold text-on-surface hover:text-primary transition-colors">
                          {exam.title}
                        </Link>
                      </td>
                      <td className="py-3 px-4">
                        <div className="flex flex-wrap gap-1">
                          {exam.classCodes && exam.classCodes.length > 0 ? (
                            exam.classCodes.map(code => (
                              <span key={code} className="font-mono px-1.5 py-0.5 rounded bg-surface-container text-[11px] font-semibold text-on-surface-variant">
                                {code}
                              </span>
                            ))
                          ) : (
                            <span className="font-mono px-2 py-0.5 rounded bg-surface-container text-[11px] font-semibold text-on-surface-variant">
                              {exam.courseCode || 'SE-GENERAL'}
                            </span>
                          )}
                        </div>
                      </td>
                      <td className="py-3 px-4 text-on-surface-variant">
                        {exam.examDate ? new Date(exam.examDate).toLocaleString('vi-VN') : 'Linh hoạt'}
                      </td>
                      <td className="py-3 px-4 text-on-surface-variant">
                        {exam.duration || 90} phút
                      </td>
                      <td className="py-3 px-4">
                        <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-semibold border ${
                          exam.status === 'draft'
                            ? 'bg-amber-50 text-amber-700 border-amber-200'
                            : 'bg-emerald-50 text-emerald-700 border-emerald-200'
                        }`}>
                          {exam.status === 'draft' ? 'Bản nháp' : 'Đang hoạt động'}
                        </span>
                      </td>
                      <td className="py-3 px-4 text-right">
                        <div className="flex items-center justify-end gap-1.5">
                          {exam.status === 'draft' && (
                            <button
                              type="button"
                              onClick={() => handlePublish(exam.id, exam.title)}
                              disabled={publishMutation.isPending}
                              className="inline-flex items-center gap-1 px-2.5 py-1 bg-emerald-600 hover:bg-emerald-700 text-white rounded-lg text-xs font-medium transition-colors shadow-xs cursor-pointer disabled:opacity-50"
                              title="Phát hành bài thi cho sinh viên làm bài"
                              id={`btn-publish-${exam.id}`}
                            >
                              <Send className="w-3 h-3" />
                              <span>Phát hành</span>
                            </button>
                          )}
                          <Link 
                            href={`/teacher/exams/${exam.id}`} 
                            className="inline-flex items-center gap-1 px-3 py-1 bg-surface-container hover:bg-surface-container-high text-primary rounded-lg font-medium transition-colors"
                          >
                            <span>{t('teacherExams.manage')}</span>
                            <ExternalLink className="w-3 h-3" />
                          </Link>
                          <button
                            type="button"
                            onClick={() => handleOpenDeleteModal(exam)}
                            className="inline-flex items-center justify-center p-1.5 text-rose-600 hover:text-rose-700 hover:bg-rose-50 rounded-lg transition-colors cursor-pointer border border-transparent hover:border-rose-200"
                            title={t('teacherExams.deleteButton')}
                            aria-label={t('teacherExams.deleteButton')}
                          >
                            <Trash2 className="w-3.5 h-3.5" />
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
          <div className="px-4 py-2.5 bg-surface-container-low/40 border-t border-outline-variant/30 flex flex-col sm:flex-row items-center justify-between gap-3 text-xs text-outline">
            <span>
              Hiển thị <strong className="text-on-surface">{filteredExams.length}</strong> trên tổng số <strong className="text-on-surface">{stats.total}</strong> bài thi
            </span>
          </div>
        </div>

        {/* Create Exam Modal */}
        {isCreateModalOpen && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-xs p-4 animate-in fade-in duration-150">
            <div className="bg-surface-container-lowest rounded-2xl max-w-lg w-full p-6 shadow-2xl border border-outline-variant/40 flex flex-col gap-4 animate-in zoom-in-95 duration-150">
              <div className="flex items-center justify-between border-b border-outline-variant/30 pb-3">
                <div className="flex items-center gap-2">
                  <PlusCircle className="w-5 h-5 text-primary" />
                  <h2 className="font-headline-sm text-headline-sm text-on-surface font-semibold">Tạo bài thi mới</h2>
                </div>
                <button
                  type="button"
                  onClick={() => setIsCreateModalOpen(false)}
                  className="text-outline hover:text-on-surface transition-colors p-1 rounded-lg cursor-pointer"
                >
                  <X className="w-4 h-4" />
                </button>
              </div>

              <form onSubmit={handleCreateSubmit} className="flex flex-col gap-4">
                {formError && (
                  <div className="p-3 rounded-lg bg-rose-50 border border-rose-200 text-rose-700 flex items-center gap-2 text-xs">
                    <AlertCircle className="w-4 h-4 shrink-0" />
                    <span>{formError}</span>
                  </div>
                )}

                <div className="flex flex-col gap-1">
                  <label className="text-xs font-semibold text-on-surface">Tiêu đề bài thi *</label>
                  <input
                    type="text"
                    required
                    placeholder="Ví dụ: Thi thực hành giữa kỳ Lập trình Java CS201"
                    value={examTitle}
                    onChange={(e) => setExamTitle(e.target.value)}
                    className="w-full px-3 py-2 bg-surface-container-low border border-outline-variant/40 rounded-lg text-xs text-on-surface focus:outline-none focus:border-primary transition-colors"
                  />
                </div>

                <div className="flex flex-col gap-1.5">
                  <div className="flex items-center justify-between">
                    <label className="text-xs font-semibold text-on-surface">
                      Lớp học áp dụng ({selectedClassIds.length} lớp đã chọn) *
                    </label>
                    <div className="flex items-center gap-2 text-[11px]">
                      {classes && classes.length > 1 && (
                        <>
                          <button
                            type="button"
                            onClick={() => setSelectedClassIds(classes.map(c => c.id))}
                            className="text-primary hover:underline font-medium cursor-pointer"
                          >
                            Chọn tất cả
                          </button>
                          <span className="text-outline">|</span>
                          <button
                            type="button"
                            onClick={() => setSelectedClassIds([])}
                            className="text-outline hover:underline cursor-pointer"
                          >
                            Bỏ chọn
                          </button>
                          <span className="text-outline">|</span>
                        </>
                      )}
                      <button
                        type="button"
                        onClick={() => setShowQuickCreateClass(!showQuickCreateClass)}
                        className="text-primary hover:underline font-medium cursor-pointer flex items-center gap-1"
                      >
                        <FolderPlus className="w-3 h-3" />
                        <span>{showQuickCreateClass ? 'Ẩn tạo lớp' : '+ Tạo lớp mới'}</span>
                      </button>
                    </div>
                  </div>

                  {/* Quick Class Creation Panel */}
                  {showQuickCreateClass && (
                    <div className="p-3 rounded-lg bg-surface-container border border-primary/30 flex flex-col gap-2 text-xs animate-in fade-in">
                      <span className="font-semibold text-on-surface text-[11px] flex items-center gap-1 text-primary">
                        <FolderPlus className="w-3.5 h-3.5" /> Thêm lớp học cho giảng viên
                      </span>
                      <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                        <input
                          type="text"
                          placeholder="Mã lớp * (vd: PRJ301)"
                          value={newClassCode}
                          onChange={(e) => setNewClassCode(e.target.value)}
                          className="px-2.5 py-1.5 bg-surface-container-lowest border border-outline-variant/40 rounded text-xs text-on-surface focus:outline-none focus:border-primary uppercase font-mono"
                        />
                        <input
                          type="text"
                          placeholder="Tên lớp (vd: Lập trình Java)"
                          value={newClassName}
                          onChange={(e) => setNewClassName(e.target.value)}
                          className="px-2.5 py-1.5 bg-surface-container-lowest border border-outline-variant/40 rounded text-xs text-on-surface focus:outline-none focus:border-primary"
                        />
                      </div>
                      <div className="flex justify-end">
                        <button
                          type="button"
                          onClick={handleQuickAddClass}
                          disabled={isCreatingClass || !newClassCode.trim()}
                          className="px-3 py-1 bg-primary text-on-primary rounded text-xs font-medium hover:bg-primary/90 disabled:opacity-50 transition-colors cursor-pointer"
                        >
                          {isCreatingClass ? 'Đang tạo lớp...' : 'Lưu & Chọn lớp này'}
                        </button>
                      </div>
                    </div>
                  )}

                  <div className="max-h-44 overflow-y-auto p-2 bg-surface-container-low border border-outline-variant/40 rounded-lg flex flex-col gap-1.5">
                    {classes && classes.length > 0 ? (
                      classes.map((cls) => {
                        const isChecked = selectedClassIds.includes(cls.id);
                        return (
                          <label
                            key={cls.id}
                            className={`flex items-center justify-between p-2 rounded-md border text-xs cursor-pointer transition-colors ${
                              isChecked 
                                ? 'bg-primary/5 border-primary/40 text-on-surface font-medium' 
                                : 'bg-surface-container-lowest border-outline-variant/20 hover:bg-surface-container-high/40 text-on-surface-variant'
                            }`}
                          >
                            <div className="flex items-center gap-2.5">
                              <input
                                type="checkbox"
                                checked={isChecked}
                                onChange={() => handleToggleClass(cls.id)}
                                className="w-3.5 h-3.5 rounded text-primary focus:ring-primary cursor-pointer"
                              />
                              <span className="font-mono font-semibold">{cls.code}</span>
                              <span className="text-outline">—</span>
                              <span className="truncate max-w-[220px]">{cls.name}</span>
                            </div>
                            {cls.studentCount !== undefined && (
                              <span className="text-[11px] text-outline shrink-0">
                                {cls.studentCount} sinh viên
                              </span>
                            )}
                          </label>
                        );
                      })
                    ) : (
                      <div className="py-3 px-2 text-center text-outline flex flex-col items-center gap-1">
                        <p className="text-xs">Chưa có lớp học nào được phân công.</p>
                        <p className="text-[11px] text-primary">Hãy nhập mã lớp ở mục &quot;+ Tạo lớp mới&quot; phía trên để tiếp tục tạo bài thi.</p>
                      </div>
                    )}
                  </div>
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                  <div className="flex flex-col gap-1">
                    <label className="text-xs font-semibold text-on-surface">Thời gian bắt đầu *</label>
                    <input
                      type="datetime-local"
                      required
                      value={examDate}
                      onChange={(e) => setExamDate(e.target.value)}
                      className="w-full px-3 py-2 bg-surface-container-low border border-outline-variant/40 rounded-lg text-xs text-on-surface focus:outline-none focus:border-primary transition-colors"
                    />
                  </div>

                  <div className="flex flex-col gap-1.5">
                    <div className="flex items-center justify-between">
                      <label className="text-xs font-semibold text-on-surface">Thời lượng (Phút) *</label>
                      <span className="text-[11px] text-outline">Đơn vị: phút</span>
                    </div>
                    <input
                      type="number"
                      required
                      min={1}
                      max={1440}
                      value={durationMinutes}
                      onChange={(e) => setDurationMinutes(Number(e.target.value))}
                      className="w-full px-3 py-2 bg-surface-container-low border border-outline-variant/40 rounded-lg text-xs text-on-surface focus:outline-none focus:border-primary transition-colors font-mono"
                      placeholder="Ví dụ: 90"
                    />
                    <div className="flex items-center gap-1.5 flex-wrap pt-0.5">
                      {[45, 60, 90, 120, 180].map((mins) => (
                        <button
                          key={mins}
                          type="button"
                          onClick={() => setDurationMinutes(mins)}
                          className={`px-2 py-0.5 rounded text-[11px] font-medium transition-colors cursor-pointer border ${
                            durationMinutes === mins
                              ? 'bg-primary text-on-primary border-primary'
                              : 'bg-surface-container hover:bg-surface-container-high text-on-surface-variant border-outline-variant/40'
                          }`}
                        >
                          {mins} phút {mins === 60 ? '(1h)' : mins === 90 ? '(1.5h)' : mins === 120 ? '(2h)' : ''}
                        </button>
                      ))}
                    </div>
                  </div>
                </div>

                <div className="flex justify-end gap-3 mt-2 pt-3 border-t border-outline-variant/30">
                  <button
                    type="button"
                    onClick={() => setIsCreateModalOpen(false)}
                    disabled={createExam.isPending}
                    className="px-4 py-2 text-xs rounded-lg border border-outline-variant/40 hover:bg-surface-container transition-colors cursor-pointer"
                  >
                    Hủy
                  </button>
                  <button
                    type="submit"
                    id="submit-create-exam-btn"
                    disabled={createExam.isPending}
                    className="inline-flex items-center gap-2 px-5 py-2 bg-primary text-on-primary rounded-lg text-xs font-medium hover:bg-primary/90 transition-colors disabled:opacity-50 cursor-pointer shadow-sm"
                  >
                    {createExam.isPending ? (
                      <>
                        <Loader2 className="w-3.5 h-3.5 animate-spin" />
                        <span>Đang lưu bài thi...</span>
                      </>
                    ) : (
                      <>
                        <CheckCircle2 className="w-3.5 h-3.5" />
                        <span>Lưu &amp; Xuất bản</span>
                      </>
                    )}
                  </button>
                </div>
              </form>
            </div>
          </div>
        )}

        {/* Delete Exam Confirmation Modal */}
        {examToDelete && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-xs p-4 animate-in fade-in duration-150">
            <div className="bg-surface-container-lowest rounded-2xl max-w-md w-full p-6 shadow-2xl border border-rose-200/60 flex flex-col gap-4 animate-in zoom-in-95 duration-150">
              <div className="flex items-center gap-3">
                <div className="p-2.5 rounded-full bg-rose-50 text-rose-600 shrink-0">
                  <Trash2 className="w-5 h-5" />
                </div>
                <div>
                  <h3 className="font-headline-sm text-headline-sm font-semibold text-on-surface">
                    {t('teacherExams.deleteModalTitle')}
                  </h3>
                  <p className="text-xs text-on-surface-variant font-medium truncate max-w-[280px]">
                    {examToDelete.title}
                  </p>
                </div>
              </div>

              <div className="p-3 rounded-lg bg-surface-container-low border border-outline-variant/30 text-xs text-on-surface-variant space-y-2">
                <p>
                  {t('teacherExams.deleteModalWarning').replace('{title}', examToDelete.title)}
                </p>
                <p className="text-[11px] text-outline italic">
                  {t('teacherExams.deleteModalActiveGradingWarning')}
                </p>
              </div>

              {deleteError && (
                <div className="p-3 rounded-lg bg-rose-50 border border-rose-200 text-rose-700 flex items-center gap-2 text-xs">
                  <AlertCircle className="w-4 h-4 shrink-0" />
                  <span>{deleteError}</span>
                </div>
              )}

              <div className="flex justify-end gap-3 pt-2 border-t border-outline-variant/30">
                <button
                  type="button"
                  onClick={() => setExamToDelete(null)}
                  disabled={deleteExamMutation.isPending}
                  className="px-4 py-2 text-xs rounded-lg border border-outline-variant/40 hover:bg-surface-container transition-colors cursor-pointer"
                >
                  {t('common.cancel')}
                </button>
                <button
                  type="button"
                  id="confirm-delete-exam-btn"
                  onClick={handleConfirmDelete}
                  disabled={deleteExamMutation.isPending}
                  className="inline-flex items-center gap-2 px-5 py-2 bg-rose-600 text-white rounded-lg text-xs font-medium hover:bg-rose-700 transition-colors disabled:opacity-50 cursor-pointer shadow-sm"
                >
                  {deleteExamMutation.isPending ? (
                    <>
                      <Loader2 className="w-3.5 h-3.5 animate-spin" />
                      <span>{t('teacherExams.deleting')}</span>
                    </>
                  ) : (
                    <>
                      <Trash2 className="w-3.5 h-3.5" />
                      <span>{t('common.confirmDelete')}</span>
                    </>
                  )}
                </button>
              </div>
            </div>
          </div>
        )}

        {/* AI Exam Generator Modal */}
        <AiExamGeneratorModal
          isOpen={isAiModalOpen}
          onClose={() => setIsAiModalOpen(false)}
          classes={classes || []}
          onExamCreated={() => {
            refetchClasses();
            toast.success('Đã lưu bài thi sinh bởi AI thành công ở trạng thái bản nháp!');
          }}
        />
      </div>
    </div>
  );
}
