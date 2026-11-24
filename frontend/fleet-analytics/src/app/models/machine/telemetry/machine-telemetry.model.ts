/**
 * Leitura de telemetria (REST e SSE). Qualquer sinal pode vir `null`:
 * leituras antigas ou dispositivos que não enviam o sinal.
 */
export interface MachineTelemetry {
  timestamp: string;
  batteryVoltage: number | null;
  engineRpm: number | null;
  engineTempC: number | null;
  fuelLevelPct: number | null;
  odometerKm: number | null;
  /** km/h */
  speedKmh?: number | null;
  /** carga do motor, % */
  loadPct?: number | null;
  /** torque do motor, % (negativo no freio-motor) */
  engineTorquePct?: number | null;
  /** acelerador, % */
  pedalPct?: number | null;
  /** marcha engatada */
  gear?: number | null;
  /** pressão do óleo, kPa */
  oilKpa?: number | null;
  /** consumo instantâneo, L/h */
  fuelRateLph?: number | null;
  /** pressão do turbo, kPa */
  boostKpa?: number | null;
  /** temperatura de admissão, °C */
  intakeC?: number | null;
}
