package com.unesp.fleetanalyticsapi.app.services;

import com.unesp.fleetanalyticsapi.app.dtos.LoginRequest;
import com.unesp.fleetanalyticsapi.app.dtos.LoginResponse;
import com.unesp.fleetanalyticsapi.app.dtos.LogoutRequest;
import com.unesp.fleetanalyticsapi.app.dtos.RefreshTokenRequest;
import com.unesp.fleetanalyticsapi.infra.security.AccessTokenBlacklist;
import com.unesp.fleetanalyticsapi.infra.security.JwtService;
import com.unesp.fleetanalyticsapi.model.entities.RefreshToken;
import com.unesp.fleetanalyticsapi.model.entities.User;
import com.unesp.fleetanalyticsapi.model.repositories.RefreshTokenRepository;
import com.unesp.fleetanalyticsapi.model.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
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

        return generateJwt(user);
    }

    @Override
    @Transactional
    public LoginResponse refreshToken(RefreshTokenRequest request) {
        String tokenStr = request.refreshToken();

        if (jwtService.isTokenExpired(tokenStr)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Refresh token expired");
        }

        String username = jwtService.extractUsername(tokenStr);

        RefreshToken storedToken = refreshTokenRepository.findByToken(tokenStr)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Refresh token not found"));

        if (storedToken.isRevoked()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Refresh token revoked");
        }

        storedToken.setRevoked(true);
        refreshTokenRepository.save(storedToken);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        return generateJwt(user);
    }

    private LoginResponse generateJwt(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        String refreshTokenStr = jwtService.generateRefreshToken(user);
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
        String jti = jwtService.extractJti(accessToken);
        Instant accessExpiry = jwtService.extractExpiration(accessToken);
        accessTokenBlacklist.add(jti, accessExpiry);

        RefreshToken storedToken = refreshTokenRepository.findByToken(request.refreshToken())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Refresh token not found"));

        storedToken.setRevoked(true);
        refreshTokenRepository.save(storedToken);
    }
}
