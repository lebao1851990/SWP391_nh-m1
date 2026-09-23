export type AppealStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'ESCALATED';

export interface Appeal {
  id: string; // opaque identifier like apl_72ks91m
  submissionId: string;
  examName: string;
  status: AppealStatus;
  basis: string;
  explanation: string;
  createdAt: string;
  resolvedAt?: string;
  // If backend provides other fields they can be added here
}
