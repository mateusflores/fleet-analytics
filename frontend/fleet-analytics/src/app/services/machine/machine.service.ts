import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Machine } from '../../models/machine/machine.model';
import { MachineTelemetry } from '../../models/machine/telemetry/machine-telemetry.model';
import { delay, map, Observable, of, timer } from 'rxjs';
import { PaginatedResponse } from '../../responses/paginated-response.model';
import { MachineTelemetrySeriesPoint } from '../../models/machine/telemetry/machine-telemetry-series-point.model';
import { TelemetryMetric } from '../../models/machine/telemetry/machine-telemetry-metric.type';
import { environment } from '../../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class MachineService {

  private readonly _httpClient: HttpClient = inject(HttpClient);
  private readonly _vehiclesUrl = `${environment.apiBaseUrl}/vehicles`;

  /** Base do WebSocket (ex.: `ws://localhost:3000/ws/machines`). Ajuste conforme o backend. */
  private readonly _machineTelemetryWsBaseUrl = 'ws://localhost:3000/ws/machines';

  constructor() { }
  

  public getMachines(
    field: keyof Machine = 'nickname',
    term = '',
    page = 0,
    pageSize = 9
  ): Observable<PaginatedResponse<Machine>> {
    return this._httpClient.get<PaginatedResponse<Machine>>(`${this._vehiclesUrl}/search?${field}=${term}&page=${page}&size=${pageSize}`);
  }

  public addMachine(machine: Machine): Observable<Machine> {
    return this._httpClient.post<Machine>(this._vehiclesUrl, machine);
  }

  public updateMachine(id: string, machine: Partial<Machine>): Observable<Machine | undefined> {
    return this._httpClient.put<Machine>(`${this._vehiclesUrl}/${id}`, machine);
  }

  public getMachineById(id: string): Observable<Machine | undefined> {
    return this._httpClient.get<Machine>(`${this._vehiclesUrl}/${id}`);
  }

  public getMachineTelemetrySeries(
    _machineId: string,
    _periodStart: Date,
    _metric: TelemetryMetric
  ): Observable<MachineTelemetrySeriesPoint[]> {
    return of([]).pipe(delay(200));
  }

  public streamMachineTelemetry(_machineId: string): Observable<MachineTelemetry> {
    return of({
      id: 0,
      rpm: 0,
      temperature: 0,
      fuel: 0,
      pressure: 0
    });
  }

  /** Mock: 40 pontos de `periodStart` até agora (cada ponto = média/máx/mín de 2 amostras no tempo). */
  /*public getMachineTelemetrySeries(
    machineId: number,
    periodStart: Date,
    metric: TelemetryMetric
  ): Observable<MachineTelemetrySeriesPoint[]> {
    const startMs = periodStart.getTime();
    const endMs = Date.now();

    if (!Number.isFinite(startMs) || endMs <= startMs) {
      return of([]).pipe(delay(200));
    }
    const span = Math.max(endMs - startMs, 1);
    const phase = machineId * 0.37;
    const rawTotal = 80;
    const denom = rawTotal - 1;
    const points: MachineTelemetrySeriesPoint[] = [];
    for (let b = 0; b < 40; b++) {
      const i0 = b * 2;
      const i1 = i0 + 1;
      const t0 = startMs + (span * i0) / denom;
      const t1 = startMs + (span * i1) / denom;
      const u0 = (t0 - startMs) / span;
      const u1 = (t1 - startMs) / span;
      const v0 = this.mockTelemetryValueAt(metric, u0, phase, machineId, t0);
      const v1 = this.mockTelemetryValueAt(metric, u1, phase, machineId, t1);
      points.push({
        timestamp: new Date((t0 + t1) / 2).toISOString(),
        average: this.roundTelemetryMetric(metric, (v0 + v1) / 2),
        max: this.roundTelemetryMetric(metric, Math.max(v0, v1)),
        min: this.roundTelemetryMetric(metric, Math.min(v0, v1))
      });
    }
    return of(points).pipe(delay(200));
  }

  public streamMachineTelemetry(machineId: number): Observable<MachineTelemetry> {
    if (this._useMockMachineTelemetry) {
      return timer(0, 2000).pipe(map(() => this.buildMockTelemetry(machineId)));
    }
    const base = this._machineTelemetryWsBaseUrl.replace(/\/$/, '');
    const url = `${base}/${machineId}/telemetry`;
    return webSocket<MachineTelemetry>({ url });
  }

  private buildMockTelemetry(machineId: number): MachineTelemetry {
    const phase = machineId * 0.37;
    const t = Date.now() / 1000;
    return {
      id: machineId,
      rpm: Math.round(1100 + 280 * Math.sin(t * 0.4 + phase) + (Math.random() - 0.5) * 45),
      temperature: Math.round(78 + 12 * Math.sin(t * 0.25 + phase) + (Math.random() - 0.5) * 4),
      fuel: Math.max(
        8,
        Math.min(100, Math.round(42 + 18 * Math.sin(t * 0.12 + phase) + (Math.random() - 0.5) * 3))
      ),
      pressure: Math.round(36 + 7 * Math.sin(t * 0.3 + phase) + (Math.random() - 0.5) * 5)
    };
  }

  private mockTelemetryValueAt(
    metric: TelemetryMetric,
    u: number,
    phase: number,
    machineId: number,
    tMs: number
  ): number {
    const t = tMs / 1000;
    const noise = (this.pseudoRandomNumber(machineId, Math.floor(tMs / 60_000)) - 0.5) * 2;
    switch (metric) {
      case 'rpm':
        return Math.round(1100 + 280 * Math.sin(t * 0.0008 + phase) + 40 * Math.sin(u * Math.PI * 4 + phase) + noise * 25);
      case 'temperature':
        return Math.round(78 + 12 * Math.sin(t * 0.0005 + phase) + 6 * Math.sin(u * Math.PI * 3) + noise * 3);
      case 'fuel': {
        const raw = 42 + 18 * Math.sin(t * 0.00025 + phase) + 8 * (1 - u) + noise * 4;
        return Math.max(8, Math.min(100, Math.round(raw)));
      }
      case 'pressure':
        return Math.round(36 + 7 * Math.sin(t * 0.0006 + phase) + 4 * Math.sin(u * Math.PI * 5 + phase) + noise * 4);
      default: {
        const _exhaustive: never = metric;
        return _exhaustive;
      }
    }
  }

  private pseudoRandomNumber(machineId: number, bucket: number): number {
    const x = Math.sin(machineId * 12.9898 + bucket * 78.233) * 43758.5453;
    return x - Math.floor(x);
  }

  private roundTelemetryMetric(metric: TelemetryMetric, n: number): number {
    if (metric === 'fuel') {
      return Math.max(8, Math.min(100, Math.round(n)));
    }
    return Math.round(n);
  }
    */
}
