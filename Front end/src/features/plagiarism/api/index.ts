import { apiClient } from '@/lib/api-client';
import { PlagiarismRecord, CodeComparisonData, StudentPlagiarismDetail, AstTreeComparison } from '../types';

/**
 * Strictly parses a boolean value from documented boolean representations.
 * Accepts boolean primitives and canonical string representations ("true", "false").
 * Does NOT allow arbitrary strings (e.g. "false", "0", "") to evaluate as truthy.
 */
export function parseStrictBoolean(val: unknown): boolean | undefined {
  if (typeof val === 'boolean') {
    return val;
  }
  if (typeof val === 'string') {
    const trimmed = val.trim().toLowerCase();
    if (trimmed === 'true') return true;
    if (trimmed === 'false') return false;
  }
  return undefined;
}

/**
 * Strictly parses similarity rate, preserving legitimate values including 0 and decimals.
 * Returns undefined if value is not a finite number.
 */
export function parseSimilarityRate(val: unknown): number | undefined {
  if (typeof val === 'number' && Number.isFinite(val)) {
    return val;
  }
  if (typeof val === 'string' && val.trim() !== '') {
    const parsed = Number(val.trim());
    if (Number.isFinite(parsed)) {
      return parsed;
    }
  }
  return undefined;
}

/**
 * Normalizes a single raw plagiarism item from backend SimilarityPairDto
 * into frontend PlagiarismRecord interface.
 * Throws explicit descriptive errors on missing required fields or malformed data.
 * Does NOT infer authoritative flag from similarityRate when the backend flag is absent.
 */
export function normalizeSimilarityPair(item: unknown): PlagiarismRecord {
  if (!item || typeof item !== 'object') {
    throw new Error('Malformed plagiarism record: expected a non-null object');
  }

  const raw = item as Record<string, unknown>;

  // 1. Handle legacy or test-fixture nested pair structure if provided
  if (raw.pair && typeof raw.pair === 'object') {
    const p = raw.pair as Record<string, unknown>;
    const pairId = String(p.pairId || raw.pairId || raw.id || '').trim();
    if (!pairId) {
      throw new Error('Malformed plagiarism record: missing required pairId');
    }

    const submissionAId = String(p.submissionAId || raw.submissionAId || '').trim();
    const submissionBId = String(p.submissionBId || raw.submissionBId || '').trim();
    if (!submissionAId || !submissionBId) {
      throw new Error(`Malformed plagiarism record (ID: ${pairId}): missing submission identifiers`);
    }

    const simRate = parseSimilarityRate(p.similarityRate ?? raw.similarityRate ?? p.similarity_rate);
    if (simRate === undefined) {
      throw new Error(`Malformed plagiarism record (ID: ${pairId}): missing or invalid similarityRate`);
    }

    const flagVal = parseStrictBoolean(p.isFlagged ?? p.flagged ?? raw.isFlagged ?? raw.flagged);
    if (flagVal === undefined) {
      throw new Error(`Malformed plagiarism record (ID: ${pairId}): missing or invalid authoritative boolean flag`);
    }

    return {
      recordId: pairId,
      examName: typeof raw.examName === 'string' && raw.examName.trim() ? raw.examName.trim() : 'Assessment Evaluation',
      className: typeof raw.className === 'string' && raw.className.trim() ? raw.className.trim() : 'Assigned Cohort',
      pair: {
        pairId,
        submissionAId,
        submissionBId,
        studentAId: String(p.studentAId || raw.studentAId || ''),
        studentBId: String(p.studentBId || raw.studentBId || ''),
        studentAName: typeof p.studentAName === 'string' ? p.studentAName : (typeof raw.studentAName === 'string' ? raw.studentAName : undefined),
        studentBName: typeof p.studentBName === 'string' ? p.studentBName : (typeof raw.studentBName === 'string' ? raw.studentBName : undefined),
        similarityRate: simRate,
        isFlagged: flagVal,
      },
      evidence: Array.isArray(raw.evidence) ? raw.evidence : [],
    };
  }

  // 2. Handle flat SimilarityPairDto from active backend
  const pairId = String(raw.pairId || raw.id || '').trim();
  if (!pairId) {
    throw new Error('Malformed plagiarism record: missing required pairId');
  }

  const submissionAId = String(raw.submissionAId || '').trim();
  const submissionBId = String(raw.submissionBId || '').trim();
  if (!submissionAId || !submissionBId) {
    throw new Error(`Malformed plagiarism record (ID: ${pairId}): missing submission identifiers`);
  }

  const simRate = parseSimilarityRate(raw.similarityRate ?? raw.similarity_rate);
  if (simRate === undefined) {
    throw new Error(`Malformed plagiarism record (ID: ${pairId}): missing or invalid similarityRate`);
  }

  // Strictly check backend authoritative flag; DO NOT guess or infer from similarityRate
  const flagVal = parseStrictBoolean(raw.flagged ?? raw.isFlagged);
  if (flagVal === undefined) {
    throw new Error(`Malformed plagiarism record (ID: ${pairId}): missing or invalid authoritative boolean flag`);
  }

  const extractedExamName = typeof raw.examTitle === 'string' && raw.examTitle.trim()
    ? raw.examTitle.trim()
    : (typeof raw.examName === 'string' && raw.examName.trim() ? raw.examName.trim() : 'Assessment Evaluation');
  const extractedClassName = typeof raw.className === 'string' && raw.className.trim() ? raw.className.trim() : 'Assigned Cohort';
  const extractedExamId = typeof raw.examId === 'string' && raw.examId.trim() ? raw.examId.trim() : undefined;

  const adjudicationStatus = (typeof raw.adjudicationStatus === 'string' ? raw.adjudicationStatus : 'PENDING') as
    | 'PENDING'
    | 'FLAG_CONFIRMED'
    | 'FALSE_POSITIVE'
    | 'HEARING_SCHEDULED';

  return {
    recordId: pairId,
    examName: extractedExamName,
    className: extractedClassName,
    pair: {
      pairId,
      submissionAId,
      submissionBId,
      studentAId: String(raw.studentAId || ''),
      studentBId: String(raw.studentBId || ''),
      studentAName: typeof raw.studentAName === 'string' ? raw.studentAName : undefined,
      studentBName: typeof raw.studentBName === 'string' ? raw.studentBName : undefined,
      similarityRate: simRate,
      isFlagged: flagVal,
      examId: extractedExamId,
      examTitle: extractedExamName,
      className: extractedClassName,
      adjudicationStatus,
      adjudicationNotes: typeof raw.adjudicationNotes === 'string' ? raw.adjudicationNotes : undefined,
      adjudicatedBy: typeof raw.adjudicatedBy === 'string' ? raw.adjudicatedBy : undefined,
      adjudicatedAt: typeof raw.adjudicatedAt === 'string' ? raw.adjudicatedAt : undefined,
    },
    evidence: Array.isArray(raw.evidence) ? raw.evidence : [],
  };
}

