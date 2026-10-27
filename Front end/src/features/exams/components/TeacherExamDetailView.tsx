'use client';

import React, { useState, useMemo } from 'react';
import { useRouter } from 'next/navigation';
import { 
  useTeacherExamDetail, 
  useTeacherExamSubmissions, 
  useUpdateExam, 
  useDeleteExam,
  useTeacherClasses,
  useTeacherExamTestCases,
  useCreateTestCase,
  useUpdateTestCase,
  useDeleteTestCase,
  usePublishExam
} from '../hooks/use-teacher-exams';
import { TestCaseResponse } from '../api/teacher-exams.api';
import { AxiosError } from 'axios';
import { AppLink as Link } from '@/components/ui/app-link';
import { useTranslation } from '@/i18n';
import { 
  ArrowLeft, 
  Search, 
  Edit3, 
  X, 
  ChevronDown, 
  CheckCircle2, 
  Loader2, 
  AlertCircle, 
  Calendar, 
  Clock, 
  BookOpen, 
  ExternalLink,
  Code2, 
  Users,
  Plus,
  Trash2,
  Lock,
  FileCheck,
  Eye,
  EyeOff,
  ShieldAlert,
  FileArchive,
  AlertTriangle,
  FileText,
  Download,
  MessageSquare,
  Send
} from 'lucide-react';
import { toast } from 'sonner';

interface TeacherExamDetailViewProps {
  examId?: string;
}

