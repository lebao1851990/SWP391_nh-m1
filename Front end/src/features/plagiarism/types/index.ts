export type FlaggedState = 'flagged' | 'clean' | 'pending';

export interface SimilarityPair {
  pairId: string;
  submissionAId: string;
  submissionBId: string;
  studentAId: string;
  studentBId: string;
  studentAName?: string;
  studentBName?: string;
  similarityRate: number; // e.g. 74.5
  isFlagged: boolean; // Backend authoritative flag
}

export interface PlagiarismEvidence {
  evidenceId: string;
  description: string; // e.g. "Potentially matching ForLoop structure, Lines 12-18"
  severity: 'high' | 'medium' | 'low';
}

export interface PlagiarismRecord {
  recordId: string;
  examName: string;
  className: string;
  pair: SimilarityPair;
  evidence: PlagiarismEvidence[];
}

export interface CodeComparisonData {
  pairId: string;
  fileA: string;
  fileB: string;
  sourceCodeA: string;
  sourceCodeB: string;
  matches: Array<{
    startLineA: number;
    endLineA: number;
    startLineB: number;
    endLineB: number;
    type: string;
  }>;
}

export interface StudentPlagiarismDetail {
  recordId: string;
  examName: string;
  similarityRate: number;
  isFlagged: boolean;
  evidence: PlagiarismEvidence[];
  // Student view MUST NOT contain another student's source code, ID, or raw hash.
}
