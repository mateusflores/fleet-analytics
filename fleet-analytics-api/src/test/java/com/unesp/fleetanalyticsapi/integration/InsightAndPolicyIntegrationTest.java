package com.unesp.fleetanalyticsapi.integration;

import com.unesp.fleetanalyticsapi.model.entities.crud.User;
import com.unesp.fleetanalyticsapi.model.entities.crud.Vehicle;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Insights (gravados pelo ml-service) e políticas da frota, pela API. */
class InsightAndPolicyIntegrationTest extends IntegrationTestSupport {

    private UUID insight(String scope, UUID subject, String category, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO telemetry.insights (id, scope, subject_id, category, severity, title, description, evidence,
                                                status, dedup_key, updated_at)
                VALUES (?, ?, ?, ?, 'WARNING', 't', 'd', '{"ratio": 1.3}'::jsonb, ?, ?, now())""",
                id, scope, subject, category, status, category + ":" + id);
        return id;
    }

    @Test
    void listsOpenInsightsWithSubjectNamesAndEvidence() throws Exception {
        Vehicle v = vehicle(true);
        User driver = user("Driver");
        jdbc.update("DELETE FROM telemetry.insights");
        insight("VEHICLE", v.getId(), "VEHICLE_FUEL_TREND", "ACTIVE");
        insight("DRIVER", driver.getId(), "DRIVER_IDLE", "ACKNOWLEDGED");
        insight("FLEET", null, "FLEET_IDLE_COST", "RESOLVED");
        String analyst = token(user("Analyst"));

        mvc.perform(auth(get("/api/v1/insights"), analyst)).andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2));
        mvc.perform(auth(get("/api/v1/insights?scope=VEHICLE"), analyst))
                .andExpect(jsonPath("$.content[0].subjectName").value(v.getNickname()))
                .andExpect(jsonPath("$.content[0].evidence.ratio").value(1.3));
        mvc.perform(auth(get("/api/v1/insights?scope=DRIVER"), analyst))
                .andExpect(jsonPath("$.content[0].subjectName").value("Teste Driver"));
        mvc.perform(auth(get("/api/v1/insights?status=RESOLVED"), analyst))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].subjectName").doesNotExist());
        mvc.perform(auth(get("/api/v1/insights?status=ACTIVE,RESOLVED"), analyst))
                .andExpect(jsonPath("$.content.length()").value(2));
        mvc.perform(auth(get("/api/v1/insights?scope=NOPE"), analyst)).andExpect(status().isBadRequest());
    }

    @Test
    void acknowledgeThenDismissRecordsWhoAndClosesTheInsight() throws Exception {
        UUID id = insight("FLEET", null, "FLEET_IDLE_COST", "ACTIVE");
        User analyst = user("Analyst");
        String t = token(analyst);
        mvc.perform(json(patch("/api/v1/insights/" + id), t, "{\"status\":\"ACKNOWLEDGED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.acknowledgedBy").value(analyst.getId().toString()));
        mvc.perform(json(patch("/api/v1/insights/" + id), t, "{\"status\":\"DISMISSED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DISMISSED"));
        mvc.perform(json(patch("/api/v1/insights/" + id), t, "{\"status\":\"ACKNOWLEDGED\"}"))
                .andExpect(status().isConflict());
        mvc.perform(json(patch("/api/v1/insights/" + id), t, "{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(json(patch("/api/v1/insights/" + UUID.randomUUID()), t, "{\"status\":\"DISMISSED\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void insightsRequireAnalyticsPermission() throws Exception {
        mvc.perform(auth(get("/api/v1/insights"), token(user("Driver")))).andExpect(status().isForbidden());
    }

    @Test
    void fleetPolicyCanBeReadByAnalystsAndChangedOnlyByAdmins() throws Exception {
        User admin = user("Admin");
        String body = """
                {"speedLimitKmh":80,"harshAccelMs2":0.8,"harshBrakeMs2":1.6,"idleThresholdS":600,
                 "fuelPricePerLiter":6.49,"vehicleTypes":[{"vehicleType":"RIGID_TRUCK","economyRpmMax":1900}]}""";
        mvc.perform(json(put("/api/v1/fleet-policy"), token(user("Analyst")), body)).andExpect(status().isForbidden());
        mvc.perform(json(put("/api/v1/fleet-policy"), token(admin), body)).andExpect(status().isOk())
                .andExpect(jsonPath("$.speedLimitKmh").value(80.0))
                .andExpect(jsonPath("$.updatedBy").value(admin.getId().toString()));
        mvc.perform(auth(get("/api/v1/fleet-policy"), token(user("Analyst")))).andExpect(status().isOk())
                .andExpect(jsonPath("$.idleThresholdS").value(600))
                .andExpect(jsonPath("$.vehicleTypes[?(@.vehicleType=='RIGID_TRUCK')].economyRpmMax").value(1900));
        mvc.perform(json(put("/api/v1/fleet-policy"), token(admin), body.replace("80,", "500,")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("speedLimitKmh")));
    }
}
