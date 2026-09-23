import { useQuery } from '@tanstack/react-query';
import { teacherExamsApi } from '../api/teacher-exams.api';

export const TEACHER_EXAMS_QUERY_KEYS = {
  all: ['teacher-exams'] as const,
  detail: (id: string) => ['teacher-exams', id] as const,
};

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
