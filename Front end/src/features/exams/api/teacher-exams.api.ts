import { apiClient } from '@/lib/api-client';

export interface ClassSummary {
  id: string;
  name: string;
  code: string;
  semester?: string;
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
  status: string;
  createdAt: string;
  courseName: string;
  courseCode: string;
  enrolledCount: number;
  submittedCount: number;
}

export interface TeacherSubmissionListResponse {
  id: string;
  studentName: string;
  studentEmail: string;
  examTitle: string;
  courseCode: string;
  status: string;
  totalScore: number;
  submittedAt: string;
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
}

export interface TeacherClassResponse {
  id: string;
  name: string;
  code: string;
  teacherId?: string;
  studentCount?: number;
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
}

export interface UpdateExamPayload {
  title?: string;
  classId?: string;
  classIds?: string[];
  date?: string;
  durationMinutes?: number;
  status?: string;
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
  }
};

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

