package com.unesp.fleetanalyticsapi.integration;

import com.unesp.fleetanalyticsapi.app.dtos.messaging.TelemetryMessage;
import com.unesp.fleetanalyticsapi.app.messaging.TelemetryConsumer;
import com.unesp.fleetanalyticsapi.model.entities.crud.Vehicle;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RoleAndTelemetryIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private TelemetryConsumer consumer;

    private String permissionId(String name) {
        return jdbc.queryForObject("SELECT id::text FROM fleet.permissions WHERE name = ?", String.class, name);
    }

    @Test
    void roleLifecycleAndIntegrityRules() throws Exception {
        String admin = token(user("Admin"));
        String name = "IT-" + UUID.randomUUID();
        String body = "{\"name\":\"%s\",\"permissionIds\":[\"%s\"]}".formatted(name, permissionId("vehicles:view"));
        String roleId = id(mvc.perform(json(post("/api/v1/roles"), admin, body)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.permissions[0].name").value("vehicles:view")), "$.id");
        mvc.perform(json(post("/api/v1/roles"), admin, body)).andExpect(status().isConflict());
        mvc.perform(json(post("/api/v1/roles"), admin, "{\"name\":\"X%s\",\"permissionIds\":[\"%s\"]}"
                .formatted(UUID.randomUUID(), UUID.randomUUID()))).andExpect(status().isBadRequest());
        // perfil em uso por um usuário não pode ser apagado
        jdbc.update("INSERT INTO fleet.users (id, username, email, password, is_enabled, role_id) "
                + "VALUES (gen_random_uuid(), ?, ?, 'x', true, ?::uuid)", "it.r" + roleId.substring(0, 6),
                roleId.substring(0, 6) + "@it.test", roleId);
        mvc.perform(auth(delete("/api/v1/roles/" + roleId), admin)).andExpect(status().isConflict());
        mvc.perform(auth(delete("/api/v1/roles/" + UUID.randomUUID()), admin)).andExpect(status().isNotFound());
    }

    @Test
    void telemetryPersistedByTheConsumerIsServedWithExtendedSignalsAndNulls() throws Exception {
        Vehicle v = vehicle(true);
        Instant t = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        consumer.handleTelemetry(new TelemetryMessage(v.getId(), null, t, 1500, 80.0, 90.0, 1000.0, 27.5,
                72.0, 40.0, 35.0, 20.0, 6, 300.0, 18.5, 150.0, 35.0));
        consumer.handleTelemetry(new TelemetryMessage(v.getId(), null, t.plusSeconds(1), 1400, 80.0, 90.0, 1000.1, 27.4));

        mvc.perform(auth(get("/api/v1/telemetry/vehicle-data?vehicleId=%s&from=%s&to=%s"
                        .formatted(v.getId(), t.minusSeconds(5), t.plusSeconds(5))), token(user("Analyst"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].oilKpa").value(300.0))
                .andExpect(jsonPath("$[0].gear").value(6))
                .andExpect(jsonPath("$[1].engineRpm").value(1400))
                .andExpect(jsonPath("$[1].oilKpa").doesNotExist());
    }
}
