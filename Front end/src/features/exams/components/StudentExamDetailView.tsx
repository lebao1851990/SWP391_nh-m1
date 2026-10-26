'use client';

import React, { useState, useEffect, useMemo, useRef } from 'react';
import { useRouter } from 'next/navigation';
import { useStudentExam, useSubmitExam, useSubmitExamZip } from '../hooks/use-student-exams';
import { SubmissionResultResponse } from '../api/student-exams.api';
import { AxiosError } from 'axios';
import { AppLink as Link } from '@/components/ui/app-link';
import { useTranslation } from '@/i18n';
import {
  ArrowLeft,
  Code2,
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
  ExternalLink
} from 'lucide-react';
import { toast } from 'sonner';

interface StudentExamDetailViewProps {
  examId: string;
}

export function StudentExamDetailView({ examId }: StudentExamDetailViewProps) {
  const router = useRouter();
  const { t } = useTranslation();
  const { data: exam, isLoading, isError, error } = useStudentExam(examId);
  const submitExam = useSubmitExam();
  const submitExamZip = useSubmitExamZip();

  const [submissionMode, setSubmissionMode] = useState<'editor' | 'zip'>('editor');
  const [sourceCode, setSourceCode] = useState('');
  const [selectedZip, setSelectedZip] = useState<File | null>(null);
  const [isDragging, setIsDragging] = useState(false);
  const [uploadProgress, setUploadProgress] = useState(0);
  const [localError, setLocalError] = useState('');
  const [showConfirmModal, setShowConfirmModal] = useState(false);
  const [submissionResult, setSubmissionResult] = useState<SubmissionResultResponse | null>(null);
  const [copiedSection, setCopiedSection] = useState<string | null>(null);

  const fileInputRef = useRef<HTMLInputElement>(null);

  // Real-time server-synchronized countdown timer
  const [remainingSeconds, setRemainingSeconds] = useState<number | null>(null);
  const [isUpcoming, setIsUpcoming] = useState(false);

  useEffect(() => {
    if (!exam) return;

    // Calculate server offset: serverTimestamp - localTimestamp
    // Client clock adjustment will NOT alter real deadline calculation!
    const serverTimestamp = exam.serverTime ? new Date(exam.serverTime).getTime() : Date.now();
    const clientOffset = serverTimestamp - Date.now();
    const startTimestamp = exam.startTime ? new Date(exam.startTime).getTime() : 0;
    const endTimestamp = exam.endTime ? new Date(exam.endTime).getTime() : 0;

    const updateTimer = () => {
      const currentServerTime = Date.now() + clientOffset;
      setIsUpcoming(Boolean(startTimestamp && currentServerTime < startTimestamp));
      if (endTimestamp) {
        const diffSec = Math.max(0, Math.floor((endTimestamp - currentServerTime) / 1000));
        setRemainingSeconds(diffSec);
      }
    };

    updateTimer();
    const interval = setInterval(updateTimer, 1000);
    return () => clearInterval(interval);
  }, [exam]);

  const isExpiredByTimer = remainingSeconds !== null && remainingSeconds <= 0;
  const isDeadlinePassed = Boolean(exam?.isExpired) || isExpiredByTimer;
  const isClosed = exam?.status === 'completed' || exam?.status === 'missed' || isDeadlinePassed || isUpcoming;

  const is5MinWarning = !isClosed && remainingSeconds !== null && remainingSeconds <= 300 && remainingSeconds > 60;
  const is1MinWarning = !isClosed && remainingSeconds !== null && remainingSeconds <= 60 && remainingSeconds > 0;

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

  const lineCount = sourceCode ? sourceCode.split('\n').length : 0;
  const charCount = sourceCode.length;
  const isPending = submitExam.isPending || submitExamZip.isPending;

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

  const handleBrowseClick = (e?: React.MouseEvent) => {
    if (e) {
      e.stopPropagation();
    }
    if (isPending) return;
    fileInputRef.current?.click();
  };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const err = validateZipFile(file);
    if (err) {
      setLocalError(err);
      toast.error(err);
      setSelectedZip(null);
      if (fileInputRef.current) fileInputRef.current.value = '';
      return;
    }
    setLocalError('');
    setSelectedZip(file);
    toast.success(`Đã chọn tệp: ${file.name} (${(file.size / (1024 * 1024)).toFixed(2)} MB)`);
    if (fileInputRef.current) fileInputRef.current.value = '';
  };

  const handleDragOver = (e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    if (!isPending) {
      setIsDragging(true);
    }
  };

  const handleDragLeave = (e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragging(false);
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragging(false);
    if (isPending) return;

    const file = e.dataTransfer.files?.[0];
    if (!file) return;
    const err = validateZipFile(file);
    if (err) {
      setLocalError(err);
      toast.error(err);
      setSelectedZip(null);
      return;
    }
    setLocalError('');
    setSelectedZip(file);
    toast.success(`Đã chọn tệp: ${file.name} (${(file.size / (1024 * 1024)).toFixed(2)} MB)`);
  };

  const handleRemoveZip = () => {
    setSelectedZip(null);
    setLocalError('');
    if (fileInputRef.current) {
      fileInputRef.current.value = '';
    }
  };

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

    if (submissionMode === 'editor') {
      if (!sourceCode.trim()) {
        setLocalError('Mã nguồn không được để trống.');
        return;
      }
    } else {
      if (!selectedZip) {
        setLocalError('Vui lòng chọn hoặc kéo thả tệp .zip trước khi nộp.');
        return;
      }
      const err = validateZipFile(selectedZip);
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

    if (submissionMode === 'editor') {
      submitExam.mutate(
        { examId, sourceCode },
        {
          onSuccess: (data: SubmissionResultResponse) => {
            toast.success('Nộp bài làm thành công! Hệ thống đang lưu trữ và phân tích.');
            setSubmissionResult(data);
            if (data?.id) {
              setTimeout(() => {
                router.push(`/student/submissions/${data.id}`);
              }, 1200);
            }
          },
          onError: (err: unknown) => {
            const axiosErr = err as AxiosError<{ message?: string; error?: string }>;
            let msg = axiosErr?.response?.data?.message || (err as Error)?.message || t('studentExams.submitError');
            if (axiosErr?.response?.status === 409 || axiosErr?.response?.data?.error === 'EXAM_TIME_EXPIRED' || msg === 'EXAM_TIME_EXPIRED') {
              msg = t('errors.EXAM_TIME_EXPIRED') || 'Thời gian nộp bài đã kết thúc.';
            }
            setLocalError(msg);
            toast.error(msg);
          }
        }
      );
    } else {
      if (!selectedZip) return;
      submitExamZip.mutate(
        {
          examId,
          file: selectedZip,
          onUploadProgress: (pct) => setUploadProgress(pct)
        },
        {
          onSuccess: (data: SubmissionResultResponse) => {
            toast.success(`Nộp tệp ${data.originalFilename || selectedZip.name} thành công! Đã chuyển vào Sandbox kiểm thử.`);
            setSubmissionResult(data);
            if (data?.id) {
              setTimeout(() => {
                router.push(`/student/submissions/${data.id}`);
              }, 1500);
            }
          },
          onError: (err: unknown) => {
            const axiosErr = err as AxiosError<{ message?: string; error?: string }>;
            let msg = axiosErr?.response?.data?.message || (err as Error)?.message || 'Nộp tệp ZIP thất bại.';
            if (axiosErr?.response?.status === 409 || axiosErr?.response?.data?.error === 'EXAM_TIME_EXPIRED' || msg === 'EXAM_TIME_EXPIRED') {
              msg = t('errors.EXAM_TIME_EXPIRED') || 'Thời gian nộp bài đã kết thúc.';
            }
            setLocalError(msg);
            toast.error(msg);
          }
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
              <div className={`flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-semibold ${
                !isClosed && exam.status === 'active'
                  ? 'bg-emerald-50 text-emerald-700 border border-emerald-200'
                  : isUpcoming
                  ? 'bg-blue-50 text-blue-700 border border-blue-200'
                  : 'bg-rose-50 text-rose-700 border border-rose-200'
              }`}>
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
              <p className="text-blue-700 mt-0.5">Bài thi này sẽ bắt đầu vào lúc {new Date(exam.startTime).toLocaleString('vi-VN')}. Bạn chỉ có thể nộp bài khi thời gian bắt đầu.</p>
            </div>
          </div>
        )}

        {/* Closed/Expired Exam Alert Banner */}
        {isDeadlinePassed && !isUpcoming && (
          <div className="p-4 rounded-xl bg-rose-50 border border-rose-200 text-rose-800 text-xs flex items-center gap-3 shadow-sm">
            <Lock className="w-5 h-5 text-rose-600 shrink-0" />
            <div>
              <p className="font-bold text-sm">{t('studentExams.timeExpiredTitle') || 'Thời gian làm bài đã kết thúc'}</p>
              <p className="text-rose-700 mt-0.5">{t('studentExams.timeExpiredDesc') || 'Hạn nộp bài thi này đã qua. Hệ thống đã đóng cổng nhận bài.'}</p>
            </div>
          </div>
        )}

        {/* Completed/Missed Banner */}
        {(!isDeadlinePassed && !isUpcoming && (exam.status === 'completed' || exam.status === 'missed')) && (
          <div className="p-4 rounded-xl bg-surface-container border border-outline-variant/40 text-on-surface-variant text-xs flex items-center gap-3">
            <CheckCircle2 className="w-5 h-5 text-primary shrink-0" />
            <div>
              <p className="font-semibold">Trạng thái: {exam.status === 'completed' ? 'ĐÃ HOÀN THÀNH' : 'QUÁ HẠN'}</p>
              <p className="text-on-surface-variant/80 mt-0.5">Bài thi này hiện không còn nhận bài nộp mới.</p>
            </div>
          </div>
        )}

        {/* Exam Description Card */}
        {exam.description && (
          <div className="p-space-md rounded-xl bg-surface-container-lowest border border-outline-variant/40 shadow-xs">
            <h3 className="font-label-sm font-semibold text-outline uppercase tracking-wider mb-1">Đề bài &amp; Yêu cầu kỹ thuật</h3>
            <p className="text-xs text-on-surface leading-relaxed whitespace-pre-wrap">{exam.description}</p>
          </div>
        )}

        {/* Submission Workspace Card */}
        <div className="bg-surface-container-lowest p-space-md rounded-2xl border border-outline-variant/40 shadow-xs">
          <form onSubmit={handlePreSubmit} className="flex flex-col gap-4">

            {/* Mode Switcher Tabs */}
            <div className="flex flex-col sm:flex-row sm:items-center justify-between border-b border-outline-variant/30 pb-3 gap-3">
              <div className="flex items-center gap-2">
                <button
                  type="button"
                  onClick={() => { setSubmissionMode('editor'); setLocalError(''); }}
                  disabled={isPending}
                  className={`flex items-center gap-2 px-3.5 py-1.5 rounded-lg text-xs font-medium transition-all cursor-pointer ${
                    submissionMode === 'editor'
                      ? 'bg-primary text-on-primary shadow-xs'
                      : 'text-on-surface-variant hover:text-on-surface hover:bg-surface-container'
                  }`}
                >
                  <Code2 className="w-3.5 h-3.5" />
                  <span>Viết mã nguồn trực tiếp</span>
                </button>

                <button
                  type="button"
                  onClick={() => { setSubmissionMode('zip'); setLocalError(''); }}
                  disabled={isPending}
                  className={`flex items-center gap-2 px-3.5 py-1.5 rounded-lg text-xs font-medium transition-all cursor-pointer ${
                    submissionMode === 'zip'
                      ? 'bg-primary text-on-primary shadow-xs'
                      : 'text-on-surface-variant hover:text-on-surface hover:bg-surface-container'
                  }`}
                >
                  <UploadCloud className="w-3.5 h-3.5" />
                  <span>Tải lên tệp ZIP</span>
                  <span className="px-1.5 py-0.2 rounded text-[10px] font-mono bg-primary-fixed/20 text-on-surface">.zip</span>
                </button>
              </div>

              <div className="flex items-center gap-3 text-xs text-outline font-mono">
                {submissionMode === 'editor' ? (
                  <>
                    <span>{lineCount} dòng</span>
                    <span>•</span>
                    <span>{charCount} ký tự</span>
                    <span>•</span>
                    <span className="px-2 py-0.5 rounded bg-surface-container text-on-surface-variant font-semibold">Java 17</span>
                  </>
                ) : (
                  <>
                    <span>Tối đa: 10 MB</span>
                    <span>•</span>
                    <span className="px-2 py-0.5 rounded bg-surface-container text-on-surface-variant font-semibold">Multi-file Java ZIP</span>
                  </>
                )}
              </div>
            </div>

            {/* Mode 1: Code Textarea with IDE Styling */}
            {submissionMode === 'editor' && (
              <div className="relative rounded-xl border border-outline-variant/60 bg-slate-950 overflow-hidden shadow-inner focus-within:ring-2 focus-within:ring-primary/40 focus-within:border-primary transition-all">
                <div className="px-4 py-2 bg-slate-900 border-b border-slate-800 flex items-center justify-between text-xs text-slate-400 font-mono">
                  <div className="flex items-center gap-2">
                    <span className="w-2.5 h-2.5 rounded-full bg-rose-500/80"></span>
                    <span className="w-2.5 h-2.5 rounded-full bg-amber-500/80"></span>
                    <span className="w-2.5 h-2.5 rounded-full bg-emerald-500/80"></span>
                    <span className="ml-2 text-slate-300">Solution.java</span>
                  </div>
                  <span>UTF-8</span>
                </div>

                <textarea
                  className="w-full h-96 p-4 bg-transparent font-mono text-xs text-slate-100 placeholder:text-slate-600 outline-none resize-y leading-relaxed"
                  placeholder="// Nhập mã nguồn Java của bạn tại đây...&#10;public class Solution {&#10;    public static void main(String[] args) {&#10;        // Viết mã nguồn của bạn tại đây&#10;    }&#10;}"
                  value={sourceCode}
                  onChange={(e) => setSourceCode(e.target.value)}
                  disabled={isPending || isClosed}
                  spellCheck="false"
                />
              </div>
            )}

            {/* Mode 2: Drag and Drop ZIP Upload Area */}
            {submissionMode === 'zip' && (
              <div className="flex flex-col gap-3">
                <input
                  ref={fileInputRef}
                  type="file"
                  accept=".zip,application/zip,application/x-zip-compressed"
                  className="hidden"
                  onChange={handleFileChange}
                  disabled={isPending}
                />

                {!selectedZip ? (
                  <div
                    onDragOver={handleDragOver}
                    onDragLeave={handleDragLeave}
                    onDrop={handleDrop}
                    onClick={handleBrowseClick}
                    className={`border-2 border-dashed rounded-2xl p-8 sm:p-12 text-center flex flex-col items-center justify-center gap-3 transition-all cursor-pointer ${
                      isDragging
                        ? 'border-primary bg-primary/10 scale-[0.99]'
                        : 'border-outline-variant/60 hover:border-primary/80 hover:bg-surface-container-low/50 bg-surface-container-lowest'
                    }`}
                  >
                    <div className="p-4 rounded-full bg-primary/10 text-primary pointer-events-none">
                      <UploadCloud className="w-8 h-8 animate-bounce" />
                    </div>
                    <div className="pointer-events-auto">
                      <p className="font-semibold text-sm text-on-surface">
                        Kéo và thả tệp mã nguồn <span className="text-primary font-mono font-bold">.zip</span> vào đây
                      </p>
                      <p className="text-xs text-on-surface-variant mt-1">
                        hoặc{' '}
                        <span
                          onClick={handleBrowseClick}
                          className="text-primary hover:text-primary/80 underline font-medium cursor-pointer transition-colors"
                        >
                          bấm để duyệt tệp từ máy tính
                        </span>
                      </p>
                    </div>
                    <div className="flex flex-wrap items-center justify-center gap-2 pt-2 text-[11px] text-outline pointer-events-none">
                      <span className="px-2 py-0.5 rounded bg-surface-container">Định dạng .zip</span>
                      <span>•</span>
                      <span className="px-2 py-0.5 rounded bg-surface-container">Dung lượng tối đa 10 MB</span>
                      <span>•</span>
                      <span className="px-2 py-0.5 rounded bg-surface-container">Tự động phát hiện hàm main</span>
                    </div>
                  </div>
                ) : (
                  <div className="border border-outline-variant/50 rounded-2xl p-5 bg-surface-container-low/30 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                    <div className="flex items-center gap-3.5">
                      <div className="p-3 rounded-xl bg-primary/15 text-primary flex items-center justify-center">
                        <FileArchive className="w-7 h-7" />
                      </div>
                      <div>
                        <div className="flex items-center gap-2">
                          <h4 className="font-semibold text-xs sm:text-sm text-on-surface font-mono">{selectedZip.name}</h4>
                          <span className="px-2 py-0.2 rounded-full text-[10px] font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200">
                            Hợp lệ
                          </span>
                        </div>
                        <p className="text-xs text-on-surface-variant font-mono mt-0.5">
                          {(selectedZip.size / (1024 * 1024)).toFixed(2)} MB / 10 MB
                        </p>
                      </div>
                    </div>

                    <div className="flex items-center gap-2 self-end sm:self-auto">
                      <button
                        type="button"
                        onClick={handleBrowseClick}
                        disabled={isPending}
                        className="px-3 py-1.5 rounded-lg border border-outline-variant/40 hover:bg-surface-container text-xs transition-colors cursor-pointer"
                      >
                        Đổi tệp khác
                      </button>
                      <button
                        type="button"
                        onClick={handleRemoveZip}
                        disabled={isPending}
                        className="p-1.5 rounded-lg text-rose-600 hover:bg-rose-50 border border-rose-200 transition-colors cursor-pointer"
                        title="Xóa tệp"
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                    </div>
                  </div>
                )}
              </div>
            )}

            {/* Upload & Execution Progress Bar */}
            {isPending && (
              <div className="p-4 rounded-xl bg-surface-container-low border border-outline-variant/40 flex flex-col gap-2 animate-in fade-in">
                <div className="flex items-center justify-between text-xs">
                  <span className="font-medium text-on-surface flex items-center gap-2">
                    <Loader2 className="w-3.5 h-3.5 animate-spin text-primary" />
                    {uploadProgress < 100
                      ? `Đang tải tệp lên máy chủ (${uploadProgress}%)...`
                      : 'Đang giải nén & thực thi kiểm thử an toàn trong Docker Sandbox...'}
                  </span>
                  <span className="font-mono text-outline">{uploadProgress}%</span>
                </div>
                <div className="w-full h-2 rounded-full bg-surface-container overflow-hidden">
                  <div
                    className="h-full bg-primary rounded-full transition-all duration-300"
                    style={{ width: `${Math.max(uploadProgress, 5)}%` }}
                  />
                </div>
              </div>
            )}

            {localError && (
              <div className="p-3 rounded-lg bg-rose-50 border border-rose-200 text-rose-700 flex items-center gap-2 text-xs">
                <AlertCircle className="w-4 h-4 shrink-0" />
                <span>{localError}</span>
              </div>
            )}

            {/* Submission Actions */}
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pt-2">
              <div className="text-xs text-outline flex items-center gap-1.5">
                <ShieldAlert className="w-3.5 h-3.5 text-amber-600 shrink-0" />
                <span>Tất cả bài làm được quét đối soát AST chống đạo văn và thực thi cô lập trong Docker.</span>
              </div>

              <div className="flex items-center gap-3 justify-end">
                <button
                  type="button"
                  className="px-4 py-2 text-xs rounded-lg border border-outline-variant/40 hover:bg-surface-container transition-colors cursor-pointer"
                  onClick={() => router.push('/student/exams')}
                  disabled={isPending}
                >
                  {t('common.cancel') || 'Hủy bỏ'}
                </button>

                <button
                  type="submit"
                  id="submit-student-exam-btn"
                  className="flex items-center gap-2 px-6 py-2 bg-primary text-on-primary rounded-lg text-xs font-medium hover:bg-primary/90 transition-colors disabled:opacity-50 disabled:cursor-not-allowed cursor-pointer shadow-sm"
                  disabled={
                    isPending ||
                    isClosed ||
                    (submissionMode === 'editor' && !sourceCode.trim()) ||
                    (submissionMode === 'zip' && !selectedZip)
                  }
                >
                  {isPending ? (
                    <>
                      <Loader2 className="w-3.5 h-3.5 animate-spin" />
                      <span>Đang xử lý nộp bài...</span>
                    </>
                  ) : (
                    <>
                      <Send className="w-3.5 h-3.5" />
                      <span>
                        {isUpcoming
                          ? 'Chưa mở nộp bài'
                          : isDeadlinePassed
                          ? 'Đã quá hạn nộp'
                          : submissionMode === 'zip'
                          ? 'Nộp tệp ZIP'
                          : 'Nộp bài làm'}
                      </span>
                      {/* Hidden text helper for Playwright e2e selector backwards compatibility */}
                      <span className="sr-only">Submit Code Artifact</span>
                    </>
                  )}
                </button>
              </div>
            </div>
          </form>
        </div>

        {/* Confirmation Modal */}
        {showConfirmModal && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-xs p-4 animate-in fade-in duration-150">
            <div className="bg-surface-container-lowest rounded-2xl max-w-md w-full p-6 shadow-2xl border border-outline-variant/40 flex flex-col gap-4 animate-in zoom-in-95 duration-150">
              <div className="flex items-center gap-3 text-on-surface">
                <div className="p-2.5 rounded-full bg-blue-50 text-blue-600">
                  <FileCheck className="w-6 h-6" />
                </div>
                <div>
                  <h3 className="font-headline-sm text-headline-sm font-semibold">{t('studentExams.confirmModalTitle') || 'Xác nhận nộp bài chính thức'}</h3>
                  <p className="text-xs text-on-surface-variant">Bạn đã kiểm tra kỹ mã nguồn trước khi chấm điểm?</p>
                </div>
              </div>

              <div className="p-3 rounded-lg bg-surface-container-low border border-outline-variant/30 text-xs text-on-surface-variant space-y-1.5">
                <div className="flex justify-between">
                  <span>Phương thức nộp:</span>
                  <span className="font-semibold text-on-surface">
                    {submissionMode === 'zip' ? 'Tệp nén đa file (.ZIP)' : 'Soạn thảo trực tiếp (Single file)'}
                  </span>
                </div>
                {submissionMode === 'editor' ? (
                  <>
                    <div className="flex justify-between">
                      <span>Số dòng code:</span>
                      <span className="font-mono font-semibold text-on-surface">{lineCount}</span>
                    </div>
                    <div className="flex justify-between">
                      <span>Số lượng ký tự:</span>
                      <span className="font-mono font-semibold text-on-surface">{charCount}</span>
                    </div>
                  </>
                ) : (
                  <>
                    <div className="flex justify-between">
                      <span>Tên tệp:</span>
                      <span className="font-mono font-semibold text-on-surface truncate max-w-[200px]">{selectedZip?.name}</span>
                    </div>
                    <div className="flex justify-between">
                      <span>Dung lượng:</span>
                      <span className="font-mono font-semibold text-on-surface">
                        {selectedZip ? (selectedZip.size / (1024 * 1024)).toFixed(2) : 0} MB
                      </span>
                    </div>
                  </>
                )}
                <div className="flex justify-between">
                  <span>Môi trường biên dịch:</span>
                  <span className="font-mono font-semibold text-on-surface">Java 17 (OpenJDK Temurin)</span>
                </div>
                <div className="flex justify-between">
                  <span>Pipeline tiếp theo:</span>
                  <span className="font-semibold text-emerald-700">Docker Sandbox &amp; AST Plagiarism</span>
                </div>
              </div>

              <p className="text-xs text-outline leading-relaxed">
                Sau khi bấm nộp, hệ thống sẽ đưa mã nguồn vào sandbox Docker cô lập để biên dịch, thực thi các bộ kiểm thử và kiểm tra trùng lặp AST.
              </p>

              <div className="flex justify-end gap-3 pt-2 border-t border-outline-variant/30">
                <button
                  type="button"
                  onClick={() => setShowConfirmModal(false)}
                  className="px-4 py-2 text-xs rounded-lg border border-outline-variant/40 hover:bg-surface-container transition-colors cursor-pointer"
                >
                  {t('common.cancel') || 'Kiểm tra lại'}
                </button>
                <button
                  type="button"
                  id="confirm-student-exam-btn"
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

        {/* Post-submission Result & Execution Breakdown Modal */}
        {submissionResult && (
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
                    : submissionResult.executionStatus === 'CAPACITY_EXCEEDED'
                    ? 'bg-purple-50 text-purple-700 border border-purple-200'
                    : 'bg-amber-50 text-amber-700 border border-amber-200'
                }`}>
                  {submissionResult.executionStatus || 'SUBMITTED'}
                </span>
              </div>

              {/* Execution Telemetry Grid */}
              <div className="grid grid-cols-2 sm:grid-cols-4 gap-2.5 text-xs">
                <div className="p-3 rounded-xl bg-surface-container-low border border-outline-variant/20">
                  <span className="text-outline text-[11px] block">Loại bài nộp</span>
                  <span className="font-semibold text-on-surface font-mono mt-0.5 block">
                    {submissionResult.submissionType || (submissionMode === 'zip' ? 'ZIP' : 'SINGLE_FILE')}
                  </span>
                </div>

                {submissionResult.fileCount !== undefined && submissionResult.fileCount !== null && (
                  <div className="p-3 rounded-xl bg-surface-container-low border border-outline-variant/20">
                    <span className="text-outline text-[11px] block">Số file nguồn</span>
                    <span className="font-semibold text-on-surface font-mono mt-0.5 block">
                      {submissionResult.fileCount} files
                    </span>
                  </div>
                )}

                {submissionResult.entryPointClass && (
                  <div className="p-3 rounded-xl bg-surface-container-low border border-outline-variant/20 col-span-2">
                    <span className="text-outline text-[11px] block">Entry Point (Main Class)</span>
                    <span className="font-semibold text-on-surface font-mono mt-0.5 truncate block" title={submissionResult.entryPointClass}>
                      {submissionResult.entryPointClass}
                    </span>
                  </div>
                )}

                {submissionResult.executionDurationMs !== undefined && submissionResult.executionDurationMs !== null && (
                  <div className="p-3 rounded-xl bg-surface-container-low border border-outline-variant/20">
                    <span className="text-outline text-[11px] block">Thời gian chạy</span>
                    <span className="font-semibold text-on-surface font-mono mt-0.5 block">
                      {submissionResult.executionDurationMs} ms
                    </span>
                  </div>
                )}
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

              {/* Standard Output (stdout) */}
              {submissionResult.stdout && (
                <div className="space-y-1.5">
                  <div className="flex items-center justify-between text-xs text-outline font-medium">
                    <span className="flex items-center gap-1.5">
                      <Terminal className="w-3.5 h-3.5 text-emerald-600" />
                      <span>Kết quả đầu ra tiêu chuẩn (stdout):</span>
                    </span>
                    <button
                      type="button"
                      onClick={() => copyToClipboard(submissionResult.stdout || '', 'stdout')}
                      className="hover:text-on-surface transition-colors flex items-center gap-1"
                    >
                      {copiedSection === 'stdout' ? <Check className="w-3 h-3 text-emerald-600" /> : <Copy className="w-3 h-3" />}
                      <span>{copiedSection === 'stdout' ? 'Đã sao chép' : 'Sao chép'}</span>
                    </button>
                  </div>
                  <pre className="p-3 bg-slate-950 text-emerald-300 rounded-xl font-mono text-xs overflow-x-auto max-h-36 leading-relaxed">
                    {submissionResult.stdout}
                  </pre>
                </div>
              )}

              {/* Standard Error (stderr) */}
              {submissionResult.stderr && (
                <div className="space-y-1.5">
                  <div className="flex items-center justify-between text-xs text-outline font-medium">
                    <span className="flex items-center gap-1.5">
                      <AlertCircle className="w-3.5 h-3.5 text-rose-500" />
                      <span>Nhật ký lỗi (stderr):</span>
                    </span>
                    <button
                      type="button"
                      onClick={() => copyToClipboard(submissionResult.stderr || '', 'stderr')}
                      className="hover:text-on-surface transition-colors flex items-center gap-1"
                    >
                      {copiedSection === 'stderr' ? <Check className="w-3 h-3 text-emerald-600" /> : <Copy className="w-3 h-3" />}
                      <span>{copiedSection === 'stderr' ? 'Đã sao chép' : 'Sao chép'}</span>
                    </button>
                  </div>
                  <pre className="p-3 bg-slate-950 text-rose-300 rounded-xl font-mono text-xs overflow-x-auto max-h-36 leading-relaxed">
                    {submissionResult.stderr}
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
