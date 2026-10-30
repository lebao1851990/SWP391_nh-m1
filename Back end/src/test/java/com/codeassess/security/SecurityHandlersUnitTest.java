package com.codeassess.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityHandlersUnitTest {

    private final CustomAuthenticationEntryPoint authenticationEntryPoint = new CustomAuthenticationEntryPoint();
    private final CustomAccessDeniedHandler accessDeniedHandler = new CustomAccessDeniedHandler();

    @Test
    void authenticationEntryPointReturns401JsonContract() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        BadCredentialsException exception = new BadCredentialsException("Bad credentials");

        authenticationEntryPoint.commence(request, response, exception);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).contains("application/json");
        assertThat(response.getContentAsString()).contains("\"status\":401");
        assertThat(response.getContentAsString()).contains("\"code\":\"UNAUTHORIZED\"");
        assertThat(response.getContentAsString()).contains("\"message\":\"Please authenticate to access this resource.\"");
    }

    @Test
    void accessDeniedHandlerReturns403JsonContract() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AccessDeniedException exception = new AccessDeniedException("Access denied");

        accessDeniedHandler.handle(request, response, exception);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).contains("application/json");
        assertThat(response.getContentAsString()).contains("\"status\":403");
        assertThat(response.getContentAsString()).contains("\"code\":\"FORBIDDEN\"");
        assertThat(response.getContentAsString()).contains("\"message\":\"You do not have permission to access this resource.\"");
    }
}
