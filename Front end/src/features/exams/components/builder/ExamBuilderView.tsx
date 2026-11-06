'use client';

import React, { useState, useMemo } from 'react';
import { useSearchParams } from 'next/navigation';
import { 
  BuilderExamState, 
  BuilderQuestion, 
  BuilderTestCase 
} from '../../types/builder.types';
import { QuestionNavigator } from './QuestionNavigator';
import { QuestionEditor } from './QuestionEditor';
import { AiGenerationModal } from './AiGenerationModal';
import { ExamPreviewModal } from './ExamPreviewModal';
import { 
  useTeacherClasses, 
  useTeacherExamDetail, 
  useTeacherExamTestCases, 
  usePublishExam 
} from '../../hooks/use-teacher-exams';
import { teacherExamsApi, TeacherClassResponse } from '../../api/teacher-exams.api';
import { AppLink as Link } from '@/components/ui/app-link';
import { 
  ArrowLeft, 
  Sparkles, 
  Save, 
  Send, 
  Eye, 
  CheckCircle2, 
  AlertCircle, 
  Loader2, 
  FileCode2, 
  Calendar, 
  Clock, 
  X,
  Scale
} from 'lucide-react';
import { AxiosError } from 'axios';

interface ExamBuilderViewProps {
  initialExamId?: string;
}

interface ExamBuilderInnerProps {
  initialState: BuilderExamState;
  classes: TeacherClassResponse[];
}

