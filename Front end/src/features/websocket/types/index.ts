export type ConnectionStatus = 'CONNECTING' | 'CONNECTED' | 'DISCONNECTED' | 'RECONNECTING';

export interface WebSocketEvent<T = Record<string, unknown>> {
  eventId: string;
  version: string;
  timestamp: string;
  channel: string;
  event: string;
  payload: T;
}

export interface WebSocketMessageOut<T = Record<string, unknown>> {
  type: 'CONNECTED' | 'SUBSCRIBED' | 'UNSUBSCRIBED' | 'ERROR' | 'EVENT' | 'PONG';
  code?: 'UNAUTHORIZED' | 'FORBIDDEN' | 'BAD_REQUEST';
  message?: string;
  channel?: string;
  userId?: string;
  role?: string;
  event?: WebSocketEvent<T>;
}

export interface SubmissionStatusPayload {
  submissionId: string;
  examId?: string;
  status: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED' | string;
  executionStatus?: 'SUCCESS' | 'ERROR' | 'TIME_LIMIT_EXCEEDED' | string;
  score?: number;
  totalPoints?: number;
}

export interface PlagiarismMatrixPayload {
  examId: string;
  pairsAnalyzed: number;
}

export interface AppealStatusPayload {
  appealId: string;
  status: 'PENDING' | 'UNDER_REVIEW' | 'APPROVED' | 'REJECTED' | string;
  remarks?: string;
}

export interface QueueProgressPayload {
  queueName: string;
  activeJobs: number;
  completedJobs: number;
  failedJobs: number;
}

