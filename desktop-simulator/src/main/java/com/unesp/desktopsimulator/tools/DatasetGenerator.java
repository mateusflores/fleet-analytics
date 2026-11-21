package com.unesp.desktopsimulator.tools;

import com.unesp.desktopsimulator.app.services.simulation.DriverStyle;
import com.unesp.desktopsimulator.app.services.simulation.FaultSet;
import com.unesp.desktopsimulator.app.services.simulation.SimulationOptions;
import com.unesp.desktopsimulator.app.services.simulation.VehicleModel;
import com.unesp.desktopsimulator.app.services.simulation.VehicleProfile;
import com.unesp.desktopsimulator.app.services.simulation.VehicleSnapshot;
import com.unesp.desktopsimulator.app.services.simulation.route.Route;
import com.unesp.desktopsimulator.app.services.simulation.route.RouteCatalog;
import com.unesp.desktopsimulator.model.FaultSeverity;
import com.unesp.desktopsimulator.model.FaultType;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Gera um histórico de frota em CSV (1 linha por segundo com motor ligado) sem interface e sem broker,
 * usando o mesmo {@link VehicleModel} da simulação em tempo real. Alimenta o treino da "Empresa B" no ml-service.
 *
 * <p>Cada veículo opera {@code --days} dias: jornada a partir das ~06:00, viagens de 1,5–3,5 h em rotas sorteadas,
 * com pausas de motor desligado (30–90 min) entre elas; partida a frio após pausas longas e no início do dia.
 *
 * <p>{@code --fault TIPO:INTENSIDADE:INICIO_S[:VEICULO]} (repetível com vírgula) ativa uma falha a partir de
 * INICIO_S segundos de operação do veículo (ex.: {@code OVERHEATING:SEVERE:3600:sim-01}).
 *
 * <pre>
 * ./mvnw -q compile exec:java -Dexec.mainClass=com.unesp.desktopsimulator.tools.DatasetGenerator \
 *     -Dexec.args="--out ../ml-service/data/synthetic/empresa-b.csv --vehicles 3 --days 2 --seed 42"
 * </pre>
 */
public final class DatasetGenerator {

    private static final String HEADER = "timestamp,vehicleId,speedKmh,engineRpm,engineTempC,fuelLevelPct,odometerKm,"
            + "batteryVoltage,loadPct,engineTorquePct,pedalPct,gear,oilKpa,fuelRateLph,boostKpa,intakeC,latitude,longitude";
    /** 2026-11-02 06:00 em São Paulo (UTC−3). */
    private static final long START_EPOCH = Instant.parse("2026-11-02T09:00:00Z").getEpochSecond();
    private static final double WORKDAY_S = 11 * 3600;

    private DatasetGenerator() {
    }

    record FaultPlan(FaultType type, FaultSeverity severity, double onsetS, String vehicle) {
        static List<FaultPlan> parse(String spec) {
            if (spec == null || spec.isBlank()) return List.of();
            return java.util.Arrays.stream(spec.split(",")).map(item -> {
                String[] p = item.split(":");
                return new FaultPlan(FaultType.valueOf(p[0]), FaultSeverity.valueOf(p[1]),
                        Double.parseDouble(p[2]), p.length > 3 ? p[3] : null);
            }).toList();
        }
    }

