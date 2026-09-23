import { apiClient } from '@/lib/api-client';
import { PlagiarismRecord, CodeComparisonData, StudentPlagiarismDetail } from '../types';

/**
 * INTEGRATION BOUNDARY - UNCONFIRMED BACKEND CONTRACT
 * Fetch list of plagiarism records for teacher
 */
export async function getPlagiarismRecords(): Promise<PlagiarismRecord[]> {
  const response = await apiClient.get<PlagiarismRecord[]>('/teacher/plagiarism');
  return response.data;
}

/**
 * INTEGRATION BOUNDARY - UNCONFIRMED BACKEND CONTRACT
 * Fetch specific plagiarism record detail
 */
export async function getPlagiarismRecordDetail(recordId: string): Promise<PlagiarismRecord> {
  const response = await apiClient.get<PlagiarismRecord>(`/teacher/plagiarism/${recordId}`);
  return response.data;
}

/**
 * INTEGRATION BOUNDARY - UNCONFIRMED BACKEND CONTRACT
 * Fetch source code comparison data (Authorized for Teacher)
 */
export async function getPlagiarismComparison(recordId: string): Promise<CodeComparisonData> {
  const response = await apiClient.get<CodeComparisonData>(`/teacher/plagiarism/${recordId}/compare`);
  return response.data;
}

/**
 * INTEGRATION BOUNDARY - UNCONFIRMED BACKEND CONTRACT
 * Fetch student plagiarism details (Safe read-only data)
 */
export async function getStudentPlagiarismDetail(submissionId: string): Promise<StudentPlagiarismDetail> {
  const response = await apiClient.get<StudentPlagiarismDetail>(`/student/plagiarism/${submissionId}`);
  return response.data;
}
