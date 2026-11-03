'use client';

import React, { useState } from 'react';
import { 
  useStudentExamScores, 
  useSetQuestionOverride, 
  useClearQuestionOverride 
} from '../hooks/use-teacher-exams';
import { QuestionScoreDetailDto } from '../api/teacher-exams.api';
import { 
  X,
  BookOpen,
  AlertCircle, 
  Loader2, 
  Edit3, 
  RotateCcw, 
  Save, 
  Scale 
} from 'lucide-react';
import { toast } from 'sonner';

interface StudentExamScoreAggregationModalProps {
  isOpen: boolean;
  onClose: () => void;
  examId: string;
  studentId: string;
  studentName?: string;
}

export function StudentExamScoreAggregationModal({
  isOpen,
  onClose,
  examId,
  studentId,
  studentName,
}: StudentExamScoreAggregationModalProps) {
  const { data, isLoading, isError, error } = useStudentExamScores(examId, studentId);
  const setOverrideMutation = useSetQuestionOverride();
  const clearOverrideMutation = useClearQuestionOverride();

  // State for editing a specific question override
  const [editingQuestionId, setEditingQuestionId] = useState<string | null>(null);
  const [overrideScoreInput, setOverrideScoreInput] = useState<string>('');
  const [overrideReasonInput, setOverrideReasonInput] = useState<string>('');

  if (!isOpen) return null;

  const handleStartEdit = (q: QuestionScoreDetailDto) => {
    setEditingQuestionId(q.questionId);
    setOverrideScoreInput(q.overrideScore != null ? String(q.overrideScore) : String(q.autoScore ?? 0));
    setOverrideReasonInput(q.overrideReason || '');
  };

  const handleCancelEdit = () => {
    setEditingQuestionId(null);
    setOverrideScoreInput('');
    setOverrideReasonInput('');
  };

  const handleSaveOverride = async (questionId: string) => {
    const scoreNum = parseFloat(overrideScoreInput);
    if (isNaN(scoreNum) || scoreNum < 0) {
      toast.error('Điểm số điều chỉnh phải là số hợp lệ và lớn hơn hoặc bằng 0.');
      return;
    }
    if (scoreNum > 10.0) {
      toast.error('Điểm số điều chỉnh không được vượt quá 10.0 điểm.');
      return;
    }

    try {
      await setOverrideMutation.mutateAsync({
        examId,
        questionId,
        studentId,
        payload: {
          overrideScore: scoreNum,
          rationale: overrideReasonInput.trim() || undefined,
        },
      });
      toast.success('Đã ghi nhận điểm điều chỉnh cho câu hỏi thành công!');
      handleCancelEdit();
    } catch (err: unknown) {
      const msg = (err as Error)?.message || 'Không thể lưu điểm điều chỉnh';
      toast.error(msg);
    }
  };

  const handleClearOverride = async (questionId: string) => {
    try {
      await clearOverrideMutation.mutateAsync({
        examId,
        questionId,
        studentId,
      });
      toast.success('Đã khôi phục điểm chấm tự động ban đầu!');
    } catch (err: unknown) {
      const msg = (err as Error)?.message || 'Không thể khôi phục điểm ban đầu';
      toast.error(msg);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-xs p-4 animate-in fade-in duration-150">
      <div id="student-score-aggregation-modal" className="bg-white dark:bg-slate-900 text-on-surface rounded-2xl max-w-3xl w-full p-6 shadow-2xl border border-outline-variant/40 flex flex-col gap-4 max-h-[90vh] overflow-y-auto">
        {/* Header */}
        <div className="flex items-center justify-between pb-3 border-b border-outline-variant/30">
          <div className="flex items-center gap-3">
            <div className="p-2.5 rounded-full bg-primary/10 text-primary">
              <Scale className="w-6 h-6" />
            </div>
            <div>
              <h3 className="font-headline-sm text-headline-sm font-semibold text-on-surface">
                Bảng điểm tổng hợp bài thi
              </h3>
              <p className="text-xs text-on-surface-variant flex items-center gap-2 mt-0.5">
                <span className="font-medium text-on-surface">{data?.studentName || studentName || studentId}</span>
                <span className="text-outline">({studentId})</span>
                {data?.examTitle && (
                  <>
                    <span className="text-outline">•</span>
                    <span className="truncate max-w-[240px]">{data.examTitle}</span>
                  </>
                )}
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 rounded-lg hover:bg-surface-container text-outline hover:text-on-surface transition-colors cursor-pointer"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Content */}
        {isLoading ? (
          <div className="flex flex-col items-center justify-center py-12 gap-3">
            <Loader2 className="w-8 h-8 animate-spin text-primary" />
            <span className="text-xs text-on-surface-variant font-medium">Đang tính toán tổng hợp điểm số các câu hỏi...</span>
          </div>
        ) : isError ? (
          <div className="p-4 rounded-xl bg-rose-50 border border-rose-200 text-rose-700 text-xs flex items-center gap-2">
            <AlertCircle className="w-4 h-4 shrink-0" />
            <span>{(error as Error)?.message || 'Không thể tải bảng điểm tổng hợp.'}</span>
          </div>
        ) : data ? (
          <div className="flex flex-col gap-5">
            {/* Consolidated Score Metric Banner */}
            <div className="p-4 rounded-xl bg-surface-container-low border border-outline-variant/30 flex items-center justify-between gap-4 flex-wrap">
              <div className="flex flex-col gap-1">
                <span className="text-[11px] font-semibold text-outline uppercase tracking-wider">
                  Tổng điểm quy đổi (Thang 10)
                </span>
                <div className="flex items-baseline gap-2">
                  <span className="text-3xl font-display-lg font-bold text-primary font-mono">
                    {data.examScore10 != null ? Number(data.examScore10).toFixed(2) : '--'}
                  </span>
                  <span className="text-xs text-on-surface-variant font-medium">/ 10.00 đ</span>
                </div>
              </div>

              <div className="flex items-center gap-4 text-xs font-mono">
                <div className="flex flex-col">
                  <span className="text-[11px] text-outline font-sans">Tổng điểm thô:</span>
                  <span className="font-semibold text-on-surface">
                    {data.totalPointsEarned != null ? Number(data.totalPointsEarned).toFixed(2) : '--'} / {data.totalMaxPoints != null ? Number(data.totalMaxPoints).toFixed(2) : '--'} đ
                  </span>
                </div>
                <div className="h-8 w-[1px] bg-outline-variant/40" />
                <div className="flex flex-col">
                  <span className="text-[11px] text-outline font-sans">Tiến độ câu hỏi:</span>
                  <span className="font-semibold text-on-surface">
                    {data.submittedQuestions} / {data.totalQuestions} đã nộp
                  </span>
                </div>
                <div className="h-8 w-[1px] bg-outline-variant/40" />
                <div className="flex flex-col">
                  <span className="text-[11px] text-outline font-sans">Trạng thái:</span>
                  <span className={`px-2 py-0.5 rounded font-bold text-[10px] uppercase inline-block ${
                    data.status === 'GRADED'
                      ? 'bg-emerald-50 text-emerald-700 border border-emerald-200'
                      : 'bg-amber-50 text-amber-700 border border-amber-200'
                  }`}>
                    {data.status || 'CHỜ XỬ LÝ'}
                  </span>
                </div>
              </div>
            </div>

            {/* Questions List & Overrides */}
            <div className="space-y-3">
              <h4 className="text-xs font-bold text-on-surface uppercase tracking-wider flex items-center gap-2">
                <BookOpen className="w-3.5 h-3.5 text-primary" />
                <span>Chi tiết điểm theo từng câu hỏi ({data.questionScores?.length || 0})</span>
              </h4>

              {data.questionScores && data.questionScores.length > 0 ? (
                data.questionScores.map((q, idx) => {
                  const isEditing = editingQuestionId === q.questionId;
                  return (
                    <div
                      key={q.questionId || idx}
                      className={`p-3.5 rounded-xl border transition-all ${
                        q.isOverridden
                          ? 'bg-amber-50/40 border-amber-300 dark:bg-amber-950/20'
                          : 'bg-surface-container-lowest border-outline-variant/30'
                      }`}
                    >
                      <div className="flex items-start justify-between gap-3">
                        <div className="flex flex-col gap-1">
                          <div className="flex items-center gap-2">
                            <span className="w-5 h-5 rounded bg-primary/10 text-primary font-mono text-[11px] font-bold flex items-center justify-center">
                              {q.displayOrder || idx + 1}
                            </span>
                            <span className="font-semibold text-xs text-on-surface">
                              {q.questionTitle || `Câu hỏi ${idx + 1}`}
                            </span>
                            {q.isOverridden && (
                              <span className="px-2 py-0.2 rounded text-[10px] font-bold bg-amber-100 text-amber-800 border border-amber-200">
                                Đã điều chỉnh điểm
                              </span>
                            )}
                          </div>

                          <div className="flex items-center gap-3 text-xs text-on-surface-variant font-mono pl-7">
                            <span>Điểm tối đa: <strong className="text-on-surface">{q.maxScore} đ</strong></span>
                            <span>•</span>
                            <span>Chấm tự động: <strong className="text-on-surface">{q.autoScore != null ? q.autoScore : '--'} đ</strong></span>
                            <span>•</span>
                            <span>
                              Điểm hiệu lực:{' '}
                              <strong className={q.isOverridden ? 'text-amber-700 font-bold' : 'text-emerald-700 font-bold'}>
                                {q.effectiveScore != null ? q.effectiveScore : '--'} đ
                              </strong>
                            </span>
                          </div>

                          {q.isOverridden && q.overrideReason && (
                            <p className="text-[11px] text-amber-800 dark:text-amber-300 italic pl-7 mt-0.5">
                              Lý do điều chỉnh: &ldquo;{q.overrideReason}&rdquo; {q.overriddenBy && `— bởi ${q.overriddenBy}`}
                            </p>
                          )}
                        </div>

                        {/* Action Buttons */}
                        <div className="flex items-center gap-2 shrink-0">
                          {!isEditing ? (
                            <>
                              <button
                                onClick={() => handleStartEdit(q)}
                                className="px-2.5 py-1 rounded-lg text-xs font-medium text-primary hover:bg-primary/10 border border-primary/20 transition-all flex items-center gap-1 cursor-pointer"
                                title="Điều chỉnh điểm thủ công cho câu hỏi này"
                              >
                                <Edit3 className="w-3 h-3" />
                                <span>Điều chỉnh</span>
                              </button>
                              {q.isOverridden && (
                                <button
                                  onClick={() => handleClearOverride(q.questionId)}
                                  disabled={clearOverrideMutation.isPending}
                                  className="px-2.5 py-1 rounded-lg text-xs font-medium text-rose-700 hover:bg-rose-50 border border-rose-200 transition-all flex items-center gap-1 cursor-pointer disabled:opacity-50"
                                  title="Khôi phục lại điểm tự động ban đầu"
                                >
                                  <RotateCcw className="w-3 h-3" />
                                  <span>Khôi phục</span>
                                </button>
                              )}
                            </>
                          ) : null}
                        </div>
                      </div>

                      {/* Inline Override Form */}
                      {isEditing && (
                        <div className="mt-3 pt-3 border-t border-outline-variant/30 pl-7 flex flex-col gap-2.5 animate-in fade-in duration-150">
                          <div className="flex items-center gap-3">
                            <div className="flex flex-col gap-1 w-36">
                              <label className="text-[11px] font-semibold text-outline">Điểm số mới (Thang 10):</label>
                              <input
                                type="number"
                                step="0.1"
                                min="0"
                                max="10"
                                value={overrideScoreInput}
                                onChange={(e) => setOverrideScoreInput(e.target.value)}
                                className="px-2.5 py-1 text-xs rounded-lg border border-outline-variant/40 bg-surface-container-low font-mono focus:outline-none focus:border-primary"
                                placeholder="0.00"
                              />
                            </div>
                            <div className="flex flex-col gap-1 flex-1">
                              <label className="text-[11px] font-semibold text-outline">Lý do điều chỉnh (Audit Rationale):</label>
                              <input
                                type="text"
                                value={overrideReasonInput}
                                onChange={(e) => setOverrideReasonInput(e.target.value)}
                                className="px-2.5 py-1 text-xs rounded-lg border border-outline-variant/40 bg-surface-container-low focus:outline-none focus:border-primary"
                                placeholder="Ví dụ: Chấm bù thuật toán đúng nhưng thiếu định dạng output..."
                              />
                            </div>
                          </div>

                          <div className="flex items-center justify-end gap-2 pt-1">
                            <button
                              type="button"
                              onClick={handleCancelEdit}
                              className="px-3 py-1 rounded-lg text-xs border border-outline-variant/40 hover:bg-surface-container text-on-surface-variant cursor-pointer"
                            >
                              Hủy
                            </button>
                            <button
                              type="button"
                              onClick={() => handleSaveOverride(q.questionId)}
                              disabled={setOverrideMutation.isPending}
                              className="px-3 py-1 rounded-lg text-xs font-semibold bg-primary text-on-primary hover:bg-primary/90 flex items-center gap-1.5 cursor-pointer disabled:opacity-50"
                            >
                              {setOverrideMutation.isPending ? (
                                <Loader2 className="w-3 h-3 animate-spin" />
                              ) : (
                                <Save className="w-3 h-3" />
                              )}
                              <span>Lưu điểm điều chỉnh</span>
                            </button>
                          </div>
                        </div>
                      )}
                    </div>
                  );
                })
              ) : (
                <div className="p-4 rounded-xl bg-surface-container-low text-center text-xs text-outline">
                  Bài thi chưa có câu hỏi nào được cấu hình điểm.
                </div>
              )}
            </div>
          </div>
        ) : null}

        {/* Footer */}
        <div className="flex justify-end pt-3 border-t border-outline-variant/30">
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-2 text-xs rounded-lg border border-outline-variant/40 hover:bg-surface-container transition-colors cursor-pointer"
          >
            Đóng
          </button>
        </div>
      </div>
    </div>
  );
}
