import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { getTeacherScoring, updateTeacherScoring, getStudentResult, getTeacherGradeAuditLogs } from '../api';
import { SaveScoringPayload } from '../types';

export const SCORING_QUERY_KEYS = {
  teacherScoring: (id: string) => ['teacherScoring', id] as const,
  teacherAuditLogs: (id: string) => ['teacherAuditLogs', id] as const,
  studentResult: (id: string) => ['studentResult', id] as const,
};

export function useTeacherScoring(submissionId: string) {
  return useQuery({
    queryKey: SCORING_QUERY_KEYS.teacherScoring(submissionId),
    queryFn: () => getTeacherScoring(submissionId),
    retry: false, // Don't retry on 401/403/404
  });
}

export function useTeacherGradeAuditLogs(submissionId: string) {
  return useQuery({
    queryKey: SCORING_QUERY_KEYS.teacherAuditLogs(submissionId),
    queryFn: () => getTeacherGradeAuditLogs(submissionId),
    retry: false,
  });
}

export function useUpdateTeacherScoring(submissionId: string) {
  const queryClient = useQueryClient();
  
  return useMutation({
    mutationFn: (payload: SaveScoringPayload) => updateTeacherScoring(submissionId, payload),
    onSuccess: () => {
      // Upon successful save, invalidate both the scoring and audit log queries
      queryClient.invalidateQueries({ queryKey: SCORING_QUERY_KEYS.teacherScoring(submissionId) });
      queryClient.invalidateQueries({ queryKey: SCORING_QUERY_KEYS.teacherAuditLogs(submissionId) });
    },
  });
}

export function useStudentResult(submissionId: string) {
  return useQuery({
    queryKey: SCORING_QUERY_KEYS.studentResult(submissionId),
    queryFn: () => getStudentResult(submissionId),
    retry: false, // Don't retry on 401/403/404
  });
}

