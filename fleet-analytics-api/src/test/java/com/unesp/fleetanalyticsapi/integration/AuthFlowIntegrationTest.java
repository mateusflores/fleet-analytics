package com.unesp.fleetanalyticsapi.integration;

import com.unesp.fleetanalyticsapi.model.entities.crud.User;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Autenticação de ponta a ponta: login, rotas protegidas, rotação de refresh token, logout e usuário desativado. */
class AuthFlowIntegrationTest extends IntegrationTestSupport {

    @Test
    void loginReturnsTokensAndWrongPasswordIsUnauthorized() throws Exception {
        User u = user("Analyst");
        loginRaw(u.getUsername(), PASSWORD).andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.userId").value(u.getId().toString()));
        loginRaw(u.getUsername(), "wrong").andExpect(status().isUnauthorized());
        loginRaw("nobody-" + u.getId(), PASSWORD).andExpect(status().isUnauthorized());
    }

    @Test
    void protectedRoutesRequireAValidToken() throws Exception {
        mvc.perform(get("/api/v1/vehicles")).andExpect(status().isUnauthorized());
        mvc.perform(auth(get("/api/v1/vehicles"), "not-a-jwt")).andExpect(status().isUnauthorized());
        mvc.perform(auth(get("/api/v1/vehicles"), token(user("Analyst")))).andExpect(status().isOk());
    }

    @Test
    void missingPermissionIsForbidden() throws Exception {
        String driver = token(user("Driver"));
        String valid = """
                {"serialNumber":"IT-F1","licensePlate":"ITF1A23","brand":"b","model":"m","vehicleType":"RIGID_TRUCK"}""";
        mvc.perform(json(post("/api/v1/vehicles"), driver, valid)).andExpect(status().isForbidden());
        mvc.perform(auth(get("/api/v1/users"), driver)).andExpect(status().isForbidden());
    }

    @Test
    void refreshRotatesAndTheOldRefreshTokenCannotBeReused() throws Exception {
        String[] t = login(user("Analyst"));
        String body = "{\"refreshToken\":\"%s\"}".formatted(t[1]);
        mvc.perform(post("/api/v1/auth/refresh").contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isNotEmpty());
        mvc.perform(post("/api/v1/auth/refresh").contentType("application/json").content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void malformedRefreshTokenIsUnauthorizedNotServerError() throws Exception {
        mvc.perform(post("/api/v1/auth/refresh").contentType("application/json").content("{\"refreshToken\":\"x.y.z\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh").contentType("application/json").content("{}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void logoutInvalidatesBothTokens() throws Exception {
        String[] t = login(user("Analyst"));
        mvc.perform(json(post("/api/v1/auth/logout"), t[0], "{\"refreshToken\":\"%s\"}".formatted(t[1])))
                .andExpect(status().isNoContent());
        mvc.perform(auth(get("/api/v1/vehicles"), t[0])).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh").contentType("application/json")
                .content("{\"refreshToken\":\"%s\"}".formatted(t[1]))).andExpect(status().isUnauthorized());
    }

    @Test
    void cannotLogOutAnotherUsersSession() throws Exception {
        String[] mine = login(user("Analyst"));
        String[] theirs = login(user("Analyst"));
        mvc.perform(json(post("/api/v1/auth/logout"), mine[0], "{\"refreshToken\":\"%s\"}".formatted(theirs[1])))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh").contentType("application/json")
                .content("{\"refreshToken\":\"%s\"}".formatted(theirs[1]))).andExpect(status().isOk());
    }

    @Test
    void disabledUserLosesAccessImmediately() throws Exception {
        User u = user("Analyst");
        String[] t = login(u);
        u.setEnabled(false);
        userRepository.saveAndFlush(u);

        mvc.perform(auth(get("/api/v1/vehicles"), t[0])).andExpect(status().isUnauthorized());
        loginRaw(u.getUsername(), PASSWORD).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/refresh").contentType("application/json")
                .content("{\"refreshToken\":\"%s\"}".formatted(t[1]))).andExpect(status().isForbidden());
    }

    @Test
    void neverCreatedUserIsDisabledAndCannotLogin() throws Exception {
        User u = user("Analyst", false);
        loginRaw(u.getUsername(), PASSWORD).andExpect(status().isForbidden());
    }
}
