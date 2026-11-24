package com.unesp.desktopsimulator.tools;

import com.unesp.desktopsimulator.app.services.simulation.AlertMonitor;
import com.unesp.desktopsimulator.app.services.simulation.DriverStyle;
import com.unesp.desktopsimulator.app.services.simulation.FaultSet;
import com.unesp.desktopsimulator.app.services.simulation.SimulationOptions;
import com.unesp.desktopsimulator.app.services.simulation.VehicleModel;
import com.unesp.desktopsimulator.app.services.simulation.VehicleProfile;
import com.unesp.desktopsimulator.app.services.simulation.VehicleSnapshot;
import com.unesp.desktopsimulator.app.services.simulation.route.Route;
import com.unesp.desktopsimulator.app.services.simulation.route.RouteCatalog;
import com.unesp.desktopsimulator.model.FaultType;
import com.unesp.desktopsimulator.model.telemetry.Message;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;

/**
 * Executa uma agenda de operação (um veículo × um dia por linha) com o mesmo {@link VehicleModel} da simulação em
 * tempo real e grava viagens, alertas e telemetria a 1 Hz. Os cenários (quem dirige o quê, estilo, ociosidade,
 * falhas que evoluem ao longo de semanas) são decididos fora daqui, pelo {@code ml_service.insights.demo} — o Java só
 * executa a física. Cada linha é independente (semente própria): a mesma agenda gera sempre os mesmos dados.
 *
 * <p>Agenda ({@code --schedule}, CSV com cabeçalho):
 * {@code date,vehicle,driver,style,idle_habit_s,ambient_c,faults,seed,odometer_km}, em que {@code faults} é
 * {@code TIPO=intensidade;...} (intensidade 0–1, ex.: {@code OIL_PRESSURE_DROP=0.4}).
 *
 * <p>Saída ({@code --out DIR}): {@code trips.csv}, {@code events.csv} e {@code telemetry.csv.gz}.
 *
 * <pre>
 * java -cp ... com.unesp.desktopsimulator.tools.HistoryGenerator --schedule agenda.csv --out data/demo/2026-01
 * </pre>
 */
public final class HistoryGenerator {

    static final String TELEMETRY_HEADER = "timestamp,vehicleId,tripId,speedKmh,engineRpm,engineTempC,fuelLevelPct,"
            + "odometerKm,batteryVoltage,loadPct,engineTorquePct,pedalPct,gear,oilKpa,fuelRateLph,boostKpa,intakeC,"
            + "latitude,longitude";
    /** Jornada começa ~06:00 em São Paulo (UTC−3) + até 1 h. */
    private static final long DAY_START_OFFSET_S = 9 * 3600;
    private static final double WORKDAY_S = 10 * 3600;
    /** Depois do tempo previsto, o motorista segue até a próxima parada (no máximo isso). */
    private static final int MAX_EXTRA_TO_STOP_S = 900;

    private HistoryGenerator() {
    }

    record DayPlan(LocalDate date, String vehicle, String driver, DriverStyle style, double idleHabitS,
                   double ambientC, Map<FaultType, Double> faults, long seed, double odometerKm) {

        static DayPlan parse(String line) {
            String[] p = line.split(",", -1);
            Map<FaultType, Double> faults = new EnumMap<>(FaultType.class);
            if (!p[6].isBlank()) {
                for (String item : p[6].split(";")) {
                    String[] kv = item.split("=");
                    faults.put(FaultType.valueOf(kv[0].trim()), Double.parseDouble(kv[1]));
                }
            }
            return new DayPlan(LocalDate.parse(p[0]), p[1], p[2], DriverStyle.valueOf(p[3]), Double.parseDouble(p[4]),
                    Double.parseDouble(p[5]), faults, Long.parseLong(p[7]), Double.parseDouble(p[8]));
        }
    }

    /** Destino das linhas geradas (arquivos na ferramenta; listas em memória nos testes). */
    interface Sink {
        void trip(UUID id, DayPlan day, long startEpoch, long endEpoch, boolean warmStart) throws IOException;

