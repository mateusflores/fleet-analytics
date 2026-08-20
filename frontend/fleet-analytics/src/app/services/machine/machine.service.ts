import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Machine } from '../../models/machine/machine.model';
import { Observable } from 'rxjs';
import { PaginatedResponse } from '../../responses/paginated-response.model';
import { MachineTelemetrySeriesPoint } from '../../models/machine/telemetry/machine-telemetry-series-point.model';
import { environment } from '../../../environments/environment';
import { SseService } from '../sse/sse.service';
import { AuthService } from '../auth/auth.service';

@Injectable({
  providedIn: 'root'
})
export class MachineService {

  private readonly _httpClient: HttpClient = inject(HttpClient);
  private readonly _sseService = inject(SseService);
  private readonly _authService = inject(AuthService);
  private readonly _vehiclesUrl = `${environment.apiBaseUrl}/vehicles`;
  private readonly _vehicleTelemetryUrl = `${environment.apiBaseUrl}/telemetry/vehicle-data`;

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

  public getMachineTelemetrySeries(_machineId: string, _periodStart: Date): Observable<MachineTelemetrySeriesPoint[]> {
    return this._httpClient.get<MachineTelemetrySeriesPoint[]>(`${this._vehicleTelemetryUrl}?vehicleId=${_machineId}&from=${_periodStart.toISOString()}&to=${new Date().toISOString()}`);
  }

  public streamMachineTelemetry(id: string): Observable<any> {
    return this._sseService.connect<any>(`${this._vehicleTelemetryUrl}/sse?vehicleId=${id}&token=${this._authService.getAccessToken()}`);
  }
}
