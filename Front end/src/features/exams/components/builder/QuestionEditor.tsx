'use client';

import React, { useState } from 'react';
import { BuilderQuestion, BuilderTestCase, QuestionDifficulty } from '../../types/builder.types';
import { TestCaseManager } from './TestCaseManager';
import { 
  FileText, 
  BookOpen, 
  Terminal, 
  Sliders, 
  AlertTriangle,
  AlertCircle
} from 'lucide-react';

interface QuestionEditorProps {
  question: BuilderQuestion;
  questionIndex: number;
  totalQuestions: number;
  activeTab?: 'statement' | 'specs' | 'testcases';
  onTabChange?: (tab: 'statement' | 'specs' | 'testcases') => void;
  onUpdateQuestion: (updated: Partial<BuilderQuestion>) => void;
  onAddTestCase: (newTestCase: Omit<BuilderTestCase, 'id' | 'displayOrder'>) => void;
  onUpdateTestCase: (updatedTestCase: BuilderTestCase) => void;
  onDeleteTestCase: (testCaseId: string) => void;
}

export const QuestionEditor: React.FC<QuestionEditorProps> = ({
  question,
  questionIndex,
  totalQuestions,
  activeTab: controlledTab,
  onTabChange,
  onUpdateQuestion,
  onAddTestCase,
  onUpdateTestCase,
  onDeleteTestCase,
}) => {
  const [internalTab, setInternalTab] = useState<'statement' | 'specs' | 'testcases'>('statement');
  const activeTab = controlledTab !== undefined ? controlledTab : internalTab;
  const setActiveTab = (tab: 'statement' | 'specs' | 'testcases') => {
    if (onTabChange) {
      onTabChange(tab);
    } else {
      setInternalTab(tab);
    }
  };

  const totalTestCaseScore = Number(
    question.testCases.reduce((sum, tc) => sum + (Number(tc.points) || 0), 0).toFixed(2)
  );
  const isTcOverAllocated = totalTestCaseScore > question.maxScore + 0.005;

  // Question Form Validation
  const isTitleEmpty = !question.title?.trim();
  const isTitleTooLong = (question.title?.trim().length || 0) > 255;
  const isTitleInvalid = isTitleEmpty || isTitleTooLong;

  const isScoreInvalid = isNaN(question.maxScore) || question.maxScore <= 0;
  const scoreStr = question.maxScore.toString();
  const isScoreExcessiveDecimals = scoreStr.includes('.') && scoreStr.split('.')[1].length > 2;
  const isScoreError = isScoreInvalid || isScoreExcessiveDecimals;

  const isStatementEmpty = !question.problemStatement?.trim();

  return (
    <div className="flex-1 flex flex-col h-full overflow-y-auto bg-surface-container-lowest">
      {/* Editor Top Bar */}
      <div className="p-4 sm:p-6 border-b border-outline-variant/30 bg-surface-container-low/40 space-y-4">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          <div className="flex items-center gap-2">
            <span className="px-2.5 py-1 rounded-lg bg-primary/10 text-primary text-xs font-bold border border-primary/20">
              Câu hỏi {questionIndex + 1} / {totalQuestions}
            </span>
            <span className="text-xs text-outline">
              ID: <code className="font-mono text-[11px]">{question.id}</code>
            </span>
          </div>

          <div className="flex items-center gap-3">
            {/* Difficulty Selector */}
            <div className="flex items-center gap-1.5">
              <label className="text-xs font-medium text-outline">Độ khó:</label>
              <select
                value={question.difficulty}
                onChange={(e) => onUpdateQuestion({ difficulty: e.target.value as QuestionDifficulty })}
                className="px-2.5 py-1 bg-surface-container-lowest border border-outline-variant/40 rounded-lg text-xs font-semibold text-on-surface focus:outline-none focus:border-primary"
              >
                <option value="EASY">Dễ (EASY)</option>
                <option value="MEDIUM">Vừa (MEDIUM)</option>
                <option value="HARD">Khó (HARD)</option>
              </select>
            </div>

            {/* Max Score (10-point Platform Scale) */}
            <div className="flex flex-col items-end">
              <div className={`flex items-center gap-1.5 bg-surface-container-low px-2.5 py-1 rounded-lg border ${
                isScoreError ? 'border-rose-400 ring-1 ring-rose-200' : 'border-outline-variant/30'
              }`}>
                <label htmlFor="question-max-score-input" className="text-xs font-medium text-outline">
                  Điểm (thang 10):
                </label>
                <input
                  id="question-max-score-input"
                  data-testid="question-max-score-input"
                  type="number"
                  min="0.01"
                  max="10"
                  step="0.01"
                  value={question.maxScore}
                  onChange={(e) => {
                    const val = parseFloat(e.target.value);
                    onUpdateQuestion({ maxScore: isNaN(val) ? 0 : val });
                  }}
                  className={`w-16 px-2 py-0.5 bg-surface-container-lowest border rounded-md text-xs font-bold text-center focus:outline-none font-mono shadow-2xs ${
                    isScoreError ? 'border-rose-500 text-rose-600 focus:border-rose-500' : 'border-outline-variant/40 text-primary focus:border-primary'
                  }`}
                />
                <span className="text-[11px] font-semibold text-outline">đ</span>
                <span className="text-[10px] text-outline-variant font-medium bg-surface-container px-1.5 py-0.5 rounded">
                  {((question.maxScore / 10.0) * 100).toFixed(0)}% đề
                </span>
              </div>
              {isScoreError && (
                <span className="text-[10px] text-rose-600 font-semibold mt-0.5" data-testid="question-max-score-error">
                  {isScoreInvalid ? 'Điểm phải > 0' : 'Tối đa 2 số thập phân'}
                </span>
              )}
            </div>
          </div>
        </div>

        {/* Question Title Input */}
        <div>
          <div className="flex items-center justify-between mb-1">
            <label htmlFor="question-title-input" className="block text-xs font-semibold text-outline uppercase tracking-wider">
              Tiêu đề câu hỏi / Tên bài toán *
            </label>
            <span className={`text-[11px] font-mono ${isTitleTooLong ? 'text-rose-600 font-bold' : 'text-outline'}`}>
              {(question.title || '').length} / 255 ký tự
            </span>
          </div>
          <input
            id="question-title-input"
            data-testid="question-title-input"
            type="text"
            maxLength={255}
            value={question.title}
            onChange={(e) => onUpdateQuestion({ title: e.target.value })}
            placeholder="Nhập tiêu đề câu hỏi (Ví dụ: Tìm phần tử lớn thứ k trong mảng)..."
            className={`w-full px-3.5 py-2 bg-surface-container-lowest border rounded-xl text-sm font-bold text-on-surface focus:outline-none transition-all shadow-xs ${
              isTitleInvalid
                ? 'border-rose-500 focus:border-rose-500 ring-1 ring-rose-200'
                : 'border-outline-variant/40 focus:border-primary'
            }`}
          />
          {isTitleEmpty && (
            <p className="text-xs text-rose-600 font-medium mt-1 flex items-center gap-1" data-testid="question-title-error">
              <AlertCircle className="w-3.5 h-3.5 shrink-0" />
              <span>Tiêu đề câu hỏi không được để trống hoặc chỉ chứa khoảng trắng.</span>
            </p>
          )}
          {isTitleTooLong && (
            <p className="text-xs text-rose-600 font-medium mt-1 flex items-center gap-1" data-testid="question-title-error">
              <AlertCircle className="w-3.5 h-3.5 shrink-0" />
              <span>Tiêu đề câu hỏi không được vượt quá 255 ký tự.</span>
            </p>
          )}
        </div>

        {/* Over-allocation warning banner for question */}
        {isTcOverAllocated && (
          <div
            data-testid="question-tc-over-allocated-banner"
            className="p-2.5 rounded-lg bg-rose-50 border border-rose-200 text-xs text-rose-800 flex items-center gap-2"
          >
            <AlertTriangle className="w-4 h-4 text-rose-600 shrink-0" />
            <span>
              <strong>Cảnh báo phân bổ điểm:</strong> Tổng điểm test case ({totalTestCaseScore.toFixed(2)}đ) vượt quá điểm tối đa câu hỏi ({question.maxScore.toFixed(2)}đ). Vui lòng điều chỉnh lại điểm test case hoặc tăng điểm câu hỏi.
            </span>
          </div>
        )}

        {/* Section Tabs */}
        <div className="flex items-center gap-1 border-b border-outline-variant/30 -mb-4 pt-1">
          <button
            type="button"
            onClick={() => setActiveTab('statement')}
            className={`px-4 py-2 text-xs font-semibold border-b-2 flex items-center gap-2 transition-all cursor-pointer ${
              activeTab === 'statement'
                ? 'border-primary text-primary bg-primary/5 rounded-t-lg'
                : 'border-transparent text-outline hover:text-on-surface'
            }`}
          >
            <BookOpen className="w-3.5 h-3.5" />
            <span>Mô tả đề bài</span>
          </button>
          <button
            type="button"
            onClick={() => setActiveTab('specs')}
            className={`px-4 py-2 text-xs font-semibold border-b-2 flex items-center gap-2 transition-all cursor-pointer ${
              activeTab === 'specs'
                ? 'border-primary text-primary bg-primary/5 rounded-t-lg'
                : 'border-transparent text-outline hover:text-on-surface'
            }`}
          >
            <Sliders className="w-3.5 h-3.5" />
            <span>Đặc tả I/O & Ràng buộc</span>
          </button>
          <button
            type="button"
            onClick={() => setActiveTab('testcases')}
            className={`px-4 py-2 text-xs font-semibold border-b-2 flex items-center gap-2 transition-all cursor-pointer ${
              activeTab === 'testcases'
                ? 'border-primary text-primary bg-primary/5 rounded-t-lg'
                : isTcOverAllocated
                ? 'border-transparent text-rose-600 hover:text-rose-700'
                : 'border-transparent text-outline hover:text-on-surface'
            }`}
          >
            <Terminal className="w-3.5 h-3.5" />
            <span>Bộ Test Cases ({question.testCases.length})</span>
            <span
              className={`text-[10px] px-1.5 py-0.5 rounded font-mono ${
                isTcOverAllocated
                  ? 'bg-rose-100 text-rose-800 font-bold'
                  : 'bg-surface-container text-outline'
              }`}
            >
              {totalTestCaseScore.toFixed(2)}/{question.maxScore.toFixed(2)}đ
            </span>
          </button>
        </div>
      </div>

      {/* Editor Content Body */}
      <div className="p-4 sm:p-6 space-y-6">
        {activeTab === 'statement' && (
          <div className="space-y-4">
            <div>
              <div className="flex items-center justify-between mb-1.5">
                <label htmlFor="problem-statement-textarea" className="text-xs font-bold text-on-surface flex items-center gap-1.5">
                  <FileText className="w-4 h-4 text-primary" />
                  <span>Nội dung yêu cầu đề bài (Problem Statement) *</span>
                </label>
                <span className="text-[11px] text-outline">Hỗ trợ văn bản thuần hoặc Markdown</span>
              </div>
              <textarea
                id="problem-statement-textarea"
                data-testid="problem-statement-textarea"
                rows={10}
                value={question.problemStatement}
                onChange={(e) => onUpdateQuestion({ problemStatement: e.target.value })}
                placeholder="Mô tả chi tiết bài toán, bối cảnh thực tế và yêu cầu lập trình..."
                className={`w-full p-3.5 bg-surface-container-low/50 border rounded-xl text-xs sm:text-sm text-on-surface font-body leading-relaxed focus:outline-none transition-all font-mono ${
                  isStatementEmpty
                    ? 'border-amber-400 focus:border-amber-500'
                    : 'border-outline-variant/40 focus:border-primary'
                }`}
              />
              {isStatementEmpty && (
                <p className="text-xs text-amber-600 font-medium mt-1 flex items-center gap-1" data-testid="problem-statement-warning">
                  <AlertCircle className="w-3.5 h-3.5 shrink-0 text-amber-600" />
                  <span>Nội dung yêu cầu đề bài là bắt buộc khi xuất bản bài thi. Vui lòng nhập mô tả chi tiết bài toán.</span>
                </p>
              )}
            </div>
          </div>
        )}

        {activeTab === 'specs' && (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-bold text-on-surface mb-1">
                Quy cách Đầu vào (Input Specification)
              </label>
              <textarea
                rows={4}
                value={question.inputSpecification || ''}
                onChange={(e) => onUpdateQuestion({ inputSpecification: e.target.value })}
                placeholder="Dòng 1 chứa số nguyên n. Dòng 2 chứa n số nguyên cách nhau..."
                className="w-full p-2.5 bg-surface-container-low/50 border border-outline-variant/40 rounded-xl text-xs text-on-surface focus:outline-none focus:border-primary"
              />
            </div>

            <div>
              <label className="block text-xs font-bold text-on-surface mb-1">
                Quy cách Đầu ra (Output Specification)
              </label>
              <textarea
                rows={4}
                value={question.outputSpecification || ''}
                onChange={(e) => onUpdateQuestion({ outputSpecification: e.target.value })}
                placeholder="In ra giá trị tìm được trên một dòng duy nhất..."
                className="w-full p-2.5 bg-surface-container-low/50 border border-outline-variant/40 rounded-xl text-xs text-on-surface focus:outline-none focus:border-primary"
              />
            </div>

            <div>
              <label className="block text-xs font-bold text-on-surface mb-1">
                Ràng buộc & Giới hạn (Constraints)
              </label>
              <textarea
                rows={3}
                value={question.constraints || ''}
                onChange={(e) => onUpdateQuestion({ constraints: e.target.value })}
                placeholder="1 <= n <= 10^5, thời gian chạy <= 1.0s, bộ nhớ <= 256MB"
                className="w-full p-2.5 bg-surface-container-low/50 border border-outline-variant/40 rounded-xl text-xs text-on-surface focus:outline-none focus:border-primary font-mono"
              />
            </div>

            <div className="space-y-3">
              <div>
                <label className="block text-xs font-bold text-on-surface mb-1">
                  Ví dụ Đầu vào (Sample Input)
                </label>
                <textarea
                  rows={2}
                  value={question.sampleInput || ''}
                  onChange={(e) => onUpdateQuestion({ sampleInput: e.target.value })}
                  placeholder="5\n3 1 4 1 5"
                  className="w-full p-2.5 bg-surface-container-low/50 border border-outline-variant/40 rounded-xl text-xs text-on-surface focus:outline-none focus:border-primary font-mono"
                />
              </div>
              <div>
                <label className="block text-xs font-bold text-on-surface mb-1">
                  Ví dụ Đầu ra (Sample Output)
                </label>
                <textarea
                  rows={2}
                  value={question.sampleOutput || ''}
                  onChange={(e) => onUpdateQuestion({ sampleOutput: e.target.value })}
                  placeholder="4"
                  className="w-full p-2.5 bg-surface-container-low/50 border border-outline-variant/40 rounded-xl text-xs text-on-surface focus:outline-none focus:border-primary font-mono"
                />
              </div>
            </div>
          </div>
        )}

        {activeTab === 'testcases' && (
          <TestCaseManager
            questionId={question.id}
            questionTitle={question.title || `Câu hỏi #${questionIndex + 1}`}
            questionMaxScore={question.maxScore}
            testCases={question.testCases}
            onAddTestCase={onAddTestCase}
            onUpdateTestCase={onUpdateTestCase}
            onDeleteTestCase={onDeleteTestCase}
          />
        )}
      </div>
    </div>
  );
};
