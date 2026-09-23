import { z } from 'zod';

export const createAppealSchema = z.object({
  submissionId: z.string().min(1, 'Submission ID is required'),
  basis: z.string().min(1, 'Please select a basis for your appeal'),
  explanation: z.string().min(1, 'Please provide an explanation'),
});

export type AppealFormData = z.infer<typeof createAppealSchema>;
