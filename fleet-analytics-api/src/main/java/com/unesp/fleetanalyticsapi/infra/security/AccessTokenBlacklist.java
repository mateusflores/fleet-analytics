package com.unesp.fleetanalyticsapi.infra.security;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AccessTokenBlacklist {

    private final Map<String, Instant> blacklistedJtis = new ConcurrentHashMap<>();

    public void add(String jti, Instant expiresAt) {
        blacklistedJtis.put(jti, expiresAt);
    }

    public boolean contains(String jti) {
        Instant expiresAt = blacklistedJtis.get(jti);
        if (expiresAt == null) {
            return false;
        }
        if (expiresAt.isBefore(Instant.now())) {
            blacklistedJtis.remove(jti);
            return false;
        }
        return true;
    }
}
