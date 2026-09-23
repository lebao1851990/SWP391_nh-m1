import { apiClient } from '@/lib/api-client';
import { Appeal } from '../types';
import { AppealFormData } from '../schemas';

/**
 * INTEGRATION BOUNDARY - UNCONFIRMED BACKEND CONTRACT
 * Fetch list of appeals for the authenticated student
 */
export async function getStudentAppeals(): Promise<Appeal[]> {
  const response = await apiClient.get<Appeal[]>('/student/appeals');
  return response.data;
}

/**
 * INTEGRATION BOUNDARY - UNCONFIRMED BACKEND CONTRACT
 * Fetch specific appeal detail
 */
export async function getStudentAppealDetail(appealId: string): Promise<Appeal> {
  const response = await apiClient.get<Appeal>(`/student/appeals/${appealId}`);
  return response.data;
}

/**
 * INTEGRATION BOUNDARY - UNCONFIRMED BACKEND CONTRACT
 * Create a new appeal
 */
export async function createStudentAppeal(data: AppealFormData): Promise<Appeal> {
  const response = await apiClient.post<Appeal>('/student/appeals', data);
  return response.data;
}
