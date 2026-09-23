import { apiClient } from '@/lib/api-client';

export interface SubmissionDetailResponse {
  id: string;
  examId: string;
  studentId: string;
  status: string;
  sourceCode: string;
  totalScore: number;
  overrideScore: number | null;
  evaluatedAt: string | null;
  createdAt: string;
}

export interface SubmissionListResponse {
  id: string;
  examId: string;
  studentId: string;
  status: string;
  totalScore: number;
  overrideScore: number | null;
  evaluatedAt: string | null;
  createdAt: string;
}

export const submissionApi = {
  getStudentSubmissions: async (): Promise<SubmissionListResponse[]> => {
    const response = await apiClient.get('/student/submissions');
    return response.data;
  },

  getStudentSubmission: async (id: string): Promise<SubmissionDetailResponse> => {
    const response = await apiClient.get(`/student/submissions/${id}`);
    return response.data;
  },
};
