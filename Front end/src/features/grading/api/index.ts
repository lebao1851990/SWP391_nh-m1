import { apiClient } from '@/lib/api-client';
import {
  TeacherGradingItem,
  TeacherGradingDetail,
  ApproveGradePayload,
  RequestRevisionPayload,
} from '../types';

export const teacherGradingApi = {
  getGradingSubmissions: async (status?: string): Promise<TeacherGradingItem[]> => {
    const params = status && status !== 'ALL' ? { status } : {};
    const res = await apiClient.get<TeacherGradingItem[]>('/teacher/grading', { params });
    return res.data;
  },

  getPendingReviews: async (): Promise<TeacherGradingItem[]> => {
    const res = await apiClient.get<TeacherGradingItem[]>('/teacher/grading/pending');
    return res.data;
  },

  getGradingDetail: async (submissionId: string): Promise<TeacherGradingDetail> => {
    const res = await apiClient.get<TeacherGradingDetail>(`/teacher/grading/${submissionId}`);
    return res.data;
  },

  approveGrade: async (submissionId: string, payload?: ApproveGradePayload): Promise<TeacherGradingDetail> => {
    const res = await apiClient.post<TeacherGradingDetail>(`/teacher/grading/${submissionId}/approve`, payload || {});
    return res.data;
  },

  requestRevision: async (submissionId: string, payload: RequestRevisionPayload): Promise<TeacherGradingDetail> => {
    const res = await apiClient.post<TeacherGradingDetail>(`/teacher/grading/${submissionId}/request-revision`, payload);
    return res.data;
  },

  publishGrade: async (submissionId: string): Promise<TeacherGradingDetail> => {
    const res = await apiClient.post<TeacherGradingDetail>(`/teacher/grading/${submissionId}/publish`);
    return res.data;
  },
};

