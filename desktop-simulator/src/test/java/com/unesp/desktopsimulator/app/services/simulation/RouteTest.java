package com.unesp.desktopsimulator.app.services.simulation;

import com.unesp.desktopsimulator.app.services.simulation.route.Route;
import com.unesp.desktopsimulator.app.services.simulation.route.RouteCatalog;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RouteTest {

    private final RouteCatalog catalog = new RouteCatalog();

    @Test
    void loadsAllPackagedRoutes() {
        assertThat(catalog.all()).hasSize(RouteCatalog.ROUTE_IDS.size());
        assertThat(catalog.get("campinas-sao-paulo").lengthM()).isBetween(80_000.0, 100_000.0);
    }

    @Test
    void smoothedGradeAndLimitsAreBounded() {
        for (Route r : catalog.all()) {
            for (double d = 0; d < r.lengthM(); d += 50) {
                assertThat(Math.abs(r.gradeAt(d))).isLessThanOrEqualTo(0.08);
                assertThat(r.speedLimitAt(d)).isBetween(20.0, 120.0);
            }
        }
    }

    @Test
    void serraDescendsTowardsTheCoast() {
        Route serra = catalog.get("sao-paulo-santos");
        double first = serra.points().get(0).elevationM();
        double last = serra.points().get(serra.points().size() - 1).elevationM();
        assertThat(first - last).isGreaterThan(600);
    }

    @Test
    void positionIsContinuousAlongTheRoute() {
        Route r = catalog.get("campinas-urbano");
        double[] prev = r.latLonAt(0);
        for (double d = 10; d < r.lengthM(); d += 10) {
            double[] cur = r.latLonAt(d);
            double meters = Math.hypot(cur[0] - prev[0], (cur[1] - prev[1]) * Math.cos(Math.toRadians(cur[0]))) * 111_320;
            assertThat(meters).isLessThan(15);
            prev = cur;
        }
    }

    @Test
    void defaultRouteIsStablePerVehicle() {
        UUID id = UUID.randomUUID();
        assertThat(catalog.defaultFor(id).id()).isEqualTo(catalog.defaultFor(id).id());
    }
}
