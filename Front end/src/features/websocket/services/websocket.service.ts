import { ConnectionStatus, WebSocketEvent, WebSocketMessageOut } from '../types';

type EventCallback<T = any> = (event: WebSocketEvent<T>) => void;
type StatusListener = (status: ConnectionStatus) => void;

class WebSocketService {
  private socket: WebSocket | null = null;
  private status: ConnectionStatus = 'DISCONNECTED';
  private statusListeners: Set<StatusListener> = new Set();
  
  // Channel -> Set of callback listeners
  private channelSubscriptions: Map<string, Set<EventCallback>> = new Map();
  
  // Deduplication: ring buffer / set of recently processed event IDs
  private seenEventIds: Set<string> = new Set();
  private maxSeenIds = 200;

  // Reconnection state
  private reconnectAttempt = 0;
  private reconnectTimer: NodeJS.Timeout | null = null;
  private pingTimer: NodeJS.Timeout | null = null;
  private isIntentionallyClosed = false;

  constructor() {
    // In browser environment, initialize when imported if window exists
    if (typeof window !== 'undefined') {
      window.addEventListener('online', () => this.handleNetworkOnline());
      window.addEventListener('beforeunload', () => this.disconnect());
    }
  }

  public getStatus(): ConnectionStatus {
    return this.status;
  }

  public onStatusChange(listener: StatusListener): () => void {
    this.statusListeners.add(listener);
    listener(this.status);
    return () => {
      this.statusListeners.delete(listener);
    };
  }

  private setStatus(newStatus: ConnectionStatus) {
    if (this.status !== newStatus) {
      this.status = newStatus;
      this.statusListeners.forEach((l) => l(newStatus));
    }
  }

  public connect(): void {
    if (typeof window === 'undefined') return;
    if (this.socket && (this.socket.readyState === WebSocket.OPEN || this.socket.readyState === WebSocket.CONNECTING)) {
      return; // Already connecting or connected
    }

    this.isIntentionallyClosed = false;
    this.setStatus(this.reconnectAttempt > 0 ? 'RECONNECTING' : 'CONNECTING');

    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const host = window.location.host;
    
    // Prefer environment override if configured; default to current host's /api/ws (handled by Next.js rewrites)
    const wsUrl = process.env.NEXT_PUBLIC_WS_URL || `${protocol}//${host}/api/ws`;

    try {
      this.socket = new WebSocket(wsUrl);

      this.socket.onopen = () => {
        this.setStatus('CONNECTED');
        this.reconnectAttempt = 0;
        this.startHeartbeat();
        this.resubscribeAllChannels();
      };

      this.socket.onmessage = (messageEvent) => {
        this.handleMessage(messageEvent.data);
      };

      this.socket.onerror = (err) => {
        console.warn('[WebSocket] Connection error:', err);
      };

      this.socket.onclose = (closeEvent) => {
        this.stopHeartbeat();
        this.socket = null;
        if (!this.isIntentionallyClosed) {
          this.setStatus('DISCONNECTED');
          this.scheduleReconnect();
        } else {
          this.setStatus('DISCONNECTED');
        }
      };
    } catch (e) {
      console.warn('[WebSocket] Failed establishing socket connection:', e);
      this.scheduleReconnect();
    }
  }

  public disconnect(): void {
    this.isIntentionallyClosed = true;
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
    this.stopHeartbeat();
    if (this.socket) {
      this.socket.close();
      this.socket = null;
    }
    this.setStatus('DISCONNECTED');
  }

  public subscribe<T = any>(channel: string, callback: EventCallback<T>): () => void {
    const clean = channel.trim();
    if (!this.channelSubscriptions.has(clean)) {
      this.channelSubscriptions.set(clean, new Set());
    }
    this.channelSubscriptions.get(clean)!.add(callback);

    // If socket is open, immediately send SUBSCRIBE action frame
    if (this.socket && this.socket.readyState === WebSocket.OPEN) {
      this.sendJson({ action: 'SUBSCRIBE', channel: clean });
    } else {
      this.connect();
    }

    return () => {
      this.unsubscribe(clean, callback);
    };
  }

  public unsubscribe(channel: string, callback: EventCallback): void {
    const clean = channel.trim();
    const subs = this.channelSubscriptions.get(clean);
    if (subs) {
      subs.delete(callback);
      if (subs.size === 0) {
        this.channelSubscriptions.delete(clean);
        if (this.socket && this.socket.readyState === WebSocket.OPEN) {
          this.sendJson({ action: 'UNSUBSCRIBE', channel: clean });
        }
      }
    }
  }

  private handleMessage(data: string) {
    try {
      const msg: WebSocketMessageOut = JSON.parse(data);

      if (msg.type === 'EVENT' && msg.event) {
        const event = msg.event;
        
        // Deduplicate events by eventId
        if (event.eventId) {
          if (this.seenEventIds.has(event.eventId)) {
            return; // Ignore duplicate delivery
          }
          this.seenEventIds.add(event.eventId);
          if (this.seenEventIds.size > this.maxSeenIds) {
            const first = this.seenEventIds.values().next().value;
            if (first) this.seenEventIds.delete(first);
          }
        }

        // Dispatch to registered subscribers
        const subscribers = this.channelSubscriptions.get(event.channel);
        if (subscribers && subscribers.size > 0) {
          subscribers.forEach((cb) => {
            try {
              cb(event);
            } catch (err) {
              console.error('[WebSocket] Error in subscriber callback:', err);
            }
          });
        }
      } else if (msg.type === 'ERROR') {
        console.warn(`[WebSocket] Server returned error: ${msg.code} - ${msg.message}`);
      }
    } catch (e) {
      console.warn('[WebSocket] Error parsing incoming message JSON:', e);
    }
  }

  private resubscribeAllChannels() {
    if (!this.socket || this.socket.readyState !== WebSocket.OPEN) return;
    this.channelSubscriptions.forEach((_, channel) => {
      this.sendJson({ action: 'SUBSCRIBE', channel });
    });
  }

  private sendJson(payload: Record<string, unknown>) {
    if (this.socket && this.socket.readyState === WebSocket.OPEN) {
      this.socket.send(JSON.stringify(payload));
    }
  }

  private scheduleReconnect() {
    if (this.isIntentionallyClosed || this.reconnectTimer) return;

    // Exponential backoff: 1s, 2s, 4s, capped at 10s with 20% jitter
    this.reconnectAttempt++;
    const baseDelay = Math.min(10000, 1000 * Math.pow(2, this.reconnectAttempt - 1));
    const jitter = Math.floor(Math.random() * 500);
    const delay = baseDelay + jitter;

    this.setStatus('RECONNECTING');
    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null;
      this.connect();
    }, delay);
  }

  private startHeartbeat() {
    this.stopHeartbeat();
    this.pingTimer = setInterval(() => {
      if (this.socket && this.socket.readyState === WebSocket.OPEN) {
        this.sendJson({ action: 'PING' });
      }
    }, 25000); // 25s keepalive
  }

  private stopHeartbeat() {
    if (this.pingTimer) {
      clearInterval(this.pingTimer);
      this.pingTimer = null;
    }
  }

  private handleNetworkOnline() {
    if (this.status === 'DISCONNECTED') {
      this.reconnectAttempt = 0;
      this.connect();
    }
  }
}

export const wsService = new WebSocketService();

