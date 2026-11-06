'use client';

import { useCallback } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useWebSocket } from './use-websocket';
import { SubmissionStatusPayload, WebSocketEvent } from '../types';
import { toast } from 'sonner';

/**
 * Hook providing real-time submission status updates via WebSocket with automatic TanStack Query cache invalidation.
 * Exposes a fallback polling interval when WebSocket connection is unavailable.
 */
export function useSubmissionRealtime(submissionId?: string) {
  const queryClient = useQueryClient();

  const handleSubmissionEvent = useCallback(
    (event: WebSocketEvent<SubmissionStatusPayload>) => {
      const payload = event.payload;
      if (!payload) return;

      // Invalidate queries so UI refreshes with exact canonical data from REST API
      queryClient.invalidateQueries({ queryKey: ['student-submission', payload.submissionId] });
      queryClient.invalidateQueries({ queryKey: ['student-submissions'] });
      queryClient.invalidateQueries({ queryKey: ['teacher-submissions'] });
      queryClient.invalidateQueries({ queryKey: ['studentResult', payload.submissionId] });
      queryClient.invalidateQueries({ queryKey: ['studentResult'] });
      queryClient.invalidateQueries({ queryKey: ['teacherScoring', payload.submissionId] });
      queryClient.invalidateQueries({ queryKey: ['scoring'] });

      if (payload.status === 'COMPLETED') {
        toast.success(`Chấm điểm hoàn tất: ${payload.executionStatus || 'Thành công'}`);
      } else if (payload.status === 'FAILED') {
        toast.error(`Chấm điểm thất bại: ${payload.executionStatus || 'Lỗi hệ thống'}`);
      }
    },
    [queryClient]
  );

  const channel = submissionId ? `submission:${submissionId}` : null;
  const { status, isConnected } = useWebSocket<SubmissionStatusPayload>(channel, handleSubmissionEvent);

  // If WebSocket is disconnected or connecting, fall back to conservative polling (every 4000ms)
  const fallbackRefetchInterval = isConnected ? false : 4000;

  return {
    status,
    isConnected,
    fallbackRefetchInterval,
  };
}

