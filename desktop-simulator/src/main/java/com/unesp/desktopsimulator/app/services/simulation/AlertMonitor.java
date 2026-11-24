package com.unesp.desktopsimulator.app.services.simulation;

import com.unesp.desktopsimulator.model.telemetry.Message;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Alertas por limite fixo (os "alarmes do painel"), avaliados a cada leitura de um veículo.
 *
 * <p>Cada tipo tem três níveis (normal, WARNING, CRITICAL) e histerese: o evento é emitido quando o nível sobe
 * e o alerta só é rearmado quando o sinal volta para além do limite de retorno. Isso evita uma rajada de eventos
 * quando a leitura oscila em torno do limite. Os limites de tensão escalam com o sistema (12 V / 24 V).
 * Os desvios sutis ficam para o serviço de ML; aqui ficam só as condições claramente anormais.
 */
public final class AlertMonitor {

    enum Kind { ENGINE_OVERHEAT, LOW_OIL_PRESSURE, LOW_BATTERY, LOW_FUEL, RPM_LIMIT }

    /** Engine em funcionamento há pelo menos isso antes de avaliar óleo e carga da bateria (partida). */
    private static final double SETTLE_S = 30;

    private final VehicleProfile profile;
    private final Map<Kind, Integer> level = new EnumMap<>(Kind.class);
    private double runningSinceS = Double.NaN;

    public AlertMonitor(VehicleProfile profile) {
        this.profile = profile;
        for (Kind k : Kind.values()) level.put(k, 0);
    }

    public List<Message> check(VehicleSnapshot s, Instant now) {
        boolean running = s.engineRpm() > 300;
        if (!running) runningSinceS = Double.NaN;
        else if (Double.isNaN(runningSinceS)) runningSinceS = s.elapsedS();
        boolean settled = running && s.elapsedS() - runningSinceS >= SETTLE_S;
        double k = profile.voltageScale();

        List<Message> out = new ArrayList<>();
        evaluate(out, now, Kind.ENGINE_OVERHEAT, high(s.coolantC(), 105, 110, 100),
                "Engine coolant temperature high at %.0f°C", "Engine coolant temperature critical at %.0f°C", s.coolantC());
        int oil = settled ? low(s.oilKpa(), 150, 90, 190) : 0;
        evaluate(out, now, Kind.LOW_OIL_PRESSURE, settled ? oil : keepOrReset(Kind.LOW_OIL_PRESSURE, running),
                "Engine oil pressure low at %.0f kPa", "Engine oil pressure critical at %.0f kPa", s.oilKpa());
        int bat = settled ? low(s.batteryV(), 13.0 * k, 12.2 * k, 13.4 * k) : 0;
        evaluate(out, now, Kind.LOW_BATTERY, settled ? bat : keepOrReset(Kind.LOW_BATTERY, running),
                "Battery voltage low at %.2f V (charging fault)", "Battery voltage critical at %.2f V", s.batteryV());
        evaluate(out, now, Kind.LOW_FUEL, low(s.fuelLevelPct(), 15, 8, 18),
                "Fuel level low at %.1f%%", "Fuel level critical at %.1f%%", s.fuelLevelPct());
        evaluate(out, now, Kind.RPM_LIMIT, high(s.engineRpm(), profile.maxRpm() - 20, Double.MAX_VALUE, profile.maxRpm() - 200),
                "Engine RPM at %.0f reached the governor limit", "Engine RPM at %.0f", s.engineRpm());
        return out;
    }

    /** Antes de estabilizar, mantém o estado se o motor segue ligado (evita rearmar) e zera se desligou. */
    private int keepOrReset(Kind kind, boolean running) {
        return running ? -1 : 0;
    }

    /** Nível para um sinal em que valores altos são ruins; -1 = manter o nível atual (zona de histerese). */
    private static int high(double x, double warn, double crit, double reset) {
        if (x >= crit) return 2;
        if (x >= warn) return 1;
        if (x < reset) return 0;
        return -1;
    }

    private static int low(double x, double warn, double crit, double reset) {
        if (x <= crit) return 2;
        if (x <= warn) return 1;
        if (x > reset) return 0;
        return -1;
    }

    private void evaluate(List<Message> out, Instant now, Kind kind, int newLevel,
                          String warnFmt, String critFmt, double value) {
        int current = level.get(kind);
        if (newLevel < 0) return;
        if (newLevel > current) {
            boolean critical = newLevel == 2;
            out.add(new Message.Builder()
                    .timestamp(now)
                    .eventType(kind.name())
                    .severity(critical ? "CRITICAL" : "WARNING")
                    .message(String.format(critical ? critFmt : warnFmt, value))
                    .build());
            level.put(kind, newLevel);
        } else if (newLevel == 0) {
            level.put(kind, 0);
        }
    }
}
