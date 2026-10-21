import { apiClient } from '@/lib/api-client';

export interface AiAnalysisResponse {
  analysis: string;
}

export const plagiarismAiApi = {
  getSimilarityExplanation: async (submissionId: string): Promise<AiAnalysisResponse> => {
    const response = await apiClient.post(`/teacher/plagiarism/${submissionId}/ai/similarity-explanation`);
    return response.data;
  },

  getCodeReview: async (submissionId: string, evidenceId: string): Promise<AiAnalysisResponse> => {
    const response = await apiClient.post(`/teacher/plagiarism/${submissionId}/ai/code-review`, {
      evidenceId: String(evidenceId)
    });
    return response.data;
  },
};
