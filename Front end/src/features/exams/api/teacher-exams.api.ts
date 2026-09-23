import { apiClient } from '@/lib/api-client';

export interface ExamResponse {
  id: string;
  classId: string;
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

export const teacherExamsApi = {
  getMyExams: async (): Promise<ExamResponse[]> => {
    const response = await apiClient.get('/teacher/exams');
    return response.data;
  },

  getExamById: async (id: string): Promise<ExamResponse> => {
    const response = await apiClient.get(`/teacher/exams/${id}`);
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
