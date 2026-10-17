import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  getStudentAppeals,
  getStudentAppealDetail,
  createStudentAppeal,
  getTeacherAppeals,
  getTeacherAppealDetail,
  resolveTeacherAppeal
} from '../api';
import { AppealFormData } from '../schemas';
import { Appeal, AppealResolvePayload } from '../types';
import { AxiosError } from 'axios';

export function useStudentAppeals() {
  return useQuery({
    queryKey: ['appeals', 'student', 'list'],
    queryFn: getStudentAppeals,
    retry: false,
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

export function useTeacherAppeals() {
  return useQuery({
    queryKey: ['appeals', 'teacher', 'list'],
    queryFn: getTeacherAppeals,
    retry: false,
  });
}

export function useTeacherAppeal(id: string) {
  return useQuery({
    queryKey: ['appeals', 'teacher', 'detail', id],
    queryFn: () => getTeacherAppealDetail(id),
    retry: false,
    enabled: !!id,
  });
}

export function useResolveAppeal(appealId: string) {
  const queryClient = useQueryClient();

  return useMutation<Appeal, AxiosError, AppealResolvePayload>({
    mutationFn: (payload: AppealResolvePayload) => resolveTeacherAppeal(appealId, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['appeals', 'teacher'] });
      queryClient.invalidateQueries({ queryKey: ['appeals', 'student'] });
    },
  });
}
