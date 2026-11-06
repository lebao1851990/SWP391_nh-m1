import { apiClient } from '@/lib/api-client';

export type SandboxStatus = 
  | 'SUCCESS'
  | 'COMPILE_ERROR'
  | 'RUNTIME_ERROR'
  | 'TIMEOUT'
  | 'INTERNAL_ERROR'
  | 'COMPILE_ONLY_SUCCESS'
  | 'AMBIGUOUS_MAIN_METHOD'
  | 'CAPACITY_EXCEEDED';

export interface SandboxResult {
  status: SandboxStatus;
  stdout?: string | null;
  stderr?: string | null;
  compilerOutput?: string | null;
  exitCode?: number | null;
  executionTimeMs: number;
  truncated: boolean;
}

export interface SandboxExecuteRequest {
  sourceCode: string;
}

export const sandboxApi = {
  executeCode: async (sourceCode: string): Promise<SandboxResult> => {
    const response = await apiClient.post<SandboxResult>('/student/sandbox/execute', { sourceCode });
    return response.data;
  },
};

