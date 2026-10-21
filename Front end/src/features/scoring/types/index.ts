export type ScoringStatus = 'pending' | 'graded' | 'published';

export interface RuleScore {
  ruleId: string;
  name: string;
  description: string;
  score: number;
  maxScore: number;
  status: 'passed' | 'failed' | 'warning';
  message?: string;
}

export interface CriterionScore {
  criterionId: string;
  name: string;
  description: string;
  score: number; // The official score for this criterion
  maxScore: number;
  rules: RuleScore[];
  instructorNote?: string;
}

export interface ScoringDetail {
  submissionId: string;
  studentId: string;
  studentName: string;
  examName: string;
  className: string;
  evaluatedAt?: string;
  evaluators: string[];
  status: ScoringStatus;
  
  // Scoring Data
  totalScore: number; // Official backend total
  overrideTotalScore?: number | null;
  effectiveScore?: number;
  maxTotalScore: number;
  gradeEquivalent?: string;
  percentile?: string;
  classMedian?: number;
  
  criteria: CriterionScore[];
  
  // Plagiarism Data (Read-only metadata from Phase 4 perspective)
  astSimilarityPercentage?: number;
  
  instructorEvaluationComments?: string;
}

export interface StudentSubmissionResult {
  submissionId: string;
  examName: string;
  status: ScoringStatus;
  evaluatedAt?: string;
  evaluatorName?: string;
  
  totalScore: number;
  overrideTotalScore?: number | null;
  effectiveScore?: number;
  maxTotalScore: number;
  gradeEquivalent?: string;
  percentile?: string;
  
  criteria: CriterionScore[];
  
  astSimilarityPercentage?: number;
  similarityStatus?: 'normal' | 'flagged';
  instructorFeedback?: string;
  instructorEvaluationComments?: string;
}

export interface SaveScoringPayload {
  criteriaScores: Array<{
    criterionId: string;
    score: number;
  }>;
  instructorEvaluationComments?: string;
  
  // Manual override fields are UNCONFIRMED by backend specification.
  // Including them as optional based on UI, but they might not be supported.
  overrideTotalScore?: number;
  overrideRationale?: string;
}

// API Error representation
export interface ApiError {
  message: string;
  code?: string;
  status?: number;
}

export type GradeAuditActionType = 
  | 'INITIAL_GRADING'
  | 'OVERRIDE_SET'
  | 'OVERRIDE_CLEARED'
  | 'OVERRIDE_UPDATE'
  | 'RUBRIC_UPDATE'
  | string;

export interface GradeAuditLog {
  id: string;
  submissionId: string;
  actorId: string | null;
  actorName: string | null;
  actorEmail: string | null;
  actionType: GradeAuditActionType;
  preRubricScore: number | null;
  postRubricScore: number | null;
  preOverrideScore: number | null;
  postOverrideScore: number | null;
  preEffectiveScore: number | null;
  postEffectiveScore: number | null;
  rationale: string | null;
  createdAt: string;
}

