'use client';

import React, { useState, useEffect, useMemo, useRef } from 'react';
import { useRouter } from 'next/navigation';
import {
  useStudentExam,
  useStudentExamSubmissions,
  useSubmitExamZip,
  useSubmitExamBatchZip,
} from '../hooks/use-student-exams';
import { SubmissionResultResponse, ExamQuestionResponse, ExamResponse } from '../api/student-exams.api';
import { AxiosError } from 'axios';
import { AppLink as Link } from '@/components/ui/app-link';
import { useTranslation } from '@/i18n';
import {
  ArrowLeft,
  Send,
  AlertCircle,
  Clock,
  BookOpen,
  Lock,
  FileCheck,
  CheckCircle2,
  Loader2,
  ShieldAlert,
  AlertTriangle,
  Timer,
  UploadCloud,
  FileArchive,
  Trash2,
  Terminal,
  Copy,
  Check,
  ExternalLink,
  Code2,
  FileText,
  RefreshCw,
  ChevronDown,
  Download,
} from 'lucide-react';
import { toast } from 'sonner';
import { formatQuestionTitle } from '@/utils/questionUtils';

interface StudentExamDetailViewProps {
  examId: string;
}

type QuestionStatus =
  | 'NOT_SUBMITTED'
  | 'FILE_SELECTED'
  | 'UPLOADING'
  | 'SUBMITTED'
  | 'PROCESSING'
  | 'GRADED'
  | 'FAILED';