const ExamBuilderInner: React.FC<ExamBuilderInnerProps> = ({ initialState, classes }) => {
  const publishExamMutation = usePublishExam();

  // Builder State initialized from props
  const [exam, setExam] = useState<BuilderExamState>(initialState);

  // Modals & UI States
  const [isAiModalOpen, setIsAiModalOpen] = useState(false);
  const [isPreviewModalOpen, setIsPreviewModalOpen] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  const [isPublishing, setIsPublishing] = useState(false);
  const [bannerMessage, setBannerMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null);
  const [editorActiveTab, setEditorActiveTab] = useState<'statement' | 'specs' | 'testcases'>('statement');
  const [examTitleError, setExamTitleError] = useState<string | null>(null);
  const [examDateError, setExamDateError] = useState<string | null>(null);
  const [examDurationError, setExamDurationError] = useState<string | null>(null);

  const focusAndScrollTo = (elementId: string) => {
    setTimeout(() => {
      const el = document.getElementById(elementId);
      if (el) {
        el.focus();
        el.scrollIntoView({ behavior: 'smooth', block: 'center' });
      }
    }, 60);
  };

  // Total test case count across all questions
  const totalTestCasesCount = useMemo(() => {
    return exam.questions.reduce((acc, q) => acc + q.testCases.length, 0);
  }, [exam.questions]);

  // Score Allocation Calculations (10.0-point Platform Scale)
  const totalAllocatedPoints = useMemo(() => {
    const rawSum = exam.questions.reduce((sum, q) => sum + (Number(q.maxScore) || 0), 0);
    return Math.round(rawSum * 100) / 100;
  }, [exam.questions]);

  const isScoreValid = useMemo(() => {
    return Math.abs(totalAllocatedPoints - 10.0) < 0.001;
  }, [totalAllocatedPoints]);

  // Distribute 10.0 points equally across all questions
  const handleDistributeEqually = () => {
    if (exam.questions.length === 0) return;
    const count = exam.questions.length;
    const baseShare = Math.floor((10.0 / count) * 100) / 100;
    let remainder = Math.round((10.0 - baseShare * count) * 100) / 100;

    const redistributed = exam.questions.map((q) => {
      let score = baseShare;
      if (remainder > 0.001) {
        score = Math.round((score + 0.01) * 100) / 100;
        remainder = Math.round((remainder - 0.01) * 100) / 100;
      }
      return { ...q, maxScore: score };
    });

    setExam((prev) => ({ ...prev, questions: redistributed }));
    setBannerMessage({
      type: 'success',
      text: `Đã chia đều 10.00 điểm cho ${count} câu hỏi thành công!`,
    });
  };

  // Currently selected question
  const selectedQuestion = useMemo(() => {
    if (!exam.selectedQuestionId) return exam.questions[0] || null;
    return exam.questions.find((q) => q.id === exam.selectedQuestionId) || exam.questions[0] || null;
  }, [exam.questions, exam.selectedQuestionId]);

  const selectedQuestionIndex = useMemo(() => {
    if (!selectedQuestion) return -1;
    return exam.questions.findIndex((q) => q.id === selectedQuestion.id);
  }, [exam.questions, selectedQuestion]);

  // Question Management Handlers
  const handleSelectQuestion = (questionId: string) => {
    setExam((prev) => ({ ...prev, selectedQuestionId: questionId }));
  };

  const handleAddQuestion = () => {
    const newIndex = exam.questions.length;
    const currentSum = exam.questions.reduce((sum, q) => sum + (Number(q.maxScore) || 0), 0);
    const remaining = Math.max(0, Math.round((10.0 - currentSum) * 100) / 100);
    const initialScore = exam.questions.length === 0 ? 10.0 : remaining;

    const newQuestion: BuilderQuestion = {
      id: `q_client_${Date.now()}_${Math.random().toString(36).slice(2, 6)}`,
      title: `Câu hỏi #${newIndex + 1}`,
      problemStatement: '',
      difficulty: 'MEDIUM',
      maxScore: initialScore,
      displayOrder: newIndex,
      testCases: [],
      isPersisted: false,
    };

    setExam((prev) => ({
      ...prev,
      questions: [...prev.questions, newQuestion],
      selectedQuestionId: newQuestion.id,
    }));
  };

  const handleDeleteQuestion = (questionId: string) => {
    setExam((prev) => {
      const remaining = prev.questions.filter((q) => q.id !== questionId);
      const reindexed = remaining.map((q, idx) => ({ ...q, displayOrder: idx }));
      const nextSelected =
        prev.selectedQuestionId === questionId
          ? reindexed[0]?.id || null
          : prev.selectedQuestionId;

      return {
        ...prev,
        questions: reindexed,
        selectedQuestionId: nextSelected,
      };
    });
  };

  const handleMoveQuestion = (questionId: string, direction: 'UP' | 'DOWN') => {
    setExam((prev) => {
      const idx = prev.questions.findIndex((q) => q.id === questionId);
      if (idx === -1) return prev;
      if (direction === 'UP' && idx === 0) return prev;
      if (direction === 'DOWN' && idx === prev.questions.length - 1) return prev;

      const targetIdx = direction === 'UP' ? idx - 1 : idx + 1;
      const copy = [...prev.questions];
      const temp = copy[idx];
      copy[idx] = copy[targetIdx];
      copy[targetIdx] = temp;

      return {
        ...prev,
        questions: copy.map((q, i) => ({ ...q, displayOrder: i })),
      };
    });
  };

  const handleUpdateQuestion = (updated: Partial<BuilderQuestion>) => {
    if (!selectedQuestion) return;
    setExam((prev) => ({
      ...prev,
      questions: prev.questions.map((q) =>
        q.id === selectedQuestion.id ? { ...q, ...updated } : q
      ),
    }));
  };

  // Test Case Management Handlers (Per Question Isolation)
  const handleAddTestCase = (newTc: Omit<BuilderTestCase, 'id' | 'displayOrder'>) => {
    if (!selectedQuestion) return;
    const testCase: BuilderTestCase = {
      ...newTc,
      id: `tc_client_${Date.now()}_${Math.random().toString(36).slice(2, 6)}`,
      displayOrder: selectedQuestion.testCases.length,
      isPersisted: false,
    };

    setExam((prev) => ({
      ...prev,
      questions: prev.questions.map((q) =>
        q.id === selectedQuestion.id
          ? { ...q, testCases: [...q.testCases, testCase] }
          : q
      ),
    }));
  };

  const handleUpdateTestCase = (updatedTc: BuilderTestCase) => {
    if (!selectedQuestion) return;
    setExam((prev) => ({
      ...prev,
      questions: prev.questions.map((q) =>
        q.id === selectedQuestion.id
          ? {
              ...q,
              testCases: q.testCases.map((tc) =>
                tc.id === updatedTc.id ? updatedTc : tc
              ),
            }
          : q
      ),
    }));
  };

  const handleDeleteTestCase = (testCaseId: string) => {
    if (!selectedQuestion) return;
    setExam((prev) => ({
      ...prev,
      questions: prev.questions.map((q) =>
        q.id === selectedQuestion.id
          ? {
              ...q,
              testCases: q.testCases
                .filter((tc) => tc.id !== testCaseId)
                .map((tc, idx) => ({ ...tc, displayOrder: idx })),
            }
          : q
      ),
    }));
  };

  // AI Generator Append Handler
  const handleQuestionsGenerated = (generatedQuestions: BuilderQuestion[]) => {
    setExam((prev) => {
      const merged = [...prev.questions, ...generatedQuestions].map((q, idx) => ({
        ...q,
        displayOrder: idx,
      }));
      return {
        ...prev,
        questions: merged,
        selectedQuestionId: generatedQuestions[0]?.id || prev.selectedQuestionId,
      };
    });

    const hasIncomplete = generatedQuestions.some(
      (q) =>
        !q.title.trim() ||
        !q.problemStatement.trim() ||
        q.testCases.length === 0 ||
        q.testCases.some(
          (tc) => !tc.name.trim() || !tc.expectedOutput.trim() || typeof tc.points !== 'number' || isNaN(tc.points) || tc.points <= 0
        )
    );

    setBannerMessage({
      type: hasIncomplete ? 'error' : 'success',
      text: hasIncomplete
        ? `Đã sinh ${generatedQuestions.length} câu hỏi từ AI! Lưu ý: Có câu hỏi hoặc test case chưa hoàn thiện, vui lòng kiểm tra và bổ sung trước khi xuất bản.`
        : `Đã sinh thành công ${generatedQuestions.length} câu hỏi kèm bộ test cases độc lập từ AI!`,
    });
  };

  // Save Draft Handler
  const handleSaveDraft = async () => {
    if (!exam.title.trim()) {
      setExamTitleError('Tiêu đề bài thi không được để trống.');
      setBannerMessage({ type: 'error', text: 'Vui lòng nhập tiêu đề bài thi hợp lệ (1 - 255 ký tự).' });
      focusAndScrollTo('exam-title-input');
      return;
    }
    if (exam.title.trim().length > 255) {
      setExamTitleError('Tiêu đề không được vượt quá 255 ký tự.');
      setBannerMessage({ type: 'error', text: 'Tiêu đề bài thi không được vượt quá 255 ký tự.' });
      focusAndScrollTo('exam-title-input');
      return;
    }
    const resolvedClassId = exam.classId || (classes.length > 0 ? classes[0].id : '');
    if (!resolvedClassId) {
      setBannerMessage({ type: 'error', text: 'Vui lòng chọn lớp học áp dụng.' });
      focusAndScrollTo('exam-class-select');
      return;
    }
    if (!exam.durationMinutes || exam.durationMinutes < 1 || exam.durationMinutes > 1440) {
      setExamDurationError('Thời lượng 1-1440 phút');
      setBannerMessage({ type: 'error', text: 'Thời lượng bài thi phải từ 1 đến 1440 phút.' });
      focusAndScrollTo('exam-duration-input');
      return;
    }
    if (exam.questions.length === 0) {
      setBannerMessage({ type: 'error', text: 'Đề thi phải có tối thiểu 1 câu hỏi.' });
      return;
    }

    setIsSaving(true);
    setBannerMessage(null);

    try {
      let activeExamId = exam.id;

      const questionPayload = exam.questions.map((q, idx) => ({
        id: q.isPersisted && !q.id.startsWith('q_') ? q.id : undefined,
        title: q.title.trim(),
        description: q.problemStatement,
        maxScore: q.maxScore,
        displayOrder: idx,
        status: 'DRAFT',
        testCases: q.testCases.map((tc, tcIdx) => ({
          id: tc.isPersisted && !tc.id.startsWith('tc_') ? tc.id : undefined,
          name: tc.name.trim(),
          description: tc.description,
          inputData: tc.inputData,
          expectedOutput: tc.expectedOutput,
          points: tc.points,
          isHidden: tc.isHidden,
          displayOrder: tcIdx,
        })),
      }));

      if (!activeExamId) {
        const createRes = await teacherExamsApi.createExam({
          title: exam.title.trim(),
          classId: resolvedClassId,
          date: exam.examDate,
          durationMinutes: exam.durationMinutes,
          questions: questionPayload,
        });
        activeExamId = createRes.id;
        setExam((prev) => ({
          ...prev,
          id: createRes.id,
          classId: resolvedClassId,
          status: 'draft',
          questions: createRes.questions && createRes.questions.length > 0
            ? createRes.questions.map((eq, qIdx) => ({
                ...prev.questions[qIdx],
                id: eq.id,
                title: eq.title,
                problemStatement: eq.description,
                maxScore: eq.maxScore,
                displayOrder: eq.displayOrder,
                isPersisted: true,
                testCases: (eq.testCases || []).map((tc, tcIdx) => ({
                  ...prev.questions[qIdx]?.testCases[tcIdx],
                  id: tc.id,
                  isPersisted: true,
                })),
              }))
            : prev.questions.map((q) => ({ ...q, isPersisted: true })),
        }));
      } else {
        const updateRes = await teacherExamsApi.updateExam(activeExamId, {
          title: exam.title.trim(),
          classId: resolvedClassId,
          date: exam.examDate,
          durationMinutes: exam.durationMinutes,
          status: 'draft',
          questions: questionPayload,
        });
        if (updateRes.questions && updateRes.questions.length > 0) {
          setExam((prev) => ({
            ...prev,
            questions: updateRes.questions!.map((eq, qIdx) => ({
              ...prev.questions[qIdx],
              id: eq.id,
              title: eq.title,
              problemStatement: eq.description,
              maxScore: eq.maxScore,
              displayOrder: eq.displayOrder,
              isPersisted: true,
              testCases: (eq.testCases || []).map((tc, tcIdx) => ({
                ...prev.questions[qIdx]?.testCases[tcIdx],
                id: tc.id,
                isPersisted: true,
              })),
            })),
          }));
        }
      }

      if (typeof window !== 'undefined' && activeExamId) {
        localStorage.setItem(
          `codeassess_exam_draft_${activeExamId}`,
          JSON.stringify({
            ...exam,
            id: activeExamId,
            classId: resolvedClassId,
            status: 'draft',
          })
        );
      }

      const hasContentIssues = exam.questions.some(q => q.testCases.some(tc => {
        const isExplicitEmpty = Boolean(tc.allowEmptyOutput);
        return (!tc.name || !tc.name.trim()) || ((!tc.expectedOutput || !tc.expectedOutput.trim()) && !isExplicitEmpty) || Boolean(tc.inputData && tc.inputData.length > 0 && !tc.inputData.trim());
      }));

      setBannerMessage({
        type: 'success',
        text: hasContentIssues
          ? `Đã lưu bản nháp thành công! (ID: ${activeExamId}) — Lưu ý: Còn test case chưa hoàn thiện I/O, cần bổ sung trước khi xuất bản.`
          : `Đã lưu bản nháp thành công! (ID bài thi: ${activeExamId})`,
      });
    } catch (err: unknown) {
      const axiosErr = err as AxiosError<{ message?: string }>;
      setBannerMessage({
        type: 'error',
        text: axiosErr.response?.data?.message || 'Lưu bản nháp thất bại. Vui lòng thử lại.',
      });
    } finally {
      setIsSaving(false);
    }
  };

  // Publish Exam Handler
  const handlePublish = async () => {
    // Phase 0: Validate Exam Header Fields
    if (!exam.title.trim()) {
      setExamTitleError('Tiêu đề bài thi không được để trống.');
      setBannerMessage({ type: 'error', text: 'Vui lòng nhập tiêu đề bài thi hợp lệ trước khi xuất bản.' });
      focusAndScrollTo('exam-title-input');
      return;
    }
    if (exam.title.trim().length > 255) {
      setExamTitleError('Tiêu đề không được vượt quá 255 ký tự.');
      setBannerMessage({ type: 'error', text: 'Tiêu đề bài thi không được vượt quá 255 ký tự.' });
      focusAndScrollTo('exam-title-input');
      return;
    }

    const resolvedClassId = exam.classId || (classes.length > 0 ? classes[0].id : '');
    if (!resolvedClassId) {
      setBannerMessage({ type: 'error', text: 'Vui lòng chọn lớp học áp dụng trước khi xuất bản.' });
      focusAndScrollTo('exam-class-select');
      return;
    }

    if (!exam.examDate) {
      setExamDateError('Bắt buộc khi xuất bản');
      setBannerMessage({ type: 'error', text: 'Vui lòng chọn thời gian thi trước khi xuất bản.' });
      focusAndScrollTo('exam-date-input');
      return;
    }

    if (!exam.durationMinutes || exam.durationMinutes < 1 || exam.durationMinutes > 1440) {
      setExamDurationError('Thời lượng 1-1440 phút');
      setBannerMessage({ type: 'error', text: 'Thời lượng bài thi phải từ 1 đến 1440 phút.' });
      focusAndScrollTo('exam-duration-input');
      return;
    }

    if (exam.questions.length === 0) {
      setBannerMessage({ type: 'error', text: 'Không thể xuất bản bài thi rỗng (cần ít nhất 1 câu hỏi).' });
      return;
    }

    // Strict 10.0 score allocation validation
    if (Math.abs(totalAllocatedPoints - 10.0) > 0.001) {
      setBannerMessage({
        type: 'error',
        text: `Tổng điểm các câu hỏi phải bằng chính xác 10.0 điểm trước khi xuất bản (Hiện tại: ${totalAllocatedPoints.toFixed(2)} / 10.0 điểm). Vui lòng điều chỉnh hoặc dùng nút "Chia đều 10 điểm".`,
      });
      return;
    }

    // Phase 1: All questions must have positive score (maxScore > 0) and valid decimal scale
    for (let i = 0; i < exam.questions.length; i++) {
      const q = exam.questions[i];
      if (typeof q.maxScore !== 'number' || isNaN(q.maxScore) || q.maxScore <= 0) {
        setExam((prev) => ({ ...prev, selectedQuestionId: q.id }));
        focusAndScrollTo('question-max-score-input');
        setBannerMessage({
          type: 'error',
          text: `Câu hỏi #${i + 1} ("${q.title}") phải có điểm tối đa lớn hơn 0 (Hiện tại: ${q.maxScore}đ). Vui lòng phân bổ lại điểm số.`,
        });
        return;
      }

      // Check decimal precision (at most 2 decimal places matching DECIMAL(5,2))
      const scoreStr = q.maxScore.toString();
      const dotIdx = scoreStr.indexOf('.');
      if (dotIdx !== -1 && scoreStr.length - dotIdx - 1 > 2) {
        setExam((prev) => ({ ...prev, selectedQuestionId: q.id }));
        focusAndScrollTo('question-max-score-input');
        setBannerMessage({
          type: 'error',
          text: `Câu hỏi #${i + 1} ("${q.title}") có điểm tối đa vượt quá 2 chữ số thập phân (${q.maxScore}). Định dạng chuẩn tối đa 2 số thập phân (DECIMAL(5,2)).`,
        });
        return;
      }
    }

    // Phase 2: Question title and problem statement
    for (let i = 0; i < exam.questions.length; i++) {
      const q = exam.questions[i];
      if (!q.title?.trim() || q.title.trim().length > 255) {
        setExam((prev) => ({ ...prev, selectedQuestionId: q.id }));
        focusAndScrollTo('question-title-input');
        setBannerMessage({
          type: 'error',
          text: `Câu hỏi #${i + 1} có tiêu đề không hợp lệ (1 - 255 ký tự). Vui lòng đặt lại tiêu đề câu hỏi.`,
        });
        return;
      }
      if (!q.problemStatement?.trim()) {
        setExam((prev) => ({ ...prev, selectedQuestionId: q.id }));
        setEditorActiveTab('statement');
        focusAndScrollTo('problem-statement-textarea');
        setBannerMessage({
          type: 'error',
          text: `Câu hỏi #${i + 1} ("${q.title}") chưa có nội dung mô tả đề bài. Vui lòng hoàn thiện đề bài trước khi xuất bản.`,
        });
        return;
      }
      const publicTcs = q.testCases.filter((tc) => !tc.isHidden);
      if (publicTcs.length === 0) {
        setExam((prev) => ({ ...prev, selectedQuestionId: q.id }));
        setEditorActiveTab('testcases');
        setBannerMessage({
          type: 'error',
          text: `Câu hỏi #${i + 1} ("${q.title}") cần tối thiểu 1 test case công khai (Public/Sample) để học sinh kiểm thử.`,
        });
        return;
      }
    }

    // Phase 2.5: Test Case Content Validation (name, expectedOutput, non-whitespace input)
    for (let i = 0; i < exam.questions.length; i++) {
      const q = exam.questions[i];
      for (let j = 0; j < q.testCases.length; j++) {
        const tc = q.testCases[j];
        if (!tc.name || !tc.name.trim()) {
          setExam((prev) => ({ ...prev, selectedQuestionId: q.id }));
          setEditorActiveTab('testcases');
          setBannerMessage({
            type: 'error',
            text: `Câu hỏi #${i + 1} ("${q.title}"): Test case #${j + 1} chưa có tên. Vui lòng đặt tên cho test case.`,
          });
          return;
        }

        const isExplicitEmptyAllowed = Boolean(tc.allowEmptyOutput);
        if ((!tc.expectedOutput || !tc.expectedOutput.trim()) && !isExplicitEmptyAllowed) {
          setExam((prev) => ({ ...prev, selectedQuestionId: q.id }));
          setEditorActiveTab('testcases');
          setBannerMessage({
            type: 'error',
            text: `Câu hỏi #${i + 1} ("${q.title}"): Test case #${j + 1} ("${tc.name}") chưa có kết quả mong đợi (Expected Output). Vui lòng nhập Expected Output hoặc xác nhận không có stdout.`,
          });
          return;
        }

        if (tc.inputData && tc.inputData.length > 0 && !tc.inputData.trim()) {
          setExam((prev) => ({ ...prev, selectedQuestionId: q.id }));
          setEditorActiveTab('testcases');
          setBannerMessage({
            type: 'error',
            text: `Câu hỏi #${i + 1} ("${q.title}"): Test case #${j + 1} ("${tc.name}") có dữ liệu đầu vào chỉ chứa khoảng trắng. Vui lòng nhập dữ liệu hợp lệ hoặc để trống hoàn toàn nếu không cần stdin.`,
          });
          return;
        }
      }
    }

    // Phase 3: Test Case score allocation validation
    for (let i = 0; i < exam.questions.length; i++) {
      const q = exam.questions[i];
      let tcSum = 0;
      for (let j = 0; j < q.testCases.length; j++) {
        const tc = q.testCases[j];
        const tcPoints = Number(tc.points);
        if (isNaN(tcPoints) || tcPoints <= 0) {
          setExam((prev) => ({ ...prev, selectedQuestionId: q.id }));
          setEditorActiveTab('testcases');
          setBannerMessage({
            type: 'error',
            text: `Câu hỏi #${i + 1} ("${q.title}"): Test case #${j + 1} ("${tc.name}") phải có điểm lớn hơn 0 (Hiện tại: ${tc.points}đ).`,
          });
          return;
        }
        if (tcPoints > q.maxScore) {
          setExam((prev) => ({ ...prev, selectedQuestionId: q.id }));
          setEditorActiveTab('testcases');
          setBannerMessage({
            type: 'error',
            text: `Câu hỏi #${i + 1} ("${q.title}"): Điểm test case #${j + 1} ("${tc.name}": ${tcPoints}đ) vượt quá điểm tối đa của câu hỏi (${q.maxScore}đ).`,
          });
          return;
        }
        tcSum += tcPoints;
      }
      tcSum = Number(tcSum.toFixed(2));
      if (tcSum > q.maxScore + 0.005) {
        setExam((prev) => ({ ...prev, selectedQuestionId: q.id }));
        setEditorActiveTab('testcases');
        setBannerMessage({
          type: 'error',
          text: `Câu hỏi #${i + 1} ("${q.title}"): Tổng điểm các test case (${tcSum.toFixed(2)}đ) vượt quá điểm tối đa của câu hỏi (${q.maxScore.toFixed(2)}đ). Vui lòng điều chỉnh lại điểm các test case.`,
        });
        return;
      }
    }

    const confirmed = window.confirm(
      'Bạn có chắc chắn muốn xuất bản bài thi này? Sau khi xuất bản, sinh viên trong lớp sẽ có thể thấy đề thi theo lịch.'
    );
    if (!confirmed) return;

    setIsPublishing(true);
    setBannerMessage(null);

    try {
      let activeExamId = exam.id;
      const resolvedClassId = exam.classId || (classes.length > 0 ? classes[0].id : '');

      const questionPayload = exam.questions.map((q, idx) => ({
        id: q.isPersisted && !q.id.startsWith('q_') ? q.id : undefined,
        title: q.title.trim(),
        description: q.problemStatement,
        maxScore: q.maxScore,
        displayOrder: idx,
        status: 'DRAFT',
        testCases: q.testCases.map((tc, tcIdx) => ({
          id: tc.isPersisted && !tc.id.startsWith('tc_') ? tc.id : undefined,
          name: tc.name.trim(),
          description: tc.description,
          inputData: tc.inputData,
          expectedOutput: tc.expectedOutput,
          points: tc.points,
          isHidden: tc.isHidden,
          displayOrder: tcIdx,
        })),
      }));

      if (!activeExamId) {
        const createRes = await teacherExamsApi.createExam({
          title: exam.title.trim(),
          classId: resolvedClassId,
          date: exam.examDate,
          durationMinutes: exam.durationMinutes,
          questions: questionPayload,
        });
        activeExamId = createRes.id;
        setExam((prev) => ({ ...prev, id: createRes.id, classId: resolvedClassId }));
      } else {
        await teacherExamsApi.updateExam(activeExamId, {
          title: exam.title.trim(),
          classId: resolvedClassId,
          date: exam.examDate,
          durationMinutes: exam.durationMinutes,
          questions: questionPayload,
        });
      }

      await publishExamMutation.mutateAsync(activeExamId);
      setExam((prev) => ({ ...prev, status: 'published' }));
      setBannerMessage({
        type: 'success',
        text: 'Bài thi đã được xuất bản chính thức thành công!',
      });
    } catch (err: unknown) {
      const axiosErr = err as AxiosError<{ message?: string }>;
      setBannerMessage({
        type: 'error',
        text: axiosErr.response?.data?.message || 'Xuất bản bài thi thất bại. Vui lòng kiểm tra lại.',
      });
    } finally {
      setIsPublishing(false);
    }
  };

  return (
    <div className="flex flex-col h-[calc(100vh-4rem)] bg-surface-container-lowest overflow-hidden">
      {/* Top Banner Alert */}
      {bannerMessage && (
        <div
          className={`px-4 py-2.5 text-xs font-semibold flex items-center justify-between shrink-0 transition-all ${
            bannerMessage.type === 'success'
              ? 'bg-emerald-50 text-emerald-800 border-b border-emerald-200'
              : 'bg-rose-50 text-rose-800 border-b border-rose-200'
          }`}
        >
          <div className="flex items-center gap-2">
            {bannerMessage.type === 'success' ? (
              <CheckCircle2 className="w-4 h-4 text-emerald-600 shrink-0" />
            ) : (
              <AlertCircle className="w-4 h-4 text-rose-600 shrink-0" />
            )}
            <span>{bannerMessage.text}</span>
          </div>
          <button
            type="button"
            onClick={() => setBannerMessage(null)}
            className="p-0.5 rounded hover:bg-black/5 cursor-pointer"
          >
            <X className="w-3.5 h-3.5" />
          </button>
        </div>
      )}

      {/* Main Header */}
      <header className="p-3 sm:px-6 bg-surface-container-low border-b border-outline-variant/30 shrink-0 space-y-3">
        <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-3">
          {/* Back & Title */}
          <div className="flex items-center gap-3 flex-1 min-w-0">
            <Link
              href="/teacher/exams"
              className="p-2 rounded-xl text-outline hover:text-on-surface hover:bg-surface-container transition-colors shrink-0"
              title="Quay lại danh sách bài thi"
            >
              <ArrowLeft className="w-5 h-5" />
            </Link>

            <div className="flex-1 min-w-0">
              <div>
                <div className="flex items-center gap-2">
                  <input
                    id="exam-title-input"
                    data-testid="exam-title-input"
                    type="text"
                    maxLength={255}
                    value={exam.title}
                    onChange={(e) => {
                      const val = e.target.value;
                      setExam((prev) => ({ ...prev, title: val }));
                      if (!val.trim()) {
                        setExamTitleError('Tiêu đề bài thi không được để trống.');
                      } else if (val.trim().length > 255) {
                        setExamTitleError('Tiêu đề không được vượt quá 255 ký tự.');
                      } else {
                        setExamTitleError(null);
                      }
                    }}
                    placeholder="Nhập tiêu đề bài thi..."
                    className={`text-base sm:text-lg font-bold text-on-surface bg-transparent hover:bg-surface-container-lowest focus:bg-surface-container-lowest focus:outline-none px-2 py-0.5 rounded-lg w-full transition-all ${
                      examTitleError ? 'border border-rose-500 ring-1 ring-rose-200' : 'focus:ring-1 focus:ring-primary'
                    }`}
                  />
                  {exam.status === 'published' ? (
                    <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-emerald-100 text-emerald-800 border border-emerald-200 shrink-0">
                      ĐÃ XUẤT BẢN
                    </span>
                  ) : (
                    <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-amber-100 text-amber-800 border border-amber-200 shrink-0">
                      BẢN NHÁP (DRAFT)
                    </span>
                  )}
                </div>
                {examTitleError && (
                  <p className="text-[11px] text-rose-600 font-semibold px-2 mt-0.5" data-testid="exam-title-error">
                    {examTitleError}
                  </p>
                )}
              </div>
              <p className="text-[11px] text-outline px-2 mt-0.5 flex items-center gap-2">
                <span>Trình soạn thảo đề thi AI (AI Multi-Question Exam Builder)</span>
                <span>•</span>
                <span>{exam.questions.length} câu hỏi</span>
                <span>•</span>
                <span>{totalTestCasesCount} test cases</span>
              </p>
            </div>
          </div>

          {/* Action Buttons */}
          <div className="flex items-center gap-2 shrink-0 self-end lg:self-auto">
            <button
              type="button"
              onClick={() => setIsAiModalOpen(true)}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-purple-600 hover:bg-purple-700 text-white text-xs font-semibold shadow-xs transition-colors cursor-pointer"
            >
              <Sparkles className="w-3.5 h-3.5" />
              <span>Sinh câu hỏi với AI</span>
            </button>

            <button
              type="button"
              onClick={() => setIsPreviewModalOpen(true)}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-outline-variant/40 hover:bg-surface-container text-on-surface text-xs font-semibold shadow-xs transition-colors cursor-pointer"
            >
              <Eye className="w-3.5 h-3.5" />
              <span>Xem trước</span>
            </button>

            <button
              type="button"
              disabled={isSaving}
              onClick={handleSaveDraft}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-surface-container-high hover:bg-surface-container-highest text-on-surface text-xs font-semibold shadow-xs transition-colors cursor-pointer disabled:opacity-50"
            >
              {isSaving ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <Save className="w-3.5 h-3.5" />}
              <span>Lưu bản nháp</span>
            </button>

            <button
              type="button"
              disabled={isPublishing || exam.status === 'published'}
              onClick={handlePublish}
              className="inline-flex items-center gap-1.5 px-3.5 py-1.5 rounded-lg bg-primary hover:bg-primary/90 text-on-primary text-xs font-semibold shadow-xs transition-colors cursor-pointer disabled:opacity-50"
            >
              {isPublishing ? (
                <Loader2 className="w-3.5 h-3.5 animate-spin" />
              ) : (
                <Send className="w-3.5 h-3.5" />
              )}
              <span>{exam.status === 'published' ? 'Đã xuất bản' : 'Xuất bản bài thi'}</span>
            </button>
          </div>
        </div>

        {/* Exam Metadata Row */}
        <div className="flex flex-wrap items-center gap-3 pt-2 border-t border-outline-variant/20 text-xs">
          {/* Class Select */}
          <div className="flex items-center gap-1.5">
            <span className="text-outline font-medium">Lớp học:</span>
            <select
              id="exam-class-select"
              data-testid="exam-class-select"
              value={exam.classId || (classes.length > 0 ? classes[0].id : '')}
              onChange={(e) => setExam((prev) => ({ ...prev, classId: e.target.value }))}
              className="px-2.5 py-1 bg-surface-container-lowest border border-outline-variant/40 rounded-lg text-xs font-semibold text-on-surface focus:outline-none focus:border-primary"
            >
              {classes.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.code} - {c.name}
                </option>
              ))}
            </select>
          </div>

          {/* Exam Date */}
          <div className="flex items-center gap-1.5">
            <span className="text-outline font-medium flex items-center gap-1">
              <Calendar className="w-3 h-3" />
              <span>Thời gian thi:</span>
            </span>
            <input
              id="exam-date-input"
              data-testid="exam-date-input"
              type="datetime-local"
              value={exam.examDate}
              onChange={(e) => {
                setExam((prev) => ({ ...prev, examDate: e.target.value }));
                if (e.target.value) setExamDateError(null);
              }}
              className={`px-2.5 py-1 bg-surface-container-lowest border rounded-lg text-xs font-semibold text-on-surface focus:outline-none focus:border-primary ${
                examDateError ? 'border-rose-500 ring-1 ring-rose-200' : 'border-outline-variant/40'
              }`}
            />
            {examDateError && (
              <span className="text-[10px] text-rose-600 font-semibold" data-testid="exam-date-error">
                {examDateError}
              </span>
            )}
          </div>

          {/* Duration */}
          <div className="flex items-center gap-1.5">
            <span className="text-outline font-medium flex items-center gap-1">
              <Clock className="w-3 h-3" />
              <span>Thời lượng:</span>
            </span>
            <input
              id="exam-duration-input"
              data-testid="exam-duration-input"
              type="number"
              min="1"
              max="1440"
              step="1"
              value={exam.durationMinutes}
              onChange={(e) => {
                const val = parseInt(e.target.value, 10);
                setExam((prev) => ({ ...prev, durationMinutes: isNaN(val) ? 0 : val }));
                if (isNaN(val) || val < 1 || val > 1440) {
                  setExamDurationError('1-1440 phút');
                } else {
                  setExamDurationError(null);
                }
              }}
              className={`w-16 px-2 py-1 bg-surface-container-lowest border rounded-lg text-xs font-semibold text-on-surface text-center focus:outline-none focus:border-primary ${
                examDurationError ? 'border-rose-500 ring-1 ring-rose-200 text-rose-600' : 'border-outline-variant/40'
              }`}
            />
            <span className="text-outline">phút</span>
            {examDurationError && (
              <span className="text-[10px] text-rose-600 font-semibold" data-testid="exam-duration-error">
                {examDurationError}
              </span>
            )}
          </div>

          {/* Language */}
          <div className="flex items-center gap-1.5">
            <span className="text-outline font-medium">Ngôn ngữ:</span>
            <select
              value={exam.programmingLanguage}
              onChange={(e) => setExam((prev) => ({ ...prev, programmingLanguage: e.target.value }))}
              className="px-2.5 py-1 bg-surface-container-lowest border border-outline-variant/40 rounded-lg text-xs font-semibold text-on-surface focus:outline-none focus:border-primary"
            >
              <option value="Java">Java</option>
              <option value="C++">C++</option>
              <option value="Python">Python</option>
              <option value="JavaScript">JavaScript</option>
            </select>
          </div>

          {/* Total Exam Score & Allocation Status */}
          <div className="flex items-center gap-1.5 px-2.5 py-1 rounded-lg border border-outline-variant/30 bg-surface-container-lowest shadow-2xs">
            <span className="text-outline font-medium">Thang điểm:</span>
            <strong className="text-on-surface font-mono font-bold">10.0đ</strong>
            <span className="text-outline-variant">•</span>
            <span className="text-outline font-medium">Phân bổ:</span>
            <strong
              data-testid="header-score-allocated"
              className={`font-mono font-bold ${
                isScoreValid
                  ? 'text-emerald-600'
                  : totalAllocatedPoints > 10
                  ? 'text-rose-600'
                  : 'text-amber-600'
              }`}
            >
              {totalAllocatedPoints.toFixed(2)} / 10.0đ
            </strong>
            {isScoreValid ? (
              <span className="px-1.5 py-0.5 rounded text-[10px] font-bold bg-emerald-100 text-emerald-800 border border-emerald-200">
                CHUẨN
              </span>
            ) : (
              <button
                type="button"
                data-testid="header-distribute-btn"
                disabled={exam.questions.length === 0}
                onClick={handleDistributeEqually}
                title="Tự động chia đều 10 điểm cho tất cả câu hỏi"
                className="px-1.5 py-0.5 rounded text-[10px] font-bold bg-amber-100 hover:bg-amber-200 text-amber-800 border border-amber-200 transition-colors cursor-pointer inline-flex items-center gap-1"
              >
                <Scale className="w-3 h-3" />
                <span>Chia đều 10đ</span>
              </button>
            )}
          </div>
        </div>
      </header>

      {/* Main Workspace (Navigator + Editor) */}
      <div className="flex-1 flex flex-col lg:flex-row overflow-hidden min-h-0">
        <QuestionNavigator
          questions={exam.questions}
          selectedQuestionId={exam.selectedQuestionId}
          totalAllocatedPoints={totalAllocatedPoints}
          onSelectQuestion={handleSelectQuestion}
          onAddQuestion={handleAddQuestion}
          onDeleteQuestion={handleDeleteQuestion}
          onMoveQuestion={handleMoveQuestion}
          onOpenAiGenerator={() => setIsAiModalOpen(true)}
          onDistributeEqually={handleDistributeEqually}
        />

        {selectedQuestion ? (
          <QuestionEditor
            question={selectedQuestion}
            questionIndex={selectedQuestionIndex}
            totalQuestions={exam.questions.length}
            activeTab={editorActiveTab}
            onTabChange={setEditorActiveTab}
            onUpdateQuestion={handleUpdateQuestion}
            onAddTestCase={handleAddTestCase}
            onUpdateTestCase={handleUpdateTestCase}
            onDeleteTestCase={handleDeleteTestCase}
          />
        ) : (
          <div className="flex-1 flex flex-col items-center justify-center p-8 text-center bg-surface-container-lowest">
            <div className="p-4 rounded-2xl bg-surface-container-low mb-3">
              <FileCode2 className="w-10 h-10 text-outline opacity-60" />
            </div>
            <h3 className="text-sm font-bold text-on-surface">Chưa chọn câu hỏi nào</h3>
            <p className="text-xs text-outline max-w-sm mt-1 mb-4">
              Vui lòng chọn một câu hỏi từ thanh điều hướng bên trái hoặc tạo câu hỏi mới bằng AI.
            </p>
            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={() => setIsAiModalOpen(true)}
                className="px-3.5 py-2 rounded-xl bg-purple-600 hover:bg-purple-700 text-white text-xs font-semibold shadow-xs flex items-center gap-1.5 cursor-pointer"
              >
                <Sparkles className="w-3.5 h-3.5" />
                <span>Sinh câu hỏi với AI</span>
              </button>
              <button
                type="button"
                onClick={handleAddQuestion}
                className="px-3.5 py-2 rounded-xl bg-primary text-on-primary text-xs font-semibold shadow-xs flex items-center gap-1.5 cursor-pointer"
              >
                <span>+ Thêm câu hỏi thủ công</span>
              </button>
            </div>
          </div>
        )}
      </div>

      {/* Modals */}
      {isAiModalOpen && (
        <AiGenerationModal
          existingQuestionsCount={exam.questions.length}
          onQuestionsGenerated={handleQuestionsGenerated}
          onClose={() => setIsAiModalOpen(false)}
        />
      )}

      {isPreviewModalOpen && (
        <ExamPreviewModal
          exam={exam}
          onClose={() => setIsPreviewModalOpen(false)}
        />
      )}
    </div>
  );
};

