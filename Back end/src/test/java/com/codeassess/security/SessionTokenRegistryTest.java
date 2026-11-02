package com.codeassess.security;

import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SessionTokenRegistryTest {

    private SessionTokenRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SessionTokenRegistry();
    }

    @AfterEach
    void tearDown() {
        SessionTokenRegistry.removeSession("test-session-1");
        SessionTokenRegistry.removeSession("test-session-2");
        SessionTokenRegistry.removeSession("test-session-3");
        SessionTokenRegistry.removeSession("whitespace-session");
        SessionTokenRegistry.removeSession("event-session-1");
        SessionTokenRegistry.removeSession("event-session-2");
    }

    // ==========================================
    // --- registerSession & getSession Tests ---
    // ==========================================

    @Test
    void registerSessionWithValidSessionStoresSessionInRegistry() {
        MockHttpSession session = new MockHttpSession(null, "test-session-1");

        SessionTokenRegistry.registerSession(session);

        HttpSession retrieved = SessionTokenRegistry.getSession("test-session-1");
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getId()).isEqualTo("test-session-1");
    }

    @Test
    void registerSessionWithNullOrEmptyIdDoesNothing() {
        HttpSession mockSessionWithNullId = mock(HttpSession.class);
        when(mockSessionWithNullId.getId()).thenReturn(null);

        SessionTokenRegistry.registerSession(mockSessionWithNullId);

        assertThat(SessionTokenRegistry.getSession(null)).isNull();
    }

    @Test
    void registerSessionWithNullSessionDoesNothing() {
        SessionTokenRegistry.registerSession(null);
        // Should not throw NPE
    }

    @Test
    void registerMultipleDistinctSessionsStoresBothIndependently() {
        MockHttpSession session1 = new MockHttpSession(null, "test-session-1");
        MockHttpSession session2 = new MockHttpSession(null, "test-session-2");

        SessionTokenRegistry.registerSession(session1);
        SessionTokenRegistry.registerSession(session2);

        assertThat(SessionTokenRegistry.getSession("test-session-1")).isSameAs(session1);
        assertThat(SessionTokenRegistry.getSession("test-session-2")).isSameAs(session2);
    }

    @Test
    void registerDuplicateSessionIdUpdatesSessionInRegistry() {
        MockHttpSession sessionOld = new MockHttpSession(null, "test-session-1");
        MockHttpSession sessionNew = new MockHttpSession(null, "test-session-1");

        SessionTokenRegistry.registerSession(sessionOld);
        assertThat(SessionTokenRegistry.getSession("test-session-1")).isSameAs(sessionOld);

        SessionTokenRegistry.registerSession(sessionNew);
        assertThat(SessionTokenRegistry.getSession("test-session-1")).isSameAs(sessionNew);
    }

    @Test
    void getSessionWithWhitespaceIdTrimsAndReturnsSession() {
        MockHttpSession session = new MockHttpSession(null, "whitespace-session");
        SessionTokenRegistry.registerSession(session);

        HttpSession retrieved = SessionTokenRegistry.getSession("   whitespace-session   ");
        assertThat(retrieved).isSameAs(session);
    }

    @Test
    void getSessionWithNullReturnsNull() {
        assertThat(SessionTokenRegistry.getSession(null)).isNull();
    }

    @Test
    void getSessionWithEmptyOrBlankStringReturnsNull() {
        assertThat(SessionTokenRegistry.getSession("")).isNull();
        assertThat(SessionTokenRegistry.getSession("   ")).isNull();
    }

    @Test
    void getSessionWithUnknownIdReturnsNull() {
        assertThat(SessionTokenRegistry.getSession("non-existent-session-id")).isNull();
    }

    @Test
    void getSessionWhenSessionInvalidatedEvictsFromRegistryAndReturnsNull() {
        HttpSession mockSession = mock(HttpSession.class);
        when(mockSession.getId()).thenReturn("test-session-3");
        when(mockSession.getLastAccessedTime()).thenThrow(new IllegalStateException("Session invalidated"));

        SessionTokenRegistry.registerSession(mockSession);

        // Upon lookup, getLastAccessedTime() throws IllegalStateException, which should evict session and return null
        HttpSession retrieved = SessionTokenRegistry.getSession("test-session-3");
        assertThat(retrieved).isNull();

        // Subsequent call must also return null
        assertThat(SessionTokenRegistry.getSession("test-session-3")).isNull();
    }

    // ==========================================
    // --- removeSession Tests ---
    // ==========================================

    @Test
    void removeSessionWithValidIdRemovesFromRegistry() {
        MockHttpSession session = new MockHttpSession(null, "test-session-1");
        SessionTokenRegistry.registerSession(session);
        assertThat(SessionTokenRegistry.getSession("test-session-1")).isNotNull();

        SessionTokenRegistry.removeSession("test-session-1");
        assertThat(SessionTokenRegistry.getSession("test-session-1")).isNull();
    }

    @Test
    void removeSessionWithWhitespaceIdTrimsAndRemovesFromRegistry() {
        MockHttpSession session = new MockHttpSession(null, "test-session-1");
        SessionTokenRegistry.registerSession(session);
        assertThat(SessionTokenRegistry.getSession("test-session-1")).isNotNull();

        SessionTokenRegistry.removeSession("  test-session-1  ");
        assertThat(SessionTokenRegistry.getSession("test-session-1")).isNull();
    }

    @Test
    void removeSessionWithNullOrBlankDoesNothing() {
        // Should not throw NPE or exception
        SessionTokenRegistry.removeSession(null);
        SessionTokenRegistry.removeSession("");
        SessionTokenRegistry.removeSession("   ");
    }

    @Test
    void removeSessionWithNonExistentIdDoesNotThrow() {
        SessionTokenRegistry.removeSession("does-not-exist");
    }

    // ==========================================
    // --- HttpSessionListener Lifecycle Tests ---
    // ==========================================

    @Test
    void sessionCreatedWithValidEventRegistersSession() {
        MockHttpSession session = new MockHttpSession(null, "event-session-1");
        HttpSessionEvent event = new HttpSessionEvent(session);

        registry.sessionCreated(event);

        assertThat(SessionTokenRegistry.getSession("event-session-1")).isSameAs(session);
    }

    @Test
    void sessionCreatedWithNullEventOrNullSessionOrNullIdDoesNothing() {
        registry.sessionCreated(null);

        HttpSessionEvent eventWithNullSession = mock(HttpSessionEvent.class);
        when(eventWithNullSession.getSession()).thenReturn(null);
        registry.sessionCreated(eventWithNullSession);

        HttpSession mockSessionWithNullId = mock(HttpSession.class);
        when(mockSessionWithNullId.getId()).thenReturn(null);
        HttpSessionEvent eventWithNullId = mock(HttpSessionEvent.class);
        when(eventWithNullId.getSession()).thenReturn(mockSessionWithNullId);
        registry.sessionCreated(eventWithNullId);
    }

    @Test
    void sessionDestroyedWithValidEventRemovesSession() {
        MockHttpSession session = new MockHttpSession(null, "event-session-2");
        SessionTokenRegistry.registerSession(session);
        assertThat(SessionTokenRegistry.getSession("event-session-2")).isNotNull();

        HttpSessionEvent event = new HttpSessionEvent(session);
        registry.sessionDestroyed(event);

        assertThat(SessionTokenRegistry.getSession("event-session-2")).isNull();
    }

    @Test
    void sessionDestroyedWithNullEventOrNullSessionOrNullIdDoesNothing() {
        registry.sessionDestroyed(null);

        HttpSessionEvent eventWithNullSession = mock(HttpSessionEvent.class);
        when(eventWithNullSession.getSession()).thenReturn(null);
        registry.sessionDestroyed(eventWithNullSession);

        HttpSession mockSessionWithNullId = mock(HttpSession.class);
        when(mockSessionWithNullId.getId()).thenReturn(null);
        HttpSessionEvent eventWithNullId = mock(HttpSessionEvent.class);
        when(eventWithNullId.getSession()).thenReturn(mockSessionWithNullId);
        registry.sessionDestroyed(eventWithNullId);
    }

    @Test
    void sessionTokenRegistryCanBeInstantiatedAsListenerComponent() {
        SessionTokenRegistry listener = new SessionTokenRegistry();
        assertThat(listener).isNotNull();
    }
}
