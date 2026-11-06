'use client';

import React, { useState } from 'react';
import { AppLink as Link } from '@/components/ui/app-link';
import {
  useTeacherGradingDetail,
  useApproveGrade,
  useRequestRevision,
  usePublishGrade,
} from '../hooks';
import { useTeacherSubmissionFiles } from '@/features/exams/hooks/use-teacher-exams';
import { teacherExamsApi } from '@/features/exams/api/teacher-exams.api';
import {
  ArrowLeft,
  CheckCircle2,
  AlertCircle,
  Clock,
  Download,
  FileCode,
  FileText,
  SendHorizontal,
  RotateCcw,
  ShieldCheck,
  Award,
  Terminal,
  Calendar,
  Layers,
  History,
  X,
  Lock,
} from 'lucide-react';
import { toast } from 'sonner';

export function TeacherGradingDetailView({ submissionId }: { submissionId: string }) {
  const { data: detail, isLoading, isError, error, refetch } = useTeacherGradingDetail(submissionId);
  const { data: files } = useTeacherSubmissionFiles(submissionId);

  const approveMutation = useApproveGrade(submissionId);
  const revisionMutation = useRequestRevision(submissionId);
  const publishMutation = usePublishGrade(submissionId);

  const [selectedFileIdx, setSelectedFileIdx] = useState<number>(0);
  const [isDownloading, setIsDownloading] = useState<boolean>(false);

  // Modals state
  const [showApproveModal, setShowApproveModal] = useState<boolean>(false);
  const [approveNotes, setApproveNotes] = useState<string>('');

  const [showRevisionModal, setShowRevisionModal] = useState<boolean>(false);
  const [revisionReason, setRevisionReason] = useState<string>('');

  const [showPublishModal, setShowPublishModal] = useState<boolean>(false);

  const handleDownloadZip = async () => {
    try {
      setIsDownloading(true);
      const { blob, filename } = await teacherExamsApi.downloadSubmissionZip(submissionId);
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = filename || `submission-${submissionId}.zip`;
      document.body.appendChild(a);
      a.click();
      window.URL.revokeObjectURL(url);
      document.body.removeChild(a);
      toast.success('Đã tải xuống file ZIP bài nộp');
    } catch (err) {
      console.error('Download failed:', err);
      toast.error('Không thể tải file ZIP bài nộp');
    } finally {
      setIsDownloading(false);
    }
  };

  const handleConfirmApprove = () => {
    approveMutation.mutate(
      { notes: approveNotes.trim() },
      {
        onSuccess: () => {
          setShowApproveModal(false);
          setApproveNotes('');
          toast.success('Đã phê duyệt kết quả đánh giá thành công! Bài nộp hiện đã đủ điều kiện công bố.');
          refetch();
        },
        onError: (err: unknown) => {
          const msg = (err as { response?: { data?: { message?: string } }; message?: string })?.response?.data?.message || (err as { message?: string })?.message || 'Không thể phê duyệt điểm.';
          toast.error(msg);
        },
      }
    );
  };

  const handleConfirmRevision = () => {
    if (!revisionReason.trim() || revisionReason.trim().length < 10) {
      toast.error('Lý do yêu cầu làm lại/chỉnh sửa phải có ít nhất 10 ký tự.');
      return;
    }

    revisionMutation.mutate(
      { reason: revisionReason.trim() },
      {
        onSuccess: () => {
          setShowRevisionModal(false);
          setRevisionReason('');
          toast.success('Đã ghi nhận yêu cầu chỉnh sửa/làm lại.');
          refetch();
        },
        onError: (err: unknown) => {
          const msg = (err as { response?: { data?: { message?: string } }; message?: string })?.response?.data?.message || (err as { message?: string })?.message || 'Không thể yêu cầu chỉnh sửa.';
          toast.error(msg);
        },
      }
    );
  };

  const handleConfirmPublish = () => {
    publishMutation.mutate(undefined, {
      onSuccess: () => {
        setShowPublishModal(false);
        toast.success('Đã công bố điểm chính thức cho sinh viên thành công!');
        refetch();
      },
      onError: (err: unknown) => {
        const msg = (err as { response?: { data?: { message?: string } }; message?: string })?.response?.data?.message || (err as { message?: string })?.message || 'Không thể công bố điểm.';
        toast.error(msg);
      },
    });
  };

  if (isLoading) {
    return (
      <div className="flex flex-col h-72 items-center justify-center gap-3">
        <div className="w-8 h-8 rounded-full border-2 border-primary border-t-transparent animate-spin" />
        <p className="text-xs text-on-surface-variant font-medium">Đang tải hồ sơ bài nộp và dữ liệu chấm...</p>
      </div>
    );
  }

  if (isError || !detail) {
    return (
      <div className="max-w-xl mx-auto my-12 p-8 rounded-2xl bg-surface-container-lowest border border-outline-variant/40 shadow-lg text-center flex flex-col items-center gap-3">
        <AlertCircle className="w-8 h-8 text-rose-600" />
        <h2 className="text-base font-semibold text-on-surface">Không tìm thấy bài nộp hoặc quyền truy cập bị từ chối</h2>
        <p className="text-xs text-on-surface-variant">
          {(error as Error)?.message || 'Bạn không có quyền thẩm định bài thi này.'}
        </p>
        <Link href="/teacher/grading" className="mt-2 px-4 py-2 bg-primary text-on-primary rounded-lg text-xs font-medium">
          Quay lại danh sách thẩm định
        </Link>
      </div>
    );
  }

  const isApproved = detail.reviewStatus === 'APPROVED';
  const isRevision = detail.reviewStatus === 'REVISION_REQUIRED';
  const isPending = detail.reviewStatus === 'PENDING_REVIEW';
  const isLegacy = detail.reviewStatus === 'LEGACY_AUTO';
  const isPublished = detail.publicationStatus === 'PUBLISHED';

  const fileList = files || [];
  const activeFile = fileList[selectedFileIdx];

  return (
    <div className="max-w-7xl mx-auto px-space-lg py-space-lg flex flex-col w-full gap-space-lg">
      {/* Top Bar */}
      <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-space-md pb-space-md border-b border-outline-variant/30">
        <div className="space-y-1">
          <nav className="flex items-center gap-2 text-on-surface-variant text-xs mb-1">
            <Link className="hover:text-primary transition-colors flex items-center gap-1 font-medium" href="/teacher/grading">
              <ArrowLeft className="w-3.5 h-3.5" />
              <span>Duyệt điểm &amp; Thẩm định</span>
            </Link>
            <span className="text-outline">/</span>
            <span className="text-primary font-semibold font-mono">#{detail.submissionId.substring(0, 8)}</span>
          </nav>
          <div className="flex items-center gap-space-sm flex-wrap">
            <h1 className="font-headline-lg text-headline-lg text-on-surface tracking-tight">
              Thẩm định bài thi: {detail.examTitle}
            </h1>
            {isPending && (
              <span className="text-xs px-2.5 py-0.5 rounded-full bg-amber-50 text-amber-800 border border-amber-200 font-semibold flex items-center gap-1">
                <Clock className="w-3.5 h-3.5 text-amber-600 animate-pulse" />
                <span>Chờ giảng viên duyệt</span>
              </span>
            )}
            {isApproved && (
              <span className="text-xs px-2.5 py-0.5 rounded-full bg-blue-50 text-blue-800 border border-blue-200 font-semibold flex items-center gap-1">
                <CheckCircle2 className="w-3.5 h-3.5 text-blue-600" />
                <span>Đã phê duyệt ({detail.approvedScore} đ)</span>
              </span>
            )}
            {isLegacy && (
              <span className="text-xs px-2.5 py-0.5 rounded-full bg-purple-50 text-purple-800 border border-purple-200 font-semibold flex items-center gap-1">
                <CheckCircle2 className="w-3.5 h-3.5 text-purple-600" />
                <span>Tự động (Legacy V21)</span>
              </span>
            )}
            {isRevision && (
              <span className="text-xs px-2.5 py-0.5 rounded-full bg-rose-50 text-rose-800 border border-rose-200 font-semibold flex items-center gap-1">
                <AlertCircle className="w-3.5 h-3.5 text-rose-600" />
                <span>Yêu cầu sửa đổi</span>
              </span>
            )}
            {isPublished ? (
              <span className="text-xs px-2.5 py-0.5 rounded-full bg-emerald-50 text-emerald-800 border border-emerald-200 font-semibold flex items-center gap-1">
                <CheckCircle2 className="w-3.5 h-3.5 text-emerald-600" />
                <span>Đã công bố</span>
              </span>
            ) : (
              <span className="text-xs px-2.5 py-0.5 rounded-full bg-slate-100 text-slate-700 border border-slate-200 font-medium">
                Chưa công bố cho SV
              </span>
            )}
          </div>
          <p className="text-xs text-on-surface-variant">
            Sinh viên: <strong className="text-on-surface">{detail.studentName || 'Sinh viên'}</strong> ({detail.studentEmail}) • Nộp lúc: {new Date(detail.submissionTime).toLocaleString('vi-VN')}
          </p>
        </div>

        {/* Action Buttons Bar */}
        <div className="flex items-center gap-2 self-start lg:self-auto flex-wrap">
          <button
            onClick={handleDownloadZip}
            disabled={isDownloading}
            className="px-3 py-2 bg-surface-container hover:bg-surface-container-high text-on-surface border border-outline-variant/40 rounded-lg text-xs font-medium flex items-center gap-1.5 transition-colors disabled:opacity-50 cursor-pointer shadow-xs"
          >
            <Download className="w-3.5 h-3.5 text-primary" />
            <span>{isDownloading ? 'Đang tải...' : 'Tải ZIP'}</span>
          </button>

          <button
            onClick={() => setShowRevisionModal(true)}
            className="px-3.5 py-2 bg-rose-50 hover:bg-rose-100 text-rose-800 border border-rose-200 rounded-lg text-xs font-semibold flex items-center gap-1.5 transition-colors cursor-pointer shadow-xs"
          >
            <RotateCcw className="w-3.5 h-3.5 text-rose-600" />
            <span>Yêu cầu làm lại</span>
          </button>

          <button
            onClick={() => setShowApproveModal(true)}
            className="px-3.5 py-2 bg-blue-50 hover:bg-blue-100 text-blue-800 border border-blue-200 rounded-lg text-xs font-semibold flex items-center gap-1.5 transition-colors cursor-pointer shadow-xs"
          >
            <CheckCircle2 className="w-3.5 h-3.5 text-blue-600" />
            <span>Phê duyệt điểm</span>
          </button>

          <button
            onClick={() => setShowPublishModal(true)}
            disabled={!isApproved}
            className="px-4 py-2 bg-emerald-600 hover:bg-emerald-700 text-white rounded-lg text-xs font-semibold flex items-center gap-1.5 transition-colors disabled:opacity-50 disabled:cursor-not-allowed cursor-pointer shadow-sm"
          >
            <SendHorizontal className="w-3.5 h-3.5" />
            <span>Công bố điểm cho SV</span>
          </button>
        </div>
      </div>

      {/* Score Summary Banner */}
      <div className="rounded-2xl bg-surface-container-lowest border border-outline-variant/40 p-5 shadow-xs flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div className="flex flex-col gap-1.5">
          <div className="flex items-center gap-2">
            <Award className="w-5 h-5 text-primary" />
            <span className="font-semibold text-xs uppercase tracking-wider text-outline">
              Điểm Đánh Giá &amp; Thẩm Định
            </span>
            {detail.overrideScore != null && (
              <span className="text-[11px] px-2 py-0.5 rounded-full font-semibold bg-amber-50 text-amber-700 border border-amber-200">
                Có điểm điều chỉnh ghi đè
              </span>
            )}
          </div>
          <div className="flex items-baseline gap-2">
            <span className="font-display-lg text-5xl font-bold text-primary font-mono tracking-tight">
              {detail.effectiveScore ?? detail.provisionalScore ?? '0.00'}
            </span>
            <span className="text-xl text-outline font-normal font-mono">
              / {detail.questionMaxScore} điểm
            </span>
          </div>
          <div className="flex items-center gap-3 text-xs text-on-surface-variant flex-wrap">
            <span>Điểm tự động: <strong>{detail.autoScore ?? '0.00'} đ</strong></span>
            <span>•</span>
            <span>Test cases: <strong>{detail.testCasesPassed ?? 0} / {detail.testCasesTotal ?? 0}</strong></span>
            <span>•</span>
            <span>Phiên bản điểm: <strong>v{detail.scoreVersion}</strong></span>
          </div>
        </div>

        {/* Status Box */}
        <div className="flex flex-col gap-1.5 md:text-right border-t md:border-t-0 md:border-l border-outline-variant/30 pt-3 md:pt-0 md:pl-5 text-xs text-on-surface-variant">
          <div>
            Trạng thái duyệt: <strong className="text-on-surface">{detail.reviewStatus}</strong>
          </div>
          {detail.reviewedByName && (
            <div>
              Người duyệt: <strong>{detail.reviewedByName}</strong> ({new Date(detail.reviewedAt!).toLocaleDateString()})
            </div>
          )}
          <div>
            Trạng thái công bố: <strong className={isPublished ? 'text-emerald-700 font-semibold' : 'text-amber-700 font-semibold'}>
              {isPublished ? `ĐÃ CÔNG BỐ (${detail.publishedScore} đ)` : 'CHƯA CÔNG BỐ'}
            </strong>
          </div>
          {detail.publishedByName && (
            <div>
              Công bố bởi: <strong>{detail.publishedByName}</strong>
            </div>
          )}
        </div>
      </div>

      {/* Question Details Card */}
      {detail.questionTitle && (
        <div className="rounded-2xl bg-surface-container-lowest border border-outline-variant/40 p-5 shadow-xs flex flex-col gap-2">
          <div className="flex items-center justify-between">
            <h2 className="font-semibold text-sm text-on-surface flex items-center gap-2">
              <Layers className="w-4 h-4 text-primary" />
              <span>Nội dung Câu hỏi: {detail.questionTitle}</span>
            </h2>
            <span className="text-xs px-2.5 py-0.5 rounded-full bg-primary/10 text-primary font-semibold">
              Trọng số: {detail.questionMaxScore} điểm
            </span>
          </div>
          {detail.questionDescription && (
            <div className="text-xs text-on-surface-variant whitespace-pre-line bg-surface-container-low p-3.5 rounded-xl border border-outline-variant/20 max-h-48 overflow-y-auto font-sans leading-relaxed">
              {detail.questionDescription}
            </div>
          )}
        </div>
      )}

      {/* Automated Test Case Suite (Full details for Teacher) */}
      <div className="flex flex-col gap-3">
        <div className="flex items-center justify-between">
          <h2 className="font-semibold text-sm text-on-surface flex items-center gap-2">
            <FileCode className="w-4 h-4 text-primary" />
            <span>Kết quả Kiểm thử Tự động ({detail.testResults?.length || 0} Test Cases)</span>
          </h2>
          <span className="text-xs text-outline font-medium">Giảng viên có quyền xem toàn bộ test case ẩn</span>
        </div>

        {detail.testResults && detail.testResults.length > 0 ? (
          <div className="space-y-3">
            {detail.testResults.map((tr, idx) => {
              const isPassed = tr.status === 'PASSED';
              return (
                <div
                  key={tr.id || idx}
                  className="rounded-xl border border-outline-variant/30 bg-surface-container-lowest p-4 shadow-xs flex flex-col gap-3"
                >
                  <div className="flex items-center justify-between flex-wrap gap-2">
                    <div className="flex items-center gap-2">
                      <span className={`w-2 h-2 rounded-full ${isPassed ? 'bg-emerald-500' : 'bg-rose-500'}`} />
                      <span className="font-semibold text-xs text-on-surface">
                        {tr.testCaseName || `Test Case #${idx + 1}`}
                      </span>
                      {tr.isHidden && (
                        <span className="px-2 py-0.5 rounded-full text-[10px] font-semibold bg-purple-50 text-purple-700 border border-purple-200 flex items-center gap-1">
                          <Lock className="w-2.5 h-2.5" />
                          <span>Test Case Ẩn</span>
                        </span>
                      )}
                    </div>
                    <div className="flex items-center gap-2">
                      <span className="text-xs font-mono font-bold text-primary">
                        {tr.pointsAwarded ?? 0} / {tr.maxPoints ?? 0} đ
                      </span>
                      <span
                        className={`px-2.5 py-0.5 rounded-full text-[11px] font-semibold ${
                          isPassed
                            ? 'bg-emerald-50 text-emerald-700 border border-emerald-200'
                            : 'bg-rose-50 text-rose-700 border border-rose-200'
                        }`}
                      >
                        {tr.status}
                      </span>
                    </div>
                  </div>

                  {/* Input / Expected / Actual */}
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-3 text-xs font-mono">
                    <div className="p-2.5 rounded-lg bg-surface-container-low border border-outline-variant/20 flex flex-col gap-1">
                      <span className="text-[10px] font-sans font-semibold text-outline uppercase tracking-wider">
                        Đầu ra mong đợi (Expected Output)
                      </span>
                      <pre className="whitespace-pre-wrap break-all text-on-surface max-h-32 overflow-y-auto">
                        {tr.expectedOutput || '(Trống)'}
                      </pre>
                    </div>

                    <div className="p-2.5 rounded-lg bg-surface-container-low border border-outline-variant/20 flex flex-col gap-1">
                      <span className="text-[10px] font-sans font-semibold text-outline uppercase tracking-wider">
                        Đầu ra thực tế (Actual Output)
                      </span>
                      <pre
                        className={`whitespace-pre-wrap break-all max-h-32 overflow-y-auto ${
                          isPassed ? 'text-emerald-700 font-semibold' : 'text-rose-700'
                        }`}
                      >
                        {tr.actualOutput || '(Trống)'}
                      </pre>
                    </div>
                  </div>

                  {tr.errorMessage && (
                    <div className="p-2.5 rounded-lg bg-rose-50 border border-rose-200 text-rose-900 text-xs font-mono whitespace-pre-wrap">
                      {tr.errorMessage}
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        ) : (
          <div className="p-6 rounded-xl border border-outline-variant/30 bg-surface-container-lowest text-center text-xs text-on-surface-variant">
            Chưa có thông tin chi tiết test case cho bài nộp này.
          </div>
        )}
      </div>

      {/* Compiler Output & Execution Logs */}
      {detail.compileOutput && (
        <div className="rounded-2xl bg-surface-container-lowest border border-outline-variant/40 p-5 shadow-xs flex flex-col gap-2">
          <h2 className="font-semibold text-sm text-on-surface flex items-center gap-2">
            <Terminal className="w-4 h-4 text-primary" />
            <span>Nhật ký Biên dịch &amp; Thực thi (Docker Sandbox)</span>
          </h2>
          <pre className="p-3.5 rounded-xl bg-slate-900 text-slate-100 text-xs font-mono whitespace-pre-wrap max-h-60 overflow-y-auto">
            {detail.compileOutput}
          </pre>
        </div>
      )}

      {/* Source Code File Inspector */}
      {fileList.length > 0 && (
        <div className="rounded-2xl bg-surface-container-lowest border border-outline-variant/40 p-5 shadow-xs flex flex-col gap-3">
          <div className="flex items-center justify-between flex-wrap gap-2">
            <h2 className="font-semibold text-sm text-on-surface flex items-center gap-2">
              <FileCode className="w-4 h-4 text-primary" />
              <span>Duyệt Mã Nguồn ({fileList.length} tệp)</span>
            </h2>
            <div className="flex items-center gap-1.5 overflow-x-auto pb-1 max-w-full">
              {fileList.map((f, idx) => (
                <button
                  key={f.filename || idx}
                  onClick={() => setSelectedFileIdx(idx)}
                  className={`px-3 py-1 rounded-lg text-xs font-mono font-medium transition-colors cursor-pointer whitespace-nowrap ${
                    selectedFileIdx === idx
                      ? 'bg-primary text-on-primary shadow-xs font-semibold'
                      : 'bg-surface-container hover:bg-surface-container-high text-on-surface border border-outline-variant/30'
                  }`}
                >
                  {f.filename}
                </button>
              ))}
            </div>
          </div>

          {activeFile && (
            <div className="flex flex-col rounded-xl border border-outline-variant/30 overflow-hidden">
              <div className="bg-surface-container px-3.5 py-2 text-xs font-mono text-outline flex items-center justify-between">
                <span>{activeFile.filename}</span>
                <span>{activeFile.content?.split('\n').length || 0} dòng</span>
              </div>
              <pre className="p-4 bg-slate-950 text-slate-100 font-mono text-xs overflow-x-auto max-h-96 leading-relaxed">
                <code>{activeFile.content}</code>
              </pre>
            </div>
          )}
        </div>
      )}

      {/* Grade Audit History */}
      {detail.auditLogs && detail.auditLogs.length > 0 && (
        <div className="rounded-2xl bg-surface-container-lowest border border-outline-variant/40 p-5 shadow-xs flex flex-col gap-3">
          <h2 className="font-semibold text-sm text-on-surface flex items-center gap-2">
            <History className="w-4 h-4 text-primary" />
            <span>Lịch sử Thẩm định &amp; Biến động Điểm (Audit Trail)</span>
          </h2>
          <div className="space-y-2.5">
            {detail.auditLogs.map((log) => (
              <div
                key={log.id}
                className="p-3 rounded-xl bg-surface-container-low border border-outline-variant/20 flex flex-col sm:flex-row sm:items-center justify-between gap-2 text-xs"
              >
                <div className="flex flex-col gap-0.5">
                  <div className="flex items-center gap-2">
                    <span className="font-semibold text-primary">{log.actionType}</span>
                    <span className="text-[11px] text-outline">bởi {log.actorName || log.actorEmail || 'Hệ thống'}</span>
                  </div>
                  {log.rationale && (
                    <span className="text-on-surface-variant text-[11px]">{log.rationale}</span>
                  )}
                </div>
                <div className="flex items-center gap-3 text-outline text-[11px] self-start sm:self-auto">
                  <span>Điểm: {log.postEffectiveScore ?? '—'} đ</span>
                  <span>{new Date(log.createdAt).toLocaleString('vi-VN')}</span>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* MODAL: Approve Grade */}
      {showApproveModal && (
        <div className="fixed inset-0 bg-black/50 backdrop-blur-xs z-50 flex items-center justify-center p-4">
          <div className="bg-surface-container-lowest rounded-2xl max-w-md w-full p-6 border border-outline-variant/40 shadow-xl flex flex-col gap-4">
            <div className="flex items-center justify-between">
              <h3 className="font-semibold text-base text-on-surface flex items-center gap-2">
                <CheckCircle2 className="w-5 h-5 text-blue-600" />
                <span>Xác nhận Phê duyệt Điểm</span>
              </h3>
              <button onClick={() => setShowApproveModal(false)} className="text-outline hover:text-on-surface">
                <X className="w-4 h-4" />
              </button>
            </div>
            <p className="text-xs text-on-surface-variant">
              Bạn đang phê duyệt kết quả đánh giá cho bài nộp của <strong>{detail.studentName}</strong> với số điểm: <strong>{detail.effectiveScore} / {detail.questionMaxScore} điểm</strong>.
            </p>
            <div className="flex flex-col gap-1.5">
              <label className="text-xs font-medium text-on-surface">Ghi chú duyệt điểm (Tùy chọn):</label>
              <textarea
                value={approveNotes}
                onChange={(e) => setApproveNotes(e.target.value)}
                placeholder="Nhập nhận xét hoặc ghi chú thẩm định nếu có..."
                rows={3}
                className="w-full p-2.5 bg-surface-container-low border border-outline-variant/40 rounded-xl text-xs text-on-surface focus:outline-none focus:ring-1 focus:ring-primary"
              />
            </div>
            <div className="flex justify-end gap-2 pt-2">
              <button
                onClick={() => setShowApproveModal(false)}
                className="px-4 py-2 rounded-lg bg-surface-container hover:bg-surface-container-high text-xs font-medium"
              >
                Hủy
              </button>
              <button
                onClick={handleConfirmApprove}
                disabled={approveMutation.isPending}
                className="px-4 py-2 rounded-lg bg-blue-600 hover:bg-blue-700 text-white text-xs font-semibold shadow-xs disabled:opacity-50"
              >
                {approveMutation.isPending ? 'Đang duyệt...' : 'Xác nhận Phê duyệt'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* MODAL: Request Revision */}
      {showRevisionModal && (
        <div className="fixed inset-0 bg-black/50 backdrop-blur-xs z-50 flex items-center justify-center p-4">
          <div className="bg-surface-container-lowest rounded-2xl max-w-md w-full p-6 border border-outline-variant/40 shadow-xl flex flex-col gap-4">
            <div className="flex items-center justify-between">
              <h3 className="font-semibold text-base text-on-surface flex items-center gap-2">
                <RotateCcw className="w-5 h-5 text-rose-600" />
                <span>Yêu cầu Chỉnh sửa / Làm lại</span>
              </h3>
              <button onClick={() => setShowRevisionModal(false)} className="text-outline hover:text-on-surface">
                <X className="w-4 h-4" />
              </button>
            </div>
            <p className="text-xs text-on-surface-variant">
              Bài thi sẽ chuyển sang trạng thái <strong>Cần sửa đổi (REVISION_REQUIRED)</strong> và không được công bố điểm. Sinh viên sẽ nhận được thông báo về lý do cần làm lại.
            </p>
            <div className="flex flex-col gap-1.5">
              <label className="text-xs font-medium text-on-surface">Lý do yêu cầu làm lại (Bắt buộc &ge; 10 ký tự):</label>
              <textarea
                value={revisionReason}
                onChange={(e) => setRevisionReason(e.target.value)}
                placeholder="Nêu rõ lý do hoặc chỉ dẫn cho sinh viên khắc phục..."
                rows={4}
                className="w-full p-2.5 bg-surface-container-low border border-outline-variant/40 rounded-xl text-xs text-on-surface focus:outline-none focus:ring-1 focus:ring-primary"
              />
            </div>
            <div className="flex justify-end gap-2 pt-2">
              <button
                onClick={() => setShowRevisionModal(false)}
                className="px-4 py-2 rounded-lg bg-surface-container hover:bg-surface-container-high text-xs font-medium"
              >
                Hủy
              </button>
              <button
                onClick={handleConfirmRevision}
                disabled={revisionMutation.isPending || revisionReason.trim().length < 10}
                className="px-4 py-2 rounded-lg bg-rose-600 hover:bg-rose-700 text-white text-xs font-semibold shadow-xs disabled:opacity-50"
              >
                {revisionMutation.isPending ? 'Đang gửi...' : 'Xác nhận Yêu cầu'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* MODAL: Publish Grade */}
      {showPublishModal && (
        <div className="fixed inset-0 bg-black/50 backdrop-blur-xs z-50 flex items-center justify-center p-4">
          <div className="bg-surface-container-lowest rounded-2xl max-w-md w-full p-6 border border-outline-variant/40 shadow-xl flex flex-col gap-4">
            <div className="flex items-center justify-between">
              <h3 className="font-semibold text-base text-on-surface flex items-center gap-2">
                <SendHorizontal className="w-5 h-5 text-emerald-600" />
                <span>Xác nhận Công bố Điểm Chính thức</span>
              </h3>
              <button onClick={() => setShowPublishModal(false)} className="text-outline hover:text-on-surface">
                <X className="w-4 h-4" />
              </button>
            </div>
            <div className="p-3.5 rounded-xl bg-amber-50 border border-amber-200 text-amber-900 text-xs flex items-start gap-2.5">
              <AlertCircle className="w-4 h-4 text-amber-600 shrink-0 mt-0.5" />
              <span>
                Sau khi công bố, sinh viên sẽ có thể xem kết quả đánh giá chính thức (<strong>{detail.approvedScore} điểm</strong>) và có quyền gửi đơn khiếu nại điểm nếu có thắc mắc học thuật.
              </span>
            </div>
            <p className="text-xs text-on-surface-variant">
              Bạn có chắc chắn muốn công bố điểm bài thi này cho sinh viên <strong>{detail.studentName}</strong>?
            </p>
            <div className="flex justify-end gap-2 pt-2">
              <button
                onClick={() => setShowPublishModal(false)}
                className="px-4 py-2 rounded-lg bg-surface-container hover:bg-surface-container-high text-xs font-medium"
              >
                Hủy
              </button>
              <button
                onClick={handleConfirmPublish}
                disabled={publishMutation.isPending}
                className="px-4 py-2 rounded-lg bg-emerald-600 hover:bg-emerald-700 text-white text-xs font-semibold shadow-xs disabled:opacity-50"
              >
                {publishMutation.isPending ? 'Đang công bố...' : 'Công bố Điểm Ngay'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
