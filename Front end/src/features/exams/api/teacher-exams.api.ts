import { apiClient } from '@/lib/api-client';

export interface ClassSummary {
  id: string;
  name: string;
  code: string;
  semester?: string;
}

export interface ExamQuestionResponse {
  id: string;
  examId: string;
  title: string;
  description: string;
  maxScore: number;
  displayOrder: number;
  status: string;
  testCases?: TestCaseResponse[];
}

export interface ExamResponse {
  id: string;
  classId: string;
  classIds?: string[];
  classCodes?: string[];
  classNames?: string[];
  assignedClasses?: ClassSummary[];
  title: string;
  examDate: string;
  duration: number;
  durationMinutes?: number;
  status: string;
  createdAt: string;
  courseName: string;
  courseCode: string;
  enrolledCount: number;
  submittedCount: number;

  // Multi-Question Support
  questions?: ExamQuestionResponse[];
  totalQuestions?: number;
  totalAllocatedPoints?: number;

  // AI Problem Specifications
  problemStatement?: string;
  inputSpecification?: string;
  outputSpecification?: string;
  constraints?: string;
  sampleInput?: string;
  sampleOutput?: string;
  programmingLanguage?: string;
  sourceDocumentName?: string;
  rubricCriteria?: Array<{
    name: string;
    description?: string;
    maxScore: number;
  }>;
}

export interface TeacherSubmissionListResponse {
  id: string;
  studentId?: string;
  studentName: string;
  studentEmail: string;
  examTitle: string;
  courseCode: string;
  status: string;
  totalScore: number;
  submittedAt: string;
  questionId?: string;
  questionTitle?: string;
  originalFilename?: string;
  executionStatus?: string;
  autoScore?: number;
  testCasesPassed?: number;
  testCasesTotal?: number;
  plagiarismStatus?: string;
  maxSimilarityRate?: number;
  instructorFeedback?: string;
}

export interface SubmissionFileDto {
  filename: string;
  relativePath: string;
  sizeBytes: number;
  content: string;
}

export interface SubmissionDetailResponse {
  id: string;
  examId: string;
  studentId: string;
  status: string;
  sourceCode: string;
  totalScore: number;
  overrideScore: number;
  evaluatedAt: string;
  createdAt: string;
  questionId?: string;
  questionTitle?: string;
  originalFilename?: string;
  submissionType?: string;
  archiveHash?: string;
  sourceTreeHash?: string;
  entryPointClass?: string;
  executionStatus?: string;
  executionDurationMs?: number;
  autoScore?: number;
  testCasesPassed?: number;
  testCasesTotal?: number;
  instructorFeedback?: string;
}

export interface TeacherClassResponse {
  id: string;
  name: string;
  code: string;
  teacherId?: string;
  studentCount?: number;
}

export interface TestCaseDraftPayload {
  id?: string;
  name: string;
  description?: string;
  inputData?: string;
  expectedOutput?: string;
  points: number;
  isHidden?: boolean;
  displayOrder?: number;
}

export interface ExamQuestionDraftPayload {
  id?: string;
  title: string;
  description?: string;
  maxScore: number;
  displayOrder?: number;
  status?: string;
  testCases?: TestCaseDraftPayload[];
}

export interface CreateExamPayload {
  title: string;
  classId?: string;
  classIds?: string[];
  newClassCode?: string;
  newClassName?: string;
  semester?: string;
  date: string;
  durationMinutes: number;
  questions?: ExamQuestionDraftPayload[];
}

export interface UpdateExamPayload {
  title?: string;
  classId?: string;
  classIds?: string[];
  date?: string;
  durationMinutes?: number;
  status?: string;
  questions?: ExamQuestionDraftPayload[];
}

