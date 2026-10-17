import { apiClient } from '@/lib/api-client';
import { Appeal, AppealResolvePayload } from '../types';
import { AppealFormData } from '../schemas';

/**
 * Fetch list of appeals for the authenticated student
 */
export async function getStudentAppeals(): Promise<Appeal[]> {
  const response = await apiClient.get<Appeal[]>('/student/appeals');
  return response.data;
}

/**
 * Fetch specific appeal detail for the authenticated student
 */
export async function getStudentAppealDetail(appealId: string): Promise<Appeal> {
  const response = await apiClient.get<Appeal>(`/student/appeals/${appealId}`);
  return response.data;
}

/**
 * Create a new appeal by student
 */
export async function createStudentAppeal(data: AppealFormData): Promise<Appeal> {
  const response = await apiClient.post<Appeal>('/student/appeals', data);
  return response.data;
}

/**
 * Fetch list of appeals for classes taught by the authenticated teacher
 */
export async function getTeacherAppeals(): Promise<Appeal[]> {
  const response = await apiClient.get<Appeal[]>('/teacher/appeals');
  return response.data;
}

/**
 * Fetch specific appeal detail for teacher
 */
export async function getTeacherAppealDetail(appealId: string): Promise<Appeal> {
  const response = await apiClient.get<Appeal>(`/teacher/appeals/${appealId}`);
  return response.data;
}

/**
 * Resolve an appeal (APPROVED or REJECTED) by teacher
 */
export async function resolveTeacherAppeal(appealId: string, payload: AppealResolvePayload): Promise<Appeal> {
  const response = await apiClient.put<Appeal>(`/teacher/appeals/${appealId}/resolve`, payload);
  return response.data;
}
