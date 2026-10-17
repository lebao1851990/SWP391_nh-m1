import { apiClient } from '@/lib/api-client';

export type StudentAiMode = 'COMPILER_ERROR' | 'CODE_EXPLANATION' | 'DEBUG_HINTS';

export interface StudentAiRequest {
  mode: StudentAiMode;
  compilerOutput?: string;
}

export interface StudentSandboxAiRequest {
  mode: StudentAiMode;
  sourceCode: string;
  compilerOutput?: string;
}

export interface StudentAiResponse {
  mode: StudentAiMode;
  explanation: string;
  advisoryNotice: string;
}

export const studentAiApi = {
  getSubmissionAiFeedback: async (
    submissionId: string,
    request: StudentAiRequest
  ): Promise<StudentAiResponse> => {
    const response = await apiClient.post<StudentAiResponse>(
      `/student/submissions/${submissionId}/ai/assist`,
      request
    );
    return response.data;
  },

  explainSubmissionError: async (
    submissionId: string,
    compilerOutput?: string
  ): Promise<StudentAiResponse> => {
    const response = await apiClient.post<StudentAiResponse>(
      `/student/submissions/${submissionId}/ai/explain-error`,
      { mode: 'COMPILER_ERROR', compilerOutput }
    );
    return response.data;
  },

  getSandboxAiFeedback: async (
    request: StudentSandboxAiRequest
  ): Promise<StudentAiResponse> => {
    const response = await apiClient.post<StudentAiResponse>(
      '/student/sandbox/ai/explain',
      request
    );
    return response.data;
  },
};
