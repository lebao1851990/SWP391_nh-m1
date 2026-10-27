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
  serverTime?: string;
  isExpired?: boolean;
  classId?: string;
  classIds?: string[];
  courseClassId: string;
  courseCode?: string;
  courseClassCode: string;
  classCodes?: string[];
  status: 'upcoming' | 'active' | 'completed' | 'missed';
  score?: number;
  problemStatement?: string;
  inputSpecification?: string;
  outputSpecification?: string;
  constraints?: string;
  sampleInput?: string;
  sampleOutput?: string;
  programmingLanguage?: string;
  sourceDocumentName?: string;
  rubricCriteria?: { name: string; description?: string; maxScore: number }[];
}

function normalizeExam(data: Record<string, unknown>): ExamResponse {
  const startTime = (data.examDate as string) || (data.startTime as string) || (data.createdAt as string) || new Date().toISOString();
  const rawDuration = Number(data.duration ?? data.durationMinutes);
  const duration = (!isNaN(rawDuration) && rawDuration > 0) ? rawDuration : 90;
  let endTime = data.endTime as string;
  if (!endTime && startTime) {
    const end = new Date(startTime);
    end.setMinutes(end.getMinutes() + duration);
    endTime = end.toISOString();
  }
  const serverTime = (data.serverTime as string) || new Date().toISOString();
  const isExpired = Boolean(data.isExpired) || (endTime ? new Date(serverTime).getTime() >= new Date(endTime).getTime() : false);
  const courseCode = (data.courseCode as string) || (data.courseClassCode as string) || 'PRJ301';
  const statusStr = String(data.status || 'active').toLowerCase();
  const validStatus: ExamResponse['status'] = isExpired
    ? 'completed'
    : ['upcoming', 'active', 'completed', 'missed'].includes(statusStr)
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
    serverTime,
    isExpired,
    classId: (data.classId as string) || (data.courseClassId as string) || '',
    classIds: Array.isArray(data.classIds) ? (data.classIds as string[]) : [],
    courseClassId: (data.classId as string) || (data.courseClassId as string) || '',
    courseCode,
    courseClassCode: courseCode,
    classCodes: Array.isArray(data.classCodes) ? (data.classCodes as string[]) : [],
    status: validStatus,
    score: data.score !== undefined ? Number(data.score) : undefined,
    problemStatement: (data.problemStatement as string) || undefined,
    inputSpecification: (data.inputSpecification as string) || undefined,
    outputSpecification: (data.outputSpecification as string) || undefined,
    constraints: (data.constraints as string) || undefined,
    sampleInput: (data.sampleInput as string) || undefined,
    sampleOutput: (data.sampleOutput as string) || undefined,
    programmingLanguage: (data.programmingLanguage as string) || undefined,
    sourceDocumentName: (data.sourceDocumentName as string) || undefined,
    rubricCriteria: Array.isArray(data.rubricCriteria) ? (data.rubricCriteria as { name: string; description?: string; maxScore: number }[]) : undefined,
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

  submitExamZip: async (examId: string, file: File): Promise<{ id: string; status?: string; message?: string }> => {
    const formData = new FormData();
    formData.append('file', file);
    const response = await apiClient.post(`/student/exams/${examId}/submissions`, formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
    return response.data;
  },
};
