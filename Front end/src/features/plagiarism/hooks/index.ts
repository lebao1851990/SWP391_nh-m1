import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { getPlagiarismRecords, getPlagiarismRecordDetail, getPlagiarismComparison, getStudentPlagiarismDetail, adjudicatePlagiarismPair } from '../api';
import { AdjudicationRequest } from '../types';

export const PLAGIARISM_QUERY_KEYS = {
  list: () => ['plagiarism', 'list'] as const,
  detail: (id: string) => ['plagiarism', 'detail', id] as const,
  compare: (id: string) => ['plagiarism', 'compare', id] as const,
  student: (id: string) => ['plagiarism', 'student', id] as const,
};

export function usePlagiarismList() {
  return useQuery({
    queryKey: PLAGIARISM_QUERY_KEYS.list(),
    queryFn: getPlagiarismRecords,
    retry: false,
  });
}

export function usePlagiarismDetail(recordId: string) {
  return useQuery({
    queryKey: PLAGIARISM_QUERY_KEYS.detail(recordId),
    queryFn: () => getPlagiarismRecordDetail(recordId),
    retry: false,
  });
}

export function usePlagiarismComparison(recordId: string) {
  return useQuery({
    queryKey: PLAGIARISM_QUERY_KEYS.compare(recordId),
    queryFn: () => getPlagiarismComparison(recordId),
    retry: false,
  });
}

export function useStudentPlagiarismDetail(submissionId: string) {
  return useQuery({
    queryKey: PLAGIARISM_QUERY_KEYS.student(submissionId),
    queryFn: () => getStudentPlagiarismDetail(submissionId),
    retry: false,
  });
}

export function useAdjudicatePlagiarismPair(recordId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: AdjudicationRequest) => adjudicatePlagiarismPair(recordId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: PLAGIARISM_QUERY_KEYS.compare(recordId) });
      queryClient.invalidateQueries({ queryKey: PLAGIARISM_QUERY_KEYS.detail(recordId) });
      queryClient.invalidateQueries({ queryKey: PLAGIARISM_QUERY_KEYS.list() });
    },
  });
}
