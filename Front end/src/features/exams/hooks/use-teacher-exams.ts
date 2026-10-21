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
    retry: 1,
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

export function useTeacherExamSubmissions(id: string) {
  return useQuery({
    queryKey: [...TEACHER_EXAMS_QUERY_KEYS.detail(id), 'submissions'],
    queryFn: () => teacherExamsApi.getExamSubmissions(id),
    enabled: !!id,
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
