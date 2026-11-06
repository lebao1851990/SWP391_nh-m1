'use client';

import React, { useState, useRef } from 'react';
import { BuilderQuestion, BuilderTestCase, QuestionDifficulty } from '../../types/builder.types';
import { teacherAiExamApi, AiTestCaseDraft, AiDocumentExamGenerateResponse, AiExamGenerateResponse } from '../../api/ai-exam.api';
import { AxiosError } from 'axios';
import {
  Sparkles,
  UploadCloud,
  FileText,
  X,
  Loader2,
  AlertCircle
} from 'lucide-react';

interface AiGenerationModalProps {
  onClose: () => void;
  onQuestionsGenerated: (questions: BuilderQuestion[]) => void;
  existingQuestionsCount: number;
}

export const AiGenerationModal: React.FC<AiGenerationModalProps> = ({
  onClose,
  onQuestionsGenerated,
  existingQuestionsCount,
}) => {
  const [mode, setMode] = useState<'DOCUMENT_UPLOAD' | 'MANUAL_TOPIC'>('DOCUMENT_UPLOAD');
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [topic, setTopic] = useState('');
  const [programmingLanguage, setProgrammingLanguage] = useState('Java');
  const [difficulty, setDifficulty] = useState<QuestionDifficulty>('MEDIUM');
  const [questionCount, setQuestionCount] = useState<number>(1);
  const [testCasesPerQuestion, setTestCasesPerQuestion] = useState<number>(3);
  const [additionalRequirements, setAdditionalRequirements] = useState('');

  const [isLoading, setIsLoading] = useState(false);
  const [progressMessage, setProgressMessage] = useState('');
  const [error, setError] = useState<string | null>(null);

  const fileInputRef = useRef<HTMLInputElement>(null);
  const isSubmittingRef = useRef<boolean>(false);

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files[0]) {
      const file = e.target.files[0];
      const validExtensions = ['.pdf', '.docx', '.txt'];
      const fileExt = '.' + file.name.split('.').pop()?.toLowerCase();
      if (!validExtensions.includes(fileExt)) {
        setError('Định dạng tệp không hợp lệ. Vui lòng chọn tệp .pdf, .docx hoặc .txt.');
        return;
      }
      if (file.size > 25 * 1024 * 1024) {
        setError('Kích thước tệp vượt quá giới hạn 25MB.');
        return;
      }
      setSelectedFile(file);
      setError(null);
    }
  };

  const handleGenerate = async (e: React.FormEvent) => {
    e.preventDefault();
    if (isSubmittingRef.current || isLoading) {
      return;
    }
    isSubmittingRef.current = true;
    setError(null);

    if (mode === 'DOCUMENT_UPLOAD' && !selectedFile) {
      setError('Vui lòng tải lên tài liệu học tập hoặc đề cương (.pdf, .docx, .txt).');
      isSubmittingRef.current = false;
      return;
    }
    if (mode === 'MANUAL_TOPIC' && !topic.trim()) {
      setError('Vui lòng nhập chủ đề câu hỏi.');
      isSubmittingRef.current = false;
      return;
    }

    setIsLoading(true);
    setProgressMessage(`Đang phân tích kiến thức và khởi tạo đề thi (${questionCount} câu hỏi, ${testCasesPerQuestion} test cases/câu)...`);

    try {
      const totalToGen = Math.min(Math.max(questionCount, 1), 5);
      const targetTcsPerQ = Math.min(Math.max(testCasesPerQuestion, 2), 6);

      let draftResponse: AiDocumentExamGenerateResponse | AiExamGenerateResponse;
      if (mode === 'DOCUMENT_UPLOAD' && selectedFile) {
        draftResponse = await teacherAiExamApi.generateFromDocument({
          file: selectedFile,
          difficulty,
          programmingLanguage,
          questionCount: totalToGen,
          testCasesPerQuestion: targetTcsPerQ,
          criteriaCount: 4,
          additionalRequirements: additionalRequirements.trim() || undefined,
        });
      } else {
        draftResponse = await teacherAiExamApi.generateExamDraft({
          topic: topic.trim(),
          difficulty,
          programmingLanguage,
          questionCount: totalToGen,
          testCasesPerQuestion: targetTcsPerQ,
          criteriaCount: 4,
          additionalRequirements: additionalRequirements.trim() || undefined,
        });
      }

      // Validate AI response structure
      if (!draftResponse || typeof draftResponse !== 'object') {
        throw new Error('Dữ liệu trả về từ AI không đúng cấu trúc đối tượng hợp lệ.');
      }

      // Extract raw question items
      const rawQuestions: Array<AiDocumentExamGenerateResponse | AiExamGenerateResponse> =
        draftResponse.questions && Array.isArray(draftResponse.questions) && draftResponse.questions.length > 0
          ? draftResponse.questions
          : [draftResponse];

      // Phase B requirement: Enforce requested question count
      if (rawQuestions.length < totalToGen) {
        throw new Error(`AI đã tạo ${rawQuestions.length}/${totalToGen} câu hỏi. Chưa hoàn thành đủ số lượng câu hỏi yêu cầu.`);
      }

      const generatedList: BuilderQuestion[] = [];
      const totalExamQuestions = existingQuestionsCount + totalToGen;
      const baseScore = Math.floor((10.0 / totalExamQuestions) * 100) / 100;

      for (let i = 0; i < totalToGen; i++) {
        const qData = rawQuestions[i];
        const questionOrder = existingQuestionsCount + i + 1;
        const isLastOverall = questionOrder === totalExamQuestions;
        const questionMaxScore = isLastOverall
          ? Math.round((10.0 - baseScore * (totalExamQuestions - 1)) * 100) / 100
          : baseScore;

        if (!qData || typeof qData !== 'object') {
          throw new Error(`Câu hỏi ${i + 1} từ AI không đúng định dạng đối tượng.`);
        }

        if (qData.testCases !== undefined && !Array.isArray(qData.testCases)) {
          throw new Error('Cấu trúc danh sách Test Cases từ AI không hợp lệ.');
        }

        const rawTcs = Array.isArray(qData.testCases) ? qData.testCases : [];

        // Phase B requirement: Enforce requested test case count per question
        const requiredTcs = draftResponse.requestedTestCasesPerQuestion || (mode === 'DOCUMENT_UPLOAD' ? targetTcsPerQ : 1);
        if (rawTcs.length < requiredTcs) {
          throw new Error(`Câu ${i + 1} thiếu ${requiredTcs - rawTcs.length}/${requiredTcs} test cases.`);
        }

        const tcCount = rawTcs.length;
        const baseTcScore = tcCount > 0 ? Math.floor((questionMaxScore / tcCount) * 100) / 100 : 0;

        const mappedTestCases: BuilderTestCase[] = rawTcs.map(
          (tc: AiTestCaseDraft, tcIdx: number) => {
            const isLastTc = tcIdx === tcCount - 1;
            const defaultProportionalPoints = isLastTc
              ? Math.round((questionMaxScore - baseTcScore * (tcCount - 1)) * 100) / 100
              : baseTcScore;

            let tcPoints = defaultProportionalPoints;
            if (tc.points !== undefined && tc.points !== null) {
              const parsedPts = Number(tc.points);
              if (!isNaN(parsedPts) && parsedPts > 0) {
                tcPoints = parsedPts;
              }
            }

            return {
              id: `tc_gen_${Date.now()}_${i}_${tcIdx}`,
              name: typeof tc.name === 'string' ? tc.name.trim() : `Test Case ${tcIdx + 1}`,
              description: typeof tc.description === 'string' ? tc.description : undefined,
              inputData: typeof tc.inputData === 'string' ? tc.inputData : '',
              expectedOutput: typeof tc.expectedOutput === 'string' ? tc.expectedOutput : '',
              points: tcPoints,
              isHidden: Boolean(tc.isHidden),
              displayOrder: tcIdx,
              isPersisted: false,
              allowEmptyOutput: false,
            };
          }
        );

        const newQuestion: BuilderQuestion = {
          id: `q_gen_${Date.now()}_${i}`,
          title: typeof qData.title === 'string' && qData.title.trim() ? qData.title.trim() : `Bài toán ${questionOrder}`,
          problemStatement: typeof qData.problemStatement === 'string' ? qData.problemStatement : '',
          inputSpecification: qData.inputSpecification,
          outputSpecification: qData.outputSpecification,
          constraints: qData.constraints,
          sampleInput: qData.sampleInput,
          sampleOutput: qData.sampleOutput,
          difficulty,
          maxScore: questionMaxScore,
          displayOrder: questionOrder - 1,
          testCases: mappedTestCases,
          rubricCriteria: qData.rubricCriteria,
          isPersisted: false,
        };

        generatedList.push(newQuestion);
      }

      onQuestionsGenerated(generatedList);
      onClose();
    } catch (err: unknown) {
      const axiosErr = err as AxiosError<{ message?: string }>;
      const statusCode = axiosErr.response?.status;
      const rawMessage = axiosErr.response?.data?.message || (err instanceof Error ? err.message : '');
      const userFriendlyMessage = mapAiErrorMessage(rawMessage, statusCode);
      setError(userFriendlyMessage);
    } finally {
      isSubmittingRef.current = false;
      setIsLoading(false);
      setProgressMessage('');
    }
  };

  function mapAiErrorMessage(rawMessage?: string, statusCode?: number): string {
    if (!rawMessage && !statusCode) {
      return 'AI chưa thể hoàn thành dữ liệu đề thi. Vui lòng thử lại hoặc giảm số lượng câu hỏi.';
    }

    const msgLower = (rawMessage || '').toLowerCase();

    // 1. Preserve actionable Vietnamese diagnostic feedback directly
    if (
      rawMessage &&
      (rawMessage.includes('AI đã tạo') ||
        rawMessage.includes('chưa hoàn thành') ||
        rawMessage.includes('thiếu nội dung đề bài') ||
        rawMessage.includes('thiếu trường problemStatement') ||
        rawMessage.includes('thiếu trường') ||
        rawMessage.includes('có test case không hợp lệ') ||
        rawMessage.includes('chỉ có') ||
        rawMessage.includes('Không thể hoàn thành câu') ||
        rawMessage.includes('không thể hoàn thành') ||
        msgLower.includes('test cases') ||
        msgLower.includes('testcases') ||
        rawMessage.includes('dàn ý') ||
        rawMessage.includes('đáp ứng đủ') ||
        rawMessage.includes('trùng lặp') ||
        rawMessage.includes('Cấu trúc danh sách') ||
        rawMessage.includes('cấu trúc testCases'))
    ) {
      return rawMessage;
    }

    // 2. Identify Jackson/JSON syntax or structure failure (MUST take precedence over HTTP 503)
    const isJacksonSyntaxError =
      msgLower.includes('unexpected') ||
      msgLower.includes('reference chain') ||
      msgLower.includes('syntax') ||
      msgLower.includes('invalid structured json') ||
      msgLower.includes('end-of-input') ||
      msgLower.includes('streamreadfeature') ||
      msgLower.includes('jsonparseexception') ||
      msgLower.includes('jsonmappingexception');

    if (
      msgLower.includes('invalid structured json') ||
      msgLower.includes('json') ||
      isJacksonSyntaxError
    ) {
      if (msgLower.includes('testcase') || msgLower.includes('test_case')) {
        return 'AI trả về cấu trúc testCases không hợp lệ.';
      }
      return 'AI trả về dữ liệu không đúng cấu trúc. Vui lòng thử lại.';
    }

    // 3. Genuine missing problemStatement (MUST NOT be triggered by Jackson reference chain syntax errors)
    if (
      !isJacksonSyntaxError &&
      (msgLower.includes('thiếu trường problemstatement') ||
        msgLower.includes('missing or empty problem statement') ||
        (msgLower.includes('problemstatement') && !msgLower.includes('json')))
    ) {
      return 'AI trả về dữ liệu thiếu trường problemStatement.';
    }

    // 4. Truncated output / MAX_TOKENS limit
    if (
      msgLower.includes('truncated') ||
      msgLower.includes('max_tokens') ||
      msgLower.includes('output limit') ||
      msgLower.includes('bị cắt') ||
      msgLower.includes('giới hạn độ dài') ||
      rawMessage?.includes('chạm giới hạn đầu ra') ||
      rawMessage?.includes('chưa hoàn chỉnh')
    ) {
      return 'AI trả về dữ liệu chưa hoàn chỉnh. Vui lòng giảm số câu hỏi hoặc thử lại.';
    }

    // 5. Timeouts & Connection Drops
    if (
      statusCode === 504 ||
      msgLower.includes('timed out') ||
      msgLower.includes('timeout') ||
      msgLower.includes('connection') ||
      msgLower.includes('mất kết nối') ||
      msgLower.includes('quá hạn') ||
      msgLower.includes('sockettimeout')
    ) {
      return 'Kết nối tới dịch vụ AI bị quá hạn hoặc gián đoạn. Vui lòng kiểm tra đường truyền và thử lại.';
    }

    // 6. Rate limit (HTTP 429 / Quota exceeded)
    if (
      statusCode === 429 ||
      msgLower.includes('rate limit') ||
      msgLower.includes('429') ||
      msgLower.includes('quota') ||
      msgLower.includes('resource_exhausted') ||
      msgLower.includes('vượt hạn mức')
    ) {
      return 'Hệ thống AI đang tạm thời quá tải hoặc vượt hạn mức yêu cầu. Vui lòng đợi trong giây lát và thử lại.';
    }

    // 7. Provider Unavailable (HTTP 503 / 502 / Server Error)
    if (
      statusCode === 503 ||
      statusCode === 502 ||
      msgLower.includes('503') ||
      msgLower.includes('service unavailable') ||
      msgLower.includes('service is currently unavailable') ||
      msgLower.includes('server unavailable') ||
      msgLower.includes('tạm thời không khả dụng')
    ) {
      // Preserve specific safe message if provider returned it
      if (rawMessage && rawMessage.includes('AI provider service is currently unavailable')) {
        return rawMessage;
      }
      return 'Hệ thống AI đang tạm thời quá tải hoặc vượt hạn mức yêu cầu. Vui lòng đợi trong giây lát và thử lại.';
    }

    // 9. Authentication / Secrets
    if (
      statusCode === 401 ||
      statusCode === 403 ||
      msgLower.includes('authentication') ||
      msgLower.includes('api key') ||
      msgLower.includes('unauthorized') ||
      msgLower.includes('401') ||
      msgLower.includes('403')
    ) {
      return 'Xác thực dịch vụ AI thất bại. Vui lòng kiểm tra khóa API cấu hình trên hệ thống.';
    }

    // 10. Quality / Problem Statement criteria
    if (
      msgLower.includes('problem statement') ||
      msgLower.includes('mô tả đề bài')
    ) {
      return 'Nội dung do AI tạo ra chưa đáp ứng đủ tiêu chuẩn đề bài. Vui lòng thử lại.';
    }

    // 11. Internal Server Error (HTTP 500)
    if (
      statusCode === 500 ||
      msgLower.includes('500') ||
      msgLower.includes('internal_server_error') ||
      msgLower.includes('an unexpected error occurred')
    ) {
      return 'Hệ thống AI gặp sự cố khi xử lý dữ liệu đề thi. Vui lòng thử lại hoặc giảm số lượng câu hỏi.';
    }

    return rawMessage || 'AI chưa thể hoàn thành dữ liệu đề thi. Vui lòng thử lại hoặc giảm số lượng câu hỏi.';
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-scrim/40 backdrop-blur-xs animate-in fade-in duration-200"
      role="dialog"
      aria-modal="true"
      aria-labelledby="ai-generator-modal-title"
    >
      <div
        className="w-full max-w-xl bg-surface-container-lowest border border-outline-variant/60 rounded-2xl shadow-xl overflow-hidden flex flex-col max-h-[90vh]"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div className="px-5 py-4 border-b border-outline-variant/30 flex items-center justify-between bg-surface-container-low/40">
          <div className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-lg bg-purple-100 flex items-center justify-center text-purple-700 shadow-2xs">
              <Sparkles className="w-4 h-4" />
            </div>
            <div>
              <h2 id="ai-generator-modal-title" className="text-sm font-bold text-on-surface">
                Trợ lý AI Sinh Đề Thi Tự Động
              </h2>
              <p className="text-[11px] text-outline">
                Tạo đề bài lập trình, bộ test cases và barem điểm chấm tự động bằng Gemini AI
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            disabled={isLoading}
            className="p-1 rounded-lg text-outline hover:text-on-surface hover:bg-surface-container transition-colors cursor-pointer"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* Body Content */}
        <div className="p-5 overflow-y-auto space-y-4">
          {error && (
            <div className="p-3 rounded-xl bg-error-container/40 border border-error/30 text-error text-xs flex items-start gap-2">
              <AlertCircle className="w-4 h-4 mt-0.5 shrink-0" />
              <span>{error}</span>
            </div>
          )}

          {/* Mode Switcher */}
          <div className="flex p-1 bg-surface-container rounded-xl gap-1">
            <button
              type="button"
              disabled={isLoading}
              onClick={() => setMode('DOCUMENT_UPLOAD')}
              className={`flex-1 py-1.5 px-3 rounded-lg text-xs font-semibold flex items-center justify-center gap-1.5 transition-all cursor-pointer ${mode === 'DOCUMENT_UPLOAD'
                ? 'bg-surface-container-lowest text-primary shadow-xs'
                : 'text-outline hover:text-on-surface'
                }`}
            >
              <UploadCloud className="w-3.5 h-3.5" />
              <span>Từ tài liệu học tập</span>
            </button>
            <button
              type="button"
              disabled={isLoading}
              onClick={() => setMode('MANUAL_TOPIC')}
              className={`flex-1 py-1.5 px-3 rounded-lg text-xs font-semibold flex items-center justify-center gap-1.5 transition-all cursor-pointer ${mode === 'MANUAL_TOPIC'
                ? 'bg-surface-container-lowest text-primary shadow-xs'
                : 'text-outline hover:text-on-surface'
                }`}
            >
              <FileText className="w-3.5 h-3.5" />
              <span>Theo chủ đề cụ thể</span>
            </button>
          </div>

          <form onSubmit={handleGenerate} className="space-y-4">
            {mode === 'DOCUMENT_UPLOAD' ? (
              <div>
                <label className="block text-xs font-semibold text-on-surface mb-1">
                  Tài liệu học tập (.pdf, .docx, .txt) *
                </label>
                <div
                  onClick={() => fileInputRef.current?.click()}
                  className="p-4 border-2 border-dashed border-outline-variant/60 rounded-xl bg-surface-container-low/30 hover:bg-surface-container-low text-center cursor-pointer transition-all"
                >
                  <input
                    ref={fileInputRef}
                    type="file"
                    accept=".pdf,.docx,.txt"
                    onChange={handleFileChange}
                    className="hidden"
                  />
                  <UploadCloud className="w-6 h-6 text-primary mx-auto mb-1.5 opacity-80" />
                  {selectedFile ? (
                    <div className="text-xs">
                      <p className="font-semibold text-on-surface">{selectedFile.name}</p>
                      <p className="text-[11px] text-outline">
                        {(selectedFile.size / (1024 * 1024)).toFixed(2)} MB • Nhấp để đổi tệp
                      </p>
                    </div>
                  ) : (
                    <div className="text-xs">
                      <p className="font-medium text-on-surface">Kéo thả hoặc nhấp để tải tài liệu lên</p>
                      <p className="text-[11px] text-outline">Tối đa 25MB (.pdf, .docx, .txt)</p>
                    </div>
                  )}
                </div>
              </div>
            ) : (
              <div>
                <label className="block text-xs font-semibold text-on-surface mb-1">
                  Chủ đề câu hỏi lập trình *
                </label>
                <input
                  type="text"
                  value={topic}
                  onChange={(e) => setTopic(e.target.value)}
                  placeholder="Ví dụ: Cấu trúc dữ liệu Ngăn xếp (Stack) và Hàng đợi (Queue)..."
                  className="w-full px-3 py-2 bg-surface-container-low border border-outline-variant/40 rounded-xl text-xs text-on-surface focus:outline-none focus:border-primary"
                  required
                />
              </div>
            )}

            <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
              <div>
                <label className="block text-xs font-semibold text-on-surface mb-1">
                  Ngôn ngữ lập trình
                </label>
                <select
                  value={programmingLanguage}
                  onChange={(e) => setProgrammingLanguage(e.target.value)}
                  className="w-full px-2.5 py-1.5 bg-surface-container-low border border-outline-variant/40 rounded-lg text-xs font-semibold text-on-surface focus:outline-none focus:border-primary"
                >
                  <option value="Java">Java</option>
                  <option value="C++">C++</option>
                  <option value="Python">Python</option>
                  <option value="JavaScript">JavaScript</option>
                </select>
              </div>

              <div>
                <label className="block text-xs font-semibold text-on-surface mb-1">
                  Độ khó câu hỏi
                </label>
                <select
                  value={difficulty}
                  onChange={(e) => setDifficulty(e.target.value as QuestionDifficulty)}
                  className="w-full px-2.5 py-1.5 bg-surface-container-low border border-outline-variant/40 rounded-lg text-xs font-semibold text-on-surface focus:outline-none focus:border-primary"
                >
                  <option value="EASY">Dễ (EASY)</option>
                  <option value="MEDIUM">Vừa (MEDIUM)</option>
                  <option value="HARD">Khó (HARD)</option>
                </select>
              </div>

              <div>
                <label className="block text-xs font-semibold text-on-surface mb-1">
                  Số lượng câu hỏi
                </label>
                <select
                  value={questionCount}
                  onChange={(e) => setQuestionCount(parseInt(e.target.value, 10))}
                  className="w-full px-2.5 py-1.5 bg-surface-container-low border border-outline-variant/40 rounded-lg text-xs font-semibold text-on-surface focus:outline-none focus:border-primary"
                >
                  <option value={1}>1 câu hỏi</option>
                  <option value={2}>2 câu hỏi</option>
                  <option value={3}>3 câu hỏi</option>
                  <option value={4}>4 câu hỏi</option>
                  <option value={5}>5 câu hỏi</option>
                </select>
              </div>

              <div>
                <label className="block text-xs font-semibold text-on-surface mb-1">
                  Test cases / câu
                </label>
                <select
                  value={testCasesPerQuestion}
                  onChange={(e) => setTestCasesPerQuestion(parseInt(e.target.value, 10))}
                  className="w-full px-2.5 py-1.5 bg-surface-container-low border border-outline-variant/40 rounded-lg text-xs font-semibold text-on-surface focus:outline-none focus:border-primary"
                >
                  <option value={2}>2 test cases</option>
                  <option value={3}>3 test cases</option>
                  <option value={4}>4 test cases</option>
                  <option value={5}>5 test cases</option>
                  <option value={6}>6 test cases</option>
                </select>
              </div>
            </div>

            <div>
              <label className="block text-xs font-semibold text-on-surface mb-1">
                Yêu cầu bổ sung cho AI (Tùy chọn)
              </label>
              <textarea
                rows={2}
                value={additionalRequirements}
                onChange={(e) => setAdditionalRequirements(e.target.value)}
                placeholder="Ví dụ: Giới hạn độ phức tạp O(N log N), yêu cầu xử lý ngoại lệ mảng rỗng..."
                className="w-full px-3 py-1.5 bg-surface-container-low border border-outline-variant/40 rounded-xl text-xs text-on-surface focus:outline-none focus:border-primary"
              />
            </div>

            {isLoading && (
              <div className="p-3.5 rounded-xl bg-purple-50 border border-purple-200 text-purple-900 space-y-2">
                <div className="flex items-center gap-2 text-xs font-semibold">
                  <Loader2 className="w-4 h-4 animate-spin text-purple-600" />
                  <span>{progressMessage}</span>
                </div>
                <div className="w-full bg-purple-200 h-1.5 rounded-full overflow-hidden">
                  <div className="bg-purple-600 h-full w-2/3 animate-pulse" />
                </div>
              </div>
            )}

            <div className="flex items-center justify-end gap-2 pt-3 border-t border-outline-variant/30">
              <button
                type="button"
                disabled={isLoading}
                onClick={onClose}
                className="px-3.5 py-1.5 rounded-lg border border-outline-variant/40 text-xs font-medium text-on-surface hover:bg-surface-container cursor-pointer disabled:opacity-50"
              >
                Hủy
              </button>
              <button
                type="submit"
                disabled={isLoading}
                className="px-4 py-1.5 rounded-lg bg-purple-600 hover:bg-purple-700 text-white text-xs font-semibold shadow-xs cursor-pointer disabled:opacity-50 flex items-center gap-1.5"
              >
                {isLoading ? (
                  <>
                    <Loader2 className="w-3.5 h-3.5 animate-spin" />
                    <span>Đang sinh câu hỏi...</span>
                  </>
                ) : (
                  <>
                    <Sparkles className="w-3.5 h-3.5" />
                    <span>Bắt đầu sinh đề AI</span>
                  </>
                )}
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>
  );
};
