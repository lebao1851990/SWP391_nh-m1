'use client';

import React, { useState, useMemo } from 'react';
import { useForm as useHookForm, useWatch } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { ScoringDetail } from '../types';
import { SaveScoringPayloadSchema, SaveScoringPayloadType } from '../schemas';
import { useUpdateTeacherScoring } from '../hooks';
import { 
  Save, 
  Sliders, 
  X, 
  Award, 
  User, 
  BookOpen, 
  CheckCircle2, 
  AlertCircle, 
  Loader2,
  FileCheck,
  Download,
  FileArchive,
  Code2,
  ChevronDown,
  ChevronRight,
  ShieldAlert,
  FileText,
  AlertTriangle,
  ExternalLink,
  MessageSquare
} from 'lucide-react';
import { toast } from 'sonner';
import { useQuery } from '@tanstack/react-query';
import { teacherExamsApi, SubmissionFileDto } from '@/features/exams/api/teacher-exams.api';
import { GradeAuditHistory } from './grade-audit-history';
import { SemanticCodeReviewView } from '@/features/submissions/components';

interface TeacherScoringFormProps {
  submissionId: string;
  initialData: ScoringDetail;
}

export function TeacherScoringForm({ submissionId, initialData }: TeacherScoringFormProps) {
  const [showOverride, setShowOverride] = useState(false);
  const [showSourceViewer, setShowSourceViewer] = useState(false);
  const [selectedFileIdx, setSelectedFileIdx] = useState(0);
  const [isSavingFeedback, setIsSavingFeedback] = useState(false);
  const [expandedTestIdx, setExpandedTestIdx] = useState<number | null>(null);

  const { data: submissionFiles, isLoading: isLoadingFiles } = useQuery<SubmissionFileDto[]>({
    queryKey: ['teacher-submission-files', submissionId],
    queryFn: () => teacherExamsApi.getSubmissionFiles(submissionId),
    enabled: showSourceViewer,
  });

  const updateMutation = useUpdateTeacherScoring(submissionId);
  const maxTotalScore = initialData.maxTotalScore ?? (initialData.criteria?.length ? initialData.criteria.reduce((s, c) => s + (c.maxScore || 0), 0) : 100);

  const {
    register,
    handleSubmit,
    control,
    setValue,
    formState: { isDirty, errors },
  } = useHookForm<SaveScoringPayloadType>({
    resolver: zodResolver(SaveScoringPayloadSchema),
    defaultValues: {
      criteriaScores: initialData.criteria.map(c => ({
        criterionId: c.criterionId,
        score: c.score,
      })),
      instructorEvaluationComments: initialData.instructorEvaluationComments || initialData.instructorFeedback || '',
      overrideTotalScore: initialData.overrideTotalScore != null ? initialData.overrideTotalScore : undefined,
    },
  });

  // Watch criteriaScores to calculate live running total
  const watchedCriteria = useWatch({
    control,
    name: 'criteriaScores',
  });

  const watchedOverride = useWatch({
    control,
    name: 'overrideTotalScore',
  });

  const hasActiveOverride = watchedOverride !== undefined && watchedOverride !== null && !isNaN(Number(watchedOverride));

  const liveCalculatedTotal = useMemo(() => {
    if (!watchedCriteria || !Array.isArray(watchedCriteria)) return initialData.totalScore;
    return watchedCriteria.reduce((sum, curr) => {
      const val = Number(curr?.score);
      return sum + (isNaN(val) ? 0 : val);
    }, 0);
  }, [watchedCriteria, initialData.totalScore]);

  const handleSaveFeedbackOnly = async () => {
    const currentComment = control._formValues?.instructorEvaluationComments || '';
    setIsSavingFeedback(true);
    try {
      await teacherExamsApi.updateInstructorFeedback(submissionId, currentComment);
      toast.success('Đã lưu nhận xét của giảng viên thành công!');
    } catch (err: unknown) {
      toast.error((err as Error).message || 'Không thể lưu nhận xét');
    } finally {
      setIsSavingFeedback(false);
    }
  };

  const onSubmit = (data: SaveScoringPayloadType) => {
    updateMutation.mutate(data, {
      onSuccess: () => {
        toast.success('Đã lưu bảng điểm và tiêu chí đánh giá thành công!');
      },
      onError: (err: unknown) => {
        toast.error((err as Error).message || 'Không thể lưu đánh giá');
      }
    });
  };

  const isSaving = updateMutation.isPending;

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="max-w-7xl mx-auto px-space-lg py-space-lg flex flex-col w-full space-y-space-lg">
      {/* Header Info & Actions */}
      <div className="flex flex-col gap-space-sm border-b border-outline-variant/30 pb-space-md">
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-space-md">
          <div>
            <div className="flex items-center gap-space-sm flex-wrap">
              <h1 className="font-display-lg text-display-lg text-on-surface tracking-tight">
                Bảng điểm Đánh giá — Bài nộp #{initialData.submissionId.slice(0, 8)}
              </h1>
              <span className="text-xs px-2.5 py-0.5 rounded-full font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200">
                {(initialData.executionStatus || initialData.status).toUpperCase()}
              </span>
            </div>
            <p className="font-body-md text-body-md text-on-surface-variant mt-1 text-xs">
              Chi tiết bài thi, test cases, mã nguồn Java và tiêu chí đánh giá cho{' '}
              <strong className="text-on-surface">{initialData.studentName}</strong> (MSSV:{' '}
              <span className="font-mono">{initialData.studentId}</span>).
            </p>
          </div>

          {/* Action Buttons: Download ZIP & Toggle Source Code */}
          <div className="flex items-center gap-2 flex-wrap">
            <a
              href={`/api/teacher/submissions/${submissionId}/download`}
              download
              className="px-3.5 py-2 rounded-lg text-xs font-semibold bg-primary text-on-primary hover:bg-primary/90 transition-all flex items-center gap-1.5 shadow-xs"
            >
              <Download className="w-4 h-4" />
              <span>Tải file ZIP ({initialData.originalFilename || 'submission.zip'})</span>
            </a>

            <button
              type="button"
              onClick={() => setShowSourceViewer(prev => !prev)}
              className={`px-3.5 py-2 rounded-lg text-xs font-semibold border transition-all flex items-center gap-1.5 cursor-pointer ${
                showSourceViewer
                  ? 'bg-primary/10 border-primary text-primary'
                  : 'bg-surface-container border-outline-variant/30 text-on-surface hover:bg-surface-container-high'
              }`}
            >
              <Code2 className="w-4 h-4" />
              <span>{showSourceViewer ? 'Ẩn mã nguồn' : 'Xem mã nguồn đã nộp'}</span>
            </button>
          </div>
        </div>
      </div>

      {/* Source Code Viewer Panel */}
      {showSourceViewer && (
        <div className="rounded-2xl border border-outline-variant/40 bg-surface-container-lowest overflow-hidden shadow-md animate-in fade-in duration-150">
          <div className="p-4 bg-slate-900 border-b border-slate-800 text-slate-100 flex items-center justify-between">
            <div className="flex items-center gap-2">
              <Code2 className="w-4 h-4 text-primary" />
              <h3 className="font-semibold text-xs text-white">Mã nguồn đã trích xuất từ file ZIP</h3>
            </div>
            <span className="text-[11px] font-mono text-slate-400">
              {submissionFiles ? `${submissionFiles.length} file Java` : 'Đang tải...'}
            </span>
          </div>

          {isLoadingFiles ? (
            <div className="p-8 flex items-center justify-center gap-2 text-xs text-outline">
              <Loader2 className="w-4 h-4 animate-spin text-primary" />
              <span>Đang đọc các file mã nguồn từ lưu trữ an toàn...</span>
            </div>
          ) : !submissionFiles || submissionFiles.length === 0 ? (
            <div className="p-8 text-center text-xs text-outline">
              Không tìm thấy file mã nguồn .java trong bài nộp này.
            </div>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-12 divide-y md:divide-y-0 md:divide-x divide-outline-variant/30">
              {/* File list */}
              <div className="md:col-span-4 p-3 bg-surface-container-low max-h-96 overflow-y-auto space-y-1">
                {submissionFiles.map((file, idx) => (
                  <button
                    key={file.relativePath || idx}
                    type="button"
                    onClick={() => setSelectedFileIdx(idx)}
                    className={`w-full text-left px-3 py-2 rounded-lg text-xs font-mono transition-colors flex items-center justify-between cursor-pointer ${
                      selectedFileIdx === idx
                        ? 'bg-primary text-on-primary font-semibold'
                        : 'hover:bg-surface-container text-on-surface'
                    }`}
                  >
                    <span className="truncate">{file.relativePath || file.filename}</span>
                    <span className="text-[10px] opacity-75 shrink-0 ml-2">{(file.sizeBytes / 1024).toFixed(1)} KB</span>
                  </button>
                ))}
              </div>

              {/* File Code Display */}
              <div className="md:col-span-8 bg-slate-950 p-4 max-h-96 overflow-y-auto font-mono text-xs text-slate-100">
                <pre className="whitespace-pre-wrap leading-relaxed">
                  {submissionFiles[selectedFileIdx]?.content || '// File rỗng'}
                </pre>
              </div>
            </div>
          )}

          {/* Compiler Output if exists */}
          {initialData.compileOutput && (
            <div className="p-4 bg-rose-950/40 border-t border-rose-900/60 text-xs text-rose-200 font-mono">
              <span className="font-semibold block mb-1 text-rose-300">Thông báo từ trình biên dịch Java (javac):</span>
              <pre className="whitespace-pre-wrap max-h-32 overflow-y-auto">{initialData.compileOutput}</pre>
            </div>
          )}
        </div>
      )}

      {/* Semantic Code Review for Instructor */}
      <SemanticCodeReviewView submissionId={submissionId} isTeacher={true} />

      {updateMutation.isError && (
        <div className="p-3.5 rounded-xl bg-rose-50 border border-rose-200 text-rose-700 text-xs flex items-center gap-2">
          <AlertCircle className="w-4 h-4 shrink-0" />
          <span>Lưu đánh giá thất bại: {(updateMutation.error as Error).message}</span>
        </div>
      )}

      {updateMutation.isSuccess && (
        <div className="p-3.5 rounded-xl bg-emerald-50 border border-emerald-200 text-emerald-800 text-xs flex items-center gap-2">
          <CheckCircle2 className="w-4 h-4 shrink-0 text-emerald-600" />
          <span>Đã lưu đánh giá thành công! Kết quả chính thức của sinh viên đã được cập nhật.</span>
        </div>
      )}

      <div className="grid grid-cols-1 xl:grid-cols-12 gap-space-lg items-start">
        {/* Left: Test Results & Criteria List & Instructor Comments */}
        <div className="xl:col-span-8 flex flex-col space-y-space-md">
          {/* Automated Test Case Execution Overview */}
          {initialData.testResults && initialData.testResults.length > 0 && (
            <div className="p-4 rounded-xl bg-surface-container-low border border-outline-variant/40 flex flex-col gap-3">
              <div className="flex items-center justify-between flex-wrap gap-2">
                <span className="font-semibold text-xs text-on-surface flex items-center gap-1.5">
                  <FileCheck className="w-4 h-4 text-primary" />
                  <span>Kết quả chạy Test Cases tự động:</span>
                  <span className="text-primary font-bold">
                    {initialData.testCasesPassed ?? 0} / {initialData.testCasesTotal ?? initialData.testResults.length} passed
                  </span>
                </span>
                <span className="text-xs px-2.5 py-0.5 rounded-full font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200">
                  Điểm tự động: {initialData.autoScore ?? 0} đ
                </span>
              </div>
              <div className="space-y-2 text-xs">
                {initialData.testResults.map((tr, idx) => {
                  const isExpanded = expandedTestIdx === idx;
                  const isPassed = tr.status === 'PASSED';
                  return (
                    <div key={tr.id || idx} className="rounded-lg bg-surface-container-lowest border border-outline-variant/30 overflow-hidden">
                      <div 
                        onClick={() => setExpandedTestIdx(isExpanded ? null : idx)}
                        className="p-3 flex items-center justify-between cursor-pointer hover:bg-surface-container-low/50 transition-colors"
                      >
                        <div className="flex items-center gap-2">
                          {isExpanded ? <ChevronDown className="w-3.5 h-3.5 text-outline" /> : <ChevronRight className="w-3.5 h-3.5 text-outline" />}
                          <span className="font-semibold text-on-surface">
                            #{idx + 1}: {tr.testCaseName || `Test ${idx + 1}`}
                          </span>
                          {tr.isHidden && (
                            <span className="text-[10px] px-1.5 py-0.5 rounded bg-surface-container-high text-outline">Ẩn với sinh viên</span>
                          )}
                        </div>
                        <div className="flex items-center gap-2">
                          {tr.executionTimeMs != null && (
                            <span className="text-[11px] text-outline">{tr.executionTimeMs} ms</span>
                          )}
                          <span className={`text-[11px] px-2 py-0.5 rounded-full font-semibold border ${
                            isPassed ? 'bg-emerald-50 text-emerald-700 border-emerald-200' : 'bg-red-50 text-red-700 border-red-200'
                          }`}>
                            {tr.status} ({tr.pointsAwarded ?? 0} đ)
                          </span>
                        </div>
                      </div>

                      {/* Expanded Test Details for Teacher */}
                      {isExpanded && (
                        <div className="p-3 bg-surface-container-low border-t border-outline-variant/20 grid grid-cols-1 md:grid-cols-2 gap-2 text-[11px] font-mono">
                          {tr.expectedOutput && (
                            <div className="p-2 rounded bg-surface-container-lowest border border-outline-variant/20">
                              <span className="text-outline font-semibold block mb-0.5">Expected Output:</span>
                              <pre className="whitespace-pre-wrap max-h-20 overflow-y-auto">{tr.expectedOutput}</pre>
                            </div>
                          )}
                          {tr.actualOutput && (
                            <div className="p-2 rounded bg-surface-container-lowest border border-outline-variant/20">
                              <span className="text-outline font-semibold block mb-0.5">Actual Output:</span>
                              <pre className="whitespace-pre-wrap max-h-20 overflow-y-auto">{tr.actualOutput}</pre>
                            </div>
                          )}
                          {tr.errorMessage && (
                            <div className="md:col-span-2 p-2 rounded bg-rose-50 text-rose-800 border border-rose-200">
                              <span className="font-semibold block mb-0.5">Stderr / Error:</span>
                              <pre className="whitespace-pre-wrap max-h-20 overflow-y-auto">{tr.errorMessage}</pre>
                            </div>
                          )}
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
            </div>
          )}

          <div className="flex items-center justify-between">
            <h2 className="font-headline-sm text-headline-sm text-on-surface">Tiêu chí Chấm điểm (Rubric)</h2>
            <span className="text-xs text-outline font-medium">Nhập điểm cho từng tiêu chí</span>
          </div>

          {initialData.criteria.map((criterion, index) => (
            <div key={criterion.criterionId} className="rounded-xl bg-surface-container-lowest border border-outline-variant/40 shadow-xs overflow-hidden">
              <div className="p-space-md flex flex-col sm:flex-row sm:items-center justify-between gap-3 bg-surface-container-lowest border-b border-outline-variant/20">
                <div className="flex flex-col">
                  <h3 className="font-label-md font-semibold text-on-surface text-sm">{criterion.name}</h3>
                  <p className="text-xs text-on-surface-variant mt-0.5">{criterion.description}</p>
                </div>
                <div className="flex items-center gap-2 shrink-0">
                  <input 
                    type="number"
                    step="0.1"
                    min={0}
                    max={criterion.maxScore}
                    {...register(`criteriaScores.${index}.score`, { valueAsNumber: true })}
                    className="w-20 px-2.5 py-1.5 rounded-lg border border-outline-variant/60 focus:border-primary focus:outline-none bg-surface-container-low text-on-surface font-mono text-xs font-bold text-center"
                    disabled={isSaving}
                  />
                  <span className="text-on-surface-variant text-xs font-medium">/ {criterion.maxScore} điểm</span>
                </div>
              </div>
              {errors.criteriaScores?.[index]?.score && (
                <div className="p-2 text-rose-600 text-xs font-medium bg-rose-50 border-t border-rose-100">
                  {errors.criteriaScores[index]?.score?.message}
                </div>
              )}
            </div>
          ))}

          {/* Instructor Evaluation Feedback & Independent Save */}
          <div className="rounded-xl bg-surface-container-lowest border border-outline-variant/40 p-space-md shadow-xs space-y-2">
            <div className="flex items-center justify-between">
              <h4 className="font-label-md font-semibold text-on-surface text-xs uppercase tracking-wider">
                Nhận xét và Phản hồi của Giảng viên
              </h4>
              <button
                type="button"
                onClick={handleSaveFeedbackOnly}
                disabled={isSavingFeedback || isSaving}
                className="px-3 py-1 rounded-md bg-surface-container hover:bg-surface-container-high text-primary text-[11px] font-semibold border border-outline-variant/30 flex items-center gap-1 cursor-pointer transition-colors"
              >
                {isSavingFeedback ? <Loader2 className="w-3 h-3 animate-spin" /> : <Save className="w-3 h-3" />}
                <span>Lưu riêng nhận xét</span>
              </button>
            </div>
            <textarea 
              {...register('instructorEvaluationComments')}
              className="w-full p-3 rounded-lg bg-surface-container-low font-body-sm text-xs text-on-surface border border-outline-variant/40 focus:outline-none focus:border-primary transition-colors" 
              placeholder="Nhập nhận xét mang tính xây dựng, giải thích trường hợp kiểm thử không đạt hoặc đánh giá chất lượng mã nguồn (không bắt buộc)..." 
              rows={4}
              disabled={isSaving}
            />
          </div>
        </div>

        {/* Right Sidebar: Student Info & Official Score Display & Plagiarism */}
        <div className="xl:col-span-4 flex flex-col space-y-space-md">
          {/* Student & Submission Info Card */}
          <div className="rounded-xl bg-surface-container-lowest border border-outline-variant/40 p-space-md shadow-xs space-y-2 text-xs">
            <div className="flex items-center gap-1.5 pb-2 border-b border-outline-variant/20">
              <User className="w-4 h-4 text-primary" />
              <h4 className="font-semibold text-on-surface">Thông tin Bài nộp</h4>
            </div>
            <div className="space-y-1.5 text-on-surface-variant">
              <div className="flex justify-between">
                <span className="text-outline">Họ và tên:</span>
                <span className="font-semibold text-on-surface">{initialData.studentName}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-outline">Mã sinh viên:</span>
                <span className="font-mono text-on-surface">{initialData.studentId}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-outline">Lớp học:</span>
                <span className="font-mono text-on-surface">{initialData.className}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-outline">Tệp ZIP:</span>
                <span className="font-mono text-primary truncate max-w-[150px]">{initialData.originalFilename || 'submission.zip'}</span>
              </div>
              {initialData.archiveHash && (
                <div className="flex justify-between">
                  <span className="text-outline">SHA-256:</span>
                  <span className="font-mono text-[10px] text-outline truncate max-w-[130px]" title={initialData.archiveHash}>
                    {initialData.archiveHash.slice(0, 16)}...
                  </span>
                </div>
              )}
            </div>
          </div>

          {/* AST Plagiarism Card */}
          <div className="rounded-xl bg-surface-container-lowest border border-outline-variant/40 p-space-md shadow-xs space-y-2 text-xs">
            <div className="flex items-center gap-1.5 pb-2 border-b border-outline-variant/20">
              <ShieldAlert className="w-4 h-4 text-primary" />
              <h4 className="font-semibold text-on-surface">Kiểm tra Đạo văn AST</h4>
            </div>
            {initialData.astSimilarityPercentage !== undefined && initialData.astSimilarityPercentage !== null ? (
              <div className="space-y-2">
                <div className="flex items-center justify-between">
                  <span className="text-outline">Tương đồng cao nhất:</span>
                  <span className="font-bold text-sm font-mono text-on-surface">
                    {Number(initialData.astSimilarityPercentage).toFixed(0)}%
                  </span>
                </div>
                <div className={`p-2 rounded-lg border text-[11px] ${
                  initialData.astSimilarityPercentage >= 70
                    ? 'bg-rose-50 border-rose-200 text-rose-800'
                    : 'bg-emerald-50 border-emerald-200 text-emerald-800'
                }`}>
                  {initialData.astSimilarityPercentage >= 70
                    ? 'Phát hiện cấu trúc AST tương đồng đáng ngờ (≥ 70%). Cần giảng viên đối chiếu mã nguồn.'
                    : 'Cấu trúc AST an toàn (< 70%). Không phát hiện tương đồng bất thường.'}
                </div>
              </div>
            ) : (
              <p className="text-[11px] text-outline leading-relaxed">
                Chưa có dữ liệu so sánh đạo văn hoặc bài nộp đầu tiên trong kỳ thi.
              </p>
            )}
          </div>
          
          {/* Dynamic Score Card */}
          <div className="rounded-xl bg-surface-container-lowest border border-outline-variant/40 p-space-md shadow-xs space-y-2">
            <div className="flex items-center gap-1.5 pb-2 border-b border-outline-variant/20">
              <Award className="w-4 h-4 text-emerald-600" />
              <h4 className="font-semibold text-on-surface text-xs">
                {hasActiveOverride ? 'Điểm Hiệu lực (Điều chỉnh thủ công)' : 'Điểm Tính toán'}
              </h4>
            </div>
            <div className="font-display-lg text-[52px] leading-none font-bold text-primary tracking-tight">
              {hasActiveOverride 
                ? Number(watchedOverride).toFixed(1).replace(/\.0$/, '') 
                : Number(liveCalculatedTotal).toFixed(1).replace(/\.0$/, '')}
              <span className="text-lg text-outline font-normal ml-1">/ {maxTotalScore}</span>
            </div>
            {hasActiveOverride ? (
              <div className="text-[11px] text-amber-800 bg-amber-50 border border-amber-200 p-2 rounded-lg leading-relaxed">
                * Điểm điều chỉnh thủ công <strong>{watchedOverride} điểm</strong> đang kích hoạt và sẽ ghi đè lên tổng điểm tiêu chí (<strong>{Number(liveCalculatedTotal).toFixed(1).replace(/\.0$/, '')} điểm</strong>).
              </div>
            ) : (
              <p className="text-[11px] text-on-surface-variant leading-relaxed">
                * Tổng điểm tính theo thời gian thực dựa trên các tiêu chí phía trên. Nhấn lưu để cập nhật vào hệ thống.
              </p>
            )}
          </div>
        </div>
      </div>

      {/* Sticky Bottom Actions Bar */}
      <div className="sticky bottom-4 z-30 p-3.5 rounded-xl bg-surface-container-lowest/95 backdrop-blur-md border border-outline-variant/50 shadow-xl flex items-center justify-end gap-space-md">
        <button 
          type="button" 
          onClick={() => setShowOverride(true)}
          className={`px-4 py-2 rounded-lg text-xs font-medium flex items-center gap-1.5 transition-colors cursor-pointer border ${
            hasActiveOverride 
              ? 'bg-amber-50 hover:bg-amber-100 text-amber-800 border-amber-300' 
              : 'bg-surface-container hover:bg-surface-container-high text-on-surface border-outline-variant/30'
          }`}
          disabled={isSaving}
        >
          <Sliders className="w-3.5 h-3.5 text-secondary" />
          <span>Điều chỉnh điểm thủ công {hasActiveOverride ? `(${watchedOverride} đ)` : ''}</span>
        </button>

        <button 
          type="submit" 
          disabled={isSaving}
          className="px-5 py-2 rounded-lg text-xs font-semibold flex items-center gap-1.5 transition-all shadow-sm bg-primary text-on-primary hover:bg-primary/90 disabled:opacity-50 cursor-pointer"
        >
          {isSaving ? (
            <>
              <Loader2 className="w-3.5 h-3.5 animate-spin" />
              <span>Đang lưu...</span>
            </>
          ) : (
            <>
              <Save className="w-3.5 h-3.5" />
              <span>Lưu bảng điểm</span>
            </>
          )}
        </button>
      </div>

      {/* Grade Audit History */}
      <GradeAuditHistory submissionId={submissionId} className="mt-space-md" />

      {/* Manual Override Modal */}
      {showOverride && (
        <div className="fixed inset-0 bg-black/50 backdrop-blur-xs z-50 flex items-center justify-center p-4 animate-in fade-in duration-150">
          <div className="bg-surface-container-lowest rounded-2xl max-w-md w-full p-6 shadow-2xl border border-outline-variant/40 space-y-4 animate-in zoom-in-95 duration-150">
            <div className="flex items-center justify-between border-b border-outline-variant/30 pb-3">
              <div className="flex items-center gap-2">
                <Sliders className="w-5 h-5 text-secondary" />
                <h3 className="font-headline-sm text-headline-sm text-on-surface font-semibold">Điều chỉnh Điểm Thủ công</h3>
              </div>
              <button 
                type="button" 
                onClick={() => setShowOverride(false)} 
                className="text-outline hover:text-on-surface p-1 rounded-lg cursor-pointer"
              >
                <X className="w-4 h-4" />
              </button>
            </div>
            <p className="text-xs text-on-surface-variant leading-relaxed">
              Trực tiếp chỉ định điểm số tổng kết. Điểm này sẽ thay thế tổng điểm rubric nếu được áp dụng.
            </p>
            <div className="space-y-1">
              <label className="text-xs font-semibold text-on-surface">Điểm tổng kết mới</label>
              <input 
                type="number" 
                step="0.1" 
                min={0}
                max={maxTotalScore}
                {...register('overrideTotalScore', { 
                  setValueAs: (v) => v === '' || v === null || isNaN(Number(v)) ? undefined : Number(v) 
                })} 
                className="w-full px-3 py-2 rounded-lg bg-surface-container-low text-xs text-on-surface font-bold border border-outline-variant/40 focus:outline-none focus:border-primary" 
              />
              {errors.overrideTotalScore && (
                <p className="text-xs text-rose-600 mt-1">{errors.overrideTotalScore.message}</p>
              )}
            </div>
            <div className="space-y-1">
              <label className="text-xs font-semibold text-on-surface">Lý do điều chỉnh</label>
              <textarea 
                {...register('overrideRationale')} 
                className="w-full p-3 rounded-lg bg-surface-container-low text-xs text-on-surface border border-outline-variant/40 focus:outline-none focus:border-primary" 
                rows={3} 
                placeholder="Nêu rõ lý do điều chỉnh điểm (ví dụ: xử lý ngoại lệ kiểm thử, phúc khảo...)..."
              />
            </div>
            <div className="flex justify-between items-center gap-2 pt-2 border-t border-outline-variant/30">
              {hasActiveOverride ? (
                <button
                  type="button"
                  onClick={() => {
                    setValue('overrideTotalScore', undefined, { shouldDirty: true });
                    setShowOverride(false);
                    toast.info('Đã hủy điều chỉnh. Bảng điểm sẽ dùng tổng điểm tiêu chí.');
                  }}
                  className="px-3 py-1.5 rounded-lg text-xs font-medium text-rose-600 hover:bg-rose-50 border border-rose-200 cursor-pointer"
                >
                  Xóa điều chỉnh
                </button>
              ) : <div />}
              <button 
                type="button" 
                onClick={() => setShowOverride(false)} 
                className="px-4 py-2 rounded-lg bg-primary text-on-primary text-xs font-medium cursor-pointer"
              >
                Áp dụng vào form
              </button>
            </div>
          </div>
        </div>
      )}
    </form>
  );
}
