export type AppealStatus = 'PENDING' | 'UNDER_REVIEW' | 'APPROVED' | 'REJECTED' | 'ESCALATED';

export type ResolutionAction = 'REGRADE' | 'FORWARD_ACADEMIC_REVIEW' | 'DIRECT_ADJUSTMENT';

export interface Appeal {
  id: string; // opaque identifier like apl_5a93d28f1b3e4c09
  submissionId: string;
  examName: string;
  questionId?: string;
  questionTitle?: string;
  status: AppealStatus;
  basis: string;
  explanation: string;
  studentName?: string;
  studentEmail?: string;
  reviewerNotes?: string;
  currentScore?: number;
  originalScore?: number;
  updatedScore?: number;
  autoScore?: number;
  publishedScore?: number;
  publicationStatus?: string;
  reviewStatus?: string;
  sourceCode?: string;
  testCasesPassed?: number;
  testCasesTotal?: number;
  reviewerName?: string;
  reviewerEmail?: string;
  adminName?: string;
  resolutionAction?: ResolutionAction;
  submissionStatus?: string;
  originalFilename?: string;
  submissionType?: string;
  fileSize?: number;
  hasArchive?: boolean;
  executionStatus?: string;
  compileOutput?: string;
  submittedAt?: string;
  createdAt: string;
  resolvedAt?: string;
}

export interface AppealResolvePayload {
  decision: 'APPROVED' | 'REJECTED';
  reviewerNotes: string;
  adjustedScore?: number;
  resolutionAction?: ResolutionAction;
}
