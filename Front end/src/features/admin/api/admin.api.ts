import { apiClient } from '@/lib/api-client';
import { UserRole } from '@/stores/session.store';

export interface AdminUser {
  id: string;
  name: string;
  email: string;
  role: UserRole;
  department?: string;
  createdAt: string;
}

export interface AdminClass {
  id: string;
  name: string;
  code: string;
  semester: string;
  teacherId: string;
  studentCount: number;
}

export interface AdminExam {
  id: string;
  title: string;
  classId: string;
  date: string;
  durationMinutes: number;
  status: 'upcoming' | 'active' | 'completed';
}

export interface AdminDashboardMetrics {
  totalUsers: number;
  totalClasses: number;
  totalExams: number;
  activeScans: number;
}

export const adminApi = {
  // Dashboard
  getMetrics: async (): Promise<AdminDashboardMetrics> => {
    const response = await apiClient.get('/admin/metrics');
    return response.data;
  },

  // Users
  getUsers: async (): Promise<AdminUser[]> => {
    const response = await apiClient.get('/admin/users');
    return response.data;
  },
  getUser: async (id: string): Promise<AdminUser> => {
    const response = await apiClient.get(`/admin/users/${id}`);
    return response.data;
  },
  updateUser: async (id: string, data: Partial<AdminUser>): Promise<AdminUser> => {
    const response = await apiClient.put(`/admin/users/${id}`, data);
    return response.data;
  },

  // Classes
  getClasses: async (): Promise<AdminClass[]> => {
    const response = await apiClient.get('/admin/classes');
    return response.data;
  },
  getClass: async (id: string): Promise<AdminClass> => {
    const response = await apiClient.get(`/admin/classes/${id}`);
    return response.data;
  },
  createClass: async (data: Omit<AdminClass, 'id' | 'studentCount'>): Promise<AdminClass> => {
    const response = await apiClient.post('/admin/classes', data);
    return response.data;
  },
  updateClass: async (id: string, data: Partial<AdminClass>): Promise<AdminClass> => {
    const response = await apiClient.put(`/admin/classes/${id}`, data);
    return response.data;
  },

  // Exams
  getExams: async (): Promise<AdminExam[]> => {
    const response = await apiClient.get('/admin/exams');
    return response.data;
  },
  getExam: async (id: string): Promise<AdminExam> => {
    const response = await apiClient.get(`/admin/exams/${id}`);
    return response.data;
  },
  createExam: async (data: Omit<AdminExam, 'id' | 'status'>): Promise<AdminExam> => {
    const response = await apiClient.post('/admin/exams', data);
    return response.data;
  },
  updateExam: async (id: string, data: Partial<AdminExam>): Promise<AdminExam> => {
    const response = await apiClient.put(`/admin/exams/${id}`, data);
    return response.data;
  },
};