    public static void main(String[] args) throws IOException {
        Map<String, String> opt = parse(args);
        Path out = Path.of(opt.getOrDefault("out", "empresa-b.csv"));
        int vehicles = Integer.parseInt(opt.getOrDefault("vehicles", "3"));
        int days = Integer.parseInt(opt.getOrDefault("days", "2"));
        long seed = Long.parseLong(opt.getOrDefault("seed", "42"));
        VehicleProfile profile = "heavy".equals(opt.get("profile")) ? VehicleProfile.HEAVY_TRACTOR : VehicleProfile.MEDIUM_RIGID;

        List<FaultPlan> faults = FaultPlan.parse(opt.get("fault"));
        RouteCatalog catalog = new RouteCatalog();
        if (out.getParent() != null) Files.createDirectories(out.getParent());
        long rows = 0;
        try (BufferedWriter w = Files.newBufferedWriter(out)) {
            w.write(HEADER);
            w.newLine();
            for (int v = 0; v < vehicles; v++) {
                String id = String.format("sim-%02d", v + 1);
                DriverStyle style = v % 3 == 1 ? DriverStyle.AGGRESSIVE : DriverStyle.ECONOMIC;
                List<FaultPlan> mine = faults.stream().filter(f -> f.vehicle() == null || f.vehicle().equals(id)).toList();
                rows += simulateVehicle(w, id, profile, style, catalog.all(), days, mine, new Random(seed * 1_000 + v));
            }
        }
        System.out.printf("%d linhas, %d veículos × %d dias (%s) → %s%n", rows, vehicles, days, profile.name(), out);
    }

    private static long simulateVehicle(BufferedWriter w, String id, VehicleProfile profile, DriverStyle style,
                                        List<Route> routes, int days, List<FaultPlan> faults, Random rng)
            throws IOException {
        long rows = 0;
        double operated = 0;
        double fuel = 0.95;
        double odometer = 40_000 + rng.nextDouble() * 200_000;
        for (int day = 0; day < days; day++) {
            double ambient = 18 + rng.nextDouble() * 12;
            long clock = START_EPOCH + day * 86_400L + (long) (rng.nextDouble() * 3600);
            double worked = 0;
            double sinceStop = Double.MAX_VALUE;
            while (worked < WORKDAY_S) {
                Route route = routes.get(rng.nextInt(routes.size()));
                boolean warm = sinceStop < 45 * 60;
                SimulationOptions o = new SimulationOptions(style, warm, ambient, 0.3 + 0.7 * rng.nextDouble(),
                        fuel, odometer, rng.nextLong());
                FaultSet faultSet = new FaultSet();
                VehicleModel model = new VehicleModel(profile, route, o, faultSet);
                int duration = (int) (5400 + rng.nextDouble() * 7200);
                VehicleSnapshot s = null;
                for (int k = 0; k < duration; k++) {
                    for (FaultPlan f : faults) {
                        if (operated >= f.onsetS() && !faultSet.contains(f.type())) faultSet.add(f.type(), f.severity());
                    }
                    operated++;
                    s = model.step(1.0);
                    writeRow(w, clock + k, id, s);
                    rows++;
                }
                fuel = s.fuelLevelPct() / 100;
                odometer = s.odometerKm();
                double pause = 1800 + rng.nextDouble() * 3600;
                clock += duration + (long) pause;
                worked += duration + pause;
                sinceStop = pause;
                if (fuel < 0.25) fuel = 0.95; // abastecimento durante a pausa
            }
        }
        return rows;
    }

    private static void writeRow(BufferedWriter w, long ts, String id, VehicleSnapshot s) throws IOException {
        w.write(String.format(Locale.ROOT, "%d,%s,%.3f,%d,%.0f,%.1f,%.3f,%.2f,%.1f,%.1f,%.1f,%d,%.0f,%.2f,%.0f,%.0f,%.6f,%.6f",
                ts, id, s.speedKmh(), s.engineRpm(), s.coolantC(), s.fuelLevelPct(), s.odometerKm(), s.batteryV(),
                s.loadPct(), s.engineTorquePct(), s.pedalPct(), s.gear(), s.oilKpa(), s.fuelRateLph(), s.boostKpa(),
                s.intakeC(), s.latitude(), s.longitude()));
        w.newLine();
    }

    private static Map<String, String> parse(String[] args) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i + 1 < args.length; i += 2) {
            if (!args[i].startsWith("--")) throw new IllegalArgumentException("argumento inesperado: " + args[i]);
            m.put(args[i].substring(2), args[i + 1]);
        }
        return m;
    }
}
