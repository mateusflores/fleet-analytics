package com.unesp.fleetanalyticsapi.app.services;

import com.unesp.fleetanalyticsapi.app.dtos.LoginResponse;
import com.unesp.fleetanalyticsapi.app.dtos.LogoutRequest;
import com.unesp.fleetanalyticsapi.app.dtos.RefreshTokenRequest;
import com.unesp.fleetanalyticsapi.infra.security.AccessTokenBlacklist;
import com.unesp.fleetanalyticsapi.infra.security.JwtService;
import com.unesp.fleetanalyticsapi.model.entities.crud.RefreshToken;
import com.unesp.fleetanalyticsapi.model.entities.crud.User;
import com.unesp.fleetanalyticsapi.model.repositories.RefreshTokenRepository;
import com.unesp.fleetanalyticsapi.model.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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

    @Test
    void shouldRefreshTokenSuccessfully() {
        String oldRefreshToken = "old-refresh-token";
        String newAccessToken = "new-access-token";
        String newRefreshToken = "new-refresh-token";
        Instant accessExpiry = Instant.now().plusSeconds(900);
        Instant refreshExpiry = Instant.now().plusSeconds(604800);
        UUID userId = UUID.randomUUID();

        RefreshTokenRequest request = new RefreshTokenRequest(oldRefreshToken);

        User user = new User();
        user.setId(userId);
        user.setUsername("testuser");

        RefreshToken storedToken = new RefreshToken();
        storedToken.setToken(oldRefreshToken);
        storedToken.setUser(user);
        storedToken.setRevoked(false);

        when(jwtService.isTokenExpired(oldRefreshToken)).thenReturn(false);
        when(refreshTokenRepository.findByToken(oldRefreshToken))
                .thenReturn(Optional.of(storedToken));
        when(jwtService.generateAccessToken(user)).thenReturn(newAccessToken);
        when(jwtService.generateRefreshToken(user.getUsername())).thenReturn(newRefreshToken);
        when(jwtService.extractExpiration(newAccessToken)).thenReturn(accessExpiry);
        when(jwtService.extractExpiration(newRefreshToken)).thenReturn(refreshExpiry);

        LoginResponse response = loginService.refreshToken(request);

        assertTrue(storedToken.isRevoked());

        ArgumentCaptor<RefreshToken> tokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository, times(2)).save(tokenCaptor.capture());

        List<RefreshToken> savedTokens = tokenCaptor.getAllValues();
        assertEquals(2, savedTokens.size());

        RefreshToken revokedToken = savedTokens.get(0);
        assertTrue(revokedToken.isRevoked());

        RefreshToken newToken = savedTokens.get(1);
        assertEquals(newRefreshToken, newToken.getToken());
        assertEquals(user, newToken.getUser());
        assertNotNull(newToken.getExpiresAt());

        assertEquals(newAccessToken, response.accessToken());
        assertEquals(newRefreshToken, response.refreshToken());
        assertEquals(userId, response.userId());
        assertEquals("Bearer", response.tokenType());
    }

    @Test
    void shouldThrowWhenRefreshingExpiredToken() {
        String tokenStr = "expired-refresh-token";
        RefreshTokenRequest request = new RefreshTokenRequest(tokenStr);

        when(jwtService.isTokenExpired(tokenStr)).thenReturn(true);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> loginService.refreshToken(request));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        assertEquals("Refresh token expired", exception.getReason());
    }

    @Test
    void shouldThrowWhenRefreshingNonexistentToken() {
        String tokenStr = "unknown-token";
        RefreshTokenRequest request = new RefreshTokenRequest(tokenStr);

        when(jwtService.isTokenExpired(tokenStr)).thenReturn(false);
        when(refreshTokenRepository.findByToken(tokenStr)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> loginService.refreshToken(request));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        assertEquals("Refresh token not found", exception.getReason());
    }

    @Test
    void shouldThrowWhenRefreshingRevokedToken() {
        String tokenStr = "revoked-refresh-token";
        RefreshTokenRequest request = new RefreshTokenRequest(tokenStr);

        RefreshToken storedToken = new RefreshToken();
        storedToken.setRevoked(true);

        when(jwtService.isTokenExpired(tokenStr)).thenReturn(false);
        when(refreshTokenRepository.findByToken(tokenStr))
                .thenReturn(Optional.of(storedToken));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> loginService.refreshToken(request));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        assertEquals("Refresh token revoked", exception.getReason());
    }
}
