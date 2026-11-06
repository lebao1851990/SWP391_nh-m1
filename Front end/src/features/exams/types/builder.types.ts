import { AiRubricItem } from '../api/ai-exam.api';

export type QuestionDifficulty = 'EASY' | 'MEDIUM' | 'HARD';

export interface BuilderTestCase {
  id: string;
  name: string;
  description?: string;
  inputData: string;
  expectedOutput: string;
  points: number;
  isHidden: boolean;
  displayOrder: number;
  isPersisted?: boolean;
  allowEmptyOutput?: boolean;
}

export interface BuilderQuestion {
  id: string;
  title: string;
  problemStatement: string;
  inputSpecification?: string;
  outputSpecification?: string;
  constraints?: string;
  sampleInput?: string;
  sampleOutput?: string;
  difficulty: QuestionDifficulty;
  maxScore: number;
  displayOrder: number;
  testCases: BuilderTestCase[];
  rubricCriteria?: AiRubricItem[];
  isPersisted?: boolean;
}

export interface BuilderExamState {
  id?: string;
  title: string;
  classId: string;
  examDate: string;
  durationMinutes: number;
  programmingLanguage: string;
  status: 'draft' | 'published' | 'upcoming' | 'active';
  sourceDocumentName?: string;
  questions: BuilderQuestion[];
  selectedQuestionId: string | null;
}

export interface AiBatchGenerateParams {
  mode: 'DOCUMENT_UPLOAD' | 'MANUAL_TOPIC';
  file?: File;
  topic?: string;
  programmingLanguage: string;
  difficulty: QuestionDifficulty;
  questionCount: number;
  additionalRequirements?: string;
  criteriaCount?: number;
}