export const ExamBuilderView: React.FC<ExamBuilderViewProps> = ({ initialExamId }) => {
  const searchParams = useSearchParams();
  const examIdParam = initialExamId || searchParams.get('examId') || undefined;

  const { data: classes = [] } = useTeacherClasses();
  const { data: existingExam, isLoading: isLoadingExam } = useTeacherExamDetail(examIdParam || '');
  const { data: existingTestCases } = useTeacherExamTestCases(examIdParam || '');

  // Loading existing exam state
  if (examIdParam && isLoadingExam) {
    return (
      <div className="flex h-[calc(100vh-4rem)] items-center justify-center bg-surface-container-lowest">
        <div className="flex flex-col items-center gap-2">
          <Loader2 className="w-6 h-6 animate-spin text-primary" />
          <span className="text-xs font-semibold text-outline">Đang tải dữ liệu bài thi...</span>
        </div>
      </div>
    );
  }

  // Build clean initial state for inner component
  const defaultDate = new Date();
  defaultDate.setDate(defaultDate.getDate() + 1);
  defaultDate.setMinutes(0, 0, 0);

  let initialQuestions: BuilderQuestion[] = [];
  let cachedDraftQuestions: BuilderQuestion[] | null = null;
  if (typeof window !== 'undefined' && examIdParam) {
    try {
      const raw = localStorage.getItem(`codeassess_exam_draft_${examIdParam}`);
      if (raw) {
        const parsed = JSON.parse(raw);
        if (Array.isArray(parsed?.questions) && parsed.questions.length > 0) {
          cachedDraftQuestions = parsed.questions.map((q: BuilderQuestion) => ({
            ...q,
            maxScore: Number(q.maxScore) || 0,
          }));
        }
      }
    } catch {
      // ignore JSON parse error
    }
  }

  if (cachedDraftQuestions && cachedDraftQuestions.length > 0) {
    initialQuestions = cachedDraftQuestions;
  } else if (existingExam) {
    if (existingExam.questions && existingExam.questions.length > 0) {
      initialQuestions = existingExam.questions.map((eq, qIdx) => ({
        id: eq.id,
        title: eq.title || `Câu hỏi #${qIdx + 1}`,
        problemStatement: eq.description || '',
        difficulty: 'MEDIUM',
        maxScore: Number(eq.maxScore) || 0,
        displayOrder: eq.displayOrder ?? qIdx,
        testCases: (eq.testCases || []).map((tc, tcIdx) => ({
          id: tc.id,
          name: tc.name,
          description: tc.description,
          inputData: tc.inputData || '',
          expectedOutput: tc.expectedOutput || '',
          points: Number(tc.points) || 0,
          isHidden: Boolean(tc.isHidden),
          displayOrder: tc.displayOrder ?? tcIdx,
          isPersisted: true,
        })),
        isPersisted: true,
      }));
    } else {
      const mappedTcs: BuilderTestCase[] = (existingTestCases || []).map((tc, idx) => ({
        id: tc.id,
        name: tc.name,
        description: tc.description,
        inputData: tc.inputData || '',
        expectedOutput: tc.expectedOutput || '',
        points: tc.points,
        isHidden: tc.isHidden,
        displayOrder: tc.displayOrder ?? idx,
        isPersisted: true,
      }));

      initialQuestions = [
        {
          id: `q_persisted_${existingExam.id}`,
          title: existingExam.title || 'Câu hỏi 1',
          problemStatement: existingExam.problemStatement || '',
          inputSpecification: existingExam.inputSpecification,
          outputSpecification: existingExam.outputSpecification,
          constraints: existingExam.constraints,
          sampleInput: existingExam.sampleInput,
          sampleOutput: existingExam.sampleOutput,
          difficulty: 'MEDIUM',
          maxScore: 10.0,
          displayOrder: 0,
          testCases: mappedTcs,
          isPersisted: true,
        },
      ];
    }
  }

  const initialState: BuilderExamState = {
    id: existingExam?.id || examIdParam,
    title: existingExam?.title || 'Đề thi lập trình mới',
    classId: existingExam?.classId || (classes.length > 0 ? classes[0].id : ''),
    examDate: existingExam?.examDate
      ? new Date(existingExam.examDate).toISOString().slice(0, 16)
      : defaultDate.toISOString().slice(0, 16),
    durationMinutes: existingExam?.duration || 90,
    programmingLanguage: existingExam?.programmingLanguage || 'Java',
    status: (existingExam?.status?.toLowerCase() as 'draft' | 'published') || 'draft',
    questions: initialQuestions,
    selectedQuestionId: initialQuestions[0]?.id || null,
  };

  return <ExamBuilderInner initialState={initialState} classes={classes} />;
};
