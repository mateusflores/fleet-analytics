package com.unesp.fleetanalyticsapi.infra.security;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccessTokenBlacklistTest {

    private final AccessTokenBlacklist blacklist = new AccessTokenBlacklist();

    @Test
    void shouldReturnTrueWhenJtiIsBlacklistedAndNotExpired() {
        String jti = "test-jti";
        Instant expiresAt = Instant.now().plusSeconds(60);

        blacklist.add(jti, expiresAt);

        assertTrue(blacklist.contains(jti));
    }

    @Test
    void shouldReturnFalseWhenJtiIsExpired() {
        String jti = "test-jti";
        Instant expiresAt = Instant.now().minusSeconds(60);

        blacklist.add(jti, expiresAt);

        assertFalse(blacklist.contains(jti));
    }

    @Test
    void shouldReturnFalseWhenJtiIsNotInBlacklist() {
        assertFalse(blacklist.contains("unknown-jti"));
    }

    @Test
    void shouldRemoveExpiredEntryOnContainsCheck() {
        String jti = "test-jti";
        Instant expiresAt = Instant.now().minusSeconds(60);

        blacklist.add(jti, expiresAt);
        blacklist.contains(jti);

        assertFalse(blacklist.contains(jti));
    }
}
