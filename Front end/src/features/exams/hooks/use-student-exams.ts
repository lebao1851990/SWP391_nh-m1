import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { studentExamsApi } from '../api/student-exams.api';

export const studentExamKeys = {
  all: ['student-exams'] as const,
  lists: () => [...studentExamKeys.all, 'list'] as const,
  list: (filters: string) => [...studentExamKeys.lists(), { filters }] as const,
  details: () => [...studentExamKeys.all, 'detail'] as const,
  detail: (id: string) => [...studentExamKeys.details(), id] as const,
};

export function useStudentExams() {
  return useQuery({
    queryKey: studentExamKeys.lists(),
    queryFn: () => studentExamsApi.getStudentExams(),
  });
}

export function useStudentExam(id: string) {
  return useQuery({
    queryKey: studentExamKeys.detail(id),
    queryFn: () => studentExamsApi.getStudentExam(id),
    enabled: !!id,
  });
}

export function useSubmitExam() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ examId, sourceCode }: { examId: string; sourceCode: string }) =>
      studentExamsApi.submitExam(examId, sourceCode),
    onSuccess: (_, variables) => {
      // Invalidate both the list and the specific exam detail so they show as submitted
      queryClient.invalidateQueries({ queryKey: studentExamKeys.lists() });
      queryClient.invalidateQueries({ queryKey: studentExamKeys.detail(variables.examId) });
    },
  });
}
