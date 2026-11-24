package com.unesp.desktopsimulator.tools;

import com.unesp.desktopsimulator.app.services.simulation.DriverStyle;
import com.unesp.desktopsimulator.app.services.simulation.VehicleProfile;
import com.unesp.desktopsimulator.app.services.simulation.VehicleSnapshot;
import com.unesp.desktopsimulator.app.services.simulation.route.Route;
import com.unesp.desktopsimulator.app.services.simulation.route.RouteCatalog;
import com.unesp.desktopsimulator.model.FaultType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HistoryGeneratorTest {

    private static final List<Route> ROUTES = new RouteCatalog().all();

    private static HistoryGenerator.DayPlan day(DriverStyle style, double idle, Map<FaultType, Double> faults, long seed) {
        return new HistoryGenerator.DayPlan(LocalDate.of(2026, 3, 10), "demo-01", "d01", style, idle, 25, faults,
                seed, 100_000);
    }

    private static HistoryGenerator.MemorySink run(HistoryGenerator.DayPlan d) throws Exception {
        var sink = new HistoryGenerator.MemorySink();
        HistoryGenerator.simulateDay(d, VehicleProfile.MEDIUM_RIGID, ROUTES, sink);
        return sink;
    }

    private static double fractionAbove(List<VehicleSnapshot> t, double kmh) {
        return t.stream().filter(s -> s.speedKmh() > kmh).count() / (double) t.size();
    }

    @Test
    void sameSeedSameDay() throws Exception {
        var a = run(day(DriverStyle.NORMAL, 60, Map.of(), 7));
        var b = run(day(DriverStyle.NORMAL, 60, Map.of(), 7));
        assertThat(a.trips).isEqualTo(b.trips);
        assertThat(a.telemetry).hasSameSizeAs(b.telemetry);
        assertThat(a.telemetry.get(5000)).isEqualTo(b.telemetry.get(5000));
        assertThat(a.trips).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void tripsAreSequentialWithinTheDay() throws Exception {
        var a = run(day(DriverStyle.NORMAL, 60, Map.of(), 11));
        for (int i = 1; i < a.tripTimes.size(); i++) {
            assertThat(a.tripTimes.get(i)[0]).isGreaterThan(a.tripTimes.get(i - 1)[1] + 1800);
        }
    }

    @Test
    void normalStyleSitsBetweenEconomicAndAggressive() throws Exception {
        double eco = 0, normal = 0, aggr = 0;
        for (long seed = 1; seed <= 3; seed++) {
            eco += fractionAbove(run(day(DriverStyle.ECONOMIC, 60, Map.of(), seed)).telemetry, 90);
            normal += fractionAbove(run(day(DriverStyle.NORMAL, 60, Map.of(), seed)).telemetry, 90);
            aggr += fractionAbove(run(day(DriverStyle.AGGRESSIVE, 60, Map.of(), seed)).telemetry, 90);
        }
        assertThat(eco).isLessThanOrEqualTo(normal);
        assertThat(normal).isLessThan(aggr);
    }

    @Test
    void fixedFaultIntensityLowersOilPressureFromTheStart() throws Exception {
        double healthy = meanOil(run(day(DriverStyle.NORMAL, 60, Map.of(), 3)).telemetry);
        double faulty = meanOil(run(day(DriverStyle.NORMAL, 60, Map.of(FaultType.OIL_PRESSURE_DROP, 0.4), 3)).telemetry);
        // nível 0,4 → alvo da pressão × (1 − 0,5 × 0,4) = −20%
        assertThat(faulty / healthy).isBetween(0.72, 0.88);
    }

    @Test
    void idleHabitAddsStationaryEngineOnTime() throws Exception {
        long quick = stationary(run(day(DriverStyle.NORMAL, 60, Map.of(), 5)).telemetry);
        long idler = stationary(run(day(DriverStyle.NORMAL, 1200, Map.of(), 5)).telemetry);
        assertThat(idler - quick).isGreaterThan(1200);
    }

    private static double meanOil(List<VehicleSnapshot> t) {
        return t.stream().filter(s -> s.speedKmh() > 30).mapToDouble(VehicleSnapshot::oilKpa).average().orElseThrow();
    }

    private static long stationary(List<VehicleSnapshot> t) {
        return t.stream().filter(s -> s.speedKmh() < 1 && s.engineRpm() > 300).count();
    }
}
