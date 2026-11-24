package com.unesp.fleetanalyticsapi.app.services;

import com.unesp.fleetanalyticsapi.app.dtos.LoginRequest;
import com.unesp.fleetanalyticsapi.app.dtos.LogoutRequest;
import com.unesp.fleetanalyticsapi.app.dtos.RefreshTokenRequest;
import com.unesp.fleetanalyticsapi.infra.security.AccessTokenBlacklist;
import com.unesp.fleetanalyticsapi.infra.security.JwtService;
import com.unesp.fleetanalyticsapi.model.entities.crud.RefreshToken;
import com.unesp.fleetanalyticsapi.model.entities.crud.User;
import com.unesp.fleetanalyticsapi.model.repositories.RefreshTokenRepository;
import com.unesp.fleetanalyticsapi.model.repositories.UserRepository;
import io.jsonwebtoken.MalformedJwtException;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Regras de segurança do login/refresh/logout (usuário desativado, tokens inválidos, sessão de outro usuário). */
@ExtendWith(MockitoExtension.class)
class LoginServiceSecurityTest {

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
    private LoginServiceImpl service;

    private static HttpStatus status(Throwable t) {
        return HttpStatus.valueOf(((ResponseStatusException) t).getStatusCode().value());
    }

    @Test
    void disabledUserCannotLogin() {
        User user = User.builder().username("joao").password("hash").enabled(false).build();
        when(userRepository.findByUsername("joao")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pw", "hash")).thenReturn(true);

        assertThatThrownBy(() -> service.login(new LoginRequest("joao", "pw")))
                .satisfies(t -> assertThat(status(t)).isEqualTo(HttpStatus.FORBIDDEN));
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void wrongPasswordDoesNotRevealWhetherUserIsDisabled() {
        User user = User.builder().username("joao").password("hash").enabled(false).build();
        when(userRepository.findByUsername("joao")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("bad", "hash")).thenReturn(false);

        assertThatThrownBy(() -> service.login(new LoginRequest("joao", "bad")))
                .satisfies(t -> assertThat(status(t)).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void malformedRefreshTokenIsUnauthorizedNotServerError() {
        when(jwtService.isTokenExpired("garbage")).thenThrow(new MalformedJwtException("bad"));

        assertThatThrownBy(() -> service.refreshToken(new RefreshTokenRequest("garbage")))
                .satisfies(t -> assertThat(status(t)).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void disabledUserCannotRefreshAndTheTokenIsStillConsumed() {
        RefreshToken stored = RefreshToken.builder().token("r").revoked(false)
                .user(User.builder().username("joao").enabled(false).build()).build();
        when(jwtService.isTokenExpired("r")).thenReturn(false);
        when(refreshTokenRepository.findByToken("r")).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> service.refreshToken(new RefreshTokenRequest("r")))
                .satisfies(t -> assertThat(status(t)).isEqualTo(HttpStatus.FORBIDDEN));
        assertThat(stored.isRevoked()).isTrue();
        verify(jwtService, never()).generateAccessToken(any());
    }

    @Test
    void revokedRefreshTokenCannotBeReused() {
        RefreshToken stored = RefreshToken.builder().token("r").revoked(true)
                .user(User.builder().username("joao").enabled(true).build()).build();
        when(jwtService.isTokenExpired("r")).thenReturn(false);
        when(refreshTokenRepository.findByToken("r")).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> service.refreshToken(new RefreshTokenRequest("r")))
                .satisfies(t -> assertThat(status(t)).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void logoutWithInvalidAccessTokenIsUnauthorized() {
        when(jwtService.extractJti("bad")).thenThrow(new MalformedJwtException("bad"));

        assertThatThrownBy(() -> service.logout("bad", new LogoutRequest("r")))
                .satisfies(t -> assertThat(status(t)).isEqualTo(HttpStatus.UNAUTHORIZED));
        verify(accessTokenBlacklist, never()).add(any(), any());
    }

    @Test
    void cannotRevokeAnotherUsersRefreshToken() {
        Instant exp = Instant.now().plusSeconds(60);
        RefreshToken others = RefreshToken.builder().token("r").revoked(false)
                .user(User.builder().username("maria").enabled(true).build()).build();
        when(jwtService.extractJti("a")).thenReturn("jti");
        when(jwtService.extractUsername("a")).thenReturn("joao");
        when(jwtService.extractExpiration("a")).thenReturn(exp);
        when(refreshTokenRepository.findByToken("r")).thenReturn(Optional.of(others));

        assertThatThrownBy(() -> service.logout("a", new LogoutRequest("r")))
                .satisfies(t -> assertThat(status(t)).isEqualTo(HttpStatus.UNAUTHORIZED));
        assertThat(others.isRevoked()).isFalse();
        verify(accessTokenBlacklist).add("jti", exp);   // o token do próprio chamador é invalidado
        verify(refreshTokenRepository, never()).save(any());
    }
}
