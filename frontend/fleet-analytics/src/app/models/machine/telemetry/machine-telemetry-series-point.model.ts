export interface MachineTelemetrySeriesPoint {
    timestamp: string;
    engineRpm: number;
    fuelLevelPct: number;
    engineTempC: number;
    odometerKm: number;
    batteryVoltage: number;
}