export function TeacherExamDetailView({ examId }: TeacherExamDetailViewProps) {
  const router = useRouter();
  const { t } = useTranslation();
  const { data: exam, isLoading: isLoadingExam, isError: isErrorExam, error: examError } = useTeacherExamDetail(examId || '');
  const { data: submissions, isLoading: isLoadingSubmissions } = useTeacherExamSubmissions(examId || '');
  const { data: classes } = useTeacherClasses();
  const updateExam = useUpdateExam();
  const deleteExamMutation = useDeleteExam();
  const publishMutation = usePublishExam();

  const [isDeleteModalOpen, setIsDeleteModalOpen] = useState(false);
  const [deleteError, setDeleteError] = useState('');

  const handleDeleteExam = async () => {
    if (!exam) return;
    setDeleteError('');
    try {
      await deleteExamMutation.mutateAsync(exam.id);
      toast.success(t('teacherExams.deleteSuccess'));
      router.push('/teacher/exams');
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } }; message?: string })?.response?.data?.message || (err as { message?: string })?.message || t('teacherExams.deleteError');
      setDeleteError(msg);
      toast.error(msg);
    }
  };

  const handlePublishExam = () => {
    if (!exam) return;
    publishMutation.mutate(exam.id, {
      onSuccess: () => {
        toast.success(`Đã phát hành bài thi "${exam.title}" thành công! Sinh viên thuộc lớp đã có thể xem và làm bài.`);
      },
      onError: (err: unknown) => {
        const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message || 'Không thể phát hành bài thi.';
        toast.error(msg);
      },
    });
  };

  // Test Case Queries & Mutations
  const { data: testCases, isLoading: isLoadingTestCases } = useTeacherExamTestCases(examId || '');
  const createTestCaseMutation = useCreateTestCase();
  const updateTestCaseMutation = useUpdateTestCase();
  const deleteTestCaseMutation = useDeleteTestCase();

  // Main Tab State
  const [mainTab, setMainTab] = useState<'submissions' | 'problem' | 'testcases'>('submissions');

  const [searchQuery, setSearchQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState('all');

  // Edit Modal State
  const [isEditModalOpen, setIsEditModalOpen] = useState(false);
  const [editTitle, setEditTitle] = useState('');
  const [editClassIds, setEditClassIds] = useState<string[]>([]);
  const [editDate, setEditDate] = useState('');
  const [editDuration, setEditDuration] = useState(90);
  const [editStatus, setEditStatus] = useState('active');
  const [editError, setEditError] = useState('');

  // Test Case Modal State
  const [isTestCaseModalOpen, setIsTestCaseModalOpen] = useState(false);
  const [editingTestCase, setEditingTestCase] = useState<TestCaseResponse | null>(null);
  const [tcName, setTcName] = useState('');
  const [tcDescription, setTcDescription] = useState('');
  const [tcInputData, setTcInputData] = useState('');
  const [tcExpectedOutput, setTcExpectedOutput] = useState('');
  const [tcPoints, setTcPoints] = useState(10);
  const [tcIsHidden, setTcIsHidden] = useState(false);
  const [tcDisplayOrder, setTcDisplayOrder] = useState(0);
  const [tcError, setTcError] = useState('');

  const filteredSubmissions = useMemo(() => {
    if (!submissions) return [];
    return submissions.filter(sub => {
      if (statusFilter === 'pending') {
        const isPending = sub.status === 'PENDING' || sub.status === 'SUBMITTED' || sub.executionStatus === 'PENDING';
        if (!isPending) return false;
      } else if (statusFilter === 'processing') {
        const isProc = sub.status === 'PROCESSING' || sub.executionStatus === 'RUNNING';
        if (!isProc) return false;
      } else if (statusFilter === 'graded') {
        const isGraded = sub.status === 'GRADED' || sub.status === 'COMPLETED' || sub.status === 'EVALUATED';
        if (!isGraded) return false;
      } else if (statusFilter === 'failed') {
        const isFailed = sub.status === 'FAILED' || sub.executionStatus === 'FAILED' || sub.executionStatus === 'COMPILATION_ERROR' || sub.executionStatus === 'RUNTIME_ERROR' || sub.executionStatus === 'TIMEOUT';
        if (!isFailed) return false;
      } else if (statusFilter === 'flagged') {
        if (sub.plagiarismStatus !== 'FLAGGED') return false;
      }

      if (searchQuery) {
        const query = searchQuery.toLowerCase();
        return (
          sub.studentName?.toLowerCase().includes(query) ||
          sub.studentEmail?.toLowerCase().includes(query) ||
          sub.originalFilename?.toLowerCase().includes(query)
        );
      }
      return true;
    });
  }, [submissions, searchQuery, statusFilter]);

  const stats = useMemo(() => {
    if (!submissions) return { all: 0, pending: 0, processing: 0, graded: 0, failed: 0, flagged: 0 };
    return {
      all: submissions.length,
      pending: submissions.filter(s => s.status === 'PENDING' || s.status === 'SUBMITTED' || s.executionStatus === 'PENDING').length,
      processing: submissions.filter(s => s.status === 'PROCESSING' || s.executionStatus === 'RUNNING').length,
      graded: submissions.filter(s => s.status === 'GRADED' || s.status === 'COMPLETED' || s.status === 'EVALUATED').length,
      failed: submissions.filter(s => s.status === 'FAILED' || s.executionStatus === 'FAILED' || s.executionStatus === 'COMPILATION_ERROR' || s.executionStatus === 'RUNTIME_ERROR' || s.executionStatus === 'TIMEOUT').length,
      flagged: submissions.filter(s => s.plagiarismStatus === 'FLAGGED').length,
    };
  }, [submissions]);

  const handleOpenEditModal = () => {
    if (!exam) return;
    setEditTitle(exam.title || '');
    setEditClassIds(exam.classIds && exam.classIds.length > 0 ? exam.classIds : (exam.classId ? [exam.classId] : []));
    if (exam.examDate) {
      const d = new Date(exam.examDate);
      d.setMinutes(d.getMinutes() - d.getTimezoneOffset());
      setEditDate(d.toISOString().slice(0, 16));
    } else {
      setEditDate('');
    }
    setEditDuration(exam.duration || 90);
    setEditStatus((exam.status || 'active').toLowerCase());
    setEditError('');
    setIsEditModalOpen(true);
  };

  const handleToggleClass = (classId: string) => {
    setEditClassIds(prev => 
      prev.includes(classId) 
        ? prev.filter(id => id !== classId)
        : [...prev, classId]
    );
  };

  const handleEditSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!exam) return;
    if (!editTitle.trim()) {
      setEditError('Tiêu đề bài thi không được để trống.');
      return;
    }
    if (editClassIds.length === 0) {
      setEditError('Vui lòng chọn ít nhất một lớp học cho bài thi.');
      return;
    }

    setEditError('');
    updateExam.mutate(
      {
        id: exam.id,
        payload: {
          title: editTitle.trim(),
          classId: editClassIds[0],
          classIds: editClassIds,
          date: editDate ? new Date(editDate).toISOString() : undefined,
          durationMinutes: Number(editDuration),
          status: editStatus,
        },
      },
      {
        onSuccess: () => {
          setIsEditModalOpen(false);
          toast.success('Cập nhật cấu hình bài thi thành công!');
        },
        onError: (err: unknown) => {
          const msg = (err as { response?: { data?: { message?: string } }; message?: string })?.response?.data?.message || (err as { message?: string })?.message || 'Không thể cập nhật bài thi. Vui lòng thử lại.';
          setEditError(msg);
          toast.error(msg);
        },
      }
    );
  };

  const hasSubmissions = (submissions && submissions.length > 0) || (exam?.submittedCount && exam.submittedCount > 0);

  const handleOpenCreateTestCase = () => {
    if (hasSubmissions) {
      toast.error('Bài thi đã có bài nộp từ sinh viên. Không thể thêm test case mới.');
      return;
    }
    setEditingTestCase(null);
    setTcName('');
    setTcDescription('');
    setTcInputData('');
    setTcExpectedOutput('');
    setTcPoints(10);
    setTcIsHidden(false);
    setTcDisplayOrder(testCases ? testCases.length + 1 : 1);
    setTcError('');
    setIsTestCaseModalOpen(true);
  };

  const handleOpenEditTestCase = (tc: TestCaseResponse) => {
    if (hasSubmissions) {
      toast.error('Bài thi đã có bài nộp từ sinh viên. Không thể sửa test case.');
      return;
    }
    setEditingTestCase(tc);
    setTcName(tc.name || '');
    setTcDescription(tc.description || '');
    setTcInputData(tc.inputData || '');
    setTcExpectedOutput(tc.expectedOutput || '');
    setTcPoints(tc.points || 10);
    setTcIsHidden(Boolean(tc.isHidden));
    setTcDisplayOrder(tc.displayOrder || 0);
    setTcError('');
    setIsTestCaseModalOpen(true);
  };

  const handleDeleteTestCase = (tcId: string) => {
    if (hasSubmissions) {
      toast.error('Bài thi đã có bài nộp từ sinh viên. Không thể xóa test case.');
      return;
    }
    if (!confirm('Bạn có chắc chắn muốn xóa test case này không?')) return;
    deleteTestCaseMutation.mutate(
      { examId: examId || '', id: tcId },
      {
        onSuccess: () => {
          toast.success('Đã xóa test case thành công');
        },
        onError: (err: unknown) => {
          toast.error((err as Error).message || 'Không thể xóa test case');
        }
      }
    );
  };

  const handleTestCaseSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!tcName.trim()) {
      setTcError('Tên test case không được để trống.');
      return;
    }
    if (tcPoints < 0) {
      setTcError('Điểm test case phải lớn hơn hoặc bằng 0.');
      return;
    }
    setTcError('');

    if (editingTestCase) {
      updateTestCaseMutation.mutate(
        {
          examId: examId || '',
          id: editingTestCase.id,
          payload: {
            name: tcName.trim(),
            description: tcDescription.trim() || undefined,
            inputData: tcInputData,
            expectedOutput: tcExpectedOutput,
            points: Number(tcPoints),
            isHidden: tcIsHidden,
            displayOrder: Number(tcDisplayOrder),
          }
        },
        {
          onSuccess: () => {
            setIsTestCaseModalOpen(false);
            toast.success('Đã cập nhật test case thành công!');
          },
          onError: (err: unknown) => {
            setTcError((err as Error).message || 'Lỗi khi cập nhật test case');
          }
        }
      );
    } else {
      createTestCaseMutation.mutate(
        {
          examId: examId || '',
          payload: {
            name: tcName.trim(),
            description: tcDescription.trim() || undefined,
            inputData: tcInputData,
            expectedOutput: tcExpectedOutput,
            points: Number(tcPoints),
            isHidden: tcIsHidden,
            displayOrder: Number(tcDisplayOrder),
          }
        },
        {
          onSuccess: () => {
            setIsTestCaseModalOpen(false);
            toast.success('Đã tạo test case mới thành công!');
          },
          onError: (err: unknown) => {
            setTcError((err as Error).message || 'Lỗi khi tạo test case');
          }
        }
      );
    }
  };

  if (!examId) return null;

  if (isLoadingExam || isLoadingSubmissions) {
    return (
      <div className="flex flex-col h-72 items-center justify-center gap-3">
        <div className="w-8 h-8 rounded-full border-2 border-primary border-t-transparent animate-spin" />
        <p className="text-on-surface-variant font-label-md text-xs">Đang tải danh sách bài thi và bài nộp...</p>
      </div>
    );
  }

  if (isErrorExam) {
    const status = (examError as AxiosError)?.response?.status;
    return (
      <div className="max-w-xl mx-auto my-12 p-8 rounded-2xl bg-surface-container-lowest border border-outline-variant/40 shadow-lg text-center flex flex-col items-center gap-3">
        <div className="p-3 rounded-full bg-rose-50 text-rose-600">
          <AlertCircle className="w-8 h-8" />
        </div>
        <h2 className="font-headline-md text-headline-md text-on-surface">
          {status === 404 ? 'Không tìm thấy bài thi' : status === 403 ? 'Truy cập bị từ chối' : 'Đã xảy ra lỗi'}
        </h2>
        <p className="font-body-md text-on-surface-variant text-xs">{(examError as Error).message}</p>
        <Link href="/teacher/exams" className="mt-2 px-4 py-2 bg-primary text-on-primary rounded-lg text-xs font-medium">
          Quay lại danh sách bài thi
        </Link>
      </div>
    );
  }

  if (!exam) return null;

  return (
    <div className="max-w-7xl mx-auto px-space-lg py-space-lg">
      <div className="flex flex-col w-full space-y-space-lg">
        {/* Top Navigation & Action Bar */}
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-space-md">
          <div className="space-y-1.5">
            <nav className="flex items-center gap-2 text-on-surface-variant text-xs">
              <Link className="hover:text-primary transition-colors flex items-center gap-1 font-medium" href="/teacher/exams">
                <ArrowLeft className="w-3.5 h-3.5" />
                <span>Quản lý bài thi</span>
              </Link>
              <span className="text-outline-variant">/</span>
              <span className="text-primary font-semibold truncate max-w-[220px]">{exam.title}</span>
            </nav>
            <div className="flex flex-wrap items-center gap-3">
              <h1 className="font-headline-lg text-headline-lg text-on-surface tracking-tight">{exam.title}</h1>
              <div className="flex items-center gap-1.5 px-2.5 py-0.5 rounded-full bg-emerald-50 text-emerald-700 border border-emerald-200 text-xs font-semibold uppercase">
                {exam.status === 'draft' ? 'Bản nháp' : (exam.status === 'completed' ? 'Đã kết thúc' : 'Đang mở thi')}
              </div>
            </div>
            <div className="flex flex-wrap items-center gap-2 pt-1 text-xs text-on-surface-variant">
              <div className="flex flex-wrap items-center gap-1.5" data-testid="assigned-classes-list">
                <span className="text-outline font-medium">Lớp học áp dụng:</span>
                {exam.assignedClasses && exam.assignedClasses.length > 0 ? (
                  exam.assignedClasses.map((cls) => (
                    <span key={cls.id} className="inline-flex items-center gap-1 px-2.5 py-1 rounded bg-surface-container font-mono font-medium text-on-surface border border-outline-variant/30">
                      <BookOpen className="w-3.5 h-3.5 text-primary" />
                      <span>{cls.code} — {cls.name}</span>
                    </span>
                  ))
                ) : (
                  <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded bg-surface-container font-mono font-medium text-on-surface border border-outline-variant/30">
                    <BookOpen className="w-3.5 h-3.5 text-primary" />
                    <span>{exam.courseCode || 'SE-GENERAL'}</span>
                  </span>
                )}
              </div>
              <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded bg-surface-container-high text-on-surface border border-outline-variant/30">
                <Clock className="w-3.5 h-3.5 text-outline" />
                <span>Thời gian: {exam.examDate ? new Date(exam.examDate).toLocaleString('vi-VN') : 'Linh hoạt'}</span>
              </span>
              <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded bg-surface-container-high text-on-surface border border-outline-variant/30 font-medium" data-testid="exam-duration-badge">
                <Clock className="w-3.5 h-3.5 text-primary" />
                <span>Thời lượng: {exam.duration || exam.durationMinutes || 90} phút</span>
              </span>
              {exam.enrolledCount !== undefined && (
                <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded bg-surface-container text-on-surface border border-outline-variant/30">
                  <Users className="w-3.5 h-3.5 text-secondary" />
                  <span>{exam.enrolledCount} sinh viên tham gia</span>
                </span>
              )}
            </div>
          </div>
          <div className="flex flex-wrap items-center gap-2 self-start md:self-center">
            {exam.status === 'draft' && (
              <button
                onClick={handlePublishExam}
                disabled={publishMutation.isPending}
                className="inline-flex items-center gap-1.5 px-4 py-2 rounded-lg bg-emerald-600 hover:bg-emerald-700 text-white text-xs font-semibold transition-all shadow-sm cursor-pointer disabled:opacity-50"
                type="button"
                id="btn-publish-exam"
                title="Phát hành đề thi cho sinh viên trong lớp làm bài"
              >
                {publishMutation.isPending ? (
                  <Loader2 className="w-3.5 h-3.5 animate-spin" />
                ) : (
                  <Send className="w-3.5 h-3.5" />
                )}
                <span>Phát hành bài thi</span>
              </button>
            )}
            <button
              onClick={handleOpenEditModal}
              className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-lg bg-surface-container hover:bg-surface-container-high text-on-surface text-xs font-medium transition-all border border-outline-variant/40 shadow-xs cursor-pointer"
              type="button"
              id="btn-edit-exam"
            >
              <Edit3 className="w-3.5 h-3.5 text-on-surface-variant" />
              <span>Cấu hình bài thi</span>
            </button>
            <button
              onClick={() => {
                setIsDeleteModalOpen(true);
                setDeleteError('');
              }}
              className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-lg bg-rose-50 hover:bg-rose-100 text-rose-700 text-xs font-medium transition-all border border-rose-200 shadow-xs cursor-pointer"
              type="button"
              id="btn-delete-exam"
            >
              <Trash2 className="w-3.5 h-3.5 text-rose-600" />
              <span>{t('teacherExams.deleteButton')}</span>
            </button>
          </div>
        </div>

        {/* Main Section Navigation Tabs */}
        <div className="flex items-center gap-2 border-b border-outline-variant/30 pb-2">
          <button
            type="button"
            onClick={() => setMainTab('submissions')}
            className={`px-4 py-2 rounded-lg text-xs font-semibold transition-all cursor-pointer flex items-center gap-2 ${
              mainTab === 'submissions'
                ? 'bg-primary text-on-primary shadow-xs'
                : 'bg-surface-container text-on-surface-variant hover:text-on-surface'
            }`}
          >
            <Users className="w-4 h-4" />
            <span>Danh sách bài nộp ({submissions?.length ?? 0})</span>
          </button>
          <button
            type="button"
            onClick={() => setMainTab('problem')}
            className={`px-4 py-2 rounded-lg text-xs font-semibold transition-all cursor-pointer flex items-center gap-2 ${
              mainTab === 'problem'
                ? 'bg-primary text-on-primary shadow-xs'
                : 'bg-surface-container text-on-surface-variant hover:text-on-surface'
            }`}
          >
            <BookOpen className="w-4 h-4" />
            <span>Đề bài &amp; Rubric</span>
          </button>
          <button
            type="button"
            onClick={() => setMainTab('testcases')}
            className={`px-4 py-2 rounded-lg text-xs font-semibold transition-all cursor-pointer flex items-center gap-2 ${
              mainTab === 'testcases'
                ? 'bg-primary text-on-primary shadow-xs'
                : 'bg-surface-container text-on-surface-variant hover:text-on-surface'
            }`}
          >
            <Code2 className="w-4 h-4" />
            <span>Bộ Test Cases ({testCases?.length ?? 0})</span>
          </button>
        </div>

        {/* Tab 1: Submissions */}
        {mainTab === 'submissions' && (
          <>
            {/* Filter & Search Toolbar */}
            <div className="p-3.5 rounded-xl bg-surface-container-lowest border border-outline-variant/40 shadow-xs flex flex-col lg:flex-row items-stretch lg:items-center justify-between gap-3">
              <div className="flex flex-wrap items-center gap-1.5 overflow-x-auto pb-1 lg:pb-0 text-xs">
                <button 
                  className={`px-3 py-1.5 rounded-lg font-semibold transition-all cursor-pointer ${
                    statusFilter === 'all' 
                      ? 'bg-primary text-on-primary shadow-xs' 
                      : 'text-on-surface-variant hover:bg-surface-container hover:text-on-surface'
                  }`}
                  onClick={() => setStatusFilter('all')}
                >
                  Tất cả ({stats.all})
                </button>
                <button 
                  className={`px-3 py-1.5 rounded-lg font-semibold transition-all cursor-pointer ${
                    statusFilter === 'pending' 
                      ? 'bg-primary text-on-primary shadow-xs' 
                      : 'text-on-surface-variant hover:bg-surface-container hover:text-on-surface'
                  }`}
                  onClick={() => setStatusFilter('pending')}
                >
                  Chờ xử lý ({stats.pending})
                </button>
                <button 
                  className={`px-3 py-1.5 rounded-lg font-semibold transition-all cursor-pointer ${
                    statusFilter === 'processing' 
                      ? 'bg-primary text-on-primary shadow-xs' 
                      : 'text-on-surface-variant hover:bg-surface-container hover:text-on-surface'
                  }`}
                  onClick={() => setStatusFilter('processing')}
                >
                  Đang chấm ({stats.processing})
                </button>
                <button 
                  className={`px-3 py-1.5 rounded-lg font-semibold transition-all cursor-pointer ${
                    statusFilter === 'graded' 
                      ? 'bg-primary text-on-primary shadow-xs' 
                      : 'text-on-surface-variant hover:bg-surface-container hover:text-on-surface'
                  }`}
                  onClick={() => setStatusFilter('graded')}
                >
                  Hoàn tất ({stats.graded})
                </button>
                {stats.failed > 0 && (
                  <button 
                    className={`px-3 py-1.5 rounded-lg font-semibold transition-all cursor-pointer ${
                      statusFilter === 'failed' 
                        ? 'bg-rose-600 text-white shadow-xs' 
                        : 'text-rose-700 hover:bg-rose-50'
                    }`}
                    onClick={() => setStatusFilter('failed')}
                  >
                    Lỗi thi hành ({stats.failed})
                  </button>
                )}
                <button 
                  className={`px-3 py-1.5 rounded-lg font-semibold transition-all cursor-pointer ${
                    statusFilter === 'flagged' 
                      ? 'bg-rose-600 text-white shadow-xs' 
                      : 'text-rose-600 hover:bg-rose-50'
                  }`}
                  onClick={() => setStatusFilter('flagged')}
                >
                  Cần xem xét đạo văn ({stats.flagged})
                </button>
              </div>

              <div className="flex items-center gap-2 flex-1 lg:max-w-md justify-end">
                <div className="relative w-full">
                  <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-outline w-4 h-4" />
                  <input 
                    className="w-full pl-9 pr-8 py-1.5 bg-surface-container-low rounded-lg text-on-surface text-xs border border-outline-variant/40 focus:outline-none focus:border-primary transition-all" 
                    placeholder="Tìm sinh viên, email, tệp ZIP..." 
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
              </div>
            </div>

            {/* Detailed Submissions Table */}
            <div className="rounded-xl bg-surface-container-lowest border border-outline-variant/40 shadow-xs overflow-hidden flex flex-col">
              <div className="overflow-x-auto">
                <table className="w-full text-left border-collapse text-xs" id="submissions-table">
                  <thead>
                    <tr className="bg-surface-container-low text-on-surface-variant font-label-xs uppercase tracking-wider border-b border-outline-variant/30">
                      <th className="py-2.5 px-4 font-semibold">Sinh viên</th>
                      <th className="py-2.5 px-4 font-semibold">Tệp mã nguồn</th>
                      <th className="py-2.5 px-4 font-semibold">Thời gian nộp</th>
                      <th className="py-2.5 px-4 font-semibold">Trạng thái chấm</th>
                      <th className="py-2.5 px-4 font-semibold">Test Cases</th>
                      <th className="py-2.5 px-4 font-semibold">Điểm số</th>
                      <th className="py-2.5 px-4 font-semibold">Đạo văn AST</th>
                      <th className="py-2.5 px-4 font-semibold">Nhận xét</th>
                      <th className="py-2.5 px-4 font-semibold text-right">Thao tác</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-outline-variant/20 text-on-surface">
                    {filteredSubmissions.length === 0 ? (
                      <tr>
                        <td colSpan={9} className="py-12 text-center text-outline">
                          Chưa có bài nộp nào phù hợp với bộ lọc tìm kiếm.
                        </td>
                      </tr>
                    ) : (
                      filteredSubmissions.map(sub => {
                        const isPending = sub.status === 'PENDING' || sub.status === 'SUBMITTED' || sub.executionStatus === 'PENDING';
                        const isProcessing = sub.status === 'PROCESSING' || sub.executionStatus === 'RUNNING';
                        const isFailed = sub.status === 'FAILED' || sub.executionStatus === 'FAILED' || sub.executionStatus === 'COMPILATION_ERROR' || sub.executionStatus === 'RUNTIME_ERROR' || sub.executionStatus === 'TIMEOUT';
                        const isGraded = (sub.status === 'GRADED' || sub.status === 'COMPLETED' || sub.status === 'EVALUATED') && !isFailed;

                        return (
                          <tr key={sub.id} className="hover:bg-surface-container-low/60 transition-colors">
                            {/* Student */}
                            <td className="py-3 px-4">
                              <p className="font-semibold text-on-surface">{sub.studentName}</p>
                              <p className="text-[11px] text-on-surface-variant">{sub.studentEmail}</p>
                            </td>

                            {/* ZIP Archive */}
                            <td className="py-3 px-4 font-mono text-[11px]">
                              <span className="inline-flex items-center gap-1 text-on-surface font-medium">
                                <FileArchive className="w-3.5 h-3.5 text-primary" />
                                <span className="truncate max-w-[140px]" title={sub.originalFilename || 'submission.zip'}>
                                  {sub.originalFilename || 'submission.zip'}
                                </span>
                              </span>
                            </td>

                            {/* Submitted At */}
                            <td className="py-3 px-4 text-on-surface-variant text-[11px]">
                              {sub.submittedAt ? new Date(sub.submittedAt).toLocaleString('vi-VN') : 'N/A'}
                            </td>

                            {/* Status */}
                            <td className="py-3 px-4">
                              {isProcessing ? (
                                <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[10px] font-semibold bg-blue-50 text-blue-700 border border-blue-200 animate-pulse">
                                  <Loader2 className="w-3 h-3 animate-spin" />
                                  <span>Đang chấm</span>
                                </span>
                              ) : isPending ? (
                                <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[10px] font-semibold bg-amber-50 text-amber-700 border border-amber-200">
                                  <Clock className="w-3 h-3" />
                                  <span>Chờ chấm</span>
                                </span>
                              ) : isFailed ? (
                                <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[10px] font-semibold bg-rose-50 text-rose-700 border border-rose-200">
                                  <AlertTriangle className="w-3 h-3" />
                                  <span>{sub.executionStatus || 'Lỗi thi hành'}</span>
                                </span>
                              ) : (
                                <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[10px] font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200">
                                  <CheckCircle2 className="w-3 h-3" />
                                  <span>Hoàn tất</span>
                                </span>
                              )}
                            </td>

                            {/* Test Cases */}
                            <td className="py-3 px-4 font-mono">
                              {isPending || isProcessing ? (
                                <span className="text-outline text-[11px]">Đang chạy...</span>
                              ) : sub.testCasesTotal !== undefined && sub.testCasesTotal > 0 ? (
                                <span className={`px-2 py-0.5 rounded text-[11px] font-semibold ${
                                  sub.testCasesPassed === sub.testCasesTotal
                                    ? 'bg-emerald-50 text-emerald-700 border border-emerald-200'
                                    : 'bg-surface-container text-on-surface'
                                }`}>
                                  {sub.testCasesPassed ?? 0}/{sub.testCasesTotal} test
                                </span>
                              ) : (
                                <span className="text-outline text-[11px]">-</span>
                              )}
                            </td>

                            {/* Score */}
                            <td className="py-3 px-4">
                              {isPending || isProcessing ? (
                                <span className="text-[11px] text-amber-600 font-medium italic">Đang chấm...</span>
                              ) : isGraded ? (
                                <div>
                                  <span className="font-mono bg-surface-container px-2 py-0.5 rounded font-bold text-on-surface">
                                    {sub.totalScore ?? sub.autoScore ?? 0}
                                  </span>
                                  <span className="text-outline text-[10px] ml-0.5">/10</span>
                                  {sub.autoScore !== undefined && sub.autoScore !== sub.totalScore && (
                                    <p className="text-[10px] text-outline font-mono mt-0.5">Auto: {sub.autoScore}</p>
                                  )}
                                </div>
                              ) : isFailed ? (
                                <span className="text-[11px] text-rose-600 font-medium">0/10 (Lỗi)</span>
                              ) : (
                                <span className="text-[11px] text-outline">Chưa có điểm</span>
                              )}
                            </td>

                            {/* AST Plagiarism */}
                            <td className="py-3 px-4">
                              {sub.plagiarismStatus === 'FLAGGED' ? (
                                <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-semibold bg-rose-50 text-rose-700 border border-rose-200">
                                  <ShieldAlert className="w-3 h-3" />
                                  <span>Cần xem xét {sub.maxSimilarityRate != null ? `(${Number(sub.maxSimilarityRate).toFixed(0)}%)` : ''}</span>
                                </span>
                              ) : sub.plagiarismStatus === 'SAFE' ? (
                                <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200">
                                  <CheckCircle2 className="w-3 h-3" />
                                  <span>An toàn {sub.maxSimilarityRate != null ? `(${Number(sub.maxSimilarityRate).toFixed(0)}%)` : ''}</span>
                                </span>
                              ) : (
                                <span className="text-outline text-[10px]">Chưa kiểm tra</span>
                              )}
                            </td>

                            {/* Instructor Feedback */}
                            <td className="py-3 px-4 text-[11px]">
                              {sub.instructorFeedback ? (
                                <span className="inline-flex items-center gap-1 text-primary truncate max-w-[120px]" title={sub.instructorFeedback}>
                                  <MessageSquare className="w-3 h-3" />
                                  <span className="truncate">{sub.instructorFeedback}</span>
                                </span>
                              ) : (
                                <span className="text-outline text-[10px]">-</span>
                              )}
                            </td>

                            {/* Action */}
                            <td className="py-3 px-4 text-right">
                              <Link 
                                href={`/teacher/submissions/${sub.id}`} 
                                className="inline-flex items-center gap-1 px-3 py-1 bg-surface-container hover:bg-surface-container-high text-primary rounded-lg font-medium transition-colors"
                              >
                                <span>Xem &amp; Chấm</span>
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
              <div className="px-4 py-2.5 bg-surface-container-low/40 border-t border-outline-variant/30 flex items-center justify-between text-xs text-outline">
                <span>Hiển thị <strong className="text-on-surface">{filteredSubmissions.length}</strong> trên tổng số <strong className="text-on-surface">{stats.all}</strong> bài nộp</span>
              </div>
            </div>
          </>
        )}

        {/* Tab: Problem Statement & Rubric */}
        {mainTab === 'problem' && (
          <div className="flex flex-col gap-5">
            {/* Metadata bar */}
            <div className="p-4 rounded-xl bg-surface-container-lowest border border-outline-variant/40 shadow-xs flex flex-wrap items-center justify-between gap-3 text-xs">
              <div className="flex flex-wrap items-center gap-2">
                <span className="font-semibold text-on-surface">Ngôn ngữ:</span>
                <span className="px-2.5 py-1 rounded bg-primary/10 text-primary font-mono font-medium border border-primary/20">
                  {exam.programmingLanguage || 'Java'}
                </span>
                {exam.sourceDocumentName && (
                  <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded bg-surface-container text-on-surface font-medium border border-outline-variant/30">
                    <FileText className="w-3.5 h-3.5 text-primary" />
                    <span>Tài liệu: {exam.sourceDocumentName}</span>
                  </span>
                )}
              </div>
              <div className="flex items-center gap-2">
                <span className="text-on-surface-variant">Tổng tiêu chí rubric:</span>
                <span className="px-2 py-0.5 rounded bg-emerald-50 text-emerald-700 font-mono font-bold text-xs border border-emerald-200">
                  {exam.rubricCriteria?.length ?? 0} tiêu chí
                </span>
              </div>
            </div>

            {/* Problem Statement Card */}
            <div className="p-5 rounded-xl bg-surface-container-lowest border border-outline-variant/40 shadow-xs flex flex-col gap-3">
              <h2 className="font-headline-sm text-sm font-bold text-on-surface flex items-center gap-2">
                <BookOpen className="w-4 h-4 text-primary" />
                <span>Mô tả đề bài chi tiết</span>
              </h2>
              <div className="text-xs text-on-surface leading-relaxed whitespace-pre-wrap bg-surface-container-low/40 p-4 rounded-lg border border-outline-variant/20 font-sans">
                {exam.problemStatement || 'Chưa có nội dung đề bài chi tiết.'}
              </div>
            </div>

            {/* Input & Output Specifications */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div className="p-4 rounded-xl bg-surface-container-lowest border border-outline-variant/40 shadow-xs flex flex-col gap-2">
                <h3 className="text-xs font-bold text-on-surface uppercase tracking-wide flex items-center gap-1.5">
                  <span className="w-2 h-2 rounded-full bg-blue-500"></span>
                  Quy chuẩn Đầu vào (Input Specification)
                </h3>
                <p className="text-xs text-on-surface-variant leading-relaxed whitespace-pre-wrap">
                  {exam.inputSpecification || 'Tiêu chuẩn theo stdin.'}
                </p>
              </div>

              <div className="p-4 rounded-xl bg-surface-container-lowest border border-outline-variant/40 shadow-xs flex flex-col gap-2">
                <h3 className="text-xs font-bold text-on-surface uppercase tracking-wide flex items-center gap-1.5">
                  <span className="w-2 h-2 rounded-full bg-emerald-500"></span>
                  Quy chuẩn Đầu ra (Output Specification)
                </h3>
                <p className="text-xs text-on-surface-variant leading-relaxed whitespace-pre-wrap">
                  {exam.outputSpecification || 'Tiêu chuẩn theo stdout.'}
                </p>
              </div>
            </div>

            {/* Constraints */}
            {exam.constraints && (
              <div className="p-4 rounded-xl bg-surface-container-lowest border border-outline-variant/40 shadow-xs flex flex-col gap-2">
                <h3 className="text-xs font-bold text-on-surface uppercase tracking-wide flex items-center gap-1.5">
                  <AlertCircle className="w-3.5 h-3.5 text-amber-500" />
                  Ràng buộc kỹ thuật (Constraints)
                </h3>
                <div className="text-xs text-on-surface-variant leading-relaxed whitespace-pre-wrap font-mono bg-surface-container-low/50 p-3 rounded-lg border border-outline-variant/20">
                  {exam.constraints}
                </div>
              </div>
            )}

            {/* Samples */}
            {(exam.sampleInput || exam.sampleOutput) && (
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div className="p-4 rounded-xl bg-surface-container-lowest border border-outline-variant/40 shadow-xs flex flex-col gap-2">
                  <h3 className="text-xs font-bold text-on-surface">Sample Input</h3>
                  <pre className="font-mono text-xs bg-surface-container-low p-3 rounded-lg border border-outline-variant/20 overflow-x-auto whitespace-pre-wrap">
                    {exam.sampleInput || '(trống)'}
                  </pre>
                </div>
                <div className="p-4 rounded-xl bg-surface-container-lowest border border-outline-variant/40 shadow-xs flex flex-col gap-2">
                  <h3 className="text-xs font-bold text-on-surface">Sample Output</h3>
                  <pre className="font-mono text-xs bg-surface-container-low p-3 rounded-lg border border-outline-variant/20 overflow-x-auto whitespace-pre-wrap">
                    {exam.sampleOutput || '(trống)'}
                  </pre>
                </div>
              </div>
            )}

            {/* Rubric Criteria Table */}
            <div className="rounded-xl bg-surface-container-lowest border border-outline-variant/40 shadow-xs overflow-hidden flex flex-col">
              <div className="p-3.5 bg-surface-container-low/60 border-b border-outline-variant/30 flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <FileCheck className="w-4 h-4 text-primary" />
                  <h3 className="text-xs font-bold text-on-surface uppercase tracking-wide">
                    Tiêu chí chấm điểm Rubric (Thang điểm 10)
                  </h3>
                </div>
                <span className="font-mono font-bold text-xs text-emerald-700 bg-emerald-50 px-2.5 py-1 rounded-full border border-emerald-200">
                  Tổng:{' '}
                  {exam.rubricCriteria?.reduce((acc, c) => acc + (c.maxScore || 0), 0).toFixed(2) ?? '10.00'}{' '}
                  / 10.00 đ
                </span>
              </div>
              <div className="overflow-x-auto">
                <table className="w-full text-left border-collapse text-xs">
                  <thead>
                    <tr className="bg-surface-container-low text-on-surface-variant font-label-xs uppercase tracking-wider border-b border-outline-variant/30">
                      <th className="py-2.5 px-4 font-semibold w-12">#</th>
                      <th className="py-2.5 px-4 font-semibold">Tên tiêu chí</th>
                      <th className="py-2.5 px-4 font-semibold">Mô tả chi tiết / Hướng dẫn chấm</th>
                      <th className="py-2.5 px-4 font-semibold text-right w-28">Điểm tối đa</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-outline-variant/20 text-on-surface">
                    {!exam.rubricCriteria || exam.rubricCriteria.length === 0 ? (
                      <tr>
                        <td colSpan={4} className="py-8 text-center text-outline">
                          Chưa có bảng tiêu chí rubric riêng cho đề thi này. Điểm số sẽ tính theo trọng số test cases.
                        </td>
                      </tr>
                    ) : (
                      exam.rubricCriteria.map((item, idx) => (
                        <tr key={idx} className="hover:bg-surface-container-low/60 transition-colors">
                          <td className="py-3 px-4 font-mono font-semibold text-outline">{idx + 1}</td>
                          <td className="py-3 px-4 font-semibold text-on-surface">{item.name}</td>
                          <td className="py-3 px-4 text-on-surface-variant text-[11px] leading-relaxed">
                            {item.description || '-'}
                          </td>
                          <td className="py-3 px-4 text-right font-mono font-bold text-primary">
                            {Number(item.maxScore).toFixed(2)} đ
                          </td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        )}

        {/* Tab 2: Test Cases Management */}
        {mainTab === 'testcases' && (
          <div className="flex flex-col gap-4">
            {/* Warning or Action Bar */}
            {hasSubmissions ? (
              <div className="p-3.5 rounded-xl bg-amber-50 border border-amber-200 text-amber-800 text-xs flex items-center gap-2">
                <Lock className="w-4 h-4 shrink-0 text-amber-600" />
                <span>
                  Bài thi đã có bài nộp từ sinh viên. Theo quy định khảo thí, không được thêm, sửa hoặc xóa test cases để đảm bảo tính toàn vẹn kết quả.
                </span>
              </div>
            ) : null}

            <div className="p-3.5 rounded-xl bg-surface-container-lowest border border-outline-variant/40 shadow-xs flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3">
              <div className="flex items-center gap-3 text-xs">
                <span className="font-semibold text-on-surface">
                  Tổng: <strong>{testCases?.length ?? 0}</strong> Test Cases
                </span>
                <span className="text-outline">•</span>
                <span className="text-on-surface">
                  Tổng điểm trọng số: <strong className="text-primary font-mono">{testCases?.reduce((acc, c) => acc + (c.points || 0), 0) ?? 0} đ</strong>
                </span>
              </div>
              <button
                type="button"
                onClick={handleOpenCreateTestCase}
                disabled={Boolean(hasSubmissions)}
                className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-lg bg-primary text-on-primary text-xs font-semibold hover:bg-primary/90 transition-all shadow-xs cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
              >
                <Plus className="w-4 h-4" />
                <span>Thêm Test Case mới</span>
              </button>
            </div>

            {/* Test Cases Table */}
            <div className="rounded-xl bg-surface-container-lowest border border-outline-variant/40 shadow-xs overflow-hidden flex flex-col">
              <div className="overflow-x-auto">
                <table className="w-full text-left border-collapse text-xs" id="testcases-table">
                  <thead>
                    <tr className="bg-surface-container-low text-on-surface-variant font-label-xs uppercase tracking-wider border-b border-outline-variant/30">
                      <th className="py-2.5 px-4 font-semibold w-12">#</th>
                      <th className="py-2.5 px-4 font-semibold">Tên Test Case / Mô tả</th>
                      <th className="py-2.5 px-4 font-semibold">Input (stdin)</th>
                      <th className="py-2.5 px-4 font-semibold">Expected Output</th>
                      <th className="py-2.5 px-4 font-semibold">Điểm</th>
                      <th className="py-2.5 px-4 font-semibold">Chế độ</th>
                      <th className="py-2.5 px-4 font-semibold text-right">Thao tác</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-outline-variant/20 text-on-surface">
                    {isLoadingTestCases ? (
                      <tr>
                        <td colSpan={7} className="py-8 text-center text-outline">
                          <Loader2 className="w-5 h-5 animate-spin mx-auto mb-1 text-primary" />
                          <span>Đang tải danh sách test cases...</span>
                        </td>
                      </tr>
                    ) : !testCases || testCases.length === 0 ? (
                      <tr>
                        <td colSpan={7} className="py-12 text-center text-outline">
                          Chưa có Test Case nào được tạo cho bài thi này. Nhấn &quot;Thêm Test Case mới&quot; để thiết lập.
                        </td>
                      </tr>
                    ) : (
                      testCases.map((tc, index) => (
                        <tr key={tc.id} className="hover:bg-surface-container-low/60 transition-colors">
                          <td className="py-3 px-4 font-mono font-semibold text-outline">{tc.displayOrder || index + 1}</td>
                          <td className="py-3 px-4">
                            <span className="font-semibold text-on-surface block">{tc.name}</span>
                            {tc.description && (
                              <span className="text-[11px] text-on-surface-variant line-clamp-1">{tc.description}</span>
                            )}
                          </td>
                          <td className="py-3 px-4">
                            {tc.inputData ? (
                              <pre className="font-mono text-[11px] bg-surface-container-low px-2 py-1 rounded max-w-[200px] max-h-16 overflow-y-auto whitespace-pre-wrap">{tc.inputData}</pre>
                            ) : (
                              <span className="text-outline italic text-[11px]">Không có</span>
                            )}
                          </td>
                          <td className="py-3 px-4">
                            {tc.expectedOutput ? (
                              <pre className="font-mono text-[11px] bg-surface-container-low px-2 py-1 rounded max-w-[200px] max-h-16 overflow-y-auto whitespace-pre-wrap">{tc.expectedOutput}</pre>
                            ) : (
                              <span className="text-outline italic text-[11px]">Không có</span>
                            )}
                          </td>
                          <td className="py-3 px-4">
                            <span className="font-mono font-bold text-primary bg-primary/5 px-2 py-0.5 rounded border border-primary/20">
                              {tc.points} đ
                            </span>
                          </td>
                          <td className="py-3 px-4">
                            {tc.isHidden ? (
                              <span className="px-2 py-0.5 rounded-full bg-surface-container-high text-on-surface-variant border text-[10px] font-semibold inline-flex items-center gap-1">
                                <EyeOff className="w-3 h-3 text-outline" />
                                <span>Ẩn</span>
                              </span>
                            ) : (
                              <span className="px-2 py-0.5 rounded-full bg-emerald-50 text-emerald-700 border border-emerald-200 text-[10px] font-semibold inline-flex items-center gap-1">
                                <Eye className="w-3 h-3" />
                                <span>Công khai</span>
                              </span>
                            )}
                          </td>
                          <td className="py-3 px-4 text-right">
                            <div className="flex items-center justify-end gap-1.5">
                              <button
                                type="button"
                                onClick={() => handleOpenEditTestCase(tc)}
                                disabled={Boolean(hasSubmissions)}
                                className="p-1 text-on-surface-variant hover:text-primary transition-colors disabled:opacity-30 disabled:cursor-not-allowed cursor-pointer"
                                title="Sửa test case"
                              >
                                <Edit3 className="w-3.5 h-3.5" />
                              </button>
                              <button
                                type="button"
                                onClick={() => handleDeleteTestCase(tc.id)}
                                disabled={Boolean(hasSubmissions)}
                                className="p-1 text-on-surface-variant hover:text-rose-600 transition-colors disabled:opacity-30 disabled:cursor-not-allowed cursor-pointer"
                                title="Xóa test case"
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
            </div>
          </div>
        )}

        {/* Edit Exam Modal */}
        {isEditModalOpen && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-xs p-4 animate-in fade-in duration-150">
            <div className="bg-surface-container-lowest rounded-2xl max-w-lg w-full p-6 shadow-2xl border border-outline-variant/40 flex flex-col gap-4 animate-in zoom-in-95 duration-150">
              <div className="flex items-center justify-between border-b border-outline-variant/30 pb-3">
                <div className="flex items-center gap-2">
                  <Edit3 className="w-5 h-5 text-primary" />
                  <h2 className="font-headline-sm text-headline-sm text-on-surface font-semibold">Chỉnh sửa cấu hình bài thi</h2>
                </div>
                <button
                  type="button"
                  onClick={() => setIsEditModalOpen(false)}
                  className="text-outline hover:text-on-surface transition-colors p-1 rounded-lg cursor-pointer"
                >
                  <X className="w-4 h-4" />
                </button>
              </div>

              <form onSubmit={handleEditSubmit} className="flex flex-col gap-4">
                {editError && (
                  <div className="p-3 rounded-lg bg-rose-50 border border-rose-200 text-rose-700 flex items-center gap-2 text-xs">
                    <AlertCircle className="w-4 h-4 shrink-0" />
                    <span>{editError}</span>
                  </div>
                )}

                <div className="flex flex-col gap-1">
                  <label className="text-xs font-semibold text-on-surface">Tiêu đề bài thi *</label>
                  <input
                    type="text"
                    required
                    value={editTitle}
                    onChange={(e) => setEditTitle(e.target.value)}
                    className="w-full px-3 py-2 bg-surface-container-low border border-outline-variant/40 rounded-lg text-xs text-on-surface focus:outline-none focus:border-primary transition-colors"
                  />
                </div>

                <div className="flex flex-col gap-1.5">
                  <div className="flex items-center justify-between">
                    <label className="text-xs font-semibold text-on-surface">
                      Lớp học áp dụng ({editClassIds.length} lớp đã chọn) *
                    </label>
                    {classes && classes.length > 1 && (
                      <div className="flex items-center gap-2 text-[11px]">
                        <button
                          type="button"
                          onClick={() => setEditClassIds(classes.map(c => c.id))}
                          className="text-primary hover:underline font-medium cursor-pointer"
                        >
                          Chọn tất cả
                        </button>
                        <span className="text-outline">|</span>
                        <button
                          type="button"
                          onClick={() => setEditClassIds([])}
                          className="text-outline hover:underline cursor-pointer"
                        >
                          Bỏ chọn
                        </button>
                      </div>
                    )}
                  </div>
                  <div className="max-h-40 overflow-y-auto p-2 bg-surface-container-low border border-outline-variant/40 rounded-lg flex flex-col gap-1.5">
                    {classes && classes.length > 0 ? (
                      classes.map((cls) => {
                        const isChecked = editClassIds.includes(cls.id);
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
                              <span className="truncate max-w-[200px]">{cls.name}</span>
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
                      <p className="text-xs text-outline py-2 text-center">Không tìm thấy lớp học nào.</p>
                    )}
                  </div>
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                  <div className="flex flex-col gap-1">
                    <label className="text-xs font-semibold text-on-surface">Thời gian bắt đầu *</label>
                    <input
                      type="datetime-local"
                      required
                      value={editDate}
                      onChange={(e) => setEditDate(e.target.value)}
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
                      value={editDuration}
                      onChange={(e) => setEditDuration(Number(e.target.value))}
                      className="w-full px-3 py-2 bg-surface-container-low border border-outline-variant/40 rounded-lg text-xs text-on-surface focus:outline-none focus:border-primary transition-colors font-mono"
                    />
                    <div className="flex items-center gap-1.5 flex-wrap pt-0.5">
                      {[45, 60, 90, 120, 180].map((mins) => (
                        <button
                          key={mins}
                          type="button"
                          onClick={() => setEditDuration(mins)}
                          className={`px-2 py-0.5 rounded text-[11px] font-medium transition-colors cursor-pointer border ${
                            editDuration === mins
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

                <div className="flex flex-col gap-1">
                  <label className="text-xs font-semibold text-on-surface">Trạng thái bài thi *</label>
                  <select
                    value={editStatus}
                    onChange={(e) => setEditStatus(e.target.value)}
                    className="w-full px-3 py-2 bg-surface-container-low border border-outline-variant/40 rounded-lg text-xs text-on-surface focus:outline-none focus:border-primary transition-colors cursor-pointer"
                  >
                    <option value="draft">Bản nháp (Chưa phát hành cho sinh viên)</option>
                    <option value="active">Đang mở thi (Cho phép sinh viên nộp bài)</option>
                    <option value="upcoming">Sắp diễn ra (Đã lên lịch)</option>
                    <option value="completed">Đã kết thúc (Đóng nhận bài nộp)</option>
                  </select>
                </div>

                <div className="flex justify-end gap-3 mt-2 pt-3 border-t border-outline-variant/30">
                  <button
                    type="button"
                    onClick={() => setIsEditModalOpen(false)}
                    disabled={updateExam.isPending}
                    className="px-4 py-2 text-xs rounded-lg border border-outline-variant/40 hover:bg-surface-container transition-colors cursor-pointer"
                  >
                    Hủy
                  </button>
                  <button
                    type="submit"
                    disabled={updateExam.isPending}
                    className="inline-flex items-center gap-2 px-5 py-2 bg-primary text-on-primary rounded-lg text-xs font-medium hover:bg-primary/90 transition-colors disabled:opacity-50 cursor-pointer shadow-sm"
                  >
                    {updateExam.isPending ? (
                      <>
                        <Loader2 className="w-3.5 h-3.5 animate-spin" />
                        <span>Đang lưu...</span>
                      </>
                    ) : (
                      <>
                        <CheckCircle2 className="w-3.5 h-3.5" />
                        <span>Lưu thay đổi</span>
                      </>
                    )}
                  </button>
                </div>
              </form>
            </div>
          </div>
        )}

        {/* Create/Edit Test Case Modal */}
        {isTestCaseModalOpen && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-xs p-4 animate-in fade-in duration-150">
            <div className="bg-surface-container-lowest rounded-2xl max-w-xl w-full p-6 shadow-2xl border border-outline-variant/40 flex flex-col gap-4 animate-in zoom-in-95 duration-150 max-h-[90vh] overflow-y-auto">
              <div className="flex items-center justify-between border-b border-outline-variant/30 pb-3">
                <div className="flex items-center gap-2">
                  <Code2 className="w-5 h-5 text-primary" />
                  <h2 className="font-headline-sm text-headline-sm text-on-surface font-semibold">
                    {editingTestCase ? 'Chỉnh sửa Test Case' : 'Thêm Test Case mới'}
                  </h2>
                </div>
                <button
                  type="button"
                  onClick={() => setIsTestCaseModalOpen(false)}
                  className="text-outline hover:text-on-surface transition-colors p-1 rounded-lg cursor-pointer"
                >
                  <X className="w-4 h-4" />
                </button>
              </div>

              <form onSubmit={handleTestCaseSubmit} className="flex flex-col gap-4">
                {tcError && (
                  <div className="p-3 rounded-lg bg-rose-50 border border-rose-200 text-rose-700 flex items-center gap-2 text-xs">
                    <AlertCircle className="w-4 h-4 shrink-0" />
                    <span>{tcError}</span>
                  </div>
                )}

                <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                  <div className="sm:col-span-2 flex flex-col gap-1">
                    <label className="text-xs font-semibold text-on-surface">Tên Test Case *</label>
                    <input
                      type="text"
                      required
                      placeholder="Ví dụ: Test Case 1 - Basic Addition"
                      value={tcName}
                      onChange={(e) => setTcName(e.target.value)}
                      className="w-full px-3 py-2 bg-surface-container-low border border-outline-variant/40 rounded-lg text-xs text-on-surface focus:outline-none focus:border-primary transition-colors"
                    />
                  </div>

                  <div className="flex flex-col gap-1">
                    <label className="text-xs font-semibold text-on-surface">Điểm / Trọng số *</label>
                    <input
                      type="number"
                      required
                      min={0}
                      step={0.5}
                      value={tcPoints}
                      onChange={(e) => setTcPoints(Number(e.target.value))}
                      className="w-full px-3 py-2 bg-surface-container-low border border-outline-variant/40 rounded-lg text-xs text-on-surface focus:outline-none focus:border-primary transition-colors"
                    />
                  </div>
                </div>

                <div className="flex flex-col gap-1">
                  <label className="text-xs font-semibold text-on-surface">Mô tả / Mục đích kiểm thử</label>
                  <input
                    type="text"
                    placeholder="Ví dụ: Kiểm tra trường hợp hai số nguyên dương"
                    value={tcDescription}
                    onChange={(e) => setTcDescription(e.target.value)}
                    className="w-full px-3 py-2 bg-surface-container-low border border-outline-variant/40 rounded-lg text-xs text-on-surface focus:outline-none focus:border-primary transition-colors"
                  />
                </div>

                <div className="flex flex-col gap-1">
                  <label className="text-xs font-semibold text-on-surface">
                    Input dữ liệu (Standard Input / stdin)
                  </label>
                  <span className="text-[11px] text-on-surface-variant">Dữ liệu được truyền vào qua stdin khi chạy chương trình (nhiều dòng hoặc phân tách bằng khoảng trắng).</span>
                  <textarea
                    rows={3}
                    placeholder="Ví dụ: 10 20"
                    value={tcInputData}
                    onChange={(e) => setTcInputData(e.target.value)}
                    className="w-full px-3 py-2 bg-surface-container-low font-mono text-xs border border-outline-variant/40 rounded-lg text-on-surface focus:outline-none focus:border-primary transition-colors"
                  />
                </div>

                <div className="flex flex-col gap-1">
                  <label className="text-xs font-semibold text-on-surface">
                    Expected Output (Đầu ra mong đợi chuẩn)
                  </label>
                  <span className="text-[11px] text-on-surface-variant">Hệ thống sẽ so sánh chính xác sau khi chuẩn hóa newline (CRLF -&gt; LF) và loại bỏ khoảng trắng dư ở cuối dòng.</span>
                  <textarea
                    rows={3}
                    placeholder="Ví dụ: 30"
                    value={tcExpectedOutput}
                    onChange={(e) => setTcExpectedOutput(e.target.value)}
                    className="w-full px-3 py-2 bg-surface-container-low font-mono text-xs border border-outline-variant/40 rounded-lg text-on-surface focus:outline-none focus:border-primary transition-colors"
                  />
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 pt-1">
                  <div className="flex flex-col gap-1">
                    <label className="text-xs font-semibold text-on-surface">Thứ tự hiển thị</label>
                    <input
                      type="number"
                      min={1}
                      value={tcDisplayOrder}
                      onChange={(e) => setTcDisplayOrder(Number(e.target.value))}
                      className="w-full px-3 py-2 bg-surface-container-low border border-outline-variant/40 rounded-lg text-xs text-on-surface focus:outline-none focus:border-primary transition-colors"
                    />
                  </div>

                  <div className="flex items-center gap-3 p-3 bg-surface-container-low rounded-xl border border-outline-variant/30 mt-auto">
                    <input
                      type="checkbox"
                      id="tc-is-hidden"
                      checked={tcIsHidden}
                      onChange={(e) => setTcIsHidden(e.target.checked)}
                      className="w-4 h-4 text-primary rounded border-outline-variant focus:ring-primary cursor-pointer"
                    />
                    <label htmlFor="tc-is-hidden" className="text-xs text-on-surface cursor-pointer select-none">
                      <span className="font-semibold block">Test Case Ẩn (Hidden)</span>
                      <span className="text-[11px] text-on-surface-variant">Sinh viên chỉ thấy Đạt/Trượt, không xem được Input và Expected Output.</span>
                    </label>
                  </div>
                </div>

                <div className="flex justify-end gap-3 mt-3 pt-3 border-t border-outline-variant/30">
                  <button
                    type="button"
                    onClick={() => setIsTestCaseModalOpen(false)}
                    disabled={createTestCaseMutation.isPending || updateTestCaseMutation.isPending}
                    className="px-4 py-2 text-xs rounded-lg border border-outline-variant/40 hover:bg-surface-container transition-colors cursor-pointer"
                  >
                    Hủy
                  </button>
                  <button
                    type="submit"
                    disabled={createTestCaseMutation.isPending || updateTestCaseMutation.isPending}
                    className="inline-flex items-center gap-2 px-5 py-2 bg-primary text-on-primary rounded-lg text-xs font-medium hover:bg-primary/90 transition-colors disabled:opacity-50 cursor-pointer shadow-sm"
                  >
                    {createTestCaseMutation.isPending || updateTestCaseMutation.isPending ? (
                      <>
                        <Loader2 className="w-3.5 h-3.5 animate-spin" />
                        <span>Đang lưu...</span>
                      </>
                    ) : (
                      <>
                        <CheckCircle2 className="w-3.5 h-3.5" />
                        <span>{editingTestCase ? 'Cập nhật' : 'Tạo mới'}</span>
                      </>
                    )}
                  </button>
                </div>
              </form>
            </div>
          </div>
        )}

        {/* Delete Exam Confirmation Modal */}
        {isDeleteModalOpen && exam && (
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
                    {exam.title}
                  </p>
                </div>
              </div>

              <div className="p-3 rounded-lg bg-surface-container-low border border-outline-variant/30 text-xs text-on-surface-variant space-y-2">
                <p>
                  {t('teacherExams.deleteModalWarning').replace('{title}', exam.title)}
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
                  onClick={() => setIsDeleteModalOpen(false)}
                  disabled={deleteExamMutation.isPending}
                  className="px-4 py-2 text-xs rounded-lg border border-outline-variant/40 hover:bg-surface-container transition-colors cursor-pointer"
                >
                  {t('common.cancel')}
                </button>
                <button
                  type="button"
                  id="confirm-delete-exam-detail-btn"
                  onClick={handleDeleteExam}
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
      </div>
    </div>
  );
}
