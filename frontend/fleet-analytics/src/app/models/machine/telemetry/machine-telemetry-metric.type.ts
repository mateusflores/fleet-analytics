import { MachineTelemetry } from './machine-telemetry.model';

export type TelemetryMetric =
  | 'rpm'
  | 'temperature'
  | 'fuel'
  | 'battery'
  | 'odometer'
  | 'speed'
  | 'fuelRate'
  | 'oilPressure';

/** Campo da leitura correspondente a cada métrica dos gráficos. */
export const TELEMETRY_METRIC_FIELD: Record<TelemetryMetric, keyof MachineTelemetry> = {
  rpm: 'engineRpm',
  temperature: 'engineTempC',
  fuel: 'fuelLevelPct',
  battery: 'batteryVoltage',
  odometer: 'odometerKm',
  speed: 'speedKmh',
  fuelRate: 'fuelRateLph',
  oilPressure: 'oilKpa'
};
