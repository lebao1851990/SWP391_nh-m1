package com.codeassess.websocket.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class WebSocketSessionRegistry {

    // Channel -> Set of active WebSocketSessions
    private final Map<String, Set<WebSocketSession>> channelSubscribers = new ConcurrentHashMap<>();

    // SessionId -> Set of channel names (for O(1) teardown on disconnect)
    private final Map<String, Set<String>> sessionChannels = new ConcurrentHashMap<>();

    // Active session index: SessionId -> WebSocketSession
    private final Map<String, WebSocketSession> activeSessions = new ConcurrentHashMap<>();

    public void registerSession(WebSocketSession session) {
        if (session != null && session.getId() != null) {
            activeSessions.put(session.getId(), session);
        }
    }

    public void addSubscription(String channel, WebSocketSession session) {
        if (channel == null || session == null) return;
        String cleanChannel = channel.trim();

        channelSubscribers.computeIfAbsent(cleanChannel, k -> ConcurrentHashMap.newKeySet()).add(session);
        sessionChannels.computeIfAbsent(session.getId(), k -> ConcurrentHashMap.newKeySet()).add(cleanChannel);

        log.debug("Session {} subscribed to channel {}", session.getId(), cleanChannel);
    }

    public void removeSubscription(String channel, WebSocketSession session) {
        if (channel == null || session == null) return;
        String cleanChannel = channel.trim();

        Set<WebSocketSession> subs = channelSubscribers.get(cleanChannel);
        if (subs != null) {
            subs.remove(session);
            if (subs.isEmpty()) {
                channelSubscribers.remove(cleanChannel);
            }
        }

        Set<String> channels = sessionChannels.get(session.getId());
        if (channels != null) {
            channels.remove(cleanChannel);
        }
    }

    public void removeSession(WebSocketSession session) {
        if (session == null || session.getId() == null) return;
        String sessionId = session.getId();

        activeSessions.remove(sessionId);
        Set<String> subscribedChannels = sessionChannels.remove(sessionId);
        if (subscribedChannels != null) {
            for (String ch : subscribedChannels) {
                Set<WebSocketSession> subs = channelSubscribers.get(ch);
                if (subs != null) {
                    subs.remove(session);
                    if (subs.isEmpty()) {
                        channelSubscribers.remove(ch);
                    }
                }
            }
        }
        log.debug("Session {} removed and subscriptions cleaned up", sessionId);
    }

    public Set<WebSocketSession> getSubscribers(String channel) {
        if (channel == null) return Collections.emptySet();
        Set<WebSocketSession> subs = channelSubscribers.get(channel.trim());
        return (subs != null) ? Collections.unmodifiableSet(subs) : Collections.emptySet();
    }

    public int getActiveSessionCount() {
        return activeSessions.size();
    }

    public int getSubscribedChannelCount() {
        return channelSubscribers.size();
    }

    public boolean isSessionSubscribed(String channel, WebSocketSession session) {
        if (channel == null || session == null) return false;
        Set<WebSocketSession> subs = channelSubscribers.get(channel.trim());
        return subs != null && subs.contains(session);
    }
}

