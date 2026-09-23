import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { adminApi, AdminClass, AdminExam, AdminUser } from '../api/admin.api';

// --- Dashboard ---
export function useAdminDashboardMetrics() {
  return useQuery({
    queryKey: ['admin', 'metrics'],
    queryFn: adminApi.getMetrics,
  });
}

// --- Users ---
export function useAdminUsers() {
  return useQuery({
    queryKey: ['admin', 'users'],
    queryFn: adminApi.getUsers,
  });
}

export function useAdminUser(id: string) {
  return useQuery({
    queryKey: ['admin', 'users', id],
    queryFn: () => adminApi.getUser(id),
    enabled: !!id,
  });
}

export function useUpdateAdminUser() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: Partial<AdminUser> }) => adminApi.updateUser(id, data),
    onSuccess: (data, variables) => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'users'] });
      queryClient.invalidateQueries({ queryKey: ['admin', 'users', variables.id] });
    },
  });
}

// --- Classes ---
export function useAdminClasses() {
  return useQuery({
    queryKey: ['admin', 'classes'],
    queryFn: adminApi.getClasses,
  });
}

export function useAdminClass(id: string) {
  return useQuery({
    queryKey: ['admin', 'classes', id],
    queryFn: () => adminApi.getClass(id),
    enabled: !!id,
  });
}

export function useCreateAdminClass() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: Omit<AdminClass, 'id' | 'studentCount'>) => adminApi.createClass(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'classes'] });
    },
  });
}

export function useUpdateAdminClass() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: Partial<AdminClass> }) => adminApi.updateClass(id, data),
    onSuccess: (data, variables) => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'classes'] });
      queryClient.invalidateQueries({ queryKey: ['admin', 'classes', variables.id] });
    },
  });
}

// --- Exams ---
export function useAdminExams() {
  return useQuery({
    queryKey: ['admin', 'exams'],
    queryFn: adminApi.getExams,
  });
}

export function useAdminExam(id: string) {
  return useQuery({
    queryKey: ['admin', 'exams', id],
    queryFn: () => adminApi.getExam(id),
    enabled: !!id,
  });
}

export function useCreateAdminExam() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: Omit<AdminExam, 'id' | 'status'>) => adminApi.createExam(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'exams'] });
    },
  });
}

export function useUpdateAdminExam() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: Partial<AdminExam> }) => adminApi.updateExam(id, data),
    onSuccess: (data, variables) => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'exams'] });
      queryClient.invalidateQueries({ queryKey: ['admin', 'exams', variables.id] });
    },
  });
}
