'use client';

import React, { useState } from 'react';
import { BuilderExamState } from '../../types/builder.types';
import { 
  X, 
  Eye, 
  Lock
} from 'lucide-react';

interface ExamPreviewModalProps {
  exam: BuilderExamState;
  onClose: () => void;
}

export const ExamPreviewModal: React.FC<ExamPreviewModalProps> = ({ exam, onClose }) => {
  const [selectedQuestionIndex, setSelectedQuestionIndex] = useState(0);

  const activeQuestion = exam.questions[selectedQuestionIndex] || exam.questions[0];
  const totalAllocated = exam.questions.reduce((sum, q) => sum + (Number(q.maxScore) || 0), 0);

  return (
    <div className="fixed inset-0 z-[70] flex items-center justify-center p-4 bg-black/60 backdrop-blur-xs animate-in fade-in">
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="exam-preview-title"
        className="bg-surface-container-lowest max-w-4xl w-full rounded-2xl shadow-2xl border border-outline-variant/40 flex flex-col max-h-[90vh] overflow-hidden"
      >
        {/* Header */}
        <div className="flex items-center justify-between p-4 bg-surface-container-low border-b border-outline-variant/30">
          <div className="flex items-center gap-2.5">
            <div className="p-2 rounded-xl bg-primary text-on-primary shadow-xs">
              <Eye className="w-4 h-4" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h2 id="exam-preview-title" className="text-sm font-bold text-on-surface">
                  Xem trước đề thi (Góc nhìn sinh viên)
                </h2>
                <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-amber-100 text-amber-800 border border-amber-200">
                  CHẾ ĐỘ PREVIEW
                </span>
              </div>
              <p className="text-xs text-outline">
                {exam.title} • {exam.durationMinutes} phút • {exam.programmingLanguage} • Thang điểm chuẩn: <strong className="text-on-surface">10.0đ</strong> (Tổng câu hỏi: <strong className={Math.abs(totalAllocated - 10) < 0.001 ? 'text-emerald-600' : 'text-amber-600'}>{totalAllocated.toFixed(2)}đ</strong>)
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="p-1.5 text-outline hover:text-on-surface rounded-lg cursor-pointer"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Question Selector Tabs */}
        <div className="flex items-center gap-2 p-3 bg-surface-container-lowest border-b border-outline-variant/20 overflow-x-auto">
          {exam.questions.map((q, idx) => (
            <button
              key={q.id}
              type="button"
              onClick={() => setSelectedQuestionIndex(idx)}
              className={`px-3 py-1.5 rounded-lg text-xs font-semibold shrink-0 transition-all cursor-pointer flex items-center gap-1.5 ${
                selectedQuestionIndex === idx
                  ? 'bg-primary text-on-primary shadow-xs'
                  : 'bg-surface-container text-on-surface hover:bg-surface-container-high'
              }`}
            >
              <span>Câu {idx + 1}:</span>
              <span className="max-w-[120px] truncate">{q.title || `Bài ${idx + 1}`}</span>
              <span className="text-[10px] opacity-80 font-mono">({q.maxScore.toFixed(2)}đ)</span>
            </button>
          ))}
        </div>

        {/* Content Body */}
        <div className="p-5 overflow-y-auto space-y-5 flex-1 text-xs">
          {activeQuestion ? (
            <div className="space-y-4">
              {/* Question Headline */}
              <div className="flex items-start justify-between gap-4 pb-3 border-b border-outline-variant/30">
                <div>
                  <h3 className="text-base font-bold text-on-surface">
                    Câu {selectedQuestionIndex + 1}: {activeQuestion.title}
                  </h3>
                  <div className="flex items-center gap-3 mt-1 text-outline">
                    <span>Độ khó: <strong>{activeQuestion.difficulty}</strong></span>
                    <span>•</span>
                    <span>
                      Điểm tối đa:{' '}
                      <strong className="text-primary font-mono">
                        {activeQuestion.maxScore.toFixed(2)} / 10.0 điểm
                      </strong>{' '}
                      <span className="text-[11px] text-outline">
                        ({((activeQuestion.maxScore / 10.0) * 100).toFixed(0)}% đề thi)
                      </span>
                    </span>
                  </div>
                </div>
              </div>

              {/* Problem statement */}
              <div>
                <h4 className="text-xs font-bold text-on-surface uppercase tracking-wider mb-1 text-primary">
                  1. Yêu cầu đề bài
                </h4>
                <div className="p-3.5 rounded-xl bg-surface-container-low/50 border border-outline-variant/30 text-on-surface font-body leading-relaxed whitespace-pre-wrap">
                  {activeQuestion.problemStatement || 'Chưa có mô tả đề bài.'}
                </div>
              </div>

              {/* Specifications */}
              {(activeQuestion.inputSpecification || activeQuestion.outputSpecification || activeQuestion.constraints) && (
                <div className="space-y-3">
                  <h4 className="text-xs font-bold text-on-surface uppercase tracking-wider text-primary">
                    2. Quy cách kỹ thuật & Ràng buộc
                  </h4>
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                    {activeQuestion.inputSpecification && (
                      <div className="p-3 rounded-xl bg-surface-container-low/40 border border-outline-variant/30">
                        <span className="font-bold text-outline uppercase tracking-wider text-[10px] block mb-1">
                          Đầu vào (Input Format)
                        </span>
                        <p className="text-on-surface leading-normal">{activeQuestion.inputSpecification}</p>
                      </div>
                    )}
                    {activeQuestion.outputSpecification && (
                      <div className="p-3 rounded-xl bg-surface-container-low/40 border border-outline-variant/30">
                        <span className="font-bold text-outline uppercase tracking-wider text-[10px] block mb-1">
                          Đầu ra (Output Format)
                        </span>
                        <p className="text-on-surface leading-normal">{activeQuestion.outputSpecification}</p>
                      </div>
                    )}
                  </div>
                  {activeQuestion.constraints && (
                    <div className="p-3 rounded-xl bg-surface-container-low/40 border border-outline-variant/30 font-mono text-[11px]">
                      <span className="font-bold text-outline uppercase tracking-wider text-[10px] block mb-1 font-sans">
                        Giới hạn (Constraints)
                      </span>
                      <p className="text-on-surface">{activeQuestion.constraints}</p>
                    </div>
                  )}
                </div>
              )}

              {/* Examples */}
              {(activeQuestion.sampleInput || activeQuestion.sampleOutput) && (
                <div className="space-y-2">
                  <h4 className="text-xs font-bold text-on-surface uppercase tracking-wider text-primary">
                    3. Ví dụ minh họa (Sample)
                  </h4>
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-3 font-mono">
                    <div className="p-3 rounded-xl bg-surface-container-low border border-outline-variant/40">
                      <span className="font-sans font-bold text-outline uppercase text-[10px] block mb-1">
                        Sample Input
                      </span>
                      <pre className="whitespace-pre-wrap break-all text-on-surface">{activeQuestion.sampleInput}</pre>
                    </div>
                    <div className="p-3 rounded-xl bg-surface-container-low border border-outline-variant/40">
                      <span className="font-sans font-bold text-outline uppercase text-[10px] block mb-1">
                        Sample Output
                      </span>
                      <pre className="whitespace-pre-wrap break-all text-on-surface">{activeQuestion.sampleOutput}</pre>
                    </div>
                  </div>
                </div>
              )}

              {/* Test Cases from Student Perspective */}
              <div className="space-y-3 pt-2">
                <h4 className="text-xs font-bold text-on-surface uppercase tracking-wider text-primary">
                  4. Các trường hợp kiểm thử (Test Cases)
                </h4>
                <div className="space-y-2">
                  {activeQuestion.testCases.map((tc, idx) => (
                    <div
                      key={tc.id}
                      className="p-3 rounded-xl border border-outline-variant/30 bg-surface-container-low/30 space-y-1.5"
                    >
                      <div className="flex items-center justify-between">
                        <span className="font-semibold text-on-surface">
                          Test Case #{idx + 1}: {tc.name}
                        </span>
                        {tc.isHidden ? (
                          <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-bold bg-purple-50 text-purple-700 border border-purple-200">
                            <Lock className="w-2.5 h-2.5" />
                            <span>Trường hợp ẩn ({tc.points}đ)</span>
                          </span>
                        ) : (
                          <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-bold bg-emerald-50 text-emerald-700 border border-emerald-200">
                            <Eye className="w-2.5 h-2.5" />
                            <span>Công khai ({tc.points}đ)</span>
                          </span>
                        )}
                      </div>

                      {tc.isHidden ? (
                        <div className="p-2 rounded bg-purple-50/50 border border-purple-100 text-[11px] text-purple-800 italic">
                          🔒 Dữ liệu đầu vào và kết quả được bảo mật để hệ thống chấm điểm tự động sau khi nộp bài.
                        </div>
                      ) : (
                        <div className="grid grid-cols-2 gap-2 text-[11px] font-mono">
                          <div className="p-2 rounded bg-surface-container border border-outline-variant/20">
                            <span className="font-sans text-[10px] text-outline block mb-0.5">Input:</span>
                            <pre className="whitespace-pre-wrap break-all text-on-surface">{tc.inputData || '(None)'}</pre>
                          </div>
                          <div className="p-2 rounded bg-surface-container border border-outline-variant/20">
                            <span className="font-sans text-[10px] text-outline block mb-0.5">Expected Output:</span>
                            <pre className="whitespace-pre-wrap break-all text-on-surface">{tc.expectedOutput}</pre>
                          </div>
                        </div>
                      )}
                    </div>
                  ))}
                </div>
              </div>
            </div>
          ) : (
            <p className="text-center py-10 text-outline">Không có câu hỏi nào để hiển thị.</p>
          )}
        </div>

        {/* Footer */}
        <div className="p-4 bg-surface-container-low border-t border-outline-variant/30 flex items-center justify-between">
          <span className="text-xs text-outline">
            Tổng cộng: <strong>{exam.questions.length}</strong> câu hỏi •{' '}
            <strong>{exam.questions.reduce((acc, q) => acc + q.testCases.length, 0)}</strong> test cases
          </span>
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-1.5 bg-primary text-on-primary rounded-lg text-xs font-semibold hover:bg-primary/90 cursor-pointer shadow-xs"
          >
            Đóng xem trước
          </button>
        </div>
      </div>
    </div>
  );
};
