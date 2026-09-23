import { z } from 'zod';

export const SaveScoringPayloadSchema = z.object({
  criteriaScores: z.array(
    z.object({
      criterionId: z.string().min(1, 'Criterion ID is required'),
      // We only validate that the score is a positive number.
      // We DO NOT enforce a strict max (e.g. max(3)) because the backend contract
      // hasn't confirmed universal maximums for all rubrics.
      score: z.number().min(0, 'Score cannot be negative'),
    })
  ),
  instructorEvaluationComments: z.string().optional(),
  
  // Optional override fields. Backend contract is UNCONFIRMED.
  overrideTotalScore: z.number().min(0).optional(),
  overrideRationale: z.string().optional(),
});

export type SaveScoringPayloadType = z.infer<typeof SaveScoringPayloadSchema>;
