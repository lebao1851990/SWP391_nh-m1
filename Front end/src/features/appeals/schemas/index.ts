import { z } from 'zod';

export const createAppealSchema = z.object({
  submissionId: z.string().min(1, 'Submission ID is required'),
  basis: z.string().min(1, 'Please select a basis for your appeal'),
  explanation: z
    .string()
    .min(20, 'Nội dung giải trình phải có ít nhất 20 ký tự')
    .max(1000, 'Nội dung giải trình không được vượt quá 1000 ký tự'),
});

export type AppealFormData = z.infer<typeof createAppealSchema>;
