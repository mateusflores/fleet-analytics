package com.unesp.desktopsimulator.app.services.simulation;

import com.unesp.desktopsimulator.model.FaultSeverity;
import com.unesp.desktopsimulator.model.FaultType;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Falhas ativas de um veículo. Pode ser alterado pela interface enquanto a simulação roda.
 *
 * <p>Uma falha não aparece de uma vez: sua intensidade cresce linearmente durante {@link #RAMP_S} segundos
 * de simulação a partir do momento em que o modelo a percebe — como um defeito que se agrava.
 */
public final class FaultSet {

    public static final double RAMP_S = 120.0;

    private static final class Active {
        final FaultSeverity severity;
        volatile double onsetS = Double.NaN;

        Active(FaultSeverity severity) {
            this.severity = severity;
        }
    }

    private final Map<FaultType, Active> active = new ConcurrentHashMap<>();

    public void add(FaultType type, FaultSeverity severity) {
        active.compute(type, (k, old) -> old != null && old.severity == severity ? old : new Active(severity));
    }

    public void remove(FaultType type) {
        active.remove(type);
    }

    public void clear() {
        active.clear();
    }

    public boolean isEmpty() {
        return active.isEmpty();
    }

    public boolean contains(FaultType type) {
        return active.containsKey(type);
    }

    public Set<FaultType> types() {
        return Collections.unmodifiableSet(active.keySet());
    }

    public Map<FaultType, FaultSeverity> severities() {
        Map<FaultType, FaultSeverity> out = new EnumMap<>(FaultType.class);
        active.forEach((k, v) -> out.put(k, v.severity));
        return out;
    }

    /**
     * Fração já atingida da intensidade escolhida (0–1), sem alterar o estado — para a interface.
     * Retorna 0 se a falha ainda não foi percebida pelo modelo.
     */
    public double progress(FaultType type, double simTimeS) {
        Active a = active.get(type);
        if (a == null || Double.isNaN(a.onsetS)) return 0;
        return Math.max(0, Math.min(1.0, (simTimeS - a.onsetS) / RAMP_S));
    }

    /** Intensidade efetiva (0–1) da falha no instante {@code simTimeS}. */
    double level(FaultType type, double simTimeS) {
        Active a = active.get(type);
        if (a == null) return 0;
        if (Double.isNaN(a.onsetS)) a.onsetS = simTimeS;
        return a.severity.factor() * Math.min(1.0, (simTimeS - a.onsetS) / RAMP_S);
    }
}
