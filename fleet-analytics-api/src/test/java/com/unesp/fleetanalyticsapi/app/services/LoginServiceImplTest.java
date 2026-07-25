package com.unesp.fleetanalyticsapi.app.services;

import com.unesp.fleetanalyticsapi.app.dtos.LogoutRequest;
import com.unesp.fleetanalyticsapi.infra.security.AccessTokenBlacklist;
import com.unesp.fleetanalyticsapi.infra.security.JwtService;
import com.unesp.fleetanalyticsapi.model.entities.RefreshToken;
import com.unesp.fleetanalyticsapi.model.repositories.RefreshTokenRepository;
import com.unesp.fleetanalyticsapi.model.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private AccessTokenBlacklist accessTokenBlacklist;

    @InjectMocks
    private LoginServiceImpl loginService;

    @Test
    void shouldLogoutSuccessfully() {
        String accessToken = "access-token";
        String refreshTokenValue = "refresh-token";
        String jti = "test-jti";
        Instant accessExpiry = Instant.now().plusSeconds(60);

        LogoutRequest request = new LogoutRequest(refreshTokenValue);

        RefreshToken storedToken = new RefreshToken();
        storedToken.setRevoked(false);

        when(jwtService.extractJti(accessToken)).thenReturn(jti);
        when(jwtService.extractExpiration(accessToken)).thenReturn(accessExpiry);
        when(refreshTokenRepository.findByToken(refreshTokenValue))
                .thenReturn(Optional.of(storedToken));

        loginService.logout(accessToken, request);

        verify(accessTokenBlacklist).add(jti, accessExpiry);
        verify(refreshTokenRepository).findByToken(refreshTokenValue);
        verify(refreshTokenRepository).save(storedToken);
        assertTrue(storedToken.isRevoked());
    }

    @Test
    void shouldThrowWhenRefreshTokenNotFound() {
        String accessToken = "access-token";
        String refreshTokenValue = "unknown-refresh-token";
        String jti = "test-jti";
        Instant accessExpiry = Instant.now().plusSeconds(60);

        LogoutRequest request = new LogoutRequest(refreshTokenValue);

        when(jwtService.extractJti(accessToken)).thenReturn(jti);
        when(jwtService.extractExpiration(accessToken)).thenReturn(accessExpiry);
        when(refreshTokenRepository.findByToken(refreshTokenValue))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> loginService.logout(accessToken, request));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        verify(accessTokenBlacklist).add(jti, accessExpiry);
        verify(refreshTokenRepository, never()).save(any());
    }
}
