package com.codeassess.security;

import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry to track active HTTP sessions by sessionId.
 * Enables token-based request association for tab-isolated multi-role testing.
 */
@Component
public class SessionTokenRegistry implements HttpSessionListener {

    private static final Map<String, HttpSession> ACTIVE_SESSIONS = new ConcurrentHashMap<>();

    @Override
    public void sessionCreated(HttpSessionEvent se) {
        if (se != null && se.getSession() != null && se.getSession().getId() != null) {
            ACTIVE_SESSIONS.put(se.getSession().getId(), se.getSession());
        }
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent se) {
        if (se != null && se.getSession() != null && se.getSession().getId() != null) {
            ACTIVE_SESSIONS.remove(se.getSession().getId());
        }
    }

    public static HttpSession getSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }
        HttpSession session = ACTIVE_SESSIONS.get(sessionId.trim());
        if (session != null) {
            try {
                // Verify the session has not been invalidated by the container
                session.getLastAccessedTime();
                return session;
            } catch (IllegalStateException e) {
                ACTIVE_SESSIONS.remove(sessionId.trim());
            }
        }
        return null;
    }

    public static void registerSession(HttpSession session) {
        if (session != null && session.getId() != null) {
            ACTIVE_SESSIONS.put(session.getId(), session);
        }
    }

    public static void removeSession(String sessionId) {
        if (sessionId != null && !sessionId.isBlank()) {
            ACTIVE_SESSIONS.remove(sessionId.trim());
        }
    }
}