export function StudentExamDetailView({ examId }: StudentExamDetailViewProps) {
  const router = useRouter();
  const { t } = useTranslation();
  const { data: exam, isLoading, isError, error, refetch: refetchExam } = useStudentExam(examId);
  const { data: submissionsData, refetch: refetchSubmissions } = useStudentExamSubmissions(examId);

  const submitExamZip = useSubmitExamZip();
  const submitExamBatchZip = useSubmitExamBatchZip();

  // Multi-Question State
  const [selectedZips, setSelectedZips] = useState<Record<string, File>>({});
  const [questionErrors, setQuestionErrors] = useState<Record<string, string>>({});
  const [draggingQuestionId, setDraggingQuestionId] = useState<string | null>(null);
  const [uploadingQuestionId, setUploadingQuestionId] = useState<string | null>(null);
  const [selectedQuestionId, setSelectedQuestionId] = useState<string | null>(null);
  const [expandedHistory, setExpandedHistory] = useState<Record<string, boolean>>({});

  // Single Question (Legacy) State
  const [legacyZip, setLegacyZip] = useState<File | null>(null);
  const [isLegacyDragging, setIsLegacyDragging] = useState(false);
  const legacyFileInputRef = useRef<HTMLInputElement>(null);

  const [uploadProgress, setUploadProgress] = useState(0);
  const [localError, setLocalError] = useState('');
  const [showConfirmModal, setShowConfirmModal] = useState(false);
  const [submissionResult, setSubmissionResult] = useState<SubmissionResultResponse | null>(null);
  const [batchResults, setBatchResults] = useState<SubmissionResultResponse[] | null>(null);
  const [copiedSection, setCopiedSection] = useState<string | null>(null);
  const [submissionPhase, setSubmissionPhase] = useState<'idle' | 'uploading' | 'processing' | 'completed' | 'failed'>('idle');

  // Real-time server-synchronized countdown timer
  const [remainingSeconds, setRemainingSeconds] = useState<number | null>(null);

  useEffect(() => {
    if (!exam) return;

    const serverTimestamp = exam.serverTime ? new Date(exam.serverTime).getTime() : Date.now();
    const clientOffset = serverTimestamp - Date.now();
    const endTimestamp = exam.endTime ? new Date(exam.endTime).getTime() : 0;

    const updateTimer = () => {
      const currentServerTime = Date.now() + clientOffset;
      if (endTimestamp) {
        const diffSec = Math.max(0, Math.floor((endTimestamp - currentServerTime) / 1000));
        setRemainingSeconds(diffSec);
      }
    };

    updateTimer();
    const interval = setInterval(updateTimer, 1000);
    return () => clearInterval(interval);
  }, [exam]);

  // Exam Status & Accessibility Rules
  const isUpcoming = exam?.status === 'upcoming';
  const isExpiredByTimer = remainingSeconds !== null && remainingSeconds <= 0;
  const isDeadlinePassed = Boolean(exam?.isExpired) || (exam?.status !== 'active' && isExpiredByTimer);
  const isClosed = exam?.status === 'completed' || exam?.status === 'missed' || isDeadlinePassed || isUpcoming;
  const isLocked = isClosed;

  const is5MinWarning = !isClosed && !isUpcoming && remainingSeconds !== null && remainingSeconds <= 300 && remainingSeconds > 60;
  const is1MinWarning = !isClosed && !isUpcoming && remainingSeconds !== null && remainingSeconds <= 60 && remainingSeconds > 0;

  const formattedCountdown = useMemo(() => {
    if (remainingSeconds === null) return '--:--:--';
    if (remainingSeconds <= 0) return '00:00:00';
    const hours = Math.floor(remainingSeconds / 3600);
    const mins = Math.floor((remainingSeconds % 3600) / 60);
    const secs = remainingSeconds % 60;
    if (hours > 0) {
      return `${String(hours).padStart(2, '0')}:${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`;
    }
    return `${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`;
  }, [remainingSeconds]);

  const isPending = submitExamZip.isPending || submitExamBatchZip.isPending || uploadingQuestionId !== null;

  const validateZipFile = (file: File): string | null => {
    if (!file.name.toLowerCase().endsWith('.zip')) {
      return 'Chỉ chấp nhận tệp nén có định dạng .zip';
    }
    const maxBytes = 10 * 1024 * 1024; // 10 MiB
    if (file.size > maxBytes) {
      return `Dung lượng tệp (${(file.size / (1024 * 1024)).toFixed(1)} MB) vượt quá giới hạn tối đa 10 MB cho phép.`;
    }
    if (file.size === 0) {
      return 'Tệp ZIP nộp bài không được rỗng (0 bytes).';
    }
    return null;
  };

  // Map submissions by questionId to show current submission state per question
  const latestSubmissionByQuestion = useMemo(() => {
    const map: Record<string, SubmissionResultResponse> = {};
    if (!submissionsData || !Array.isArray(submissionsData)) return map;
    for (const sub of submissionsData) {
      if (sub.questionId && !map[sub.questionId]) {
        map[sub.questionId] = sub;
      }
    }
    return map;
  }, [submissionsData]);

  // Group all submissions by questionId for full resubmission history
  const submissionsByQuestion = useMemo(() => {
    const map: Record<string, SubmissionResultResponse[]> = {};
    if (!submissionsData || !Array.isArray(submissionsData)) return map;
    for (const sub of submissionsData) {
      if (sub.questionId) {
        if (!map[sub.questionId]) map[sub.questionId] = [];
        map[sub.questionId].push(sub);
      }
    }
    return map;
  }, [submissionsData]);

  const hasQuestions = Boolean(exam?.questions && exam.questions.length > 0);
  const questionsList = useMemo(() => exam?.questions || [], [exam?.questions]);
  const totalQuestionsCount = questionsList.length;
  const readyQuestionsCount = Object.keys(selectedZips).length;
  const isAllQuestionsReady = hasQuestions && readyQuestionsCount === totalQuestionsCount && totalQuestionsCount > 0;

  // Set default selected question
  useEffect(() => {
    if (questionsList.length > 0 && !selectedQuestionId) {
      setSelectedQuestionId(questionsList[0].id);
    }
  }, [questionsList, selectedQuestionId]);

  // Selected question context for active question and sticky footer
  const selectedQuestion = useMemo(() => {
    if (!questionsList || questionsList.length === 0) return null;
    return questionsList.find((q) => q.id === selectedQuestionId) || questionsList[0];
  }, [questionsList, selectedQuestionId]);

  const selectedQuestionIdx = useMemo(() => {
    if (!selectedQuestion) return 0;
    const idx = questionsList.findIndex((q) => q.id === selectedQuestion.id);
    return idx >= 0 ? idx : 0;
  }, [questionsList, selectedQuestion]);

  const selectedQuestionNumber = selectedQuestionIdx + 1;

  // Informational count: how many questions have at least one submission recorded
  const submittedQuestionsCount = useMemo(() => {
    if (!hasQuestions) return 0;
    return questionsList.filter((q) => Boolean(latestSubmissionByQuestion[q.id])).length;
  }, [hasQuestions, questionsList, latestSubmissionByQuestion]);

  const remainingQuestionsCount = Math.max(0, totalQuestionsCount - submittedQuestionsCount);

  // Fallback question description resolver
  const getQuestionDescription = (question: ExamQuestionResponse, examObj: ExamResponse, totalQCount: number): string => {
    const qDesc = question.description?.trim();
    if (qDesc) return qDesc;
    const qProb = (question as any).problemStatement?.trim();
    if (qProb) return qProb;
    const qContent = (question as any).content?.trim();
    if (qContent) return qContent;
    if (totalQCount === 1 && examObj.problemStatement?.trim()) {
      return examObj.problemStatement.trim();
    }
    if (totalQCount === 1 && examObj.description?.trim()) {
      return examObj.description.trim();
    }
    return '';
  };

  // Determine individual question status
  const getQuestionStatus = (questionId: string): QuestionStatus => {
    if (uploadingQuestionId === questionId) return 'UPLOADING';
    if (selectedZips[questionId]) return 'FILE_SELECTED';
    const sub = latestSubmissionByQuestion[questionId];
    if (sub) {
      if (sub.executionStatus === 'COMPLETED') return 'GRADED';
      if (
        sub.status === 'PROCESSING' ||
        sub.executionStatus === 'IN_PROGRESS' ||
        sub.executionStatus === 'DISPATCHED'
      ) {
        return 'PROCESSING';
      }
      if (
        sub.status === 'FAILED' ||
        sub.executionStatus === 'FAILED' ||
        sub.executionStatus === 'COMPILE_ERROR'
      ) {
        return 'FAILED';
      }
      return 'SUBMITTED';
    }
    return 'NOT_SUBMITTED';
  };

  const renderStatusBadge = (status: QuestionStatus, sub?: SubmissionResultResponse, maxScore?: number) => {
    switch (status) {
      case 'GRADED':
        return (
          <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[10px] font-semibold bg-emerald-100 text-emerald-800 border border-emerald-300">
            <CheckCircle2 className="w-3 h-3 text-emerald-600" />
            <span>Đã chấm: {sub?.totalScore ?? sub?.autoScore ?? 0}{maxScore !== undefined ? `/${maxScore}` : ''} đ</span>
          </span>
        );
      case 'PROCESSING':
        return (
          <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[10px] font-semibold bg-amber-50 text-amber-800 border border-amber-300 animate-pulse">
            <Loader2 className="w-3 h-3 animate-spin text-amber-600" />
            <span>Đang chấm Sandbox</span>
          </span>
        );
      case 'SUBMITTED':
        return (
          <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[10px] font-semibold bg-blue-50 text-blue-800 border border-blue-300">
            <Check className="w-3 h-3 text-blue-600" />
            <span>Đã nộp bài</span>
          </span>
        );
      case 'UPLOADING':
        return (
          <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[10px] font-semibold bg-primary/10 text-primary border border-primary/30 animate-pulse">
            <Loader2 className="w-3 h-3 animate-spin text-primary" />
            <span>Đang nộp...</span>
          </span>
        );
      case 'FILE_SELECTED':
        return (
          <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[10px] font-semibold bg-sky-50 text-sky-800 border border-sky-300">
            <FileArchive className="w-3 h-3 text-sky-600" />
            <span>Đã chọn ZIP</span>
          </span>
        );
      case 'FAILED':
        return (
          <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[10px] font-semibold bg-rose-50 text-rose-800 border border-rose-300">
            <AlertCircle className="w-3 h-3 text-rose-600" />
            <span>{sub?.executionStatus === 'COMPILE_ERROR' ? 'Lỗi biên dịch' : 'Chấm thất bại'}</span>
          </span>
        );
      case 'NOT_SUBMITTED':
      default:
        return (
          <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[10px] font-semibold bg-slate-100 text-slate-600 border border-slate-200">
            <span>Chưa nộp</span>
          </span>
        );
    }
  };

  // Multi-Question Handlers
  const handleQuestionFileSelect = (questionId: string, file: File) => {
    setSelectedQuestionId(questionId);
    const err = validateZipFile(file);
    if (err) {
      setQuestionErrors((prev) => ({ ...prev, [questionId]: err }));
      toast.error(err);
      return;
    }
    setQuestionErrors((prev) => {
      const next = { ...prev };
      delete next[questionId];
      return next;
    });
    setSelectedZips((prev) => ({ ...prev, [questionId]: file }));
    setLocalError('');
    toast.success(`Đã chọn tệp: ${file.name} (${(file.size / (1024 * 1024)).toFixed(2)} MB)`);
  };

  const handleQuestionFileRemove = (questionId: string) => {
    setSelectedQuestionId(questionId);
    setSelectedZips((prev) => {
      const next = { ...prev };
      delete next[questionId];
      return next;
    });
    setQuestionErrors((prev) => {
      const next = { ...prev };
      delete next[questionId];
      return next;
    });
  };

  // INDEPENDENT PER-QUESTION SUBMISSION HANDLER
  const handleSingleQuestionSubmit = async (questionId: string) => {
    const file = selectedZips[questionId];
    if (!file) {
      toast.error('Vui lòng chọn hoặc kéo thả tệp ZIP trước khi nộp.');
      return;
    }
    if (isClosed || isUpcoming) {
      toast.error('Cổng nộp bài hiện đã đóng hoặc chưa mở.');
      return;
    }

    setUploadingQuestionId(questionId);
    setUploadProgress(0);

    try {
      const res = await submitExamZip.mutateAsync({
        examId,
        file,
        questionId,
        onUploadProgress: (pct) => setUploadProgress(pct),
      });

      toast.success(`Nộp thành công Câu hỏi (${res.originalFilename || file.name})! Đã chuyển bài làm vào Sandbox kiểm thử.`);
      // Clear the selected file from state since it is now submitted
      setSelectedZips((prev) => {
        const next = { ...prev };
        delete next[questionId];
        return next;
      });
      // Clear error on success
      setQuestionErrors((prev) => {
        const next = { ...prev };
        delete next[questionId];
        return next;
      });
      refetchSubmissions();
      refetchExam();
    } catch (err: unknown) {
      const axiosErr = err as AxiosError<{ message?: string; error?: string }>;
      let msg = axiosErr?.response?.data?.message || (err as Error)?.message || 'Nộp câu hỏi thất bại.';
      if (axiosErr?.response?.status === 409 || axiosErr?.response?.data?.error === 'EXAM_TIME_EXPIRED' || msg === 'EXAM_TIME_EXPIRED') {
        msg = t('errors.EXAM_TIME_EXPIRED') || 'Thời gian nộp bài đã kết thúc.';
      }
      setQuestionErrors((prev) => ({ ...prev, [questionId]: msg }));
      toast.error(msg);
      // NOTE: Preserving selectedZips[questionId] so student doesn't need to reselect the file on retry
    } finally {
      setUploadingQuestionId(null);
      setUploadProgress(0);
    }
  };

  // Legacy Single-Question Handlers
  const handleLegacyFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const err = validateZipFile(file);
    if (err) {
      setLocalError(err);
      toast.error(err);
      setLegacyZip(null);
      if (legacyFileInputRef.current) legacyFileInputRef.current.value = '';
      return;
    }
    setLocalError('');
    setLegacyZip(file);
    toast.success(`Đã chọn tệp: ${file.name} (${(file.size / (1024 * 1024)).toFixed(2)} MB)`);
    if (legacyFileInputRef.current) legacyFileInputRef.current.value = '';
  };

  // Batch Pre-Submit Handler
  const handlePreSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (isUpcoming) {
      const msg = `Bài thi chưa bắt đầu. Cổng nộp bài sẽ mở lúc ${exam?.startTime ? new Date(exam.startTime).toLocaleString('vi-VN') : ''}.`;
      setLocalError(msg);
      toast.error(msg);
      return;
    }
    if (isDeadlinePassed || exam?.status === 'completed' || exam?.status === 'missed') {
      const msg = `Thời gian làm bài đã kết thúc (${exam?.endTime ? new Date(exam.endTime).toLocaleString('vi-VN') : ''}). Cổng nộp bài đã đóng.`;
      setLocalError(msg);
      toast.error(msg);
      return;
    }

    if (hasQuestions) {
      const missingQuestions = questionsList.filter((q) => !selectedZips[q.id]);
      if (missingQuestions.length > 0) {
        const missingNames = missingQuestions.map((q) => {
          const qIdx = questionsList.findIndex((item) => item.id === q.id);
          return `Câu ${qIdx >= 0 ? qIdx + 1 : 1}: ${q.title}`;
        }).join(', ');
        const msg = `Vui lòng chọn tệp ZIP cho tất cả ${totalQuestionsCount} câu hỏi trước khi nộp. Còn thiếu: ${missingNames}`;
        setLocalError(msg);
        toast.error(msg);
        return;
      }
    } else {
      if (!legacyZip) {
        setLocalError('Vui lòng chọn hoặc kéo thả tệp .zip trước khi nộp.');
        return;
      }
      const err = validateZipFile(legacyZip);
      if (err) {
        setLocalError(err);
        return;
      }
    }

    setLocalError('');
    setShowConfirmModal(true);
  };

  const handleFinalSubmit = () => {
    setShowConfirmModal(false);
    setUploadProgress(0);
    setSubmissionPhase('uploading');

    if (hasQuestions) {
      // Multi-Question Batch Submission
      const questionIds: string[] = [];
      const files: File[] = [];

      for (const q of questionsList) {
        const file = selectedZips[q.id];
        if (!file) {
          toast.error(`Thiếu tệp cho câu hỏi ${q.title}`);
          setSubmissionPhase('failed');
          return;
        }
        questionIds.push(q.id);
        files.push(file);
      }

      submitExamBatchZip.mutate(
        {
          examId,
          questionIds,
          files,
          onUploadProgress: (pct) => setUploadProgress(pct),
        },
        {
          onSuccess: (data: SubmissionResultResponse[]) => {
            setSubmissionPhase('processing');
            toast.success(`Đã nộp thành công ${data.length} tệp ZIP cho tất cả các câu hỏi!`);
            setBatchResults(data);
            setSelectedZips({});
            refetchSubmissions();
            refetchExam();
            setTimeout(() => {
              setSubmissionPhase('completed');
            }, 1000);
          },
          onError: (err: unknown) => {
            setSubmissionPhase('failed');
            const axiosErr = err as AxiosError<{ message?: string; error?: string }>;
            let msg = axiosErr?.response?.data?.message || (err as Error)?.message || 'Nộp bài thi thất bại.';
            if (axiosErr?.response?.status === 409 || axiosErr?.response?.data?.error === 'EXAM_TIME_EXPIRED' || msg === 'EXAM_TIME_EXPIRED') {
              msg = t('errors.EXAM_TIME_EXPIRED') || 'Thời gian nộp bài đã kết thúc.';
            }
            setLocalError(msg);
            toast.error(msg);
          },
        }
      );
    } else {
      // Legacy Single-Question Submission
      if (!legacyZip) return;
      submitExamZip.mutate(
        {
          examId,
          file: legacyZip,
          onUploadProgress: (pct) => setUploadProgress(pct),
        },
        {
          onSuccess: (data: SubmissionResultResponse) => {
            setSubmissionPhase('processing');
            toast.success(`Nộp tệp ${data.originalFilename || legacyZip.name} thành công! Đã chuyển vào Sandbox kiểm thử.`);
            setSubmissionResult(data);
            refetchSubmissions();
            refetchExam();
            setTimeout(() => {
              setSubmissionPhase('completed');
              if (data?.id) {
                router.push(`/student/submissions/${data.id}`);
              }
            }, 1000);
          },
          onError: (err: unknown) => {
            setSubmissionPhase('failed');
            const axiosErr = err as AxiosError<{ message?: string; error?: string }>;
            let msg = axiosErr?.response?.data?.message || (err as Error)?.message || 'Nộp tệp ZIP thất bại.';
            if (axiosErr?.response?.status === 409 || axiosErr?.response?.data?.error === 'EXAM_TIME_EXPIRED' || msg === 'EXAM_TIME_EXPIRED') {
              msg = t('errors.EXAM_TIME_EXPIRED') || 'Thời gian nộp bài đã kết thúc.';
            }
            setLocalError(msg);
            toast.error(msg);
          },
        }
      );
    }
  };

  const copyToClipboard = (text: string, sectionKey: string) => {
    navigator.clipboard.writeText(text);
    setCopiedSection(sectionKey);
    setTimeout(() => setCopiedSection(null), 2000);
  };

  if (isLoading) {
    return (
      <div className="flex flex-col h-72 items-center justify-center gap-3">
        <div className="w-8 h-8 rounded-full border-2 border-primary border-t-transparent animate-spin" />
        <p className="text-on-surface-variant font-label-md text-xs">{t('common.loading')}</p>
      </div>
    );
  }

  if (isError) {
    const status = (error as AxiosError)?.response?.status;
    if (status === 403) {
      return (
        <div className="max-w-xl mx-auto my-12 p-8 rounded-2xl bg-surface-container-lowest border border-outline-variant/40 shadow-lg text-center flex flex-col items-center gap-3">
          <div className="p-3 rounded-full bg-rose-50 text-rose-600">
            <Lock className="w-8 h-8" />
          </div>
          <h2 className="font-headline-md text-headline-md text-on-surface">{t('studentExams.accessDenied')}</h2>
          <p className="font-body-md text-on-surface-variant text-xs">{t('studentExams.accessDenied')}</p>
          <Link href="/student/exams" className="mt-2 px-4 py-2 bg-primary text-on-primary rounded-lg text-xs font-medium">
            {t('common.back')}
          </Link>
        </div>
      );
    }
    if (status === 404) {
      return (
        <div className="max-w-xl mx-auto my-12 p-8 rounded-2xl bg-surface-container-lowest border border-outline-variant/40 shadow-lg text-center flex flex-col items-center gap-3">
          <div className="p-3 rounded-full bg-amber-50 text-amber-600">
            <AlertCircle className="w-8 h-8" />
          </div>
          <h2 className="font-headline-md text-headline-md text-on-surface">{t('studentExams.examNotFound')}</h2>
          <p className="font-body-md text-on-surface-variant text-xs">{t('studentExams.examNotFound')}</p>
          <Link href="/student/exams" className="mt-2 px-4 py-2 bg-primary text-on-primary rounded-lg text-xs font-medium">
            {t('common.back')}
          </Link>
        </div>
      );
    }
    return (
      <div className="max-w-xl mx-auto my-12 p-8 rounded-2xl bg-surface-container-lowest border border-rose-200/60 shadow-lg text-center flex flex-col items-center gap-3">
        <div className="p-3 rounded-full bg-rose-50 text-rose-600">
          <AlertCircle className="w-8 h-8" />
        </div>
        <h2 className="font-headline-md text-headline-md text-on-surface">{t('common.error')}</h2>
        <p className="font-body-md text-on-surface-variant text-xs">{(error as Error).message}</p>
        <button onClick={() => window.location.reload()} className="mt-2 px-4 py-2 bg-primary text-on-primary rounded-lg text-xs font-medium cursor-pointer">
          {t('common.refresh')}
        </button>
      </div>
    );
  }

  if (!exam) return null;

  return (
    <div className="max-w-7xl mx-auto px-space-lg py-space-lg">
      <div className="flex flex-col w-full space-y-space-lg">
        {/* Header Breadcrumbs & Status Bar */}
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-space-md">
          <div className="space-y-1.5">
            <nav className="flex items-center gap-2 text-on-surface-variant text-xs">
              <Link className="hover:text-primary transition-colors flex items-center gap-1 font-medium" href="/student/exams">
                <ArrowLeft className="w-3.5 h-3.5" />
                <span>{t('nav.myExams')}</span>
              </Link>
              <span className="text-outline-variant">/</span>
              <span className="text-primary font-semibold truncate max-w-[200px]">{exam.title}</span>
            </nav>

            <div className="flex flex-wrap items-center gap-3">
              <h1 className="font-headline-lg text-headline-lg text-on-surface tracking-tight">{exam.title}</h1>
              <div
                data-testid="student-exam-status-badge"
                className={`flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-semibold ${
                  !isClosed && exam.status === 'active'
                    ? 'bg-emerald-50 text-emerald-700 border border-emerald-200'
                    : isUpcoming
                    ? 'bg-blue-50 text-blue-700 border border-blue-200'
                    : 'bg-rose-50 text-rose-700 border border-rose-200'
                }`}
              >
                {!isClosed && exam.status === 'active' && (
                  <span className="relative flex h-2 w-2">
                    <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75"></span>
                    <span className="relative inline-flex rounded-full h-2 w-2 bg-emerald-500"></span>
                  </span>
                )}
                <span>
                  {isUpcoming
                    ? 'SẮP DIỄN RA'
                    : isDeadlinePassed
                    ? (t('common.archived') || 'ĐÃ ĐÓNG')
                    : exam.status === 'active'
                    ? (t('common.active') || 'ĐANG MỞ')
                    : exam.status.toUpperCase()}
                </span>
              </div>
            </div>

            <div className="flex flex-wrap items-center gap-2 pt-1 text-xs text-on-surface-variant">
              <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded bg-surface-container font-mono font-medium text-on-surface border border-outline-variant/30">
                <BookOpen className="w-3.5 h-3.5 text-primary" />
                <span>{t('teacherExams.tableClass')}: {exam.courseClassCode || 'SE-PRACTICAL'}</span>
              </span>
              <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded bg-surface-container-high text-on-surface border border-outline-variant/30">
                <Clock className="w-3.5 h-3.5 text-outline" />
                <span>Hạn chót: {exam.endTime ? new Date(exam.endTime).toLocaleString('vi-VN') : '--'}</span>
                {isDeadlinePassed && <span className="text-rose-600 font-semibold ml-1">(Đã kết thúc)</span>}
                {isUpcoming && <span className="text-blue-600 font-semibold ml-1">(Chưa bắt đầu)</span>}
              </span>
              {hasQuestions && (
                <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded bg-purple-50 text-purple-700 font-semibold border border-purple-200">
                  <span>{totalQuestionsCount} câu hỏi</span>
                  {exam.totalAllocatedPoints !== undefined && <span>• {exam.totalAllocatedPoints} điểm</span>}
                </span>
              )}
            </div>
          </div>

          {/* Real-Time Countdown Timer Widget */}
          <div className="flex items-center self-start md:self-center">
            <div className={`px-4 py-2 rounded-xl border flex items-center gap-3 shadow-xs transition-all ${
              isClosed
                ? 'bg-rose-50 border-rose-200 text-rose-700'
                : is1MinWarning
                ? 'bg-rose-100 border-rose-300 text-rose-800 animate-pulse'
                : is5MinWarning
                ? 'bg-amber-50 border-amber-300 text-amber-800'
                : 'bg-surface-container-lowest border-outline-variant/40 text-on-surface'
            }`}>
              <div className={`p-1.5 rounded-lg ${isClosed ? 'bg-rose-100 text-rose-600' : is1MinWarning ? 'bg-rose-200 text-rose-700' : 'bg-primary/10 text-primary'}`}>
                {isClosed ? <Lock className="w-4 h-4" /> : <Timer className="w-4 h-4" />}
              </div>
              <div className="flex flex-col">
                <span className="text-[10px] uppercase font-mono tracking-wider font-semibold opacity-75">
                  {t('studentExams.remainingTime') || 'Thời gian còn lại'}
                </span>
                <span className="font-mono text-base font-bold tracking-tight">
                  {isClosed ? '00:00:00' : formattedCountdown}
                </span>
              </div>
            </div>
          </div>
        </div>

        {/* 1-Minute Urgent Warning Banner */}
        {is1MinWarning && (
          <div className="p-3.5 rounded-xl bg-rose-50 border border-rose-300 text-rose-900 text-xs flex items-center gap-3 animate-pulse shadow-sm">
            <AlertTriangle className="w-5 h-5 text-rose-600 shrink-0" />
            <div className="flex-1">
              <p className="font-bold">{t('studentExams.warning1min') || 'Cảnh báo khẩn cấp: Chỉ còn ít hơn 1 phút trước khi hệ thống khóa cổng nộp bài!'}</p>
            </div>
          </div>
        )}

        {/* 5-Minute Warning Banner */}
        {is5MinWarning && (
          <div className="p-3.5 rounded-xl bg-amber-50 border border-amber-300 text-amber-900 text-xs flex items-center gap-3 shadow-xs">
            <AlertCircle className="w-5 h-5 text-amber-600 shrink-0" />
            <div className="flex-1">
              <p className="font-semibold">{t('studentExams.warning5min') || 'Chú ý: Còn dưới 5 phút. Vui lòng nhanh chóng nộp bài thi để tránh quá hạn.'}</p>
            </div>
          </div>
        )}

        {/* Upcoming Exam Alert Banner */}
        {isUpcoming && (
          <div className="p-4 rounded-xl bg-blue-50 border border-blue-200 text-blue-800 text-xs flex items-center gap-3">
            <Clock className="w-5 h-5 text-blue-600 shrink-0" />
            <div>
              <p className="font-semibold">Bài thi chưa mở cổng làm bài</p>
              <p className="text-blue-700 mt-0.5">
                Bài thi này sẽ bắt đầu vào lúc {exam.startTime ? new Date(exam.startTime).toLocaleString('vi-VN') : 'Sắp diễn ra'}. Bạn chỉ có thể nộp bài khi thời gian bắt đầu.
              </p>
            </div>
          </div>
        )}

        {/* Closed/Expired Exam Alert Banner */}
        {isLocked && !isUpcoming && (
          <div className="p-4 rounded-xl bg-rose-50 border border-rose-200 text-rose-800 text-xs flex items-center gap-3 shadow-sm">
            <Lock className="w-5 h-5 text-rose-600 shrink-0" />
            <div>
              <p className="font-bold text-sm">{t('studentExams.timeExpiredTitle') || 'Thời gian làm bài đã kết thúc'}</p>
              <p className="text-rose-700 mt-0.5">{t('studentExams.timeExpiredDesc') || 'Hạn nộp bài thi này đã qua. Hệ thống đã đóng cổng nhận bài.'}</p>
            </div>
          </div>
        )}

        {/* Overall Description Card (if single-question or high-level instructions) */}
        {exam.description && !hasQuestions && (
          <div className="p-space-md rounded-xl bg-surface-container-lowest border border-outline-variant/40 shadow-xs">
            <h3 className="font-label-sm font-semibold text-outline uppercase tracking-wider mb-1">Đề bài &amp; Yêu cầu kỹ thuật</h3>
            <p className="text-xs text-on-surface leading-relaxed whitespace-pre-wrap">{exam.description}</p>
          </div>
        )}

        {/* MULTI-QUESTION EXAM WORKSPACE */}
        {hasQuestions ? (
          <div className="flex flex-col gap-6">
            {/* Overall Informational Progress Banner (strictly informational, never blocking) */}
            <div className="p-4 rounded-2xl bg-surface-container-lowest border border-outline-variant/40 shadow-xs flex flex-col md:flex-row md:items-center justify-between gap-4">
              <div className="flex flex-col gap-2 flex-1">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="text-xs font-bold text-on-surface">Tiến độ nộp bài thi:</span>
                  <span
                    className={`font-mono text-xs font-bold px-2.5 py-0.5 rounded-full ${
                      submittedQuestionsCount === totalQuestionsCount && totalQuestionsCount > 0
                        ? 'bg-emerald-100 text-emerald-800 border border-emerald-300'
                        : 'bg-primary/10 text-primary border border-primary/20'
                    }`}
                  >
                    {submittedQuestionsCount} / {totalQuestionsCount} câu hỏi đã nộp
                  </span>
                  {remainingQuestionsCount > 0 ? (
                    <span className="text-[11px] font-mono text-outline">
                      ({remainingQuestionsCount} câu chưa nộp)
                    </span>
                  ) : (
                    <span className="text-[11px] font-bold text-emerald-700 bg-emerald-50 px-2 py-0.5 rounded border border-emerald-200">
                      ✓ Đã hoàn thành nộp đủ tất cả câu hỏi
                    </span>
                  )}
                  <span
                    data-testid="ready-questions-counter"
                    className="font-mono text-xs font-bold px-2.5 py-0.5 rounded-full bg-surface-container text-on-surface-variant border border-outline-variant/30"
                  >
                    {readyQuestionsCount} / {totalQuestionsCount} câu hỏi đã sẵn sàng nộp
                  </span>
                </div>
                <div className="w-full max-w-lg h-2 rounded-full bg-surface-container overflow-hidden">
                  <div
                    className="h-full rounded-full bg-emerald-600 transition-all duration-300"
                    style={{ width: `${(submittedQuestionsCount / Math.max(totalQuestionsCount, 1)) * 100}%` }}
                  />
                </div>
                <p className="text-[11px] text-outline">
                  * Cơ chế nộp bài độc lập: Bạn có thể nộp hoặc cập nhật từng câu hỏi riêng biệt bất kỳ lúc nào trước khi hết giờ mà không cần chờ câu khác.
                </p>
              </div>

              <button
                type="button"
                onClick={() => {
                  refetchSubmissions();
                  refetchExam();
                  toast.success('Đã làm mới trạng thái bài nộp');
                }}
                className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl border border-outline-variant/40 text-xs font-medium text-on-surface-variant hover:bg-surface-container transition-colors cursor-pointer self-start md:self-center"
              >
                <RefreshCw className="w-3.5 h-3.5" />
                <span>Làm mới kết quả</span>
              </button>
            </div>

            {/* Question Navigator */}
            <div className="p-4 rounded-2xl bg-surface-container-lowest border border-outline-variant/40 shadow-xs flex flex-col gap-3">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <Code2 className="w-4 h-4 text-primary" />
                  <span className="text-xs font-bold text-on-surface uppercase tracking-wider">
                    Điều hướng câu hỏi ({totalQuestionsCount} câu)
                  </span>
                </div>
                <span className="text-[11px] text-outline font-medium">
                  Bấm vào câu hỏi để chọn và cuộn nhanh đến vị trí làm bài
                </span>
              </div>

              <div className="flex flex-wrap gap-2.5">
                {questionsList.map((q, idx) => {
                  const status = getQuestionStatus(q.id);
                  const sub = latestSubmissionByQuestion[q.id];
                  const isSelected = selectedQuestionId === q.id;
                  const qNum = idx + 1;
                  return (
                    <button
                      key={q.id || idx}
                      id={`nav-question-${q.id}`}
                      data-testid={`nav-question-${q.id}`}
                      type="button"
                      onClick={() => {
                        setSelectedQuestionId(q.id);
                        const el = document.getElementById(`question-card-${q.id}`);
                        el?.scrollIntoView({ behavior: 'smooth', block: 'center' });
                      }}
                      className={`flex items-center gap-2 px-3.5 py-2 rounded-xl text-xs font-medium border transition-all cursor-pointer shadow-xs ${
                        isSelected
                          ? 'ring-2 ring-primary border-primary bg-primary/10 font-bold'
                          : status === 'GRADED'
                          ? 'bg-emerald-50/90 border-emerald-300 text-emerald-800 hover:bg-emerald-100'
                          : status === 'PROCESSING' || status === 'SUBMITTED'
                          ? 'bg-blue-50/90 border-blue-300 text-blue-800 hover:bg-blue-100'
                          : status === 'FILE_SELECTED'
                          ? 'bg-sky-50/90 border-sky-300 text-sky-800 hover:bg-sky-100'
                          : status === 'FAILED'
                          ? 'bg-rose-50/90 border-rose-300 text-rose-800 hover:bg-rose-100'
                          : 'bg-surface-container-low border-outline-variant/40 text-on-surface hover:bg-surface-container'
                      }`}
                    >
                      <span className="font-bold">Câu {qNum}</span>
                      <span className="text-[11px] opacity-75 font-mono">({q.maxScore} đ)</span>
                      {renderStatusBadge(status, sub, q.maxScore)}
                    </button>
                  );
                })}
              </div>
            </div>

            {/* Questions List with Independent Submission per Question */}
            <div className="flex flex-col gap-6">
              {questionsList.map((question, idx) => {
                const qNum = idx + 1;
                const questionFile = selectedZips[question.id];
                const questionErr = questionErrors[question.id];
                const isDragging = draggingQuestionId === question.id;
                const prevSubmission = latestSubmissionByQuestion[question.id];
                const questionHistory = submissionsByQuestion[question.id] || [];
                const status = getQuestionStatus(question.id);
                const isCurrentlyUploading = uploadingQuestionId === question.id;
                const isSelected = selectedQuestionId === question.id;
                const questionDesc = getQuestionDescription(question, exam, totalQuestionsCount);

                const lang = (question as any).programmingLanguage || exam.programmingLanguage || 'Java';
                const inputSpec = (question as any).inputSpecification?.trim() || (totalQuestionsCount === 1 ? exam.inputSpecification?.trim() : undefined);
                const outputSpec = (question as any).outputSpecification?.trim() || (totalQuestionsCount === 1 ? exam.outputSpecification?.trim() : undefined);
                const constraints = (question as any).constraints?.trim() || (totalQuestionsCount === 1 ? exam.constraints?.trim() : undefined);
                const sampleInput = (question as any).sampleInput?.trim() || (totalQuestionsCount === 1 ? exam.sampleInput?.trim() : undefined);
                const sampleOutput = (question as any).sampleOutput?.trim() || (totalQuestionsCount === 1 ? exam.sampleOutput?.trim() : undefined);
                const hasSpecs = Boolean(inputSpec || outputSpec || constraints || sampleInput || sampleOutput);

                return (
                  <div
                    key={question.id || idx}
                    id={`question-card-${question.id}`}
                    data-testid={`question-card-${question.id}`}
                    onClick={() => setSelectedQuestionId(question.id)}
                    className={`p-6 rounded-2xl bg-surface-container-lowest border transition-all flex flex-col gap-5 scroll-mt-20 ${
                      isSelected
                        ? 'border-primary ring-2 ring-primary/20 shadow-md'
                        : 'border-outline-variant/40 shadow-xs'
                    }`}
                  >
                    {/* Question Header */}
                    <div className="flex items-center justify-between pb-3 border-b border-outline-variant/20 flex-wrap gap-2">
                      <div className="flex items-center gap-3">
                        <span className="w-8 h-8 rounded-xl bg-primary/10 text-primary font-mono font-bold flex items-center justify-center text-sm shadow-2xs">
                          {idx + 1}
                        </span>
                        <div>
                          <h3 className="font-semibold text-sm text-on-surface">
                            {formatQuestionTitle(question.title)}
                          </h3>
                        </div>
                      </div>
                      <div className="flex items-center gap-2.5">
                        <span className="font-mono font-bold text-xs text-emerald-700 bg-emerald-50 px-3 py-1 rounded-full border border-emerald-200">
                          {question.maxScore} điểm
                        </span>
                        {renderStatusBadge(status, prevSubmission, question.maxScore)}
                      </div>
                    </div>

                    {/* Question Description / Problem Statement */}
                    <div className="flex flex-col gap-2">
                      <div className="flex items-center justify-between">
                        <span className="text-[11px] font-semibold text-outline uppercase tracking-wider">
                          Đề bài &amp; Yêu cầu kỹ thuật:
                        </span>
                        <span className="font-mono text-[11px] text-on-surface-variant bg-surface-container px-2 py-0.5 rounded border border-outline-variant/20">
                          Ngôn ngữ: {lang}
                        </span>
                      </div>
                      {questionDesc ? (
                        <div className="text-xs text-on-surface leading-relaxed whitespace-pre-wrap bg-surface-container-low/40 p-4 rounded-xl border border-outline-variant/20 font-sans">
                          {questionDesc}
                        </div>
                      ) : (
                        <div className="p-3.5 rounded-xl bg-amber-50/70 border border-amber-200/80 text-amber-800 text-xs flex items-center gap-2">
                          <AlertCircle className="w-4 h-4 text-amber-600 shrink-0" />
                          <span>Giảng viên chưa cung cấp nội dung mô tả chi tiết cho câu hỏi này.</span>
                        </div>
                      )}
                    </div>

                    {/* Technical Specifications & Constraints */}
                    {hasSpecs && (
                      <div className="p-4 rounded-xl bg-surface-container-low/50 border border-outline-variant/20 flex flex-col gap-3">
                        <span className="text-[11px] font-semibold text-outline uppercase tracking-wider">
                          Quy cách kỹ thuật &amp; Ràng buộc:
                        </span>

                        <div className="grid grid-cols-1 md:grid-cols-2 gap-3 text-xs">
                          {inputSpec && (
                            <div className="flex flex-col gap-1">
                              <span className="font-semibold text-[11px] text-on-surface">Định dạng đầu vào (Input):</span>
                              <div className="p-2.5 rounded-lg bg-surface-container-lowest border border-outline-variant/20 text-[11px] text-on-surface-variant whitespace-pre-wrap font-mono">
                                {inputSpec}
                              </div>
                            </div>
                          )}

                          {outputSpec && (
                            <div className="flex flex-col gap-1">
                              <span className="font-semibold text-[11px] text-on-surface">Định dạng đầu ra (Output):</span>
                              <div className="p-2.5 rounded-lg bg-surface-container-lowest border border-outline-variant/20 text-[11px] text-on-surface-variant whitespace-pre-wrap font-mono">
                                {outputSpec}
                              </div>
                            </div>
                          )}

                          {constraints && (
                            <div className="flex flex-col gap-1 md:col-span-2">
                              <span className="font-semibold text-[11px] text-on-surface">Ràng buộc (Constraints):</span>
                              <div className="p-2.5 rounded-lg bg-surface-container-lowest border border-outline-variant/20 text-[11px] text-on-surface-variant whitespace-pre-wrap font-mono">
                                {constraints}
                              </div>
                            </div>
                          )}

                          {sampleInput && (
                            <div className="flex flex-col gap-1">
                              <div className="flex items-center justify-between">
                                <span className="font-semibold text-[11px] text-on-surface">Ví dụ Input:</span>
                                <button
                                  type="button"
                                  onClick={() => copyToClipboard(sampleInput, `sample-in-${question.id}`)}
                                  className="text-[10px] text-primary hover:underline flex items-center gap-1 cursor-pointer"
                                >
                                  {copiedSection === `sample-in-${question.id}` ? <Check className="w-3 h-3 text-emerald-600" /> : <Copy className="w-3 h-3" />}
                                  <span>{copiedSection === `sample-in-${question.id}` ? 'Đã sao chép' : 'Sao chép'}</span>
                                </button>
                              </div>
                              <pre className="p-2.5 rounded-lg bg-surface-container-lowest border border-outline-variant/20 text-[11px] text-on-surface font-mono overflow-x-auto whitespace-pre-wrap">
                                {sampleInput}
                              </pre>
                            </div>
                          )}

                          {sampleOutput && (
                            <div className="flex flex-col gap-1">
                              <div className="flex items-center justify-between">
                                <span className="font-semibold text-[11px] text-on-surface">Ví dụ Output:</span>
                                <button
                                  type="button"
                                  onClick={() => copyToClipboard(sampleOutput, `sample-out-${question.id}`)}
                                  className="text-[10px] text-primary hover:underline flex items-center gap-1 cursor-pointer"
                                >
                                  {copiedSection === `sample-out-${question.id}` ? <Check className="w-3 h-3 text-emerald-600" /> : <Copy className="w-3 h-3" />}
                                  <span>{copiedSection === `sample-out-${question.id}` ? 'Đã sao chép' : 'Sao chép'}</span>
                                </button>
                              </div>
                              <pre className="p-2.5 rounded-lg bg-surface-container-lowest border border-outline-variant/20 text-[11px] text-on-surface font-mono overflow-x-auto whitespace-pre-wrap">
                                {sampleOutput}
                              </pre>
                            </div>
                          )}
                        </div>
                      </div>
                    )}

                    {/* Question Public Test Cases (Safe Isolation - Never Leak Hidden Cases) */}
                    {question.testCases && question.testCases.length > 0 && (() => {
                      const publicCases = question.testCases.filter((tc) => !tc.isHidden);
                      const hiddenCount = question.testCases.filter((tc) => tc.isHidden).length;

                      return (
                        <div className="flex flex-col gap-2 pt-1">
                          <div className="flex items-center justify-between">
                            <span className="text-[11px] font-semibold text-outline uppercase tracking-wider flex items-center gap-1">
                              <CheckCircle2 className="w-3.5 h-3.5 text-primary" />
                              <span>Test Cases ({publicCases.length} công khai)</span>
                            </span>
                            {hiddenCount > 0 && (
                              <span className="text-[10px] text-outline font-mono flex items-center gap-1">
                                <Lock className="w-3 h-3 text-outline" />
                                <span>+{hiddenCount} test case ẩn bảo mật</span>
                              </span>
                            )}
                          </div>

                          {publicCases.length > 0 ? (
                            <div className="grid grid-cols-1 md:grid-cols-2 gap-2.5 text-xs">
                              {publicCases.map((tc, tcIdx) => (
                                <div key={tc.id || tcIdx} className="p-3 rounded-xl bg-surface-container-low border border-outline-variant/20 flex flex-col gap-1.5">
                                  <div className="flex items-center justify-between">
                                    <span className="font-mono font-semibold text-on-surface text-[11px]">{tc.name}</span>
                                    <span className="font-mono font-bold text-primary text-[11px] bg-primary/10 px-2 py-0.5 rounded">{tc.points} đ</span>
                                  </div>
                                  {tc.description && (
                                    <p className="text-[11px] text-outline">{tc.description}</p>
                                  )}
                                  {tc.inputData && (
                                    <div className="font-mono text-[10px] text-on-surface-variant bg-surface-container-lowest p-2 rounded border border-outline-variant/20">
                                      <span className="text-outline font-semibold">Input:</span>
                                      <pre className="whitespace-pre-wrap">{tc.inputData}</pre>
                                    </div>
                                  )}
                                  {tc.expectedOutput && (
                                    <div className="font-mono text-[10px] text-on-surface-variant bg-surface-container-lowest p-2 rounded border border-outline-variant/20">
                                      <span className="text-outline font-semibold">Expected Output:</span>
                                      <pre className="whitespace-pre-wrap">{tc.expectedOutput}</pre>
                                    </div>
                                  )}
                                </div>
                              ))}
                            </div>
                          ) : (
                            <p className="text-xs text-outline italic">Các test cases của câu hỏi này được giữ bảo mật và chỉ dùng để chấm điểm tự động.</p>
                          )}
                        </div>
                      );
                    })()}

                    {/* Previous Submission Info for THIS Question (if exists) */}
                    {prevSubmission && (
                      <div className="p-4 rounded-xl bg-surface-container-low/70 border border-outline-variant/30 flex flex-col gap-3">
                        <div className="flex items-center justify-between flex-wrap gap-2">
                          <div className="flex items-center gap-2">
                            <FileArchive className="w-4 h-4 text-primary" />
                            <span className="text-xs font-semibold text-on-surface">
                              Bài đã nộp gần nhất: <span className="font-mono text-primary font-bold">{prevSubmission.originalFilename || 'submission.zip'}</span>
                            </span>
                            {prevSubmission.createdAt && (
                              <span className="text-[11px] text-outline font-mono">
                                ({new Date(prevSubmission.createdAt).toLocaleString('vi-VN')})
                              </span>
                            )}
                          </div>

                          <div className="flex items-center gap-2 flex-wrap">
                            {prevSubmission.testCasesTotal !== undefined && prevSubmission.testCasesTotal > 0 && (
                              <span className="font-mono text-xs text-on-surface font-semibold bg-surface-container px-2 py-0.5 rounded">
                                {prevSubmission.testCasesPassed ?? 0}/{prevSubmission.testCasesTotal} test đạt
                              </span>
                            )}
                            <span className="font-mono font-bold text-xs text-emerald-800 bg-emerald-100 px-2.5 py-0.5 rounded border border-emerald-300">
                              {prevSubmission.totalScore ?? prevSubmission.autoScore ?? 0} / {question.maxScore} đ
                            </span>
                            <a
                              href={`/api/student/submissions/${prevSubmission.id}/download`}
                              download={prevSubmission.originalFilename || `submission-${prevSubmission.id}.zip`}
                              className="inline-flex items-center gap-1 text-[11px] text-outline hover:text-on-surface font-medium border border-outline-variant/30 px-2 py-0.5 rounded hover:bg-surface-container transition-colors"
                              title="Tải về tệp ZIP bài nộp gần nhất"
                            >
                              <Download className="w-3 h-3 text-primary" />
                              <span>Tải ZIP</span>
                            </a>
                            <Link
                              href={`/student/submissions/${prevSubmission.id}`}
                              className="inline-flex items-center gap-1 text-[11px] text-primary hover:underline font-medium ml-1"
                              title="Xem chi tiết kết quả nộp bài"
                            >
                              <span>Xem chi tiết</span>
                              <ExternalLink className="w-3 h-3" />
                            </Link>
                          </div>
                        </div>

                        {prevSubmission.compileOutput && prevSubmission.executionStatus === 'COMPILE_ERROR' && (
                          <div className="space-y-1 mt-1">
                            <span className="text-[10px] font-mono text-rose-700 font-bold uppercase">Lỗi biên dịch:</span>
                            <pre className="p-2 bg-slate-950 text-rose-300 rounded-lg font-mono text-[11px] overflow-x-auto max-h-24">
                              {prevSubmission.compileOutput}
                            </pre>
                          </div>
                        )}

                        {/* Collapsible Submission History for Resubmissions */}
                        {questionHistory.length > 1 && (
                          <div className="pt-2 border-t border-outline-variant/20">
                            <button
                              type="button"
                              onClick={(e) => {
                                e.stopPropagation();
                                setExpandedHistory((prev) => ({ ...prev, [question.id]: !prev[question.id] }));
                              }}
                              className="flex items-center gap-1.5 text-xs text-primary hover:underline font-medium cursor-pointer"
                            >
                              <Clock className="w-3.5 h-3.5" />
                              <span>Lịch sử nộp bài ({questionHistory.length} lần nộp)</span>
                              <ChevronDown className={`w-3.5 h-3.5 transition-transform duration-200 ${expandedHistory[question.id] ? 'rotate-180' : ''}`} />
                            </button>

                            {expandedHistory[question.id] && (
                              <div className="mt-2.5 overflow-x-auto">
                                <table className="w-full text-[11px] text-left border-collapse">
                                  <thead>
                                    <tr className="border-b border-outline-variant/30 text-outline">
                                      <th className="py-1.5 px-2 font-semibold">Lần nộp</th>
                                      <th className="py-1.5 px-2 font-semibold">Thời gian</th>
                                      <th className="py-1.5 px-2 font-semibold">Tệp</th>
                                      <th className="py-1.5 px-2 font-semibold">Trạng thái</th>
                                      <th className="py-1.5 px-2 font-semibold">Điểm số</th>
                                      <th className="py-1.5 px-2 font-semibold text-right">Chi tiết</th>
                                    </tr>
                                  </thead>
                                  <tbody className="divide-y divide-outline-variant/15 font-mono">
                                    {questionHistory.map((sub, hIdx) => {
                                      const attemptNum = questionHistory.length - hIdx;
                                      return (
                                        <tr key={sub.id || hIdx} className="hover:bg-surface-container/50">
                                          <td className="py-1.5 px-2 font-bold text-on-surface">#{attemptNum}</td>
                                          <td className="py-1.5 px-2 text-outline">
                                            {sub.createdAt ? new Date(sub.createdAt).toLocaleString('vi-VN') : '--'}
                                          </td>
                                          <td className="py-1.5 px-2 text-on-surface-variant truncate max-w-[140px]">
                                            {sub.originalFilename || 'submission.zip'}
                                          </td>
                                          <td className="py-1.5 px-2">
                                            <span className={`px-1.5 py-0.2 rounded text-[10px] font-semibold ${
                                              sub.executionStatus === 'COMPLETED'
                                                ? 'bg-emerald-100 text-emerald-800'
                                                : sub.executionStatus === 'COMPILE_ERROR'
                                                ? 'bg-rose-100 text-rose-800'
                                                : 'bg-amber-100 text-amber-800'
                                            }`}>
                                              {sub.executionStatus || 'SUBMITTED'}
                                            </span>
                                          </td>
                                          <td className="py-1.5 px-2 text-emerald-700 font-bold">
                                            {sub.totalScore ?? sub.autoScore ?? 0} đ
                                          </td>
                                          <td className="py-1.5 px-2 text-right">
                                            <Link
                                              href={`/student/submissions/${sub.id}`}
                                              className="text-primary hover:underline"
                                              onClick={(e) => e.stopPropagation()}
                                            >
                                              Xem
                                            </Link>
                                          </td>
                                        </tr>
                                      );
                                    })}
                                  </tbody>
                                </table>
                              </div>
                            )}
                          </div>
                        )}
                      </div>
                    )}

                    {/* Question Dedicated ZIP Upload Dropzone */}
                    <div className="flex flex-col gap-2 pt-2 border-t border-outline-variant/20">
                      <span
                        id={`upload-label-${question.id}`}
                        data-testid={`upload-label-${question.id}`}
                        className="text-xs font-semibold text-on-surface"
                      >
                        {prevSubmission ? `Cập nhật / Nộp lại tệp ZIP cho Câu ${qNum}:` : `Đính kèm tệp ZIP cho Câu ${qNum}:`}
                      </span>

                      <input
                        id={`file-input-${question.id}`}
                        data-testid={`file-input-${question.id}`}
                        type="file"
                        accept=".zip,application/zip,application/x-zip-compressed"
                        className="hidden"
                        onChange={(e) => {
                          const f = e.target.files?.[0];
                          if (f) handleQuestionFileSelect(question.id, f);
                          e.target.value = '';
                        }}
                        disabled={isPending || isClosed || isUpcoming}
                      />

                      {!questionFile ? (
                        <div
                          data-testid={`dropzone-${question.id}`}
                          onDragOver={(e) => {
                            e.preventDefault();
                            e.stopPropagation();
                            if (!isPending && !isClosed && !isUpcoming) {
                              setDraggingQuestionId(question.id);
                              setSelectedQuestionId(question.id);
                            }
                          }}
                          onDragLeave={(e) => {
                            e.preventDefault();
                            e.stopPropagation();
                            setDraggingQuestionId(null);
                          }}
                          onDrop={(e) => {
                            e.preventDefault();
                            e.stopPropagation();
                            setDraggingQuestionId(null);
                            if (isPending || isClosed || isUpcoming) return;
                            const f = e.dataTransfer.files?.[0];
                            if (f) handleQuestionFileSelect(question.id, f);
                          }}
                          onClick={() => {
                            if (!isPending && !isClosed && !isUpcoming) {
                              setSelectedQuestionId(question.id);
                              document.getElementById(`file-input-${question.id}`)?.click();
                            }
                          }}
                          className={`border-2 border-dashed rounded-xl p-5 text-center flex flex-col items-center justify-center gap-2 transition-all cursor-pointer ${
                            isDragging
                              ? 'border-primary bg-primary/10 scale-[0.99]'
                              : 'border-outline-variant/60 hover:border-primary/80 hover:bg-surface-container-low/50 bg-surface-container-low/20'
                          }`}
                        >
                          <UploadCloud className="w-6 h-6 text-primary pointer-events-none" />
                          <p className="font-semibold text-xs text-on-surface pointer-events-none">
                            Kéo &amp; thả tệp .zip hoặc <span className="text-primary underline">bấm để chọn</span> cho Câu {qNum}
                          </p>
                          <span className="text-[10px] text-outline font-mono pointer-events-none">
                            Chỉ chấp nhận .zip • Tối đa 10 MB
                          </span>
                        </div>
                      ) : (
                        <div className="border border-emerald-300 rounded-xl p-4 bg-emerald-50/40 flex items-center justify-between gap-3">
                          <div className="flex items-center gap-3">
                            <div className="p-2.5 rounded-lg bg-emerald-100 text-emerald-700">
                              <FileArchive className="w-5 h-5" />
                            </div>
                            <div>
                              <div className="flex items-center gap-2">
                                <h4 className="font-semibold text-xs text-on-surface font-mono">{questionFile.name}</h4>
                                <span className="px-2 py-0.2 rounded-full text-[10px] font-semibold bg-emerald-100 text-emerald-800 border border-emerald-300">
                                  Hợp lệ
                                </span>
                              </div>
                              <p className="text-[11px] text-outline font-mono mt-0.5">
                                {(questionFile.size / (1024 * 1024)).toFixed(2)} MB / 10 MB
                              </p>
                            </div>
                          </div>

                          <div className="flex items-center gap-2">
                            <button
                              type="button"
                              onClick={() => {
                                setSelectedQuestionId(question.id);
                                document.getElementById(`file-input-${question.id}`)?.click();
                              }}
                              disabled={isPending || isClosed || isUpcoming}
                              className="px-3 py-1 rounded-lg border border-outline-variant/40 hover:bg-surface-container text-xs transition-colors cursor-pointer"
                            >
                              Đổi tệp
                            </button>
                            <button
                              type="button"
                              onClick={() => handleQuestionFileRemove(question.id)}
                              disabled={isPending || isClosed || isUpcoming}
                              className="p-1 rounded-lg text-rose-600 hover:bg-rose-50 border border-rose-200 transition-colors cursor-pointer"
                              title="Xóa tệp"
                            >
                              <Trash2 className="w-3.5 h-3.5" />
                            </button>
                          </div>
                        </div>
                      )}

                      {questionErr && (
                        <div className="p-2 rounded bg-rose-50 border border-rose-200 text-rose-700 flex items-center gap-1.5 text-[11px]">
                          <AlertCircle className="w-3.5 h-3.5 shrink-0" />
                          <span>{questionErr}</span>
                        </div>
                      )}

                      {/* INDEPENDENT SUBMIT ACTION FOR THIS QUESTION */}
                      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pt-3 border-t border-outline-variant/20 mt-1">
                        <div className="text-[11px] text-outline">
                          {questionFile ? (
                            <span className="text-emerald-700 font-medium">Tệp đã chọn sẵn sàng nộp riêng cho câu này.</span>
                          ) : prevSubmission ? (
                            <span className="text-on-surface-variant">Chọn tệp ZIP mới để nộp lại câu này bất cứ lúc nào.</span>
                          ) : (
                            <span>Chọn tệp ZIP để kích hoạt nộp cho Câu {qNum}.</span>
                          )}
                        </div>

                        <button
                          type="button"
                          id={`submit-question-${question.id}`}
                          data-testid={`submit-question-${question.id}`}
                          onClick={() => handleSingleQuestionSubmit(question.id)}
                          disabled={!questionFile || isCurrentlyUploading || isPending || isClosed || isUpcoming}
                          className="flex items-center justify-center gap-2 px-5 py-2 rounded-xl bg-primary text-on-primary text-xs font-semibold hover:bg-primary/90 transition-all disabled:opacity-40 disabled:cursor-not-allowed cursor-pointer shadow-xs"
                          title={
                            !questionFile
                              ? `Vui lòng chọn tệp ZIP cho Câu ${qNum} trước`
                              : isClosed
                              ? 'Cổng nộp bài đã đóng'
                              : isUpcoming
                              ? 'Bài thi chưa bắt đầu'
                              : `Nộp bài độc lập cho Câu ${qNum}`
                          }
                        >
                          {isCurrentlyUploading ? (
                            <>
                              <Loader2 className="w-3.5 h-3.5 animate-spin" />
                              <span>Đang nộp ({uploadProgress}%)...</span>
                            </>
                          ) : (
                            <>
                              <Send className="w-3.5 h-3.5" />
                              <span>
                                {prevSubmission
                                  ? `Nộp lại Câu ${qNum}`
                                  : `Nộp Câu ${qNum}`}
                              </span>
                            </>
                          )}
                        </button>
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>

            {/* Bottom Action Bar: Non-blocking Overview & Active Question Context */}
            <div
              data-testid="student-exam-sticky-footer"
              className="sticky bottom-4 z-20 p-4 rounded-2xl bg-surface-container-lowest border border-outline-variant/60 shadow-xl flex flex-col md:flex-row md:items-center justify-between gap-4"
            >
              <div className="flex flex-col gap-1.5">
                <div className="flex items-center gap-2">
                  <span className="text-xs font-bold text-on-surface">Đã hoàn thành:</span>
                  <span
                    data-testid="submitted-questions-counter"
                    className={`font-mono text-xs font-bold px-2.5 py-0.5 rounded-full ${
                      submittedQuestionsCount === totalQuestionsCount && totalQuestionsCount > 0
                        ? 'bg-emerald-100 text-emerald-800 border border-emerald-300'
                        : 'bg-primary/10 text-primary border border-primary/20'
                    }`}
                  >
                    {submittedQuestionsCount} / {totalQuestionsCount} câu
                  </span>
                </div>
                <div className="w-48 sm:w-64 h-2 rounded-full bg-surface-container overflow-hidden">
                  <div
                    className="h-full rounded-full bg-emerald-600 transition-all duration-300"
                    style={{ width: `${(submittedQuestionsCount / Math.max(totalQuestionsCount, 1)) * 100}%` }}
                  />
                </div>
              </div>

              {/* Active Selected Question Context in Sticky Footer */}
              {selectedQuestion && (
                <div className="flex flex-wrap items-center gap-3">
                  <div className="flex items-center gap-2 px-3 py-1.5 rounded-xl bg-surface-container border border-outline-variant/30 text-xs">
                    <span className="font-bold text-primary">Đang chọn:</span>
                    <span
                      data-testid="sticky-active-question-label"
                      className="font-semibold text-on-surface"
                    >
                      Câu {selectedQuestionNumber}: {formatQuestionTitle(selectedQuestion.title)}
                    </span>
                    {selectedZips[selectedQuestion.id] ? (
                      <span className="font-mono text-[11px] text-emerald-700 bg-emerald-50 px-2 py-0.5 rounded border border-emerald-200">
                        {selectedZips[selectedQuestion.id].name}
                      </span>
                    ) : (
                      <span
                        data-testid="sticky-active-upload-label"
                        className="text-[11px] text-outline italic"
                      >
                        {latestSubmissionByQuestion[selectedQuestion.id]
                          ? `Đã nộp (${latestSubmissionByQuestion[selectedQuestion.id].originalFilename || 'ZIP'})`
                          : `Đính kèm tệp ZIP cho Câu ${selectedQuestionNumber}`}
                      </span>
                    )}
                  </div>

                  <button
                    type="button"
                    id="sticky-submit-question-btn"
                    data-testid="sticky-submit-question-btn"
                    onClick={() => handleSingleQuestionSubmit(selectedQuestion.id)}
                    disabled={
                      !selectedZips[selectedQuestion.id] ||
                      uploadingQuestionId === selectedQuestion.id ||
                      isPending ||
                      isClosed ||
                      isUpcoming
                    }
                    className="flex items-center justify-center gap-2 px-5 py-2 rounded-xl bg-primary text-on-primary text-xs font-semibold hover:bg-primary/90 transition-all disabled:opacity-40 disabled:cursor-not-allowed cursor-pointer shadow-xs"
                    title={`Nộp bài cho Câu ${selectedQuestionNumber}`}
                  >
                    {uploadingQuestionId === selectedQuestion.id ? (
                      <>
                        <Loader2 className="w-3.5 h-3.5 animate-spin" />
                        <span>Đang nộp ({uploadProgress}%)...</span>
                      </>
                    ) : (
                      <>
                        <Send className="w-3.5 h-3.5" />
                        <span>
                          {latestSubmissionByQuestion[selectedQuestion.id]
                            ? `Nộp lại Câu ${selectedQuestionNumber}`
                            : `Nộp Câu ${selectedQuestionNumber}`}
                        </span>
                      </>
                    )}
                  </button>

                  <button
                    type="button"
                    className="px-4 py-2 text-xs rounded-lg border border-outline-variant/40 hover:bg-surface-container transition-colors cursor-pointer"
                    onClick={() => router.push('/student/exams')}
                    disabled={isPending}
                  >
                    {t('common.cancel') || 'Quay lại danh sách bài thi'}
                  </button>
                </div>
              )}
            </div>

            {localError && (
              <div className="p-3 rounded-lg bg-rose-50 border border-rose-200 text-rose-700 flex items-center gap-2 text-xs">
                <AlertCircle className="w-4 h-4 shrink-0" />
                <span>{localError}</span>
              </div>
            )}
          </div>
        ) : (
          /* LEGACY SINGLE-QUESTION WORKSPACE */
          <div className="bg-surface-container-lowest p-space-md rounded-2xl border border-outline-variant/40 shadow-xs">
            {exam.problemStatement && (
              <div className="bg-surface-container-low p-4 rounded-xl border border-outline-variant/30 mb-4 space-y-3">
                <div className="flex items-center gap-2 border-b border-outline-variant/20 pb-2">
                  <FileText className="w-4 h-4 text-primary" />
                  <h3 className="font-semibold text-xs text-on-surface">Yêu cầu lập trình</h3>
                </div>
                <div className="text-xs text-on-surface whitespace-pre-wrap">{exam.problemStatement}</div>
              </div>
            )}

            <form onSubmit={handlePreSubmit} className="flex flex-col gap-4">
              <div className="flex items-center justify-between border-b border-outline-variant/30 pb-3">
                <div className="flex items-center gap-2">
                  <UploadCloud className="w-4 h-4 text-primary" />
                  <span className="text-xs font-bold text-on-surface">Tải lên tệp ZIP bài làm</span>
                  <span className="px-1.5 py-0.2 rounded text-[10px] font-mono bg-primary-fixed/20 text-on-surface">.zip</span>
                </div>
                <div className="text-xs text-outline font-mono">
                  <span>Tối đa: 10 MB • Java ZIP</span>
                </div>
              </div>

              <input
                ref={legacyFileInputRef}
                type="file"
                accept=".zip,application/zip,application/x-zip-compressed"
                className="hidden"
                onChange={handleLegacyFileChange}
                disabled={isPending || isClosed}
              />

              {!legacyZip ? (
                <div
                  onDragOver={(e) => { e.preventDefault(); if (!isPending && !isClosed) setIsLegacyDragging(true); }}
                  onDragLeave={(e) => { e.preventDefault(); setIsLegacyDragging(false); }}
                  onDrop={(e) => {
                    e.preventDefault();
                    setIsLegacyDragging(false);
                    if (isPending || isClosed) return;
                    const file = e.dataTransfer.files?.[0];
                    if (file) {
                      const err = validateZipFile(file);
                      if (err) { setLocalError(err); toast.error(err); return; }
                      setLegacyZip(file);
                    }
                  }}
                  onClick={() => legacyFileInputRef.current?.click()}
                  className={`border-2 border-dashed rounded-2xl p-8 sm:p-12 text-center flex flex-col items-center justify-center gap-3 transition-all cursor-pointer ${
                    isLegacyDragging
                      ? 'border-primary bg-primary/10 scale-[0.99]'
                      : 'border-outline-variant/60 hover:border-primary/80 hover:bg-surface-container-low/50 bg-surface-container-lowest'
                  }`}
                >
                  <div className="p-4 rounded-full bg-primary/10 text-primary pointer-events-none">
                    <UploadCloud className="w-8 h-8 animate-bounce" />
                  </div>
                  <div>
                    <p className="font-semibold text-sm text-on-surface">Kéo và thả tệp mã nguồn .zip vào đây</p>
                    <p className="text-xs text-on-surface-variant mt-1">
                      hoặc <span className="text-primary underline font-medium">bấm để duyệt tệp từ máy tính</span>
                    </p>
                  </div>
                  <div className="flex items-center gap-2 pt-2 text-[11px] text-outline">
                    <span className="px-2 py-0.5 rounded bg-surface-container">Định dạng .zip</span>
                    <span>•</span>
                    <span className="px-2 py-0.5 rounded bg-surface-container">Dung lượng tối đa 10 MB</span>
                  </div>
                </div>
              ) : (
                <div className="border border-outline-variant/50 rounded-2xl p-5 bg-surface-container-low/30 flex items-center justify-between gap-4">
                  <div className="flex items-center gap-3.5">
                    <div className="p-3 rounded-xl bg-primary/15 text-primary flex items-center justify-center">
                      <FileArchive className="w-7 h-7" />
                    </div>
                    <div>
                      <div className="flex items-center gap-2">
                        <h4 className="font-semibold text-xs sm:text-sm text-on-surface font-mono">{legacyZip.name}</h4>
                        <span className="px-2 py-0.2 rounded-full text-[10px] font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200">
                          Hợp lệ
                        </span>
                      </div>
                      <p className="text-xs text-on-surface-variant font-mono mt-0.5">
                        {(legacyZip.size / (1024 * 1024)).toFixed(2)} MB / 10 MB
                      </p>
                    </div>
                  </div>
                  <div className="flex items-center gap-2">
                    <button
                      type="button"
                      onClick={() => legacyFileInputRef.current?.click()}
                      disabled={isPending}
                      className="px-3 py-1.5 rounded-lg border border-outline-variant/40 hover:bg-surface-container text-xs transition-colors cursor-pointer"
                    >
                      Đổi tệp
                    </button>
                    <button
                      type="button"
                      onClick={() => setLegacyZip(null)}
                      disabled={isPending}
                      className="p-1.5 rounded-lg text-rose-600 hover:bg-rose-50 border border-rose-200 transition-colors cursor-pointer"
                      title="Xóa tệp"
                    >
                      <Trash2 className="w-4 h-4" />
                    </button>
                  </div>
                </div>
              )}

              {localError && (
                <div className="p-3 rounded-lg bg-rose-50 border border-rose-200 text-rose-700 flex items-center gap-2 text-xs">
                  <AlertCircle className="w-4 h-4 shrink-0" />
                  <span>{localError}</span>
                </div>
              )}

              <div className="flex items-center justify-between gap-3 pt-2">
                <div className="text-xs text-outline flex items-center gap-1.5">
                  <ShieldAlert className="w-3.5 h-3.5 text-amber-600 shrink-0" />
                  <span>Tất cả bài làm được quét AST chống đạo văn và thực thi cô lập trong Docker.</span>
                </div>
                <button
                  type="submit"
                  id="submit-student-exam-btn"
                  className="flex items-center gap-2 px-6 py-2 bg-primary text-on-primary rounded-lg text-xs font-medium hover:bg-primary/90 transition-colors disabled:opacity-50 disabled:cursor-not-allowed cursor-pointer shadow-sm"
                  disabled={isPending || isClosed || !legacyZip}
                >
                  <Send className="w-3.5 h-3.5" />
                  <span>Nộp tệp ZIP</span>
                  <span className="sr-only">Submit Code Artifact</span>
                </button>
              </div>
            </form>
          </div>
        )}

        {/* Confirmation Modal */}
        {showConfirmModal && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-xs p-4">
            <div className="bg-white dark:bg-slate-900 text-on-surface rounded-2xl max-w-lg w-full p-6 shadow-2xl border border-outline-variant/40 flex flex-col gap-4">
              <div className="flex items-center gap-3 text-on-surface">
                <div className="p-2.5 rounded-full bg-blue-50 text-blue-600">
                  <FileCheck className="w-6 h-6" />
                </div>
                <div>
                  <h3 className="font-headline-sm text-headline-sm font-semibold">Xác nhận nộp bài thi</h3>
                  <p className="text-xs text-on-surface-variant">Kiểm tra danh sách các tệp mã nguồn sẽ nộp</p>
                </div>
              </div>

              {hasQuestions ? (
                <div className="p-3.5 rounded-xl bg-surface-container-low border border-outline-variant/30 text-xs flex flex-col gap-2 max-h-60 overflow-y-auto">
                  <span className="font-semibold text-outline text-[11px] uppercase tracking-wider">
                    Danh sách tệp nộp ({totalQuestionsCount} câu hỏi):
                  </span>
                  {questionsList.map((q, idx) => {
                    const f = selectedZips[q.id];
                    return (
                      <div key={q.id || idx} className="flex items-center justify-between p-2 rounded-lg bg-surface-container-lowest border border-outline-variant/20">
                        <div className="flex items-center gap-2">
                          <span className="w-5 h-5 rounded bg-primary/10 text-primary font-mono text-[10px] font-bold flex items-center justify-center">
                            {idx + 1}
                          </span>
                          <span className="font-medium text-on-surface truncate max-w-[180px]">{q.title}</span>
                        </div>
                        <span className="font-mono text-primary font-semibold text-[11px]">
                          {f ? `${f.name} (${(f.size / (1024 * 1024)).toFixed(2)} MB)` : 'Chưa có'}
                        </span>
                      </div>
                    );
                  })}
                </div>
              ) : (
                <div className="p-3 rounded-lg bg-surface-container-low border border-outline-variant/30 text-xs text-on-surface-variant space-y-1.5">
                  <div className="flex justify-between">
                    <span>Tên tệp:</span>
                    <span className="font-mono font-semibold text-on-surface truncate max-w-[200px]">{legacyZip?.name}</span>
                  </div>
                  <div className="flex justify-between">
                    <span>Dung lượng:</span>
                    <span className="font-mono font-semibold text-on-surface">
                      {legacyZip ? (legacyZip.size / (1024 * 1024)).toFixed(2) : 0} MB
                    </span>
                  </div>
                </div>
              )}

              <p className="text-xs text-outline leading-relaxed">
                Sau khi bấm nộp, hệ thống sẽ đưa toàn bộ mã nguồn vào sandbox Docker cô lập để biên dịch, thực thi các bộ kiểm thử và phân tích cây AST chống đạo văn.
              </p>

              <div className="flex justify-end gap-3 pt-2 border-t border-outline-variant/30">
                <button
                  type="button"
                  onClick={() => setShowConfirmModal(false)}
                  className="px-4 py-2 text-xs rounded-lg border border-outline-variant/40 hover:bg-surface-container transition-colors cursor-pointer"
                >
                  Kiểm tra lại
                </button>
                <button
                  type="button"
                  id="confirm-student-exam-btn"
                  data-testid="confirm-student-exam-btn"
                  onClick={handleFinalSubmit}
                  disabled={isPending}
                  className="px-5 py-2 bg-primary text-on-primary rounded-lg text-xs font-medium hover:bg-primary/90 transition-colors cursor-pointer shadow-sm flex items-center gap-2"
                >
                  <CheckCircle2 className="w-3.5 h-3.5" />
                  <span>Xác nhận và gửi chấm điểm</span>
                  {/* Hidden text helper for Playwright e2e selector backwards compatibility */}
                  <span className="sr-only">Confirm &amp; Grade</span>
                </button>
              </div>
            </div>
          </div>
        )}

        {/* Batch Submission Results Modal */}
        {batchResults && batchResults.length > 0 && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-xs p-4">
            <div className="bg-white dark:bg-slate-900 text-on-surface rounded-2xl max-w-2xl w-full p-6 shadow-2xl border border-outline-variant/40 flex flex-col gap-4 max-h-[90vh] overflow-y-auto">
              <div className="flex items-center justify-between pb-3 border-b border-outline-variant/30">
                <div className="flex items-center gap-3">
                  <div className="p-2.5 rounded-full bg-emerald-50 text-emerald-600">
                    <CheckCircle2 className="w-6 h-6" />
                  </div>
                  <div>
                    <h3 className="font-headline-sm text-headline-sm font-semibold text-on-surface">
                      Đã ghi nhận toàn bộ bài nộp thành công!
                    </h3>
                    <p className="text-xs text-on-surface-variant">Tổng số {batchResults.length} câu hỏi đã được đưa vào hệ thống chấm tự động</p>
                  </div>
                </div>
              </div>

              <div className="space-y-3">
                {batchResults.map((res, idx) => (
                  <div key={res.id || idx} className="p-4 rounded-xl bg-surface-container-low border border-outline-variant/20 flex items-center justify-between gap-3 flex-wrap">
                    <div className="flex flex-col gap-1">
                      <span className="font-semibold text-xs text-on-surface">
                        {res.questionTitle || `Câu hỏi ${idx + 1}`}
                      </span>
                      <div className="flex items-center gap-2 text-[11px] text-outline font-mono">
                        <FileArchive className="w-3.5 h-3.5 text-primary" />
                        <span>{res.originalFilename || 'submission.zip'}</span>
                      </div>
                    </div>

                    <div className="flex items-center gap-3">
                      <span className={`px-2.5 py-0.5 rounded-full text-[10px] font-bold font-mono ${
                        res.executionStatus === 'COMPLETED'
                          ? 'bg-emerald-50 text-emerald-700 border border-emerald-200'
                          : res.executionStatus === 'COMPILE_ERROR'
                          ? 'bg-rose-50 text-rose-700 border border-rose-200'
                          : 'bg-amber-50 text-amber-700 border border-amber-200'
                      }`}>
                        {res.executionStatus || 'SUBMITTED'}
                      </span>

                      {res.testCasesTotal !== undefined && res.testCasesTotal > 0 && (
                        <span className="text-xs font-mono font-semibold text-on-surface">
                          {res.testCasesPassed ?? 0}/{res.testCasesTotal} test
                        </span>
                      )}

                      <span className="font-mono font-bold text-xs text-primary bg-primary/10 px-2.5 py-1 rounded">
                        {res.totalScore ?? res.autoScore ?? 0} {res.questionMaxScore ? `/ ${res.questionMaxScore}` : ''} đ
                      </span>

                      <Link
                        href={`/student/submissions/${res.id}`}
                        className="p-1.5 rounded-lg text-primary hover:bg-surface-container transition-colors"
                        title="Xem chi tiết kết quả"
                      >
                        <ExternalLink className="w-4 h-4" />
                      </Link>
                    </div>
                  </div>
                ))}
              </div>

              <div className="flex items-center justify-end gap-3 pt-3 border-t border-outline-variant/30">
                <button
                  type="button"
                  onClick={() => router.push('/student/submissions')}
                  className="px-5 py-2 bg-primary text-on-primary rounded-lg text-xs font-medium hover:bg-primary/90 transition-colors cursor-pointer shadow-sm flex items-center gap-2"
                >
                  <span>Xem danh sách bài nộp của tôi</span>
                  <ExternalLink className="w-3.5 h-3.5" />
                </button>
              </div>
            </div>
          </div>
        )}

        {/* Legacy Single Submission Result Modal */}
        {submissionResult && !batchResults && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-xs p-4 animate-in fade-in duration-150">
            <div className="bg-surface-container-lowest rounded-2xl max-w-2xl w-full p-6 shadow-2xl border border-outline-variant/40 flex flex-col gap-4 animate-in zoom-in-95 duration-150 max-h-[90vh] overflow-y-auto">
              <div className="flex items-center justify-between pb-3 border-b border-outline-variant/30">
                <div className="flex items-center gap-3">
                  <div className="p-2.5 rounded-full bg-emerald-50 text-emerald-600">
                    <CheckCircle2 className="w-6 h-6" />
                  </div>
                  <div>
                    <h3 className="font-headline-sm text-headline-sm font-semibold text-on-surface">
                      Đã ghi nhận bài nộp thành công!
                    </h3>
                    <p className="text-xs text-on-surface-variant">Kết quả thực thi biên dịch và sandbox sơ bộ</p>
                  </div>
                </div>

                <span className={`px-2.5 py-1 rounded-full text-xs font-bold font-mono ${
                  submissionResult.executionStatus === 'COMPLETED'
                    ? 'bg-emerald-50 text-emerald-700 border border-emerald-200'
                    : submissionResult.executionStatus === 'COMPILE_ERROR'
                    ? 'bg-rose-50 text-rose-700 border border-rose-200'
                    : 'bg-amber-50 text-amber-700 border border-amber-200'
                }`}>
                  {submissionResult.executionStatus || 'SUBMITTED'}
                </span>
              </div>

              {/* Compiler Output */}
              {submissionResult.compileOutput && (
                <div className="space-y-1.5">
                  <div className="flex items-center justify-between text-xs text-outline font-medium">
                    <span className="flex items-center gap-1.5">
                      <Terminal className="w-3.5 h-3.5 text-primary" />
                      <span>Thông điệp biên dịch (javac):</span>
                    </span>
                    <button
                      type="button"
                      onClick={() => copyToClipboard(submissionResult.compileOutput || '', 'compile')}
                      className="hover:text-on-surface transition-colors flex items-center gap-1"
                    >
                      {copiedSection === 'compile' ? <Check className="w-3 h-3 text-emerald-600" /> : <Copy className="w-3 h-3" />}
                      <span>{copiedSection === 'compile' ? 'Đã sao chép' : 'Sao chép'}</span>
                    </button>
                  </div>
                  <pre className="p-3 bg-slate-950 text-slate-200 rounded-xl font-mono text-xs overflow-x-auto max-h-36 leading-relaxed">
                    {submissionResult.compileOutput}
                  </pre>
                </div>
              )}

              <div className="flex items-center justify-end gap-3 pt-3 border-t border-outline-variant/30">
                <button
                  type="button"
                  onClick={() => router.push(`/student/submissions/${submissionResult.id}`)}
                  className="px-5 py-2 bg-primary text-on-primary rounded-lg text-xs font-medium hover:bg-primary/90 transition-colors cursor-pointer shadow-sm flex items-center gap-2"
                >
                  <span>Xem bảng điểm chi tiết</span>
                  <ExternalLink className="w-3.5 h-3.5" />
                </button>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
