import { apiClient } from '@/lib/api-client';

export interface SubmissionFileDto {
  filename: string;
  size: number;
  content: string;
}

export interface SubmissionDetailResponse {
  id: string;
  examId: string;
  questionId?: string;
  questionTitle?: string;
  studentId: string;
  status: string;
  sourceCode: string;
  totalScore: number;
  overrideScore: number | null;
  autoScore?: number | null;
  testCasesPassed?: number;
  testCasesTotal?: number;
  evaluatedAt: string | null;
  createdAt: string;
  submissionType?: string;
  originalFilename?: string;
  archiveHash?: string;
  sourceTreeHash?: string;
  fileCount?: number;
  entryPointClass?: string;
  executionStatus?: string;
  exitCode?: number;
  compileOutput?: string;
  stdout?: string;
  stderr?: string;
  executionDurationMs?: number;
  instructorFeedback?: string | null;
}

export interface SubmissionListResponse {
  id: string;
  examId: string;
  examTitle?: string;
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

  getSubmissionFiles: async (id: string): Promise<SubmissionFileDto[]> => {
    const response = await apiClient.get(`/student/submissions/${id}/files`);
    return response.data;
  },

  downloadSubmissionZip: async (submissionId: string): Promise<{ blob: Blob; filename: string }> => {
    const response = await apiClient.get<Blob>(`/student/submissions/${submissionId}/download`, {
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
  },
};
