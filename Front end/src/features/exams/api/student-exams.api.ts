import { apiClient } from '@/lib/api-client';

export interface ExamResponse {
  id: string;
  title: string;
  description: string;
  durationMinutes: number;
  startTime: string;
  endTime: string;
  courseClassId: string;
  courseClassCode: string;
  status: 'upcoming' | 'active' | 'completed' | 'missed';
  score?: number;
}

export const studentExamsApi = {
  getStudentExams: async (): Promise<ExamResponse[]> => {
    const response = await apiClient.get('/student/exams');
    return response.data;
  },

  getStudentExam: async (id: string): Promise<ExamResponse> => {
    const response = await apiClient.get(`/student/exams/${id}`);
    return response.data;
  },

  submitExam: async (examId: string, sourceCode: string): Promise<void> => {
    const response = await apiClient.post(`/student/exams/${examId}/submissions`, { sourceCode });
    return response.data;
  },
};
