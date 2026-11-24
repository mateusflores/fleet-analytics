package com.unesp.desktopsimulator.app.services.simulation;

/**
 * Condições de uma simulação.
 *
 * @param warmStart          motor já aquecido (evita a fase de aquecimento, ~20 min, que o ML não avalia)
 * @param ambientC           temperatura ambiente
 * @param payloadFraction    fração da carga útil máxima transportada (0–1)
 * @param initialFuelFraction nível inicial do tanque (0–1)
 * @param initialOdometerKm  odômetro inicial
 * @param seed               semente do gerador aleatório (mesma semente ⇒ mesma simulação)
 */
public record SimulationOptions(
        DriverStyle style,
        boolean warmStart,
        double ambientC,
        double payloadFraction,
        double initialFuelFraction,
        double initialOdometerKm,
        long seed
) {

    public static SimulationOptions defaults(long seed) {
        return new SimulationOptions(DriverStyle.ECONOMIC, true, 25.0, 0.6, 0.9,
                50_000 + Math.floorMod(seed, 250_000), seed);
    }

    public SimulationOptions withStyle(DriverStyle s) {
        return new SimulationOptions(s, warmStart, ambientC, payloadFraction, initialFuelFraction, initialOdometerKm, seed);
    }

    public SimulationOptions withWarmStart(boolean w) {
        return new SimulationOptions(style, w, ambientC, payloadFraction, initialFuelFraction, initialOdometerKm, seed);
    }
}
