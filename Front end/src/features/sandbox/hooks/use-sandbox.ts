import { useMutation } from '@tanstack/react-query';
import { sandboxApi, SandboxResult } from '../api/sandbox.api';

export function useExecuteSandbox() {
  return useMutation<SandboxResult, Error, string>({
    mutationFn: (sourceCode: string) => sandboxApi.executeCode(sourceCode),
  });
}

