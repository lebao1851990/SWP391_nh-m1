import { apiClient } from '@/lib/api-client';
import { ScoringDetail, SaveScoringPayload, StudentSubmissionResult } from '../types';

/**
 * FETCH TEACHER SCORING DATA
 * This endpoint is an Integration Boundary.
 * Returns the backend-authorized scoring structure.
 */
export async function getTeacherScoring(submissionId: string): Promise<ScoringDetail> {
  // If backend is offline, this request will naturally fail and trigger the Error Boundary.
  // We do NOT mock successful responses.
  const response = await apiClient.get<ScoringDetail>(`/teacher/submissions/${submissionId}/scoring`);
  return response.data;
}

/**
 * UPDATE TEACHER SCORING DATA
 * This endpoint is an Integration Boundary.
 * Submits the criteria scores to the backend, which will compute and return the final official score.
 */
export async function updateTeacherScoring(submissionId: string, payload: SaveScoringPayload): Promise<ScoringDetail> {
  const response = await apiClient.put<ScoringDetail>(`/teacher/submissions/${submissionId}/scoring`, payload);
  return response.data;
}

/**
 * FETCH STUDENT SCORING RESULT
 * Read-only endpoint strictly authorized by backend.
 */
export async function getStudentResult(submissionId: string): Promise<StudentSubmissionResult> {
  const response = await apiClient.get<StudentSubmissionResult>(`/student/submissions/${submissionId}/result`);
  return response.data;
}
