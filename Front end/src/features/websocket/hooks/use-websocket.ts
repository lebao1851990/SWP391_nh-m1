'use client';

import { useEffect, useState } from 'react';
import { wsService } from '../services/websocket.service';
import { ConnectionStatus, WebSocketEvent } from '../types';

export function useWebSocket<T = any>(
  channel: string | null | undefined,
  onEvent?: (event: WebSocketEvent<T>) => void
) {
  const [status, setStatus] = useState<ConnectionStatus>(wsService.getStatus());

  useEffect(() => {
    const unsubStatus = wsService.onStatusChange((s) => setStatus(s));
    return () => unsubStatus();
  }, []);

  useEffect(() => {
    if (!channel || !onEvent) return;

    const unsubChannel = wsService.subscribe<T>(channel, onEvent);
    return () => {
      unsubChannel();
    };
  }, [channel, onEvent]);

  return {
    status,
    isConnected: status === 'CONNECTED',
    isConnecting: status === 'CONNECTING' || status === 'RECONNECTING',
  };
}

