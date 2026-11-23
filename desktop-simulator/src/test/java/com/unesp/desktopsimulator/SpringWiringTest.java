package com.unesp.desktopsimulator;

import com.unesp.desktopsimulator.app.services.client.RestApiClient;
import com.unesp.desktopsimulator.infra.config.RestClientConfig;
import com.unesp.desktopsimulator.app.services.simulation.route.RouteCatalog;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/** Garante que os beans do simulador são instanciáveis pelo Spring (sem MQTT, API nem interface gráfica). */
class SpringWiringTest {

    @Import({RestClientConfig.class, RestApiClient.class, RouteCatalog.class})
    static class Config {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    @Test
    void restApiClientAndRouteCatalogAreCreatedByTheContainer() {
        try (var ctx = new AnnotationConfigApplicationContext(Config.class)) {
            assertThat(ctx.getBean(RestApiClient.class)).isNotNull();
            assertThat(ctx.getBean(RouteCatalog.class).all()).isNotEmpty();
        }
    }
}
