import { apiClient } from '@/lib/api-client';

export interface StudentDashboardDto {
  fullName: string;
  email: string;
  activeExamsCount: number;
  submittedCount: number;
  gradedCount: number;
  pendingAppealsCount: number;
  upcomingDeadlines: { title: string; examDate: string; courseCode: string }[];
  recentSubmissions: { id: string; examId: string; submittedAt: string; totalScore: number; status: string }[];
}

export const dashboardApi = {
  getStudentDashboard: async (): Promise<StudentDashboardDto> => {
    const response = await apiClient.get('/student/dashboard');
    return response.data;
  },
};
