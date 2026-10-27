import { apiClient } from '@/lib/api-client';

export type ReviewIssueSeverity = 'INFO' | 'WARNING' | 'CRITICAL';
export type ReviewIssueCategory =
  | 'READABILITY_AND_NAMING'
  | 'CODE_DUPLICATION'
  | 'COMPLEXITY_AND_MAINTAINABILITY'
  | 'CLEAN_CODE_SMELLS'
  | 'COMPILER_OR_RUNTIME';

export interface CodeReviewIssue {
  line: number | null;
  severity: ReviewIssueSeverity;
  category: ReviewIssueCategory;
  message: string;
  suggestion?: string;
}

export interface SemanticReviewResponse {
  summary: string;
  cleanCodeScore: number;
  issues: CodeReviewIssue[];
  advisoryNotice: string;
}

export const semanticReviewApi = {
  getStudentSemanticReview: async (submissionId: string): Promise<SemanticReviewResponse> => {
    const response = await apiClient.post<SemanticReviewResponse>(
      `/student/submissions/${submissionId}/ai/semantic-review`
    );
    return response.data;
  },

  getTeacherSemanticReview: async (submissionId: string): Promise<SemanticReviewResponse> => {
    const response = await apiClient.post<SemanticReviewResponse>(
      `/teacher/submissions/${submissionId}/ai/semantic-review`
    );
    return response.data;
  },
};