        void event(long epoch, DayPlan day, UUID tripId, Message m) throws IOException;

        void telemetry(long epoch, DayPlan day, UUID tripId, VehicleSnapshot s) throws IOException;
    }

    public static void main(String[] args) throws IOException {
        Map<String, String> opt = parse(args);
        Path schedule = Path.of(required(opt, "schedule"));
        Path out = Path.of(required(opt, "out"));
        VehicleProfile profile = "heavy".equals(opt.get("profile")) ? VehicleProfile.HEAVY_TRACTOR
                : VehicleProfile.MEDIUM_RIGID;
        List<DayPlan> days = Files.readAllLines(schedule).stream().skip(1).filter(l -> !l.isBlank())
                .map(DayPlan::parse).toList();
        Files.createDirectories(out);
        RouteCatalog catalog = new RouteCatalog();
        long rows;
        try (FileSink sink = new FileSink(out)) {
            for (DayPlan day : days) simulateDay(day, profile, catalog.all(), sink);
            rows = sink.telemetryRows;
        }
        System.out.printf("%d dias-veículo, %d linhas de telemetria → %s%n", days.size(), rows, out);
    }

    /** Simula a jornada de um veículo num dia: viagens com pausas de motor desligado entre elas. */
    static void simulateDay(DayPlan day, VehicleProfile profile, List<Route> routes, Sink sink) throws IOException {
        Random rng = new Random(day.seed());
        long dayStart = day.date().atStartOfDay(java.time.ZoneOffset.UTC).toEpochSecond() + DAY_START_OFFSET_S;
        long clock = dayStart + (long) (rng.nextDouble() * 3600);
        double fuel = 0.6 + 0.35 * rng.nextDouble();
        double odometer = day.odometerKm();
        double worked = 0;
        double sincePause = Double.MAX_VALUE;
        int k = 0;
        while (worked < WORKDAY_S * (0.85 + 0.25 * rng.nextDouble())) {
            Route route = routes.get(rng.nextInt(routes.size()));
            boolean warm = sincePause < 45 * 60;
            SimulationOptions o = new SimulationOptions(day.style(), warm, day.ambientC() + rng.nextGaussian(),
                    0.3 + 0.7 * rng.nextDouble(), fuel, odometer, rng.nextLong());
            FaultSet faults = new FaultSet();
            day.faults().forEach(faults::setIntensity);
            VehicleModel model = new VehicleModel(profile, route, o, faults);
            AlertMonitor alerts = new AlertMonitor(profile);
            UUID tripId = UUID.nameUUIDFromBytes((day.vehicle() + "|" + day.date() + "|" + k++)
                    .getBytes(StandardCharsets.UTF_8));

            model.extendIdle(idle(day.idleHabitS(), rng));
            int planned = (int) (5400 + rng.nextDouble() * 7200);
            long t = clock;
            VehicleSnapshot s = null;
            boolean endIdleApplied = false;
            int endIdleLeft = 0;
            for (int i = 0; ; i++) {
                if (i >= planned) {
                    if (!endIdleApplied && (model.isStationary() || i >= planned + MAX_EXTRA_TO_STOP_S)) {
                        if (!model.isStationary()) break; // não achou parada a tempo: encerra em movimento
                        endIdleLeft = (int) Math.max(30, idle(day.idleHabitS(), rng));
                        model.extendIdle(endIdleLeft);
                        endIdleApplied = true;
                    }
                    if (endIdleApplied && endIdleLeft-- <= 0) break;
                }
                s = model.step(1.0);
                sink.telemetry(t, day, tripId, s);
                for (Message m : alerts.check(s, Instant.ofEpochSecond(t))) sink.event(t, day, tripId, m);
                t++;
            }
            sink.trip(tripId, day, clock, t - 1, warm);
            fuel = s.fuelLevelPct() / 100;
            odometer = s.odometerKm();
            if (fuel < 0.25) fuel = 0.95; // abastecimento durante a pausa
            double pause = 1800 + rng.nextDouble() * 3600;
            worked += (t - clock) + pause;
            clock = t + (long) pause;
            sincePause = pause;
        }
    }

