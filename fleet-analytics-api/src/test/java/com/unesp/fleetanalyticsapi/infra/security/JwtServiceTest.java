package com.unesp.fleetanalyticsapi.infra.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private static final String SECRET = Base64.getEncoder()
            .encodeToString("this-is-a-test-secret-key-for-hmac-sha-256!!".getBytes());

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, 900000L, 604800000L);
    }

    @Test
    void accessTokenShouldContainAuthoritiesClaim() {
        UserDetails userDetails = createUserDetails("testuser", "users:read", "users:write");

        String token = jwtService.generateAccessToken(userDetails);

        Claims claims = parseClaims(token);
        assertNotNull(claims.get("authorities"));
        assertTrue(claims.get("authorities") instanceof Collection);
    }

    @Test
    void refreshTokenShouldNotContainAuthoritiesClaim() {
        String token = jwtService.generateRefreshToken("testuser");

        Claims claims = parseClaims(token);
        assertFalse(claims.containsKey("authorities"));
    }

    @Test
    void refreshTokenShouldContainSubject() {
        String username = "testuser";
        String token = jwtService.generateRefreshToken(username);

        Claims claims = parseClaims(token);
        assertEquals(username, claims.getSubject());
    }

    @Test
    void refreshTokenShouldContainJti() {
        String token = jwtService.generateRefreshToken("testuser");

        Claims claims = parseClaims(token);
        assertNotNull(claims.getId());
    }

    @Test
    void refreshTokenShouldBeSmallerThanAccessToken() {
        UserDetails userDetails = createUserDetails("testuser",
                "users:read", "users:write", "users:create", "users:management",
                "analytics:view", "analytics:export", "reports:generate");

        String accessToken = jwtService.generateAccessToken(userDetails);
        String refreshToken = jwtService.generateRefreshToken("testuser");

        assertTrue(refreshToken.length() < accessToken.length());
    }

    @Test
    void extractJtiShouldReturnTokenId() {
        String token = jwtService.generateRefreshToken("testuser");
        Claims claims = parseClaims(token);

        String jti = jwtService.extractJti(token);

        assertEquals(claims.getId(), jti);
    }

    @Test
    void extractUsernameShouldReturnSubject() {
        String username = "testuser";
        String token = jwtService.generateRefreshToken(username);

        String extracted = jwtService.extractUsername(token);

        assertEquals(username, extracted);
    }

    @Test
    void extractExpirationShouldReturnFutureInstant() {
        String token = jwtService.generateRefreshToken("testuser");

        var expiration = jwtService.extractExpiration(token);

        assertTrue(expiration.isAfter(Instant.now()));
    }

    private Claims parseClaims(String token) {
        byte[] keyBytes = Base64.getDecoder().decode(SECRET);
        SecretKey signingKey = Keys.hmacShaKeyFor(keyBytes);
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private static UserDetails createUserDetails(String username, String... authorities) {
        return new UserDetails() {
            @Override
            public Collection<? extends GrantedAuthority> getAuthorities() {
                return List.of(authorities).stream()
                        .map(SimpleGrantedAuthority::new)
                        .toList();
            }

            @Override
            public String getPassword() {
                return "password";
            }

            @Override
            public String getUsername() {
                return username;
            }
        };
    }
}
