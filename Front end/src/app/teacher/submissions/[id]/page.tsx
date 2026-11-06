'use client';

import React, { use, useState } from 'react';
import { 
  useTeacherSubmissionDetail, 
  useTeacherSubmissionFiles, 
  useUpdateInstructorFeedback 
} from '@/features/exams/hooks/use-teacher-exams';
import { teacherExamsApi } from '@/features/exams/api/teacher-exams.api';
import { AppLink as Link } from '@/components/ui/app-link';
import { GradeAuditHistory } from '@/features/scoring/components';
import { SemanticCodeReviewView } from '@/features/submissions/components';
import { 
  Download, 
  FileCode, 
  ShieldCheck, 
  GitCompare, 
  CheckCircle2, 
  Clock, 
  Save, 
  ExternalLink 
} from 'lucide-react';

export default function TeacherSubmissionDetail({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  const { data: submission, isLoading, isError, error } = useTeacherSubmissionDetail(id);
  const { data: files } = useTeacherSubmissionFiles(id);
  const updateFeedbackMutation = useUpdateInstructorFeedback();

  const [selectedFileIdx, setSelectedFileIdx] = useState<number>(0);
  const [isDownloading, setIsDownloading] = useState<boolean>(false);
  const [feedbackText, setFeedbackText] = useState<string>('');
  const [isFeedbackInitialized, setIsFeedbackInitialized] = useState<boolean>(false);

  // Initialize feedback when submission loads
  if (submission && !isFeedbackInitialized) {
    setFeedbackText(submission.instructorFeedback || '');
    setIsFeedbackInitialized(true);
  }

  const handleDownloadZip = async () => {
    if (!submission) return;
    try {
      setIsDownloading(true);
      const { blob, filename } = await teacherExamsApi.downloadSubmissionZip(submission.id);
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = filename || `submission-${submission.id}.zip`;
      document.body.appendChild(a);
      a.click();
      window.URL.revokeObjectURL(url);
      document.body.removeChild(a);
    } catch (err) {
      console.error('Download failed:', err);
      alert('Không thể tải file ZIP bài nộp. Vui lòng thử lại.');
    } finally {
      setIsDownloading(false);
    }
  };

  const handleSaveFeedback = async () => {
    if (!submission) return;
    try {
      await updateFeedbackMutation.mutateAsync({ id: submission.id, feedback: feedbackText });
      alert('Đã cập nhật nhận xét của giảng viên thành công.');
    } catch (err) {
      console.error('Save feedback failed:', err);
      alert('Lỗi lưu nhận xét. Vui lòng thử lại.');
    }
  };

  if (isLoading) {
    return (
      <div className="flex flex-col h-64 items-center justify-center text-on-surface-variant gap-4">
        <span className="material-symbols-outlined animate-spin text-[48px]">progress_activity</span>
        <span className="font-label-md text-label-md">Đang tải thông tin bài nộp...</span>
      </div>
    );
  }

  if (isError || !submission) {
    return (
      <div className="flex flex-col h-64 items-center justify-center p-space-lg text-center gap-space-sm">
        <span className="material-symbols-outlined text-error text-[48px]">error</span>
        <h2 className="font-headline-md text-headline-md text-on-surface">Không tìm thấy bài nộp</h2>
        <p className="font-body-md text-on-surface-variant">
          {error instanceof Error ? error.message : 'Bài nộp không tồn tại hoặc bạn không có quyền truy cập.'}
        </p>
      </div>
    );
  }

  const fileList = files || [];
  const activeFile = fileList[selectedFileIdx];

  return (
    <div className="max-w-7xl mx-auto px-space-lg py-space-lg">
      <div className="flex flex-col w-full">
        {/* Breadcrumb */}
        <nav aria-label="Breadcrumb" className="flex items-center gap-space-xs text-on-surface-variant font-body-sm text-body-sm mb-space-md">
          <Link className="hover:text-primary transition-colors" href="/teacher/exams">Đề thi</Link>
          <span className="material-symbols-outlined text-[14px] text-outline">chevron_right</span>
          <Link className="hover:text-primary transition-colors" href={`/teacher/exams/${submission.examId}`}>Chi tiết đề thi</Link>
          <span className="material-symbols-outlined text-[14px] text-outline">chevron_right</span>
          <span className="font-medium text-on-surface">Bài nộp #{submission.id.slice(0, 8)}</span>
        </nav>

        {/* Header Banner */}
        <section className="bg-surface-container-lowest p-space-lg rounded-xl shadow-xs border border-outline-variant/30 mb-space-lg">
          <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-space-md">
            <div className="flex flex-col gap-space-xs">
              <div className="flex flex-wrap items-center gap-space-sm">
                <h1 className="font-headline-lg text-headline-lg text-on-surface tracking-tight">
                  Bài nộp #{submission.id.slice(0, 8)}
                </h1>
                <span className={`font-label-sm text-label-sm px-2.5 py-0.5 rounded-full font-semibold uppercase tracking-wide flex items-center gap-1 ${
                  submission.status === 'GRADED' 
                    ? 'bg-emerald-50 text-emerald-700 border border-emerald-200' 
                    : 'bg-surface-container-high text-on-surface-variant'
                }`}>
                  {submission.status === 'GRADED' && <CheckCircle2 className="w-3.5 h-3.5 text-emerald-600" />}
                  {submission.status || 'PENDING'}
                </span>
                {submission.submissionType && (
                  <span className="font-mono text-xs px-2 py-0.5 rounded bg-primary/10 text-primary border border-primary/20 font-semibold">
                    {submission.submissionType}
                  </span>
                )}
                {submission.questionTitle && (
                  <span className="text-xs px-2.5 py-0.5 rounded-full bg-surface-container text-on-surface font-medium">
                    Câu hỏi: {submission.questionTitle}
                  </span>
                )}
              </div>
              <div className="flex flex-wrap items-center gap-y-1 gap-x-space-md font-body-sm text-body-sm text-on-surface-variant mt-1">
                <div className="flex items-center gap-1.5 font-medium text-on-surface">
                  <span className="material-symbols-outlined text-[16px] text-primary">person</span>
                  <span>Sinh viên ID: <span className="font-label-sm text-label-sm font-mono text-outline">{submission.studentId}</span></span>
                </div>
                <span className="h-1 w-1 rounded-full bg-outline-variant"></span>
                <div className="flex items-center gap-1.5">
                  <span className="material-symbols-outlined text-[16px] text-outline">assignment</span>
                  <span>Mã đề thi: <span className="font-mono text-xs">{submission.examId}</span></span>
                </div>
                <span className="h-1 w-1 rounded-full bg-outline-variant"></span>
                <div className="flex items-center gap-1.5 text-outline">
                  <Clock className="w-3.5 h-3.5" />
                  <span>Thời gian nộp: {new Date(submission.createdAt).toLocaleString()}</span>
                </div>
              </div>
            </div>
            
            <div className="flex flex-wrap items-center gap-space-sm shrink-0">
              <button
                onClick={handleDownloadZip}
                disabled={isDownloading}
                className="px-3.5 py-2 bg-surface-container hover:bg-surface-container-high text-on-surface border border-outline-variant/40 rounded-lg text-xs font-semibold flex items-center gap-1.5 transition-colors disabled:opacity-50 cursor-pointer"
              >
                <Download className="w-3.5 h-3.5 text-primary" />
                <span>{isDownloading ? 'Đang tải...' : 'Tải mã nguồn ZIP'}</span>
              </button>

              <Link 
                href={`/teacher/scoring/${submission.id}`} 
                className="bg-primary hover:bg-primary/90 text-on-primary font-label-md text-label-md px-3.5 py-2 rounded-lg flex items-center gap-1.5 transition-colors cursor-pointer"
              >
                <span className="material-symbols-outlined text-[16px]">edit_document</span>
                <span>Chấm điểm</span>
              </Link>
            </div>
          </div>
        </section>

        {/* Content Grid */}
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-space-lg">
          {/* Left Column: Source Code Explorer & Audit History */}
          <div className="lg:col-span-8 flex flex-col gap-space-lg">
            {/* File Explorer / Code Viewer */}
            <div className="bg-surface-container-lowest rounded-xl p-space-lg shadow-xs border border-outline-variant/30 flex flex-col gap-space-md">
              <div className="flex items-center justify-between pb-space-xs border-b border-outline-variant/30">
                <div className="flex items-center gap-space-sm">
                  <span className="p-1.5 rounded-lg bg-surface-container text-primary">
                    <FileCode className="w-5 h-5 text-primary" />
                  </span>
                  <div>
                    <h2 className="font-headline-sm text-headline-sm text-on-surface leading-tight">
                      Mã nguồn bài nộp {fileList.length > 0 ? `(${fileList.length} tệp)` : ''}
                    </h2>
                    <span className="font-label-sm text-label-sm text-outline">
                      {submission.originalFilename || 'Tệp mã nguồn từ sinh viên'}
                    </span>
                  </div>
                </div>
              </div>
              
              {fileList.length > 0 ? (
                <div className="grid grid-cols-1 md:grid-cols-12 rounded-lg border border-outline-variant/30 overflow-hidden bg-surface-container-lowest">
                  {/* File Tree List */}
                  <div className="md:col-span-4 border-r border-outline-variant/30 bg-surface-container-low/40 p-2 overflow-y-auto max-h-[500px]">
                    <div className="text-xs font-semibold text-outline uppercase tracking-wider px-2 py-1 mb-1">
                      Danh sách tệp
                    </div>
                    <div className="space-y-1">
                      {fileList.map((f, idx) => (
                        <button
                          key={f.filename || idx}
                          onClick={() => setSelectedFileIdx(idx)}
                          className={`w-full text-left px-2.5 py-1.5 rounded text-xs font-mono flex items-center gap-2 transition-colors truncate ${
                            selectedFileIdx === idx 
                              ? 'bg-primary/10 text-primary font-semibold border border-primary/20' 
                              : 'text-on-surface-variant hover:bg-surface-container'
                          }`}
                        >
                          <FileCode className="w-3.5 h-3.5 shrink-0 opacity-70" />
                          <span className="truncate">{f.filename}</span>
                        </button>
                      ))}
                    </div>
                  </div>

                  {/* Code View of Selected File */}
                  <div className="md:col-span-8 flex flex-col bg-surface-container-lowest overflow-hidden">
                    <div className="flex items-center justify-between px-space-md py-2 bg-surface-container-low border-b border-outline-variant/30 font-label-sm text-label-sm text-on-surface-variant font-mono">
                      <span className="font-semibold text-on-surface">{activeFile?.filename || 'main.java'}</span>
                      <span className="text-xs text-outline">{activeFile?.sizeBytes ? `${activeFile.sizeBytes} bytes` : ''}</span>
                    </div>
                    <pre className="p-space-md font-code-body text-code-body text-on-surface overflow-x-auto whitespace-pre-wrap max-h-[500px] bg-slate-950 text-slate-100 font-mono text-xs">
                      {activeFile?.content || 'Tệp trống hoặc không thể đọc nội dung.'}
                    </pre>
                  </div>
                </div>
              ) : (
                <div className="rounded-lg border border-outline-variant/30 overflow-hidden">
                  <div className="flex items-center px-space-md py-2 bg-surface-container-low border-b border-outline-variant/30 font-label-sm text-label-sm text-on-surface-variant font-mono">
                    {submission.originalFilename || 'main.java'}
                  </div>
                  <pre className="p-space-md font-code-body text-code-body text-on-surface overflow-x-auto whitespace-pre-wrap max-h-[500px] bg-slate-950 text-slate-100 font-mono text-xs">
                    {submission.sourceCode || 'Không có nội dung mã nguồn.'}
                  </pre>
                </div>
              )}
            </div>

            {/* Semantic AI Code Review Component */}
            <SemanticCodeReviewView submissionId={submission.id} isTeacher={true} />

            {/* Grade Audit History Component */}
            <GradeAuditHistory submissionId={submission.id} />
          </div>

          {/* Right Column: Score, Execution, Forensics & Feedback */}
          <div className="lg:col-span-4 flex flex-col gap-space-lg">
            {/* Score Summary Card */}
            <div className="bg-surface-container-lowest rounded-xl p-space-lg shadow-xs border border-outline-variant/30 flex flex-col gap-space-md">
              <div className="flex items-center justify-between pb-space-xs">
                <div className="flex flex-col">
                  <span className="font-label-sm text-label-sm text-outline uppercase tracking-wider font-semibold">Điểm số Tổng hợp</span>
                  <span className="font-body-sm text-body-sm text-on-surface-variant">Thang điểm 10</span>
                </div>
              </div>
              
              <div className="flex items-center justify-between p-space-md bg-surface-container-low rounded-xl">
                <div className="flex items-baseline gap-space-xs">
                  <span className="font-display-lg text-display-lg text-primary font-bold tracking-tight">
                    {submission.totalScore != null ? submission.totalScore : 'N/A'}
                  </span>
                  <span className="font-headline-lg text-headline-lg text-outline">/ 10</span>
                </div>
                {submission.testCasesTotal != null && (
                  <div className="text-right">
                    <div className="text-xs font-semibold text-on-surface">Test cases</div>
                    <div className="text-sm font-mono text-emerald-600 font-bold">
                      {submission.testCasesPassed ?? 0} / {submission.testCasesTotal}
                    </div>
                  </div>
                )}
              </div>
              
              {submission.overrideScore != null && (
                <div className="flex items-center justify-between p-space-sm bg-purple-50 border border-purple-200 rounded-lg">
                  <span className="font-label-sm text-label-sm font-semibold text-purple-900">Điểm ghi đè thủ công:</span>
                  <span className="font-label-md text-label-md font-bold text-purple-700">{submission.overrideScore} / 10</span>
                </div>
              )}
              
              {submission.evaluatedAt && (
                <div className="flex items-center gap-2 mt-1 font-label-sm text-label-sm text-outline">
                  <Clock className="w-3.5 h-3.5" />
                  <span>Đã chấm lúc: {new Date(submission.evaluatedAt).toLocaleString()}</span>
                </div>
              )}
            </div>

            {/* Real Integrity & Forensics Card */}
            <div className="bg-surface-container-lowest rounded-xl p-space-lg shadow-xs border border-outline-variant/30 flex flex-col gap-space-md">
              <div className="flex items-center justify-between pb-space-xs border-b border-outline-variant/30">
                <div className="flex items-center gap-space-sm">
                  <span className="p-1.5 rounded-lg bg-surface-container text-primary">
                    <ShieldCheck className="w-5 h-5 text-primary" />
                  </span>
                  <div>
                    <h2 className="font-headline-sm text-headline-sm text-on-surface leading-tight">Tính toàn vẹn & Pháp y AST</h2>
                    <span className="text-xs text-outline">Xác thực mã nguồn & đối chiếu chống gian lận</span>
                  </div>
                </div>
              </div>

              <div className="flex flex-col gap-3 font-body-sm text-xs">
                {submission.archiveHash && (
                  <div className="flex flex-col gap-1 p-2 bg-surface-container-low rounded">
                    <span className="text-outline font-semibold">Mã băm lưu trữ (Archive SHA-256):</span>
                    <span className="font-mono text-[11px] text-on-surface break-all">{submission.archiveHash}</span>
                  </div>
                )}

                {submission.sourceTreeHash && (
                  <div className="flex flex-col gap-1 p-2 bg-surface-container-low rounded">
                    <span className="text-outline font-semibold">Mã băm cây nguồn (AST Tree Hash):</span>
                    <span className="font-mono text-[11px] text-primary font-semibold break-all">{submission.sourceTreeHash}</span>
                  </div>
                )}

                <div className="grid grid-cols-2 gap-2">
                  <div className="p-2 bg-surface-container-low rounded">
                    <span className="text-outline block text-[11px]">Trạng thái thực thi:</span>
                    <span className="font-semibold text-on-surface">{submission.executionStatus || 'SUCCESS'}</span>
                  </div>
                  <div className="p-2 bg-surface-container-low rounded">
                    <span className="text-outline block text-[11px]">Thời gian chạy:</span>
                    <span className="font-semibold text-on-surface">
                      {submission.executionDurationMs != null ? `${submission.executionDurationMs}ms` : '—'}
                    </span>
                  </div>
                </div>

                {submission.entryPointClass && (
                  <div className="p-2 bg-surface-container-low rounded">
                    <span className="text-outline block text-[11px]">Lớp điểm vào (Entry Point):</span>
                    <span className="font-mono font-semibold text-on-surface">{submission.entryPointClass}</span>
                  </div>
                )}

                <div className="pt-2 border-t border-outline-variant/30">
                  <Link
                    href={`/teacher/plagiarism`}
                    className="w-full py-2 px-3 bg-primary/10 hover:bg-primary/20 text-primary border border-primary/20 rounded-lg font-semibold flex items-center justify-center gap-1.5 transition-colors"
                  >
                    <GitCompare className="w-3.5 h-3.5" />
                    <span>Mở Bảng phân tích Đạo văn AST</span>
                    <ExternalLink className="w-3 h-3 ml-auto opacity-70" />
                  </Link>
                </div>
              </div>
            </div>

            {/* Instructor Feedback Card */}
            <div className="bg-surface-container-lowest rounded-xl p-space-lg shadow-xs border border-outline-variant/30 flex flex-col gap-space-md">
              <div className="flex items-center justify-between pb-space-xs border-b border-outline-variant/30">
                <div className="flex items-center gap-space-sm">
                  <span className="p-1.5 rounded-lg bg-surface-container text-primary">
                    <span className="material-symbols-outlined text-[20px]">rate_review</span>
                  </span>
                  <div>
                    <h2 className="font-headline-sm text-headline-sm text-on-surface leading-tight">Nhận xét của Giảng viên</h2>
                    <span className="text-xs text-outline">Được hiển thị trực tiếp cho sinh viên</span>
                  </div>
                </div>
              </div>

              <div className="flex flex-col gap-2">
                <textarea
                  value={feedbackText}
                  onChange={(e) => setFeedbackText(e.target.value)}
                  placeholder="Nhập phản hồi, góp ý cho bài nộp của sinh viên..."
                  rows={4}
                  className="w-full text-xs p-2.5 rounded-lg border border-outline-variant/40 bg-surface-container-low text-on-surface focus:outline-hidden focus:border-primary"
                />
                <button
                  onClick={handleSaveFeedback}
                  disabled={updateFeedbackMutation.isPending}
                  className="self-end px-3 py-1.5 bg-primary hover:bg-primary/90 text-on-primary rounded text-xs font-semibold flex items-center gap-1.5 transition-colors cursor-pointer disabled:opacity-50"
                >
                  <Save className="w-3 h-3" />
                  <span>{updateFeedbackMutation.isPending ? 'Đang lưu...' : 'Lưu nhận xét'}</span>
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
