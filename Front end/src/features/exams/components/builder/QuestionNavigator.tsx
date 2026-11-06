'use client';

import React, { useState } from 'react';
import { BuilderQuestion, QuestionDifficulty } from '../../types/builder.types';
import { 
  FileCode2, 
  Plus, 
  Trash2, 
  ChevronUp, 
  ChevronDown, 
  AlertTriangle, 
  Sparkles,
  Scale,
  CheckCircle2,
  AlertCircle
} from 'lucide-react';

interface QuestionNavigatorProps {
  questions: BuilderQuestion[];
  selectedQuestionId: string | null;
  totalAllocatedPoints: number;
  onSelectQuestion: (questionId: string) => void;
  onAddQuestion: () => void;
  onDeleteQuestion: (questionId: string) => void;
  onMoveQuestion: (questionId: string, direction: 'UP' | 'DOWN') => void;
  onOpenAiGenerator: () => void;
  onDistributeEqually?: () => void;
}

const difficultyBadgeMap: Record<QuestionDifficulty, { label: string; className: string }> = {
  EASY: { label: 'Dễ', className: 'bg-emerald-50 text-emerald-700 border-emerald-200' },
  MEDIUM: { label: 'Vừa', className: 'bg-amber-50 text-amber-700 border-amber-200' },
  HARD: { label: 'Khó', className: 'bg-rose-50 text-rose-700 border-rose-200' },
};

