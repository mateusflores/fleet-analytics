package com.unesp.fleetanalyticsapi.infra.security;

import com.unesp.fleetanalyticsapi.model.entities.crud.Permission;
import com.unesp.fleetanalyticsapi.model.entities.crud.Role;
import com.unesp.fleetanalyticsapi.model.entities.crud.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
        User user = createUser("testuser", "users:read", "users:write");

        String token = jwtService.generateAccessToken(user);

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
        User user = createUser("testuser",
                "users:read", "users:write", "users:create", "users:management",
                "analytics:view", "analytics:export", "reports:generate");

        String accessToken = jwtService.generateAccessToken(user);
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

    @Test
    void accessTokenShouldContainUserIdClaim() {
        User user = createUser("testuser", "users:read");

        String token = jwtService.generateAccessToken(user);

        Claims claims = parseClaims(token);
        assertEquals(user.getId().toString(), claims.get("userId").toString());
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

    private static User createUser(String username, String... authorities) {
        Set<Permission> permissions = Arrays.stream(authorities)
                .map(a -> Permission.builder().name(a).build())
                .collect(Collectors.toSet());
        Role role = Role.builder()
                .name("TEST_ROLE")
                .permissions(permissions)
                .build();
        return User.builder()
                .id(UUID.randomUUID())
                .username(username)
                .password("password")
                .role(role)
                .build();
    }
}
