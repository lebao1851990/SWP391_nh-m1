'use client';

import React, { useState, useRef, useEffect } from 'react';
import { AxiosError } from 'axios';
import {
  Sparkles,
  X,
  Loader2,
  AlertCircle,
  CheckCircle2,
  Plus,
  Trash2,
  BookOpen,
  FileText,
  UploadCloud,
  FileCode2,
  FileCheck2,
  Scale,
  ListPlus,
  Info,
  RefreshCw,
  ExternalLink,
  Tag,
  Sliders,
  ChevronRight,
  ShieldCheck,
  FileArchive
} from 'lucide-react';
import {
  teacherAiExamApi,
  AiExamGenerateRequest,
  AiDocumentExamGenerateResponse,
  DocumentAnalysisResponse,
  AiRubricItem,
  AiTestCaseDraft,
  SaveAiExamRequest
} from '../api/ai-exam.api';

interface AiExamGeneratorModalProps {
  isOpen: boolean;
  onClose: () => void;
  classes: Array<{ id: string; name: string; code: string }>;
  onExamCreated: () => void;
}

type GeneratorMode = 'DOCUMENT_UPLOAD' | 'MANUAL_TOPIC';
type GenerationPhase = 'IDLE' | 'EXTRACTING' | 'ANALYZING' | 'GENERATING' | 'SUCCESS' | 'ERROR';