export const teacherExamsApi = {
  getMyClasses: async (): Promise<TeacherClassResponse[]> => {
    const response = await apiClient.get('/teacher/classes');
    return response.data;
  },

  createClass: async (payload: { name: string; code: string; semester?: string }): Promise<TeacherClassResponse> => {
    const response = await apiClient.post('/teacher/classes', payload);
    return response.data;
  },

  getMyExams: async (): Promise<ExamResponse[]> => {
    const response = await apiClient.get('/teacher/exams');
    return response.data;
  },

  getExamById: async (id: string): Promise<ExamResponse> => {
    const response = await apiClient.get(`/teacher/exams/${id}`);
    return response.data;
  },

  createExam: async (payload: CreateExamPayload): Promise<ExamResponse> => {
    const response = await apiClient.post('/teacher/exams', payload);
    return response.data;
  },

  updateExam: async (id: string, payload: UpdateExamPayload): Promise<ExamResponse> => {
    const response = await apiClient.put(`/teacher/exams/${id}`, payload);
    return response.data;
  },

  publishExam: async (id: string): Promise<ExamResponse> => {
    const response = await apiClient.post(`/teacher/exams/${id}/publish`);
    return response.data;
  },

  deleteExam: async (id: string): Promise<void> => {
    await apiClient.delete(`/teacher/exams/${id}`);
  },

  getMySubmissions: async (): Promise<TeacherSubmissionListResponse[]> => {
    const response = await apiClient.get('/teacher/submissions');
    return response.data;
  },

  getExamSubmissions: async (id: string): Promise<TeacherSubmissionListResponse[]> => {
    const response = await apiClient.get(`/teacher/exams/${id}/submissions`);
    return response.data;
  },
  
  getSubmissionById: async (id: string): Promise<SubmissionDetailResponse> => {
    const response = await apiClient.get(`/teacher/submissions/${id}`);
    return response.data;
  },

  getSubmissionFiles: async (id: string): Promise<SubmissionFileDto[]> => {
    const response = await apiClient.get(`/teacher/submissions/${id}/files`);
    return response.data;
  },

  updateInstructorFeedback: async (id: string, feedback: string): Promise<void> => {
    await apiClient.put(`/teacher/submissions/${id}/feedback`, { feedback });
  },

  getClassStudents: async (classId: string): Promise<{ id: string; studentId: string; email: string; fullName?: string }[]> => {
    const response = await apiClient.get(`/teacher/classes/${classId}/students`);
    return response.data;
  },

  enrollStudent: async (classId: string, payload: { studentId?: string; email?: string }): Promise<{ id: string; studentId: string; email: string; fullName?: string }> => {
    const response = await apiClient.post(`/teacher/classes/${classId}/students`, payload);
    return response.data;
  },

  unenrollStudent: async (classId: string, studentId: string): Promise<void> => {
    await apiClient.delete(`/teacher/classes/${classId}/students/${studentId}`);
  },

  getExamTestCases: async (examId: string): Promise<TestCaseResponse[]> => {
    const response = await apiClient.get(`/teacher/exams/${examId}/test-cases`);
    return response.data;
  },

  createTestCase: async (examId: string, payload: CreateTestCasePayload): Promise<TestCaseResponse> => {
    const response = await apiClient.post(`/teacher/exams/${examId}/test-cases`, payload);
    return response.data;
  },

  updateTestCase: async (examId: string, id: string, payload: UpdateTestCasePayload): Promise<TestCaseResponse> => {
    const response = await apiClient.put(`/teacher/exams/${examId}/test-cases/${id}`, payload);
    return response.data;
  },

  deleteTestCase: async (examId: string, id: string): Promise<void> => {
    await apiClient.delete(`/teacher/exams/${examId}/test-cases/${id}`);
  },

  // Multi-Question Exam Score Aggregation & Question-Level Overrides (TeacherExamScoringController)
  getStudentExamScores: async (examId: string, studentId: string): Promise<ExamScoreAggregationDto> => {
    const response = await apiClient.get(`/teacher/exams/${examId}/students/${studentId}/scores`);
    return response.data;
  },

  setQuestionOverride: async (examId: string, questionId: string, studentId: string, payload: QuestionOverrideRequestDto): Promise<QuestionScoreDetailDto> => {
    const response = await apiClient.put(`/teacher/exams/${examId}/questions/${questionId}/students/${studentId}/override`, payload);
    return response.data;
  },

  clearQuestionOverride: async (examId: string, questionId: string, studentId: string): Promise<QuestionScoreDetailDto> => {
    const response = await apiClient.delete(`/teacher/exams/${examId}/questions/${questionId}/students/${studentId}/override`);
    return response.data;
  },

  downloadSubmissionZip: async (submissionId: string): Promise<{ blob: Blob; filename: string }> => {
    const response = await apiClient.get<Blob>(`/teacher/submissions/${submissionId}/download`, {
      responseType: 'blob',
    });
    let filename = `submission-${submissionId}.zip`;
    const disposition = response.headers?.['content-disposition'];
    if (disposition) {
      const match = disposition.match(/filename\*=UTF-8''([^;]+)|filename="?([^";]+)"?/);
      if (match) {
        filename = decodeURIComponent(match[1] || match[2]);
      }
    }
    return { blob: response.data, filename };
  }
};

export interface QuestionScoreDetailDto {
  questionId: string;
  questionTitle: string;
  displayOrder?: number;
  maxScore: number;
  autoScore?: number;
  overrideScore?: number | null;
  effectiveScore: number;
  overrideReason?: string | null;
  overriddenBy?: string | null;
  overriddenAt?: string | null;
  status?: string;
  isOverridden: boolean;
  latestSubmissionId?: string | null;
  submissionCount: number;
  lastSubmittedAt?: string | null;
}

export interface ExamScoreAggregationDto {
  examId: string;
  examTitle: string;
  studentId: string;
  studentName?: string;
  examScore10: number;
  totalPointsEarned: number;
  totalMaxPoints: number;
  status: string;
  isProvisional: boolean;
  totalQuestions: number;
  submittedQuestions: number;
  completedQuestions: number;
  pendingQuestions: number;
  questionScores: QuestionScoreDetailDto[];
}

export interface QuestionOverrideRequestDto {
  overrideScore: number;
  rationale?: string;
}

export interface TestCaseResponse {
  id: string;
  examId: string;
  name: string;
  description?: string;
  inputData?: string;
  expectedOutput?: string;
  points: number;
  isHidden: boolean;
  displayOrder: number;
  createdAt: string;
  updatedAt: string;
}

export interface CreateTestCasePayload {
  name: string;
  description?: string;
  inputData?: string;
  expectedOutput?: string;
  points: number;
  isHidden?: boolean;
  displayOrder?: number;
}

export interface UpdateTestCasePayload {
  name: string;
  description?: string;
  inputData?: string;
  expectedOutput?: string;
  points: number;
  isHidden?: boolean;
  displayOrder?: number;
}

