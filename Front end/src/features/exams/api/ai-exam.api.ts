import { apiClient } from '@/lib/api-client';

export interface AiRubricItem {
  name: string;
  description?: string;
  maxScore: number;
}

export interface AiTestCaseDraft {
  name: string;
  description?: string;
  inputData: string;
  expectedOutput: string;
  points?: number;
  isHidden?: boolean;
}

export interface DocumentAnalysisResponse {
  documentName: string;
  documentSizeBytes: number;
  characterCount: number;
  documentPreview: string;
  mainTopic: string;
  subTopics: string[];
  keyConcepts: string[];
  extractedSnippets: string[];
  suggestedDifficulty: 'EASY' | 'MEDIUM' | 'HARD' | string;
  suggestedProblemTypes: string[];
  isSimulation: boolean;
}

export interface AiExamGenerateRequest {
  topic: string;
  difficulty: 'EASY' | 'MEDIUM' | 'HARD';
  programmingLanguage: string;
  additionalRequirements?: string;
  criteriaCount?: number;
}

export interface AiDocumentExamGenerateResponse {
  title: string;
  problemStatement: string;
  inputSpecification?: string;
  outputSpecification?: string;
  constraints?: string;
  sampleInput?: string;
  sampleOutput?: string;
  programmingLanguage?: string;
  difficulty?: string;
  sourceDocumentName?: string;
  learningObjectives?: string[];
  sourceReferences?: string[];
  documentTopicSummary?: string;
  testCases: AiTestCaseDraft[];
  rubricCriteria: AiRubricItem[];
  totalRubricScore: number;
  isSimulation?: boolean;
}

export interface AiExamGenerateResponse {
  title: string;
  problemStatement: string;
  inputSpecification?: string;
  outputSpecification?: string;
  constraints?: string;
  sampleInput?: string;
  sampleOutput?: string;
  testCases: AiTestCaseDraft[];
  rubricCriteria: AiRubricItem[];
  totalRubricScore: number;
}

export interface SaveAiExamRequest {
  classId: string;
  title: string;
  examDate: string;
  durationMinutes: number;
  problemStatement?: string;
  inputSpecification?: string;
  outputSpecification?: string;
  constraints?: string;
  sampleInput?: string;
  sampleOutput?: string;
  programmingLanguage?: string;
  sourceDocumentName?: string;
  testCases?: AiTestCaseDraft[];
  rubricCriteria?: AiRubricItem[];
}

export const teacherAiExamApi = {
  analyzeDocument: async (file: File): Promise<DocumentAnalysisResponse> => {
    const formData = new FormData();
    formData.append('file', file);

    const response = await apiClient.post<DocumentAnalysisResponse>(
      '/teacher/exams/ai/analyze-document',
      formData,
      {
        headers: {
          'Content-Type': 'multipart/form-data',
        },
      }
    );
    return response.data;
  },

  generateFromDocument: async (params: {
    file: File;
    difficulty: string;
    programmingLanguage: string;
    criteriaCount?: number;
    additionalRequirements?: string;
  }): Promise<AiDocumentExamGenerateResponse> => {
    const formData = new FormData();
    formData.append('file', params.file);
    formData.append('difficulty', params.difficulty);
    formData.append('programmingLanguage', params.programmingLanguage);
    if (params.criteriaCount) {
      formData.append('criteriaCount', params.criteriaCount.toString());
    }
    if (params.additionalRequirements) {
      formData.append('additionalRequirements', params.additionalRequirements);
    }

    const response = await apiClient.post<AiDocumentExamGenerateResponse>(
      '/teacher/exams/ai/generate-from-document',
      formData,
      {
        headers: {
          'Content-Type': 'multipart/form-data',
        },
      }
    );
    return response.data;
  },

  generateExamDraft: async (request: AiExamGenerateRequest): Promise<AiExamGenerateResponse> => {
    const response = await apiClient.post<AiExamGenerateResponse>(
      '/teacher/exams/ai/generate-draft',
      request
    );
    return response.data;
  },

  confirmAndSaveExam: async (request: SaveAiExamRequest): Promise<{ success: boolean; examId: string; message: string }> => {
    const response = await apiClient.post<{ success: boolean; examId: string; message: string }>(
      '/teacher/exams/ai/confirm-save',
      request
    );
    return response.data;
  },
};
