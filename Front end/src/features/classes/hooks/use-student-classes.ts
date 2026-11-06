import { useQuery } from '@tanstack/react-query';
import { studentClassesApi } from '../api/student-classes.api';

export function useStudentClasses() {
  return useQuery({
    queryKey: ['student-classes'] as const,
    queryFn: studentClassesApi.getMyClasses,
  });
}

export function useStudentClassDetail(id: string) {
  return useQuery({
    queryKey: ['student-classes', id] as const,
    queryFn: () => studentClassesApi.getClassById(id),
    enabled: !!id,
  });
}

