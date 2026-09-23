import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { getStudentAppeals, getStudentAppealDetail, createStudentAppeal } from '../api';
import { AppealFormData } from '../schemas';
import { Appeal } from '../types';
import { AxiosError } from 'axios';

export function useStudentAppeals() {
  return useQuery({
    queryKey: ['appeals', 'student', 'list'],
    queryFn: getStudentAppeals,
    retry: false, // Don't retry since it's an integration boundary that might 401/403/404 quickly
  });
}

export function useStudentAppeal(id: string) {
  return useQuery({
    queryKey: ['appeals', 'student', 'detail', id],
    queryFn: () => getStudentAppealDetail(id),
    retry: false,
    enabled: !!id,
  });
}

export function useCreateAppeal() {
  const queryClient = useQueryClient();

  return useMutation<Appeal, AxiosError, AppealFormData>({
    mutationFn: (data: AppealFormData) => createStudentAppeal(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['appeals', 'student', 'list'] });
    },
  });
}
