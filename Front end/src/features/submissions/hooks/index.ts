import { useQuery } from '@tanstack/react-query';
import { submissionApi } from '../api';

export const useStudentSubmission = (id: string) => {
  return useQuery({
    queryKey: ['student-submission', id],
    queryFn: () => submissionApi.getStudentSubmission(id),
    enabled: !!id,
  });
};

export const useStudentSubmissions = () => {
  return useQuery({
    queryKey: ['student-submissions'],
    queryFn: () => submissionApi.getStudentSubmissions(),
  });
};

export const useStudentSubmissionFiles = (id: string) => {
  return useQuery({
    queryKey: ['student-submission-files', id],
    queryFn: () => submissionApi.getSubmissionFiles(id),
    enabled: !!id,
  });
};
