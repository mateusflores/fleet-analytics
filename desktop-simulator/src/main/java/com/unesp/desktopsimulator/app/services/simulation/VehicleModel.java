package com.unesp.desktopsimulator.app.services.simulation;

import com.unesp.desktopsimulator.app.services.simulation.route.Route;
import com.unesp.desktopsimulator.app.services.simulation.route.RouteKind;
import com.unesp.desktopsimulator.model.FaultType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Modelo de um veículo percorrendo uma rota, integrado em passos de até {@link #SUBSTEP_S} s.
 *
 * <p>Cadeia de causa e efeito (documentada em docs/modelo-veiculo.md):
 * rota (limite, rampa, paradas) → motorista (velocidade alvo, aceleração suave) → dinâmica longitudinal
 * (rolamento + arrasto + rampa + inércia) → câmbio e motor (rpm, torque, carga) → consumo → calor no
 * arrefecimento, turbo, admissão, óleo e sistema elétrico → sensores (ruído com memória e resolução J1939).
 * As falhas alteram parâmetros dessa cadeia, não as saídas.
 */
public final class VehicleModel {

    static final double SUBSTEP_S = 0.2;
    private static final double G = 9.81;
    private static final double AIR_DENSITY = 1.2;
    private static final double DIESEL_KG_PER_L = 0.835;
    private static final double DIESEL_KJ_PER_G = 42.8;
    private static final double HEAT_TO_COOLANT = 0.28;
    private static final double THERMOSTAT_OPEN_C = 86;
    private static final double THERMOSTAT_FULL_C = 96;
    private static final double FAN_ON_C = 97;
    private static final double FAN_OFF_C = 93;
    private static final double LOOKAHEAD_M = 800;
    private static final double REFUEL_BELOW = 0.25;
    /** Limitador de velocidade de frota (km/h acima do máximo do perfil). */
    private static final double LIMITER_MARGIN_KMH = 3;

    enum Phase { STARTING, DRIVING, STOPPED }

    record Stop(double atM, double durationS, String reason) {
    }

    private final VehicleProfile profile;
    private final Route route;
    private final SimulationOptions options;
    private final FaultSet faults;
    private final Random rng;
    private final double massKg;

    // ---- estado
    private double t;
    private Phase phase = Phase.STARTING;
    private double phaseTimer;
    private String stopReason = "";
    private boolean returning;
    private double legPos;
    private List<Stop> stops = new ArrayList<>();
    private int nextStop;
    private double v;
    private double accel;
    private int gear;
    private double shiftTimer;
    private double shiftCooldown;
    private double rpm;
    private double torqueNm;
    private boolean fuelCut;
    private double loadPct;
    private double torquePct;
    private double pedalPct;
    private double cruiseTimer;
    private double fuelRateLph;
    private double fuelL;
    private double odometerKm;
    private double coolant;
    private boolean fanOn;
    private double intake;
    private double boost;
    private double oil;
    private double soc = 1.0;
    private double batteryV;
    private double ambient;
    private double lastStepS = 1.0;

    // ---- ruídos com memória (AR(1))
    private final Ar1 speedVariation;
    private final Ar1 rpmNoise;
    private final Ar1 coolantNoise;
    private final Ar1 batteryNoise;
    private final Ar1 fuelSlosh;
    private final Ar1 gpsNorth;
    private final Ar1 gpsEast;
    private final double misfirePhase;

    public VehicleModel(VehicleProfile profile, Route route, SimulationOptions options, FaultSet faults) {
        this.profile = profile;
        this.route = route;
        this.options = options;
        this.faults = faults;
        this.rng = new Random(options.seed());
        this.massKg = profile.massKg() + profile.payloadMaxKg() * clamp(options.payloadFraction(), 0, 1);
        this.ambient = options.ambientC();
        this.fuelL = profile.tankL() * clamp(options.initialFuelFraction(), 0.02, 1);
        this.odometerKm = options.initialOdometerKm();
        this.gear = profile.startGear();
        this.coolant = options.warmStart() ? 88 + rng.nextDouble() * 2 : ambient;
        this.intake = ambient + 8;
        this.phaseTimer = options.warmStart() ? 20 + rng.nextDouble() * 20 : 60 + rng.nextDouble() * 90;
        this.batteryV = 12.6 * profile.voltageScale();

        speedVariation = new Ar1(60, 0.6, rng);
        rpmNoise = new Ar1(2, 6, rng);
        coolantNoise = new Ar1(20, 0.35, rng);
        batteryNoise = new Ar1(3, 0.035 * profile.voltageScale(), rng);
        fuelSlosh = new Ar1(4, 0.15, rng);
        gpsNorth = new Ar1(10, 1, rng);
        gpsEast = new Ar1(10, 1, rng);
        misfirePhase = rng.nextDouble() * 2 * Math.PI;
        planLeg();
    }

    // =====================================================================================================
    // API

    /** Avança a simulação em {@code dtS} segundos e devolve o estado resultante. */
    public VehicleSnapshot step(double dtS) {
        double remaining = Math.max(0, dtS);
        if (dtS > 0) lastStepS = dtS;
        while (remaining > 1e-9) {
            double h = Math.min(SUBSTEP_S, remaining);
            substep(h);
            remaining -= h;
        }
        return snapshot();
    }

    public VehicleProfile profile() {
        return profile;
    }

    public Route route() {
        return route;
    }

    public double elapsedS() {
        return t;
    }

    // =====================================================================================================
    // rota e paradas

    private double routeDist(double s) {
        return returning ? route.lengthM() - s : s;
    }

    private double limitMs(double s) {
        double lim = Math.min(route.speedLimitAt(routeDist(clamp(s, 0, route.lengthM()))), profile.maxSpeedKmh());
        return lim / 3.6;
    }

    private double grade(double s) {
        double g = route.gradeAt(routeDist(s));
        return returning ? -g : g;
    }

    /** Sorteia as paradas da perna (semáforos nos trechos urbanos, pedágio, entregas e a parada final). */
    private void planLeg() {
        double length = route.lengthM();
        List<Stop> list = new ArrayList<>();
        double s = 300 + rng.nextDouble() * 600;
        while (s < length - 300) {
            if (limitMs(s) * 3.6 <= 60) {
                if (rng.nextDouble() < 0.45) list.add(new Stop(s, 15 + rng.nextDouble() * 45, "Semáforo"));
                s += 350 + rng.nextDouble() * 550;
            } else {
                s += 200;
            }
        }
        if (route.kind() == RouteKind.HIGHWAY) list.add(new Stop(0.45 * length, 20 + rng.nextDouble() * 20, "Pedágio"));
        if (route.kind() == RouteKind.URBAN) {
            list.add(new Stop(0.30 * length, 120 + rng.nextDouble() * 180, "Entrega"));
            list.add(new Stop(0.65 * length, 120 + rng.nextDouble() * 180, "Entrega"));
        }
        list.sort(Comparator.comparingDouble(Stop::atM));
        list.add(new Stop(length, 180 + rng.nextDouble() * 240, "Carga/descarga"));
        stops = list;
        nextStop = 0;
    }

    private void arriveAtStop(Stop stop) {
        phase = Phase.STOPPED;
        phaseTimer = stop.durationS();
        stopReason = stop.reason();
        v = 0;
        accel = 0;
    }

    private void leaveStop() {
        Stop stop = stops.get(nextStop);
        nextStop++;
        if (nextStop >= stops.size()) {
            returning = !returning;
            legPos = 0;
            if (fuelL < REFUEL_BELOW * profile.tankL()) fuelL = profile.tankL() * (0.95 + 0.05 * rng.nextDouble());
            planLeg();
        } else {
            legPos = Math.max(legPos, stop.atM());
        }
        phase = Phase.DRIVING;
    }

    // =====================================================================================================
    // passo de integração

    private void substep(double h) {
        t += h;
        double misfire = faults.level(FaultType.ERRATIC_RPM, t);
        double boostLeak = faults.level(FaultType.BOOST_LEAK, t);
        double powerFactor = (1 - 0.12 * misfire) * (1 - 0.25 * boostLeak);

        double tNeedNm = 0;
        double tAvailNm = 0;
        double aCmd = 0;
        boolean moving = false;

        switch (phase) {
            case STARTING, STOPPED -> {
                v = 0;
                accel = 0;
                gear = profile.startGear();
                phaseTimer -= h;
                if (phaseTimer <= 0) {
                    if (phase == Phase.STARTING) phase = Phase.DRIVING;
                    else leaveStop();
                }
            }
            case DRIVING -> {
                moving = true;
                aCmd = driverCommand(h);
                double theta = Math.atan(grade(legPos));
                double fRes = massKg * G * (profile.rollingCoef() * Math.cos(theta) + Math.sin(theta))
                        + 0.5 * AIR_DENSITY * profile.frontalAreaCdA() * v * v;
                double mEff = massKg * 1.05;
                double ratio = profile.gearRatios()[gear - 1] * profile.finalDrive();
                double aAct;
                if (shiftTimer > 0) {
                    aAct = Math.min(aCmd, -fRes / mEff);
                    torqueNm = 0;
                } else {
                    double fNeed = mEff * aCmd + fRes;
                    double rpmNow = Math.max(engineIdle(), profile.rpmAt(v, gear));
                    tAvailNm = profile.maxTorqueAt(rpmNow) * powerFactor;
                    if (fNeed > 0) {
                        tNeedNm = fNeed * profile.wheelRadiusM() / (ratio * profile.drivetrainEff());
                        torqueNm = Math.min(tNeedNm, tAvailNm);
                        double fDrive = torqueNm * ratio * profile.drivetrainEff() / profile.wheelRadiusM();
                        aAct = (fDrive - fRes) / mEff;
                    } else {
                        torqueNm = 0;
                        aAct = aCmd;
                    }
                }
                accel = aAct;
                v = Math.max(0, v + aAct * h);
                legPos += v * h;
                odometerKm += v * h / 1000;
                Stop stop = stops.get(nextStop);
                if (legPos >= stop.atM() - 1.5 && v < 0.8) arriveAtStop(stop);
                else if (legPos >= route.lengthM()) {
                    nextStop = stops.size() - 1;
                    arriveAtStop(stops.get(nextStop));
                }
                selectGear(h, tNeedNm, tAvailNm);
            }
        }

        updateEngine(h, moving, aCmd, tNeedNm, tAvailNm, misfire);
        updateFuel(h);
        updateThermal(h);
        updateAirAndOil(h, boostLeak);
        updateElectrical(h);
    }

    /** Aceleração que o motorista pede: mira o limite (com variação lenta), antecipa reduções e paradas. */
    private double driverCommand(double h) {
        DriverStyle style = options.style();
        double drift = speedVariation.next(h);
        double target = limitMs(legPos) * style.speedFactor + drift;
        double b = style.comfortDecel;
        for (double x = 25; x <= LOOKAHEAD_M; x += 25) {
            double vl = limitMs(legPos + x) * style.speedFactor;
            target = Math.min(target, Math.sqrt(vl * vl + 2 * b * x));
        }
        Stop stop = stops.get(nextStop);
        double toStop = stop.atM() - legPos;
        double requiredDecel = 0;
        if (toStop < LOOKAHEAD_M) {
            double r = Math.max(0, toStop - 1.0);
            target = Math.min(target, Math.sqrt(2 * b * r));
            requiredDecel = r > 0.5 ? v * v / (2 * r) : (v > 0 ? 3.0 : 0);
        }
        target = clamp(target, 0, (profile.maxSpeedKmh() + LIMITER_MARGIN_KMH) / 3.6);
        double aDes = clamp((target - v) / 3.0, -2.5, style.maxAccel);
        if (requiredDecel > b) aDes = Math.min(aDes, -Math.min(requiredDecel, 3.5));
        if (toStop < 3 && v < 1.5) aDes = Math.min(aDes, -1.0);
        double up = style.jerk * h;
        double down = 3 * style.jerk * h;
        return accel + clamp(aDes - accel, -down, up);
    }

    private void selectGear(double h, double tNeedNm, double tAvailNm) {
        if (shiftTimer > 0) {
            shiftTimer -= h;
            return;
        }
        shiftCooldown -= h;
        if (v < 1.0) {
            gear = profile.startGear();
            return;
        }
        if (shiftCooldown > 0) return;
        double rpmHere = profile.rpmAt(v, gear);
        int upshift = profile.upshiftRpm() + options.style().upshiftOffsetRpm;
        int newGear = gear;
        if (gear < profile.topGear() && rpmHere > upshift
                && profile.rpmAt(v, gear + 1) > profile.downshiftRpm() + 50) {
            newGear = gear + 1;
        } else if (gear > 1 && rpmHere < profile.downshiftRpm()) {
            newGear = gear - 1;
        } else if (gear > 1 && tNeedNm > 0.97 * tAvailNm
                && profile.rpmAt(v, gear - 1) < profile.ratedRpm() - 150) {
            newGear = gear - 1; // redução para ganhar torque (subida)
        }
        if (newGear != gear) {
            gear = newGear;
            shiftTimer = profile.topGear() > 6 ? 0.8 : 0.3;
            shiftCooldown = 2.5;
        }
    }

    private double engineIdle() {
        return profile.idleRpm() + 150 * clamp((60 - coolant) / 40, 0, 1);
    }

    private void updateEngine(double h, boolean moving, double aCmd, double tNeedNm, double tAvailNm, double misfire) {
        double idle = engineIdle();
        double target;
        if (phase == Phase.STARTING && t < 2.0) {
            target = idle * Math.min(1, t / 1.5);
        } else if (moving) {
            double mech = profile.rpmAt(v, gear);
            target = mech < idle ? idle + 350 * pedalPct / 100 : mech;
        } else {
            target = idle;
        }
        double erratic = misfire > 0
                ? misfire * (120 * Math.sin(2 * Math.PI * t / 1.3 + misfirePhase) + 100 * rng.nextGaussian())
                : 0;
        rpm = clamp(target + rpmNoise.next(h) + erratic, 0, profile.maxRpm());

        double omega = Math.max(rpm, 1) * 2 * Math.PI / 60;
        double accessoryNm = 2500 * profile.voltageScale() / omega;
        fuelCut = moving && torqueNm == 0 && aCmd < 0 && rpm > idle + 150;
        double total = fuelCut ? 0 : torqueNm + accessoryNm;
        double maxHere = Math.max(1, profile.maxTorqueAt(rpm));
        loadPct = fuelCut ? 0 : clamp(total / maxHere * 100, 0, 100);
        torquePct = fuelCut ? -8 : clamp(total / profile.peakTorqueNm() * 100, 0, 100);

        DriverStyle style = options.style();
        boolean steady = moving && v > 60 / 3.6 && Math.abs(aCmd) < 0.08 && torqueNm > 0;
        cruiseTimer = steady ? cruiseTimer + h : 0;
        if (!moving || torqueNm == 0) {
            pedalPct = 0;
        } else if (style.usesCruiseControl && cruiseTimer > 20) {
            pedalPct = 0; // piloto automático: o pedal fica solto (como no log real do T270)
        } else {
            double saturation = tNeedNm > tAvailNm ? 1.0 : 0.92;
            pedalPct = clamp(loadPct * saturation + 3, 0, 100);
        }
    }

    private void updateFuel(double h) {
        if (fuelCut) {
            fuelRateLph = 0;
        } else {
            double omega = rpm * 2 * Math.PI / 60;
            double shaftKw = (torqueNm + 2500 * profile.voltageScale() / Math.max(omega, 1)) * omega / 1000;
            double load = loadPct / 100;
            double bsfc = profile.bsfcGPerKwh() * (1 + 0.15 * Math.max(0, 0.3 - load) / 0.3);
            double idleAccessoryLph = 2.5 * profile.voltageScale() * bsfc / (1000 * DIESEL_KG_PER_L);
            double base = Math.max(0.3, profile.idleFuelLph() - idleAccessoryLph) * rpm / profile.idleRpm();
            double rate = base + bsfc * shaftKw / (1000 * DIESEL_KG_PER_L);
            rate *= 1 + 0.35 * faults.level(FaultType.FUEL_OVERCONSUMPTION, t);
            fuelRateLph = rate;
        }
        double leakLph = 40 * faults.level(FaultType.FUEL_LEAK, t);
        fuelL = Math.max(0, fuelL - (fuelRateLph + leakLph) * h / 3600);
    }

    private void updateThermal(double h) {
        // potência química do combustível: L/h · kg/L · 1000 g/kg · kJ/g / 3600 s/h = kJ/s = kW
        double fuelKw = fuelRateLph * DIESEL_KG_PER_L * 1000 * DIESEL_KJ_PER_G / 3600;
        double qIn = HEAT_TO_COOLANT * fuelKw;
        if (coolant >= FAN_ON_C) fanOn = true;
        else if (coolant <= FAN_OFF_C) fanOn = false;
        double open = clamp((coolant - THERMOSTAT_OPEN_C) / (THERMOSTAT_FULL_C - THERMOSTAT_OPEN_C), 0.03, 1);
        double air = 0.35 + 0.65 * Math.min(1, v / 22) + (fanOn ? 1.2 : 0);
        double radEff = 1 - 0.92 * faults.level(FaultType.OVERHEATING, t);
        double qOut = profile.radiatorKwPerK() * radEff * open * (coolant - ambient) * air + 0.08 * (coolant - ambient);
        coolant = Math.min(125, coolant + (qIn - qOut) / profile.thermalMassKjPerK() * h); // alívio da tampa pressurizada

        double intakeTarget = ambient + 10 + 32 * loadPct / 100 + 6 * (1 - Math.min(1, v / 22));
        intake += (intakeTarget - intake) * Math.min(1, h / 40);
    }

    private void updateAirAndOil(double h, double boostLeak) {
        double idle = profile.idleRpm();
        double spool = clamp((rpm - idle) / (0.55 * profile.ratedRpm() - idle), 0, 1);
        double boostTarget = profile.maxBoostKpa() * Math.pow(loadPct / 100, 1.25) * spool * (1 - 0.5 * boostLeak);
        boost += (boostTarget - boost) * Math.min(1, h / 1.5);

        double x = clamp((rpm - idle) / (profile.ratedRpm() - idle), 0, 1);
        double oilTarget = rpm < 100 ? 0
                : profile.oilIdleKpa() + (profile.oilRatedKpa() - profile.oilIdleKpa()) * Math.pow(x, 0.6);
        oilTarget *= 1 + 0.25 * clamp((80 - coolant) / 60, 0, 1); // óleo frio: mais viscoso
        oilTarget *= 1 - 0.5 * faults.level(FaultType.OIL_PRESSURE_DROP, t);
        oil += (oilTarget - oil) * Math.min(1, h / 1.0);
    }

    private void updateElectrical(double h) {
        double k = profile.voltageScale();
        double alternator = 1 - faults.level(FaultType.BATTERY_DRAIN, t);
        double ocv = k * (11.8 + 0.9 * soc);
        boolean running = rpm > 300;
        double regulated = k * 14.0;
        if (running && alternator >= 0.999) {
            soc = Math.min(1, soc + h / 3600 * 0.2);
            batteryV = regulated;
        } else {
            double deficitA = profile.electricalLoadA() * (running ? 1 - alternator : 1);
            soc = Math.max(0, soc - deficitA * h / 3600 / profile.batteryAh());
            double terminal = ocv - deficitA * 0.008 * k;
            batteryV = running ? alternator * regulated + (1 - alternator) * terminal : terminal;
        }
    }

    // =====================================================================================================
    // sensores

    private VehicleSnapshot snapshot() {
        double d = routeDist(clamp(legPos, 0, route.lengthM()));
        double[] ll = route.latLonAt(d);
        double heading = route.headingAt(d);
        if (returning) heading = (heading + 180) % 360;

        double drift = faults.level(FaultType.GPS_DRIFT, t);
        double sigmaM = 2.5 + 30 * drift;
        double north = gpsNorth.next(lastStepS) * sigmaM;
        double east = gpsEast.next(lastStepS) * sigmaM;
        double lat = ll[0] + north / 111_320.0;
        double lon = ll[1] + east / (111_320.0 * Math.cos(Math.toRadians(ll[0])));
        double accuracy = 3 + Math.abs(rng.nextGaussian()) + 25 * drift;

        double fuelPct = fuelL / profile.tankL() * 100 + fuelSlosh.next(lastStepS) * (1 + 4 * Math.abs(accel));
        String phaseLabel = switch (phase) {
            case STARTING -> "Partida";
            case DRIVING -> "Em movimento";
            case STOPPED -> "Parado (" + stopReason + ")";
        };

        return new VehicleSnapshot(
                t, lat, lon, heading, accuracy,
                round(v * 3.6, 1.0 / 256),
                (int) Math.round(rpm),
                gear,
                round(loadPct, 1),
                round(torquePct, 1),
                round(pedalPct, 0.4),
                round(coolant + coolantNoise.next(lastStepS), 1),
                rpm < 100 ? 0 : round(Math.max(0, oil + 2 * rng.nextGaussian()), 4),
                round(fuelRateLph * (1 + 0.004 * rng.nextGaussian()), 0.05),
                round(clamp(fuelPct, 0, 100), 0.4),
                round(Math.max(0, boost + 1.5 * rng.nextGaussian()), 2),
                round(intake, 1),
                round(batteryV + batteryNoise.next(lastStepS), 0.05),
                odometerKm,
                phaseLabel,
                grade(clamp(legPos, 0, route.lengthM())) * 100,
                limitMs(legPos) * 3.6,
                clamp(legPos / route.lengthM(), 0, 1),
                returning);
    }

    /** Arredonda para a resolução do sensor. */
    private static double round(double value, double resolution) {
        return Math.round(value / resolution) * resolution;
    }

    private static double clamp(double x, double lo, double hi) {
        return Math.max(lo, Math.min(hi, x));
    }

    /** Ruído AR(1) de média zero e desvio {@code sigma}, com constante de tempo {@code tauS}. */
    static final class Ar1 {
        private final double tauS;
        private final double sigma;
        private final Random rng;
        private double x;

        Ar1(double tauS, double sigma, Random rng) {
            this.tauS = tauS;
            this.sigma = sigma;
            this.rng = rng;
        }

        double next(double h) {
            double phi = Math.exp(-h / tauS);
            x = phi * x + sigma * Math.sqrt(1 - phi * phi) * rng.nextGaussian();
            return x;
        }
    }
}
