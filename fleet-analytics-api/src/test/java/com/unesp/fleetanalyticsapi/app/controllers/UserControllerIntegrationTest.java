package com.unesp.fleetanalyticsapi.app.controllers;

import com.unesp.fleetanalyticsapi.model.entities.crud.Role;
import com.unesp.fleetanalyticsapi.model.repositories.RoleRepository;
import com.unesp.fleetanalyticsapi.model.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cadastro e edição de usuários de ponta a ponta (HTTP → validação → serviço → banco). Usa o banco configurado
 * por DATABASE_URL; cada teste é revertido ao final (@Transactional), então nada permanece nas tabelas.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(authorities = {"users:create", "users:read", "users:management"})
class UserControllerIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private Role role;

    @BeforeEach
    void createRole() {
        role = roleRepository.save(Role.builder().name("IT-" + UUID.randomUUID()).build());
    }

    private static String json(String username, String email, String password, Object roleId) {
        return """
                {"username":"%s","email":"%s","password":"%s","roleId":%s,
                 "firstName":"João","lastName":"Silva","phone":"11999990000"}
                """.formatted(username, email, password, roleId == null ? "null" : "\"" + roleId + "\"");
    }

    private ResultActions create(String body) throws Exception {
        return mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String createOk(String username, String email) throws Exception {
        String response = create(json(username, email, "secret1", role.getId()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(response, "$.userId");
    }

    // ---------------------------------------------------------------------------------------------
    // cadastro

    @Test
    void createsDriverAndStoresHashedPassword() throws Exception {
        create(json("motorista01", "m01@fleet.test", "secret1", role.getId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("motorista01"))
                .andExpect(jsonPath("$.email").value("m01@fleet.test"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.roleName").value(role.getName()))
                .andExpect(jsonPath("$.password").doesNotExist());

        var saved = userRepository.findByUsername("motorista01").orElseThrow();
        assertThat(saved.getPassword()).isNotEqualTo("secret1");
        assertThat(passwordEncoder.matches("secret1", saved.getPassword())).isTrue();
    }

    @Test
    void shortUsernameIsRejectedWithFieldNamedInMessage() throws Exception {
        create(json("joao", "j@fleet.test", "secret1", role.getId()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("username")));
    }

    @Test
    void invalidEmailMissingPasswordAndMissingRoleAreRejected() throws Exception {
        create(json("motorista02", "not-an-email", "secret1", role.getId()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("email")));
        create(json("motorista02", "m02@fleet.test", "", role.getId()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("password")));
        create(json("motorista02", "m02@fleet.test", "secret1", null))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("roleId")));
    }

    @Test
    void unknownRoleIsNotFound() throws Exception {
        create(json("motorista03", "m03@fleet.test", "secret1", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void duplicateUsernameIsConflict() throws Exception {
        createOk("motorista04", "m04@fleet.test");
        create(json("motorista04", "other@fleet.test", "secret1", role.getId()))
                .andExpect(status().isConflict());
    }

    @Test
    void duplicateEmailIsConflictNotServerError() throws Exception {
        createOk("motorista05", "m05@fleet.test");
        create(json("motorista06", "m05@fleet.test", "secret1", role.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Email")));
    }

    @Test
    void malformedBodyIsBadRequest() throws Exception {
        create("{not json").andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "users:read")
    void createRequiresPermission() throws Exception {
        create(json("motorista07", "m07@fleet.test", "secret1", role.getId())).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = {})
    void editRequiresPermission() throws Exception {
        mvc.perform(put("/api/v1/users/" + UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\":\"A\"}")).andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------------------------------------
    // edição

    private ResultActions update(String userId, String body) throws Exception {
        return mvc.perform(put("/api/v1/users/" + userId).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void updatesProvidedFieldsAndKeepsTheRest() throws Exception {
        String id = createOk("motorista10", "m10@fleet.test");

        update(id, "{\"lastName\":\"Souza\",\"phone\":\"2233\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("João"))
                .andExpect(jsonPath("$.lastName").value("Souza"))
                .andExpect(jsonPath("$.phone").value("2233"))
                .andExpect(jsonPath("$.email").value("m10@fleet.test"))
                .andExpect(jsonPath("$.username").value("motorista10"));
    }

    @Test
    void canResendOwnEmailButNotAnotherUsers() throws Exception {
        String first = createOk("motorista11", "m11@fleet.test");
        createOk("motorista12", "m12@fleet.test");

        update(first, "{\"email\":\"m11@fleet.test\",\"firstName\":\"Novo\"}").andExpect(status().isOk());
        update(first, "{\"email\":\"m12@fleet.test\"}").andExpect(status().isConflict());
        update(first, "{\"email\":\"livre@fleet.test\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("livre@fleet.test"));
    }

    @Test
    void invalidEmailOnUpdateIsRejected() throws Exception {
        String id = createOk("motorista13", "m13@fleet.test");
        update(id, "{\"email\":\"nope\"}").andExpect(status().isUnprocessableEntity());
    }

    @Test
    void updateOfUnknownUserIsNotFound() throws Exception {
        update(UUID.randomUUID().toString(), "{\"firstName\":\"A\"}").andExpect(status().isNotFound());
    }
}
