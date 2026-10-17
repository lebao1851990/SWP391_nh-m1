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
  }
};
