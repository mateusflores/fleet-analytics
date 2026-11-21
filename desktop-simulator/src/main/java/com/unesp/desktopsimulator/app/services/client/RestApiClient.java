package com.unesp.desktopsimulator.app.services.client;

import com.unesp.desktopsimulator.app.services.client.dto.ApiCreateTripRequest;
import com.unesp.desktopsimulator.app.services.client.dto.ApiLoginRequest;
import com.unesp.desktopsimulator.app.services.client.dto.ApiLoginResponse;
import com.unesp.desktopsimulator.app.services.client.dto.ApiLogoutRequest;
import com.unesp.desktopsimulator.app.services.client.dto.ApiRefreshTokenRequest;
import com.unesp.desktopsimulator.app.services.client.dto.ApiTripSummary;
import com.unesp.desktopsimulator.app.services.client.dto.ApiUserSummary;
import com.unesp.desktopsimulator.app.services.client.dto.ApiVehicleSummary;
import com.unesp.desktopsimulator.model.Driver;
import com.unesp.desktopsimulator.model.Vehicle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class RestApiClient {

    private static final Logger log = LoggerFactory.getLogger(RestApiClient.class);
    private static final int PAGE_SIZE = 1000;
    /** Renova o access token um pouco antes de expirar, para não depender só do 401. */
    private static final Duration REFRESH_MARGIN = Duration.ofSeconds(60);

    private final RestClient restClient;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final Object tokenLock = new Object();

    private volatile String accessToken;
    private volatile String refreshToken;
    private volatile Instant accessExpiresAt = Instant.MIN;

    public RestApiClient(RestClient restClient, ObjectMapper mapper) {
        this(restClient, mapper, Clock.systemUTC());
    }

    RestApiClient(RestClient restClient, ObjectMapper mapper, Clock clock) {
        this.restClient = restClient;
        this.mapper = mapper;
        this.clock = clock;
    }

    public void login(String username, String password) {
        ApiLoginRequest request = new ApiLoginRequest(username, password);

        ApiLoginResponse response = restClient.post()
                .uri("/api/v1/auth/login")
                .body(request)
                .retrieve()
                .body(ApiLoginResponse.class);

        storeTokens(response);
        log.info("Logged in as user {}", response.userId());
    }

    public boolean isAuthenticated() {
        return accessToken != null && !accessToken.isBlank();
    }

    /**
     * Troca o refresh token por um novo par de tokens. A API revoga o refresh token usado (rotação),
     * por isso a troca é serializada: chamadas concorrentes que chegam com um token já renovado não refazem a troca.
     */
    public void refreshToken() {
        refreshIfStale(accessToken);
    }

    private void refreshIfStale(String staleAccessToken) {
        synchronized (tokenLock) {
            if (accessToken != null && !accessToken.equals(staleAccessToken) && !isExpiringSoon()) {
                return; // outra thread já renovou
            }
            if (refreshToken == null || refreshToken.isBlank()) {
                throw new SessionExpiredException("No refresh token available - please login again");
            }
            ApiLoginResponse response;
            try {
                response = restClient.post()
                        .uri("/api/v1/auth/refresh")
                        .body(new ApiRefreshTokenRequest(refreshToken))
                        .retrieve()
                        .body(ApiLoginResponse.class);
            } catch (RestClientResponseException e) {
                if (e.getStatusCode().is4xxClientError() || e.getStatusCode().value() == 500) {
                    clearTokens();
                    throw new SessionExpiredException("Session expired - please login again", e);
                }
                throw e;
            }
            if (response == null || response.accessToken() == null) {
                clearTokens();
                throw new SessionExpiredException("Session expired - please login again");
            }
            storeTokens(response);
            log.debug("Token refreshed; expires at {}", accessExpiresAt);
        }
    }

    private boolean isExpiringSoon() {
        return !clock.instant().plus(REFRESH_MARGIN).isBefore(accessExpiresAt);
    }

    private void storeTokens(ApiLoginResponse response) {
        this.accessToken = response.accessToken();
        this.refreshToken = response.refreshToken();
        long ttl = response.expiresIn() > 0 ? response.expiresIn() : 0;
        this.accessExpiresAt = clock.instant().plusSeconds(ttl);
    }

    private void clearTokens() {
        accessToken = null;
        refreshToken = null;
        accessExpiresAt = Instant.MIN;
    }

    /**
     * Executa uma chamada autenticada: renova o token antes se estiver perto de expirar
     * e, se a API ainda responder 401, renova e tenta mais uma vez.
     */
    private <T> T authorized(Function<String, T> call) {
        requireAuth();
        String token = accessToken;
        if (isExpiringSoon()) {
            refreshIfStale(token);
            token = accessToken;
        }
        try {
            return call.apply("Bearer " + token);
        } catch (HttpClientErrorException.Unauthorized e) {
            log.info("Access token rejected, refreshing and retrying");
            refreshIfStale(token);
            return call.apply("Bearer " + accessToken);
        }
    }

    public void logout() {
        if (!isAuthenticated()) return;

        try {
            ApiLogoutRequest request = new ApiLogoutRequest(refreshToken);
            restClient.post()
                    .uri("/api/v1/auth/logout")
                    .header("Authorization", "Bearer " + accessToken)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("Logout failed: {}", e.getMessage());
        } finally {
            clearTokens();
        }
    }

    public List<Driver> fetchDrivers() {
        List<ApiUserSummary> users = fetchPage(
                "/api/v1/users/search?roleName=DRIVER&size=" + PAGE_SIZE,
                ApiUserSummary.class
        );
        return users.stream()
                .map(this::toDriver)
                .toList();
    }

    public List<Vehicle> fetchVehicles() {
        List<ApiVehicleSummary> vehicles = fetchPage(
                "/api/v1/vehicles?size=" + PAGE_SIZE,
                ApiVehicleSummary.class
        );
        return vehicles.stream()
                .filter(ApiVehicleSummary::active)
                .map(this::toVehicle)
                .toList();
    }

    public Set<UUID> fetchBusyVehicleIds() {
        return findOngoingTrips().stream()
                .map(ApiTripSummary::vehicleId)
                .collect(Collectors.toSet());
    }

    public Set<UUID> fetchBusyDriverIds() {
        return findOngoingTrips().stream()
                .map(ApiTripSummary::driverId)
                .collect(Collectors.toSet());
    }

    public ApiTripSummary findActiveTripByVehicle(UUID vehicleId) {
        ApiTripSummary response = authorized(auth -> restClient.get()
                .uri("/api/v1/trips/vehicle/{vehicleId}/active", vehicleId)
                .header("Authorization", auth)
                .retrieve()
                .body(ApiTripSummary.class));
        if (response == null) {
            throw new IllegalStateException("No active trip found for vehicle " + vehicleId);
        }
        return response;
    }

    private List<ApiTripSummary> findOngoingTrips() {
        return fetchPage(
                "/api/v1/trips/search?size=" + PAGE_SIZE + "&ongoingTrip=true",
                ApiTripSummary.class
        );
    }

    public UUID startTrip(UUID driverId, UUID vehicleId) {
        ApiCreateTripRequest request = new ApiCreateTripRequest(driverId, vehicleId);

        ApiTripSummary response = authorized(auth -> restClient.post()
                .uri("/api/v1/trips")
                .header("Authorization", auth)
                .body(request)
                .retrieve()
                .body(ApiTripSummary.class));

        if (response == null) {
            throw new IllegalStateException("Failed to start trip: empty response");
        }
        log.info("Trip started: id={} vehicle={} driver={}",
                response.id(), response.vehicleId(), response.driverId());
        return response.id();
    }

    public void endTrip(UUID tripId) {
        authorized(auth -> restClient.patch()
                .uri("/api/v1/trips/{id}/end", tripId)
                .header("Authorization", auth)
                .retrieve()
                .toBodilessEntity());
        log.info("Trip ended: id={}", tripId);
    }

    private <T> List<T> fetchPage(String uri, Class<T> clazz) {
        String json = authorized(auth -> restClient.get()
                .uri(uri)
                .header("Authorization", auth)
                .retrieve()
                .body(String.class));
        try {
            JsonNode root = mapper.readTree(json);
            JsonNode content = root.get("content");
            return mapper.readerForListOf(clazz).readValue(content);
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch " + clazz.getSimpleName() + " from API", e);
        }
    }

    private Driver toDriver(ApiUserSummary user) {
        return new Driver.Builder(user.userId(), user.firstName(), user.lastName()).build();
    }

    private Vehicle toVehicle(ApiVehicleSummary sv) {
        return Vehicle.Builder.builder()
                .withId(sv.id())
                .withLicensePlate(sv.licensePlate())
                .withBrand(sv.brand())
                .withModel(sv.model())
                .withYear(sv.year())
                .withColor(sv.color())
                .withVehicleType(sv.vehicleType())
                .withNickname(sv.nickname())
                .build();
    }

    private void requireAuth() {
        if (!isAuthenticated()) {
            throw new SessionExpiredException("Not authenticated - please login again");
        }
    }

}
