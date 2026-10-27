export type AppealStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'ESCALATED';

export interface Appeal {
  id: string; // opaque identifier like apl_5a93d28f1b3e4c09
  submissionId: string;
  examName: string;
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
  sourceCode?: string;
  testCasesPassed?: number;
  testCasesTotal?: number;
  reviewerName?: string;
  reviewerEmail?: string;
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
}
