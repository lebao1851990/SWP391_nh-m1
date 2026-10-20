import { useQuery } from '@tanstack/react-query';
import { submissionApi } from '../api';

export const useStudentSubmission = (id: string) => {
  return useQuery({
    queryKey: ['student-submission', id],
    queryFn: () => submissionApi.getStudentSubmission(id),
    enabled: !!id,
    refetchInterval: (query) => {
      const data = query.state.data;
      if (data && (data.status === 'PENDING' || data.status === 'PROCESSING')) {
        return 2000; // Poll every 2 seconds while evaluating
      }
      return false;
    },
  });
};

export const useStudentSubmissions = () => {
  return useQuery({
    queryKey: ['student-submissions'],
    queryFn: () => submissionApi.getStudentSubmissions(),
    refetchInterval: (query) => {
      const data = query.state.data;
      if (Array.isArray(data) && data.some(s => s.status === 'PENDING' || s.status === 'PROCESSING')) {
        return 3000; // Poll every 3 seconds if any submission is pending
      }
      return false;
    },
  });
};
