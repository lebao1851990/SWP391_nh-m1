import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { teacherExamsApi, CreateExamPayload, UpdateExamPayload } from '../api/teacher-exams.api';

export const TEACHER_EXAMS_QUERY_KEYS = {
  all: ['teacher-exams'] as const,
  classes: ['teacher-classes'] as const,
  detail: (id: string) => ['teacher-exams', id] as const,
};

export function useTeacherClasses() {
  return useQuery({
    queryKey: TEACHER_EXAMS_QUERY_KEYS.classes,
    queryFn: teacherExamsApi.getMyClasses,
  });
}

export function useCreateTeacherClass() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: { name: string; code: string; semester?: string }) =>
      teacherExamsApi.createClass(payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: TEACHER_EXAMS_QUERY_KEYS.classes });
    },
  });
}

export function useTeacherExams() {
  return useQuery({
    queryKey: TEACHER_EXAMS_QUERY_KEYS.all,
    queryFn: teacherExamsApi.getMyExams,
  });
}

export function useTeacherExamDetail(id: string) {
  return useQuery({
    queryKey: TEACHER_EXAMS_QUERY_KEYS.detail(id),
    queryFn: () => teacherExamsApi.getExamById(id),
    enabled: !!id,
  });
}

export function useCreateExam() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreateExamPayload) => teacherExamsApi.createExam(payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: TEACHER_EXAMS_QUERY_KEYS.all });
    },
  });
}

export function useUpdateExam() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: UpdateExamPayload }) =>
      teacherExamsApi.updateExam(id, payload),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: TEACHER_EXAMS_QUERY_KEYS.all });
      queryClient.invalidateQueries({ queryKey: TEACHER_EXAMS_QUERY_KEYS.detail(variables.id) });
    },
  });
}

export function useDeleteExam() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => teacherExamsApi.deleteExam(id),
    onSuccess: (_, id) => {
      queryClient.invalidateQueries({ queryKey: TEACHER_EXAMS_QUERY_KEYS.all });
      queryClient.invalidateQueries({ queryKey: TEACHER_EXAMS_QUERY_KEYS.detail(id) });
    },
  });
}

export function useTeacherExamSubmissions(id: string) {
  return useQuery({
    queryKey: [...TEACHER_EXAMS_QUERY_KEYS.detail(id), 'submissions'],
    queryFn: () => teacherExamsApi.getExamSubmissions(id),
    enabled: !!id,
    refetchInterval: (query) => {
      const submissions = query.state.data as Array<{ status?: string; executionStatus?: string }> | undefined;
      const isProcessing = submissions?.some(
        s => s.status === 'PENDING' || s.status === 'PROCESSING' || s.executionStatus === 'PENDING' || s.executionStatus === 'PROCESSING'
      );
      return isProcessing ? 3000 : false;
    },
  });
}

export function useTeacherSubmissionDetail(id: string) {
  return useQuery({
    queryKey: ['teacher-submissions', id] as const,
    queryFn: () => teacherExamsApi.getSubmissionById(id),
    enabled: !!id,
  });
}

export function useTeacherSubmissions() {
  return useQuery({
    queryKey: ['teacher-submissions'] as const,
    queryFn: teacherExamsApi.getMySubmissions,
  });
}

export function useTeacherExamTestCases(examId: string) {
  return useQuery({
    queryKey: ['teacher-exam-test-cases', examId],
    queryFn: () => teacherExamsApi.getExamTestCases(examId),
    enabled: !!examId,
  });
}

export function useCreateTestCase() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ examId, payload }: { examId: string; payload: import('../api/teacher-exams.api').CreateTestCasePayload }) =>
      teacherExamsApi.createTestCase(examId, payload),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: ['teacher-exam-test-cases', variables.examId] });
    },
  });
}

export function useUpdateTestCase() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ examId, id, payload }: { examId: string; id: string; payload: import('../api/teacher-exams.api').UpdateTestCasePayload }) =>
      teacherExamsApi.updateTestCase(examId, id, payload),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: ['teacher-exam-test-cases', variables.examId] });
    },
  });
}

export function useDeleteTestCase() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ examId, id }: { examId: string; id: string }) =>
      teacherExamsApi.deleteTestCase(examId, id),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: ['teacher-exam-test-cases', variables.examId] });
    },
  });
}

