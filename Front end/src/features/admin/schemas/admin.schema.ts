import { z } from 'zod';

export const userSchema = z.object({
  name: z.string().min(2, 'Name must be at least 2 characters'),
  email: z.string().email('Invalid email address'),
  role: z.enum(['ADMIN', 'TEACHER', 'STUDENT']),
  department: z.string().optional(),
});

export const classSchema = z.object({
  name: z.string().min(2, 'Class name is required'),
  code: z.string().min(2, 'Course code is required'),
  semester: z.string().min(2, 'Semester is required'),
  teacherId: z.string().min(1, 'Teacher assignment is required'),
});

export const examSchema = z.object({
  title: z.string().min(2, 'Exam title is required'),
  classId: z.string().min(1, 'Class assignment is required'),
  date: z.string().min(1, 'Date is required'),
  durationMinutes: z.number().min(1, 'Duration must be greater than 0'),
});

export type UserFormData = z.infer<typeof userSchema>;
export type ClassFormData = z.infer<typeof classSchema>;
export type ExamFormData = z.infer<typeof examSchema>;
