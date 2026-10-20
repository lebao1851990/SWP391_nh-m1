'use client';

import React, { useState, useEffect, useMemo } from 'react';
import { useRouter } from 'next/navigation';
import { useStudentExam, useSubmitExamZip } from '../hooks/use-student-exams';
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
  X,
  Cpu
} from 'lucide-react';
import { toast } from 'sonner';

interface StudentExamDetailViewProps {
  examId: string;
}

export function StudentExamDetailView({ examId }: StudentExamDetailViewProps) {
  const router = useRouter();
  const { t } = useTranslation();
  const { data: exam, isLoading, isError, error } = useStudentExam(examId);
  const submitExamZip = useSubmitExamZip();
  
  const [zipFile, setZipFile] = useState<File | null>(null);
  const [isDragging, setIsDragging] = useState(false);
  const [localError, setLocalError] = useState('');
  const [showConfirmModal, setShowConfirmModal] = useState(false);
  const [submissionPhase, setSubmissionPhase] = useState<'idle' | 'uploading' | 'processing' | 'completed' | 'failed'>('idle');

  // Real-time server-synchronized countdown timer
  const [remainingSeconds, setRemainingSeconds] = useState<number | null>(null);

  useEffect(() => {
    if (!exam?.endTime) return;

    // Calculate server offset: serverTimestamp - localTimestamp
    // Client clock adjustment will NOT alter real deadline calculation!
    const serverTimestamp = exam.serverTime ? new Date(exam.serverTime).getTime() : Date.now();
    const clientOffset = serverTimestamp - Date.now();
    const endTimestamp = new Date(exam.endTime).getTime();

    const updateTimer = () => {
      const currentServerTime = Date.now() + clientOffset;
      const diffSec = Math.max(0, Math.floor((endTimestamp - currentServerTime) / 1000));
      setRemainingSeconds(diffSec);
    };

    updateTimer();
    const interval = setInterval(updateTimer, 1000);
    return () => clearInterval(interval);
  }, [exam?.endTime, exam?.serverTime]);

  const isExpiredByTimer = remainingSeconds !== null && remainingSeconds <= 0;
  const isLocked = Boolean(exam?.isExpired) || isExpiredByTimer || exam?.status === 'completed' || exam?.status === 'missed';

  const is5MinWarning = !isLocked && remainingSeconds !== null && remainingSeconds <= 300 && remainingSeconds > 60;
  const is1MinWarning = !isLocked && remainingSeconds !== null && remainingSeconds <= 60 && remainingSeconds > 0;

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

  const isSubmitting = submitExamZip.isPending || submissionPhase === 'uploading' || submissionPhase === 'processing';

  const handleFileSelect = (file: File | null) => {
    if (!file) {
      setZipFile(null);
      return;
    }
    if (!file.name.toLowerCase().endsWith('.zip')) {
      setLocalError('Định dạng tệp không hợp lệ. Vui lòng chỉ chọn tệp nén .zip');
      toast.error('Chỉ chấp nhận tệp nén .zip');
      return;
    }
    if (file.size > 10 * 1024 * 1024) {
      setLocalError('Kích thước tệp vượt quá 10 MB cho phép.');
      toast.error('Tệp quá lớn (tối đa 10 MB)');
      return;
    }
    setLocalError('');
    setZipFile(file);
  };

  const handlePreSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (isLocked) {
      setLocalError(t('studentExams.timeExpiredDesc'));
      toast.error(t('studentExams.timeExpiredTitle'));
      return;
    }
    if (!zipFile) {
      setLocalError('Vui lòng chọn hoặc kéo thả file .zip chứa mã nguồn Java.');
      return;
    }
    if (!zipFile.name.toLowerCase().endsWith('.zip')) {
      setLocalError('Định dạng tệp không hợp lệ. Chỉ chấp nhận tệp có đuôi .zip.');
      return;
    }
    if (zipFile.size > 10 * 1024 * 1024) {
      setLocalError('Kích thước tệp vượt quá 10 MB cho phép.');
      return;
    }
    setLocalError('');
    setShowConfirmModal(true);
  };

  const handleFinalSubmit = () => {
    setShowConfirmModal(false);
    if (!zipFile) return;

    setSubmissionPhase('uploading');
    submitExamZip.mutate({ examId, file: zipFile }, {
      onSuccess: (data: { id?: string }) => {
        setSubmissionPhase('processing');
        toast.success('Nộp bài thành công! Đang chuyển đến trang kết quả chấm...');
        setTimeout(() => {
          setSubmissionPhase('completed');
          if (data && data.id) {
            router.push(`/student/submissions/${data.id}`);
          } else {
            router.push('/student/submissions');
          }
        }, 1000);
      },
      onError: (err: unknown) => {
        setSubmissionPhase('failed');
        const axiosErr = err as AxiosError<{ message?: string; error?: string }>;
        let msg = axiosErr?.response?.data?.message || (err as Error)?.message || t('studentExams.submitError');
        if (axiosErr?.response?.status === 409 || axiosErr?.response?.data?.error === 'EXAM_TIME_EXPIRED' || msg === 'EXAM_TIME_EXPIRED') {
          msg = t('errors.EXAM_TIME_EXPIRED');
        }
        setLocalError(msg);
        toast.error(msg);
      }
    });
  };

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
                !isLocked && exam.status === 'active'
                  ? 'bg-emerald-50 text-emerald-700 border border-emerald-200' 
                  : 'bg-rose-50 text-rose-700 border border-rose-200'
              }`}>
                {!isLocked && exam.status === 'active' && (
                  <span className="relative flex h-2 w-2">
                    <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75"></span>
                    <span className="relative inline-flex rounded-full h-2 w-2 bg-emerald-500"></span>
                  </span>
                )}
                <span>
                  {isLocked ? t('common.archived') : exam.status === 'active' ? t('common.active') : exam.status.toUpperCase()}
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
                <span>Hạn chót: {new Date(exam.endTime).toLocaleString()}</span>
                {isLocked && <span className="text-rose-600 font-semibold ml-1">(Đã đóng)</span>}
              </span>
            </div>
          </div>

          {/* Real-Time Countdown Timer Widget */}
          <div className="flex items-center self-start md:self-center">
            <div className={`px-4 py-2 rounded-xl border flex items-center gap-3 shadow-xs transition-all ${
              isLocked
                ? 'bg-rose-50 border-rose-200 text-rose-700'
                : is1MinWarning
                ? 'bg-rose-100 border-rose-300 text-rose-800 animate-pulse'
                : is5MinWarning
                ? 'bg-amber-50 border-amber-300 text-amber-800'
                : 'bg-surface-container-lowest border-outline-variant/40 text-on-surface'
            }`}>
              <div className={`p-1.5 rounded-lg ${isLocked ? 'bg-rose-100 text-rose-600' : is1MinWarning ? 'bg-rose-200 text-rose-700' : 'bg-primary/10 text-primary'}`}>
                {isLocked ? <Lock className="w-4 h-4" /> : <Timer className="w-4 h-4" />}
              </div>
              <div className="flex flex-col">
                <span className="text-[10px] uppercase font-mono tracking-wider font-semibold opacity-75">
                  {t('studentExams.remainingTime')}
                </span>
                <span className="font-mono text-base font-bold tracking-tight">
                  {isLocked ? '00:00:00' : formattedCountdown}
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
              <p className="font-bold">{t('studentExams.warning1min')}</p>
            </div>
          </div>
        )}

        {/* 5-Minute Warning Banner */}
        {is5MinWarning && (
          <div className="p-3.5 rounded-xl bg-amber-50 border border-amber-300 text-amber-900 text-xs flex items-center gap-3 shadow-xs">
            <AlertCircle className="w-5 h-5 text-amber-600 shrink-0" />
            <div className="flex-1">
              <p className="font-semibold">{t('studentExams.warning5min')}</p>
            </div>
          </div>
        )}

        {/* Closed/Expired Exam Alert Banner */}
        {isLocked && (
          <div className="p-4 rounded-xl bg-rose-50 border border-rose-200 text-rose-800 text-xs flex items-center gap-3 shadow-sm">
            <Lock className="w-5 h-5 text-rose-600 shrink-0" />
            <div>
              <p className="font-bold text-sm">{t('studentExams.timeExpiredTitle')}</p>
              <p className="text-rose-700 mt-0.5">{t('studentExams.timeExpiredDesc')}</p>
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

        {/* Active Submission Pipeline Status Card */}
        {submissionPhase !== 'idle' && (
          <div className="p-4 rounded-2xl bg-surface-container-lowest border border-primary/30 shadow-md">
            <div className="flex items-center gap-3 mb-3">
              <div className="p-2 rounded-xl bg-primary/10 text-primary">
                {submissionPhase === 'completed' ? (
                  <CheckCircle2 className="w-5 h-5 text-emerald-600" />
                ) : submissionPhase === 'failed' ? (
                  <AlertCircle className="w-5 h-5 text-rose-600" />
                ) : (
                  <Loader2 className="w-5 h-5 animate-spin text-primary" />
                )}
              </div>
              <div>
                <h4 className="text-xs font-bold text-on-surface">Tiến trình nộp bài &amp; Chấm tự động</h4>
                <p className="text-[11px] text-outline">
                  {submissionPhase === 'uploading' && 'Đang tải tệp ZIP lên máy chủ an toàn...'}
                  {submissionPhase === 'processing' && 'Đã nhận bài! Đang chuyển bài nộp vào Docker Sandbox để chạy test case...'}
                  {submissionPhase === 'completed' && 'Hoàn tất! Đang chuyển bạn đến trang chi tiết kết quả...'}
                  {submissionPhase === 'failed' && 'Xử lý nộp bài không thành công. Vui lòng kiểm tra lại.'}
                </p>
              </div>
            </div>

            {/* Stepper Progress */}
            <div className="grid grid-cols-3 gap-2 text-center text-[11px]">
              <div className={`p-2 rounded-lg border ${
                submissionPhase === 'uploading'
                  ? 'bg-primary/10 border-primary text-primary font-bold'
                  : 'bg-surface-container border-outline-variant/30 text-on-surface-variant'
              }`}>
                1. Tải lên ZIP
              </div>
              <div className={`p-2 rounded-lg border ${
                submissionPhase === 'processing'
                  ? 'bg-primary/10 border-primary text-primary font-bold animate-pulse'
                  : 'bg-surface-container border-outline-variant/30 text-on-surface-variant'
              }`}>
                2. Chấm Sandbox &amp; AST
              </div>
              <div className={`p-2 rounded-lg border ${
                submissionPhase === 'completed'
                  ? 'bg-emerald-50 border-emerald-300 text-emerald-700 font-bold'
                  : 'bg-surface-container border-outline-variant/30 text-on-surface-variant'
              }`}>
                3. Hoàn tất
              </div>
            </div>
          </div>
        )}

        {/* Submission Archive Card - Strictly ZIP Submission */}
        <div className="bg-surface-container-lowest p-space-md rounded-2xl border border-outline-variant/40 shadow-xs">
          <form onSubmit={handlePreSubmit} className="flex flex-col gap-4">
            <div className="flex flex-col gap-3">
              <div className="flex items-center justify-between border-b border-outline-variant/30 pb-3">
                <div className="flex items-center gap-2">
                  <div className="p-2 rounded-lg bg-primary/10 text-primary">
                    <FileArchive className="w-5 h-5" />
                  </div>
                  <div>
                    <h2 className="font-headline-sm text-headline-sm text-on-surface font-semibold">Nộp bài thi bằng tệp .ZIP</h2>
                    <p className="text-[11px] text-outline">Chỉ chấp nhận gói mã nguồn Java (.zip). Hệ thống tự động giải nén, biên dịch và chạy test trong Docker Sandbox.</p>
                  </div>
                </div>
                <span className="px-2.5 py-1 rounded bg-surface-container text-on-surface-variant font-mono text-xs font-semibold">Tối đa 10 MB</span>
              </div>

              {!zipFile ? (
                <label
                  onDragOver={(e) => {
                    e.preventDefault();
                    if (!isLocked && !isSubmitting) setIsDragging(true);
                  }}
                  onDragLeave={() => setIsDragging(false)}
                  onDrop={(e) => {
                    e.preventDefault();
                    setIsDragging(false);
                    if (isLocked || isSubmitting) return;
                    const file = e.dataTransfer.files?.[0];
                    if (file) handleFileSelect(file);
                  }}
                  className={`flex flex-col items-center justify-center border-2 border-dashed rounded-2xl p-10 transition-all cursor-pointer ${
                    isLocked || isSubmitting
                      ? 'opacity-60 border-outline-variant/40 bg-surface-container-low cursor-not-allowed'
                      : isDragging
                      ? 'border-primary bg-primary/5 ring-4 ring-primary/10'
                      : 'border-outline-variant hover:border-primary/60 hover:bg-surface-container-lowest bg-surface-container-lowest'
                  }`}
                >
                  <input
                    type="file"
                    accept=".zip,application/zip,application/x-zip-compressed"
                    className="hidden"
                    disabled={isSubmitting || isLocked}
                    onChange={(e) => {
                      const file = e.target.files?.[0];
                      if (file) handleFileSelect(file);
                    }}
                  />
                  <div className="p-4 rounded-2xl bg-primary/10 text-primary mb-3">
                    <UploadCloud className="w-8 h-8" />
                  </div>
                  <span className="font-semibold text-sm text-on-surface mb-1">
                    Kéo thả file .zip vào đây hoặc nhấn để chọn tệp
                  </span>
                  <span className="text-xs text-outline">
                    Chỉ chấp nhận định dạng .zip chứa các file mã nguồn Java (.java)
                  </span>
                  <div className="flex flex-wrap items-center justify-center gap-3 mt-3 text-[11px] text-outline-variant font-mono">
                    <span className="px-2 py-0.5 rounded bg-surface-container">Kích thước nén &le; 10 MB</span>
                    <span className="px-2 py-0.5 rounded bg-surface-container">Giải nén an toàn &le; 50 MB</span>
                    <span className="px-2 py-0.5 rounded bg-surface-container">Tối đa 200 file</span>
                  </div>
                </label>
              ) : (
                <div className="p-4 rounded-xl border border-primary/30 bg-primary/5 flex items-center justify-between">
                  <div className="flex items-center gap-3">
                    <div className="p-3 rounded-xl bg-primary text-on-primary shadow-sm">
                      <FileArchive className="w-6 h-6" />
                    </div>
                    <div>
                      <p className="text-xs font-semibold text-on-surface truncate max-w-sm sm:max-w-md font-mono">{zipFile.name}</p>
                      <div className="flex items-center gap-2 text-[11px] text-outline font-mono mt-0.5">
                        <span>{(zipFile.size / 1024).toFixed(1)} KB</span>
                        <span>•</span>
                        <span className="text-emerald-600 font-semibold flex items-center gap-1">
                          <CheckCircle2 className="w-3 h-3" /> Tệp .ZIP hợp lệ
                        </span>
                      </div>
                    </div>
                  </div>
                  {!isLocked && !isSubmitting && (
                    <button
                      type="button"
                      onClick={() => setZipFile(null)}
                      className="p-1.5 rounded-lg hover:bg-surface-container text-outline hover:text-rose-600 transition-colors"
                      title="Hủy chọn file"
                    >
                      <X className="w-4 h-4" />
                    </button>
                  )}
                </div>
              )}
            </div>

            {localError && (
              <div className="p-3 rounded-lg bg-rose-50 border border-rose-200 text-rose-700 flex items-center gap-2 text-xs">
                <AlertCircle className="w-4 h-4 shrink-0" />
                <span>{localError}</span>
              </div>
            )}

            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pt-2">
              <div className="text-xs text-outline flex items-center gap-1.5">
                <ShieldAlert className="w-3.5 h-3.5 text-amber-600 shrink-0" />
                <span>Mã nguồn sẽ được cô lập trong Sandbox không có mạng ngoài, bảo vệ Zip Slip và timeout tự động.</span>
              </div>

              <div className="flex items-center gap-3 self-end sm:self-auto">
                <button 
                  type="button"
                  className="px-4 py-2 text-xs rounded-lg border border-outline-variant/40 hover:bg-surface-container transition-colors cursor-pointer"
                  onClick={() => router.push('/student/exams')}
                  disabled={isSubmitting}
                >
                  {t('common.cancel')}
                </button>
                
                <button 
                  type="submit"
                  id="submit-student-exam-btn"
                  className="flex items-center gap-2 px-6 py-2 bg-primary text-on-primary rounded-lg text-xs font-medium hover:bg-primary/90 transition-colors disabled:opacity-50 disabled:cursor-not-allowed cursor-pointer shadow-sm"
                  disabled={isSubmitting || isLocked || !zipFile}
                >
                  {isSubmitting ? (
                    <>
                      <Loader2 className="w-3.5 h-3.5 animate-spin" />
                      <span>{submissionPhase === 'processing' ? 'Đang chấm bài...' : 'Đang tải lên...'}</span>
                    </>
                  ) : (
                    <>
                      <Send className="w-3.5 h-3.5" />
                      <span>{t('studentExams.submitButton')}</span>
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
                  <h3 className="font-headline-sm text-headline-sm font-semibold">{t('studentExams.confirmModalTitle')}</h3>
                  <p className="text-xs text-on-surface-variant">Xác nhận gửi gói mã nguồn vào hệ thống tự động chấm điểm</p>
                </div>
              </div>

              <div className="p-3.5 rounded-lg bg-surface-container-low border border-outline-variant/30 text-xs text-on-surface-variant space-y-1.5">
                <div className="flex justify-between">
                  <span>Loại bài nộp:</span>
                  <span className="font-semibold text-primary font-mono">Tệp nén .ZIP</span>
                </div>
                <div className="flex justify-between">
                  <span>Tên tệp:</span>
                  <span className="font-mono font-semibold text-on-surface truncate max-w-[200px]">{zipFile?.name}</span>
                </div>
                <div className="flex justify-between">
                  <span>Dung lượng:</span>
                  <span className="font-mono font-semibold text-on-surface">{zipFile ? `${(zipFile.size / 1024).toFixed(1)} KB` : '0 KB'}</span>
                </div>
                <div className="flex justify-between">
                  <span>Môi trường biên dịch:</span>
                  <span className="font-mono font-semibold text-on-surface">Docker Sandbox (OpenJDK 17/21)</span>
                </div>
                <div className="flex justify-between">
                  <span>Pipeline tiếp theo:</span>
                  <span className="font-semibold text-emerald-700">Chạy Test Cases &amp; Kiểm tra đạo văn AST</span>
                </div>
              </div>

              <p className="text-xs text-outline leading-relaxed">
                Sau khi xác nhận nộp, hệ thống sẽ tiến hành tự động giải nén an toàn, chạy các bài kiểm thử và tính điểm. Bạn có thể theo dõi tiến trình ngay sau khi gửi.
              </p>

              <div className="flex justify-end gap-3 pt-2 border-t border-outline-variant/30">
                <button
                  type="button"
                  onClick={() => setShowConfirmModal(false)}
                  className="px-4 py-2 text-xs rounded-lg border border-outline-variant/40 hover:bg-surface-container transition-colors cursor-pointer"
                >
                  {t('common.cancel')}
                </button>
                <button
                  type="button"
                  onClick={handleFinalSubmit}
                  disabled={isSubmitting}
                  className="px-5 py-2 bg-primary text-on-primary rounded-lg text-xs font-medium hover:bg-primary/90 transition-colors cursor-pointer shadow-sm flex items-center gap-2"
                >
                  {isSubmitting ? (
                    <>
                      <Loader2 className="w-3.5 h-3.5 animate-spin" />
                      <span>{t('studentExams.submitting')}</span>
                    </>
                  ) : (
                    <>
                      <CheckCircle2 className="w-3.5 h-3.5" />
                      <span>{t('common.confirm')}</span>
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
