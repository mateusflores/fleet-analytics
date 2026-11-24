package com.unesp.fleetanalyticsapi.infra.security;

import com.unesp.fleetanalyticsapi.model.entities.crud.User;
import com.unesp.fleetanalyticsapi.model.entities.crud.Role;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.Base64;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class JwtAuthenticationFilterTest {

    private static final String SECRET = Base64.getEncoder()
            .encodeToString("this-is-a-test-secret-key-for-hmac-sha-256!!".getBytes());

    private final UserDetailsService users = mock(UserDetailsService.class);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
            new JwtService(SECRET, 900000L, 604800000L), users, new AccessTokenBlacklist());

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private FilterChain run(String bearer) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + bearer);
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        return chain;
    }

    @Test
    void expiredTokenContinuesUnauthenticatedInsteadOfFailing() throws Exception {
        String expired = new JwtService(SECRET, -1000L, 1000L)
                .generateAccessToken(User.builder().id(UUID.randomUUID()).username("u").password("p")
                        .role(Role.builder().name("R").permissions(Set.of()).build()).build());

        FilterChain chain = run(expired);

        verify(chain).doFilter(any(), any());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void garbageTokenContinuesUnauthenticated() throws Exception {
        FilterChain chain = run("not-a-jwt");

        verify(chain).doFilter(any(), any());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(users, never()).loadUserByUsername(any());
    }
}
