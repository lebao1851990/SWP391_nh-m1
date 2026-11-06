'use client';

import React from 'react';
import { useWebSocket } from '../hooks/use-websocket';
import { Wifi, WifiOff } from 'lucide-react';

export const ConnectionStatusIndicator: React.FC = () => {
  const { status, isConnected, isConnecting } = useWebSocket(null);

  if (isConnected) {
    return (
      <span
        className="inline-flex items-center gap-1.5 px-2 py-0.5 rounded-full text-[11px] font-medium bg-emerald-50 text-emerald-700 border border-emerald-200"
        title="WebSocket live real-time connection active"
      >
        <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse" />
        <Wifi className="w-3 h-3 text-emerald-600" />
        <span>Live</span>
      </span>
    );
  }

  if (isConnecting) {
    return (
      <span
        className="inline-flex items-center gap-1.5 px-2 py-0.5 rounded-full text-[11px] font-medium bg-amber-50 text-amber-700 border border-amber-200"
        title="Reconnecting to real-time WebSocket..."
      >
        <span className="w-1.5 h-1.5 rounded-full bg-amber-500 animate-ping" />
        <span>Reconnecting</span>
      </span>
    );
  }

  return (
    <span
      className="inline-flex items-center gap-1.5 px-2 py-0.5 rounded-full text-[11px] font-medium bg-slate-100 text-slate-600 border border-slate-200"
      title="WebSocket offline; using polling fallback"
    >
      <WifiOff className="w-3 h-3 text-slate-400" />
      <span>Polling fallback</span>
    </span>
  );
};

