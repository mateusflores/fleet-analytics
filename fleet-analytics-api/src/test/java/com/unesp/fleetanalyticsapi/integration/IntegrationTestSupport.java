package com.unesp.fleetanalyticsapi.integration;

import com.jayway.jsonpath.JsonPath;
import com.unesp.fleetanalyticsapi.model.entities.crud.Role;
import com.unesp.fleetanalyticsapi.model.entities.crud.User;
import com.unesp.fleetanalyticsapi.model.entities.crud.Vehicle;
import com.unesp.fleetanalyticsapi.model.enums.FuelType;
import com.unesp.fleetanalyticsapi.model.enums.VehicleType;
import com.unesp.fleetanalyticsapi.model.repositories.RoleRepository;
import com.unesp.fleetanalyticsapi.model.repositories.UserRepository;
import com.unesp.fleetanalyticsapi.model.repositories.VehicleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base dos testes de integração: contexto completo, MockMvc com a cadeia de segurança real (JWT emitido pelo
 * próprio /auth/login) e o banco de DATABASE_URL. Cada teste roda numa transação revertida ao final — nada
 * permanece no banco. Use um banco descartável (ver fleet-analytics-api/README ou AGENTS.md).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public abstract class IntegrationTestSupport {

    protected static final String PASSWORD = "secret-pass";

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected UserRepository userRepository;
    @Autowired
    protected RoleRepository roleRepository;
    @Autowired
    protected VehicleRepository vehicleRepository;
    @Autowired
    protected PasswordEncoder passwordEncoder;
    @Autowired
    protected JdbcTemplate jdbc;

    protected Role role(String name) {
        return roleRepository.findAll().stream().filter(r -> r.getName().equals(name)).findFirst()
                .orElseThrow(() -> new IllegalStateException("perfil não semeado: " + name));
    }

    protected User user(String roleName) {
        return user(roleName, true);
    }

    protected User user(String roleName, boolean enabled) {
        String username = "it." + UUID.randomUUID().toString().substring(0, 8);
        return userRepository.saveAndFlush(User.builder().username(username).email(username + "@it.test")
                .password(passwordEncoder.encode(PASSWORD)).firstName("Teste").lastName(roleName)
                .enabled(enabled).role(role(roleName)).build());
    }

    protected Vehicle vehicle(boolean active) {
        String suffix = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        return vehicleRepository.saveAndFlush(Vehicle.builder().serialNumber("IT-" + suffix).licensePlate("I" + suffix)
                .brand("Marca").model("Modelo").vehicleType(VehicleType.RIGID_TRUCK).fuelType(FuelType.DIESEL)
                .nickname("Caminhão " + suffix).active(active).build());
    }

    protected ResultActions loginRaw(String username, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)));
    }

    /** {accessToken, refreshToken} */
    protected String[] login(User u) throws Exception {
        String body = loginRaw(u.getUsername(), PASSWORD).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return new String[]{JsonPath.read(body, "$.accessToken"), JsonPath.read(body, "$.refreshToken")};
    }

    protected String token(User u) throws Exception {
        return login(u)[0];
    }

    protected static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder req, String token) {
        return req.header("Authorization", "Bearer " + token);
    }

    protected static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder req, String token, String body) {
        return auth(req, token).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    protected static String id(ResultActions r, String path) throws Exception {
        return JsonPath.read(r.andReturn().getResponse().getContentAsString(), path);
    }
}
