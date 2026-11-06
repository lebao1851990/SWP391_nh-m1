'use client';

import { useCallback } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useWebSocket } from './use-websocket';
import { PlagiarismMatrixPayload, WebSocketEvent } from '../types';
import { PLAGIARISM_QUERY_KEYS } from '@/features/plagiarism/hooks';
import { toast } from 'sonner';

/**
 * Hook subscribing to real-time exam plagiarism matrix calculation updates.
 */
export function usePlagiarismRealtime(examId?: string) {
  const queryClient = useQueryClient();

  const handlePlagiarismEvent = useCallback(
    (event: WebSocketEvent<PlagiarismMatrixPayload>) => {
      queryClient.invalidateQueries({ queryKey: PLAGIARISM_QUERY_KEYS.list() });
      toast.info('Kết quả phân tích đạo văn vừa được cập nhật');
    },
    [queryClient]
  );

  const channel = examId ? `exam:${examId}` : null;
  const { status, isConnected } = useWebSocket<PlagiarismMatrixPayload>(channel, handlePlagiarismEvent);

  const fallbackRefetchInterval = isConnected ? false : 5000;

  return {
    status,
    isConnected,
    fallbackRefetchInterval,
  };
}

