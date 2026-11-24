package com.unesp.fleetanalyticsapi.integration;

import com.unesp.fleetanalyticsapi.model.entities.crud.Vehicle;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class VehicleIntegrationTest extends IntegrationTestSupport {

    private static final String BODY = """
            {"serialNumber":"%s","licensePlate":"%s","brand":"Volvo","model":"VM","vehicleType":"RIGID_TRUCK",
             "fuelType":"DIESEL","nickname":"Teste","mileageKm":1000}""";

    @Test
    void createValidatesAndRejectsDuplicates() throws Exception {
        String admin = token(user("Admin"));
        mvc.perform(json(post("/api/v1/vehicles"), admin, BODY.formatted("IT-SER-1", "ITA1B23")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.active").value(true));
        mvc.perform(json(post("/api/v1/vehicles"), admin, BODY.formatted("IT-SER-2", "ITA1B23")))
                .andExpect(status().isConflict());
        mvc.perform(json(post("/api/v1/vehicles"), admin, "{\"licensePlate\":\"X\"}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(json(post("/api/v1/vehicles"), admin, BODY.formatted("IT-SER-3", "ITA1B24")
                .replace("RIGID_TRUCK", "SPACESHIP"))).andExpect(status().isBadRequest());
        mvc.perform(json(post("/api/v1/vehicles"), admin, BODY.formatted("IT-SER-4", "ITA1B25")
                .replace("1000", "-5"))).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void partialUpdateKeepsUntouchedFieldsIncludingActive() throws Exception {
        Vehicle v = vehicle(true);
        String admin = token(user("Admin"));
        mvc.perform(json(put("/api/v1/vehicles/" + v.getId()), admin, "{\"color\":\"azul\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.color").value("azul"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.licensePlate").value(v.getLicensePlate()));
        mvc.perform(json(put("/api/v1/vehicles/" + v.getId()), admin, "{\"active\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void searchAndLookup() throws Exception {
        Vehicle v = vehicle(true);
        String analyst = token(user("Analyst"));
        mvc.perform(auth(get("/api/v1/vehicles/search?licensePlate=" + v.getLicensePlate()), analyst))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(v.getId().toString()));
        mvc.perform(auth(get("/api/v1/vehicles/" + v.getId()), analyst)).andExpect(status().isOk());
        mvc.perform(auth(get("/api/v1/vehicles/" + java.util.UUID.randomUUID()), analyst))
                .andExpect(status().isNotFound());
        mvc.perform(json(put("/api/v1/vehicles/" + v.getId()), analyst, "{\"color\":\"x\"}"))
                .andExpect(status().isForbidden());
    }
}