export function AiExamGeneratorModal({
  isOpen,
  onClose,
  classes,
  onExamCreated
}: AiExamGeneratorModalProps) {
  const fileInputRef = useRef<HTMLInputElement>(null);

  // Mode Selection
  const [mode, setMode] = useState<GeneratorMode>('DOCUMENT_UPLOAD');

  // Document Upload State
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [isDragging, setIsDragging] = useState(false);

  // Form Inputs
  const [topic, setTopic] = useState('');
  const [difficulty, setDifficulty] = useState<'EASY' | 'MEDIUM' | 'HARD'>('MEDIUM');
  const [programmingLanguage, setProgrammingLanguage] = useState('Java');
  const [additionalRequirements, setAdditionalRequirements] = useState('');
  const [criteriaCount, setCriteriaCount] = useState<number>(4);

  // Agent Progress Phase
  const [phase, setPhase] = useState<GenerationPhase>('IDLE');
  const [progressMessage, setProgressMessage] = useState('');
  const [error, setError] = useState<string | null>(null);

  // Stage 1 Analysis Insight
  const [analysisResult, setAnalysisResult] = useState<DocumentAnalysisResponse | null>(null);

  // Stage 3 Generated Draft
  const [draft, setDraft] = useState<AiDocumentExamGenerateResponse | null>(null);

  // Save Settings
  const [selectedClassId, setSelectedClassId] = useState<string>(classes[0]?.id || '');
  const [examDate, setExamDate] = useState<string>(
    new Date(Date.now() + 86400000 * 2).toISOString().slice(0, 16)
  );
  const [durationMinutes, setDurationMinutes] = useState<number>(90);
  const [isSaving, setIsSaving] = useState(false);

  // Synchronize selectedClassId whenever classes list loads or updates
  useEffect(() => {
    if (classes && classes.length > 0) {
      if (!selectedClassId || !classes.some((c) => c.id === selectedClassId)) {
        setSelectedClassId(classes[0].id);
      }
    }
  }, [classes, selectedClassId]);

  if (!isOpen) return null;

  // File Selection & Drag-and-Drop Handlers
  const handleFileDrop = (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragging(false);
    setError(null);
    const files = e.dataTransfer.files;
    if (files && files.length > 0) {
      validateAndSetFile(files[0]);
    }
  };

  const handleFileInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setError(null);
    if (e.target.files && e.target.files.length > 0) {
      validateAndSetFile(e.target.files[0]);
    }
  };

  const validateAndSetFile = (file: File) => {
    const ext = file.name.split('.').pop()?.toLowerCase();
    if (!ext || !['pdf', 'docx', 'txt'].includes(ext)) {
      setError('Định dạng tệp không được hỗ trợ. Vui lòng tải lên tài liệu PDF, DOCX hoặc TXT.');
      return;
    }
    if (file.size > 15 * 1024 * 1024) {
      setError('Dung lượng tệp vượt quá 15 MB cho phép.');
      return;
    }
    if (file.size === 0) {
      setError('Tệp tải lên bị rỗng (0 bytes). Vui lòng chọn tệp hợp lệ.');
      return;
    }
    setSelectedFile(file);
    setError(null);
  };

  const handleRemoveFile = () => {
    setSelectedFile(null);
    setAnalysisResult(null);
    if (fileInputRef.current) {
      fileInputRef.current.value = '';
    }
  };

  // Run Document Analysis Only
  const handleAnalyzeOnly = async () => {
    if (!selectedFile) {
      setError('Vui lòng chọn hoặc kéo thả tệp tài liệu trước.');
      return;
    }

    setPhase('EXTRACTING');
    setProgressMessage('Đang trích xuất văn bản từ tài liệu...');
    setError(null);

    try {
      setPhase('ANALYZING');
      setProgressMessage('AI Agent đang phân tích chủ đề, thuật toán và cấu trúc kiến thức...');
      const result = await teacherAiExamApi.analyzeDocument(selectedFile);
      setAnalysisResult(result);
      if (result.mainTopic && !topic) {
        setTopic(result.mainTopic);
      }
      setPhase('IDLE');
    } catch (err: unknown) {
      setPhase('ERROR');
      const axiosErr = err as AxiosError<{ message?: string }>;
      setError(axiosErr.response?.data?.message || 'Không thể phân tích tài liệu. Tệp có thể không có lớp văn bản số hoặc bị khóa.');
    }
  };

  // Full Agent Generation Pipeline
  const handleGenerate = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (mode === 'DOCUMENT_UPLOAD') {
      if (!selectedFile) {
        setError('Vui lòng tải lên tài liệu bài giảng hoặc giáo trình (.pdf, .docx, .txt).');
        return;
      }

      setPhase('EXTRACTING');
      setProgressMessage('Giai đoạn 1/4: Đang đọc và trích xuất nội dung văn bản...');

      try {
        setPhase('ANALYZING');
        setProgressMessage('Giai đoạn 2/4: AI Agent phân tích kiến thức trọng tâm & khái niệm...');

        setPhase('GENERATING');
        setProgressMessage('Giai đoạn 3/4: Đang tổng hợp bài toán, test cases và barem điểm 10đ...');

        const result = await teacherAiExamApi.generateFromDocument({
          file: selectedFile,
          difficulty,
          programmingLanguage,
          criteriaCount,
          additionalRequirements: additionalRequirements.trim() || undefined,
        });

        setDraft(result);
        setPhase('SUCCESS');
      } catch (err: unknown) {
        setPhase('ERROR');
        const axiosErr = err as AxiosError<{ message?: string }>;
        setError(
          axiosErr.response?.data?.message ||
          'Không thể sinh đề từ tài liệu. Vui lòng kiểm tra lại định dạng tệp hoặc thử lại sau.'
        );
      }
    } else {
      // Manual Topic Mode
      if (!topic.trim()) {
        setError('Vui lòng nhập chủ đề bài thi cần sinh.');
        return;
      }

      setPhase('GENERATING');
      setProgressMessage('AI Agent đang soạn thảo đề bài theo chủ đề và chuẩn hóa rubric 10đ...');

      try {
        const result = await teacherAiExamApi.generateExamDraft({
          topic: topic.trim(),
          difficulty,
          programmingLanguage,
          additionalRequirements: additionalRequirements.trim() || undefined,
          criteriaCount,
        });

        setDraft({
          ...result,
          programmingLanguage,
          difficulty,
        });
        setPhase('SUCCESS');
      } catch (err: unknown) {
        setPhase('ERROR');
        const axiosErr = err as AxiosError<{ message?: string }>;
        setError(
          axiosErr.response?.data?.message ||
          'Không thể sinh bản nháp đề thi. Vui lòng thử lại sau.'
        );
      }
    }
  };

  // Live Rubric Sum Calculation
  const calculateRubricTotal = (): number => {
    if (!draft || !draft.rubricCriteria) return 0;
    return draft.rubricCriteria.reduce((sum, item) => sum + (Number(item.maxScore) || 0), 0);
  };

  const isRubricValid = (): boolean => {
    const total = calculateRubricTotal();
    return Math.abs(total - 10.0) < 0.001;
  };

  // Auto-normalize rubric back to exact 10.00
  const handleAutoNormalizeRubric = () => {
    if (!draft || !draft.rubricCriteria || draft.rubricCriteria.length === 0) return;
    const currentSum = draft.rubricCriteria.reduce((s, c) => s + (Number(c.maxScore) || 1), 0);
    if (currentSum <= 0) return;

    let runningSum = 0;
    const updated = draft.rubricCriteria.map((item, idx) => {
      if (idx === draft.rubricCriteria.length - 1) {
        const remainder = Math.max(0.5, Number((10.0 - runningSum).toFixed(2)));
        return { ...item, maxScore: remainder };
      }
      const scaled = Number(((item.maxScore * 10.0) / currentSum).toFixed(2));
      runningSum += scaled;
      return { ...item, maxScore: Math.max(0.5, scaled) };
    });

    setDraft({ ...draft, rubricCriteria: updated, totalRubricScore: 10.0 });
  };

  // Rubric Editing
  const handleUpdateCriterion = (index: number, field: keyof AiRubricItem, value: string | number) => {
    if (!draft || !draft.rubricCriteria) return;
    const updated = [...draft.rubricCriteria];
    updated[index] = { ...updated[index], [field]: value };
    setDraft({ ...draft, rubricCriteria: updated });
  };

  const handleAddCriterion = () => {
    if (!draft) return;
    const newCriteria: AiRubricItem = {
      name: 'Tiêu chí bổ sung',
      description: 'Mô tả tiêu chí đánh giá',
      maxScore: 1.0,
    };
    setDraft({
      ...draft,
      rubricCriteria: [...(draft.rubricCriteria || []), newCriteria],
    });
  };

  const handleDeleteCriterion = (index: number) => {
    if (!draft || !draft.rubricCriteria || draft.rubricCriteria.length <= 1) return;
    const updated = draft.rubricCriteria.filter((_, i) => i !== index);
    setDraft({ ...draft, rubricCriteria: updated });
  };

  // Test Case Editing
  const handleUpdateTestCase = (index: number, field: keyof AiTestCaseDraft, value: any) => {
    if (!draft || !draft.testCases) return;
    const updated = [...draft.testCases];
    updated[index] = { ...updated[index], [field]: value };
    setDraft({ ...draft, testCases: updated });
  };

  const handleAddTestCase = () => {
    if (!draft) return;
    const newTc: AiTestCaseDraft = {
      name: `Test Case #${(draft.testCases?.length || 0) + 1}`,
      description: 'Mô tả trường hợp kiểm thử',
      inputData: '',
      expectedOutput: '',
      points: 2.5,
      isHidden: false,
    };
    setDraft({ ...draft, testCases: [...(draft.testCases || []), newTc] });
  };

  const handleDeleteTestCase = (index: number) => {
    if (!draft || !draft.testCases || draft.testCases.length <= 1) return;
    const updated = draft.testCases.filter((_, i) => i !== index);
    setDraft({ ...draft, testCases: updated });
  };

  // Final Confirmation & Persistence
  const handleSaveConfirmed = async () => {
    if (!draft) return;
    const classIdToSave = selectedClassId || (classes.length > 0 ? classes[0].id : '');
    if (!classIdToSave) {
      setError('Vui lòng chọn lớp học áp dụng bài thi.');
      return;
    }
    if (!classes.some((c) => c.id === classIdToSave)) {
      setError('Lớp học đã chọn không hợp lệ hoặc bạn không có quyền phụ trách.');
      return;
    }
    if (!draft.title.trim()) {
      setError('Tiêu đề bài thi không được để trống.');
      return;
    }
    if (!isRubricValid()) {
      setError('Tổng điểm barem rubric phải bằng chính xác 10.00 điểm trước khi lưu.');
      return;
    }

    setIsSaving(true);
    setError(null);

    try {
      const payload: SaveAiExamRequest = {
        classId: classIdToSave,
        title: draft.title.trim(),
        examDate,
        durationMinutes,
        problemStatement: draft.problemStatement,
        inputSpecification: draft.inputSpecification,
        outputSpecification: draft.outputSpecification,
        constraints: draft.constraints,
        sampleInput: draft.sampleInput,
        sampleOutput: draft.sampleOutput,
        programmingLanguage: draft.programmingLanguage || programmingLanguage,
        sourceDocumentName: draft.sourceDocumentName || selectedFile?.name,
        testCases: draft.testCases,
        rubricCriteria: draft.rubricCriteria,
      };

      await teacherAiExamApi.confirmAndSaveExam(payload);
      onExamCreated();
      onClose();
    } catch (err: unknown) {
      const axiosErr = err as AxiosError<{ message?: string }>;
      setError(
        axiosErr.response?.data?.message ||
        'Lưu bài thi thất bại. Vui lòng kiểm tra lại quyền hạn giáo viên hoặc kết nối máy chủ.'
      );
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-scrim/60 backdrop-blur-sm p-4 overflow-y-auto animate-in fade-in">
      <div className="bg-surface-container-lowest w-full max-w-4xl rounded-3xl shadow-2xl border border-outline-variant/60 flex flex-col my-8 overflow-hidden max-h-[92vh]">
        {/* Modal Header */}
        <div className="flex items-center justify-between p-5 bg-surface-container-low border-b border-outline-variant/40 shrink-0">
          <div className="flex items-center gap-3">
            <div className="p-2.5 rounded-2xl bg-purple-600 text-white shadow-sm shadow-purple-600/30">
              <Sparkles className="w-5 h-5 animate-pulse" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h2 className="font-headline-sm font-bold text-on-surface text-base">
                  AI Agent Sinh Đề Bài & Rubric Chấm Điểm
                </h2>
                <span className="px-2.5 py-0.5 rounded-full text-[10px] font-bold uppercase tracking-wider bg-purple-100 text-purple-800 border border-purple-300">
                  Document-Grounded Agent
                </span>
              </div>
              <p className="text-xs text-on-surface-variant mt-0.5">
                AI Agent đọc hiểu tài liệu học tập (PDF, Word, Text) để tự động thiết kế bài thi và barem điểm chuẩn 10đ
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-2 rounded-xl text-on-surface-variant hover:text-on-surface hover:bg-surface-container transition-colors cursor-pointer"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Modal Body Container with Scroll */}
        <div className="flex-1 overflow-y-auto p-5 sm:p-6 space-y-6">
          {/* Error Banner */}
          {error && (
            <div className="p-4 rounded-2xl bg-rose-50 border border-rose-200 text-rose-800 flex items-start gap-3 text-xs animate-in slide-in-from-top-1">
              <AlertCircle className="w-4 h-4 text-rose-600 shrink-0 mt-0.5" />
              <div className="flex-1">
                <span className="font-semibold block mb-0.5">Lưu ý hoặc lỗi xử lý:</span>
                <p>{error}</p>
              </div>
              <button onClick={() => setError(null)} className="text-rose-500 hover:text-rose-700">
                <X className="w-4 h-4" />
              </button>
            </div>
          )}

          {/* Step 1: Input & Configuration (When draft is not yet produced) */}
          {!draft && (
            <div className="space-y-6">
              {/* Mode Toggle Switch */}
              <div className="flex p-1 rounded-2xl bg-surface-container border border-outline-variant/40">
                <button
                  type="button"
                  onClick={() => setMode('DOCUMENT_UPLOAD')}
                  className={`flex-1 py-2 px-4 rounded-xl text-xs font-semibold flex items-center justify-center gap-2 transition-all cursor-pointer ${
                    mode === 'DOCUMENT_UPLOAD'
                      ? 'bg-surface-container-lowest text-primary shadow-xs'
                      : 'text-on-surface-variant hover:text-on-surface'
                  }`}
                >
                  <UploadCloud className="w-4 h-4" />
                  <span>Tải lên tài liệu học tập (Khuyến nghị)</span>
                </button>
                <button
                  type="button"
                  onClick={() => setMode('MANUAL_TOPIC')}
                  className={`flex-1 py-2 px-4 rounded-xl text-xs font-semibold flex items-center justify-center gap-2 transition-all cursor-pointer ${
                    mode === 'MANUAL_TOPIC'
                      ? 'bg-surface-container-lowest text-primary shadow-xs'
                      : 'text-on-surface-variant hover:text-on-surface'
                  }`}
                >
                  <FileText className="w-4 h-4" />
                  <span>Soạn thảo theo Chủ đề thủ công</span>
                </button>
              </div>

              {/* Mode 1: Document Upload Zone */}
              {mode === 'DOCUMENT_UPLOAD' && (
                <div className="space-y-3">
                  <label className="text-xs font-semibold text-on-surface flex items-center gap-1.5">
                    <span>Tài liệu nguồn (PDF có văn bản, DOCX, TXT):</span>
                    <span className="text-rose-600 font-bold">*</span>
                  </label>

                  {!selectedFile ? (
                    <div
                      onDragOver={(e) => {
                        e.preventDefault();
                        setIsDragging(true);
                      }}
                      onDragLeave={() => setIsDragging(false)}
                      onDrop={handleFileDrop}
                      onClick={() => fileInputRef.current?.click()}
                      className={`border-2 border-dashed rounded-2xl p-8 flex flex-col items-center justify-center gap-3 transition-all cursor-pointer text-center ${
                        isDragging
                          ? 'border-primary bg-primary/5 scale-[1.005]'
                          : 'border-outline-variant/60 hover:border-primary/50 hover:bg-surface-container-low'
                      }`}
                    >
                      <input
                        ref={fileInputRef}
                        type="file"
                        accept=".pdf,.docx,.txt"
                        className="hidden"
                        onChange={handleFileInputChange}
                      />
                      <div className="p-3.5 rounded-2xl bg-purple-50 text-purple-700 border border-purple-200">
                        <UploadCloud className="w-7 h-7" />
                      </div>
                      <div>
                        <p className="text-sm font-semibold text-on-surface">
                          Kéo thả tài liệu vào đây hoặc <span className="text-primary underline">chọn tệp từ máy tính</span>
                        </p>
                        <p className="text-xs text-on-surface-variant mt-1">
                          Hỗ trợ định dạng PDF (chứa text), DOCX (Word), TXT (Tối đa 15 MB)
                        </p>
                      </div>
                    </div>
                  ) : (
                    <div className="flex items-center justify-between p-4 rounded-2xl bg-surface-container border border-outline-variant/60">
                      <div className="flex items-center gap-3">
                        <div className="p-2.5 rounded-xl bg-purple-100 text-purple-700 border border-purple-200">
                          <FileText className="w-6 h-6" />
                        </div>
                        <div>
                          <p className="text-xs font-bold text-on-surface truncate max-w-sm sm:max-w-md">
                            {selectedFile.name}
                          </p>
                          <p className="text-[11px] text-on-surface-variant mt-0.5">
                            {(selectedFile.size / (1024 * 1024)).toFixed(2)} MB • Định dạng {selectedFile.name.split('.').pop()?.toUpperCase()}
                          </p>
                        </div>
                      </div>
                      <div className="flex items-center gap-2">
                        <button
                          type="button"
                          onClick={handleAnalyzeOnly}
                          disabled={phase !== 'IDLE'}
                          className="px-3 py-1.5 rounded-lg border border-purple-300 text-purple-700 bg-purple-50 hover:bg-purple-100 text-xs font-semibold transition-colors flex items-center gap-1.5 cursor-pointer disabled:opacity-50"
                        >
                          <Sparkles className="w-3.5 h-3.5" />
                          <span>Phân tích tài liệu trước</span>
                        </button>
                        <button
                          type="button"
                          onClick={handleRemoveFile}
                          className="p-1.5 rounded-lg text-rose-600 hover:bg-rose-50 border border-transparent hover:border-rose-200 transition-colors cursor-pointer"
                          title="Hủy chọn tệp"
                        >
                          <Trash2 className="w-4 h-4" />
                        </button>
                      </div>
                    </div>
                  )}

                  {/* Document Analysis Insight Preview (Stage 1) */}
                  {analysisResult && (
                    <div className="p-4 rounded-2xl bg-purple-50/70 border border-purple-200 space-y-3 animate-in fade-in">
                      <div className="flex items-center justify-between">
                        <span className="text-xs font-bold text-purple-900 flex items-center gap-1.5">
                          <CheckCircle2 className="w-4 h-4 text-purple-700" />
                          Kết quả AI Agent đọc hiểu tài liệu:
                        </span>
                        {analysisResult.isSimulation && (
                          <span className="text-[10px] px-2 py-0.5 rounded-full bg-amber-100 text-amber-800 font-bold border border-amber-300">
                            Chế độ Dev Mock / Stub
                          </span>
                        )}
                      </div>
                      <div className="text-xs space-y-1.5 text-purple-950">
                        <p><strong className="text-purple-900">Chủ đề chính:</strong> {analysisResult.mainTopic}</p>
                        {analysisResult.subTopics?.length > 0 && (
                          <div className="flex items-center gap-1 flex-wrap pt-0.5">
                            <strong className="text-purple-900 mr-1">Chủ đề nhánh:</strong>
                            {analysisResult.subTopics.map((st, i) => (
                              <span key={i} className="px-2 py-0.5 rounded-md bg-white border border-purple-200 text-[11px] font-medium text-purple-800">
                                {st}
                              </span>
                            ))}
                          </div>
                        )}
                        {analysisResult.keyConcepts?.length > 0 && (
                          <div className="flex items-center gap-1 flex-wrap pt-0.5">
                            <strong className="text-purple-900 mr-1">Khái niệm & Thuật toán:</strong>
                            {analysisResult.keyConcepts.map((kc, i) => (
                              <span key={i} className="px-2 py-0.5 rounded-md bg-purple-100/80 border border-purple-300 text-[11px] font-semibold text-purple-900">
                                {kc}
                              </span>
                            ))}
                          </div>
                        )}
                      </div>
                    </div>
                  )}
                </div>
              )}

              {/* Mode 2: Manual Topic Field */}
              {mode === 'MANUAL_TOPIC' && (
                <div className="space-y-1.5">
                  <label className="text-xs font-semibold text-on-surface flex items-center gap-1.5">
                    <span>Chủ đề bài thi:</span>
                    <span className="text-rose-600 font-bold">*</span>
                  </label>
                  <input
                    type="text"
                    value={topic}
                    onChange={(e) => setTopic(e.target.value)}
                    placeholder="VD: Tìm kiếm theo chiều rộng BFS và đường đi ngắn nhất trên đồ thị..."
                    className="w-full px-3.5 py-2.5 rounded-xl border border-outline-variant/60 bg-surface-container-low text-on-surface text-xs focus:outline-none focus:border-primary focus:ring-1 focus:ring-primary"
                  />
                </div>
              )}

              {/* Common Assessment Options */}
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 pt-1">
                <div className="space-y-1.5">
                  <label className="text-xs font-semibold text-on-surface">Ngôn ngữ lập trình:</label>
                  <select
                    value={programmingLanguage}
                    onChange={(e) => setProgrammingLanguage(e.target.value)}
                    className="w-full px-3 py-2 rounded-xl border border-outline-variant/60 bg-surface-container-low text-on-surface text-xs focus:outline-none focus:border-primary cursor-pointer"
                  >
                    <option value="Java">Java (OpenJDK 21)</option>
                    <option value="Python">Python 3</option>
                    <option value="C++">C++ (GCC)</option>
                    <option value="C#">C# (.NET)</option>
                  </select>
                </div>

                <div className="space-y-1.5">
                  <label className="text-xs font-semibold text-on-surface">Độ khó mong muốn:</label>
                  <select
                    value={difficulty}
                    onChange={(e) => setDifficulty(e.target.value as 'EASY' | 'MEDIUM' | 'HARD')}
                    className="w-full px-3 py-2 rounded-xl border border-outline-variant/60 bg-surface-container-low text-on-surface text-xs focus:outline-none focus:border-primary cursor-pointer"
                  >
                    <option value="EASY">Dễ (Cơ bản / Hiểu cú pháp)</option>
                    <option value="MEDIUM">Trung bình (Vận dụng giải thuật)</option>
                    <option value="HARD">Khó (Tối ưu độ phức tạp & Biên)</option>
                  </select>
                </div>

                <div className="space-y-1.5">
                  <label className="text-xs font-semibold text-on-surface">Số tiêu chí Barem điểm:</label>
                  <select
                    value={criteriaCount}
                    onChange={(e) => setCriteriaCount(Number(e.target.value))}
                    className="w-full px-3 py-2 rounded-xl border border-outline-variant/60 bg-surface-container-low text-on-surface text-xs focus:outline-none focus:border-primary cursor-pointer"
                  >
                    <option value={2}>2 tiêu chí (Đúng đắn + Tối ưu)</option>
                    <option value={3}>3 tiêu chí</option>
                    <option value={4}>4 tiêu chí (Chuẩn khuyến nghị)</option>
                    <option value={5}>5 tiêu chí</option>
                  </select>
                </div>
              </div>

              {/* Additional Requirements */}
              <div className="space-y-1.5">
                <label className="text-xs font-semibold text-on-surface">
                  Yêu cầu hoặc ràng buộc bổ sung (Tùy chọn):
                </label>
                <textarea
                  rows={2}
                  value={additionalRequirements}
                  onChange={(e) => setAdditionalRequirements(e.target.value)}
                  placeholder="VD: Trọng tâm kiểm tra cấu trúc Queue, yêu cầu xử lý đồ thị không liên thông, thời gian O(V+E)..."
                  className="w-full px-3.5 py-2.5 rounded-xl border border-outline-variant/60 bg-surface-container-low text-on-surface text-xs focus:outline-none focus:border-primary"
                />
              </div>

              {/* Progress Stepper & Status Banner */}
              {phase !== 'IDLE' && phase !== 'SUCCESS' && (
                <div className="p-4 rounded-2xl bg-surface-container border border-purple-200/80 space-y-3 animate-in fade-in">
                  <div className="flex items-center gap-3">
                    <Loader2 className="w-5 h-5 text-purple-600 animate-spin shrink-0" />
                    <div>
                      <p className="text-xs font-bold text-on-surface">{progressMessage}</p>
                      <p className="text-[11px] text-on-surface-variant">Quá trình này có thể mất từ 5 đến 15 giây tuỳ theo độ dài tài liệu.</p>
                    </div>
                  </div>
                  {/* Visual Stepper */}
                  <div className="grid grid-cols-3 gap-2 text-center text-[10px] font-bold">
                    <div className={`p-1.5 rounded-lg border ${phase === 'EXTRACTING' ? 'bg-purple-100 border-purple-300 text-purple-800' : 'bg-surface-container-low text-outline'}`}>
                      1. Trích xuất văn bản
                    </div>
                    <div className={`p-1.5 rounded-lg border ${phase === 'ANALYZING' ? 'bg-purple-100 border-purple-300 text-purple-800' : 'bg-surface-container-low text-outline'}`}>
                      2. Phân tích tài liệu
                    </div>
                    <div className={`p-1.5 rounded-lg border ${phase === 'GENERATING' ? 'bg-purple-100 border-purple-300 text-purple-800' : 'bg-surface-container-low text-outline'}`}>
                      3. Sinh đề & Barem 10đ
                    </div>
                  </div>
                </div>
              )}

              {/* Action Button */}
              <div className="flex items-center justify-end gap-3 pt-2">
                <button
                  type="button"
                  onClick={onClose}
                  className="px-4 py-2.5 rounded-xl border border-outline-variant/60 text-xs font-semibold text-on-surface hover:bg-surface-container transition-colors cursor-pointer"
                >
                  Hủy bỏ
                </button>
                <button
                  type="button"
                  onClick={handleGenerate}
                  disabled={phase === 'EXTRACTING' || phase === 'ANALYZING' || phase === 'GENERATING'}
                  className="px-5 py-2.5 rounded-xl bg-purple-600 hover:bg-purple-700 text-white text-xs font-bold shadow-sm shadow-purple-600/30 flex items-center gap-2 transition-all cursor-pointer disabled:opacity-60"
                  id="btn-start-ai-generate"
                >
                  <Sparkles className="w-4 h-4" />
                  <span>Bắt đầu Sinh đề bài bằng AI Agent</span>
                </button>
              </div>
            </div>
          )}

          {/* Step 2: Interactive Preview & Edit Mode (After draft is generated) */}
          {draft && (
            <div className="space-y-6 animate-in fade-in">
              {/* Draft Editor Workspace Header */}
              <div className="flex flex-col sm:flex-row sm:items-center justify-between p-4 rounded-2xl bg-surface-container border border-outline-variant/60 gap-3">
                <div className="flex items-center gap-3">
                  <div className="p-2.5 rounded-xl bg-purple-100 text-purple-800 border border-purple-200">
                    <Sparkles className="w-5 h-5 text-purple-700" />
                  </div>
                  <div>
                    <h3 className="text-xs font-bold text-on-surface">
                      Bản thảo Đề bài & Rubric do AI Agent đề xuất
                    </h3>
                    <p className="text-[11px] text-on-surface-variant">
                      Giảng viên đối chiếu và tinh chỉnh các mục trước khi xác nhận lưu bản nháp vào lớp học.
                    </p>
                  </div>
                </div>

                <div className="flex items-center gap-2">
                  <button
                    type="button"
                    onClick={() => {
                      setDraft(null);
                      setError(null);
                    }}
                    className="px-3 py-1.5 rounded-lg border border-outline-variant text-on-surface text-xs font-semibold hover:bg-surface-container-high transition-colors flex items-center gap-1 cursor-pointer"
                  >
                    <RefreshCw className="w-3.5 h-3.5" />
                    <span>Sinh đề khác</span>
                  </button>
                </div>
              </div>

              {/* Source Document Citation & Simulation Tag */}
              {(draft.sourceDocumentName || draft.sourceReferences?.length) && (
                <div className="p-3.5 rounded-xl bg-purple-50 border border-purple-200 text-xs space-y-1.5 text-purple-950">
                  <div className="flex items-center justify-between">
                    <span className="font-bold text-purple-900 flex items-center gap-1.5">
                      <BookOpen className="w-3.5 h-3.5 text-purple-700" />
                      Nguồn tài liệu học tập tham chiếu: {draft.sourceDocumentName || selectedFile?.name}
                    </span>
                    {draft.isSimulation && (
                      <span className="text-[10px] px-2 py-0.5 rounded-full bg-amber-100 text-amber-800 font-bold border border-amber-300">
                        Chế độ Dev Mock / Stub
                      </span>
                    )}
                  </div>
                  {draft.learningObjectives && draft.learningObjectives.length > 0 && (
                    <p className="text-[11px] text-purple-900">
                      <strong>Mục tiêu đánh giá:</strong> {draft.learningObjectives.join(' • ')}
                    </p>
                  )}
                  {draft.sourceReferences && draft.sourceReferences.length > 0 && (
                    <div className="text-[11px] text-purple-900 italic">
                      <strong>Trích dẫn căn cứ:</strong> {draft.sourceReferences.join(' | ')}
                    </div>
                  )}
                </div>
              )}

              {/* Exam Title Input */}
              <div className="space-y-1.5">
                <label className="text-xs font-semibold text-on-surface">Tiêu đề bài thi:</label>
                <input
                  type="text"
                  value={draft.title}
                  onChange={(e) => setDraft({ ...draft, title: e.target.value })}
                  className="w-full px-3.5 py-2.5 rounded-xl border border-outline-variant/60 bg-surface-container-low text-on-surface text-xs font-bold focus:outline-none focus:border-primary"
                />
              </div>

              {/* Problem Statement Textarea */}
              <div className="space-y-1.5">
                <label className="text-xs font-semibold text-on-surface">Mô tả bài toán (Problem Statement):</label>
                <textarea
                  rows={4}
                  value={draft.problemStatement}
                  onChange={(e) => setDraft({ ...draft, problemStatement: e.target.value })}
                  className="w-full px-3.5 py-2.5 rounded-xl border border-outline-variant/60 bg-surface-container-low text-on-surface text-xs focus:outline-none focus:border-primary font-sans leading-relaxed"
                />
              </div>

              {/* Input / Output Specs */}
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div className="space-y-1.5">
                  <label className="text-xs font-semibold text-on-surface">Đặc tả đầu vào (Input Specification):</label>
                  <textarea
                    rows={3}
                    value={draft.inputSpecification || ''}
                    onChange={(e) => setDraft({ ...draft, inputSpecification: e.target.value })}
                    className="w-full px-3.5 py-2 rounded-xl border border-outline-variant/60 bg-surface-container-low text-on-surface text-xs focus:outline-none focus:border-primary font-mono"
                  />
                </div>
                <div className="space-y-1.5">
                  <label className="text-xs font-semibold text-on-surface">Đặc tả đầu ra (Output Specification):</label>
                  <textarea
                    rows={3}
                    value={draft.outputSpecification || ''}
                    onChange={(e) => setDraft({ ...draft, outputSpecification: e.target.value })}
                    className="w-full px-3.5 py-2 rounded-xl border border-outline-variant/60 bg-surface-container-low text-on-surface text-xs focus:outline-none focus:border-primary font-mono"
                  />
                </div>
              </div>

              {/* Constraints & Sample I/O */}
              <div className="space-y-1.5">
                <label className="text-xs font-semibold text-on-surface">Giới hạn & Ràng buộc (Constraints):</label>
                <input
                  type="text"
                  value={draft.constraints || ''}
                  onChange={(e) => setDraft({ ...draft, constraints: e.target.value })}
                  className="w-full px-3.5 py-2 rounded-xl border border-outline-variant/60 bg-surface-container-low text-on-surface text-xs focus:outline-none focus:border-primary font-mono"
                />
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div className="space-y-1.5">
                  <label className="text-xs font-semibold text-on-surface">Dữ liệu mẫu đầu vào (Sample Input):</label>
                  <textarea
                    rows={2}
                    value={draft.sampleInput || ''}
                    onChange={(e) => setDraft({ ...draft, sampleInput: e.target.value })}
                    className="w-full px-3 py-1.5 rounded-xl border border-outline-variant/60 bg-surface-container-low text-on-surface font-mono text-xs focus:outline-none focus:border-primary"
                  />
                </div>
                <div className="space-y-1.5">
                  <label className="text-xs font-semibold text-on-surface">Dữ liệu mẫu đầu ra (Sample Output):</label>
                  <textarea
                    rows={2}
                    value={draft.sampleOutput || ''}
                    onChange={(e) => setDraft({ ...draft, sampleOutput: e.target.value })}
                    className="w-full px-3 py-1.5 rounded-xl border border-outline-variant/60 bg-surface-container-low text-on-surface font-mono text-xs focus:outline-none focus:border-primary"
                  />
                </div>
              </div>

              {/* Test Cases Editor */}
              <div className="space-y-3 pt-2">
                <div className="flex items-center justify-between border-b border-outline-variant/40 pb-2">
                  <div className="flex items-center gap-2">
                    <FileCode2 className="w-4 h-4 text-primary" />
                    <h4 className="text-xs font-bold text-on-surface">Danh sách Test Cases tự động ({draft.testCases?.length || 0})</h4>
                  </div>
                  <button
                    type="button"
                    onClick={handleAddTestCase}
                    className="px-2.5 py-1 rounded-lg bg-surface-container hover:bg-surface-container-high border border-outline-variant text-[11px] font-semibold text-primary flex items-center gap-1 cursor-pointer"
                  >
                    <Plus className="w-3.5 h-3.5" />
                    <span>Thêm test case</span>
                  </button>
                </div>

                <div className="space-y-2.5">
                  {draft.testCases?.map((tc, idx) => (
                    <div key={idx} className="p-3.5 rounded-2xl bg-surface-container-low border border-outline-variant/50 space-y-2">
                      <div className="flex items-center justify-between gap-3">
                        <input
                          type="text"
                          value={tc.name}
                          onChange={(e) => handleUpdateTestCase(idx, 'name', e.target.value)}
                          placeholder="Tên test case"
                          className="font-bold text-xs bg-transparent border-b border-dashed border-outline-variant focus:border-primary focus:outline-none px-1 py-0.5 flex-1"
                        />
                        <div className="flex items-center gap-2">
                          <label className="flex items-center gap-1 text-[11px] text-on-surface-variant cursor-pointer">
                            <input
                              type="checkbox"
                              checked={tc.isHidden}
                              onChange={(e) => handleUpdateTestCase(idx, 'isHidden', e.target.checked)}
                              className="rounded border-outline-variant"
                            />
                            <span>Test ẩn</span>
                          </label>
                          <button
                            type="button"
                            onClick={() => handleDeleteTestCase(idx)}
                            className="p-1 text-rose-500 hover:text-rose-700 cursor-pointer"
                          >
                            <Trash2 className="w-3.5 h-3.5" />
                          </button>
                        </div>
                      </div>

                      <div className="grid grid-cols-1 md:grid-cols-2 gap-2 text-xs">
                        <div>
                          <span className="text-[10px] text-outline font-mono block">Input Data:</span>
                          <textarea
                            rows={2}
                            value={tc.inputData}
                            onChange={(e) => handleUpdateTestCase(idx, 'inputData', e.target.value)}
                            className="w-full px-2 py-1 rounded-lg bg-surface-container-lowest border border-outline-variant/40 font-mono text-xs"
                          />
                        </div>
                        <div>
                          <span className="text-[10px] text-outline font-mono block">Expected Output:</span>
                          <textarea
                            rows={2}
                            value={tc.expectedOutput}
                            onChange={(e) => handleUpdateTestCase(idx, 'expectedOutput', e.target.value)}
                            className="w-full px-2 py-1 rounded-lg bg-surface-container-lowest border border-outline-variant/40 font-mono text-xs"
                          />
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              </div>

              {/* Rubric Criteria Editor with 10.00 Point Sum Enforcement */}
              <div className="space-y-3 pt-2">
                <div className="flex flex-col sm:flex-row sm:items-center justify-between border-b border-outline-variant/40 pb-2 gap-2">
                  <div className="flex items-center gap-2">
                    <Scale className="w-4 h-4 text-purple-600" />
                    <h4 className="text-xs font-bold text-on-surface">Barem chấm điểm Rubric (Bắt buộc đúng 10.00 điểm)</h4>
                  </div>
                  <div className="flex items-center gap-2">
                    <span
                      className={`px-2.5 py-0.5 rounded-full text-xs font-bold border inline-flex items-center gap-1 ${
                        isRubricValid()
                          ? 'bg-emerald-50 text-emerald-700 border-emerald-300'
                          : 'bg-rose-50 text-rose-700 border-rose-300'
                      }`}
                    >
                      {isRubricValid() ? <CheckCircle2 className="w-3.5 h-3.5" /> : <AlertCircle className="w-3.5 h-3.5" />}
                      <span>Tổng: {calculateRubricTotal().toFixed(2)} / 10.00 đ</span>
                    </span>

                    {!isRubricValid() && (
                      <button
                        type="button"
                        onClick={handleAutoNormalizeRubric}
                        className="px-2.5 py-1 rounded-lg bg-purple-50 text-purple-700 border border-purple-200 text-[11px] font-bold hover:bg-purple-100 transition-colors cursor-pointer"
                      >
                        Tự động cân bằng 10.00đ
                      </button>
                    )}

                    <button
                      type="button"
                      onClick={handleAddCriterion}
                      className="px-2 py-1 rounded-lg bg-surface-container hover:bg-surface-container-high border border-outline-variant text-[11px] font-semibold text-primary flex items-center gap-1 cursor-pointer"
                    >
                      <Plus className="w-3.5 h-3.5" />
                      <span>Thêm tiêu chí</span>
                    </button>
                  </div>
                </div>

                <div className="space-y-2">
                  {draft.rubricCriteria?.map((crit, idx) => (
                    <div key={idx} className="flex items-center justify-between p-3 rounded-xl bg-surface-container-low border border-outline-variant/50 gap-3">
                      <div className="flex-1 space-y-1">
                        <input
                          type="text"
                          value={crit.name}
                          onChange={(e) => handleUpdateCriterion(idx, 'name', e.target.value)}
                          placeholder="Tên tiêu chí"
                          className="font-bold text-xs bg-transparent border-b border-dashed border-outline-variant focus:border-primary focus:outline-none w-full"
                        />
                        <input
                          type="text"
                          value={crit.description || ''}
                          onChange={(e) => handleUpdateCriterion(idx, 'description', e.target.value)}
                          placeholder="Mô tả hướng dẫn chấm"
                          className="text-[11px] text-on-surface-variant bg-transparent border-none focus:outline-none w-full"
                        />
                      </div>
                      <div className="flex items-center gap-2 shrink-0">
                        <input
                          type="number"
                          step="0.25"
                          min="0.25"
                          max="10"
                          value={crit.maxScore}
                          onChange={(e) => handleUpdateCriterion(idx, 'maxScore', parseFloat(e.target.value) || 0)}
                          className="w-16 px-2 py-1 rounded-lg bg-surface-container-lowest border border-outline-variant/60 font-mono text-xs font-bold text-center focus:outline-none focus:border-primary"
                        />
                        <span className="text-xs text-on-surface-variant">đ</span>
                        <button
                          type="button"
                          onClick={() => handleDeleteCriterion(idx)}
                          className="p-1 text-rose-500 hover:text-rose-700 cursor-pointer"
                        >
                          <Trash2 className="w-3.5 h-3.5" />
                        </button>
                      </div>
                    </div>
                  ))}
                </div>
              </div>

              {/* Assignment & Execution Metadata */}
              <div className="p-4 rounded-2xl bg-surface-container border border-outline-variant/50 space-y-3">
                <h4 className="text-xs font-bold text-on-surface flex items-center gap-1.5">
                  <Sliders className="w-4 h-4 text-primary" />
                  Cài đặt Áp dụng bài thi (Lưu bản nháp Draft):
                </h4>

                <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                  <div className="space-y-1">
                    <label className="text-[11px] font-semibold text-on-surface">Lớp học áp dụng:</label>
                    <select
                      id="select-exam-class"
                      value={selectedClassId}
                      onChange={(e) => {
                        setSelectedClassId(e.target.value);
                        setError(null);
                      }}
                      disabled={classes.length === 0}
                      className="w-full px-3 py-2 rounded-xl border border-outline-variant/60 bg-surface-container-lowest text-on-surface text-xs focus:outline-none focus:border-primary cursor-pointer disabled:opacity-50"
                    >
                      {classes.length === 0 ? (
                        <option value="">Không có lớp học nào khả dụng</option>
                      ) : (
                        classes.map((c) => (
                          <option key={c.id} value={c.id}>
                            {c.code} - {c.name}
                          </option>
                        ))
                      )}
                    </select>
                    {classes.length === 0 && (
                      <p className="text-[10px] text-rose-600 font-medium">
                        Bạn chưa được phân công lớp học nào để giao đề thi.
                      </p>
                    )}
                  </div>

                  <div className="space-y-1">
                    <label className="text-[11px] font-semibold text-on-surface">Thời gian bắt đầu thi:</label>
                    <input
                      type="datetime-local"
                      value={examDate}
                      onChange={(e) => setExamDate(e.target.value)}
                      className="w-full px-3 py-1.5 rounded-xl border border-outline-variant/60 bg-surface-container-lowest text-on-surface text-xs focus:outline-none focus:border-primary cursor-pointer"
                    />
                  </div>

                  <div className="space-y-1">
                    <label className="text-[11px] font-semibold text-on-surface">Thời lượng thi (Phút):</label>
                    <input
                      type="number"
                      min={15}
                      max={360}
                      value={durationMinutes}
                      onChange={(e) => setDurationMinutes(Number(e.target.value))}
                      className="w-full px-3 py-2 rounded-xl border border-outline-variant/60 bg-surface-container-lowest text-on-surface text-xs focus:outline-none focus:border-primary"
                    />
                  </div>
                </div>
              </div>

              {/* Save Confirmation Button */}
              <div className="flex items-center justify-end gap-3 pt-3 border-t border-outline-variant/40">
                <button
                  type="button"
                  onClick={() => setDraft(null)}
                  className="px-4 py-2.5 rounded-xl border border-outline-variant/60 text-xs font-semibold text-on-surface hover:bg-surface-container transition-colors cursor-pointer"
                >
                  Quay lại chỉnh sửa đầu vào
                </button>
                <button
                  type="button"
                  onClick={handleSaveConfirmed}
                  disabled={isSaving || !isRubricValid()}
                  className="px-6 py-2.5 rounded-xl bg-emerald-600 hover:bg-emerald-700 text-white text-xs font-bold shadow-sm shadow-emerald-600/30 flex items-center gap-2 transition-all cursor-pointer disabled:opacity-50"
                  id="btn-confirm-save-ai-exam"
                >
                  {isSaving ? (
                    <>
                      <Loader2 className="w-4 h-4 animate-spin" />
                      <span>Đang lưu bài thi...</span>
                    </>
                  ) : (
                    <>
                      <CheckCircle2 className="w-4 h-4" />
                      <span>Xác nhận & Lưu Bài Thi (Trạng thái Draft)</span>
                    </>
                  )}
                </button>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
