import { apiClient } from '@/lib/api-client';

export interface ExamResponse {
  id: string;
  title: string;
  description: string;
  durationMinutes: number;
  duration?: number;
  examDate?: string;
  startTime: string;
  endTime: string;
  classId?: string;
  classIds?: string[];
  courseClassId: string;
  courseCode?: string;
  courseClassCode: string;
  classCodes?: string[];
  status: 'upcoming' | 'active' | 'completed' | 'missed';
  score?: number;
}

function normalizeExam(data: Record<string, unknown>): ExamResponse {
  const startTime = (data.examDate as string) || (data.startTime as string) || (data.createdAt as string) || new Date().toISOString();
  const duration = Number(data.duration || data.durationMinutes) || 60;
  let endTime = data.endTime as string;
  if (!endTime && startTime) {
    const end = new Date(startTime);
    end.setMinutes(end.getMinutes() + duration);
    endTime = end.toISOString();
  }
  const courseCode = (data.courseCode as string) || (data.courseClassCode as string) || 'PRJ301';
  const statusStr = String(data.status || 'active').toLowerCase();
  const validStatus: ExamResponse['status'] = ['upcoming', 'active', 'completed', 'missed'].includes(statusStr)
    ? (statusStr as ExamResponse['status'])
    : 'active';

  return {
    id: String(data.id || ''),
    title: String(data.title || ''),
    description: (data.description as string) || `Assessment for ${courseCode}. Implement and submit your Java solution.`,
    duration,
    durationMinutes: duration,
    examDate: (data.examDate as string) || startTime,
    startTime,
    endTime,
    classId: (data.classId as string) || (data.courseClassId as string) || '',
    classIds: Array.isArray(data.classIds) ? (data.classIds as string[]) : [],
    courseClassId: (data.classId as string) || (data.courseClassId as string) || '',
    courseCode,
    courseClassCode: courseCode,
    classCodes: Array.isArray(data.classCodes) ? (data.classCodes as string[]) : [],
    status: validStatus,
    score: data.score !== undefined ? Number(data.score) : undefined,
  };
}

export const studentExamsApi = {
  getStudentExams: async (): Promise<ExamResponse[]> => {
    const response = await apiClient.get('/student/exams');
    const items = Array.isArray(response.data) ? response.data : [];
    return items.map((item) => normalizeExam(item as Record<string, unknown>));
  },

  getStudentExam: async (id: string): Promise<ExamResponse> => {
    const response = await apiClient.get(`/student/exams/${id}`);
    return normalizeExam(response.data as Record<string, unknown>);
  },

  submitExam: async (examId: string, sourceCode: string): Promise<{ id: string; status?: string; message?: string }> => {
    const response = await apiClient.post(`/student/exams/${examId}/submissions`, { sourceCode });
    return response.data;
  },
};