/**
 * Fetch list of plagiarism records for teacher
 * Normalizes backend SimilarityPairDto records into frontend PlagiarismRecord interface.
 * Throws explicit error on malformed response shape or invalid record contents.
 */
export async function getPlagiarismRecords(): Promise<PlagiarismRecord[]> {
  const response = await apiClient.get<unknown>('/teacher/plagiarism');
  const rawData = response.data;
  if (!Array.isArray(rawData)) {
    throw new Error('Malformed API response: expected an array of plagiarism records from backend');
  }

  return rawData.map((item: unknown, index: number) => {
    try {
      return normalizeSimilarityPair(item);
    } catch (err) {
      throw new Error(`Invalid record at index ${index}: ${(err as Error).message}`);
    }
  });
}

/**
 * Fetch specific plagiarism record detail
 */
export async function getPlagiarismRecordDetail(recordId: string): Promise<PlagiarismRecord> {
  const response = await apiClient.get<PlagiarismRecord>(`/teacher/plagiarism/${recordId}`);
  return response.data;
}

/**
 * Fetch source code comparison data (Authorized for Teacher)
 */
export async function getPlagiarismComparison(recordId: string): Promise<CodeComparisonData> {
  const response = await apiClient.get<CodeComparisonData>(`/teacher/plagiarism/${recordId}/compare`);
  return response.data;
}

/**
 * Submit faculty adjudication decision and notes for a comparison pair
 */
export async function adjudicatePlagiarismPair(
  pairId: string,
  data: import('../types').AdjudicationRequest
): Promise<import('../types').AdjudicationResponse> {
  const response = await apiClient.post<import('../types').AdjudicationResponse>(
    `/teacher/plagiarism/${pairId}/adjudicate`,
    data
  );
  return response.data;
}

/**
 * Fetch student plagiarism details (Safe read-only data)
 */
export async function getStudentPlagiarismDetail(submissionId: string): Promise<StudentPlagiarismDetail> {
  const response = await apiClient.get<StudentPlagiarismDetail>(`/student/plagiarism/${submissionId}`);
  return response.data;
}

/**
 * Fetch hierarchical AST tree comparison data (Authorized for Teacher)
 */
export async function getPlagiarismAstTree(recordId: string): Promise<AstTreeComparison> {
  const response = await apiClient.get<AstTreeComparison>(`/teacher/plagiarism/${recordId}/ast-tree`);
  return response.data;
}

