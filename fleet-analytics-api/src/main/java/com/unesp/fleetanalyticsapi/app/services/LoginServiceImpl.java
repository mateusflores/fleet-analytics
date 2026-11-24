package com.unesp.fleetanalyticsapi.app.services;

import com.unesp.fleetanalyticsapi.app.dtos.LoginRequest;
import com.unesp.fleetanalyticsapi.app.dtos.LoginResponse;
import com.unesp.fleetanalyticsapi.app.dtos.LogoutRequest;
import com.unesp.fleetanalyticsapi.app.dtos.RefreshTokenRequest;
import com.unesp.fleetanalyticsapi.infra.security.AccessTokenBlacklist;
import com.unesp.fleetanalyticsapi.infra.security.JwtService;
import com.unesp.fleetanalyticsapi.model.entities.crud.RefreshToken;
import com.unesp.fleetanalyticsapi.model.entities.crud.User;
import com.unesp.fleetanalyticsapi.model.repositories.RefreshTokenRepository;
import com.unesp.fleetanalyticsapi.model.repositories.UserRepository;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class LoginServiceImpl implements LoginService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AccessTokenBlacklist accessTokenBlacklist;

    @Override
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Invalid username or password"));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }
        requireEnabled(user);

        return generateJwt(user);
    }

    @Override
    @Transactional
    public LoginResponse refreshToken(RefreshTokenRequest request) {
        String tokenStr = request.refreshToken();

        if (isExpiredOrInvalid(tokenStr)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Refresh token expired or invalid");
        }

        RefreshToken storedToken = refreshTokenRepository.findByToken(tokenStr)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Refresh token not found"));

        if (storedToken.isRevoked()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Refresh token revoked");
        }

        storedToken.setRevoked(true);
        refreshTokenRepository.save(storedToken);
        requireEnabled(storedToken.getUser());

        return generateJwt(storedToken.getUser());
    }

    private void requireEnabled(User user) {
        if (!user.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User is disabled");
        }
    }

    private boolean isExpiredOrInvalid(String token) {
        try {
            return jwtService.isTokenExpired(token);
        } catch (JwtException | IllegalArgumentException e) {
            return true;
        }
    }

    private LoginResponse generateJwt(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        String refreshTokenStr = jwtService.generateRefreshToken(user.getUsername());
        Instant accessExpiry = jwtService.extractExpiration(accessToken);
        Instant refreshExpiry = jwtService.extractExpiration(refreshTokenStr);

        RefreshToken refreshToken = RefreshToken.builder()
                .token(refreshTokenStr)
                .user(user)
                .expiresAt(refreshExpiry.atZone(ZoneId.systemDefault()))
                .revoked(false)
                .build();
        refreshTokenRepository.save(refreshToken);
        long expiresIn = Duration.between(Instant.now(), accessExpiry).getSeconds();

        return new LoginResponse(accessToken, refreshTokenStr, "Bearer", user.getId(), expiresIn);
    }

    @Override
    @Transactional
    public void logout(String accessToken, LogoutRequest request) {
        String jti;
        String username;
        Instant accessExpiry;
        try {
            jti = jwtService.extractJti(accessToken);
            username = jwtService.extractUsername(accessToken);
            accessExpiry = jwtService.extractExpiration(accessToken);
        } catch (JwtException | IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid access token");
        }
        // o access token é do próprio chamador: é invalidado mesmo que o refresh token não confira
        accessTokenBlacklist.add(jti, accessExpiry);

        RefreshToken storedToken = refreshTokenRepository.findByToken(request.refreshToken())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Refresh token not found"));
        if (!storedToken.getUser().getUsername().equals(username)) {
            // um usuário não pode revogar a sessão de outro
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token does not belong to the user");
        }

        storedToken.setRevoked(true);
        refreshTokenRepository.save(storedToken);
    }
}