    /** Tempo parado com o motor ligado na partida/chegada: em torno do hábito do motorista. */
    private static double idle(double habitS, Random rng) {
        return habitS * (0.5 + rng.nextDouble());
    }

    private static final class FileSink implements Sink, AutoCloseable {
        private final BufferedWriter trips;
        private final BufferedWriter events;
        private final Writer telemetry;
        long telemetryRows;

        FileSink(Path dir) throws IOException {
            trips = Files.newBufferedWriter(dir.resolve("trips.csv"));
            trips.write("trip_id,vehicle,driver,date,start_epoch,end_epoch,warm_start\n");
            events = Files.newBufferedWriter(dir.resolve("events.csv"));
            events.write("epoch,vehicle,trip_id,event_type,severity,message\n");
            telemetry = new BufferedWriter(new OutputStreamWriter(
                    new GZIPOutputStream(Files.newOutputStream(dir.resolve("telemetry.csv.gz")), 1 << 16),
                    StandardCharsets.UTF_8), 1 << 16);
            telemetry.write(TELEMETRY_HEADER + "\n");
        }

        @Override
        public void trip(UUID id, DayPlan day, long startEpoch, long endEpoch, boolean warmStart) throws IOException {
            trips.write(id + "," + day.vehicle() + "," + day.driver() + "," + day.date() + "," + startEpoch + ","
                    + endEpoch + "," + warmStart + "\n");
        }

        @Override
        public void event(long epoch, DayPlan day, UUID tripId, Message m) throws IOException {
            events.write(epoch + "," + day.vehicle() + "," + tripId + "," + m.getEventType() + "," + m.getSeverity()
                    + ",\"" + m.getMessage().replace("\"", "'") + "\"\n");
        }

        @Override
        public void telemetry(long epoch, DayPlan day, UUID tripId, VehicleSnapshot s) throws IOException {
            telemetry.write(String.format(Locale.ROOT,
                    "%d,%s,%s,%.3f,%d,%.0f,%.1f,%.3f,%.2f,%.1f,%.1f,%.1f,%d,%.0f,%.2f,%.0f,%.0f,%.6f,%.6f%n",
                    epoch, day.vehicle(), tripId, s.speedKmh(), s.engineRpm(), s.coolantC(), s.fuelLevelPct(),
                    s.odometerKm(), s.batteryV(), s.loadPct(), s.engineTorquePct(), s.pedalPct(), s.gear(), s.oilKpa(),
                    s.fuelRateLph(), s.boostKpa(), s.intakeC(), s.latitude(), s.longitude()));
            telemetryRows++;
        }

        @Override
        public void close() throws IOException {
            trips.close();
            events.close();
            telemetry.close();
        }
    }

    private static String required(Map<String, String> opt, String key) {
        String v = opt.get(key);
        if (v == null) throw new IllegalArgumentException("--" + key + " é obrigatório");
        return v;
    }

    private static Map<String, String> parse(String[] args) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i + 1 < args.length; i += 2) {
            if (!args[i].startsWith("--")) throw new IllegalArgumentException("argumento inesperado: " + args[i]);
            m.put(args[i].substring(2), args[i + 1]);
        }
        return m;
    }

    /** Usado nos testes: coleta tudo em memória. */
    static final class MemorySink implements Sink {
        final List<UUID> trips = new ArrayList<>();
        final List<long[]> tripTimes = new ArrayList<>();
        final List<Message> events = new ArrayList<>();
        final List<VehicleSnapshot> telemetry = new ArrayList<>();

        @Override
        public void trip(UUID id, DayPlan day, long startEpoch, long endEpoch, boolean warmStart) {
            trips.add(id);
            tripTimes.add(new long[]{startEpoch, endEpoch});
        }

        @Override
        public void event(long epoch, DayPlan day, UUID tripId, Message m) {
            events.add(m);
        }

        @Override
        public void telemetry(long epoch, DayPlan day, UUID tripId, VehicleSnapshot s) {
            telemetry.add(s);
        }
    }
}
