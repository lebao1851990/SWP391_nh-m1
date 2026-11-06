import { SubmissionTestResultDto } from '@/features/scoring/types';

export type ReviewStatus = 'PENDING_REVIEW' | 'APPROVED' | 'REVISION_REQUIRED' | 'LEGACY_AUTO';
export type PublicationStatus = 'UNPUBLISHED' | 'PUBLISHED';

export interface TeacherGradingItem {
  submissionId: string;
  examId: string;
  examTitle: string;
  questionId: string;
  questionTitle: string;
  questionOrder: number;
  studentId: string;
  studentName: string;
  studentEmail: string;
  submissionTime: string;
  attemptNumber: number;
  provisionalScore: number | null;
  maxScore: number;
  reviewStatus: ReviewStatus;
  publicationStatus: PublicationStatus;
  executionStatus: string | null;
  approvedScore: number | null;
  publishedScore: number | null;
  testCasesPassed: number | null;
  testCasesTotal: number | null;
}

export interface TeacherGradingDetail {
  submissionId: string;
  examId: string;
  examTitle: string;
  className: string | null;
  questionId: string;
  questionTitle: string;
  questionDescription: string | null;
  questionMaxScore: number;
  studentId: string;
  studentName: string;
  studentEmail: string;
  submissionTime: string;
  attemptNumber: number;
  submissionType: string;
  originalFilename: string | null;
  archiveHash: string | null;
  executionStatus: string | null;
  exitCode: number | null;
  compileOutput: string | null;
  stdout: string | null;
  stderr: string | null;
  executionDurationMs: number | null;
  provisionalScore: number | null;
  autoScore: number | null;
  overrideScore: number | null;
  effectiveScore: number | null;
  reviewStatus: ReviewStatus;
  publicationStatus: PublicationStatus;
  reviewedById: string | null;
  reviewedByName: string | null;
  reviewedAt: string | null;
  reviewNotes: string | null;
  approvedScore: number | null;
  publishedById: string | null;
  publishedByName: string | null;
  publishedAt: string | null;
  publishedScore: number | null;
  scoreVersion: number;
  testCasesPassed: number | null;
  testCasesTotal: number | null;
  testResults: SubmissionTestResultDto[];
  criteria: Array<{
    criterionId: string;
    name: string;
    description: string;
    score: number;
    maxScore: number;
  }>;
  auditLogs: Array<{
    id: string;
    actorId: string;
    actorName: string;
    actorEmail: string;
    actionType: string;
    preEffectiveScore: number | null;
    postEffectiveScore: number | null;
    rationale: string | null;
    createdAt: string;
  }>;
  previousAttempts: TeacherGradingItem[];
}

export interface ApproveGradePayload {
  notes?: string;
}

export interface RequestRevisionPayload {
  reason: string;
}
