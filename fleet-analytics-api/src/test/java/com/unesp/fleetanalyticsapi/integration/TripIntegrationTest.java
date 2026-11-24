package com.unesp.fleetanalyticsapi.integration;

import com.unesp.fleetanalyticsapi.model.entities.crud.User;
import com.unesp.fleetanalyticsapi.model.entities.crud.Vehicle;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Regras de viagem: uma ativa por veículo e por motorista, quem pode encerrar, veículo inativo/motorista desativado. */
class TripIntegrationTest extends IntegrationTestSupport {

    private ResultActions start(String token, User driver, Vehicle v) throws Exception {
        return mvc.perform(json(post("/api/v1/trips"), token,
                "{\"driverId\":\"%s\",\"vehicleId\":\"%s\"}".formatted(driver.getId(), v.getId())));
    }

    @Test
    void driverStartsAndEndsOwnTrip() throws Exception {
        User driver = user("Driver");
        Vehicle v = vehicle(true);
        String t = token(driver);
        String tripId = id(start(t, driver, v).andExpect(status().isCreated())
                .andExpect(jsonPath("$.vehiclePlate").value(v.getLicensePlate()))
                .andExpect(jsonPath("$.endTime").doesNotExist()), "$.id");

        mvc.perform(auth(get("/api/v1/trips/vehicle/" + v.getId() + "/active"), t))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(tripId));
        mvc.perform(auth(patch("/api/v1/trips/" + tripId + "/end"), t))
                .andExpect(status().isOk()).andExpect(jsonPath("$.endTime").isNotEmpty());
        mvc.perform(auth(patch("/api/v1/trips/" + tripId + "/end"), t)).andExpect(status().isBadRequest());
        mvc.perform(auth(get("/api/v1/trips/vehicle/" + v.getId() + "/active"), t)).andExpect(status().isNotFound());
    }

    @Test
    void oneActiveTripPerVehicleAndPerDriver() throws Exception {
        User d1 = user("Driver"), d2 = user("Driver");
        Vehicle v1 = vehicle(true), v2 = vehicle(true);
        String admin = token(user("Admin"));
        start(admin, d1, v1).andExpect(status().isCreated());
        start(admin, d2, v1).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Vehicle already has an active trip"));
        start(admin, d1, v2).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Driver already has an active trip"));
        start(admin, d2, v2).andExpect(status().isCreated());
    }

    @Test
    void onlyTheDriverOrAnAdminCanEndATrip() throws Exception {
        User driver = user("Driver"), other = user("Driver");
        Vehicle v = vehicle(true);
        String tripId = id(start(token(driver), driver, v), "$.id");
        mvc.perform(auth(patch("/api/v1/trips/" + tripId + "/end"), token(other))).andExpect(status().isForbidden());
        mvc.perform(auth(patch("/api/v1/trips/" + tripId + "/end"), token(user("Admin")))).andExpect(status().isOk());
    }

    @Test
    void inactiveVehicleOrDisabledDriverCannotStart() throws Exception {
        String admin = token(user("Admin"));
        start(admin, user("Driver"), vehicle(false)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Vehicle is inactive"));
        start(admin, user("Driver", false), vehicle(true)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Driver is disabled"));
    }

    @Test
    void unknownIdsAndPermissions() throws Exception {
        String admin = token(user("Admin"));
        mvc.perform(json(post("/api/v1/trips"), admin, "{\"driverId\":\"%s\",\"vehicleId\":\"%s\"}"
                .formatted(java.util.UUID.randomUUID(), vehicle(true).getId()))).andExpect(status().isNotFound());
        mvc.perform(json(post("/api/v1/trips"), admin, "{}")).andExpect(status().isUnprocessableEntity());
        User analyst = user("Analyst");
        start(token(analyst), user("Driver"), vehicle(true)).andExpect(status().isForbidden());
    }

    @Test
    void searchFiltersOngoingTrips() throws Exception {
        User driver = user("Driver");
        Vehicle v = vehicle(true);
        String t = token(driver);
        String tripId = id(start(t, driver, v), "$.id");
        mvc.perform(auth(get("/api/v1/trips/search?vehicleId=" + v.getId() + "&ongoingTrip=true"), t))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(tripId));
        mvc.perform(auth(get("/api/v1/trips/search?vehicleId=" + v.getId() + "&ongoingTrip=false"), t))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
    }
}
