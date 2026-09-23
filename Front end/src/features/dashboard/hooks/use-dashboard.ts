import { useQuery } from '@tanstack/react-query';
import { dashboardApi } from '../api/dashboard.api';

export const DASHBOARD_QUERY_KEYS = {
  student: ['student-dashboard'] as const,
};

export function useStudentDashboard() {
  return useQuery({
    queryKey: DASHBOARD_QUERY_KEYS.student,
    queryFn: dashboardApi.getStudentDashboard,
  });
}
