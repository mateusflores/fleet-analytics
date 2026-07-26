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
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

@Service
public class RestApiClient {

    private static final Logger log = LoggerFactory.getLogger(RestApiClient.class);
    private static final int PAGE_SIZE = 1000;

    private final RestClient restClient;
    private final ObjectMapper mapper;

    private String accessToken;
    private String refreshToken;

    public RestApiClient(RestClient restClient, ObjectMapper mapper) {
        this.restClient = restClient;
        this.mapper = mapper;
    }

    public void login(String username, String password) {
        ApiLoginRequest request = new ApiLoginRequest(username, password);

        ApiLoginResponse response = restClient.post()
                .uri("/api/v1/auth/login")
                .body(request)
                .retrieve()
                .body(ApiLoginResponse.class);

        this.accessToken = response.accessToken();
        this.refreshToken = response.refreshToken();
        log.info("Logged in as user {}", response.userId());
    }

    public boolean isAuthenticated() {
        return accessToken != null && !accessToken.isBlank();
    }

    public void refreshToken() {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IllegalStateException("No refresh token available - please login first");
        }

        ApiRefreshTokenRequest request = new ApiRefreshTokenRequest(refreshToken);

        ApiLoginResponse response = restClient.post()
                .uri("/api/v1/auth/refresh")
                .body(request)
                .retrieve()
                .body(ApiLoginResponse.class);

        this.accessToken = response.accessToken();
        this.refreshToken = response.refreshToken();
        log.debug("Token refreshed successfully");
    }

    public void logout() {
        if (!isAuthenticated()) return;

        try {
            ApiLogoutRequest request = new ApiLogoutRequest(refreshToken);
            restClient.post()
                    .uri("/api/v1/auth/logout")
                    .header("Authorization", bearer())
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("Logout failed: {}", e.getMessage());
        } finally {
            accessToken = null;
            refreshToken = null;
        }
    }

    public List<Driver> fetchDrivers() {
        requireAuth();
        List<ApiUserSummary> users = fetchPage(
                "/api/v1/users/search?roleName=DRIVER&size=" + PAGE_SIZE,
                ApiUserSummary.class
        );
        return users.stream()
                .map(this::toDriver)
                .toList();
    }

    public List<Vehicle> fetchVehicles() {
        requireAuth();
        List<ApiVehicleSummary> vehicles = fetchPage(
                "/api/v1/vehicles?size=" + PAGE_SIZE,
                ApiVehicleSummary.class
        );
        return vehicles.stream()
                .filter(ApiVehicleSummary::active)
                .map(this::toVehicle)
                .toList();
    }

    public UUID startTrip(UUID driverId, UUID vehicleId) {
        requireAuth();
        ApiCreateTripRequest request = new ApiCreateTripRequest(driverId, vehicleId);

        ApiTripSummary response = restClient.post()
                .uri("/api/v1/trips")
                .header("Authorization", bearer())
                .body(request)
                .retrieve()
                .body(ApiTripSummary.class);

        if (response == null) {
            throw new IllegalStateException("Failed to start trip: empty response");
        }
        log.info("Trip started: id={} vehicle={} driver={}",
                response.id(), response.vehicleId(), response.driverId());
        return response.id();
    }

    public void endTrip(UUID tripId) {
        requireAuth();
        restClient.patch()
                .uri("/api/v1/trips/{id}/end", tripId)
                .header("Authorization", bearer())
                .retrieve()
                .toBodilessEntity();
        log.info("Trip ended: id={}", tripId);
    }

    private <T> List<T> fetchPage(String uri, Class<T> clazz) {
        try {
            String json = restClient.get()
                    .uri(uri)
                    .header("Authorization", bearer())
                    .retrieve()
                    .body(String.class);

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

    private String bearer() {
        return "Bearer " + accessToken;
    }

    private void requireAuth() {
        if (!isAuthenticated()) {
            throw new IllegalStateException("Not authenticated - call login() first");
        }
    }

}
