package com.unesp.desktopsimulator.app.services.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RestApiClientTest {

    private static final String VEHICLES = "/api/v1/vehicles?size=1000";
    private static final String EMPTY_PAGE = "{\"content\":[]}";

    private MockRestServiceServer server;
    private MutableClock clock;
    private RestApiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://api");
        server = MockRestServiceServer.bindTo(builder).build();
        clock = new MutableClock(Instant.parse("2026-11-21T12:00:00Z"));
        client = new RestApiClient(builder.build(), new ObjectMapper(), clock);
    }

    private static String tokens(String access, String refresh, long expiresIn) {
        return """
                {"accessToken":"%s","refreshToken":"%s","tokenType":"Bearer",
                 "userId":"00000000-0000-0000-0000-000000000001","expiresIn":%d}
                """.formatted(access, refresh, expiresIn);
    }

    private void expectLogin() {
        server.expect(requestTo("http://api/api/v1/auth/login"))
                .andRespond(withSuccess(tokens("a1", "r1", 900), MediaType.APPLICATION_JSON));
    }

    @Test
    void usesCurrentTokenWhileValid() {
        expectLogin();
        server.expect(requestTo("http://api" + VEHICLES))
                .andExpect(header("Authorization", "Bearer a1"))
                .andRespond(withSuccess(EMPTY_PAGE, MediaType.APPLICATION_JSON));

        client.login("u", "p");
        clock.advance(Duration.ofMinutes(10));
        assertThat(client.fetchVehicles()).isEmpty();
        server.verify();
    }

    @Test
    void refreshesBeforeExpiry() {
        expectLogin();
        server.expect(requestTo("http://api/api/v1/auth/refresh"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"refreshToken\":\"r1\"}"))
                .andRespond(withSuccess(tokens("a2", "r2", 900), MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://api" + VEHICLES))
                .andExpect(header("Authorization", "Bearer a2"))
                .andRespond(withSuccess(EMPTY_PAGE, MediaType.APPLICATION_JSON));

        client.login("u", "p");
        clock.advance(Duration.ofMinutes(14).plusSeconds(30)); // dentro da margem de 60 s
        client.fetchVehicles();
        server.verify();
    }

    @Test
    void refreshesAndRetriesOnUnauthorized() {
        expectLogin();
        server.expect(requestTo("http://api" + VEHICLES))
                .andExpect(header("Authorization", "Bearer a1"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        server.expect(requestTo("http://api/api/v1/auth/refresh"))
                .andRespond(withSuccess(tokens("a2", "r2", 900), MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://api" + VEHICLES))
                .andExpect(header("Authorization", "Bearer a2"))
                .andRespond(withSuccess(EMPTY_PAGE, MediaType.APPLICATION_JSON));

        client.login("u", "p");
        client.fetchVehicles();
        server.verify();
    }

    @Test
    void sessionExpiresWhenRefreshIsRejected() {
        expectLogin();
        server.expect(requestTo("http://api/api/v1/auth/refresh"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        client.login("u", "p");
        clock.advance(Duration.ofMinutes(20));
        assertThatThrownBy(client::fetchVehicles).isInstanceOf(SessionExpiredException.class);
        assertThat(client.isAuthenticated()).isFalse();
        server.verify();
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
