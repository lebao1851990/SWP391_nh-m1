'use client';

import React, { useState } from 'react';
import { BuilderTestCase } from '../../types/builder.types';
import { 
  Plus, 
  Trash2, 
  Edit3, 
  Eye, 
  EyeOff, 
  AlertTriangle, 
  Code2, 
  X,
  FileCheck2,
  CheckCircle2,
  AlertCircle,
  Coins,
  ShieldAlert,
  ShieldCheck
} from 'lucide-react';

interface TestCaseManagerProps {
  questionId: string;
  questionTitle: string;
  questionMaxScore: number;
  testCases: BuilderTestCase[];
  onAddTestCase: (newTestCase: Omit<BuilderTestCase, 'id' | 'displayOrder'>) => void;
  onUpdateTestCase: (updatedTestCase: BuilderTestCase) => void;
  onDeleteTestCase: (testCaseId: string) => void;
}

export const TestCaseManager: React.FC<TestCaseManagerProps> = ({
  questionId,
  questionTitle,
  questionMaxScore,
  testCases,
  onAddTestCase,
  onUpdateTestCase,
  onDeleteTestCase,
}) => {
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingTc, setEditingTc] = useState<BuilderTestCase | null>(null);

  // Form State
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [inputData, setInputData] = useState('');
  const [expectedOutput, setExpectedOutput] = useState('');
  const [pointsInput, setPointsInput] = useState("1.00");
  const [isHidden, setIsHidden] = useState(false);
  const [allowEmptyOutput, setAllowEmptyOutput] = useState(false);
  const [formError, setFormError] = useState('');
  const [fieldErrors, setFieldErrors] = useState<{
    name?: string;
    points?: string;
    inputData?: string;
    expectedOutput?: string;
  }>({});

  // Delete Confirmation
  const [tcToDelete, setTcToDelete] = useState<BuilderTestCase | null>(null);

  // Score Calculations
  const totalAllocatedScore = Number(
    testCases.reduce((sum, tc) => sum + (Number(tc.points) || 0), 0).toFixed(2)
  );
  const remainingScore = Number((questionMaxScore - totalAllocatedScore).toFixed(2));
  const isExactMatch = Math.abs(totalAllocatedScore - questionMaxScore) < 0.005;
  const isOverAllocated = totalAllocatedScore > questionMaxScore + 0.005;

  // Content Validation Calculations
  const invalidContentTcs = React.useMemo(() => {
    return testCases.filter((tc) => {
      const isNameBlank = !tc.name || !tc.name.trim();
      const isExplicitEmptyAllowed = Boolean(tc.allowEmptyOutput);
      const isOutputBlank = (!tc.expectedOutput || !tc.expectedOutput.trim()) && !isExplicitEmptyAllowed;
      const isInputWhitespaceOnly = Boolean(tc.inputData && tc.inputData.length > 0 && !tc.inputData.trim());
      return isNameBlank || isOutputBlank || isInputWhitespaceOnly;
    });
  }, [testCases]);

  const isContentFullyValid = testCases.length > 0 && invalidContentTcs.length === 0;

  const openCreateModal = () => {
    setEditingTc(null);
    setName(`Test Case #${testCases.length + 1}`);
    setDescription('');
    setInputData('');
    setExpectedOutput('');
    setAllowEmptyOutput(false);
    // Default points to remaining score if positive, else a sensible default
    const defaultPts = remainingScore > 0 ? remainingScore : Math.min(1.0, questionMaxScore);
    setPointsInput(defaultPts.toFixed(2));
    setIsHidden(false);
    setFormError('');
    setFieldErrors({});
    setIsModalOpen(true);
  };

  const openEditModal = (tc: BuilderTestCase) => {
    setEditingTc(tc);
    setName(tc.name);
    setDescription(tc.description || '');
    setInputData(tc.inputData);
    setExpectedOutput(tc.expectedOutput);
    setPointsInput(Number(tc.points).toFixed(2));
    setIsHidden(tc.isHidden);
    setAllowEmptyOutput(Boolean(tc.allowEmptyOutput));
    setFormError('');
    setFieldErrors({});
    setIsModalOpen(true);
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const errors: { name?: string; points?: string; inputData?: string; expectedOutput?: string } = {};

    // 1. Name validation
    if (!name.trim()) {
      errors.name = 'Vui lòng nhập tên test case (không được để trống hoặc chỉ có khoảng trắng).';
    }

    // 2. Points numeric input validation
    const trimmedPoints = pointsInput.trim();
    let numPoints = NaN;

    if (!trimmedPoints) {
      errors.points = 'Vui lòng nhập điểm cho test case.';
    } else if (/[eE]/.test(trimmedPoints)) {
      errors.points = 'Không chấp nhận định dạng ký hiệu khoa học (scientific notation). Vui lòng nhập số thập phân chuẩn.';
    } else if (trimmedPoints.startsWith('-')) {
      errors.points = 'Điểm test case không được là số âm. Vui lòng nhập số lớn hơn 0.';
    } else if (!/^\d+(\.\d+)?$/.test(trimmedPoints)) {
      errors.points = 'Định dạng điểm không hợp lệ. Chỉ chấp nhận các chữ số từ 0-9 và dấu chấm thập phân.';
    } else if (/^\d+\.\d{3,}$/.test(trimmedPoints)) {
      errors.points = 'Điểm số chỉ được có tối đa 2 chữ số thập phân (chuẩn DECIMAL(5,2)).';
    } else if (trimmedPoints.split('.')[0].length > 4) {
      errors.points = `Điểm test case (${trimmedPoints}đ) vượt quá giới hạn tối đa cho phép.`;
    } else {
      numPoints = Number(trimmedPoints);
      if (isNaN(numPoints) || !isFinite(numPoints)) {
        errors.points = 'Điểm số không hợp lệ.';
      } else if (numPoints <= 0) {
        errors.points = 'Điểm test case phải lớn hơn 0 (tối thiểu 0.01đ).';
      } else if (numPoints > questionMaxScore) {
        errors.points = `Điểm test case (${numPoints.toFixed(2)}đ) không được vượt quá điểm tối đa của câu hỏi (${questionMaxScore.toFixed(2)}đ).`;
      } else {
        const currentSumWithoutThis = testCases
          .filter((t) => !editingTc || t.id !== editingTc.id)
          .reduce((sum, t) => sum + (Number(t.points) || 0), 0);
        const newTotal = Number((currentSumWithoutThis + numPoints).toFixed(2));

        if (newTotal > questionMaxScore + 0.005) {
          const allowedRemaining = Math.max(0, Number((questionMaxScore - currentSumWithoutThis).toFixed(2)));
          errors.points = `Tổng điểm test cases (${newTotal.toFixed(2)}đ) vượt quá điểm tối đa của câu hỏi (${questionMaxScore.toFixed(2)}đ). Điểm tối đa còn có thể gán là ${allowedRemaining.toFixed(2)}đ.`;
        }
      }
    }

    // 3. Input Data validation: reject whitespace-only
    if (inputData && inputData.length > 0 && !inputData.trim()) {
      errors.inputData = 'Dữ liệu đầu vào (Input Data) không được chỉ chứa ký tự khoảng trắng hoặc dòng trống.';
    }

    // 4. Expected Output validation: cannot be empty or whitespace unless explicitly allowed
    if (!allowEmptyOutput && (!expectedOutput || !expectedOutput.trim())) {
      errors.expectedOutput = 'Kết quả mong đợi (Expected Output) không được để trống đối với bài toán yêu cầu stdout. Đánh dấu bên dưới nếu bài toán thực sự không in kết quả.';
    } else if (allowEmptyOutput && expectedOutput && expectedOutput.length > 0 && !expectedOutput.trim()) {
      errors.expectedOutput = 'Không được chứa khoảng trắng khi đã kích hoạt chế độ không có kết quả đầu ra.';
    }

    if (Object.keys(errors).length > 0) {
      setFieldErrors(errors);
      setFormError('Vui lòng kiểm tra và sửa các thông tin chưa hợp lệ bên dưới.');
      return;
    }

    setFieldErrors({});
    setFormError('');

    const finalDescription = description.trim() || undefined;

    if (editingTc) {
      onUpdateTestCase({
        ...editingTc,
        name: name.trim(),
        description: finalDescription,
        inputData: inputData,
        expectedOutput: allowEmptyOutput && !expectedOutput.trim() ? '' : expectedOutput,
        points: numPoints,
        isHidden,
        allowEmptyOutput,
      });
    } else {
      onAddTestCase({
        name: name.trim(),
        description: finalDescription,
        inputData: inputData,
        expectedOutput: allowEmptyOutput && !expectedOutput.trim() ? '' : expectedOutput,
        points: numPoints,
        isHidden,
        allowEmptyOutput,
      });
    }

    setIsModalOpen(false);
  };

  const confirmDelete = () => {
    if (tcToDelete) {
      onDeleteTestCase(tcToDelete.id);
      setTcToDelete(null);
    }
  };

  // Remaining points available for modal context
  const currentSumWithoutModal = testCases
    .filter((t) => !editingTc || t.id !== editingTc.id)
    .reduce((sum, t) => sum + (Number(t.points) || 0), 0);
  const maxAvailableForModal = Math.max(0, Number((questionMaxScore - currentSumWithoutModal).toFixed(2)));

  return (
    <div data-question-id={questionId} className="space-y-4">
      {/* Header & Allocation Summary Bar */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-3 border-b border-outline-variant/30">
        <div>
          <h3 className="text-sm font-bold text-on-surface flex items-center gap-2">
            <FileCheck2 className="w-4 h-4 text-primary" />
            <span>Bộ Test Cases ({testCases.length})</span>
          </h3>
          <p className="text-xs text-outline mt-0.5">
            Test cases riêng biệt của câu hỏi: <strong className="text-on-surface">{questionTitle}</strong>
          </p>
        </div>
        <button
          type="button"
          onClick={openCreateModal}
          className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-primary text-on-primary rounded-lg text-xs font-semibold hover:bg-primary/90 transition-colors shadow-xs cursor-pointer self-start sm:self-auto"
        >
          <Plus className="w-3.5 h-3.5" />
          <span>Thêm Test Case</span>
        </button>
      </div>

      {/* Score Allocation Summary Card */}
      <div className="p-3.5 rounded-xl border border-outline-variant/30 bg-surface-container-low/40 flex flex-col md:flex-row md:items-center justify-between gap-3">
        <div className="flex items-center gap-4 flex-wrap">
          <div className="flex items-center gap-1.5">
            <Coins className="w-4 h-4 text-primary" />
            <span className="text-xs text-outline font-medium">Điểm câu hỏi:</span>
            <span className="font-mono text-xs font-bold text-on-surface">
              {questionMaxScore.toFixed(2)}đ
            </span>
          </div>
          <div className="flex items-center gap-1.5">
            <span className="text-xs text-outline font-medium">Đã phân bổ:</span>
            <span
              data-testid="tc-total-allocated"
              className={`font-mono text-xs font-bold ${
                isOverAllocated
                  ? 'text-rose-600'
                  : isExactMatch
                  ? 'text-emerald-600'
                  : 'text-amber-600'
              }`}
            >
              {totalAllocatedScore.toFixed(2)}đ
            </span>
          </div>
          <div className="flex items-center gap-1.5">
            <span className="text-xs text-outline font-medium">Còn lại:</span>
            <span
              className={`font-mono text-xs font-bold ${
                remainingScore < 0 ? 'text-rose-600' : 'text-on-surface-variant'
              }`}
            >
              {remainingScore >= 0 ? `${remainingScore.toFixed(2)}đ` : `-${Math.abs(remainingScore).toFixed(2)}đ`}
            </span>
          </div>
        </div>

        {/* Two Independent Status Indicators: Score Allocation vs Content Validation */}
        <div className="flex items-center gap-2 flex-wrap">
          {/* Score Allocation Status */}
          {isExactMatch ? (
            <span data-testid="tc-score-status-pill" className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200">
              <CheckCircle2 className="w-3.5 h-3.5" />
              <span>Điểm: Hợp lệ (100%)</span>
            </span>
          ) : isOverAllocated ? (
            <span data-testid="tc-score-status-pill" className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-semibold bg-rose-50 text-rose-700 border border-rose-200">
              <AlertCircle className="w-3.5 h-3.5" />
              <span>Điểm: Vượt quá {(totalAllocatedScore - questionMaxScore).toFixed(2)}đ</span>
            </span>
          ) : (
            <span data-testid="tc-score-status-pill" className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-semibold bg-amber-50 text-amber-700 border border-amber-200">
              <AlertTriangle className="w-3.5 h-3.5" />
              <span>Điểm: Còn thiếu ({remainingScore.toFixed(2)}đ)</span>
            </span>
          )}

          {/* Content Validation Status */}
          {testCases.length === 0 ? (
            <span data-testid="tc-content-status-pill" className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-semibold bg-surface-container text-outline border border-outline-variant/40">
              <ShieldAlert className="w-3.5 h-3.5 text-outline" />
              <span>Nội dung: Chưa có test case</span>
            </span>
          ) : invalidContentTcs.length > 0 ? (
            <span data-testid="tc-content-status-pill" className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-semibold bg-rose-50 text-rose-700 border border-rose-200 animate-pulse">
              <ShieldAlert className="w-3.5 h-3.5 text-rose-600" />
              <span>Nội dung: {invalidContentTcs.length} test case lỗi I/O</span>
            </span>
          ) : (
            <span data-testid="tc-content-status-pill" className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200">
              <ShieldCheck className="w-3.5 h-3.5 text-emerald-600" />
              <span>Nội dung: Đạt chuẩn I/O</span>
            </span>
          )}
        </div>
      </div>

      {/* Warning Alert Banner when Over-allocated or Content Invalid */}
      {isOverAllocated && (
        <div
          data-testid="tc-over-allocated-banner"
          className="p-3 rounded-xl bg-rose-50 border border-rose-200 flex items-start gap-2.5 text-xs text-rose-800"
        >
          <AlertCircle className="w-4 h-4 text-rose-600 shrink-0 mt-0.5" />
          <div>
            <p className="font-bold">Cảnh báo phân bổ điểm: Tổng điểm test case vượt quá điểm câu hỏi!</p>
            <p className="text-[11px] text-rose-700 mt-0.5 leading-relaxed">
              Tổng điểm các test case hiện tại là <strong>{totalAllocatedScore.toFixed(2)}đ</strong>, lớn hơn mức tối đa cho phép của câu hỏi là <strong>{questionMaxScore.toFixed(2)}đ</strong>. Vui lòng giảm điểm test case trước khi xuất bản đề thi.
            </p>
          </div>
        </div>
      )}

      {invalidContentTcs.length > 0 && (
        <div
          data-testid="tc-invalid-content-banner"
          className="p-3 rounded-xl bg-amber-50 border border-amber-200 flex items-start gap-2.5 text-xs text-amber-800"
        >
          <AlertTriangle className="w-4 h-4 text-amber-600 shrink-0 mt-0.5" />
          <div>
            <p className="font-bold">Cảnh báo nội dung: Có {invalidContentTcs.length} test case chưa hoàn thiện!</p>
            <p className="text-[11px] text-amber-700 mt-0.5 leading-relaxed">
              Hệ thống phát hiện test case chưa có kết quả mong đợi (Expected Output) hoặc dữ liệu đầu vào chỉ chứa khoảng trắng. Hãy bấm nút chỉnh sửa để bổ sung trước khi xuất bản đề thi.
            </p>
          </div>
        </div>
      )}

      {/* Test Cases Table / List */}
      {testCases.length === 0 ? (
        <div className="text-center py-8 px-4 border border-dashed border-outline-variant/60 rounded-xl bg-surface-container-low/20">
          <Code2 className="w-8 h-8 text-outline mx-auto mb-2 opacity-50" />
          <p className="text-xs font-medium text-on-surface">Chưa có test case nào cho câu hỏi này</p>
          <p className="text-[11px] text-outline mt-1 mb-3">
            Mỗi câu hỏi cần tối thiểu 1 test case công khai để sinh viên kiểm thử.
          </p>
          <button
            type="button"
            onClick={openCreateModal}
            className="px-3 py-1.5 bg-primary text-on-primary rounded-lg text-xs font-medium hover:bg-primary/90 transition-colors inline-flex items-center gap-1.5 cursor-pointer"
          >
            <Plus className="w-3.5 h-3.5" />
            <span>Tạo test case đầu tiên</span>
          </button>
        </div>
      ) : (
        <div className="space-y-3">
          {testCases.map((tc, index) => (
            <div
              key={tc.id}
              className="p-3.5 rounded-xl border border-outline-variant/30 bg-surface-container-lowest hover:border-outline-variant transition-all shadow-xs space-y-2.5"
            >
              <div className="flex items-center justify-between gap-2">
                <div className="flex items-center gap-2 min-w-0">
                  <span className="text-xs font-bold text-on-surface">#{index + 1}</span>
                  <span className="text-xs font-semibold text-on-surface truncate">{tc.name}</span>
                  {tc.isHidden ? (
                    <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-semibold bg-purple-50 text-purple-700 border border-purple-200">
                      <EyeOff className="w-2.5 h-2.5" />
                      <span>Ẩn (Chấm điểm bí mật)</span>
                    </span>
                  ) : (
                    <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200">
                      <Eye className="w-2.5 h-2.5" />
                      <span>Công khai (Sample)</span>
                    </span>
                  )}
                  {(!tc.expectedOutput || !tc.expectedOutput.trim()) && !tc.allowEmptyOutput && (
                    <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-bold bg-rose-50 text-rose-700 border border-rose-200">
                      <AlertCircle className="w-2.5 h-2.5 text-rose-600" />
                      <span>Thiếu Expected Output</span>
                    </span>
                  )}
                  {Boolean(tc.inputData && tc.inputData.length > 0 && !tc.inputData.trim()) && (
                    <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-bold bg-amber-50 text-amber-700 border border-amber-200">
                      <AlertTriangle className="w-2.5 h-2.5 text-amber-600" />
                      <span>Input chỉ có khoảng trắng</span>
                    </span>
                  )}
                </div>

                <div className="flex items-center gap-2 shrink-0">
                  <span className="text-xs font-bold text-primary px-2 py-0.5 rounded bg-primary/5 border border-primary/20 font-mono">
                    {Number(tc.points).toFixed(2)}đ
                  </span>
                  <button
                    type="button"
                    onClick={() => openEditModal(tc)}
                    title="Chỉnh sửa test case"
                    className="p-1.5 text-outline hover:text-on-surface hover:bg-surface-container rounded-lg transition-colors cursor-pointer"
                  >
                    <Edit3 className="w-3.5 h-3.5" />
                  </button>
                  <button
                    type="button"
                    onClick={() => setTcToDelete(tc)}
                    title="Xóa test case"
                    className="p-1.5 text-rose-500 hover:text-rose-700 hover:bg-rose-50 rounded-lg transition-colors cursor-pointer"
                  >
                    <Trash2 className="w-3.5 h-3.5" />
                  </button>
                </div>
              </div>

              {/* Input / Output snippet preview */}
              <div className="grid grid-cols-1 md:grid-cols-2 gap-2 text-xs">
                <div className="p-2 rounded-lg bg-surface-container-low/70 border border-outline-variant/30">
                  <div className="text-[10px] font-bold text-outline uppercase tracking-wider mb-1">
                    Đầu vào (Input Data)
                  </div>
                  <pre className="font-mono text-[11px] text-on-surface whitespace-pre-wrap break-all max-h-20 overflow-y-auto">
                    {tc.inputData || <span className="text-outline italic">(Không có input - stdin rỗng)</span>}
                  </pre>
                </div>
                <div className="p-2 rounded-lg bg-surface-container-low/70 border border-outline-variant/30">
                  <div className="text-[10px] font-bold text-outline uppercase tracking-wider mb-1">
                    Kết quả mong đợi (Expected Output)
                  </div>
                  <pre className="font-mono text-[11px] text-on-surface whitespace-pre-wrap break-all max-h-20 overflow-y-auto">
                    {tc.expectedOutput ? (
                      tc.expectedOutput
                    ) : tc.allowEmptyOutput ? (
                      <span className="text-purple-600 font-semibold italic">(Không có stdout - Đã xác nhận)</span>
                    ) : (
                      <span className="text-rose-600 font-bold italic">(Chưa nhập - Lỗi)</span>
                    )}
                  </pre>
                </div>
              </div>

              {tc.description && (
                <p className="text-[11px] text-outline italic">
                  Ghi chú: {tc.description}
                </p>
              )}
            </div>
          ))}
        </div>
      )}

      {/* Create / Edit Test Case Modal */}
      {isModalOpen && (
        <div className="fixed inset-0 z-[70] flex items-center justify-center p-4 bg-black/60 backdrop-blur-xs animate-in fade-in">
          <div
            role="dialog"
            aria-modal="true"
            aria-labelledby="tc-modal-title"
            className="bg-surface-container-lowest max-w-lg w-full rounded-2xl p-5 shadow-2xl border border-outline-variant/40 space-y-4 max-h-[90vh] overflow-y-auto"
          >
            <div className="flex items-center justify-between pb-3 border-b border-outline-variant/30">
              <h3 id="tc-modal-title" className="text-sm font-bold text-on-surface">
                {editingTc ? 'Chỉnh sửa Test Case' : 'Thêm Test Case Mới'}
              </h3>
              <button
                type="button"
                onClick={() => setIsModalOpen(false)}
                className="text-outline hover:text-on-surface p-1 rounded-lg cursor-pointer"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            {formError && (
              <div
                data-testid="tc-form-error"
                className="p-2.5 rounded-lg bg-rose-50 border border-rose-200 text-rose-700 text-xs flex items-start gap-1.5"
              >
                <AlertCircle className="w-4 h-4 text-rose-600 shrink-0 mt-0.5" />
                <span>{formError}</span>
              </div>
            )}

            <form noValidate onSubmit={handleSubmit} className="space-y-3">
              <div>
                <label className="block text-xs font-semibold text-on-surface mb-1">
                  Tên Test Case *
                </label>
                <input
                  data-testid="tc-name-input"
                  type="text"
                  value={name}
                  onChange={(e) => {
                    setName(e.target.value);
                    if (fieldErrors.name) setFieldErrors(prev => ({ ...prev, name: undefined }));
                  }}
                  placeholder="Ví dụ: Test Case #1 (Biên mảng)"
                  className={`w-full px-3 py-1.5 bg-surface-container-low border rounded-lg text-xs text-on-surface focus:outline-none focus:border-primary transition-colors ${
                    fieldErrors.name ? 'border-rose-500 ring-1 ring-rose-500/50 bg-rose-50/20' : 'border-outline-variant/40'
                  }`}
                  required
                />
                {fieldErrors.name && (
                  <p className="text-[11px] text-rose-600 mt-1 flex items-center gap-1">
                    <AlertCircle className="w-3 h-3 shrink-0" />
                    <span>{fieldErrors.name}</span>
                  </p>
                )}
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-on-surface mb-1">
                    Điểm số * (tối đa {questionMaxScore.toFixed(2)}đ)
                  </label>
                  <input
                    data-testid="tc-points-input"
                    type="text"
                    inputMode="decimal"
                    value={pointsInput}
                    onChange={(e) => {
                      const val = e.target.value;
                      setPointsInput(val);
                      if (fieldErrors.points) setFieldErrors(prev => ({ ...prev, points: undefined }));
                    }}
                    placeholder="0.00"
                    className={`w-full px-3 py-1.5 bg-surface-container-low border rounded-lg text-xs text-on-surface focus:outline-none focus:border-primary font-mono transition-colors ${
                      fieldErrors.points ? 'border-rose-500 ring-1 ring-rose-500/50 bg-rose-50/20' : 'border-outline-variant/40'
                    }`}
                    required
                  />
                  {fieldErrors.points ? (
                    <p data-testid="tc-points-error" className="text-[11px] text-rose-600 mt-1 flex items-center gap-1">
                      <AlertCircle className="w-3 h-3 shrink-0" />
                      <span>{fieldErrors.points}</span>
                    </p>
                  ) : (
                    <span className="text-[10px] text-outline mt-0.5 block">
                      Khả dụng: <strong>{maxAvailableForModal.toFixed(2)}đ</strong> / Tối đa: <strong>{questionMaxScore.toFixed(2)}đ</strong>
                    </span>
                  )}
                </div>
                <div>
                  <label className="block text-xs font-semibold text-on-surface mb-1">
                    Chế độ hiển thị
                  </label>
                  <label className="flex items-center gap-2 mt-2 cursor-pointer">
                    <input
                      type="checkbox"
                      checked={isHidden}
                      onChange={(e) => setIsHidden(e.target.checked)}
                      className="rounded border-outline-variant text-primary focus:ring-primary h-4 w-4"
                    />
                    <span className="text-xs text-on-surface font-medium">
                      Ẩn test case (Hidden)
                    </span>
                  </label>
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-on-surface mb-1">
                  Đầu vào (Input Data / Stdin)
                </label>
                <textarea
                  rows={3}
                  value={inputData}
                  onChange={(e) => {
                    setInputData(e.target.value);
                    if (fieldErrors.inputData) setFieldErrors(prev => ({ ...prev, inputData: undefined }));
                  }}
                  placeholder="Nhập dữ liệu đầu vào mô phỏng bàn phím/stdin (để trống nếu bài toán không cần stdin)..."
                  className={`w-full px-3 py-1.5 font-mono text-xs bg-surface-container-low border rounded-lg text-on-surface focus:outline-none focus:border-primary transition-colors ${
                    fieldErrors.inputData ? 'border-rose-500 ring-1 ring-rose-500/50 bg-rose-50/20' : 'border-outline-variant/40'
                  }`}
                />
                {fieldErrors.inputData && (
                  <p className="text-[11px] text-rose-600 mt-1 flex items-center gap-1">
                    <AlertCircle className="w-3 h-3 shrink-0" />
                    <span>{fieldErrors.inputData}</span>
                  </p>
                )}
              </div>

              <div>
                <div className="flex items-center justify-between mb-1">
                  <label className="text-xs font-semibold text-on-surface">
                    Kết quả mong đợi (Expected Output / Stdout) *
                  </label>
                  <label className="flex items-center gap-1.5 cursor-pointer text-[11px] text-outline hover:text-on-surface">
                    <input
                      type="checkbox"
                      checked={allowEmptyOutput}
                      onChange={(e) => {
                        setAllowEmptyOutput(e.target.checked);
                        if (e.target.checked) {
                          setFieldErrors(prev => ({ ...prev, expectedOutput: undefined }));
                        }
                      }}
                      className="rounded border-outline-variant text-primary focus:ring-primary h-3.5 w-3.5"
                    />
                    <span>Xác nhận không có stdout</span>
                  </label>
                </div>
                <textarea
                  rows={3}
                  value={expectedOutput}
                  disabled={allowEmptyOutput}
                  onChange={(e) => {
                    setExpectedOutput(e.target.value);
                    if (fieldErrors.expectedOutput) setFieldErrors(prev => ({ ...prev, expectedOutput: undefined }));
                  }}
                  placeholder={allowEmptyOutput ? "Bài toán đã được xác nhận không yêu cầu in ra màn hình (stdout rỗng)..." : "Nhập kết quả in ra màn hình chính xác..."}
                  className={`w-full px-3 py-1.5 font-mono text-xs border rounded-lg text-on-surface focus:outline-none focus:border-primary transition-colors ${
                    allowEmptyOutput ? 'bg-surface-container opacity-60 cursor-not-allowed border-outline-variant/20' : fieldErrors.expectedOutput ? 'bg-rose-50/20 border-rose-500 ring-1 ring-rose-500/50' : 'bg-surface-container-low border-outline-variant/40'
                  }`}
                  required={!allowEmptyOutput}
                />
                {fieldErrors.expectedOutput && (
                  <p className="text-[11px] text-rose-600 mt-1 flex items-center gap-1">
                    <AlertCircle className="w-3 h-3 shrink-0" />
                    <span>{fieldErrors.expectedOutput}</span>
                  </p>
                )}
              </div>

              <div>
                <label className="block text-xs font-semibold text-on-surface mb-1">
                  Mô tả / Mục đích kiểm thử (Tùy chọn)
                </label>
                <input
                  type="text"
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  placeholder="Ví dụ: Kiểm tra trường hợp giá trị n = 0"
                  className="w-full px-3 py-1.5 bg-surface-container-low border border-outline-variant/40 rounded-lg text-xs text-on-surface focus:outline-none focus:border-primary"
                />
              </div>

              <div className="flex items-center justify-end gap-2 pt-3 border-t border-outline-variant/30">
                <button
                  type="button"
                  onClick={() => setIsModalOpen(false)}
                  className="px-3 py-1.5 rounded-lg border border-outline-variant/40 text-xs font-medium text-on-surface hover:bg-surface-container cursor-pointer"
                >
                  Hủy
                </button>
                <button
                  data-testid="tc-submit-button"
                  type="submit"
                  className="px-4 py-1.5 rounded-lg bg-primary text-on-primary text-xs font-semibold hover:bg-primary/90 shadow-xs cursor-pointer"
                >
                  {editingTc ? 'Cập nhật' : 'Thêm test case'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Delete Confirmation Modal */}
      {tcToDelete && (
        <div className="fixed inset-0 z-[70] flex items-center justify-center p-4 bg-black/60 backdrop-blur-xs animate-in fade-in">
          <div
            role="alertdialog"
            aria-labelledby="delete-tc-title"
            className="bg-surface-container-lowest max-w-sm w-full rounded-2xl p-5 shadow-2xl border border-outline-variant/40 space-y-4"
          >
            <div className="flex items-start gap-3">
              <div className="p-2.5 rounded-full bg-rose-100 text-rose-600 shrink-0">
                <AlertTriangle className="w-5 h-5" />
              </div>
              <div className="space-y-1">
                <h3 id="delete-tc-title" className="text-sm font-bold text-on-surface">
                  Xóa Test Case?
                </h3>
                <p className="text-xs text-on-surface-variant">
                  Bạn có chắc muốn xóa &quot;{tcToDelete.name}&quot; khỏi câu hỏi này?
                </p>
              </div>
            </div>
            <div className="flex items-center justify-end gap-2 pt-2 border-t border-outline-variant/30">
              <button
                type="button"
                onClick={() => setTcToDelete(null)}
                className="px-3 py-1.5 rounded-lg border border-outline-variant/40 text-xs font-medium text-on-surface hover:bg-surface-container cursor-pointer"
              >
                Hủy
              </button>
              <button
                type="button"
                onClick={confirmDelete}
                className="px-3.5 py-1.5 rounded-lg bg-rose-600 hover:bg-rose-700 text-white text-xs font-semibold shadow-xs transition-colors cursor-pointer flex items-center gap-1.5"
              >
                <Trash2 className="w-3.5 h-3.5" />
                <span>Xóa Test Case</span>
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
