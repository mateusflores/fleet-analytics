import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { FleetPolicy, UpdateFleetPolicyRequest } from '../../models/fleet-policy/fleet-policy.model';

@Injectable({ providedIn: 'root' })
export class FleetPolicyService {
  private readonly _httpClient = inject(HttpClient);
  private readonly _url = `${environment.apiBaseUrl}/fleet-policy`;

  getPolicy(): Observable<FleetPolicy> {
    return this._httpClient.get<FleetPolicy>(this._url);
  }

  updatePolicy(request: UpdateFleetPolicyRequest): Observable<FleetPolicy> {
    return this._httpClient.put<FleetPolicy>(this._url, request);
  }
}