export const QuestionNavigator: React.FC<QuestionNavigatorProps> = ({
  questions,
  selectedQuestionId,
  totalAllocatedPoints,
  onSelectQuestion,
  onAddQuestion,
  onDeleteQuestion,
  onMoveQuestion,
  onOpenAiGenerator,
  onDistributeEqually,
}) => {
  const [questionToDelete, setQuestionToDelete] = useState<BuilderQuestion | null>(null);

  const isExact10 = Math.abs(totalAllocatedPoints - 10.0) < 0.001;
  const isOver10 = totalAllocatedPoints > 10.0 && !isExact10;
  const diffPoints = Math.abs(10.0 - totalAllocatedPoints).toFixed(2);

  const confirmDelete = () => {
    if (questionToDelete) {
      onDeleteQuestion(questionToDelete.id);
      setQuestionToDelete(null);
    }
  };

  return (
    <aside className="w-full lg:w-80 shrink-0 bg-surface-container-lowest border-r border-outline-variant/30 flex flex-col h-full overflow-hidden">
      {/* Navigator Header */}
      <div className="p-4 border-b border-outline-variant/30 bg-surface-container-low/50 flex items-center justify-between">
        <div>
          <h2 className="text-xs font-semibold text-on-surface uppercase tracking-wider flex items-center gap-1.5">
            <FileCode2 className="w-3.5 h-3.5 text-primary" />
            <span>Danh sách câu hỏi</span>
          </h2>
          <p className="text-[11px] text-outline mt-0.5">
            Tổng cộng: <strong className="text-on-surface">{questions.length}</strong> câu hỏi
          </p>
        </div>
        <div className="flex items-center gap-1">
          <button
            type="button"
            onClick={onOpenAiGenerator}
            title="Sinh thêm câu hỏi với AI"
            className="p-1.5 rounded-lg bg-purple-600 text-white hover:bg-purple-700 transition-colors cursor-pointer text-xs flex items-center gap-1 font-medium shadow-xs"
          >
            <Sparkles className="w-3.5 h-3.5" />
            <span className="hidden sm:inline">AI</span>
          </button>
          <button
            type="button"
            onClick={onAddQuestion}
            title="Thêm câu hỏi thủ công"
            className="p-1.5 rounded-lg bg-primary text-on-primary hover:bg-primary/90 transition-colors cursor-pointer text-xs flex items-center gap-1 font-medium shadow-xs"
          >
            <Plus className="w-3.5 h-3.5" />
            <span className="hidden sm:inline">Thêm</span>
          </button>
        </div>
      </div>

      {/* Questions Scrollable List */}
      <div className="flex-1 overflow-y-auto p-3 space-y-2">
        {questions.length === 0 ? (
          <div className="text-center py-10 px-4 border border-dashed border-outline-variant/60 rounded-xl bg-surface-container-low/20">
            <FileCode2 className="w-8 h-8 text-outline mx-auto mb-2 opacity-50" />
            <p className="text-xs font-medium text-on-surface">Chưa có câu hỏi nào</p>
            <p className="text-[11px] text-outline mt-1 mb-3">
              Nhấn nút AI hoặc &quot;Thêm&quot; để bắt đầu soạn đề thi.
            </p>
            <button
              type="button"
              onClick={onOpenAiGenerator}
              className="px-3 py-1.5 bg-purple-600 text-white rounded-lg text-xs font-medium hover:bg-purple-700 transition-colors inline-flex items-center gap-1.5"
            >
              <Sparkles className="w-3.5 h-3.5" />
              <span>Sinh câu hỏi với AI</span>
            </button>
          </div>
        ) : (
          questions.map((question, index) => {
            const isSelected = question.id === selectedQuestionId;
            const diff = difficultyBadgeMap[question.difficulty] || difficultyBadgeMap.MEDIUM;
            const publicTcCount = question.testCases.filter((tc) => !tc.isHidden).length;
            const hiddenTcCount = question.testCases.filter((tc) => tc.isHidden).length;

            const isTitleValid = Boolean(question.title?.trim() && question.title.trim().length <= 255);
            const isProblemStatementValid = Boolean(question.problemStatement?.trim());
            const isMaxScoreValid =
              typeof question.maxScore === 'number' &&
              !isNaN(question.maxScore) &&
              question.maxScore > 0 &&
              /^\d+(\.\d{1,2})?$/.test(question.maxScore.toString());
            const tcSum = Number(question.testCases.reduce((s, tc) => s + (Number(tc.points) || 0), 0).toFixed(2));
            const isTcOver = tcSum > question.maxScore + 0.005;
            const hasPublicTc = question.testCases.some((tc) => !tc.isHidden);
            const hasTcContentIssue = question.testCases.some((tc) => {
              const isExplicitEmpty = Boolean(tc.allowEmptyOutput);
              return (
                !tc.name?.trim() ||
                ((!tc.expectedOutput || !tc.expectedOutput.trim()) && !isExplicitEmpty) ||
                Boolean(tc.inputData && tc.inputData.length > 0 && !tc.inputData.trim())
              );
            });

            const validationWarnings: string[] = [];
            if (!isTitleValid) validationWarnings.push('Tiêu đề câu hỏi chưa hợp lệ (1-255 ký tự)');
            if (!isProblemStatementValid) validationWarnings.push('Chưa có nội dung mô tả đề bài');
            if (!isMaxScoreValid) validationWarnings.push('Điểm câu hỏi phải > 0 và tối đa 2 chữ số thập phân');
            if (isTcOver) validationWarnings.push(`Tổng điểm test case (${tcSum.toFixed(2)}đ) vượt điểm câu hỏi (${question.maxScore.toFixed(2)}đ)`);
            if (!hasPublicTc) validationWarnings.push('Cần ít nhất 1 test case công khai');
            if (hasTcContentIssue) validationWarnings.push('Một số test case chưa hoàn thiện dữ liệu');

            const isIncompleteOrInvalid = validationWarnings.length > 0;

            return (
              <div
                key={question.id}
                data-testid={`question-nav-item-${index}`}
                onClick={() => onSelectQuestion(question.id)}
                className={`group relative p-3 rounded-xl border text-left cursor-pointer transition-all ${
                  isSelected
                    ? 'bg-primary/5 border-primary shadow-xs ring-1 ring-primary/20'
                    : 'bg-surface-container-lowest border-outline-variant/30 hover:border-outline-variant hover:bg-surface-container-low/30'
                }`}
              >
                <div className="flex items-start justify-between gap-2">
                  <div className="flex items-center gap-1.5 min-w-0 flex-1">
                    <span
                      className={`inline-flex items-center justify-center w-5 h-5 rounded-full text-[11px] font-bold shrink-0 ${
                        isSelected
                          ? 'bg-primary text-on-primary'
                          : 'bg-surface-container text-on-surface-variant'
                      }`}
                    >
                      {index + 1}
                    </span>
                    <h3 className="text-xs font-semibold text-on-surface truncate min-w-0">
                      {question.title || `Câu hỏi #${index + 1}`}
                    </h3>
                    {isIncompleteOrInvalid && (
                      <span
                        data-testid={`question-nav-warning-${index}`}
                        title={validationWarnings.join(' • ')}
                        className="inline-flex items-center text-amber-500 hover:text-amber-600 shrink-0"
                      >
                        <AlertCircle className="w-3.5 h-3.5" />
                      </span>
                    )}
                  </div>

                  {/* Move & Delete Actions */}
                  <div className="flex items-center gap-0.5 opacity-80 group-hover:opacity-100 transition-opacity">
                    <button
                      type="button"
                      disabled={index === 0}
                      onClick={(e) => {
                        e.stopPropagation();
                        onMoveQuestion(question.id, 'UP');
                      }}
                      title="Di chuyển lên"
                      className="p-1 text-outline hover:text-on-surface disabled:opacity-30 disabled:cursor-not-allowed rounded"
                    >
                      <ChevronUp className="w-3 h-3" />
                    </button>
                    <button
                      type="button"
                      disabled={index === questions.length - 1}
                      onClick={(e) => {
                        e.stopPropagation();
                        onMoveQuestion(question.id, 'DOWN');
                      }}
                      title="Di chuyển xuống"
                      className="p-1 text-outline hover:text-on-surface disabled:opacity-30 disabled:cursor-not-allowed rounded"
                    >
                      <ChevronDown className="w-3 h-3" />
                    </button>
                    <button
                      type="button"
                      onClick={(e) => {
                        e.stopPropagation();
                        setQuestionToDelete(question);
                      }}
                      title="Xóa câu hỏi này"
                      className="p-1 text-rose-500 hover:text-rose-700 hover:bg-rose-50 rounded"
                    >
                      <Trash2 className="w-3 h-3" />
                    </button>
                  </div>
                </div>

                {/* Sub info */}
                {(() => {
                  const tcSum = Number(question.testCases.reduce((s, tc) => s + (Number(tc.points) || 0), 0).toFixed(2));
                  const isTcOver = tcSum > question.maxScore + 0.005;

                  return (
                    <div className="flex items-center justify-between gap-2 mt-2 text-[11px]">
                      <span
                        className={`px-1.5 py-0.5 rounded border text-[10px] font-medium ${diff.className}`}
                      >
                        {diff.label}
                      </span>
                      <div className="flex items-center gap-1.5 text-outline text-[11px]">
                        <span
                          title={`${publicTcCount} công khai, ${hiddenTcCount} ẩn (Tổng điểm TC: ${tcSum.toFixed(2)}đ)`}
                          className={isTcOver ? "text-rose-600 font-semibold flex items-center gap-0.5" : ""}
                        >
                          {isTcOver && <AlertTriangle className="w-2.5 h-2.5 text-rose-600" />}
                          {question.testCases.length} TCs ({tcSum.toFixed(2)}đ)
                        </span>
                        <span>•</span>
                        <span className="font-semibold text-on-surface-variant font-mono">
                          {question.maxScore.toFixed(2)}đ
                        </span>
                      </div>
                    </div>
                  );
                })()}
              </div>
            );
          })
        )}
      </div>

      {/* Score Allocation Footer */}
      <div className="p-3 border-t border-outline-variant/30 bg-surface-container-low/60 space-y-2 shrink-0">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-1.5">
            <span className="text-[11px] font-semibold text-on-surface">Tổng điểm phân bổ:</span>
            <span
              data-testid="navigator-total-score"
              className={`font-mono text-xs font-bold ${
                isExact10
                  ? 'text-emerald-600'
                  : isOver10
                  ? 'text-rose-600'
                  : 'text-amber-600'
              }`}
            >
              {totalAllocatedPoints.toFixed(2)} / 10.00đ
            </span>
          </div>

          {/* Status Badge */}
          {isExact10 ? (
            <span className="inline-flex items-center gap-1 px-1.5 py-0.5 rounded text-[10px] font-bold bg-emerald-100 text-emerald-800 border border-emerald-200">
              <CheckCircle2 className="w-3 h-3 text-emerald-600" />
              <span>Chuẩn 10đ</span>
            </span>
          ) : isOver10 ? (
            <span className="inline-flex items-center gap-1 px-1.5 py-0.5 rounded text-[10px] font-bold bg-rose-100 text-rose-800 border border-rose-200">
              <AlertCircle className="w-3 h-3 text-rose-600" />
              <span>Thừa {diffPoints}đ</span>
            </span>
          ) : (
            <span className="inline-flex items-center gap-1 px-1.5 py-0.5 rounded text-[10px] font-bold bg-amber-100 text-amber-800 border border-amber-200">
              <AlertCircle className="w-3 h-3 text-amber-600" />
              <span>Thiếu {diffPoints}đ</span>
            </span>
          )}
        </div>

        {/* Visual Progress Bar */}
        <div className="w-full bg-surface-container-highest rounded-full h-1.5 overflow-hidden">
          <div
            className={`h-full transition-all duration-300 ${
              isExact10
                ? 'bg-emerald-500'
                : isOver10
                ? 'bg-rose-500'
                : 'bg-amber-500'
            }`}
            style={{ width: `${Math.min(100, (totalAllocatedPoints / 10.0) * 100)}%` }}
          />
        </div>

        {/* Quick Helper Button: Chia đều 10 điểm */}
        {onDistributeEqually && (
          <button
            type="button"
            data-testid="distribute-equally-btn"
            disabled={questions.length === 0}
            onClick={onDistributeEqually}
            title="Tự động chia đều 10 điểm cho tất cả câu hỏi"
            className="w-full py-1.5 px-2 bg-surface-container hover:bg-surface-container-high disabled:opacity-40 disabled:cursor-not-allowed border border-outline-variant/40 rounded-lg text-xs font-medium text-on-surface flex items-center justify-center gap-1.5 transition-colors cursor-pointer"
          >
            <Scale className="w-3.5 h-3.5 text-primary" />
            <span>Chia đều 10 điểm</span>
          </button>
        )}
      </div>

      {/* Confirmation Modal for Delete Question */}
      {questionToDelete && (
        <div className="fixed inset-0 z-[70] flex items-center justify-center p-4 bg-black/60 backdrop-blur-xs animate-in fade-in">
          <div
            role="alertdialog"
            aria-labelledby="delete-q-title"
            className="bg-surface-container-lowest max-w-sm w-full rounded-2xl p-5 shadow-2xl border border-outline-variant/40 space-y-4"
          >
            <div className="flex items-start gap-3">
              <div className="p-2.5 rounded-full bg-rose-100 text-rose-600 shrink-0">
                <AlertTriangle className="w-5 h-5" />
              </div>
              <div className="space-y-1">
                <h3 id="delete-q-title" className="text-sm font-bold text-on-surface">
                  Xác nhận xóa câu hỏi?
                </h3>
                <p className="text-xs text-on-surface-variant">
                  Câu hỏi &quot;{questionToDelete.title}&quot; cùng toàn bộ{' '}
                  <strong>{questionToDelete.testCases.length} test cases</strong> độc lập của nó sẽ bị xóa. Hành động này không thể hoàn tác.
                </p>
              </div>
            </div>
            <div className="flex items-center justify-end gap-2 pt-2 border-t border-outline-variant/30">
              <button
                type="button"
                onClick={() => setQuestionToDelete(null)}
                className="px-3 py-1.5 rounded-lg border border-outline-variant/40 text-xs font-medium text-on-surface hover:bg-surface-container transition-colors cursor-pointer"
              >
                Hủy
              </button>
              <button
                type="button"
                onClick={confirmDelete}
                className="px-3.5 py-1.5 rounded-lg bg-rose-600 hover:bg-rose-700 text-white text-xs font-semibold shadow-xs transition-colors cursor-pointer flex items-center gap-1.5"
              >
                <Trash2 className="w-3.5 h-3.5" />
                <span>Xóa câu hỏi</span>
              </button>
            </div>
          </div>
        </div>
      )}
    </aside>
  );
};
