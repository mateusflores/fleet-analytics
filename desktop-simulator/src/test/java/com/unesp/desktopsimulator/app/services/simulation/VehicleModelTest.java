package com.unesp.desktopsimulator.app.services.simulation;

import com.unesp.desktopsimulator.app.services.simulation.route.Route;
import com.unesp.desktopsimulator.app.services.simulation.route.RouteCatalog;
import com.unesp.desktopsimulator.model.FaultSeverity;
import com.unesp.desktopsimulator.model.FaultType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class VehicleModelTest {

    private static RouteCatalog catalog;
    private static Route highway;

    @BeforeAll
    static void loadRoutes() {
        catalog = new RouteCatalog();
        highway = catalog.get("campinas-sao-paulo");
    }

    private static List<VehicleSnapshot> run(VehicleProfile profile, Route route, SimulationOptions o, FaultSet faults, int seconds) {
        VehicleModel m = new VehicleModel(profile, route, o, faults);
        List<VehicleSnapshot> out = new ArrayList<>(seconds);
        for (int i = 0; i < seconds; i++) out.add(m.step(1.0));
        return out;
    }

    private static List<VehicleSnapshot> run(VehicleProfile profile, SimulationOptions o, int seconds) {
        return run(profile, highway, o, new FaultSet(), seconds);
    }

    private static double median(List<VehicleSnapshot> s, ToDoubleFunction<VehicleSnapshot> f) {
        double[] v = s.stream().mapToDouble(f).sorted().toArray();
        return v.length == 0 ? Double.NaN : v[v.length / 2];
    }

    private static double mean(List<VehicleSnapshot> s, ToDoubleFunction<VehicleSnapshot> f) {
        return s.stream().mapToDouble(f).average().orElse(Double.NaN);
    }

    @Test
    void sameSeedReproducesTheSameSimulation() {
        SimulationOptions o = SimulationOptions.defaults(11);
        List<VehicleSnapshot> a = run(VehicleProfile.MEDIUM_RIGID, o, 600);
        List<VehicleSnapshot> b = run(VehicleProfile.MEDIUM_RIGID, o, 600);
        assertThat(a).isEqualTo(b);
    }

    @Test
    void mediumTruckMatchesT270Calibration() {
        List<VehicleSnapshot> s = run(VehicleProfile.MEDIUM_RIGID, SimulationOptions.defaults(3), 2 * 3600);
        VehicleProfile p = VehicleProfile.MEDIUM_RIGID;

        assertThat(s).allSatisfy(x -> {
            assertThat(x.engineRpm()).isBetween(0, p.maxRpm());
            assertThat(x.speedKmh()).isBetween(0.0, p.maxSpeedKmh() + 3.5);
            assertThat(x.gear()).isBetween(1, p.topGear());
        });
        for (int i = 1; i < s.size(); i++) {
            double a = (s.get(i).speedKmh() - s.get(i - 1).speedKmh()) / 3.6;
            assertThat(Math.abs(a)).as("aceleração em t=%d", i).isLessThan(3.6);
        }
        List<VehicleSnapshot> cruise = s.stream().filter(x -> x.speedKmh() > 75 && x.gear() == 5).toList();
        List<VehicleSnapshot> idle = s.stream().filter(x -> x.speedKmh() < 0.5 && x.elapsedS() > 60).toList();
        assertThat(cruise).hasSizeGreaterThan(600);
        // T270: 19,7 rpm/(km/h) em 5ª; consumo 15–18 L/h em cruzeiro e 2,6 L/h em marcha lenta
        assertThat(median(cruise, x -> x.engineRpm() / x.speedKmh())).isCloseTo(19.8, within(0.5));
        assertThat(median(cruise, VehicleSnapshot::fuelRateLph)).isBetween(13.0, 23.0);
        assertThat(median(idle, VehicleSnapshot::fuelRateLph)).isBetween(2.0, 3.5);
        assertThat(median(s, VehicleSnapshot::coolantC)).isBetween(86.0, 93.0);
        assertThat(median(s, VehicleSnapshot::batteryV)).isCloseTo(14.0, within(0.1));
        assertThat(median(cruise, VehicleSnapshot::oilKpa)).isBetween(300.0, 380.0);
    }

    @Test
    void heavyTractorUses24VoltsAndCruisesAtLowRpm() {
        List<VehicleSnapshot> s = run(VehicleProfile.HEAVY_TRACTOR, SimulationOptions.defaults(5), 3600);
        List<VehicleSnapshot> cruise = s.stream().filter(x -> x.speedKmh() > 75).toList();
        assertThat(median(s, VehicleSnapshot::batteryV)).isCloseTo(28.0, within(0.15));
        assertThat(median(cruise, x -> x.engineRpm())).isBetween(1150.0, 1550.0);
        assertThat(median(cruise, VehicleSnapshot::fuelRateLph)).isBetween(20.0, 40.0);
    }

    @Test
    void coldStartWarmsUpFromAmbient() {
        SimulationOptions cold = SimulationOptions.defaults(9).withWarmStart(false);
        List<VehicleSnapshot> s = run(VehicleProfile.MEDIUM_RIGID, cold, 3600);
        assertThat(s.get(0).coolantC()).isCloseTo(25.0, within(1.5));
        assertThat(s.get(5 * 60).coolantC()).isLessThan(70.0);
        assertThat(s.get(s.size() - 1).coolantC()).isBetween(84.0, 95.0);
        // óleo frio é mais viscoso: pressão em marcha lenta maior antes de aquecer
        assertThat(s.get(30).oilKpa()).isGreaterThan(VehicleProfile.MEDIUM_RIGID.oilIdleKpa());
    }

    @Test
    void economicDriverUsesCruiseControlOnHighway() {
        List<VehicleSnapshot> s = run(VehicleProfile.MEDIUM_RIGID, SimulationOptions.defaults(3), 2 * 3600);
        long released = s.stream().filter(x -> x.speedKmh() > 75 && x.pedalPct() == 0 && x.loadPct() > 20).count();
        assertThat(released).isGreaterThan(300);
    }

    @ParameterizedTest
    @EnumSource(value = FaultType.class, names = "GPS_DRIFT", mode = EnumSource.Mode.EXCLUDE)
    void severeFaultMovesItsSignalInTheExpectedDirection(FaultType fault) {
        SimulationOptions o = SimulationOptions.defaults(21);
        int onset = 1200, end = onset + 1800;
        List<VehicleSnapshot> base = run(VehicleProfile.MEDIUM_RIGID, highway, o, new FaultSet(), end);
        FaultSet faults = new FaultSet();
        VehicleModel m = new VehicleModel(VehicleProfile.MEDIUM_RIGID, highway, o, faults);
        List<VehicleSnapshot> faulty = new ArrayList<>();
        for (int i = 0; i < end; i++) {
            if (i == onset) faults.add(fault, FaultSeverity.SEVERE);
            faulty.add(m.step(1.0));
        }
        List<VehicleSnapshot> b = base.subList(onset + 300, end);
        List<VehicleSnapshot> f = faulty.subList(onset + 300, end);
        assertThat(faulty.subList(0, onset)).isEqualTo(base.subList(0, onset));
        switch (fault) {
            case OVERHEATING -> assertThat(f.stream().mapToDouble(VehicleSnapshot::coolantC).max().orElseThrow()).isGreaterThan(105);
            case BATTERY_DRAIN -> assertThat(mean(f, VehicleSnapshot::batteryV)).isLessThan(mean(b, VehicleSnapshot::batteryV) - 1.0);
            case FUEL_LEAK -> assertThat(f.get(f.size() - 1).fuelLevelPct()).isLessThan(b.get(b.size() - 1).fuelLevelPct() - 3);
            case ERRATIC_RPM -> assertThat(rpmRoughness(f)).isGreaterThan(1.5 * rpmRoughness(b));
            case OIL_PRESSURE_DROP -> assertThat(mean(f, VehicleSnapshot::oilKpa)).isLessThan(0.6 * mean(b, VehicleSnapshot::oilKpa));
            case BOOST_LEAK -> assertThat(mean(f, VehicleSnapshot::boostKpa)).isLessThan(0.7 * mean(b, VehicleSnapshot::boostKpa));
            case FUEL_OVERCONSUMPTION -> assertThat(mean(f, VehicleSnapshot::fuelRateLph)).isGreaterThan(1.2 * mean(b, VehicleSnapshot::fuelRateLph));
            default -> throw new IllegalStateException();
        }
    }

    @Test
    void lightOverheatingStaysBelowTheDashboardAlarm() {
        SimulationOptions o = SimulationOptions.defaults(21);
        FaultSet faults = new FaultSet();
        faults.add(FaultType.OVERHEATING, FaultSeverity.LIGHT);
        List<VehicleSnapshot> s = run(VehicleProfile.MEDIUM_RIGID, highway, o, faults, 3600);
        assertThat(s.stream().mapToDouble(VehicleSnapshot::coolantC).max().orElseThrow()).isLessThan(105);
    }

    @Test
    void gpsDriftDegradesAccuracy() {
        FaultSet faults = new FaultSet();
        faults.add(FaultType.GPS_DRIFT, FaultSeverity.SEVERE);
        List<VehicleSnapshot> s = run(VehicleProfile.MEDIUM_RIGID, highway, SimulationOptions.defaults(4), faults, 600);
        assertThat(mean(s.subList(300, 600), VehicleSnapshot::gpsAccuracyM)).isGreaterThan(20);
    }

    private static double rpmRoughness(List<VehicleSnapshot> s) {
        double sum = 0;
        for (int i = 1; i < s.size(); i++) sum += Math.abs(s.get(i).engineRpm() - s.get(i - 1).engineRpm());
        return sum / (s.size() - 1);
    }
}
