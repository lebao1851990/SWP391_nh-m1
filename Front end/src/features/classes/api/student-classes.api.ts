import { apiClient } from '@/lib/api-client';

export interface StudentClassResponse {
  id: string;
  name: string;
  code: string;
  semester?: string;
  teacherId?: string;
  createdAt?: string;
  studentCount?: number;
}

export const studentClassesApi = {
  getMyClasses: async (): Promise<StudentClassResponse[]> => {
    const response = await apiClient.get<StudentClassResponse[]>('/student/classes');
    return response.data;
  },

  getClassById: async (id: string): Promise<StudentClassResponse> => {
    const response = await apiClient.get<StudentClassResponse>(`/student/classes/${id}`);
    return response.data;
  },
};

