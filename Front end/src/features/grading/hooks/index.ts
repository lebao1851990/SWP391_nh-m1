import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { teacherGradingApi } from '../api';
import { ApproveGradePayload, RequestRevisionPayload } from '../types';

export const GRADING_QUERY_KEYS = {
  list: (status?: string) => ['teacher', 'grading', 'list', status || 'ALL'] as const,
  detail: (id: string) => ['teacher', 'grading', 'detail', id] as const,
};

export function useTeacherGradingList(status?: string) {
  return useQuery({
    queryKey: GRADING_QUERY_KEYS.list(status),
    queryFn: () => teacherGradingApi.getGradingSubmissions(status),
  });
}

export function useTeacherGradingDetail(submissionId: string) {
  return useQuery({
    queryKey: GRADING_QUERY_KEYS.detail(submissionId),
    queryFn: () => teacherGradingApi.getGradingDetail(submissionId),
    enabled: !!submissionId,
  });
}

export function useApproveGrade(submissionId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload?: ApproveGradePayload) => teacherGradingApi.approveGrade(submissionId, payload),
    onSuccess: (data) => {
      queryClient.setQueryData(GRADING_QUERY_KEYS.detail(submissionId), data);
      queryClient.invalidateQueries({ queryKey: ['teacher', 'grading', 'list'] });
    },
  });
}

export function useRequestRevision(submissionId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: RequestRevisionPayload) => teacherGradingApi.requestRevision(submissionId, payload),
    onSuccess: (data) => {
      queryClient.setQueryData(GRADING_QUERY_KEYS.detail(submissionId), data);
      queryClient.invalidateQueries({ queryKey: ['teacher', 'grading', 'list'] });
    },
  });
}

export function usePublishGrade(submissionId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => teacherGradingApi.publishGrade(submissionId),
    onSuccess: (data) => {
      queryClient.setQueryData(GRADING_QUERY_KEYS.detail(submissionId), data);
      queryClient.invalidateQueries({ queryKey: ['teacher', 'grading', 'list'] });
    },
  });
}

